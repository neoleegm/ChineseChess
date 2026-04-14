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
    private static final long MEDIUM_TIME_MS = 500;
    private static final long HARD_TIME_MS = 1200;
    
    private Difficulty difficulty;
    private final InternalChessEngine internalEngine;
    private PikafishEngine pikafishEngine;
    private String lastEngineMessage = "内置 AI";
    
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
        if (enginePath == null || enginePath.isBlank()) {
            pikafishEngine = null;
            lastEngineMessage = "未配置 Pikafish，使用内置 AI";
        } else {
            pikafishEngine = new PikafishEngine(enginePath);
            lastEngineMessage = "Pikafish 已配置";
        }
    }

    public String getPikafishEnginePath() {
        return pikafishEngine == null ? "" : pikafishEngine.getEnginePath();
    }

    public boolean hasPikafishEngine() {
        return pikafishEngine != null;
    }

    public String getLastEngineMessage() {
        return lastEngineMessage;
    }
    
    /**
     * 获取 AI 的下一步走法。兼容旧调用：默认 AI 执黑。
     * @return int[]{fromRow, fromCol, toRow, toCol} 或 null
     */
    public int[] getNextMove(ChessBoard board) {
        return getNextMove(board, false);
    }

    public int[] getNextMove(ChessBoard board, boolean aiIsRed) {
        Move move = findBestMove(board, aiIsRed);
        return move == null ? null : move.toArray();
    }

    public Move findBestMove(ChessBoard board, boolean aiIsRed) {
        long timeMs = getThinkTimeMs();

        if (difficulty == Difficulty.HARD && pikafishEngine != null) {
            try {
                Move move = pikafishEngine.findBestMove(board, aiIsRed, timeMs);
                if (isMoveForSide(board, move, aiIsRed) && board.isLegalMove(move)) {
                    lastEngineMessage = "Pikafish";
                    return move;
                }
                lastEngineMessage = "Pikafish 返回非法走法，已回退内置 AI";
            } catch (Exception e) {
                lastEngineMessage = "Pikafish 不可用，已回退内置 AI: " + e.getMessage();
            }
        } else {
            lastEngineMessage = "内置 AI";
        }

        try {
            Move move = internalEngine.findBestMove(board, aiIsRed, timeMs);
            if (move != null && isMoveForSide(board, move, aiIsRed) && board.isLegalMove(move)) {
                return move;
            }
        } catch (Exception e) {
            lastEngineMessage = "内置 AI 计算失败: " + e.getMessage();
        }
        return null;
    }

    private long getThinkTimeMs() {
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
