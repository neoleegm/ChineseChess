/**
 * Converts between internal row/column moves and UCI xiangqi coordinates.
 */
public final class MoveCodec {
    private MoveCodec() {
    }

    public static String toUci(Move move) {
        return squareToUci(move.fromRow, move.fromCol) + squareToUci(move.toRow, move.toCol);
    }

    public static Move fromUci(String text) {
        if (text == null || text.length() < 4) {
            return null;
        }

        int fromCol = fileToCol(text.charAt(0));
        int fromRow = rankToRow(text.charAt(1));
        int toCol = fileToCol(text.charAt(2));
        int toRow = rankToRow(text.charAt(3));

        if (!isValid(fromRow, fromCol) || !isValid(toRow, toCol)) {
            return null;
        }
        return new Move(fromRow, fromCol, toRow, toCol);
    }

    private static String squareToUci(int row, int col) {
        return String.valueOf((char) ('a' + col)) + (9 - row);
    }

    private static int fileToCol(char file) {
        return file - 'a';
    }

    private static int rankToRow(char rank) {
        if (rank < '0' || rank > '9') {
            return -1;
        }
        return 9 - (rank - '0');
    }

    private static boolean isValid(int row, int col) {
        return row >= 0 && row < ChessBoard.ROWS && col >= 0 && col < ChessBoard.COLS;
    }
}
