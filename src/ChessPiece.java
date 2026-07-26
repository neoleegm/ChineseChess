import java.io.Serializable;

/**
 * 棋子类
 * 定义中国象棋中的各种棋子及其属性
 */
public class ChessPiece implements Serializable, Cloneable {
    private static final long serialVersionUID = 1L;
    
    // 棋子类型
    public enum Type {
        KING("帅", "将"),      // 帅/将
        ADVISOR("仕", "士"),   // 仕/士
        ELEPHANT("相", "象"),  // 相/象
        HORSE("傌", "马"),     // 傌/马
        ROOK("俥", "车"),      // 俥/车
        CANNON("炮", "砲"),    // 炮/砲
        PAWN("兵", "卒");      // 兵/卒

        private final String redName;
        private final String blackName;

        Type(String redName, String blackName) {
            this.redName = redName;
            this.blackName = blackName;
        }

        public String getName(boolean isRed) {
            return isRed ? redName : blackName;
        }
    }
    
    private final Type type;
    private final boolean isRed;
    private int row;
    private int col;
    
    public ChessPiece(Type type, boolean isRed, int row, int col) {
        this.type = type;
        this.isRed = isRed;
        this.row = row;
        this.col = col;
    }
    
    public Type getType() {
        return type;
    }
    
    public boolean isRed() {
        return isRed;
    }
    
    public int getRow() {
        return row;
    }
    
    public int getCol() {
        return col;
    }
    
    public void setPosition(int row, int col) {
        this.row = row;
        this.col = col;
    }
    
    public String getName() {
        return type.getName(isRed);
    }

    @Override
    public ChessPiece clone() {
        try {
            return (ChessPiece) super.clone();
        } catch (CloneNotSupportedException e) {
            return new ChessPiece(type, isRed, row, col);
        }
    }
    
    @Override
    public String toString() {
        return String.format("%s(%s) at [%d,%d]", getName(), isRed ? "红" : "黑", row, col);
    }
}
