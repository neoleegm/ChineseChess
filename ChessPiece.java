import java.awt.Color;

/**
 * 象棋棋子类
 */
public class ChessPiece {
    // 棋子类型
    public enum Type {
        KING("帥", "將"),      // 帅/将
        ADVISOR("仕", "士"),  // 仕/士
        ELEPHANT("相", "象"), // 相/象
        HORSE("傌", "馬"),    // 傌/马
        ROOK("俥", "車"),     // 俥/车
        CANNON("炮", "砲"),   // 炮/砲
        PAWN("兵", "卒");     // 兵/卒
        
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
    
    private Type type;
    private boolean isRed;  // true=红方, false=黑方
    private int row;        // 行 (0-9)
    private int col;        // 列 (0-8)
    
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
    
    public Color getColor() {
        return isRed ? Color.RED : Color.BLACK;
    }
    
    @Override
    public String toString() {
        return getName();
    }
}
