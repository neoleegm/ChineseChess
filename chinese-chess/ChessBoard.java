import java.util.ArrayList;
import java.util.List;

/**
 * 象棋棋盘逻辑类
 */
public class ChessBoard implements Cloneable {
    public static final int ROWS = 10;
    public static final int COLS = 9;
    
    private ChessPiece[][] board;
    private List<ChessPiece> pieces;
    private boolean redTurn;
    
    // 历史记录用于悔棋
    private static class Move {
        int fromRow, fromCol, toRow, toCol;
        ChessPiece captured;
        Move(int fr, int fc, int tr, int tc, ChessPiece cap) {
            fromRow = fr; fromCol = fc; toRow = tr; toCol = tc; captured = cap;
        }
    }
    private List<Move> history = new ArrayList<>();
    
    public ChessBoard() {
        board = new ChessPiece[ROWS][COLS];
        pieces = new ArrayList<>();
        redTurn = true;
        initBoard();
    }
    
    private ChessBoard(ChessPiece[][] board, List<ChessPiece> pieces, boolean redTurn) {
        this.board = board;
        this.pieces = pieces;
        this.redTurn = redTurn;
    }
    
    private void initBoard() {
        int r = 9;
        addPiece(ChessPiece.Type.ROOK, true, r, 0);
        addPiece(ChessPiece.Type.HORSE, true, r, 1);
        addPiece(ChessPiece.Type.ELEPHANT, true, r, 2);
        addPiece(ChessPiece.Type.ADVISOR, true, r, 3);
        addPiece(ChessPiece.Type.KING, true, r, 4);
        addPiece(ChessPiece.Type.ADVISOR, true, r, 5);
        addPiece(ChessPiece.Type.ELEPHANT, true, r, 6);
        addPiece(ChessPiece.Type.HORSE, true, r, 7);
        addPiece(ChessPiece.Type.ROOK, true, r, 8);
        addPiece(ChessPiece.Type.CANNON, true, 7, 1);
        addPiece(ChessPiece.Type.CANNON, true, 7, 7);
        for (int c = 0; c < COLS; c += 2) addPiece(ChessPiece.Type.PAWN, true, 6, c);
        
        addPiece(ChessPiece.Type.ROOK, false, 0, 0);
        addPiece(ChessPiece.Type.HORSE, false, 0, 1);
        addPiece(ChessPiece.Type.ELEPHANT, false, 0, 2);
        addPiece(ChessPiece.Type.ADVISOR, false, 0, 3);
        addPiece(ChessPiece.Type.KING, false, 0, 4);
        addPiece(ChessPiece.Type.ADVISOR, false, 0, 5);
        addPiece(ChessPiece.Type.ELEPHANT, false, 0, 6);
        addPiece(ChessPiece.Type.HORSE, false, 0, 7);
        addPiece(ChessPiece.Type.ROOK, false, 0, 8);
        addPiece(ChessPiece.Type.CANNON, false, 2, 1);
        addPiece(ChessPiece.Type.CANNON, false, 2, 7);
        for (int c = 0; c < COLS; c += 2) addPiece(ChessPiece.Type.PAWN, false, 3, c);
    }
    
    private void addPiece(ChessPiece.Type type, boolean isRed, int row, int col) {
        ChessPiece piece = new ChessPiece(type, isRed, row, col);
        pieces.add(piece);
        board[row][col] = piece;
    }
    
    public ChessPiece getPiece(int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) return null;
        return board[row][col];
    }
    
    public boolean isRedTurn() { return redTurn; }
    
    public boolean canMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (!isValidPos(fromRow, fromCol) || !isValidPos(toRow, toCol)) return false;
        if (fromRow == toRow && fromCol == toCol) return false;
        
        ChessPiece piece = board[fromRow][fromCol];
        if (piece == null) return false;
        
        ChessPiece target = board[toRow][toCol];
        if (target != null && target.isRed() == piece.isRed()) return false;
        
        return isValidMove(piece, fromRow, fromCol, toRow, toCol);
    }
    
    public boolean movePiece(int fromRow, int fromCol, int toRow, int toCol) {
        ChessPiece piece = board[fromRow][fromCol];
        if (piece == null || piece.isRed() != redTurn) return false;
        if (!canMove(fromRow, fromCol, toRow, toCol)) return false;
        
        ChessPiece target = board[toRow][toCol];
        
        // 记录历史
        history.add(new Move(fromRow, fromCol, toRow, toCol, target));
        
        board[fromRow][fromCol] = null;
        if (target != null) pieces.remove(target);
        board[toRow][toCol] = piece;
        piece.setPosition(toRow, toCol);
        redTurn = !redTurn;
        
        return true;
    }
    
    /**
     * 悔棋 - 回退一步
     */
    public boolean undo() {
        if (history.isEmpty()) return false;
        
        Move move = history.remove(history.size() - 1);
        
        // 移动棋子回去
        ChessPiece piece = board[move.toRow][move.toCol];
        board[move.toRow][move.toCol] = null;
        board[move.fromRow][move.fromCol] = piece;
        piece.setPosition(move.fromRow, move.fromCol);
        
        // 恢复被吃掉的棋子
        if (move.captured != null) {
            pieces.add(move.captured);
            board[move.toRow][move.toCol] = move.captured;
        }
        
        // 切换回合回来
        redTurn = !redTurn;
        
        return true;
    }
    
    /**
     * 获取历史记录数量
     */
    public int getHistorySize() {
        return history.size();
    }
    
    private boolean isValidPos(int row, int col) {
        return row >= 0 && row < ROWS && col >= 0 && col < COLS;
    }
    
    private boolean isValidMove(ChessPiece piece, int fromRow, int fromCol, int toRow, int toCol) {
        return switch (piece.getType()) {
            case KING -> isValidKingMove(piece, toRow, toCol);
            case ADVISOR -> isValidAdvisorMove(piece, toRow, toCol);
            case ELEPHANT -> isValidElephantMove(piece, fromRow, fromCol, toRow, toCol);
            case HORSE -> isValidHorseMove(fromRow, fromCol, toRow, toCol);
            case ROOK -> isValidRookMove(fromRow, fromCol, toRow, toCol);
            case CANNON -> isValidCannonMove(fromRow, fromCol, toRow, toCol);
            case PAWN -> isValidPawnMove(piece, fromRow, fromCol, toRow, toCol);
        };
    }
    
    private boolean isValidKingMove(ChessPiece piece, int toRow, int toCol) {
        if (toCol < 3 || toCol > 5) return false;
        if (piece.isRed() ? (toRow < 7 || toRow > 9) : (toRow < 0 || toRow > 2)) return false;
        return true;
    }
    
    private boolean isValidAdvisorMove(ChessPiece piece, int toRow, int toCol) {
        if (toCol < 3 || toCol > 5) return false;
        if (piece.isRed() ? (toRow < 7 || toRow > 9) : (toRow < 0 || toRow > 2)) return false;
        return true;
    }
    
    private boolean isValidElephantMove(ChessPiece piece, int fromRow, int fromCol, int toRow, int toCol) {
        if (piece.isRed() ? (toRow < 5) : (toRow > 4)) return false;
        if (Math.abs(toRow - fromRow) != 2 || Math.abs(toCol - fromCol) != 2) return false;
        return board[(fromRow + toRow) / 2][(fromCol + toCol) / 2] == null;
    }
    
    private boolean isValidHorseMove(int fromRow, int fromCol, int toRow, int toCol) {
        int dr = Math.abs(toRow - fromRow), dc = Math.abs(toCol - fromCol);
        if (!((dr == 2 && dc == 1) || (dr == 1 && dc == 2))) return false;
        int legRow = (dr == 2) ? (fromRow + toRow) / 2 : fromRow;
        int legCol = (dc == 2) ? (fromCol + toCol) / 2 : fromCol;
        return board[legRow][legCol] == null;
    }
    
    private boolean isValidRookMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (fromRow != toRow && fromCol != toCol) return false;
        return countBetween(fromRow, fromCol, toRow, toCol) == 0;
    }
    
    private boolean isValidCannonMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (fromRow != toRow && fromCol != toCol) return false;
        int between = countBetween(fromRow, fromCol, toRow, toCol);
        ChessPiece target = board[toRow][toCol];
        return target == null ? between == 0 : between == 1;
    }
    
    private boolean isValidPawnMove(ChessPiece piece, int fromRow, int fromCol, int toRow, int toCol) {
        int dr = toRow - fromRow, dc = Math.abs(toCol - fromCol);
        if (piece.isRed() ? (dr > 0) : (dr < 0)) return false;
        boolean crossed = piece.isRed() ? fromRow <= 4 : fromRow >= 5;
        return crossed ? (Math.abs(dr) == 1 && dc == 0) || (dr == 0 && dc == 1)
                       : Math.abs(dr) == 1 && dc == 0;
    }
    
    private int countBetween(int fromRow, int fromCol, int toRow, int toCol) {
        int count = 0;
        if (fromRow == toRow) {
            for (int c = Math.min(fromCol, toCol) + 1; c < Math.max(fromCol, toCol); c++)
                if (board[fromRow][c] != null) count++;
        } else {
            for (int r = Math.min(fromRow, toRow) + 1; r < Math.max(fromRow, toRow); r++)
                if (board[r][fromCol] != null) count++;
        }
        return count;
    }
    
    public boolean isGameOver() {
        boolean hasRed = false, hasBlack = false;
        for (ChessPiece p : pieces) {
            if (p.getType() == ChessPiece.Type.KING) {
                if (p.isRed()) hasRed = true;
                else hasBlack = true;
            }
        }
        return !hasRed || !hasBlack;
    }
    
    public String getWinner() {
        boolean hasRed = false, hasBlack = false;
        for (ChessPiece p : pieces) {
            if (p.getType() == ChessPiece.Type.KING) {
                if (p.isRed()) hasRed = true;
                else hasBlack = true;
            }
        }
        if (!hasRed) return "黑方获胜！";
        if (!hasBlack) return "红方获胜！";
        return null;
    }
    
    public void reset() {
        for (int i = 0; i < ROWS; i++)
            for (int j = 0; j < COLS; j++)
                board[i][j] = null;
        pieces.clear();
        history.clear();
        redTurn = true;
        initBoard();
    }
    
    @Override
    public ChessBoard clone() {
        ChessPiece[][] newBoard = new ChessPiece[ROWS][COLS];
        List<ChessPiece> newPieces = new ArrayList<>();
        for (ChessPiece p : pieces) {
            ChessPiece np = new ChessPiece(p.getType(), p.isRed(), p.getRow(), p.getCol());
            newPieces.add(np);
            newBoard[p.getRow()][p.getCol()] = np;
        }
        return new ChessBoard(newBoard, newPieces, this.redTurn);
    }
}
