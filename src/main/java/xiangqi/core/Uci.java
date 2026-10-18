package xiangqi.core;

/**
 * 内部行列坐标与 UCI 象棋坐标互转。
 *
 * <p>UCI 用「列字母 + 行数字」，左下角（红方底线最左）为 {@code a0}，
 * 行号自下而上递增，因此 {@code row = 9 - rank}。</p>
 */
public final class Uci {

    private Uci() {
    }

    public static String toUci(Move move) {
        return squareToUci(move.from()) + squareToUci(move.to());
    }

    public static String squareToUci(int square) {
        int row = square / Board.COLS;
        int col = square % Board.COLS;
        return String.valueOf((char) ('a' + col)) + (9 - row);
    }

    public static Move fromUci(String text) {
        if (text == null || text.length() < 4) {
            return null;
        }
        int fromCol = text.charAt(0) - 'a';
        int fromRow = 9 - (text.charAt(1) - '0');
        int toCol = text.charAt(2) - 'a';
        int toRow = 9 - (text.charAt(3) - '0');
        if (!Board.onBoard(fromRow, fromCol) || !Board.onBoard(toRow, toCol)) {
            return null;
        }
        return new Move(fromRow, fromCol, toRow, toCol);
    }
}
