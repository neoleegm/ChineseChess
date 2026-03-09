import java.util.ArrayList;
import java.util.List;

/**
 * 象棋棋盘逻辑类
 */
public class ChessBoard {
    public static final int ROWS = 10;
    public static final int COLS = 9;
    
    private ChessPiece[][] board;  // 棋盘数组
    private List<ChessPiece> pieces;  // 所有棋子列表
    private boolean redTurn;  // 轮到红方走棋
    
    public ChessBoard() {
        board = new ChessPiece[ROWS][COLS];
        pieces = new ArrayList<>();
        redTurn = true;  // 红方先行
        initBoard();
    }
    
    /**
     * 初始化棋盘，放置所有棋子
     */
    private void initBoard() {
        // 红方棋子（下方）
        // 车马相仕帅仕相马车
        addPiece(ChessPiece.Type.ROOK, true, 9, 0);
        addPiece(ChessPiece.Type.HORSE, true, 9, 1);
        addPiece(ChessPiece.Type.ELEPHANT, true, 9, 2);
        addPiece(ChessPiece.Type.ADVISOR, true, 9, 3);
        addPiece(ChessPiece.Type.KING, true, 9, 4);
        addPiece(ChessPiece.Type.ADVISOR, true, 9, 5);
        addPiece(ChessPiece.Type.ELEPHANT, true, 9, 6);
        addPiece(ChessPiece.Type.HORSE, true, 9, 7);
        addPiece(ChessPiece.Type.ROOK, true, 9, 8);
        
        // 炮
        addPiece(ChessPiece.Type.CANNON, true, 7, 1);
        addPiece(ChessPiece.Type.CANNON, true, 7, 7);
        
        // 兵
        for (int col = 0; col < COLS; col += 2) {
            addPiece(ChessPiece.Type.PAWN, true, 6, col);
        }
        
        // 黑方棋子（上方）
        // 车马象士将士象马车
        addPiece(ChessPiece.Type.ROOK, false, 0, 0);
        addPiece(ChessPiece.Type.HORSE, false, 0, 1);
        addPiece(ChessPiece.Type.ELEPHANT, false, 0, 2);
        addPiece(ChessPiece.Type.ADVISOR, false, 0, 3);
        addPiece(ChessPiece.Type.KING, false, 0, 4);
        addPiece(ChessPiece.Type.ADVISOR, false, 0, 5);
        addPiece(ChessPiece.Type.ELEPHANT, false, 0, 6);
        addPiece(ChessPiece.Type.HORSE, false, 0, 7);
        addPiece(ChessPiece.Type.ROOK, false, 0, 8);
        
        // 砲
        addPiece(ChessPiece.Type.CANNON, false, 2, 1);
        addPiece(ChessPiece.Type.CANNON, false, 2, 7);
        
        // 卒
        for (int col = 0; col < COLS; col += 2) {
            addPiece(ChessPiece.Type.PAWN, false, 3, col);
        }
    }
    
    private void addPiece(ChessPiece.Type type, boolean isRed, int row, int col) {
        ChessPiece piece = new ChessPiece(type, isRed, row, col);
        pieces.add(piece);
        board[row][col] = piece;
    }
    
    public ChessPiece getPiece(int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) {
            return null;
        }
        return board[row][col];
    }
    
    public boolean isRedTurn() {
        return redTurn;
    }
    
    /**
     * 移动棋子
     */
    public boolean movePiece(int fromRow, int fromCol, int toRow, int toCol) {
        ChessPiece piece = board[fromRow][fromCol];
        if (piece == null) {
            return false;
        }
        
        // 检查是否轮到该方走棋
        if (piece.isRed() != redTurn) {
            return false;
        }
        
        // 检查目标位置是否有己方棋子
        ChessPiece target = board[toRow][toCol];
        if (target != null && target.isRed() == piece.isRed()) {
            return false;
        }
        
        // 验证走法是否合法
        if (!isValidMove(piece, fromRow, fromCol, toRow, toCol)) {
            return false;
        }
        
        // 执行移动
        board[fromRow][fromCol] = null;
        if (target != null) {
            pieces.remove(target);
        }
        board[toRow][toCol] = piece;
        piece.setPosition(toRow, toCol);
        
        // 切换回合
        redTurn = !redTurn;
        
        return true;
    }
    
    /**
     * 验证走法是否合法
     */
    private boolean isValidMove(ChessPiece piece, int fromRow, int fromCol, int toRow, int toCol) {
        switch (piece.getType()) {
            case KING:
                return isValidKingMove(piece, fromRow, fromCol, toRow, toCol);
            case ADVISOR:
                return isValidAdvisorMove(piece, fromRow, fromCol, toRow, toCol);
            case ELEPHANT:
                return isValidElephantMove(piece, fromRow, fromCol, toRow, toCol);
            case HORSE:
                return isValidHorseMove(fromRow, fromCol, toRow, toCol);
            case ROOK:
                return isValidRookMove(fromRow, fromCol, toRow, toCol);
            case CANNON:
                return isValidCannonMove(fromRow, fromCol, toRow, toCol);
            case PAWN:
                return isValidPawnMove(piece, fromRow, fromCol, toRow, toCol);
            default:
                return false;
        }
    }
    
    /**
     * 帅的走法：只能在九宫内，一格一格走
     */
    private boolean isValidKingMove(ChessPiece piece, int fromRow, int fromCol, int toRow, int toCol) {
        // 检查是否在九宫内
        if (toCol < 3 || toCol > 5) return false;
        if (piece.isRed()) {
            if (toRow < 7 || toRow > 9) return false;
        } else {
            if (toRow < 0 || toRow > 2) return false;
        }
        
        int dr = Math.abs(toRow - fromRow);
        int dc = Math.abs(toCol - fromCol);
        
        // 一格一格走
        return (dr == 1 && dc == 0) || (dr == 0 && dc == 1);
    }
    
    /**
     * 仕的走法：只能在九宫内，斜线走一格
     */
    private boolean isValidAdvisorMove(ChessPiece piece, int fromRow, int fromCol, int toRow, int toCol) {
        // 检查是否在九宫内
        if (toCol < 3 || toCol > 5) return false;
        if (piece.isRed()) {
            if (toRow < 7 || toRow > 9) return false;
        } else {
            if (toRow < 0 || toRow > 2) return false;
        }
        
        int dr = Math.abs(toRow - fromRow);
        int dc = Math.abs(toCol - fromCol);
        
        // 斜线走一格
        return dr == 1 && dc == 1;
    }
    
    /**
     * 相的走法：不能过河，走田字
     */
    private boolean isValidElephantMove(ChessPiece piece, int fromRow, int fromCol, int toRow, int toCol) {
        // 不能过河
        if (piece.isRed()) {
            if (toRow < 5) return false;
        } else {
            if (toRow > 4) return false;
        }
        
        int dr = Math.abs(toRow - fromRow);
        int dc = Math.abs(toCol - fromCol);
        
        // 走田字
        if (dr != 2 || dc != 2) return false;
        
        // 检查象眼是否被塞
        int eyeRow = (fromRow + toRow) / 2;
        int eyeCol = (fromCol + toCol) / 2;
        return board[eyeRow][eyeCol] == null;
    }
    
    /**
     * 马的走法：走日字
     */
    private boolean isValidHorseMove(int fromRow, int fromCol, int toRow, int toCol) {
        int dr = Math.abs(toRow - fromRow);
        int dc = Math.abs(toCol - fromCol);
        
        // 走日字
        if (!((dr == 2 && dc == 1) || (dr == 1 && dc == 2))) {
            return false;
        }
        
        // 检查是否蹩马腿
        int legRow, legCol;
        if (dr == 2) {
            legRow = (fromRow + toRow) / 2;
            legCol = fromCol;
        } else {
            legRow = fromRow;
            legCol = (fromCol + toCol) / 2;
        }
        return board[legRow][legCol] == null;
    }
    
    /**
     * 车的走法：直线走，不能越过棋子
     */
    private boolean isValidRookMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (fromRow != toRow && fromCol != toCol) return false;
        
        return countPiecesBetween(fromRow, fromCol, toRow, toCol) == 0;
    }
    
    /**
     * 炮的走法：直线走，吃子时必须隔一个棋子
     */
    private boolean isValidCannonMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (fromRow != toRow && fromCol != toCol) return false;
        
        int piecesBetween = countPiecesBetween(fromRow, fromCol, toRow, toCol);
        ChessPiece target = board[toRow][toCol];
        
        if (target == null) {
            // 不吃子，中间不能有棋子
            return piecesBetween == 0;
        } else {
            // 吃子，中间必须隔一个棋子
            return piecesBetween == 1;
        }
    }
    
    /**
     * 兵/卒的走法
     */
    private boolean isValidPawnMove(ChessPiece piece, int fromRow, int fromCol, int toRow, int toCol) {
        int dr = toRow - fromRow;
        int dc = Math.abs(toCol - fromCol);
        
        // 只能向前走或横走
        if (piece.isRed()) {
            // 红方兵向上走（行号减小）
            if (dr > 0) return false;
        } else {
            // 黑方卒向下走（行号增大）
            if (dr < 0) return false;
        }
        
        // 未过河只能向前走
        boolean crossedRiver = piece.isRed() ? fromRow <= 4 : fromRow >= 5;
        
        if (!crossedRiver) {
            // 未过河，只能向前走一格
            return Math.abs(dr) == 1 && dc == 0;
        } else {
            // 已过河，可以向前走或横走
            return (Math.abs(dr) == 1 && dc == 0) || (dr == 0 && dc == 1);
        }
    }
    
    /**
     * 计算两点之间有多少棋子（不包括端点）
     */
    private int countPiecesBetween(int fromRow, int fromCol, int toRow, int toCol) {
        int count = 0;
        
        if (fromRow == toRow) {
            // 横向
            int minCol = Math.min(fromCol, toCol);
            int maxCol = Math.max(fromCol, toCol);
            for (int col = minCol + 1; col < maxCol; col++) {
                if (board[fromRow][col] != null) count++;
            }
        } else {
            // 纵向
            int minRow = Math.min(fromRow, toRow);
            int maxRow = Math.max(fromRow, toRow);
            for (int row = minRow + 1; row < maxRow; row++) {
                if (board[row][fromCol] != null) count++;
            }
        }
        
        return count;
    }
    
    /**
     * 检查游戏是否结束（某方被将死）
     */
    public boolean isGameOver() {
        // 简化处理：检查是否还有帅/将
        boolean hasRedKing = false;
        boolean hasBlackKing = false;
        
        for (ChessPiece piece : pieces) {
            if (piece.getType() == ChessPiece.Type.KING) {
                if (piece.isRed()) {
                    hasRedKing = true;
                } else {
                    hasBlackKing = true;
                }
            }
        }
        
        return !hasRedKing || !hasBlackKing;
    }
    
    /**
     * 获取获胜方
     */
    public String getWinner() {
        boolean hasRedKing = false;
        boolean hasBlackKing = false;
        
        for (ChessPiece piece : pieces) {
            if (piece.getType() == ChessPiece.Type.KING) {
                if (piece.isRed()) {
                    hasRedKing = true;
                } else {
                    hasBlackKing = true;
                }
            }
        }
        
        if (!hasRedKing) return "黑方获胜！";
        if (!hasBlackKing) return "红方获胜！";
        return null;
    }
    
    /**
     * 重新开始游戏
     */
    public void reset() {
        // 清空棋盘
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                board[i][j] = null;
            }
        }
        pieces.clear();
        redTurn = true;
        initBoard();
    }
}
