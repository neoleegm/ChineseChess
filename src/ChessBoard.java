import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * 棋盘类
 * 实现中国象棋的棋盘逻辑和所有走棋规则
 *
 * 线程安全：访问或修改棋盘状态的公开方法均为 synchronized。
 * 界面事件线程操作对局棋盘，AI 后台线程操作克隆棋盘，可安全并发。
 */
public class ChessBoard implements Cloneable {
    public static final int ROWS = 10;  // 10 行
    public static final int COLS = 9;   // 9 列

    // 棋盘格子
    private ChessPiece[][] board;

    // 所有棋子列表
    private List<ChessPiece> pieces;

    // 当前轮到红方走棋
    private boolean redTurn;

    // 走棋历史（用于悔棋）
    private List<MoveRecord> history;

    // 局面重复计数（用于重复局面裁决与 AI 避免长将）
    private HashMap<Long, Integer> positionCounts;

    // Zobrist 哈希（用于置换表）
    private long zobristKey;

    // 重复局面裁决结果（缓存：仅在 movePiece 触发三次重复时重算）
    private RepetitionOutcome repetitionOutcome = RepetitionOutcome.NONE;

    private static final long ZOBRIST_RED_TURN;
    private static final long[][][] ZOBRIST_PIECE = new long[2][7][90];

    static {
        java.util.Random rand = new java.util.Random(0x5DEECE66DL);
        for (int color = 0; color < 2; color++) {
            for (int type = 0; type < 7; type++) {
                for (int sq = 0; sq < 90; sq++) {
                    ZOBRIST_PIECE[color][type][sq] = rand.nextLong();
                }
            }
        }
        ZOBRIST_RED_TURN = rand.nextLong();
    }

    /**
     * 重复局面裁决结果：同一局面（含行棋方）第三次出现时，
     * 循环中每步都将军的一方判负（长将作负），其余情况判和棋。
     */
    public enum RepetitionOutcome {
        NONE,        // 未触发重复裁决
        DRAW,        // 三次重复，和棋
        RED_LOSES,   // 红方长将作负
        BLACK_LOSES  // 黑方长将作负
    }

    public ChessBoard() {
        board = new ChessPiece[ROWS][COLS];
        pieces = new ArrayList<>();
        history = new ArrayList<>();
        positionCounts = new HashMap<>();
        redTurn = true;
        initBoard();
        positionCounts.put(zobristKey, 1);
    }

    private ChessBoard(ChessPiece[][] board, List<ChessPiece> pieces, boolean redTurn,
                       List<MoveRecord> history, long zobristKey, HashMap<Long, Integer> positionCounts,
                       RepetitionOutcome repetitionOutcome) {
        this.board = board;
        this.pieces = pieces;
        this.redTurn = redTurn;
        this.history = new ArrayList<>(history);
        this.zobristKey = zobristKey;
        this.positionCounts = new HashMap<>(positionCounts);
        this.repetitionOutcome = repetitionOutcome;
    }

    /**
     * 初始化棋盘，放置所有棋子
     * 标准中国象棋初始布局
     */
    private void initBoard() {
        // 红方在下方（行 5-9）
        // 车
        addPiece(ChessPiece.Type.ROOK, true, 9, 0);
        addPiece(ChessPiece.Type.ROOK, true, 9, 8);
        // 马
        addPiece(ChessPiece.Type.HORSE, true, 9, 1);
        addPiece(ChessPiece.Type.HORSE, true, 9, 7);
        // 相
        addPiece(ChessPiece.Type.ELEPHANT, true, 9, 2);
        addPiece(ChessPiece.Type.ELEPHANT, true, 9, 6);
        // 仕
        addPiece(ChessPiece.Type.ADVISOR, true, 9, 3);
        addPiece(ChessPiece.Type.ADVISOR, true, 9, 5);
        // 帅
        addPiece(ChessPiece.Type.KING, true, 9, 4);
        // 炮
        addPiece(ChessPiece.Type.CANNON, true, 7, 1);
        addPiece(ChessPiece.Type.CANNON, true, 7, 7);
        // 兵
        for (int col = 0; col < COLS; col += 2) {
            addPiece(ChessPiece.Type.PAWN, true, 6, col);
        }

        // 黑方在上方（行 0-4）
        // 车
        addPiece(ChessPiece.Type.ROOK, false, 0, 0);
        addPiece(ChessPiece.Type.ROOK, false, 0, 8);
        // 马
        addPiece(ChessPiece.Type.HORSE, false, 0, 1);
        addPiece(ChessPiece.Type.HORSE, false, 0, 7);
        // 象
        addPiece(ChessPiece.Type.ELEPHANT, false, 0, 2);
        addPiece(ChessPiece.Type.ELEPHANT, false, 0, 6);
        // 士
        addPiece(ChessPiece.Type.ADVISOR, false, 0, 3);
        addPiece(ChessPiece.Type.ADVISOR, false, 0, 5);
        // 将
        addPiece(ChessPiece.Type.KING, false, 0, 4);
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
        zobristKey ^= ZOBRIST_PIECE[isRed ? 0 : 1][type.ordinal()][row * COLS + col];
    }

    public synchronized ChessPiece getPiece(int row, int col) {
        if (!isValidPos(row, col)) return null;
        return board[row][col];
    }

    public synchronized boolean isRedTurn() {
        return redTurn;
    }

    public boolean isValidPos(int row, int col) {
        return row >= 0 && row < ROWS && col >= 0 && col < COLS;
    }

    /**
     * 检查是否可以移动
     */
    public synchronized boolean canMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (!isValidPos(fromRow, fromCol) || !isValidPos(toRow, toCol)) {
            return false;
        }
        if (fromRow == toRow && fromCol == toCol) {
            return false;
        }

        ChessPiece piece = board[fromRow][fromCol];
        if (piece == null) {
            return false;
        }

        // 不能吃自己的子
        ChessPiece target = board[toRow][toCol];
        if (target != null && target.isRed() == piece.isRed()) {
            return false;
        }

        return isValidMove(piece, fromRow, fromCol, toRow, toCol);
    }

    /**
     * 检查是否是真正合法走法：满足棋子走法，且不会让己方被将军或将帅照面。
     */
    public synchronized boolean isLegalMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (!canMove(fromRow, fromCol, toRow, toCol)) {
            return false;
        }

        ChessPiece piece = board[fromRow][fromCol];
        MoveRecord record = makeMove(new Move(fromRow, fromCol, toRow, toCol));
        boolean legal = !isKingAttacked(piece.isRed());
        undoMove(record);
        return legal;
    }

    public synchronized boolean isLegalMove(Move move) {
        return move != null && isLegalMove(move.fromRow, move.fromCol, move.toRow, move.toCol);
    }

    /**
     * 获取某方全部合法走法。
     */
    public synchronized List<Move> getLegalMoves(boolean isRed) {
        List<Move> moves = new ArrayList<>();

        for (ChessPiece piece : getPieces()) {
            if (piece.isRed() != isRed) continue;

            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    if (isLegalMove(piece.getRow(), piece.getCol(), row, col)) {
                        moves.add(new Move(piece.getRow(), piece.getCol(), row, col));
                    }
                }
            }
        }

        return moves;
    }

    /**
     * 检查是否符合棋子走法规则（不考虑将军）
     */
    private boolean isValidMove(ChessPiece piece, int fromRow, int fromCol, int toRow, int toCol) {
        return switch (piece.getType()) {
            case KING -> isValidKingMove(piece, fromRow, fromCol, toRow, toCol);
            case ADVISOR -> isValidAdvisorMove(piece, fromRow, fromCol, toRow, toCol);
            case ELEPHANT -> isValidElephantMove(piece, fromRow, fromCol, toRow, toCol);
            case HORSE -> isValidHorseMove(fromRow, fromCol, toRow, toCol);
            case ROOK -> isValidRookMove(fromRow, fromCol, toRow, toCol);
            case CANNON -> isValidCannonMove(fromRow, fromCol, toRow, toCol);
            case PAWN -> isValidPawnMove(piece, fromRow, fromCol, toRow, toCol);
        };
    }

    /**
     * 帅的走法：九宫内，一步一格，不能出宫
     */
    private boolean isValidKingMove(ChessPiece piece, int fromRow, int fromCol, int toRow, int toCol) {
        // 必须在九宫内（列 3-5）
        if (toCol < 3 || toCol > 5) return false;
        // 红方九宫：行 7-9，黑方九宫：行 0-2
        if (piece.isRed()) {
            if (toRow < 7 || toRow > 9) return false;
        } else {
            if (toRow < 0 || toRow > 2) return false;
        }
        // 一步一格
        int dr = Math.abs(toRow - fromRow);
        int dc = Math.abs(toCol - fromCol);
        return (dr == 1 && dc == 0) || (dr == 0 && dc == 1);
    }

    /**
     * 士的走法：九宫内，斜线一步
     */
    private boolean isValidAdvisorMove(ChessPiece piece, int fromRow, int fromCol, int toRow, int toCol) {
        // 必须在九宫内
        if (toCol < 3 || toCol > 5) return false;
        if (piece.isRed()) {
            if (toRow < 7 || toRow > 9) return false;
        } else {
            if (toRow < 0 || toRow > 2) return false;
        }
        // 斜线一步
        return Math.abs(toRow - fromRow) == 1 && Math.abs(toCol - fromCol) == 1;
    }

    /**
     * 相的走法：走田字，不能过河，不能被塞象眼
     */
    private boolean isValidElephantMove(ChessPiece piece, int fromRow, int fromCol, int toRow, int toCol) {
        // 不能过河
        if (piece.isRed()) {
            if (toRow < 5) return false;  // 红方相不能过楚河（行 < 5）
        } else {
            if (toRow > 4) return false;  // 黑方象不能过汉界（行 > 4）
        }
        // 走田字
        int dr = Math.abs(toRow - fromRow);
        int dc = Math.abs(toCol - fromCol);
        if (dr != 2 || dc != 2) return false;
        // 检查象眼
        int eyeRow = (fromRow + toRow) / 2;
        int eyeCol = (fromCol + toCol) / 2;
        return board[eyeRow][eyeCol] == null;
    }

    /**
     * 马的走法：走日字，不能被蹩马腿
     */
    private boolean isValidHorseMove(int fromRow, int fromCol, int toRow, int toCol) {
        int dr = Math.abs(toRow - fromRow);
        int dc = Math.abs(toCol - fromCol);
        if (!((dr == 2 && dc == 1) || (dr == 1 && dc == 2))) {
            return false;
        }
        // 检查马腿
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
     * 车的走法：直线，不能越子
     */
    private boolean isValidRookMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (fromRow != toRow && fromCol != toCol) return false;
        return countBetween(fromRow, fromCol, toRow, toCol) == 0;
    }

    /**
     * 炮的走法：直线，吃子需要隔一个子，不吃子不能隔子
     */
    private boolean isValidCannonMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (fromRow != toRow && fromCol != toCol) return false;
        int count = countBetween(fromRow, fromCol, toRow, toCol);
        ChessPiece target = board[toRow][toCol];
        if (target == null) {
            return count == 0;  // 不吃子，中间不能有子
        } else {
            return count == 1;  // 吃子，中间必须隔一个子
        }
    }

    /**
     * 卒/兵的走法：
     * - 没过河只能向前走
     * - 过河后可以向前或横移，不能后退
     */
    private boolean isValidPawnMove(ChessPiece piece, int fromRow, int fromCol, int toRow, int toCol) {
        int dr = toRow - fromRow;
        int dc = Math.abs(toCol - fromCol);

        // 红方向上走（行减小），黑方向下走（行增大）
        if (piece.isRed()) {
            if (dr > 0) return false;  // 红方不能后退
        } else {
            if (dr < 0) return false;  // 黑方不能后退
        }

        // 是否过河
        boolean crossedRiver = piece.isRed() ? fromRow <= 4 : fromRow >= 5;

        if (crossedRiver) {
            // 过河后可以向前或横移
            return (Math.abs(dr) == 1 && dc == 0) || (dr == 0 && dc == 1);
        } else {
            // 没过河只能向前走
            return Math.abs(dr) == 1 && dc == 0;
        }
    }

    /**
     * 计算两点之间（不包括端点）有多少个棋子
     */
    private int countBetween(int fromRow, int fromCol, int toRow, int toCol) {
        int count = 0;
        if (fromRow == toRow) {
            // 横向
            int minCol = Math.min(fromCol, toCol);
            int maxCol = Math.max(fromCol, toCol);
            for (int col = minCol + 1; col < maxCol; col++) {
                if (board[fromRow][col] != null) count++;
            }
        } else if (fromCol == toCol) {
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
     * 执行走棋（校验规则、写入历史并维护重复局面计数）
     */
    public synchronized boolean movePiece(int fromRow, int fromCol, int toRow, int toCol) {
        ChessPiece piece = board[fromRow][fromCol];
        if (piece == null || piece.isRed() != redTurn) {
            return false;
        }
        if (!isLegalMove(fromRow, fromCol, toRow, toCol)) {
            return false;
        }

        // 执行移动
        MoveRecord record = makeMove(new Move(fromRow, fromCol, toRow, toCol));
        // 记录这步是否将军（此时 redTurn 已切换到对方）
        record.givesCheck = isKingAttacked(redTurn);
        history.add(record);
        positionCounts.merge(zobristKey, 1, Integer::sum);
        // 同一局面第三次出现：裁决长将作负或和棋
        if (positionCounts.getOrDefault(zobristKey, 0) >= 3) {
            repetitionOutcome = adjudicateRepetition();
        }
        return true;
    }

    /**
     * 裁决当前局面（已出现至少三次）的重复结果。
     * 在克隆棋盘上回溯历史，找出当前局面的前两次出现，取出循环区间的全部着法：
     * 区间内某方每步都将军而对方并非每步将军，则判该方长将作负；否则判和棋。
     */
    private RepetitionOutcome adjudicateRepetition() {
        long targetKey = zobristKey;
        ChessBoard scratch = clone();
        List<MoveRecord> segment = new ArrayList<>();
        int found = 0;
        while (!scratch.history.isEmpty()) {
            MoveRecord record = scratch.history.remove(scratch.history.size() - 1);
            scratch.undoMove(record);
            segment.add(0, record);
            if (scratch.zobristKey == targetKey && ++found == 2) {
                break;
            }
        }
        if (found < 2) {
            // 计数与历史对不上，理论上不会发生；保守判和
            return RepetitionOutcome.DRAW;
        }

        int redMoves = 0, redChecks = 0, blackMoves = 0, blackChecks = 0;
        for (MoveRecord r : segment) {
            if (r.piece.isRed()) {
                redMoves++;
                if (r.givesCheck) redChecks++;
            } else {
                blackMoves++;
                if (r.givesCheck) blackChecks++;
            }
        }
        boolean redAllCheck = redMoves > 0 && redChecks == redMoves;
        boolean blackAllCheck = blackMoves > 0 && blackChecks == blackMoves;
        if (redAllCheck && !blackAllCheck) return RepetitionOutcome.RED_LOSES;
        if (blackAllCheck && !redAllCheck) return RepetitionOutcome.BLACK_LOSES;
        return RepetitionOutcome.DRAW;
    }

    /**
     * 仅执行移动并切换行棋方，不检查规则也不写历史（用于 AI 搜索）。
     */
    public synchronized MoveRecord makeMove(Move move) {
        ChessPiece piece = board[move.fromRow][move.fromCol];
        ChessPiece captured = board[move.toRow][move.toCol];

        zobristKey ^= ZOBRIST_PIECE[piece.isRed() ? 0 : 1][piece.getType().ordinal()][move.fromRow * COLS + move.fromCol];
        if (captured != null) {
            pieces.remove(captured);
            zobristKey ^= ZOBRIST_PIECE[captured.isRed() ? 0 : 1][captured.getType().ordinal()][move.toRow * COLS + move.toCol];
        }
        zobristKey ^= ZOBRIST_PIECE[piece.isRed() ? 0 : 1][piece.getType().ordinal()][move.toRow * COLS + move.toCol];
        zobristKey ^= ZOBRIST_RED_TURN;

        board[move.fromRow][move.fromCol] = null;
        board[move.toRow][move.toCol] = piece;
        piece.setPosition(move.toRow, move.toCol);

        MoveRecord record = new MoveRecord(move, piece, captured, redTurn);
        redTurn = !redTurn;
        return record;
    }

    public synchronized void undoMove(MoveRecord record) {
        if (record == null) return;

        zobristKey ^= ZOBRIST_PIECE[record.piece.isRed() ? 0 : 1][record.piece.getType().ordinal()][record.move.toRow * COLS + record.move.toCol];
        if (record.captured != null) {
            zobristKey ^= ZOBRIST_PIECE[record.captured.isRed() ? 0 : 1][record.captured.getType().ordinal()][record.move.toRow * COLS + record.move.toCol];
        }
        zobristKey ^= ZOBRIST_PIECE[record.piece.isRed() ? 0 : 1][record.piece.getType().ordinal()][record.move.fromRow * COLS + record.move.fromCol];
        zobristKey ^= ZOBRIST_RED_TURN;

        board[record.move.toRow][record.move.toCol] = record.captured;
        board[record.move.fromRow][record.move.fromCol] = record.piece;
        record.piece.setPosition(record.move.fromRow, record.move.fromCol);

        if (record.captured != null) {
            record.captured.setPosition(record.move.toRow, record.move.toCol);
            pieces.add(record.captured);
        }

        redTurn = record.redTurnBefore;
    }

    public synchronized void doNullMove() {
        redTurn = !redTurn;
        zobristKey ^= ZOBRIST_RED_TURN;
    }

    public synchronized void undoNullMove() {
        redTurn = !redTurn;
        zobristKey ^= ZOBRIST_RED_TURN;
    }

    /**
     * 悔棋
     */
    public synchronized boolean undo() {
        if (history.isEmpty()) return false;
        MoveRecord move = history.remove(history.size() - 1);
        positionCounts.merge(zobristKey, -1, (oldVal, one) -> {
            int newVal = oldVal + one;
            return newVal <= 0 ? null : newVal;
        });
        undoMove(move);
        // 撤销后重复计数已下降，此前的重复裁决不再成立
        repetitionOutcome = RepetitionOutcome.NONE;
        return true;
    }

    public synchronized int getHistorySize() {
        return history.size();
    }

    /**
     * 获取对局全部历史着法（副本），用于向外部引擎同步对局历史。
     */
    public synchronized List<Move> getHistoryMoves() {
        List<Move> moves = new ArrayList<>(history.size());
        for (MoveRecord record : history) {
            moves.add(record.move);
        }
        return moves;
    }

    /**
     * 获取对局历史记录（副本，含每手是否将军），供内置引擎重建搜索路径。
     */
    public synchronized List<MoveRecord> getHistoryRecords() {
        return new ArrayList<>(history);
    }

    /**
     * 检查某方的将/帅是否被将军
     */
    public synchronized boolean isKingAttacked(boolean isRedKing) {
        // 找到将/帅的位置
        int kingRow = -1, kingCol = -1;
        for (ChessPiece piece : pieces) {
            if (piece.getType() == ChessPiece.Type.KING && piece.isRed() == isRedKing) {
                kingRow = piece.getRow();
                kingCol = piece.getCol();
                break;
            }
        }
        if (kingRow == -1) return true;  // 将/帅不存在，视为被将死

        if (areKingsFacing()) {
            return true;
        }

        // 检查对方是否有棋子可以吃掉将/帅
        for (ChessPiece piece : pieces) {
            if (piece.isRed() != isRedKing) {
                if (isValidMove(piece, piece.getRow(), piece.getCol(), kingRow, kingCol)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean areKingsFacing() {
        ChessPiece redKing = null;
        ChessPiece blackKing = null;
        for (ChessPiece piece : pieces) {
            if (piece.getType() == ChessPiece.Type.KING) {
                if (piece.isRed()) {
                    redKing = piece;
                } else {
                    blackKing = piece;
                }
            }
        }
        if (redKing == null || blackKing == null || redKing.getCol() != blackKing.getCol()) {
            return false;
        }

        int col = redKing.getCol();
        int minRow = Math.min(redKing.getRow(), blackKing.getRow());
        int maxRow = Math.max(redKing.getRow(), blackKing.getRow());
        for (int row = minRow + 1; row < maxRow; row++) {
            if (board[row][col] != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查某方是否被将死
     */
    public synchronized boolean isCheckmate(boolean isRed) {
        if (!isKingAttacked(isRed)) return false;

        return getLegalMoves(isRed).isEmpty();
    }

    /**
     * 检查游戏是否结束：某方被将死/困毙、将/帅被吃，或重复局面已被裁决
     */
    public synchronized boolean isGameOver() {
        if (repetitionOutcome != RepetitionOutcome.NONE) {
            return true;
        }
        boolean redKing = false, blackKing = false;
        for (ChessPiece piece : pieces) {
            if (piece.getType() == ChessPiece.Type.KING) {
                if (piece.isRed()) redKing = true;
                else blackKing = true;
            }
        }
        return !redKing || !blackKing || getLegalMoves(redTurn).isEmpty();
    }

    /**
     * 获取对局结果文案（对局未结束时返回 null）。
     * 覆盖：将/帅被吃、将死、困毙（无棋可走判负）、三次重复和棋、长将作负。
     */
    public synchronized String getWinner() {
        switch (repetitionOutcome) {
            case DRAW:
                return "和棋：三次重复局面";
            case RED_LOSES:
                return "红方长将作负，黑方获胜！";
            case BLACK_LOSES:
                return "黑方长将作负，红方获胜！";
            default:
                break;
        }

        boolean redKing = false, blackKing = false;
        for (ChessPiece piece : pieces) {
            if (piece.getType() == ChessPiece.Type.KING) {
                if (piece.isRed()) redKing = true;
                else blackKing = true;
            }
        }
        if (!redKing) return "黑方获胜！";
        if (!blackKing) return "红方获胜！";
        if (getLegalMoves(redTurn).isEmpty()) {
            String loser = redTurn ? "红方" : "黑方";
            String winner = redTurn ? "黑方获胜！" : "红方获胜！";
            return loser + (isKingAttacked(redTurn) ? "被将死，" : "困毙，") + winner;
        }
        return null;
    }

    public synchronized void reset() {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                board[row][col] = null;
            }
        }
        pieces.clear();
        history.clear();
        positionCounts.clear();
        redTurn = true;
        zobristKey = 0;
        repetitionOutcome = RepetitionOutcome.NONE;
        initBoard();
        positionCounts.put(zobristKey, 1);
    }

    public synchronized List<ChessPiece> getPieces() {
        return new ArrayList<>(pieces);
    }

    static ChessBoard createEmptyForTesting() {
        ChessBoard board = new ChessBoard();
        board.clearForTesting();
        return board;
    }

    void clearForTesting() {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                board[row][col] = null;
            }
        }
        pieces.clear();
        history.clear();
        positionCounts.clear();
        redTurn = true;
        zobristKey = 0;
        repetitionOutcome = RepetitionOutcome.NONE;
    }

    void addPieceForTesting(ChessPiece.Type type, boolean isRed, int row, int col) {
        addPiece(type, isRed, row, col);
    }

    void setRedTurnForTesting(boolean redTurn) {
        this.redTurn = redTurn;
    }

    @Override
    public synchronized ChessBoard clone() {
        ChessPiece[][] newBoard = new ChessPiece[ROWS][COLS];
        List<ChessPiece> newPieces = new ArrayList<>();
        IdentityHashMap<ChessPiece, ChessPiece> pieceMap = new IdentityHashMap<>();

        for (ChessPiece piece : pieces) {
            ChessPiece newPiece = piece.clone();
            newPieces.add(newPiece);
            newBoard[newPiece.getRow()][newPiece.getCol()] = newPiece;
            pieceMap.put(piece, newPiece);
        }

        // 历史记录中的棋子引用重映射到克隆棋盘，避免在克隆上悔棋时改到原棋盘
        List<MoveRecord> newHistory = new ArrayList<>(history.size());
        for (MoveRecord record : history) {
            ChessPiece mappedPiece = pieceMap.get(record.piece);
            ChessPiece mappedCaptured = record.captured == null ? null : record.captured.clone();
            newHistory.add(new MoveRecord(record.move, mappedPiece, mappedCaptured,
                    record.redTurnBefore, record.givesCheck));
        }

        return new ChessBoard(newBoard, newPieces, redTurn, newHistory, zobristKey, positionCounts, repetitionOutcome);
    }

    public synchronized long getZobristKey() {
        return zobristKey;
    }

    public synchronized int getRepetitionCount() {
        return positionCounts.getOrDefault(zobristKey, 0);
    }

    /**
     * 获取当前重复局面裁决结果（未触发时为 NONE）
     */
    public synchronized RepetitionOutcome getRepetitionOutcome() {
        return repetitionOutcome;
    }

    public synchronized java.util.Map<Long, Integer> getPositionCounts() {
        return new java.util.HashMap<>(positionCounts);
    }

    /**
     * 走棋记录
     */
    public static class MoveRecord {
        final Move move;
        final ChessPiece piece;
        final ChessPiece captured;
        final boolean redTurnBefore;
        // 这步棋是否将军（仅在 movePiece 中记录，AI 搜索路径不维护）
        boolean givesCheck;

        MoveRecord(Move move, ChessPiece piece, ChessPiece captured, boolean redTurnBefore) {
            this(move, piece, captured, redTurnBefore, false);
        }

        MoveRecord(Move move, ChessPiece piece, ChessPiece captured, boolean redTurnBefore, boolean givesCheck) {
            this.move = move;
            this.piece = piece;
            this.captured = captured;
            this.redTurnBefore = redTurnBefore;
            this.givesCheck = givesCheck;
        }
    }
}
