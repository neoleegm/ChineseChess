import java.util.Objects;

/**
 * Immutable board move shared by UI, AI, and engine adapters.
 */
public final class Move {
    public final int fromRow;
    public final int fromCol;
    public final int toRow;
    public final int toCol;

    public Move(int fromRow, int fromCol, int toRow, int toCol) {
        this.fromRow = fromRow;
        this.fromCol = fromCol;
        this.toRow = toRow;
        this.toCol = toCol;
    }

    public int[] toArray() {
        return new int[]{fromRow, fromCol, toRow, toCol};
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Move other)) return false;
        return fromRow == other.fromRow &&
            fromCol == other.fromCol &&
            toRow == other.toRow &&
            toCol == other.toCol;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromRow, fromCol, toRow, toCol);
    }

    @Override
    public String toString() {
        return String.format("[%d,%d]->[%d,%d]", fromRow, fromCol, toRow, toCol);
    }
}
