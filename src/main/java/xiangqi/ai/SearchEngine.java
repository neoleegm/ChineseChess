package xiangqi.ai;

import xiangqi.core.Board;
import xiangqi.core.Move;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置搜索引擎：迭代加深 PVS + 置换表 + 空着裁剪 + 后期着法削减 + 静态搜索。
 *
 * <p>设计取舍：</p>
 * <ul>
 *   <li>走法在内部用 {@code int} 打包传递，只在根节点转成 {@link Move}，
 *       避免每个节点产生几十个临时对象；</li>
 *   <li>被将时延伸一层深度、静态搜索改为搜索全部应将着法（不再使用静态分），
 *       否则「被将局面直接吃子评估」会严重低估危险；</li>
 *   <li>重复局面在搜索路径上按长将规则打分：单方步步将军判负，其余判和；</li>
 *   <li>对局历史局面会被注入搜索路径，因此 AI 能感知真实对局中的循环。</li>
 * </ul>
 */
public final class SearchEngine implements Engine {

    private static final int MAX_PLY = 64;
    private static final int MAX_MOVES = 160;
    private static final int INF = 1_000_000;
    private static final int MATE = SearchResult.MATE_SCORE;
    private static final int NO_REPETITION = Integer.MIN_VALUE;

    private static final int TT_BITS = 20;
    private static final int TT_SIZE = 1 << TT_BITS;
    private static final int TT_MASK = TT_SIZE - 1;
    private static final byte EXACT = 0;
    private static final byte LOWER = 1;
    private static final byte UPPER = 2;
    /** 只存着法、不存分数：分值受重复局面影响时用它，避免污染置换表。 */
    private static final byte MOVE_ONLY = 3;
    /** 主变着抽取的最大长度。 */
    private static final int PV_MAX = 8;

    // ---- 置换表 ----
    private final long[] ttKeys = new long[TT_SIZE];
    private final int[] ttScores = new int[TT_SIZE];
    private final int[] ttMoves = new int[TT_SIZE];
    private final short[] ttDepths = new short[TT_SIZE];
    private final byte[] ttFlags = new byte[TT_SIZE];

    // ---- 每层复用的走法缓冲 ----
    private final int[][] moveBuffers = new int[MAX_PLY + 8][MAX_MOVES];
    private final int[][] orderScores = new int[MAX_PLY + 8][MAX_MOVES];

    // ---- 启发式 ----
    private final int[][] killers = new int[MAX_PLY + 8][2];
    private final int[][] history = new int[Board.SQUARES][Board.SQUARES];

    // ---- 搜索路径（对局历史 + 当前搜索栈） ----
    private long[] pathKeys = new long[256];
    private boolean[] pathMoverRed = new boolean[256];
    private boolean[] pathGivesCheck = new boolean[256];
    private int pathLen;
    private final Map<Long, Integer> pathCounts = new HashMap<>(512);

    private Board board;
    private long deadlineNanos;
    private long nodes;
    private boolean stopped;
    /**
     * 搜索过程中命中重复局面的次数。子树里只要命中过，这一层的分值就依赖当前路径，
     * 不能写进置换表（graph-history interaction），否则换一条路径复用会得到错误结论。
     */
    private long repetitionHits;
    /** 根节点容忍度：>0 时在「与最优着法差距不超过该值」的着法里随机挑，用于低难度档。 */
    private int rootMargin;
    private final java.util.Random random = new java.util.Random();
    /** 外部中止信号：界面切局/悔棋时立刻放弃当前搜索。 */
    private volatile boolean aborted;

    /** 请求中止当前搜索（可在其他线程调用）。 */
    public void abort() {
        aborted = true;
    }

    @Override
    public String name() {
        return "内置引擎";
    }

    @Override
    public SearchResult search(Board source, SearchLimits limits) {
        // 复制一份，搜索期间只改副本，调用方棋盘保持不变
        this.board = source.copy();
        this.nodes = 0;
        this.stopped = false;
        this.aborted = false;
        this.repetitionHits = 0;
        this.rootMargin = Math.max(0, limits.rootMargin());
        this.deadlineNanos = System.nanoTime() + Math.max(30, limits.timeMs()) * 1_000_000L;
        clearHeuristics();
        seedPath();
        return iterativeDeepening(Math.max(1, limits.maxDepth()), deadlineNanos);
    }

    // ==================== 迭代加深 ====================

    private SearchResult iterativeDeepening(int maxDepth, long deadline) {
        List<Move> legal = board.legalMoves();
        if (legal.isEmpty()) {
            return SearchResult.empty();
        }
        Move best = legal.get(0);
        int bestScore = 0;
        int completed = 0;

        for (int depth = 1; depth <= maxDepth; depth++) {
            int alpha = -INF;
            int beta = INF;
            if (depth >= 4 && Math.abs(bestScore) < MATE - 1_000) {
                alpha = bestScore - 40;
                beta = bestScore + 40;
            }
            int[] rootBest = {0};
            int score = rootSearch(depth, alpha, beta, rootBest);
            if (stopped) {
                break;
            }
            if (depth >= 4 && (score <= alpha || score >= beta)) {
                rootBest[0] = 0;
                score = rootSearch(depth, -INF, INF, rootBest);
                if (stopped) {
                    break;
                }
            }
            if (rootBest[0] != 0) {
                best = Move.of(Board.moveFrom(rootBest[0]), Board.moveTo(rootBest[0]));
                bestScore = score;
                completed = depth;
            }
            if (Math.abs(bestScore) > MATE - 1_000) {
                break; // 已找到确定的杀棋
            }
            if (System.nanoTime() >= deadline) {
                break;
            }
        }
        return new SearchResult(best, bestScore, completed, nodes, extractPv(best));
    }

    /**
     * 从置换表抽取主变着。根节点强制从本次搜索的最优着法起步，
     * 之后逐步查表、试走，命中缺失 / 非法 / 重复即停止。
     */
    private List<Move> extractPv(Move best) {
        List<Move> pv = new java.util.ArrayList<>(PV_MAX);
        if (best == null || !board.isLegalMove(best)) {
            return pv;
        }
        pv.add(best);
        Board scratch = board.copy();
        if (!scratch.play(best)) {
            return pv;
        }
        for (int i = 1; i < PV_MAX; i++) {
            Move next = ttMoveAt(scratch);
            if (next == null || !scratch.play(next)) {
                break;
            }
            pv.add(next);
        }
        return pv;
    }

    /** 读取置换表中记录的最佳着法，并校验它在当前局面上合法。 */
    private Move ttMoveAt(Board position) {
        int index = (int) (position.key() & TT_MASK);
        if (ttKeys[index] != position.key()) {
            return null;
        }
        int packed = ttMoves[index];
        if (packed == 0) {
            return null;
        }
        Move move = Move.of(Board.moveFrom(packed), Board.moveTo(packed));
        return position.isLegalMove(move) ? move : null;
    }

    private int rootSearch(int depth, int alpha, int beta, int[] bestOut) {
        int n = generateLegal(0);
        int[] buf = moveBuffers[0];
        orderMoves(buf, n, 0, ttProbeMove(board.key()));

        if (rootMargin > 0) {
            return rootSearchWithMargin(depth, buf, n, bestOut);
        }

        int bestScore = -INF;
        int bestMove = 0;
        for (int i = 0; i < n; i++) {
            int move = buf[i];
            Board.MoveRecord record = board.apply(Board.moveFrom(move), Board.moveTo(move));
            int score;
            if (i == 0) {
                score = -pvs(depth - 1, -beta, -alpha, 1, true);
            } else {
                score = -pvs(depth - 1, -alpha - 1, -alpha, 1, true);
                if (!stopped && score > alpha && score < beta) {
                    score = -pvs(depth - 1, -beta, -alpha, 1, true);
                }
            }
            board.revert(record);
            if (stopped) {
                return bestScore == -INF ? 0 : bestScore;
            }
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            if (score > alpha) {
                alpha = score;
            }
            if (alpha >= beta) {
                break;
            }
        }
        if (bestMove != 0) {
            bestOut[0] = bestMove;
        }
        return bestScore;
    }

    /**
     * 低难度档专用根搜索：对每个根着法做一次全窗口搜索拿到真实分数，
     * 然后在「不差于最优 {@code rootMargin} 厘兵」的着法里随机挑一手。
     *
     * <p>这样既有随机性（不会被玩家摸清固定套路），又不会走出白送车的着法——
     * 白送一个大子会让分数掉 400 以上，直接落在容忍度之外。</p>
     */
    private int rootSearchWithMargin(int depth, int[] buf, int n, int[] bestOut) {
        if (n == 0) {
            return -INF;
        }
        int[] scores = new int[n];
        int bestScore = -INF;
        for (int i = 0; i < n; i++) {
            int move = buf[i];
            Board.MoveRecord record = board.apply(Board.moveFrom(move), Board.moveTo(move));
            int score = -pvs(depth - 1, -INF, INF, 1, true);
            board.revert(record);
            if (stopped) {
                return 0;
            }
            scores[i] = score;
            if (score > bestScore) {
                bestScore = score;
            }
        }

        int candidates = 0;
        for (int i = 0; i < n; i++) {
            if (scores[i] >= bestScore - rootMargin) {
                candidates++;
            }
        }
        if (candidates == 0) {
            return bestScore;
        }
        int pick = random.nextInt(candidates);
        for (int i = 0; i < n; i++) {
            if (scores[i] >= bestScore - rootMargin) {
                if (pick == 0) {
                    bestOut[0] = buf[i];
                    return scores[i];
                }
                pick--;
            }
        }
        return bestScore;
    }

    // ==================== 主搜索 ====================

    private int pvs(int depth, int alpha, int beta, int ply, boolean allowNull) {
        if (stopped) {
            return 0;
        }
        if (ply >= MAX_PLY) {
            return evaluate();
        }
        nodes++;
        if ((nodes & 1023) == 0 && (aborted || System.nanoTime() >= deadlineNanos)) {
            stopped = true;
            return 0;
        }

        long key = board.key();
        boolean side = board.isRedTurn();
        boolean inCheck = board.inCheck(side);
        pushPath(key, !side, inCheck);
        try {
            int repetition = repetitionScore(key, ply);
            if (repetition != NO_REPETITION) {
                return repetition;
            }
            return pvsBody(key, depth, alpha, beta, ply, inCheck, allowNull);
        } finally {
            popPath(key);
        }
    }

    private int pvsBody(long key, int depth, int alpha, int beta, int ply,
                        boolean inCheck, boolean allowNull) {
        long hitsBefore = repetitionHits;
        int ttMove = ttProbeMove(key);
        int ttScore = ttProbe(key, depth, alpha, beta, ply);
        if (ttScore != NO_REPETITION) {
            return ttScore;
        }

        if (inCheck) {
            depth++; // 被将延伸
        }
        if (depth <= 0) {
            return quiescence(alpha, beta, ply);
        }

        int n = generateLegal(ply);
        if (n == 0) {
            return -MATE + ply; // 将死或困毙，规则上同负
        }

        if (allowNull && !inCheck && depth >= 3 && beta < MATE - 1_000 && hasNonPawnMaterial()) {
            board.applyNull();
            int nullScore = -pvs(depth - 3, -beta, -beta + 1, ply + 1, false);
            board.revertNull();
            if (stopped) {
                return 0;
            }
            if (nullScore >= beta) {
                return beta;
            }
        }

        int[] buf = moveBuffers[ply];
        orderMoves(buf, n, ply, ttMove);

        int bestScore = -INF;
        int bestMove = 0;
        byte flag = UPPER;
        int originalAlpha = alpha;

        for (int i = 0; i < n; i++) {
            int move = buf[i];
            boolean capture = Board.moveCapture(move) != 0;
            Board.MoveRecord record = board.apply(Board.moveFrom(move), Board.moveTo(move));
            // 走完之后轮到对方，因此「对方是否被将」就是这手是否将军
            boolean givesCheck = board.inCheck(board.isRedTurn());

            int score;
            if (i == 0) {
                score = -pvs(depth - 1, -beta, -alpha, ply + 1, true);
            } else {
                int reduction = 0;
                // 将军着、应将着、吃子、杀手着都不削减：它们最可能是最佳着法
                if (depth >= 3 && i >= 4 && !capture && !inCheck && !givesCheck
                        && killers[ply][0] != move && killers[ply][1] != move) {
                    reduction = i >= 12 ? 2 : 1;
                }
                score = -pvs(depth - 1 - reduction, -alpha - 1, -alpha, ply + 1, true);
                if (!stopped && reduction > 0 && score > alpha) {
                    score = -pvs(depth - 1, -alpha - 1, -alpha, ply + 1, true);
                }
                if (!stopped && score > alpha && score < beta) {
                    score = -pvs(depth - 1, -beta, -alpha, ply + 1, true);
                }
            }
            board.revert(record);

            if (stopped) {
                return 0;
            }
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            if (score > alpha) {
                alpha = score;
                flag = EXACT;
            }
            if (alpha >= beta) {
                flag = LOWER;
                if (!capture) {
                    updateKillers(ply, move);
                    history[Board.moveFrom(move)][Board.moveTo(move)] += depth * depth;
                }
                break;
            }
        }

        if (bestScore <= originalAlpha) {
            flag = UPPER;
        }
        if (repetitionHits == hitsBefore) {
            ttStore(key, depth, flag, bestScore, bestMove, ply);
        } else {
            // 子树里出现过重复局面，分值依赖当前搜索路径，只保留着法用于排序
            ttStoreMoveOnly(key, depth, bestMove);
        }
        return bestScore;
    }

    // ==================== 静态搜索 ====================

    private int quiescence(int alpha, int beta, int ply) {
        if (stopped) {
            return 0;
        }
        if (ply >= MAX_PLY) {
            return evaluate();
        }
        nodes++;
        if ((nodes & 1023) == 0 && (aborted || System.nanoTime() >= deadlineNanos)) {
            stopped = true;
            return 0;
        }

        boolean inCheck = board.inCheck(board.isRedTurn());
        boolean hasStandPat = !inCheck;
        int standPat = hasStandPat ? evaluate() : 0;
        if (hasStandPat) {
            if (standPat >= beta) {
                return beta;
            }
            if (standPat > alpha) {
                alpha = standPat;
            }
        }

        int n = inCheck ? generateLegal(ply) : generateLegalCaptures(ply);
        if (n == 0) {
            return inCheck ? -MATE + ply : alpha;
        }
        int[] buf = moveBuffers[ply];
        orderMoves(buf, n, ply, 0);

        for (int i = 0; i < n; i++) {
            int move = buf[i];
            if (hasStandPat) {
                int victim = Board.moveCapture(move) - 1;
                if (victim >= 0 && standPat + Evaluation.VALUE[victim] + 200 < alpha) {
                    continue; // 差值裁剪：即使白吃也追不上 alpha
                }
            }
            Board.MoveRecord record = board.apply(Board.moveFrom(move), Board.moveTo(move));
            int score = -quiescence(-beta, -alpha, ply + 1);
            board.revert(record);
            if (stopped) {
                return 0;
            }
            if (score >= beta) {
                return beta;
            }
            if (score > alpha) {
                alpha = score;
            }
        }
        return alpha;
    }

    private int evaluate() {
        return Evaluation.evaluate(board, board.isRedTurn());
    }

    // ==================== 走法生成（带合法性过滤） ====================

    private int generateLegal(int ply) {
        int[] buf = moveBuffers[ply];
        boolean side = board.isRedTurn();
        int n = board.generate(buf, side);
        int kept = 0;
        for (int i = 0; i < n; i++) {
            int move = buf[i];
            Board.MoveRecord record = board.apply(Board.moveFrom(move), Board.moveTo(move));
            boolean legal = !board.inCheck(side);
            board.revert(record);
            if (legal) {
                buf[kept++] = move;
            }
        }
        return kept;
    }

    private int generateLegalCaptures(int ply) {
        int[] buf = moveBuffers[ply];
        boolean side = board.isRedTurn();
        int n = board.generate(buf, side);
        int kept = 0;
        for (int i = 0; i < n; i++) {
            int move = buf[i];
            if (Board.moveCapture(move) == 0) {
                continue;
            }
            Board.MoveRecord record = board.apply(Board.moveFrom(move), Board.moveTo(move));
            boolean legal = !board.inCheck(side);
            board.revert(record);
            if (legal) {
                buf[kept++] = move;
            }
        }
        return kept;
    }

    // ==================== 重复局面 ====================

    private void seedPath() {
        pathLen = 0;
        pathCounts.clear();
        List<Long> keys = board.positionKeys();
        List<Board.MoveRecord> records = board.historyRecords();
        int needed = keys.size() + MAX_PLY + 8;
        if (pathKeys.length < needed) {
            pathKeys = new long[needed];
            pathMoverRed = new boolean[needed];
            pathGivesCheck = new boolean[needed];
        }
        for (int i = 0; i < keys.size(); i++) {
            boolean moverRed = i > 0 && records.get(i - 1).piece().isRed();
            boolean check = i > 0 && records.get(i - 1).givesCheck();
            pushPath(keys.get(i), moverRed, check);
        }
    }

    private void pushPath(long key, boolean moverRed, boolean givesCheck) {
        if (pathLen >= pathKeys.length) {
            int size = pathKeys.length * 2;
            long[] keys = new long[size];
            boolean[] movers = new boolean[size];
            boolean[] checks = new boolean[size];
            System.arraycopy(pathKeys, 0, keys, 0, pathLen);
            System.arraycopy(pathMoverRed, 0, movers, 0, pathLen);
            System.arraycopy(pathGivesCheck, 0, checks, 0, pathLen);
            pathKeys = keys;
            pathMoverRed = movers;
            pathGivesCheck = checks;
        }
        pathKeys[pathLen] = key;
        pathMoverRed[pathLen] = moverRed;
        pathGivesCheck[pathLen] = givesCheck;
        pathLen++;
        pathCounts.merge(key, 1, Integer::sum);
    }

    private void popPath(long key) {
        pathLen--;
        pathCounts.merge(key, -1, (oldValue, one) -> {
            int updated = oldValue + one;
            return updated <= 0 ? null : updated;
        });
    }

    /**
     * 路径上出现同一局面时的评分。单方在循环中步步将军判负，否则按和棋计 0。
     */
    private int repetitionScore(long key, int ply) {
        if (pathCounts.getOrDefault(key, 0) < 2) {
            return NO_REPETITION;
        }
        int previous = -1;
        for (int i = pathLen - 2; i >= 0; i--) {
            if (pathKeys[i] == key) {
                previous = i;
                break;
            }
        }
        if (previous < 0) {
            return NO_REPETITION;
        }
        repetitionHits++;

        int redMoves = 0;
        int redChecks = 0;
        int blackMoves = 0;
        int blackChecks = 0;
        for (int i = previous + 1; i < pathLen; i++) {
            if (pathMoverRed[i]) {
                redMoves++;
                if (pathGivesCheck[i]) {
                    redChecks++;
                }
            } else {
                blackMoves++;
                if (pathGivesCheck[i]) {
                    blackChecks++;
                }
            }
        }
        boolean redAllCheck = redMoves > 0 && redChecks == redMoves;
        boolean blackAllCheck = blackMoves > 0 && blackChecks == blackMoves;
        boolean redToMove = board.isRedTurn();
        if (redAllCheck && !blackAllCheck) {
            return redToMove ? -MATE + ply : MATE - ply;
        }
        if (blackAllCheck && !redAllCheck) {
            return redToMove ? MATE - ply : -MATE + ply;
        }
        return 0;
    }

    // ==================== 着法排序 ====================

    private void orderMoves(int[] buf, int n, int ply, int ttMove) {
        int[] scores = orderScores[ply];
        for (int i = 0; i < n; i++) {
            int move = buf[i];
            int score;
            if (move == ttMove && ttMove != 0) {
                score = 1_000_000;
            } else {
                int capture = Board.moveCapture(move);
                if (capture != 0) {
                    int victim = capture - 1;
                    int attacker = board.pieceAt(Board.moveFrom(move)).type().ordinal();
                    score = 500_000 + Evaluation.VALUE[victim] * 8 - Evaluation.VALUE[attacker];
                } else if (killers[ply][0] == move) {
                    score = 400_000;
                } else if (killers[ply][1] == move) {
                    score = 390_000;
                } else {
                    score = Math.min(history[Board.moveFrom(move)][Board.moveTo(move)], 300_000);
                }
            }
            scores[i] = score;
        }

        // 插入排序：n 很小，比通用排序更快且无额外分配
        for (int i = 1; i < n; i++) {
            int move = buf[i];
            int score = scores[i];
            int j = i - 1;
            while (j >= 0 && scores[j] < score) {
                buf[j + 1] = buf[j];
                scores[j + 1] = scores[j];
                j--;
            }
            buf[j + 1] = move;
            scores[j + 1] = score;
        }
    }

    private void updateKillers(int ply, int move) {
        if (killers[ply][0] != move) {
            killers[ply][1] = killers[ply][0];
            killers[ply][0] = move;
        }
    }

    private void clearHeuristics() {
        for (int[] pair : killers) {
            pair[0] = 0;
            pair[1] = 0;
        }
        for (int[] row : history) {
            java.util.Arrays.fill(row, 0);
        }
    }

    private boolean hasNonPawnMaterial() {
        boolean red = board.isRedTurn();
        for (var piece : board.pieces()) {
            if (piece.isRed() == red && piece.type() != xiangqi.core.Piece.Type.PAWN
                    && piece.type() != xiangqi.core.Piece.Type.KING) {
                return true;
            }
        }
        return false;
    }

    // ==================== 置换表 ====================

    private void ttStore(long key, int depth, byte flag, int score, int move, int ply) {
        int index = (int) (key & TT_MASK);
        if (ttKeys[index] == key && ttDepths[index] > depth) {
            return; // 同一局面已有更深的结论，不覆盖
        }
        if (score > MATE - MAX_PLY) {
            score += ply;
        } else if (score < -MATE + MAX_PLY) {
            score -= ply;
        }
        ttKeys[index] = key;
        ttDepths[index] = (short) depth;
        ttFlags[index] = flag;
        ttScores[index] = score;
        ttMoves[index] = move;
    }

    /** 只存着法（用于分值不可靠的节点）：{@link #ttProbe} 永不命中，{@link #ttProbeMove} 仍可用。 */
    private void ttStoreMoveOnly(long key, int depth, int move) {
        if (move == 0) {
            return;
        }
        int index = (int) (key & TT_MASK);
        if (ttKeys[index] == key && ttDepths[index] > depth) {
            return;
        }
        ttKeys[index] = key;
        ttDepths[index] = (short) depth;
        ttFlags[index] = MOVE_ONLY;
        ttScores[index] = 0;
        ttMoves[index] = move;
    }

    /** 返回置换表命中分数；未命中返回 {@link #NO_REPETITION}。 */
    private int ttProbe(long key, int depth, int alpha, int beta, int ply) {
        int index = (int) (key & TT_MASK);
        if (ttKeys[index] != key || ttDepths[index] < depth) {
            return NO_REPETITION;
        }
        int score = ttScores[index];
        if (score > MATE - MAX_PLY) {
            score -= ply;
        } else if (score < -MATE + MAX_PLY) {
            score += ply;
        }
        return switch (ttFlags[index]) {
            case EXACT -> score;
            case LOWER -> score >= beta ? score : NO_REPETITION;
            case UPPER -> score <= alpha ? score : NO_REPETITION;
            default -> NO_REPETITION;
        };
    }

    private int ttProbeMove(long key) {
        int index = (int) (key & TT_MASK);
        return ttKeys[index] == key ? ttMoves[index] : 0;
    }
}
