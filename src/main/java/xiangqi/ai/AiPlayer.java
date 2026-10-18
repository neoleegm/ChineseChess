package xiangqi.ai;

import xiangqi.core.Board;
import xiangqi.core.Move;
import xiangqi.core.Piece;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * AI 门面：按难度选择实现，负责外部引擎回退、着法合法性兜底、长将自保。
 *
 * <p>所有计算都在调用方传入棋盘的<em>副本</em>上进行，调用方棋盘不会被修改，
 * 因此可以直接在后台线程运行。</p>
 */
public final class AiPlayer {

    private static final int UCI_MAX_FAILURES = 2;

    private final SearchEngine searchEngine = new SearchEngine();
    private final Random random = new Random();

    private Difficulty difficulty = Difficulty.MEDIUM;
    private UciEngine uciEngine;
    private int uciFailures;
    private long timeOverrideMs = -1;
    private String lastInfo = "内置引擎";
    private SearchResult lastResult = SearchResult.empty();

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty == null ? Difficulty.MEDIUM : difficulty;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public void setTimeOverride(long millis) {
        this.timeOverrideMs = millis;
    }

    /** 配置外部引擎路径；空字符串表示不使用。会先销毁旧进程。 */
    public void setEnginePath(String path) {
        if (uciEngine != null) {
            uciEngine.shutdown();
            uciEngine = null;
        }
        uciFailures = 0;
        if (path != null && !path.isBlank()) {
            uciEngine = new UciEngine(path);
        }
    }

    public String enginePath() {
        return uciEngine == null ? "" : uciEngine.enginePath();
    }

    public boolean externalEngineAvailable() {
        return uciEngine != null && uciFailures < UCI_MAX_FAILURES;
    }

    public String lastInfo() {
        return lastInfo;
    }

    /** 上一次搜索的完整结果（深度、评分、节点数），供状态栏展示。 */
    public SearchResult lastResult() {
        return lastResult;
    }

    /** 中止正在进行的搜索（下次 {@link #chooseMove} 会重新开始）。 */
    public void abort() {
        searchEngine.abort();
    }

    public void shutdown() {
        if (uciEngine != null) {
            uciEngine.shutdown();
        }
    }

    /** 为当前行棋方选一手棋；无棋可走返回 {@code null}。 */
    public Move chooseMove(Board board) {
        Board work = board.copy();
        boolean side = work.isRedTurn();
        SearchResult result;

        if (difficulty == Difficulty.EASY) {
            result = easyMove(work, side);
        } else if (difficulty == Difficulty.HARD && externalEngineAvailable()) {
            result = externalMove(work);
            if (result == null) {
                result = internalMove(work);
            }
        } else {
            result = internalMove(work);
        }

        Move move = result.move();
        if (move == null || !isLegalForSide(work, move, side)) {
            move = firstLegal(work, side);
        }
        if (move != null && losesByPerpetualCheck(work, move)) {
            Move alternative = pickAvoidingPerpetualLoss(work, side, move);
            if (alternative != null) {
                move = alternative;
                lastInfo += "（避开长将）";
            }
        }
        return move;
    }

    // ==================== 三档实现 ====================

    /**
     * 简单档：对每个合法着法做一次静态评估，加入随机扰动后在接近最优的着法里随机挑。
     * 会吃白送的子，但看不到对方的回吃，像一个刚学会规则的新手。
     */
    private SearchResult easyMove(Board board, boolean side) {
        List<Move> legal = board.legalMoves();
        if (legal.isEmpty()) {
            return SearchResult.empty();
        }
        double[] scores = new double[legal.size()];
        double best = -Double.MAX_VALUE;
        for (int i = 0; i < legal.size(); i++) {
            Move move = legal.get(i);
            Board.MoveRecord record = board.apply(move.from(), move.to());
            int value = Evaluation.evaluate(board, side);
            board.revert(record);
            scores[i] = value + random.nextDouble() * 140 - 70;
            best = Math.max(best, scores[i]);
        }
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] >= best - 140) {
                pool.add(i);
            }
        }
        lastInfo = "随机应手";
        lastResult = new SearchResult(legal.get(pool.get(random.nextInt(pool.size()))), (int) best, 1, legal.size());
        return lastResult;
    }

    private SearchResult internalMove(Board board) {
        SearchResult result = searchEngine.search(board, limits());
        lastInfo = "内置引擎 d" + result.depth();
        lastResult = result;
        return result;
    }

    private SearchResult externalMove(Board board) {
        try {
            SearchResult result = uciEngine.search(board, limits());
            uciFailures = 0;
            lastInfo = "Pikafish d" + result.depth();
            lastResult = result;
            return result;
        } catch (Exception e) {
            uciFailures++;
            lastInfo = "Pikafish 不可用，回退内置引擎";
            return null;
        }
    }

    private SearchLimits limits() {
        long time = timeOverrideMs > 0 ? timeOverrideMs : difficulty.timeMs();
        return new SearchLimits(difficulty.maxDepth(), time);
    }

    // ==================== 兜底 ====================

    private boolean isLegalForSide(Board board, Move move, boolean side) {
        Piece piece = board.pieceAt(move.from());
        if (piece == null || piece.isRed() != side) {
            return false;
        }
        return board.isLegalMove(move);
    }

    private Move firstLegal(Board board, boolean side) {
        List<Move> legal = board.legalMoves(side);
        return legal.isEmpty() ? null : legal.get(0);
    }

    /** 试走该着是否会立即触发己方长将作负。 */
    private boolean losesByPerpetualCheck(Board board, Move move) {
        Board probe = board.copy();
        if (!probe.play(move)) {
            return false;
        }
        Board.RepetitionOutcome outcome = probe.repetitionOutcome();
        return board.isRedTurn()
                ? outcome == Board.RepetitionOutcome.RED_LOSES
                : outcome == Board.RepetitionOutcome.BLACK_LOSES;
    }

    private Move pickAvoidingPerpetualLoss(Board board, boolean side, Move fallback) {
        for (Move move : board.legalMoves(side)) {
            if (!losesByPerpetualCheck(board, move)) {
                return move;
            }
        }
        return fallback;
    }
}
