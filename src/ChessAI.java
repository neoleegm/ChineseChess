/*
 * Decompiled with CFR 0.152.
 */
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ChessAI {
    private Difficulty difficulty;
    private Random random = new Random();
    private static final Map<ChessPiece.Type, Integer> PIECE_VALUE = Map.of(ChessPiece.Type.KING, 100000, ChessPiece.Type.ROOK, 1000, ChessPiece.Type.HORSE, 450, ChessPiece.Type.CANNON, 500, ChessPiece.Type.ELEPHANT, 200, ChessPiece.Type.ADVISOR, 200, ChessPiece.Type.PAWN, 100);
    private static final int[][] PAWN_PST = new int[][]{{0, 0, 0, 0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 0, 0, 0, 0}, {10, 10, 10, 20, 20, 20, 10, 10, 10}, {20, 20, 30, 40, 50, 40, 30, 20, 20}, {30, 30, 40, 50, 60, 50, 40, 30, 30}, {40, 50, 60, 70, 80, 70, 60, 50, 40}, {50, 60, 70, 80, 90, 80, 70, 60, 50}, {60, 70, 80, 90, 100, 90, 80, 70, 60}};

    public ChessAI(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public Difficulty getDifficulty() {
        return this.difficulty;
    }

    public int[] getNextMove(ChessBoard chessBoard) {
        return switch (this.difficulty.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> this.getEasyMove(chessBoard);
            case 1 -> this.getMediumMove(chessBoard);
            case 2 -> this.getHardMove(chessBoard);
        };
    }

    private int[] getEasyMove(ChessBoard chessBoard) {
        List<int[]> list = this.getAllMoves(chessBoard, false);
        if (list.isEmpty()) {
            return null;
        }
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        ArrayList<int[]> arrayList2 = new ArrayList<int[]>();
        for (int[] nArray : list) {
            ChessPiece chessPiece = chessBoard.getPiece(nArray[2], nArray[3]);
            if (chessPiece != null) {
                arrayList.add(nArray);
                continue;
            }
            if (nArray[2] <= nArray[0]) continue;
            arrayList2.add(nArray);
        }
        if (!arrayList.isEmpty() && this.random.nextDouble() < 0.8) {
            return (int[])arrayList.get(this.random.nextInt(arrayList.size()));
        }
        if (!arrayList2.isEmpty()) {
            return (int[])arrayList2.get(this.random.nextInt(arrayList2.size()));
        }
        return list.get(this.random.nextInt(list.size()));
    }

    private int[] getMediumMove(ChessBoard chessBoard) {
        List<int[]> list = this.getAllMoves(chessBoard, false);
        if (list.isEmpty()) {
            return null;
        }
        list.sort((nArray, nArray2) -> {
            ChessPiece chessPiece = chessBoard.getPiece(nArray[2], nArray[3]);
            ChessPiece chessPiece2 = chessBoard.getPiece(nArray2[2], nArray2[3]);
            int n = chessPiece != null ? PIECE_VALUE.get((Object)chessPiece.getType()) : 0;
            int n2 = chessPiece2 != null ? PIECE_VALUE.get((Object)chessPiece2.getType()) : 0;
            return Integer.compare(n2, n);
        });
        int n = Integer.MIN_VALUE;
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        for (int[] nArray3 : list) {
            int n2 = this.evaluateMoveFast(chessBoard, nArray3);
            if (n2 > n) {
                n = n2;
                arrayList.clear();
                arrayList.add(nArray3);
                continue;
            }
            if (n2 != n) continue;
            arrayList.add(nArray3);
        }
        return (int[])arrayList.get(this.random.nextInt(arrayList.size()));
    }

    private int evaluateMoveFast(ChessBoard chessBoard, int[] nArray) {
        int n = 0;
        ChessPiece chessPiece = chessBoard.getPiece(nArray[0], nArray[1]);
        ChessPiece chessPiece2 = chessBoard.getPiece(nArray[2], nArray[3]);
        if (chessPiece2 != null) {
            n += PIECE_VALUE.get((Object)chessPiece2.getType()) * 10;
        }
        ChessBoard chessBoard2 = chessBoard.clone();
        chessBoard2.movePiece(nArray[0], nArray[1], nArray[2], nArray[3]);
        if (this.isKingAttacked(chessBoard2, true)) {
            n += 500;
        }
        n += this.evaluateBoardFast(chessBoard2);
        List<int[]> list = this.getCaptureMoves(chessBoard2, true);
        if (!list.isEmpty()) {
            int n2 = Integer.MIN_VALUE;
            for (int[] nArray2 : list) {
                ChessPiece chessPiece3 = chessBoard2.getPiece(nArray2[2], nArray2[3]);
                if (chessPiece3 == null) continue;
                int n3 = PIECE_VALUE.get((Object)chessPiece3.getType());
                n2 = Math.max(n2, n3);
            }
            if (n2 > 0) {
                n -= n2 * 8;
            }
        }
        return n;
    }

    private int[] getHardMove(ChessBoard chessBoard) {
        List<int[]> list = this.getAllMovesOrdered(chessBoard, false);
        if (list.isEmpty()) {
            return null;
        }
        int n = Integer.MIN_VALUE;
        int n2 = Integer.MIN_VALUE;
        int n3 = Integer.MAX_VALUE;
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        for (int[] nArray : list) {
            ChessBoard chessBoard2 = chessBoard.clone();
            if (!chessBoard2.movePiece(nArray[0], nArray[1], nArray[2], nArray[3]) || this.isKingAttacked(chessBoard2, false)) continue;
            int n4 = -this.negamax(chessBoard2, 3, -n3, -n2, true);
            if (n4 > n) {
                n = n4;
                arrayList.clear();
                arrayList.add(nArray);
            } else if (n4 == n) {
                arrayList.add(nArray);
            }
            n2 = Math.max(n2, n4);
        }
        return arrayList.isEmpty() ? list.get(0) : (int[])arrayList.get(this.random.nextInt(arrayList.size()));
    }

    private int negamax(ChessBoard chessBoard, int n, int n2, int n3, boolean bl) {
        if (n <= 0) {
            return this.evaluateBoard(chessBoard);
        }
        if (chessBoard.isGameOver()) {
            String string = chessBoard.getWinner();
            if (string.contains("\u9ed1\u65b9")) {
                return 1000000;
            }
            if (string.contains("\u7ea2\u65b9")) {
                return -1000000;
            }
            return 0;
        }
        List<int[]> list = this.getAllMovesOrdered(chessBoard, bl);
        if (list.isEmpty()) {
            return bl ? -500000 : 500000;
        }
        for (int[] nArray : list) {
            ChessBoard chessBoard2 = chessBoard.clone();
            if (!chessBoard2.movePiece(nArray[0], nArray[1], nArray[2], nArray[3]) || this.isKingAttacked(chessBoard2, bl)) continue;
            int n4 = -this.negamax(chessBoard2, n - 1, -n3, -n2, !bl);
            if (n4 >= n3) {
                return n3;
            }
            n2 = Math.max(n2, n4);
        }
        return n2;
    }

    private int evaluateBoardFast(ChessBoard chessBoard) {
        int n = 0;
        for (int i = 0; i < 10; ++i) {
            for (int j = 0; j < 9; ++j) {
                ChessPiece chessPiece = chessBoard.getPiece(i, j);
                if (chessPiece == null) continue;
                int n2 = PIECE_VALUE.get((Object)chessPiece.getType());
                if (chessPiece.getType() == ChessPiece.Type.PAWN) {
                    n2 += chessPiece.isRed() ? PAWN_PST[9 - i][j] : PAWN_PST[i][j];
                }
                n2 = chessPiece.isRed() ? (n2 += (9 - i) * 3) : (n2 += i * 3);
                if (chessPiece.isRed()) {
                    n -= n2;
                    continue;
                }
                n += n2;
            }
        }
        return n;
    }

    private int evaluateBoard(ChessBoard chessBoard) {
        int n = this.evaluateBoardFast(chessBoard);
        for (int i = 0; i < 10; ++i) {
            for (int j = 0; j < 9; ++j) {
                int n2;
                ChessPiece chessPiece = chessBoard.getPiece(i, j);
                if (chessPiece == null) continue;
                int n3 = 0;
                for (n2 = 0; n2 < 10; ++n2) {
                    for (int k = 0; k < 9; ++k) {
                        if (!chessBoard.canMove(i, j, n2, k)) continue;
                        ++n3;
                    }
                }
                n2 = n3 * (chessPiece.getType() == ChessPiece.Type.ROOK ? 3 : (chessPiece.getType() == ChessPiece.Type.HORSE ? 5 : 2));
                if (chessPiece.isRed()) {
                    n -= n2;
                    continue;
                }
                n += n2;
            }
        }
        return n;
    }

    private boolean isKingAttacked(ChessBoard chessBoard, boolean bl) {
        ChessPiece chessPiece;
        int n;
        int n2;
        int n3 = -1;
        int n4 = -1;
        block0: for (n2 = 0; n2 < 10; ++n2) {
            for (n = 0; n < 9; ++n) {
                chessPiece = chessBoard.getPiece(n2, n);
                if (chessPiece == null || chessPiece.getType() != ChessPiece.Type.KING || chessPiece.isRed() != bl) continue;
                n3 = n2;
                n4 = n;
                continue block0;
            }
        }
        if (n3 == -1) {
            return true;
        }
        for (n2 = 0; n2 < 10; ++n2) {
            for (n = 0; n < 9; ++n) {
                chessPiece = chessBoard.getPiece(n2, n);
                if (chessPiece == null || chessPiece.isRed() == bl || !chessBoard.canMove(n2, n, n3, n4)) continue;
                return true;
            }
        }
        return false;
    }

    private List<int[]> getCaptureMoves(ChessBoard chessBoard, boolean bl) {
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        for (int i = 0; i < 10; ++i) {
            for (int j = 0; j < 9; ++j) {
                ChessPiece chessPiece = chessBoard.getPiece(i, j);
                if (chessPiece == null || chessPiece.isRed() != bl) continue;
                for (int k = 0; k < 10; ++k) {
                    for (int i2 = 0; i2 < 9; ++i2) {
                        ChessPiece chessPiece2;
                        if (!chessBoard.canMove(i, j, k, i2) || (chessPiece2 = chessBoard.getPiece(k, i2)) == null) continue;
                        arrayList.add(new int[]{i, j, k, i2});
                    }
                }
            }
        }
        return arrayList;
    }

    private List<int[]> getAllMovesOrdered(ChessBoard chessBoard, boolean bl) {
        List<int[]> list = this.getAllMoves(chessBoard, bl);
        list.sort((nArray, nArray2) -> {
            int n = this.scoreMove(chessBoard, (int[])nArray);
            int n2 = this.scoreMove(chessBoard, (int[])nArray2);
            return Integer.compare(n2, n);
        });
        return list;
    }

    private int scoreMove(ChessBoard chessBoard, int[] nArray) {
        int n = 0;
        ChessPiece chessPiece = chessBoard.getPiece(nArray[2], nArray[3]);
        ChessPiece chessPiece2 = chessBoard.getPiece(nArray[0], nArray[1]);
        if (chessPiece != null) {
            n += 10000 + PIECE_VALUE.get((Object)chessPiece.getType()) - PIECE_VALUE.get((Object)chessPiece2.getType()) / 100;
        }
        ChessBoard chessBoard2 = chessBoard.clone();
        chessBoard2.movePiece(nArray[0], nArray[1], nArray[2], nArray[3]);
        if (this.isKingAttacked(chessBoard2, !chessBoard.isRedTurn())) {
            n += 8000;
        }
        return n;
    }

    private List<int[]> getAllMoves(ChessBoard chessBoard, boolean bl) {
        ArrayList<int[]> arrayList = new ArrayList<int[]>();
        for (int i = 0; i < 10; ++i) {
            for (int j = 0; j < 9; ++j) {
                ChessPiece chessPiece = chessBoard.getPiece(i, j);
                if (chessPiece == null || chessPiece.isRed() != bl) continue;
                for (int k = 0; k < 10; ++k) {
                    for (int i2 = 0; i2 < 9; ++i2) {
                        if (!chessBoard.canMove(i, j, k, i2)) continue;
                        arrayList.add(new int[]{i, j, k, i2});
                    }
                }
            }
        }
        return arrayList;
    }

    public static enum Difficulty {
        EASY,
        MEDIUM,
        HARD;

    }
}
