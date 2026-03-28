import java.util.*;

/**
 * AI 类
 * 实现人机对战的 AI，支持三种难度
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
    
    private Difficulty difficulty;
    private final Random random = new Random();
    
    // 棋子基础价值
    private static final Map<ChessPiece.Type, Integer> PIECE_VALUE = Map.of(
        ChessPiece.Type.KING, 100000,
        ChessPiece.Type.ROOK, 900,
        ChessPiece.Type.CANNON, 500,
        ChessPiece.Type.HORSE, 450,
        ChessPiece.Type.ELEPHANT, 200,
        ChessPiece.Type.ADVISOR, 200,
        ChessPiece.Type.PAWN, 100
    );
    
    // 兵/卒位置价值表（红方视角，需要翻转给黑方用）
    private static final int[][] PAWN_PST_RED = {
        {0, 0, 0, 0, 0, 0, 0, 0, 0},
        {0, 0, 0, 0, 0, 0, 0, 0, 0},
        {0, 0, 0, 0, 0, 0, 0, 0, 0},
        {0, 0, 0, 0, 0, 0, 0, 0, 0},
        {5, 5, 5, 10, 10, 10, 5, 5, 5},
        {10, 10, 15, 20, 25, 20, 15, 10, 10},
        {15, 15, 25, 35, 45, 35, 25, 15, 15},
        {25, 35, 45, 55, 65, 55, 45, 35, 25},
        {35, 45, 55, 65, 75, 65, 55, 45, 35},
        {45, 55, 65, 75, 85, 75, 65, 55, 45}
    };
    
    public ChessAI(Difficulty difficulty) {
        this.difficulty = difficulty;
    }
    
    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }
    
    public Difficulty getDifficulty() {
        return difficulty;
    }
    
    /**
     * 获取 AI 的下一步走法
     * @return int[]{fromRow, fromCol, toRow, toCol} 或 null
     */
    public int[] getNextMove(ChessBoard board) {
        return switch (difficulty) {
            case EASY -> getEasyMove(board);
            case MEDIUM -> getMediumMove(board);
            case HARD -> getHardMove(board);
        };
    }
    
    /**
     * 简单难度：随机走法，优先吃子
     */
    private int[] getEasyMove(ChessBoard board) {
        List<int[]> allMoves = getAllValidMoves(board, false);  // AI 是黑方
        if (allMoves.isEmpty()) return null;
        
        // 分离吃子和非吃子走法
        List<int[]> captureMoves = new ArrayList<>();
        List<int[]> normalMoves = new ArrayList<>();
        
        for (int[] move : allMoves) {
            ChessPiece target = board.getPiece(move[2], move[3]);
            if (target != null) {
                captureMoves.add(move);
            } else {
                normalMoves.add(move);
            }
        }
        
        // 80% 概率吃子（如果有吃子走法）
        if (!captureMoves.isEmpty() && random.nextDouble() < 0.8) {
            return captureMoves.get(random.nextInt(captureMoves.size()));
        }
        
        // 否则随机走
        if (!normalMoves.isEmpty()) {
            // 优先向前走
            List<int[]> forwardMoves = new ArrayList<>();
            for (int[] move : normalMoves) {
                if (move[2] > move[0]) {  // 黑方向下走
                    forwardMoves.add(move);
                }
            }
            if (!forwardMoves.isEmpty()) {
                return forwardMoves.get(random.nextInt(forwardMoves.size()));
            }
            return normalMoves.get(random.nextInt(normalMoves.size()));
        }
        
        return allMoves.get(random.nextInt(allMoves.size()));
    }
    
    /**
     * 中等难度：基于局面评估，一层搜索
     */
    private int[] getMediumMove(ChessBoard board) {
        List<int[]> allMoves = getAllValidMoves(board, false);
        if (allMoves.isEmpty()) return null;
        
        int bestScore = Integer.MIN_VALUE;
        List<int[]> bestMoves = new ArrayList<>();
        
        for (int[] move : allMoves) {
            int score = evaluateMove(board, move, 1);
            if (score > bestScore) {
                bestScore = score;
                bestMoves.clear();
                bestMoves.add(move);
            } else if (score == bestScore) {
                bestMoves.add(move);
            }
        }
        
        return bestMoves.get(random.nextInt(bestMoves.size()));
    }
    
    /**
     * 困难难度：Minimax + Alpha-Beta 剪枝，3层搜索
     */
    private int[] getHardMove(ChessBoard board) {
        List<int[]> allMoves = getAllValidMoves(board, false);
        if (allMoves.isEmpty()) return null;
        
        // 按走法质量排序，提高剪枝效率
        allMoves.sort((a, b) -> {
            int scoreA = quickScoreMove(board, a);
            int scoreB = quickScoreMove(board, b);
            return Integer.compare(scoreB, scoreA);
        });
        
        int bestScore = Integer.MIN_VALUE;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;
        List<int[]> bestMoves = new ArrayList<>();
        
        for (int[] move : allMoves) {
            ChessBoard testBoard = board.clone();
            testBoard.movePiece(move[0], move[1], move[2], move[3]);
            
            int score = -negamax(testBoard, 2, -beta, -alpha, true);
            
            if (score > bestScore) {
                bestScore = score;
                bestMoves.clear();
                bestMoves.add(move);
            } else if (score == bestScore) {
                bestMoves.add(move);
            }
            
            alpha = Math.max(alpha, score);
        }
        
        return bestMoves.get(random.nextInt(bestMoves.size()));
    }
    
    /**
     * Negamax 搜索 + Alpha-Beta 剪枝
     */
    private int negamax(ChessBoard board, int depth, int alpha, int beta, boolean isRed) {
        if (depth == 0 || board.isGameOver()) {
            return evaluateBoard(board, isRed);
        }
        
        List<int[]> moves = getAllValidMoves(board, isRed);
        if (moves.isEmpty()) {
            return isRed ? -50000 : 50000;
        }
        
        // 移动排序，优先尝试好走法
        moves.sort((a, b) -> {
            int scoreA = quickScoreMove(board, a);
            int scoreB = quickScoreMove(board, b);
            return Integer.compare(scoreB, scoreA);
        });
        
        int maxScore = Integer.MIN_VALUE;
        
        for (int[] move : moves) {
            ChessBoard testBoard = board.clone();
            testBoard.movePiece(move[0], move[1], move[2], move[3]);
            
            int score = -negamax(testBoard, depth - 1, -beta, -alpha, !isRed);
            
            maxScore = Math.max(maxScore, score);
            alpha = Math.max(alpha, score);
            
            if (alpha >= beta) {
                break;  // Alpha-Beta 剪枝
            }
        }
        
        return maxScore;
    }
    
    /**
     * 快速评估单个走法（用于排序）
     */
    private int quickScoreMove(ChessBoard board, int[] move) {
        int score = 0;
        ChessPiece target = board.getPiece(move[2], move[3]);
        if (target != null) {
            score += PIECE_VALUE.get(target.getType()) * 10;
        }
        return score;
    }
    
    /**
     * 评估走法（用于中等难度）
     */
    private int evaluateMove(ChessBoard board, int[] move, int depth) {
        ChessBoard testBoard = board.clone();
        testBoard.movePiece(move[0], move[1], move[2], move[3]);
        
        int score = evaluateBoard(testBoard, false);
        
        // 如果会导致自己被将军，扣分
        if (testBoard.isKingAttacked(false)) {
            score -= 1000;
        }
        
        // 如果能让对方被将军，加分
        if (testBoard.isKingAttacked(true)) {
            score += 500;
        }
        
        return score;
    }
    
    /**
     * 局面评估函数
     * 从黑方视角评估（正数表示黑方优势）
     */
    private int evaluateBoard(ChessBoard board, boolean isRed) {
        int score = 0;
        
        for (ChessPiece piece : board.getPieces()) {
            int value = PIECE_VALUE.get(piece.getType());
            
            // 位置奖励
            if (piece.getType() == ChessPiece.Type.PAWN) {
                if (piece.isRed()) {
                    value += PAWN_PST_RED[9 - piece.getRow()][piece.getCol()];
                } else {
                    value += PAWN_PST_RED[piece.getRow()][piece.getCol()];
                }
            }
            
            // 前进奖励
            if (piece.getType() == ChessPiece.Type.PAWN || 
                piece.getType() == ChessPiece.Type.HORSE ||
                piece.getType() == ChessPiece.Type.CANNON) {
                if (piece.isRed()) {
                    value += (9 - piece.getRow()) * 2;
                } else {
                    value += piece.getRow() * 2;
                }
            }
            
            // 机动性奖励
            int mobility = countMobility(board, piece);
            value += mobility * 5;
            
            if (piece.isRed()) {
                score -= value;
            } else {
                score += value;
            }
        }
        
        // 将军奖励/惩罚
        if (board.isKingAttacked(true)) {
            score += 300;
            if (board.isCheckmate(true)) {
                score += 100000;
            }
        }
        if (board.isKingAttacked(false)) {
            score -= 300;
            if (board.isCheckmate(false)) {
                score -= 100000;
            }
        }
        
        return isRed ? -score : score;
    }
    
    /**
     * 计算棋子的机动性（可移动的位置数）
     */
    private int countMobility(ChessBoard board, ChessPiece piece) {
        int count = 0;
        for (int row = 0; row < ChessBoard.ROWS; row++) {
            for (int col = 0; col < ChessBoard.COLS; col++) {
                if (board.canMove(piece.getRow(), piece.getCol(), row, col)) {
                    count++;
                }
            }
        }
        return count;
    }
    
    /**
     * 获取某方所有合法走法
     */
    private List<int[]> getAllValidMoves(ChessBoard board, boolean isRed) {
        List<int[]> moves = new ArrayList<>();
        
        for (ChessPiece piece : board.getPieces()) {
            if (piece.isRed() != isRed) continue;
            
            for (int row = 0; row < ChessBoard.ROWS; row++) {
                for (int col = 0; col < ChessBoard.COLS; col++) {
                    if (board.canMove(piece.getRow(), piece.getCol(), row, col)) {
                        moves.add(new int[]{piece.getRow(), piece.getCol(), row, col});
                    }
                }
            }
        }
        
        return moves;
    }
}
