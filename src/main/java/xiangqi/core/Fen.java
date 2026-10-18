package xiangqi.core;

/**
 * FEN 序列化，使用 Pikafish / UCI 象棋引擎通用的方言。
 *
 * <p>格式：{@code <棋盘> <行棋方> - - <半回合计数> <回合数>}，
 * 例如初始局面 {@code rnbakabnr/9/1c5c1/... w - - 0 1}。</p>
 */
public final class Fen {

    private Fen() {
    }

    public static String toFen(Board board) {
        StringBuilder fen = new StringBuilder(96);
        for (int row = 0; row < Board.ROWS; row++) {
            if (row > 0) {
                fen.append('/');
            }
            int empty = 0;
            for (int col = 0; col < Board.COLS; col++) {
                Piece piece = board.pieceAt(row, col);
                if (piece == null) {
                    empty++;
                    continue;
                }
                if (empty > 0) {
                    fen.append(empty);
                    empty = 0;
                }
                char letter = piece.type().fenLetter();
                fen.append(piece.isRed() ? Character.toUpperCase(letter) : letter);
            }
            if (empty > 0) {
                fen.append(empty);
            }
        }
        int fullMoves = board.historySize() / 2 + 1;
        fen.append(board.isRedTurn() ? " w - - 0 " : " b - - 0 ").append(fullMoves);
        return fen.toString();
    }

    /**
     * 解析 FEN 为棋盘。解析失败抛出 {@link IllegalArgumentException}。
     * 只支持标准 10×9 象棋棋盘。
     */
    public static Board parse(String fen) {
        if (fen == null || fen.isBlank()) {
            throw new IllegalArgumentException("FEN 为空");
        }
        String[] parts = fen.trim().split("\\s+");
        String[] rows = parts[0].split("/");
        if (rows.length != Board.ROWS) {
            throw new IllegalArgumentException("FEN 行数不是 " + Board.ROWS + ": " + parts[0]);
        }

        Board board = new Board();
        board.clear();
        for (int row = 0; row < Board.ROWS; row++) {
            int col = 0;
            for (char ch : rows[row].toCharArray()) {
                if (ch >= '1' && ch <= '9') {
                    col += ch - '0';
                    continue;
                }
                Piece.Type type = Piece.Type.fromFenLetter(ch);
                if (type == null) {
                    throw new IllegalArgumentException("非法 FEN 字符: " + ch);
                }
                if (col >= Board.COLS) {
                    throw new IllegalArgumentException("FEN 第 " + row + " 行超出 9 列");
                }
                board.place(type, Character.isUpperCase(ch), row, col);
                col++;
            }
            if (col != Board.COLS) {
                throw new IllegalArgumentException("FEN 第 " + row + " 行列数为 " + col);
            }
        }
        if (parts.length > 1) {
            board.setRedTurn(!"b".equalsIgnoreCase(parts[1]));
        }
        return board;
    }
}
