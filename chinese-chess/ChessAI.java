import java.util.*;

/**
 * 象棋AI类
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
    
    private int[] getEasyMove(ChessBoard board) {
        List<int[]> moves = getAllMoves(board, false);
        if (moves.isEmpty()) return null;
        
        List<int[]> captures = new ArrayList<>();
        List<int[]> normals = new ArrayList<>();
        
        for (int[] m : moves) {
            (board.getPiece(m[2], m[3]) != null ? captures : normals).add(m);
        }
        
        if (!captures.isEmpty() && random.nextDouble() < 0.7) {
            return captures.get(random.nextInt(captures.size()));
        }
        return normals.isEmpty() ? moves.get(random.nextInt(moves.size())) 
                                 : normals.get(random.nextInt(normals.size()));
    }
    
    private int[] getMediumMove(ChessBoard board) {
        List<int[]> moves = getAllMoves(board, false);
        if (moves.isEmpty()) return null;
        
        int bestScore = Integer.MIN_VALUE;
        for (int[] m : moves) {
            bestScore = Math.max(bestScore, evaluateMove(board, m));
        }
        
        List<int[]> topMoves = new ArrayList<>();
        for (int[] m : moves) {
            if (evaluateMove(board, m) >= bestScore - 50) topMoves.add(m);
        }
        return topMoves.get(random.nextInt(topMoves.size()));
    }
    
    private int[] getHardMove(ChessBoard board) {
        List<int[]> moves = getAllMoves(board, false);
        if (moves.isEmpty()) return null;
        
        int[] bestMove = null;
        int bestScore = Integer.MIN_VALUE;
        
        for (int[] m : moves) {
            ChessBoard sim = board.clone();
            sim.movePiece(m[0], m[1], m[2], m[3]);
            int score = minimax(sim, 2, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
            if (score > bestScore) {
                bestScore = score;
                bestMove = m;
            }
        }
        return bestMove != null ? bestMove : moves.get(0);
    }
    
    private int minimax(ChessBoard board, int depth, int alpha, int beta, boolean isMax) {
        if (depth == 0 || board.isGameOver()) return evaluateBoard(board);
        
        List<int[]> moves = getAllMoves(board, board.isRedTurn());
        if (moves.isEmpty()) return evaluateBoard(board);
        
        if (isMax) {
            int maxScore = Integer.MIN_VALUE;
            for (int[] m : moves) {
                ChessBoard sim = board.clone();
                sim.movePiece(m[0], m[1], m[2], m[3]);
                maxScore = Math.max(maxScore, minimax(sim, depth - 1, alpha, beta, false));
                alpha = Math.max(alpha, maxScore);
                if (beta <= alpha) break;
            }
            return maxScore;
        } else {
            int minScore = Integer.MAX_VALUE;
            for (int[] m : moves) {
                ChessBoard sim = board.clone();
                sim.movePiece(m[0], m[1], m[2], m[3]);
                minScore = Math.min(minScore, minimax(sim, depth - 1, alpha, beta, true));
                beta = Math.min(beta, minScore);
                if (beta <= alpha) break;
            }
            return minScore;
        }
    }
    
    private int evaluateMove(ChessBoard board, int[] move) {
        ChessBoard sim = board.clone();
        sim.movePiece(move[0], move[1], move[2], move[3]);
        return evaluateBoard(sim);
    }
    
    private int evaluateBoard(ChessBoard board) {
        int score = 0;
        for (int r = 0; r < ChessBoard.ROWS; r++) {
            for (int c = 0; c < ChessBoard.COLS; c++) {
                ChessPiece p = board.getPiece(r, c);
                if (p != null) {
                    int v = getValue(p, r, c);
                    score += p.isRed() ? -v : v;
                }
            }
        }
        return score;
    }
    
    private int getValue(ChessPiece p, int r, int c) {
        int v = VALUES.getOrDefault(p.getType(), 0);
        if (p.getType() == ChessPiece.Type.PAWN) {
            v += p.isRed() ? PAWN_BONUS[r][c] : PAWN_BONUS[9 - r][c];
        } else if (p.getType() == ChessPiece.Type.ROOK) {
            v += 10;
        } else if (p.getType() == ChessPiece.Type.KING && c >= 3 && c <= 5) {
            v += 50;
        }
        return v;
    }
    
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
