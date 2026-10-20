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

    /** 以下字段会被界面线程写入、AI 线程读取，统一用 volatile 保证可见性。 */
    private volatile Difficulty difficulty = Difficulty.MEDIUM;
    private volatile UciEngine uciEngine;
    private volatile int uciFailures;
    private volatile long timeOverrideMs = -1;
    private volatile String lastInfo = "内置引擎";
    private volatile SearchResult lastResult = SearchResult.empty();

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty == null ? Difficulty.MEDIUM : difficulty;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public void setTimeOverride(long millis) {
        this.timeOverrideMs = millis;
    }

    /**
     * 绑定外部引擎实例（生命周期由调用方管理）。传 {@code null} 表示不使用。
     *
     * <p>人机对局与「提示」共用同一个实例：UCI 搜索本身是串行的，
     * 两个进程各开 4 线程只会互相抢 CPU。</p>
     */
    public void setEngine(UciEngine engine) {
        this.uciEngine = engine;
        this.uciFailures = 0;
    }

    public UciEngine engine() {
        return uciEngine;
    }

    public String enginePath() {
        UciEngine engine = uciEngine;
        return engine == null ? "" : engine.enginePath();
    }

    public boolean externalEngineAvailable() {
        UciEngine engine = uciEngine;
        return engine != null && uciFailures < UCI_MAX_FAILURES;
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
        UciEngine engine = uciEngine;
        if (engine != null) {
            engine.warmUp();
        }
    }

    /**
     * 中止正在进行的搜索（下次 {@link #chooseMove} 会重新开始）。
     *
     * <p>内置引擎靠标志位；外部引擎必须显式发 UCI {@code stop}，
     * 否则它会一直算到 movetime 结束，悔棋/重开都要干等。</p>
     */
    public void abort() {
        searchEngine.abort();
        UciEngine engine = uciEngine;
        if (engine != null) {
            engine.stop();
        }
    }

    /** 关闭外部引擎进程；共享实例时由持有者负责关闭，这里只做兜底。 */
    public void shutdown() {
        UciEngine engine = uciEngine;
        if (engine != null) {
            engine.shutdown();
        }
    }

    /** 为当前行棋方选一手棋；无棋可走返回 {@code null}。 */
    public Move chooseMove(Board board) {
        Board work = board.copy();
        boolean side = work.isRedTurn();
        SearchResult result;

        UciEngine engine = uciEngine;
        if (difficulty == Difficulty.HARD && engine != null && externalEngineAvailable()) {
            result = externalMove(engine, work);
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
        if (move != null && losesByRepetition(work, move)) {
            Move alternative = pickAvoidingRepetitionLoss(work, side, move);
            if (alternative != null) {
                move = alternative;
                lastInfo += "（避开长打）";
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

    private SearchResult externalMove(UciEngine engine, Board board) {
        try {
            SearchResult result = engine.search(board, limits());
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

    /** 试走该着是否会立即触发己方长将 / 长捉作负。 */
    private boolean losesByRepetition(Board board, Move move) {
        Board probe = board.copy();
        if (!probe.play(move)) {
            return false;
        }
        Board.RepetitionOutcome outcome = probe.repetitionOutcome();
        return board.isRedTurn()
                ? outcome == Board.RepetitionOutcome.RED_LOSES
                : outcome == Board.RepetitionOutcome.BLACK_LOSES;
    }

    private Move pickAvoidingRepetitionLoss(Board board, boolean side, Move fallback) {
        for (Move move : board.legalMoves(side)) {
            if (!losesByRepetition(board, move)) {
                return move;
            }
        }
        return fallback;
    }
}
