package xiangqi.game;

import xiangqi.ai.Difficulty;
import xiangqi.core.Board;
import xiangqi.core.GameResult;
import xiangqi.core.Move;
import xiangqi.core.Notation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 对局会话：把「棋盘状态 + 对局元信息 + 着法列表 + 计时 + 复盘」收在一处。
 *
 * <p>刻意不依赖 Swing：界面只订阅变更通知，测试可以完全无头地驱动整局棋。
 * AI 计算由界面层调度，会话本身只负责状态迁移。</p>
 */
public final class GameSession {

    public enum Mode {
        PVE("人机对战"), PVP("人人对战");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static Mode fromLabel(String label) {
            return "人人对战".equals(label) ? PVP : PVE;
        }
    }

    /** 一手棋的展示信息。{@code elapsedMs} 为该方这步棋的思考用时。 */
    public record MoveEntry(Move move, String notation, long elapsedMs) {
    }

    private final Board board = new Board();
    private final List<MoveEntry> entries = new ArrayList<>();
    private final Deque<MoveEntry> redoStack = new ArrayDeque<>();
    private final List<Runnable> listeners = new ArrayList<>();

    private Mode mode = Mode.PVE;
    private boolean playerIsRed = true;
    private Difficulty difficulty = Difficulty.MEDIUM;
    private GameResult result;
    private long turnStartNanos = System.nanoTime();
    private int reviewPly = -1;
    private Board reviewBoard;

    // ==================== 配置 ====================

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        newGame();
    }

    public boolean playerIsRed() {
        return playerIsRed;
    }

    public void setPlayerIsRed(boolean playerIsRed) {
        this.playerIsRed = playerIsRed;
        newGame();
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty == null ? Difficulty.MEDIUM : difficulty;
        if (mode == Mode.PVE) {
            newGame();
        }
    }

    public Board board() {
        return board;
    }

    // ==================== 对局状态 ====================

    public void newGame() {
        board.reset();
        entries.clear();
        redoStack.clear();
        result = null;
        reviewPly = -1;
        reviewBoard = null;
        turnStartNanos = System.nanoTime();
        notifyListeners();
    }

    public GameResult result() {
        return result;
    }

    public boolean isOver() {
        return result != null;
    }

    public boolean isPlayerTurn() {
        return mode == Mode.PVP || board.isRedTurn() == playerIsRed;
    }

    /** 是否轮到 AI 走棋（人机模式、未终局、非玩家回合）。 */
    public boolean awaitingAi() {
        return mode == Mode.PVE && result == null && board.isRedTurn() != playerIsRed;
    }

    /**
     * 落子。成功返回 {@code true}，并通知监听者。
     */
    public boolean play(Move move) {
        if (result != null || move == null) {
            return false;
        }
        if (reviewPly >= 0) {
            exitReview();
        }
        if (!board.isLegalMove(move)) {
            return false;
        }
        String notation = Notation.chinese(board, move);
        long elapsed = elapsedSinceTurnStart();
        if (!board.play(move)) {
            return false;
        }
        entries.add(new MoveEntry(move, notation, elapsed));
        redoStack.clear();
        turnStartNanos = System.nanoTime();
        result = board.result();
        notifyListeners();
        return true;
    }

    /** 悔棋。人机模式下会一直退到玩家该走的位置。 */
    public boolean undo() {
        if (!canUndo()) {
            return false;
        }
        if (reviewPly >= 0) {
            exitReview();
        }
        undoOne();
        while (mode == Mode.PVE && !entries.isEmpty() && board.isRedTurn() != playerIsRed) {
            undoOne();
        }
        result = null;
        turnStartNanos = System.nanoTime();
        notifyListeners();
        return true;
    }

    private void undoOne() {
        MoveEntry entry = entries.remove(entries.size() - 1);
        board.undo();
        redoStack.push(entry);
    }

    /** 重做。与悔棋对称，人机模式下一次补回双方各一步。 */
    public boolean redo() {
        if (redoStack.isEmpty() || result != null) {
            return false;
        }
        if (reviewPly >= 0) {
            exitReview();
        }
        redoOne();
        while (mode == Mode.PVE && !redoStack.isEmpty() && board.isRedTurn() != playerIsRed) {
            redoOne();
        }
        turnStartNanos = System.nanoTime();
        notifyListeners();
        return true;
    }

    private void redoOne() {
        MoveEntry entry = redoStack.pop();
        if (!board.play(entry.move())) {
            redoStack.clear();
            return;
        }
        entries.add(entry);
        result = board.result();
    }

    public boolean canUndo() {
        if (entries.isEmpty()) {
            return false;
        }
        // 玩家执黑时，棋盘上只有 AI 的开局那一手，悔掉只会让 AI 再走一遍
        return !(mode == Mode.PVE && !playerIsRed && entries.size() == 1);
    }

    public boolean canRedo() {
        return !redoStack.isEmpty() && result == null;
    }

    public void resign() {
        if (result != null) {
            return;
        }
        boolean redResigns = mode == Mode.PVE ? playerIsRed : board.isRedTurn();
        result = redResigns
                ? GameResult.blackWins(GameResult.Reason.RESIGNATION)
                : GameResult.redWins(GameResult.Reason.RESIGNATION);
        notifyListeners();
    }

    public void agreeDraw() {
        if (result != null) {
            return;
        }
        result = GameResult.draw(GameResult.Reason.AGREEMENT);
        notifyListeners();
    }

    // ==================== 着法列表 ====================

    public List<MoveEntry> entries() {
        return List.copyOf(entries);
    }

    public int plyCount() {
        return entries.size();
    }

    /** 供着法表使用的行数据：每行「回合号 + 红着 + 黑着」。 */
    public List<String[]> moveRows() {
        List<String[]> rows = new ArrayList<>((entries.size() + 1) / 2);
        for (int i = 0; i < entries.size(); i += 2) {
            String black = i + 1 < entries.size() ? entries.get(i + 1).notation() : "";
            rows.add(new String[]{String.valueOf(i / 2 + 1), entries.get(i).notation(), black});
        }
        return rows;
    }

    // ==================== 计时 ====================

    private long elapsedSinceTurnStart() {
        if (reviewPly >= 0) {
            return 0;
        }
        return Math.max(0, (System.nanoTime() - turnStartNanos) / 1_000_000);
    }

    /** 红方累计用时（毫秒）。 */
    public long redMillis() {
        long total = 0;
        for (int i = 0; i < entries.size(); i += 2) {
            total += entries.get(i).elapsedMs();
        }
        return total;
    }

    /** 黑方累计用时（毫秒）。 */
    public long blackMillis() {
        long total = 0;
        for (int i = 1; i < entries.size(); i += 2) {
            total += entries.get(i).elapsedMs();
        }
        return total;
    }

    public static String formatClock(long millis) {
        long totalSeconds = millis / 1000;
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    // ==================== 复盘 ====================

    /** 当前复盘到第几手；{@code -1} 表示实时对局。 */
    public int reviewPly() {
        return reviewPly;
    }

    public boolean reviewing() {
        return reviewPly >= 0;
    }

    public void setReviewPly(int ply) {
        int clamped = Math.max(0, Math.min(ply, entries.size()));
        if (clamped == entries.size()) {
            exitReview();
            return;
        }
        reviewPly = clamped;
        reviewBoard = replay(clamped);
        notifyListeners();
    }

    public void exitReview() {
        if (reviewPly < 0) {
            return;
        }
        reviewPly = -1;
        reviewBoard = null;
        notifyListeners();
    }

    /** 界面应显示的棋盘（复盘时为历史局面）。 */
    public Board displayBoard() {
        return reviewBoard != null ? reviewBoard : board;
    }

    /** 界面应高亮的最后一手。 */
    public Move displayLastMove() {
        int ply = reviewPly >= 0 ? reviewPly : entries.size();
        return ply > 0 ? entries.get(ply - 1).move() : null;
    }

    private Board replay(int plies) {
        Board replay = new Board();
        for (int i = 0; i < plies; i++) {
            if (!replay.play(entries.get(i).move())) {
                break;
            }
        }
        return replay;
    }

    // ==================== 载入棋谱 ====================

    /** 用一组着法替换当前对局（用于载入棋谱）。返回成功应用的步数。 */
    public int loadMoves(List<Move> moves, Mode loadedMode, Difficulty loadedDifficulty, boolean loadedPlayerIsRed) {
        mode = loadedMode;
        difficulty = loadedDifficulty;
        playerIsRed = loadedPlayerIsRed;
        board.reset();
        entries.clear();
        redoStack.clear();
        result = null;
        reviewPly = -1;
        reviewBoard = null;

        int applied = 0;
        for (Move move : moves) {
            if (result != null || !board.isLegalMove(move)) {
                break;
            }
            String notation = Notation.chinese(board, move);
            if (!board.play(move)) {
                break;
            }
            entries.add(new MoveEntry(move, notation, 0));
            result = board.result();
            applied++;
        }
        turnStartNanos = System.nanoTime();
        notifyListeners();
        return applied;
    }

    // ==================== 监听 ====================

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
