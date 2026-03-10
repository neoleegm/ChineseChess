/*
 * Decompiled with CFR 0.152.
 */
import java.awt.Color;

public class ChessPiece {
    private Type type;
    private boolean isRed;
    private int row;
    private int col;

    public ChessPiece(Type type, boolean bl, int n, int n2) {
        this.type = type;
        this.isRed = bl;
        this.row = n;
        this.col = n2;
    }

    public Type getType() {
        return this.type;
    }

    public boolean isRed() {
        return this.isRed;
    }

    public int getRow() {
        return this.row;
    }

    public int getCol() {
        return this.col;
    }

    public void setPosition(int n, int n2) {
        this.row = n;
        this.col = n2;
    }

    public String getName() {
        return this.type.getName(this.isRed);
    }

    public Color getColor() {
        return this.isRed ? Color.RED : Color.BLACK;
    }

    public String toString() {
        return this.getName();
    }

    public static enum Type {
        KING("\u5e25", "\u5c07"),
        ADVISOR("\u4ed5", "\u58eb"),
        ELEPHANT("\u76f8", "\u8c61"),
        HORSE("\u508c", "\u99ac"),
        ROOK("\u4fe5", "\u8eca"),
        CANNON("\u70ae", "\u7832"),
        PAWN("\u5175", "\u5352");

        private final String redName;
        private final String blackName;

        private Type(String string2, String string3) {
            this.redName = string2;
            this.blackName = string3;
        }

        public String getName(boolean bl) {
            return bl ? this.redName : this.blackName;
        }
    }
}
