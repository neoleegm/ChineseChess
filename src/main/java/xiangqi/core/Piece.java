package xiangqi.core;

/**
 * 棋子：类型 + 颜色 + 当前格位。
 *
 * <p>棋子对象在整局中保持同一实例（走子只改格位），因此悔棋、克隆都可以按引用还原，
 * 不需要在每步重新构造对象。格位用一维下标 {@code row * 9 + col} 表示，
 * 与 Zobrist 表、走法编码保持一致。</p>
 */
public final class Piece implements Cloneable {

    /** 棋子类型。ordinal 顺序即 Zobrist / 评估表的索引顺序，不可随意调整。 */
    public enum Type {
        KING("帅", "将", 'k', 100_000),
        ADVISOR("仕", "士", 'a', 220),
        ELEPHANT("相", "象", 'b', 220),
        HORSE("傌", "马", 'n', 480),
        ROOK("俥", "车", 'r', 950),
        CANNON("炮", "砲", 'c', 520),
        PAWN("兵", "卒", 'p', 110);

        private final String redName;
        private final String blackName;
        private final char fenLetter;
        private final int value;

        Type(String redName, String blackName, char fenLetter, int value) {
            this.redName = redName;
            this.blackName = blackName;
            this.fenLetter = fenLetter;
            this.value = value;
        }

        public String name(boolean isRed) {
            return isRed ? redName : blackName;
        }

        public char fenLetter() {
            return fenLetter;
        }

        /**
         * 子力价值（以「兵 = 100」为基准）。评估函数与「捉子」判定共用同一张表，
         * 避免规则层和搜索层各写一份、改一处忘一处。
         */
        public int value() {
            return value;
        }

        public static Type fromFenLetter(char letter) {
            char lower = Character.toLowerCase(letter);
            for (Type type : values()) {
                if (type.fenLetter == lower) {
                    return type;
                }
            }
            return null;
        }
    }

    private final Type type;
    private final boolean red;
    private int row;
    private int col;

    public Piece(Type type, boolean red, int row, int col) {
        this.type = type;
        this.red = red;
        this.row = row;
        this.col = col;
    }

    public Type type() {
        return type;
    }

    public boolean isRed() {
        return red;
    }

    public int row() {
        return row;
    }

    public int col() {
        return col;
    }

    /** 一维格位下标（0..89）。 */
    public int square() {
        return row * Board.COLS + col;
    }

    void setSquare(int square) {
        this.row = square / Board.COLS;
        this.col = square % Board.COLS;
    }

    public String name() {
        return type.name(red);
    }

    @Override
    public Piece clone() {
        return new Piece(type, red, row, col);
    }

    @Override
    public String toString() {
        return name() + (red ? "(红)" : "(黑)") + "@" + row + "," + col;
    }
}
