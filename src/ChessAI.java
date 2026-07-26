/**
 * AI facade used by the panel. Hard mode can delegate to Pikafish and falls
 * back to the built-in engine whenever the external process is unavailable.
 */
public class ChessAI {
    public enum Difficulty {
        EASY("简单"),
        MEDIUM("中等"),
        HARD("困难");

        private final String displayName;

        Difficulty(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static final long EASY_TIME_MS = 150;
    private static final long MEDIUM_TIME_MS = 2500;
    private static final long HARD_TIME_MS = 8000;

    private Difficulty difficulty;
    private final InternalChessEngine internalEngine;
    private PikafishEngine pikafishEngine;
    private String lastEngineMessage = "内置 AI";
    // 连续失败达到阈值后本会话内跳过 Pikafish，避免每次都白等超时
    private static final int PIKAFISH_MAX_FAILURES = 2;
    private int pikafishFailures = 0;
    // 覆盖思考时间（毫秒，>0 时生效），用于提示功能缩短等待
    private long timeLimitOverrideMs = -1;

    public ChessAI(Difficulty difficulty) {
        this.difficulty = difficulty;
        this.internalEngine = new InternalChessEngine(difficulty);
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
        internalEngine.setDifficulty(difficulty);
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setPikafishEnginePath(String enginePath) {
        // 替换前先销毁旧引擎进程，避免子进程泄漏
        if (pikafishEngine != null) {
            pikafishEngine.shutdown();
            pikafishEngine = null;
        }
        pikafishFailures = 0;
        if (enginePath == null || enginePath.isBlank()) {
            lastEngineMessage = "未配置 Pikafish，使用内置 AI";
        } else {
            pikafishEngine = new PikafishEngine(enginePath);
            lastEngineMessage = "Pikafish 已配置";
        }
    }

    public String getPikafishEnginePath() {
        return pikafishEngine == null ? "" : pikafishEngine.getEnginePath();
    }

    public String getLastEngineMessage() {
        return lastEngineMessage;
    }

    /**
     * 销毁外部引擎进程（游戏退出时调用）。
     */
    public void shutdown() {
        if (pikafishEngine != null) {
            pikafishEngine.shutdown();
        }
    }

    public int[] getNextMove(ChessBoard board, boolean aiIsRed) {
        Move move = findBestMove(board, aiIsRed);
        return move == null ? null : move.toArray();
    }

    /**
     * 覆盖思考时间（提示功能用较短预算），传入 -1 恢复按难度默认
     */
    public void setTimeLimitOverride(long ms) {
        this.timeLimitOverrideMs = ms;
    }

    public Move findBestMove(ChessBoard board, boolean aiIsRed) {
        long timeMs = getThinkTimeMs();
        // 在克隆棋盘上校验与计算，避免 AI 后台线程与界面线程并发读写对局棋盘
        ChessBoard work = board.clone();

        Move move = null;
        if (difficulty == Difficulty.HARD && pikafishEngine != null && pikafishFailures < PIKAFISH_MAX_FAILURES) {
            try {
                Move candidate = pikafishEngine.findBestMove(work, aiIsRed, timeMs);
                if (isMoveForSide(work, candidate, aiIsRed) && work.isLegalMove(candidate)) {
                    lastEngineMessage = "Pikafish";
                    move = candidate;
                    pikafishFailures = 0;
                } else {
                    pikafishFailures++;
                    lastEngineMessage = "Pikafish 返回非法走法，已回退内置 AI";
                }
            } catch (Exception e) {
                pikafishFailures++;
                lastEngineMessage = "Pikafish 不可用，已回退内置 AI: " + e.getMessage();
            }
        } else if (difficulty == Difficulty.HARD && pikafishEngine != null) {
            lastEngineMessage = "Pikafish 连续失败，改用内置 AI";
        } else {
            lastEngineMessage = "内置 AI";
        }

        if (move == null) {
            try {
                Move candidate = internalEngine.findBestMove(work, aiIsRed, timeMs);
                if (candidate != null && isMoveForSide(work, candidate, aiIsRed) && work.isLegalMove(candidate)) {
                    move = candidate;
                }
            } catch (Exception e) {
                lastEngineMessage = "内置 AI 计算失败: " + e.getMessage();
            }
        }

        // 兜底：不允许主动走出长将（长将作负），否则换不判负的合法着
        if (move != null && causesSelfPerpetualLoss(work, move, aiIsRed)) {
            move = pickMoveAvoidingPerpetualLoss(work, aiIsRed, move);
        }
        return move;
    }

    /**
     * 试走该着是否立即导致己方长将作负（同一局面第三次出现且己方步步将军）。
     */
    private boolean causesSelfPerpetualLoss(ChessBoard work, Move move, boolean aiIsRed) {
        if (!work.movePiece(move.fromRow, move.fromCol, move.toRow, move.toCol)) {
            return false; // 非法走法由上层校验处理
        }
        ChessBoard.RepetitionOutcome outcome = work.getRepetitionOutcome();
        work.undo();
        return aiIsRed ? outcome == ChessBoard.RepetitionOutcome.RED_LOSES
                       : outcome == ChessBoard.RepetitionOutcome.BLACK_LOSES;
    }

    /**
     * 原着法会立即长将判负时，改选不判负的合法着；全都判负（理论极端）则维持原着。
     */
    private Move pickMoveAvoidingPerpetualLoss(ChessBoard work, boolean aiIsRed, Move fallback) {
        for (Move m : work.getLegalMoves(aiIsRed)) {
            if (!causesSelfPerpetualLoss(work, m, aiIsRed)) {
                return m;
            }
        }
        return fallback;
    }

    private long getThinkTimeMs() {
        if (timeLimitOverrideMs > 0) {
            return timeLimitOverrideMs;
        }
        return switch (difficulty) {
            case EASY -> EASY_TIME_MS;
            case MEDIUM -> MEDIUM_TIME_MS;
            case HARD -> HARD_TIME_MS;
        };
    }

    private boolean isMoveForSide(ChessBoard board, Move move, boolean aiIsRed) {
        if (move == null) return false;
        ChessPiece piece = board.getPiece(move.fromRow, move.fromCol);
        return piece != null && piece.isRed() == aiIsRed;
    }
}
