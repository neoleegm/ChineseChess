import java.util.*;

/**
 * 象棋AI类 - 优化版
 */
public class ChessAI {
    public enum Difficulty { EASY, MEDIUM, HARD }
    
    private Difficulty difficulty;
    private Random random = new Random();
    
    private static final Map<ChessPiece.Type, Integer> VALUES = Map.of(
        ChessPiece.Type.KING, 10000,
        ChessPiece.Type.ROOK, 900,
        ChessPiece.Type.HORSE, 400,
        ChessPiece.Type.CANNON, 450,
        ChessPiece.Type.ELEPHANT, 200,
        ChessPiece.Type.ADVISOR, 200,
        ChessPiece.Type.PAWN, 100
    );
    
    // 兵卒位置价值表（越靠近对方底线价值越高）
    private static final int[][] PAWN_BONUS = {
        {0,0,0,0,0,0,0,0,0}, {0,0,0,0,0,0,0,0,0}, {0,0,0,0,0,0,0,0,0}, {0,0,0,0,0,0,0,0,0}, {0,0,0,0,0,0,0,0,0},
        {20,20,20,30,40,30,20,20,20}, {30,30,40,50,60,50,40,30,30},
        {40,50,60,70,80,70,60,50,40}, {50,60,70,80,90,80,70,60,50},
        {60,70,80,90,100,90,80,70,60}
    };
    
    public ChessAI(Difficulty difficulty) { this.difficulty = difficulty; }
    public void setDifficulty(Difficulty d) { this.difficulty = d; }
    public Difficulty getDifficulty() { return difficulty; }
    
    public int[] getNextMove(ChessBoard board) {
        return switch (difficulty) {
            case EASY -> getEasyMove(board);
            case MEDIUM -> getMediumMove(board);
            case HARD -> getHardMove(board);
        };
    }
    
    // 简单：优先吃子，随机走
    private int[] getEasyMove(ChessBoard board) {
        List<int[]> moves = getAllMoves(board, false);
        if (moves.isEmpty()) return null;
        
        List<int[]> captures = new ArrayList<>();
        List<int[]> goodMoves = new ArrayList<>();
        
        for (int[] m : moves) {
            ChessPiece target = board.getPiece(m[2], m[3]);
            if (target != null) {
                captures.add(m);
            } else if (isGoodPosition(m)) {
                goodMoves.add(m);
            }
        }
        
        // 80%吃子，20%走好位置
        if (!captures.isEmpty() && random.nextDouble() < 0.8) {
            return captures.get(random.nextInt(captures.size()));
        }
        if (!goodMoves.isEmpty()) {
            return goodMoves.get(random.nextInt(goodMoves.size()));
        }
        return moves.get(random.nextInt(moves.size()));
    }
    
    // 中等：评估局面选择最优
    private int[] getMediumMove(ChessBoard board) {
        List<int[]> moves = getAllMoves(board, false);
        if (moves.isEmpty()) return null;
        
        int bestScore = Integer.MIN_VALUE;
        List<int[]> bestMoves = new ArrayList<>();
        
        for (int[] m : moves) {
            int score = evaluateQuick(board, m);
            if (score > bestScore) {
                bestScore = score;
                bestMoves.clear();
                bestMoves.add(m);
            } else if (score == bestScore) {
                bestMoves.add(m);
            }
        }
        return bestMoves.get(random.nextInt(bestMoves.size()));
    }
    
    // 困难：评估更细致，考虑对方回应
    private int[] getHardMove(ChessBoard board) {
        List<int[]> moves = getAllMoves(board, false);
        if (moves.isEmpty()) return null;
        
        int bestScore = Integer.MIN_VALUE;
        List<int[]> bestMoves = new ArrayList<>();
        
        for (int[] m : moves) {
            // 快速评估走法
            int score = evaluateQuick(board, m);
            
            // 如果是吃子，额外加分
            ChessPiece captured = board.getPiece(m[2], m[3]);
            if (captured != null) {
                score += VALUES.getOrDefault(captured.getType(), 0);
            }
            
            // 检查移动后是否会被对方吃掉（简单的安全性检查）
            if (isSafeMove(board, m)) {
                score += 50; // 安全走法加分
            }
            
            if (score > bestScore) {
                bestScore = score;
                bestMoves.clear();
                bestMoves.add(m);
            } else if (score == bestScore) {
                bestMoves.add(m);
            }
        }
        return bestMoves.get(random.nextInt(bestMoves.size()));
    }
    
    // 快速评估（不走棋，直接计算）
    private int evaluateQuick(ChessBoard board, int[] move) {
        ChessPiece piece = board.getPiece(move[0], move[1]);
        ChessPiece target = board.getPiece(move[2], move[3]);
        
        int score = 0;
        
        // 吃子价值
        if (target != null) {
            score += VALUES.getOrDefault(target.getType(), 0) * 2;
        }
        
        // 位置价值
        if (piece.getType() == ChessPiece.Type.PAWN) {
            score += piece.isRed() ? PAWN_BONUS[move[2]][move[3]] : PAWN_BONUS[9 - move[2]][move[3]];
        }
        
        // 控制中心加分
        if (move[2] >= 3 && move[2] <= 6 && move[3] >= 2 && move[3] <= 6) {
            score += 30;
        }
        
        // 前进加分（黑方向下，行号增加）
        score += (move[2] - move[0]) * 10;
        
        return score;
    }
    
    // 检查是否为安全位置（简单检查）
    private boolean isSafeMove(ChessBoard board, int[] move) {
        // 检查目标位置是否会被对方任何棋子攻击
        // 简化：检查周围是否有对方棋子
        for (int dr = -2; dr <= 2; dr++) {
            for (int dc = -2; dc <= 2; dc++) {
                int r = move[2] + dr, c = move[3] + dc;
                if (r >= 0 && r < 10 && c >= 0 && c < 9) {
                    ChessPiece p = board.getPiece(r, c);
                    if (p != null && p.isRed()) { // 红方棋子
                        // 简化检查：不实际计算能否攻击
                        if (Math.abs(dr) <= 1 && Math.abs(dc) <= 1) {
                            return false; // 附近有对方棋子，认为不安全
                        }
                    }
                }
            }
        }
        return true;
    }
    
    // 判断是否为好位置
    private boolean isGoodPosition(int[] move) {
        // 向前的走法更好
        return move[2] > move[0];
    }
    
    // 获取所有合法走法
    private List<int[]> getAllMoves(ChessBoard board, boolean isRed) {
        List<int[]> moves = new ArrayList<>();
        for (int fr = 0; fr < ChessBoard.ROWS; fr++) {
            for (int fc = 0; fc < ChessBoard.COLS; fc++) {
                ChessPiece p = board.getPiece(fr, fc);
                if (p != null && p.isRed() == isRed) {
                    for (int tr = 0; tr < ChessBoard.ROWS; tr++) {
                        for (int tc = 0; tc < ChessBoard.COLS; tc++) {
                            if (board.canMove(fr, fc, tr, tc)) {
                                moves.add(new int[]{fr, fc, tr, tc});
                            }
                        }
                    }
                }
            }
        }
        return moves;
    }
}
