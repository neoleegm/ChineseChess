package xiangqi.ai;

import xiangqi.core.Board;
import xiangqi.core.Move;
import xiangqi.core.Piece;

import java.util.List;

/**
 * AI 门面：按难度选择实现，负责外部引擎回退、着法合法性兜底、长将自保。
 *
 * <p>所有计算都在调用方传入棋盘的<em>副本</em>上进行，调用方棋盘不会被修改，
 * 因此可以直接在后台线程运行。</p>
 */
public final class AiPlayer {

    private static final int UCI_MAX_FAILURES = 2;
    /** 简单档的根节点容忍度：允许在最优着法 0.6 兵以内随机，制造不确定性。 */
    private static final int EASY_ROOT_MARGIN = 60;

    private final SearchEngine searchEngine = new SearchEngine();

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

    /** 预启动外部引擎进程，避免第一次提示时把握手时间算进等待。 */
    public void warmUp() {
        if (uciEngine != null) {
            uciEngine.warmUp();
        }
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

        if (difficulty == Difficulty.HARD && externalEngineAvailable()) {
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

    private SearchResult internalMove(Board board) {
        SearchResult result = searchEngine.search(board, limits());
        lastInfo = "内置引擎";
        lastResult = result;
        return result;
    }

    private SearchResult externalMove(Board board) {
        try {
            SearchResult result = uciEngine.search(board, limits());
            uciFailures = 0;
            lastInfo = "Pikafish";
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
        int margin = difficulty == Difficulty.EASY ? EASY_ROOT_MARGIN : 0;
        return new SearchLimits(difficulty.maxDepth(), time, margin);
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
