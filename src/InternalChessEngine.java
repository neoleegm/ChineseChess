import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Built-in engine with advanced search (PVS, TT, NMP, killers, history)
 * and comprehensive evaluation for strong fallback play.
 */
public class InternalChessEngine implements Engine {
    private static final int INF = 1_000_000_000;
    private static final int MATE_SCORE = 10_000_000;
    private static final int MAX_DEPTH = 12;
    private static final int QUIESCENCE_MAX_DEPTH = 8;
    private static final int TT_SIZE = 1 << 20;
    private static final int TT_MASK = TT_SIZE - 1;
    private static final int MAX_PLY = 64;
    private static final byte TT_EXACT = 0;
    private static final byte TT_LOWER = 1;
    private static final byte TT_UPPER = 2;

    private static final int[] PIECE_VALUE = {100000, 220, 220, 480, 950, 520, 110};
    // indices: KING=0, ADVISOR=1, ELEPHANT=2, HORSE=3, ROOK=4, CANNON=5, PAWN=6

    // --- Piece-Square Tables (black perspective, row 0 at top) ---
    private static final int[][][] PST = new int[7][10][9];

    static {
        // PAWN
        int[][] pawn = {
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
        // HORSE
        int[][] horse = {
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
        // ROOK
        int[][] rook = {
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
        // CANNON
        int[][] cannon = {
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
        // KING (palace only)
        int[][] king = {
            {0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0},
            {0,0,0,8,12,8,0,0,0},
            {0,0,0,10,16,10,0,0,0},
            {0,0,0,8,12,8,0,0,0}
        };
        // ADVISOR (palace only)
        int[][] advisor = {
            {0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0},
            {0,0,0,12,0,12,0,0,0},
            {0,0,0,0,18,0,0,0,0},
            {0,0,0,12,0,12,0,0,0}
        };
        // ELEPHANT
        int[][] elephant = {
            {0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0,0},
            {0,0,14,0,0,0,14,0,0},
            {0,0,0,0,0,0,0,0,0},
            {0,0,14,0,0,0,14,0,0},
            {0,0,0,18,0,18,0,0,0}
        };
        PST[0] = king;
        PST[1] = advisor;
        PST[2] = elephant;
        PST[3] = horse;
        PST[4] = rook;
        PST[5] = cannon;
        PST[6] = pawn;
    }

    // --- Transposition Table (parallel arrays) ---
    private final long[] ttKeys = new long[TT_SIZE];
    private final short[] ttDepths = new short[TT_SIZE];
    private final byte[] ttFlags = new byte[TT_SIZE];
    private final int[] ttScores = new int[TT_SIZE];
    private final long[] ttMoves = new long[TT_SIZE];

    // --- Search state ---
    private final Move[][] killerMoves = new Move[MAX_PLY][2];
    private final int[][] historyTable = new int[90][90];
    private final Random random = new Random();
    private ChessAI.Difficulty difficulty;

    private ChessBoard board;
    private boolean aiIsRed;
    private long nodesSearched;
    private SearchContext context;

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

    // ==================== EASY / MEDIUM ====================

    private Move findEasyMove(ChessBoard board, boolean aiIsRed) {
        List<Move> legalMoves = board.getLegalMoves(aiIsRed);
        if (legalMoves.isEmpty()) return null;
        List<Move> captures = new ArrayList<>();
        List<Move> quiet = new ArrayList<>();
        for (Move m : legalMoves) {
            if (board.getPiece(m.toRow, m.toCol) == null) quiet.add(m);
            else captures.add(m);
        }
        if (!captures.isEmpty() && random.nextDouble() < 0.75) {
            return captures.get(random.nextInt(captures.size()));
        }
        if (!quiet.isEmpty()) return quiet.get(random.nextInt(quiet.size()));
        return legalMoves.get(random.nextInt(legalMoves.size()));
    }

    private Move findMediumMove(ChessBoard board, boolean aiIsRed) {
        List<Move> legalMoves = board.getLegalMoves(aiIsRed);
        if (legalMoves.isEmpty()) return null;

        // Medium uses 2-ply negamax + quiescence to avoid blunders
        Move bestMove = legalMoves.get(0);
        int bestScore = -INF;

        for (Move move : legalMoves) {
            ChessBoard.MoveRecord rec = board.makeMove(move);
            int score = -shallowNegamax(board, 2, -INF, -bestScore, !aiIsRed);
            board.undoMove(rec);

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }
        return bestMove;
    }

    private int shallowNegamax(ChessBoard board, int depth, int alpha, int beta, boolean sideToMove) {
        List<Move> moves = board.getLegalMoves(sideToMove);
        if (moves.isEmpty()) {
            return -MATE_SCORE + 10;
        }

        if (depth <= 0) {
            return shallowQuiescence(board, alpha, beta, sideToMove);
        }

        int bestScore = -INF;
        for (Move move : moves) {
            ChessBoard.MoveRecord rec = board.makeMove(move);
            int score = -shallowNegamax(board, depth - 1, -beta, -alpha, !sideToMove);
            board.undoMove(rec);

            if (score > bestScore) bestScore = score;
            if (score > alpha) alpha = score;
            if (alpha >= beta) break;
        }
        return bestScore;
    }

    private int shallowQuiescence(ChessBoard board, int alpha, int beta, boolean sideToMove) {
        int standPat = evaluate(board, sideToMove);
        if (standPat >= beta) return beta;
        if (alpha < standPat) alpha = standPat;

        // Look at captures one ply deeper to avoid obvious blunders
        for (Move move : board.getLegalMoves(sideToMove)) {
            if (board.getPiece(move.toRow, move.toCol) != null) {
                ChessBoard.MoveRecord rec = board.makeMove(move);
                int score = -evaluate(board, !sideToMove);
                board.undoMove(rec);
                if (score >= beta) return beta;
                if (score > alpha) alpha = score;
            }
        }
        return alpha;
    }

    // ==================== HARD SEARCH ====================

    private Move findHardMove(ChessBoard board, boolean aiIsRed, long timeMs) {
        List<Move> legalMoves = board.getLegalMoves(aiIsRed);
        if (legalMoves.isEmpty()) return null;

        this.board = board;
        this.aiIsRed = aiIsRed;
        this.nodesSearched = 0;
        clearKillersAndHistory();

        long budgetMs = Math.max(500, timeMs);
        this.context = new SearchContext(System.nanoTime() + budgetMs * 1_000_000L);

        // 将实际对局历史局面注入搜索路径，避免走回已出现过的局面
        for (java.util.Map.Entry<Long, Integer> e : board.getPositionCounts().entrySet()) {
            for (int i = 0; i < e.getValue(); i++) {
                context.incrementPathCount(e.getKey());
            }
        }

        Move bestMove = legalMoves.get(0);
        int prevScore = 0;

        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            int window = (depth <= 2) ? INF : 60;
            int alpha = prevScore - window;
            int beta = prevScore + window;

            SearchResult result = searchRoot(depth, alpha, beta);

            if (context.timedOut && result.move == null) break;

            if (result.move != null) {
                bestMove = result.move;
                prevScore = result.score;
            }

            // Aspiration window failure: re-search with full window
            if (!context.timedOut && (result.score <= alpha || result.score >= beta) && depth > 2) {
                result = searchRoot(depth, -INF, INF);
                if (!context.timedOut && result.move != null) {
                    bestMove = result.move;
                    prevScore = result.score;
                }
            }

            if (Math.abs(prevScore) > MATE_SCORE / 2) break; // found mate
        }

        return bestMove;
    }

    private SearchResult searchRoot(int depth, int alpha, int beta) {
        List<Move> moves = board.getLegalMoves(aiIsRed);
        sortMoves(moves, 0, probeTTMove(board.getZobristKey()));

        // 把当前根局面加入搜索路径
        long rootKey = board.getZobristKey();
        context.incrementPathCount(rootKey);

        Move bestMove = null;
        int bestScore = -INF;

        for (int i = 0; i < moves.size(); i++) {
            Move move = moves.get(i);
            if (context.isTimedOut()) break;

            ChessBoard.MoveRecord rec = board.makeMove(move);
            int score;
            if (i == 0) {
                score = -pvs(depth - 1, -beta, -alpha, !aiIsRed, 1, true);
            } else {
                score = -pvs(depth - 1, -alpha - 1, -alpha, !aiIsRed, 1, true);
                if (score > alpha && score < beta) {
                    score = -pvs(depth - 1, -beta, -alpha, !aiIsRed, 1, true);
                }
            }
            board.undoMove(rec);

            if (context.timedOut) break;

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            alpha = Math.max(alpha, score);
        }

        context.decrementPathCount(rootKey);

        if (bestMove != null && !context.timedOut) {
            storeTT(board.getZobristKey(), depth, TT_EXACT, bestScore, bestMove);
        }
        return new SearchResult(bestMove, bestScore);
    }

    private int pvs(int depth, int alpha, int beta, boolean sideToMove, int ply, boolean allowNull) {
        if (context.isTimedOut()) return evaluate(board, sideToMove);

        nodesSearched++;

        long key = board.getZobristKey();

        // 搜索路径重复检测：避免长将/长捉
        int pathRep = context.incrementPathCount(key);
        if (pathRep >= 2) {
            context.decrementPathCount(key);
            return 0; // 和棋分数，避免循环
        }

        // 主体有多个提前返回出口，路径计数的配平由 finally 统一保证
        try {
            return pvsImpl(key, depth, alpha, beta, sideToMove, ply, allowNull);
        } finally {
            context.decrementPathCount(key);
        }
    }

    private int pvsImpl(long key, int depth, int alpha, int beta, boolean sideToMove, int ply, boolean allowNull) {
        // TT probe
        Move ttMove = probeTTMove(key);
        TTEntry entry = probeTT(key);
        if (entry != null && entry.depth >= depth) {
            if (entry.flag == TT_EXACT) return entry.score;
            if (entry.flag == TT_LOWER && entry.score >= beta) return entry.score;
            if (entry.flag == TT_UPPER && entry.score <= alpha) return entry.score;
        }

        List<Move> moves = board.getLegalMoves(sideToMove);
        if (moves.isEmpty()) {
            return -MATE_SCORE + ply;
        }

        boolean inCheck = board.isKingAttacked(sideToMove);
        if (inCheck) depth++;

        if (depth <= 0) {
            return quiescence(alpha, beta, sideToMove, ply, QUIESCENCE_MAX_DEPTH);
        }

        // Null Move Pruning
        if (allowNull && !inCheck && depth >= 3 && hasNonPawnMaterial(sideToMove)) {
            board.doNullMove();
            int nullScore = -pvs(depth - 1 - 2, -beta, -beta + 1, !sideToMove, ply + 1, false);
            board.undoNullMove();
            if (context.isTimedOut()) return evaluate(board, sideToMove);
            if (nullScore >= beta) {
                return beta;
            }
        }

        sortMoves(moves, ply, ttMove);

        int bestScore = -INF;
        Move bestMove = null;
        byte flag = TT_UPPER;

        for (int i = 0; i < moves.size(); i++) {
            Move move = moves.get(i);
            if (context.isTimedOut()) return evaluate(board, sideToMove);

            ChessBoard.MoveRecord rec = board.makeMove(move);
            int score;
            if (i == 0) {
                score = -pvs(depth - 1, -beta, -alpha, !sideToMove, ply + 1, true);
            } else {
                score = -pvs(depth - 1, -alpha - 1, -alpha, !sideToMove, ply + 1, true);
                if (score > alpha && score < beta) {
                    score = -pvs(depth - 1, -beta, -alpha, !sideToMove, ply + 1, true);
                }
            }
            board.undoMove(rec);

            if (context.isTimedOut()) return evaluate(board, sideToMove);

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }

            if (score > alpha) {
                alpha = score;
                flag = TT_EXACT;
                if (score >= beta) {
                    flag = TT_LOWER;
                    // Update killers & history for non-captures
                    if (board.getPiece(move.toRow, move.toCol) == null) {
                        updateKillers(move, ply);
                        historyTable[move.fromRow * 9 + move.fromCol][move.toRow * 9 + move.toCol] += depth * depth;
                    }
                    storeTT(key, depth, flag, beta, bestMove);
                    return beta;
                }
            }
        }

        storeTT(key, depth, flag, bestScore, bestMove);
        return bestScore;
    }

    private int quiescence(int alpha, int beta, boolean sideToMove, int ply, int qDepth) {
        if (context.isTimedOut()) return evaluate(board, sideToMove);

        nodesSearched++;

        List<Move> moves = board.getLegalMoves(sideToMove);
        if (moves.isEmpty()) {
            return -MATE_SCORE + ply;
        }

        int standPat = evaluate(board, sideToMove);
        if (standPat >= beta) return beta;
        if (alpha < standPat) alpha = standPat;
        if (qDepth <= 0) return alpha;

        // Delta pruning threshold: queen value approx 950 + 200 margin
        int delta = 1200;
        if (standPat + delta < alpha) return alpha;

        List<Move> captures = new ArrayList<>();
        for (Move m : moves) {
            if (board.getPiece(m.toRow, m.toCol) != null) captures.add(m);
        }
        sortMoves(captures, ply, null);

        for (Move move : captures) {
            if (context.isTimedOut()) return alpha;
            // Delta prune individual captures
            ChessPiece victim = board.getPiece(move.toRow, move.toCol);
            if (standPat + PIECE_VALUE[victim.getType().ordinal()] + 200 < alpha) continue;

            ChessBoard.MoveRecord rec = board.makeMove(move);
            int score = -quiescence(-beta, -alpha, !sideToMove, ply + 1, qDepth - 1);
            board.undoMove(rec);

            if (context.isTimedOut()) return alpha;
            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }

        return alpha;
    }

    // ==================== EVALUATION ====================

    private int evaluate(ChessBoard board, boolean perspectiveRed) {
        int score = 0;

        for (ChessPiece piece : board.getPieces()) {
            int value = basePieceValue(piece);
            score += (piece.isRed() == perspectiveRed) ? value : -value;
        }

        score += evaluateStructure(board, perspectiveRed);
        score -= evaluateStructure(board, !perspectiveRed);

        if (board.isKingAttacked(!perspectiveRed)) score += 800;
        if (board.isKingAttacked(perspectiveRed)) score -= 600;

        // 实际对局历史重复惩罚
        int repCount = board.getRepetitionCount();
        if (repCount >= 2) {
            score -= 800 * (repCount - 1);
        }

        return score;
    }

    private int basePieceValue(ChessPiece piece) {
        int typeIdx = piece.getType().ordinal();
        int value = PIECE_VALUE[typeIdx];

        // PST
        int row = piece.isRed() ? 9 - piece.getRow() : piece.getRow();
        int col = piece.getCol();
        value += PST[typeIdx][row][col];

        return value;
    }

    private int evaluateStructure(ChessBoard board, boolean side) {
        int s = 0;
        int advisorCount = 0, elephantCount = 0;

        for (ChessPiece p : board.getPieces()) {
            if (p.isRed() != side) continue;
            switch (p.getType()) {
                case ADVISOR -> advisorCount++;
                case ELEPHANT -> elephantCount++;
                case PAWN -> {
                    if (side) {
                        if (p.getRow() <= 4) s += 12;
                        if (p.getRow() <= 2) s += 20;
                    } else {
                        if (p.getRow() >= 5) s += 12;
                        if (p.getRow() >= 7) s += 20;
                    }
                }
                case KING -> {
                    int protectors = 0;
                    int kr = p.getRow(), kc = p.getCol();
                    for (int dr = -1; dr <= 1; dr++) {
                        for (int dc = -1; dc <= 1; dc++) {
                            if (dr == 0 && dc == 0) continue;
                            ChessPiece n = board.getPiece(kr + dr, kc + dc);
                            if (n != null && n.isRed() == side &&
                                (n.getType() == ChessPiece.Type.ADVISOR || n.getType() == ChessPiece.Type.ELEPHANT)) {
                                protectors++;
                            }
                        }
                    }
                    s += protectors * 25;
                }
            }
        }

        if (advisorCount >= 2) s += 35;
        if (elephantCount >= 2) s += 25;

        // Rook open files
        for (ChessPiece p : board.getPieces()) {
            if (p.isRed() != side || p.getType() != ChessPiece.Type.ROOK) continue;
            boolean open = true;
            for (int r = 0; r < ChessBoard.ROWS; r++) {
                ChessPiece o = board.getPiece(r, p.getCol());
                if (o != null && o.getType() == ChessPiece.Type.PAWN) { open = false; break; }
            }
            if (open) s += 20;
        }

        // Cannon frames (friendly piece directly toward enemy)
        for (ChessPiece p : board.getPieces()) {
            if (p.isRed() != side || p.getType() != ChessPiece.Type.CANNON) continue;
            int dir = side ? -1 : 1;
            ChessPiece f = board.getPiece(p.getRow() + dir, p.getCol());
            if (f != null && f.isRed() == side) s += 18;
        }

        // Adjacent pawns
        for (ChessPiece p : board.getPieces()) {
            if (p.isRed() != side || p.getType() != ChessPiece.Type.PAWN) continue;
            for (int dc = -1; dc <= 1; dc += 2) {
                ChessPiece n = board.getPiece(p.getRow(), p.getCol() + dc);
                if (n != null && n.isRed() == side && n.getType() == ChessPiece.Type.PAWN) s += 10;
            }
        }

        return s;
    }

    // ==================== MOVE SORTING ====================

    private void sortMoves(List<Move> moves, int ply, Move ttMove) {
        moves.sort((a, b) -> scoreMove(b, ply, ttMove) - scoreMove(a, ply, ttMove));
    }

    private int scoreMove(Move move, int ply, Move ttMove) {
        if (ttMove != null && move.fromRow == ttMove.fromRow && move.fromCol == ttMove.fromCol
                && move.toRow == ttMove.toRow && move.toCol == ttMove.toCol) {
            return 10_000_000;
        }
        ChessPiece victim = board.getPiece(move.toRow, move.toCol);
        if (victim != null) {
            ChessPiece attacker = board.getPiece(move.fromRow, move.fromCol);
            int score = PIECE_VALUE[victim.getType().ordinal()] * 12;
            if (attacker != null) score -= PIECE_VALUE[attacker.getType().ordinal()] / 8;
            return score + 5_000_000;
        }
        // Killer moves
        if (ply < MAX_PLY) {
            if (killerMoves[ply][0] != null && moveEquals(move, killerMoves[ply][0])) return 4_000_000;
            if (killerMoves[ply][1] != null && moveEquals(move, killerMoves[ply][1])) return 3_999_000;
        }
        // History heuristic
        int h = historyTable[move.fromRow * 9 + move.fromCol][move.toRow * 9 + move.toCol];
        return Math.min(h, 3_000_000);
    }

    private boolean moveEquals(Move a, Move b) {
        return a.fromRow == b.fromRow && a.fromCol == b.fromCol && a.toRow == b.toRow && a.toCol == b.toCol;
    }

    private void updateKillers(Move move, int ply) {
        if (ply >= MAX_PLY) return;
        if (killerMoves[ply][0] == null || !moveEquals(move, killerMoves[ply][0])) {
            killerMoves[ply][1] = killerMoves[ply][0];
            killerMoves[ply][0] = move;
        }
    }

    // ==================== TRANSPOSITION TABLE ====================

    private void storeTT(long key, int depth, byte flag, int score, Move move) {
        int idx = (int) (key & TT_MASK);
        // Always replace if deeper or same depth
        if (ttDepths[idx] <= depth) {
            ttKeys[idx] = key;
            ttDepths[idx] = (short) depth;
            ttFlags[idx] = flag;
            ttScores[idx] = score;
            ttMoves[idx] = encodeMove(move);
        }
    }

    private TTEntry probeTT(long key) {
        int idx = (int) (key & TT_MASK);
        if (ttKeys[idx] == key) {
            return new TTEntry(ttDepths[idx], ttFlags[idx], ttScores[idx], decodeMove(ttMoves[idx]));
        }
        return null;
    }

    private Move probeTTMove(long key) {
        int idx = (int) (key & TT_MASK);
        if (ttKeys[idx] == key) {
            return decodeMove(ttMoves[idx]);
        }
        return null;
    }

    private static long encodeMove(Move m) {
        if (m == null) return -1L;
        return ((long) m.fromRow << 24) | ((long) m.fromCol << 16) | ((long) m.toRow << 8) | m.toCol;
    }

    private static Move decodeMove(long code) {
        if (code < 0) return null;
        return new Move((int) ((code >> 24) & 0xFF), (int) ((code >> 16) & 0xFF),
                        (int) ((code >> 8) & 0xFF), (int) (code & 0xFF));
    }

    private static class TTEntry {
        final int depth;
        final byte flag;
        final int score;
        final Move move;
        TTEntry(int depth, byte flag, int score, Move move) {
            this.depth = depth; this.flag = flag; this.score = score; this.move = move;
        }
    }

    // ==================== HELPERS ====================

    private boolean hasNonPawnMaterial(boolean side) {
        for (ChessPiece p : board.getPieces()) {
            if (p.isRed() == side && p.getType() != ChessPiece.Type.PAWN && p.getType() != ChessPiece.Type.KING) {
                return true;
            }
        }
        return false;
    }

    private void clearKillersAndHistory() {
        for (int i = 0; i < MAX_PLY; i++) {
            killerMoves[i][0] = null;
            killerMoves[i][1] = null;
        }
        for (int i = 0; i < 90; i++) {
            for (int j = 0; j < 90; j++) historyTable[i][j] = 0;
        }
    }

    private static class SearchContext {
        final long deadlineNanos;
        boolean timedOut;
        private final java.util.HashMap<Long, Integer> pathCounts = new java.util.HashMap<>();

        SearchContext(long deadlineNanos) { this.deadlineNanos = deadlineNanos; }
        boolean isTimedOut() {
            if (!timedOut && System.nanoTime() >= deadlineNanos) timedOut = true;
            return timedOut;
        }
        int incrementPathCount(long key) {
            return pathCounts.merge(key, 1, Integer::sum);
        }
        void decrementPathCount(long key) {
            pathCounts.merge(key, -1, (oldVal, one) -> {
                int newVal = oldVal + one;
                return newVal <= 0 ? null : newVal;
            });
        }
    }

    private static class SearchResult {
        final Move move;
        final int score;
        SearchResult(Move move, int score) { this.move = move; this.score = score; }
    }
}
