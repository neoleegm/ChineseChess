/*
 * Decompiled with CFR 0.152.
 */
import java.util.ArrayList;
import java.util.List;

public class ChessBoard
implements Cloneable {
    public static final int ROWS = 10;
    public static final int COLS = 9;
    private ChessPiece[][] board;
    private List<ChessPiece> pieces;
    private boolean redTurn;
    private List<Move> history = new ArrayList<Move>();

    public ChessBoard() {
        this.board = new ChessPiece[10][9];
        this.pieces = new ArrayList<ChessPiece>();
        this.redTurn = true;
        this.initBoard();
    }

    private ChessBoard(ChessPiece[][] chessPieceArray, List<ChessPiece> list, boolean bl) {
        this.board = chessPieceArray;
        this.pieces = list;
        this.redTurn = bl;
    }

    private void initBoard() {
        int n;
        int n2 = 9;
        this.addPiece(ChessPiece.Type.ROOK, true, n2, 0);
        this.addPiece(ChessPiece.Type.HORSE, true, n2, 1);
        this.addPiece(ChessPiece.Type.ELEPHANT, true, n2, 2);
        this.addPiece(ChessPiece.Type.ADVISOR, true, n2, 3);
        this.addPiece(ChessPiece.Type.KING, true, n2, 4);
        this.addPiece(ChessPiece.Type.ADVISOR, true, n2, 5);
        this.addPiece(ChessPiece.Type.ELEPHANT, true, n2, 6);
        this.addPiece(ChessPiece.Type.HORSE, true, n2, 7);
        this.addPiece(ChessPiece.Type.ROOK, true, n2, 8);
        this.addPiece(ChessPiece.Type.CANNON, true, 7, 1);
        this.addPiece(ChessPiece.Type.CANNON, true, 7, 7);
        for (n = 0; n < 9; n += 2) {
            this.addPiece(ChessPiece.Type.PAWN, true, 6, n);
        }
        this.addPiece(ChessPiece.Type.ROOK, false, 0, 0);
        this.addPiece(ChessPiece.Type.HORSE, false, 0, 1);
        this.addPiece(ChessPiece.Type.ELEPHANT, false, 0, 2);
        this.addPiece(ChessPiece.Type.ADVISOR, false, 0, 3);
        this.addPiece(ChessPiece.Type.KING, false, 0, 4);
        this.addPiece(ChessPiece.Type.ADVISOR, false, 0, 5);
        this.addPiece(ChessPiece.Type.ELEPHANT, false, 0, 6);
        this.addPiece(ChessPiece.Type.HORSE, false, 0, 7);
        this.addPiece(ChessPiece.Type.ROOK, false, 0, 8);
        this.addPiece(ChessPiece.Type.CANNON, false, 2, 1);
        this.addPiece(ChessPiece.Type.CANNON, false, 2, 7);
        for (n = 0; n < 9; n += 2) {
            this.addPiece(ChessPiece.Type.PAWN, false, 3, n);
        }
    }

    private void addPiece(ChessPiece.Type type, boolean bl, int n, int n2) {
        ChessPiece chessPiece = new ChessPiece(type, bl, n, n2);
        this.pieces.add(chessPiece);
        this.board[n][n2] = chessPiece;
    }

    public ChessPiece getPiece(int n, int n2) {
        if (n < 0 || n >= 10 || n2 < 0 || n2 >= 9) {
            return null;
        }
        return this.board[n][n2];
    }

    public boolean isRedTurn() {
        return this.redTurn;
    }

    public boolean canMove(int n, int n2, int n3, int n4) {
        if (!this.isValidPos(n, n2) || !this.isValidPos(n3, n4)) {
            return false;
        }
        if (n == n3 && n2 == n4) {
            return false;
        }
        ChessPiece chessPiece = this.board[n][n2];
        if (chessPiece == null) {
            return false;
        }
        ChessPiece chessPiece2 = this.board[n3][n4];
        if (chessPiece2 != null && chessPiece2.isRed() == chessPiece.isRed()) {
            return false;
        }
        return this.isValidMove(chessPiece, n, n2, n3, n4);
    }

    public boolean movePiece(int n, int n2, int n3, int n4) {
        ChessPiece chessPiece = this.board[n][n2];
        if (chessPiece == null || chessPiece.isRed() != this.redTurn) {
            return false;
        }
        if (!this.canMove(n, n2, n3, n4)) {
            return false;
        }
        ChessPiece chessPiece2 = this.board[n3][n4];
        this.history.add(new Move(n, n2, n3, n4, chessPiece2));
        this.board[n][n2] = null;
        if (chessPiece2 != null) {
            this.pieces.remove(chessPiece2);
        }
        this.board[n3][n4] = chessPiece;
        chessPiece.setPosition(n3, n4);
        this.redTurn = !this.redTurn;
        return true;
    }

    public boolean undo() {
        if (this.history.isEmpty()) {
            return false;
        }
        Move move = this.history.remove(this.history.size() - 1);
        ChessPiece chessPiece = this.board[move.toRow][move.toCol];
        this.board[move.toRow][move.toCol] = null;
        this.board[move.fromRow][move.fromCol] = chessPiece;
        chessPiece.setPosition(move.fromRow, move.fromCol);
        if (move.captured != null) {
            this.pieces.add(move.captured);
            this.board[move.toRow][move.toCol] = move.captured;
        }
        this.redTurn = !this.redTurn;
        return true;
    }

    public int getHistorySize() {
        return this.history.size();
    }

    private boolean isValidPos(int n, int n2) {
        return n >= 0 && n < 10 && n2 >= 0 && n2 < 9;
    }

    private boolean isValidMove(ChessPiece chessPiece, int n, int n2, int n3, int n4) {
        return switch (chessPiece.getType()) {
            case ChessPiece.Type.KING -> this.isValidKingMove(chessPiece, n, n2, n3, n4);
            case ChessPiece.Type.ADVISOR -> this.isValidAdvisorMove(chessPiece, n, n2, n3, n4);
            case ChessPiece.Type.ELEPHANT -> this.isValidElephantMove(chessPiece, n, n2, n3, n4);
            case ChessPiece.Type.HORSE -> this.isValidHorseMove(n, n2, n3, n4);
            case ChessPiece.Type.ROOK -> this.isValidRookMove(n, n2, n3, n4);
            case ChessPiece.Type.CANNON -> this.isValidCannonMove(n, n2, n3, n4);
            case ChessPiece.Type.PAWN -> this.isValidPawnMove(chessPiece, n, n2, n3, n4);
            default -> throw new MatchException(null, null);
        };
    }

    private boolean isValidKingMove(ChessPiece chessPiece, int n, int n2, int n3, int n4) {
        if (n4 < 3 || n4 > 5) {
            return false;
        }
        if (chessPiece.isRed() ? n3 < 7 || n3 > 9 : n3 < 0 || n3 > 2) {
            return false;
        }
        int n5 = Math.abs(n3 - n);
        int n6 = Math.abs(n4 - n2);
        return n5 == 1 && n6 == 0 || n5 == 0 && n6 == 1;
    }

    private boolean isValidAdvisorMove(ChessPiece chessPiece, int n, int n2, int n3, int n4) {
        if (n4 < 3 || n4 > 5) {
            return false;
        }
        if (chessPiece.isRed() ? n3 < 7 || n3 > 9 : n3 < 0 || n3 > 2) {
            return false;
        }
        return Math.abs(n3 - n) == 1 && Math.abs(n4 - n2) == 1;
    }

    private boolean isValidElephantMove(ChessPiece chessPiece, int n, int n2, int n3, int n4) {
        if (chessPiece.isRed() ? n3 < 5 : n3 > 4) {
            return false;
        }
        if (Math.abs(n3 - n) != 2 || Math.abs(n4 - n2) != 2) {
            return false;
        }
        return this.board[(n + n3) / 2][(n2 + n4) / 2] == null;
    }

    private boolean isValidHorseMove(int n, int n2, int n3, int n4) {
        int n5 = Math.abs(n3 - n);
        int n6 = Math.abs(n4 - n2);
        if (!(n5 == 2 && n6 == 1 || n5 == 1 && n6 == 2)) {
            return false;
        }
        int n7 = n5 == 2 ? (n + n3) / 2 : n;
        int n8 = n6 == 2 ? (n2 + n4) / 2 : n2;
        int n9 = n8;
        return this.board[n7][n8] == null;
    }

    private boolean isValidRookMove(int n, int n2, int n3, int n4) {
        if (n != n3 && n2 != n4) {
            return false;
        }
        return this.countBetween(n, n2, n3, n4) == 0;
    }

    private boolean isValidCannonMove(int n, int n2, int n3, int n4) {
        if (n != n3 && n2 != n4) {
            return false;
        }
        int n5 = this.countBetween(n, n2, n3, n4);
        ChessPiece chessPiece = this.board[n3][n4];
        return chessPiece == null ? n5 == 0 : n5 == 1;
    }

    private boolean isValidPawnMove(ChessPiece chessPiece, int n, int n2, int n3, int n4) {
        boolean bl;
        int n5 = n3 - n;
        int n6 = Math.abs(n4 - n2);
        if (chessPiece.isRed() ? n5 > 0 : n5 < 0) {
            return false;
        }
        boolean bl2 = chessPiece.isRed() ? n <= 4 : (bl = n >= 5);
        return bl ? Math.abs(n5) == 1 && n6 == 0 || n5 == 0 && n6 == 1 : Math.abs(n5) == 1 && n6 == 0;
    }

    private int countBetween(int n, int n2, int n3, int n4) {
        int n5 = 0;
        if (n == n3) {
            for (int i = Math.min(n2, n4) + 1; i < Math.max(n2, n4); ++i) {
                if (this.board[n][i] == null) continue;
                ++n5;
            }
        } else {
            for (int i = Math.min(n, n3) + 1; i < Math.max(n, n3); ++i) {
                if (this.board[i][n2] == null) continue;
                ++n5;
            }
        }
        return n5;
    }

    public boolean isGameOver() {
        boolean bl = false;
        boolean bl2 = false;
        for (ChessPiece chessPiece : this.pieces) {
            if (chessPiece.getType() != ChessPiece.Type.KING) continue;
            if (chessPiece.isRed()) {
                bl = true;
                continue;
            }
            bl2 = true;
        }
        return !bl || !bl2;
    }

    public String getWinner() {
        boolean bl = false;
        boolean bl2 = false;
        for (ChessPiece chessPiece : this.pieces) {
            if (chessPiece.getType() != ChessPiece.Type.KING) continue;
            if (chessPiece.isRed()) {
                bl = true;
                continue;
            }
            bl2 = true;
        }
        if (!bl) {
            return "\u9ed1\u65b9\u83b7\u80dc\uff01";
        }
        if (!bl2) {
            return "\u7ea2\u65b9\u83b7\u80dc\uff01";
        }
        return null;
    }

    public void reset() {
        for (int i = 0; i < 10; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.board[i][j] = null;
            }
        }
        this.pieces.clear();
        this.history.clear();
        this.redTurn = true;
        this.initBoard();
    }

    public ChessBoard clone() {
        ChessPiece[][] chessPieceArray = new ChessPiece[10][9];
        ArrayList<ChessPiece> arrayList = new ArrayList<ChessPiece>();
        for (ChessPiece chessPiece : this.pieces) {
            ChessPiece chessPiece2 = new ChessPiece(chessPiece.getType(), chessPiece.isRed(), chessPiece.getRow(), chessPiece.getCol());
            arrayList.add(chessPiece2);
            chessPieceArray[chessPiece.getRow()][chessPiece.getCol()] = chessPiece2;
        }
        return new ChessBoard(chessPieceArray, arrayList, this.redTurn);
    }

    private static class Move {
        int fromRow;
        int fromCol;
        int toRow;
        int toCol;
        ChessPiece captured;

        Move(int n, int n2, int n3, int n4, ChessPiece chessPiece) {
            this.fromRow = n;
            this.fromCol = n2;
            this.toRow = n3;
            this.toCol = n4;
            this.captured = chessPiece;
        }
    }
}
