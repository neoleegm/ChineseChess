/**
 * Converts the current board into the FEN dialect used by Pikafish/UCI xiangqi engines.
 */
public final class FenCodec {
    private FenCodec() {
    }

    public static String toFen(ChessBoard board) {
        StringBuilder fen = new StringBuilder();

        for (int row = 0; row < ChessBoard.ROWS; row++) {
            if (row > 0) {
                fen.append('/');
            }

            int emptyCount = 0;
            for (int col = 0; col < ChessBoard.COLS; col++) {
                ChessPiece piece = board.getPiece(row, col);
                if (piece == null) {
                    emptyCount++;
                    continue;
                }

                if (emptyCount > 0) {
                    fen.append(emptyCount);
                    emptyCount = 0;
                }
                fen.append(toFenChar(piece));
            }

            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
        }

        fen.append(board.isRedTurn() ? " w - - 0 1" : " b - - 0 1");
        return fen.toString();
    }

    private static char toFenChar(ChessPiece piece) {
        char symbol = switch (piece.getType()) {
            case KING -> 'k';
            case ADVISOR -> 'a';
            case ELEPHANT -> 'b';
            case HORSE -> 'n';
            case ROOK -> 'r';
            case CANNON -> 'c';
            case PAWN -> 'p';
        };
        return piece.isRed() ? Character.toUpperCase(symbol) : symbol;
    }
}
