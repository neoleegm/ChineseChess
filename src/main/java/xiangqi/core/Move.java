package xiangqi.core;

/**
 * 一手棋。不可变值对象，UI / 引擎 / 协议适配层共用。
 *
 * <p>{@link #from()} 与 {@link #to()} 返回一维格位下标，用于走法编码与引擎热路径；
 * 行列访问器用于 UI 与中文记谱。</p>
 */
public record Move(int fromRow, int fromCol, int toRow, int toCol) {

    public static Move of(int fromSquare, int toSquare) {
        return new Move(fromSquare / Board.COLS, fromSquare % Board.COLS,
                toSquare / Board.COLS, toSquare % Board.COLS);
    }

    public int from() {
        return fromRow * Board.COLS + fromCol;
    }

    public int to() {
        return toRow * Board.COLS + toCol;
    }

    public int[] toArray() {
        return new int[]{fromRow, fromCol, toRow, toCol};
    }

    @Override
    public String toString() {
        return "(" + fromRow + "," + fromCol + ")->(" + toRow + "," + toCol + ")";
    }
}
