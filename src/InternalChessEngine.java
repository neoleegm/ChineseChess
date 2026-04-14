import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Built-in engine used for easy/medium play and as the hard-mode fallback.
 */
public class InternalChessEngine implements Engine {
    private static final int INF = 1_000_000_000;
    private static final int MATE_SCORE = 10_000_000;
    private static final int MAX_DEPTH = 6;
    private static final int QUIESCENCE_DEPTH = 4;

    private static final Map<ChessPiece.Type, Integer> PIECE_VALUE = Map.of(
        ChessPiece.Type.KING, 100000,
        ChessPiece.Type.ROOK, 900,
        ChessPiece.Type.CANNON, 500,
        ChessPiece.Type.HORSE, 450,
        ChessPiece.Type.ELEPHANT, 200,
        ChessPiece.Type.ADVISOR, 200,
        ChessPiece.Type.PAWN, 100
    );

    private static final int[][] PAWN_PST = {
        {0, 0, 0, 0, 0, 0, 0, 0, 0},
        {0, 0, 0, 0, 0, 0, 0, 0, 0},
        {2, 2, 4, 6, 8, 6, 4, 2, 2},
        {6, 6, 10, 14, 18, 14, 10, 6, 6},
        {12, 14, 18, 24, 30, 24, 18, 14, 12},
        {20, 24, 30, 38, 48, 38, 30, 24, 20},
        {30, 34, 42, 54, 66, 54, 42, 34, 30},
        {40, 48, 58, 70, 84, 70, 58, 48, 40},
        {48, 58, 68, 82, 96, 82, 68, 58, 48},
        {54, 64, 74, 88, 104, 88, 74, 64, 54}
    };

    private static final int[][] HORSE_PST = {
        {-20, -8, 0, 4, 6, 4, 0, -8, -20},
        {-8, 4, 12, 16, 18, 16, 12, 4, -8},
        {0, 12, 22, 28, 32, 28, 22, 12, 0},
        {6, 18, 30, 38, 42, 38, 30, 18, 6},
        {8, 22, 36, 44, 50, 44, 36, 22, 8},
        {10, 24, 38, 48, 54, 48, 38, 24, 10},
        {8, 22, 34, 42, 48, 42, 34, 22, 8},
        {4, 14, 24, 30, 34, 30, 24, 14, 4},
        {-6, 6, 14, 18, 20, 18, 14, 6, -6},
        {-16, -6, 0, 4, 6, 4, 0, -6, -16}
    };

    private static final int[][] ROOK_PST = {
        {0, 4, 8, 10, 12, 10, 8, 4, 0},
        {4, 8, 12, 14, 16, 14, 12, 8, 4},
        {8, 12, 16, 18, 20, 18, 16, 12, 8},
        {10, 16, 20, 24, 26, 24, 20, 16, 10},
        {12, 18, 24, 28, 32, 28, 24, 18, 12},
        {12, 18, 24, 28, 32, 28, 24, 18, 12},
        {10, 16, 20, 24, 26, 24, 20, 16, 10},
        {8, 12, 16, 18, 20, 18, 16, 12, 8},
        {4, 8, 12, 14, 16, 14, 12, 8, 4},
        {0, 4, 8, 10, 12, 10, 8, 4, 0}
    };

    private static final int[][] CANNON_PST = {
        {-6, 0, 4, 8, 10, 8, 4, 0, -6},
        {0, 6, 10, 14, 18, 14, 10, 6, 0},
        {4, 10, 16, 22, 28, 22, 16, 10, 4},
        {8, 14, 22, 30, 36, 30, 22, 14, 8},
        {8, 16, 24, 32, 38, 32, 24, 16, 8},
        {8, 16, 24, 32, 38, 32, 24, 16, 8},
        {6, 12, 20, 26, 30, 26, 20, 12, 6},
        {2, 8, 14, 18, 22, 18, 14, 8, 2},
        {-4, 2, 8, 10, 12, 10, 8, 2, -4},
        {-8, -2, 2, 4, 6, 4, 2, -2, -8}
    };

    private final Random random = new Random();
    private ChessAI.Difficulty difficulty;

    public InternalChessEngine(ChessAI.Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public void setDifficulty(ChessAI.Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    @Override
    public Move findBestMove(ChessBoard board, boolean aiIsRed, long timeMs) {
        ChessBoard searchBoard = board.clone();
        return switch (difficulty) {
            case EASY -> findEasyMove(searchBoard, aiIsRed);
            case MEDIUM -> findMediumMove(searchBoard, aiIsRed);
            case HARD -> findHardMove(searchBoard, aiIsRed, timeMs);
        };
    }

    private Move findEasyMove(ChessBoard board, boolean aiIsRed) {
        List<Move> legalMoves = board.getLegalMoves(aiIsRed);
        if (legalMoves.isEmpty()) return null;

        List<Move> captures = new ArrayList<>();
        List<Move> quietMoves = new ArrayList<>();
        for (Move move : legalMoves) {
            if (board.getPiece(move.toRow, move.toCol) == null) {
                quietMoves.add(move);
            } else {
                captures.add(move);
            }
        }

        if (!captures.isEmpty() && random.nextDouble() < 0.75) {
            return captures.get(random.nextInt(captures.size()));
        }
        if (!quietMoves.isEmpty()) {
            return quietMoves.get(random.nextInt(quietMoves.size()));
        }
        return legalMoves.get(random.nextInt(legalMoves.size()));
    }

    private Move findMediumMove(ChessBoard board, boolean aiIsRed) {
        List<Move> legalMoves = board.getLegalMoves(aiIsRed);
        if (legalMoves.isEmpty()) return null;

        int bestScore = Integer.MIN_VALUE;
        List<Move> bestMoves = new ArrayList<>();
        sortMoves(board, legalMoves);

        for (Move move : legalMoves) {
            ChessBoard.MoveRecord record = board.makeMove(move);
            int score = evaluate(board, aiIsRed);
            if (board.isKingAttacked(!aiIsRed)) {
                score += 350;
            }
            if (board.getLegalMoves(!aiIsRed).isEmpty()) {
                score += MATE_SCORE / 4;
            }
            board.undoMove(record);

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

    private Move findHardMove(ChessBoard board, boolean aiIsRed, long timeMs) {
        List<Move> legalMoves = board.getLegalMoves(aiIsRed);
        if (legalMoves.isEmpty()) return null;

        Move bestMove = legalMoves.get(0);
        long budgetMs = Math.max(250, timeMs);
        SearchContext context = new SearchContext(System.nanoTime() + budgetMs * 1_000_000L);

        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            SearchResult result = searchRoot(board, aiIsRed, depth, context);
            if (context.timedOut) {
                break;
            }
            if (result.move != null) {
                bestMove = result.move;
            }
        }

        return bestMove;
    }

    private SearchResult searchRoot(ChessBoard board, boolean sideToMove, int depth, SearchContext context) {
        List<Move> moves = board.getLegalMoves(sideToMove);
        sortMoves(board, moves);

        Move bestMove = null;
        int bestScore = -INF;
        int alpha = -INF;
        int beta = INF;

        for (Move move : moves) {
            if (context.isTimedOut()) break;

            ChessBoard.MoveRecord record = board.makeMove(move);
            int score = -negamax(board, depth - 1, -beta, -alpha, !sideToMove, context, 1);
            board.undoMove(record);

            if (context.timedOut) break;

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            alpha = Math.max(alpha, score);
        }

        return new SearchResult(bestMove, bestScore);
    }

    private int negamax(ChessBoard board, int depth, int alpha, int beta,
                        boolean sideToMove, SearchContext context, int ply) {
        if (context.isTimedOut()) {
            return evaluate(board, sideToMove);
        }

        List<Move> moves = board.getLegalMoves(sideToMove);
        if (moves.isEmpty()) {
            return -MATE_SCORE + ply;
        }

        if (depth == 0) {
            return quiescence(board, alpha, beta, sideToMove, context, QUIESCENCE_DEPTH, ply);
        }

        sortMoves(board, moves);
        int bestScore = -INF;

        for (Move move : moves) {
            ChessBoard.MoveRecord record = board.makeMove(move);
            int score = -negamax(board, depth - 1, -beta, -alpha, !sideToMove, context, ply + 1);
            board.undoMove(record);

            if (context.timedOut) {
                return evaluate(board, sideToMove);
            }
            bestScore = Math.max(bestScore, score);
            alpha = Math.max(alpha, score);
            if (alpha >= beta) {
                break;
            }
        }

        return bestScore;
    }

    private int quiescence(ChessBoard board, int alpha, int beta, boolean sideToMove,
                           SearchContext context, int depth, int ply) {
        if (context.isTimedOut()) {
            return evaluate(board, sideToMove);
        }

        List<Move> legalMoves = board.getLegalMoves(sideToMove);
        if (legalMoves.isEmpty()) {
            return -MATE_SCORE + ply;
        }

        int standPat = evaluate(board, sideToMove);
        if (standPat >= beta) {
            return beta;
        }
        alpha = Math.max(alpha, standPat);
        if (depth == 0) {
            return alpha;
        }

        List<Move> captures = new ArrayList<>();
        for (Move move : legalMoves) {
            if (board.getPiece(move.toRow, move.toCol) != null) {
                captures.add(move);
            }
        }
        sortMoves(board, captures);

        for (Move move : captures) {
            ChessBoard.MoveRecord record = board.makeMove(move);
            int score = -quiescence(board, -beta, -alpha, !sideToMove, context, depth - 1, ply + 1);
            board.undoMove(record);

            if (context.timedOut) {
                return alpha;
            }
            if (score >= beta) {
                return beta;
            }
            alpha = Math.max(alpha, score);
        }

        return alpha;
    }

    private int evaluate(ChessBoard board, boolean perspectiveRed) {
        int score = 0;
        for (ChessPiece piece : board.getPieces()) {
            int value = pieceValue(board, piece);
            score += piece.isRed() == perspectiveRed ? value : -value;
        }

        if (board.isKingAttacked(perspectiveRed)) {
            score -= 700;
        }
        if (board.isKingAttacked(!perspectiveRed)) {
            score += 550;
        }

        List<Move> perspectiveMoves = board.getLegalMoves(perspectiveRed);
        List<Move> opponentMoves = board.getLegalMoves(!perspectiveRed);
        score += (perspectiveMoves.size() - opponentMoves.size()) * 3;
        return score;
    }

    private int pieceValue(ChessBoard board, ChessPiece piece) {
        int value = PIECE_VALUE.get(piece.getType()) + positionBonus(piece);
        value += countMobility(board, piece) * mobilityWeight(piece.getType());

        boolean attacked = board.isSquareAttacked(piece.getRow(), piece.getCol(), !piece.isRed());
        boolean defended = board.isSquareAttacked(piece.getRow(), piece.getCol(), piece.isRed());
        if (attacked) {
            value -= PIECE_VALUE.get(piece.getType()) / 8;
        }
        if (defended) {
            value += PIECE_VALUE.get(piece.getType()) / 25;
        }
        return value;
    }

    private int positionBonus(ChessPiece piece) {
        int row = piece.isRed() ? 9 - piece.getRow() : piece.getRow();
        int col = piece.getCol();
        return switch (piece.getType()) {
            case PAWN -> PAWN_PST[row][col];
            case HORSE -> HORSE_PST[row][col];
            case ROOK -> ROOK_PST[row][col];
            case CANNON -> CANNON_PST[row][col];
            default -> 0;
        };
    }

    private int mobilityWeight(ChessPiece.Type type) {
        return switch (type) {
            case ROOK -> 6;
            case CANNON -> 5;
            case HORSE -> 5;
            case PAWN -> 3;
            default -> 2;
        };
    }

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

    private void sortMoves(ChessBoard board, List<Move> moves) {
        moves.sort(Comparator.comparingInt((Move move) -> quickScoreMove(board, move)).reversed());
    }

    private int quickScoreMove(ChessBoard board, Move move) {
        int score = 0;
        ChessPiece attacker = board.getPiece(move.fromRow, move.fromCol);
        ChessPiece target = board.getPiece(move.toRow, move.toCol);
        if (target != null) {
            score += PIECE_VALUE.get(target.getType()) * 12;
            if (attacker != null) {
                score -= PIECE_VALUE.get(attacker.getType()) / 8;
            }
        }
        score += 12 - Math.abs(move.toCol - 4) * 2;
        score += attacker != null && attacker.getType() == ChessPiece.Type.PAWN
            ? (attacker.isRed() ? move.fromRow - move.toRow : move.toRow - move.fromRow) * 8
            : 0;
        return score;
    }

    private static class SearchContext {
        final long deadlineNanos;
        boolean timedOut;

        SearchContext(long deadlineNanos) {
            this.deadlineNanos = deadlineNanos;
        }

        boolean isTimedOut() {
            if (System.nanoTime() >= deadlineNanos) {
                timedOut = true;
            }
            return timedOut;
        }
    }

    private static class SearchResult {
        final Move move;
        final int score;

        SearchResult(Move move, int score) {
            this.move = move;
            this.score = score;
        }
    }
}
