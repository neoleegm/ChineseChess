package xiangqi.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 棋盘与全部走棋规则。
 *
 * <h2>线程模型</h2>
 * 本类<strong>不是</strong>线程安全的，也不再使用 {@code synchronized}：
 * 搜索热路径上每个节点要做上千次状态读写，加锁开销会直接吃掉搜索深度。
 * 约定是「一个棋盘只属于一个线程」——界面线程持有对局棋盘，AI 线程使用
 * {@link #copy()} 出来的独立副本，两者互不共享可变状态。
 *
 * <h2>性能要点</h2>
 * <ul>
 *   <li>格位用一维数组 {@code cells[90]}，避免二维数组寻址与空指针层数；</li>
 *   <li>将帅位置增量维护在 {@code kingSquare[]}，攻击判定不必全盘扫描；</li>
 *   <li>被将判定用<em>反向攻击探测</em>（从将帅格向外找车/炮/马/兵），
 *       复杂度约为常数 20 次查表，而不是「遍历所有敌子 × 逐个校验走法」；</li>
 *   <li>走法生成先产生伪合法着法（{@code int} 打包），再逐个试走过滤自将，
 *       规则校验只做一次；</li>
 *   <li>重复局面裁决走 {@code positionKeys} 历史，O(n) 回溯，不再克隆整盘。</li>
 * </ul>
 */
public final class Board implements Cloneable {

    public static final int ROWS = 10;
    public static final int COLS = 9;
    public static final int SQUARES = ROWS * COLS;

    /** 打包走法：bit0-6 起点，bit7-13 终点，bit14-17 被吃子类型+1（0 表示空格）。 */
    public static final int MOVE_CAP_SHIFT = 14;

    public static final int RED_INDEX = 0;
    public static final int BLACK_INDEX = 1;

    /** 重复局面裁决结果。 */
    public enum RepetitionOutcome {
        NONE, DRAW, RED_LOSES, BLACK_LOSES
    }

    private static final long[][][] ZOBRIST = new long[2][7][SQUARES];
    private static final long ZOBRIST_TURN;

    private static final int[][] ORTHO = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final int[][] DIAGONAL = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
    private static final int[][] HORSE_STEP = {
            {-2, -1}, {-2, 1}, {2, -1}, {2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}
    };

    static {
        Random random = new Random(0x5DEECE66DL);
        for (int color = 0; color < 2; color++) {
            for (int type = 0; type < 7; type++) {
                for (int square = 0; square < SQUARES; square++) {
                    ZOBRIST[color][type][square] = random.nextLong();
                }
            }
        }
        ZOBRIST_TURN = random.nextLong();
    }

    private final Piece[] cells = new Piece[SQUARES];
    private final List<Piece> pieces = new ArrayList<>(32);
    private final List<Piece> pieceView = Collections.unmodifiableList(pieces);

    private final List<MoveRecord> history = new ArrayList<>();
    /** 每个历史节点之后的局面键，{@code positionKeys.get(0)} 为初始局面。 */
    private final List<Long> positionKeys = new ArrayList<>();
    private final Map<Long, Integer> repetitions = new HashMap<>();

    private final int[] kingSquare = {-1, -1};
    private boolean redTurn = true;
    /** 是否由标准初始局面走来。外部引擎据此决定用 startpos 还是 fen 描述局面。 */
    private boolean fromInitialPosition = true;
    private long key;
    private RepetitionOutcome repetitionOutcome = RepetitionOutcome.NONE;
    /** 重复局面的具体原因（长将 / 长捉 / 判和），供结果文案使用。 */
    private GameResult.Reason repetitionReason;
    /** 距离上一次吃子的半回合数，用于自然限着判和与 FEN 的半回合计数。 */
    private int pliesSinceCapture;

    public Board() {
        reset();
    }

    private Board(Board other) {
        IdentityHashMap<Piece, Piece> map = new IdentityHashMap<>();
        for (Piece piece : other.pieces) {
            Piece clone = piece.clone();
            pieces.add(clone);
            map.put(piece, clone);
        }
        for (MoveRecord record : other.history) {
            map.computeIfAbsent(record.piece, Piece::clone);
            if (record.captured != null) {
                map.computeIfAbsent(record.captured, Piece::clone);
            }
        }
        for (int i = 0; i < SQUARES; i++) {
            Piece original = other.cells[i];
            if (original != null) {
                cells[i] = map.get(original);
            }
        }
        for (MoveRecord record : other.history) {
            history.add(new MoveRecord(record.from, record.to, map.get(record.piece),
                    map.get(record.captured), record.redTurnBefore, record.givesCheck,
                    record.chases, record.pliesSinceCaptureBefore));
        }
        positionKeys.addAll(other.positionKeys);
        repetitions.putAll(other.repetitions);
        System.arraycopy(other.kingSquare, 0, kingSquare, 0, 2);
        redTurn = other.redTurn;
        fromInitialPosition = other.fromInitialPosition;
        key = other.key;
        repetitionOutcome = other.repetitionOutcome;
        repetitionReason = other.repetitionReason;
        pliesSinceCapture = other.pliesSinceCapture;
    }

    // ==================== 查询 ====================

    public Piece pieceAt(int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) {
            return null;
        }
        return cells[row * COLS + col];
    }

    public Piece pieceAt(int square) {
        return square < 0 || square >= SQUARES ? null : cells[square];
    }

    /** 只读视图，禁止修改；供 UI 与评估函数遍历。 */
    public List<Piece> pieces() {
        return pieceView;
    }

    public int pieceCount() {
        return pieces.size();
    }

    public boolean isRedTurn() {
        return redTurn;
    }

    public long key() {
        return key;
    }

    public int kingSquare(boolean isRed) {
        return kingSquare[isRed ? RED_INDEX : BLACK_INDEX];
    }

    public int historySize() {
        return history.size();
    }

    public RepetitionOutcome repetitionOutcome() {
        return repetitionOutcome;
    }

    public static boolean onBoard(int row, int col) {
        return row >= 0 && row < ROWS && col >= 0 && col < COLS;
    }

    // ==================== 走法编码 ====================

    public static int moveFrom(int move) {
        return move & 0x7F;
    }

    public static int moveTo(int move) {
        return (move >> 7) & 0x7F;
    }

    public static int moveCapture(int move) {
        return (move >> MOVE_CAP_SHIFT) & 0xF;
    }

    private static int pack(int from, int to, Piece captured) {
        int cap = captured == null ? 0 : captured.type().ordinal() + 1;
        return from | (to << 7) | (cap << MOVE_CAP_SHIFT);
    }

    // ==================== 走法生成 ====================

    /**
     * 生成伪合法着法（不检查是否自将），写入 {@code out} 并返回数量。
     * 调用方需保证 {@code out} 容量不小于 128。
     */
    public int generate(int[] out, boolean side) {
        int n = 0;
        for (int i = 0, size = pieces.size(); i < size; i++) {
            Piece piece = pieces.get(i);
            if (piece.isRed() != side) {
                continue;
            }
            int from = piece.square();
            int row = piece.row();
            int col = piece.col();
            switch (piece.type()) {
                case KING -> {
                    int rowLow = side ? 7 : 0;
                    int rowHigh = side ? 9 : 2;
                    for (int[] d : ORTHO) {
                        int r = row + d[0];
                        int c = col + d[1];
                        if (r < rowLow || r > rowHigh || c < 3 || c > 5) {
                            continue;
                        }
                        n = addMove(out, n, from, r, c, side);
                    }
                }
                case ADVISOR -> {
                    int rowLow = side ? 7 : 0;
                    int rowHigh = side ? 9 : 2;
                    for (int[] d : DIAGONAL) {
                        int r = row + d[0];
                        int c = col + d[1];
                        if (r < rowLow || r > rowHigh || c < 3 || c > 5) {
                            continue;
                        }
                        n = addMove(out, n, from, r, c, side);
                    }
                }
                case ELEPHANT -> {
                    for (int[] d : DIAGONAL) {
                        int r = row + d[0] * 2;
                        int c = col + d[1] * 2;
                        if (r < 0 || r >= ROWS || c < 0 || c >= COLS) {
                            continue;
                        }
                        if (side ? r < 5 : r > 4) {
                            continue; // 相/象不过河
                        }
                        if (cells[(row + d[0]) * COLS + col + d[1]] != null) {
                            continue; // 塞象眼
                        }
                        n = addMove(out, n, from, r, c, side);
                    }
                }
                case HORSE -> {
                    for (int[] d : HORSE_STEP) {
                        int r = row + d[0];
                        int c = col + d[1];
                        if (r < 0 || r >= ROWS || c < 0 || c >= COLS) {
                            continue;
                        }
                        int legRow = Math.abs(d[0]) == 2 ? row + d[0] / 2 : row;
                        int legCol = Math.abs(d[0]) == 2 ? col : col + d[1] / 2;
                        if (cells[legRow * COLS + legCol] != null) {
                            continue; // 蹩马腿
                        }
                        n = addMove(out, n, from, r, c, side);
                    }
                }
                case ROOK -> {
                    for (int[] d : ORTHO) {
                        int r = row + d[0];
                        int c = col + d[1];
                        while (r >= 0 && r < ROWS && c >= 0 && c < COLS) {
                            Piece target = cells[r * COLS + c];
                            if (target == null) {
                                out[n++] = pack(from, r * COLS + c, null);
                            } else {
                                if (target.isRed() != side) {
                                    out[n++] = pack(from, r * COLS + c, target);
                                }
                                break;
                            }
                            r += d[0];
                            c += d[1];
                        }
                    }
                }
                case CANNON -> {
                    for (int[] d : ORTHO) {
                        int r = row + d[0];
                        int c = col + d[1];
                        boolean screened = false;
                        while (r >= 0 && r < ROWS && c >= 0 && c < COLS) {
                            Piece target = cells[r * COLS + c];
                            if (!screened) {
                                if (target == null) {
                                    out[n++] = pack(from, r * COLS + c, null);
                                } else {
                                    screened = true;
                                }
                            } else if (target != null) {
                                if (target.isRed() != side) {
                                    out[n++] = pack(from, r * COLS + c, target);
                                }
                                break;
                            }
                            r += d[0];
                            c += d[1];
                        }
                    }
                }
                case PAWN -> {
                    int forward = side ? -1 : 1;
                    n = addMove(out, n, from, row + forward, col, side);
                    boolean crossed = side ? row <= 4 : row >= 5;
                    if (crossed) {
                        n = addMove(out, n, from, row, col - 1, side);
                        n = addMove(out, n, from, row, col + 1, side);
                    }
                }
                default -> throw new IllegalStateException();
            }
        }
        return n;
    }

    private int addMove(int[] out, int n, int from, int toRow, int toCol, boolean side) {
        if (toRow < 0 || toRow >= ROWS || toCol < 0 || toCol >= COLS) {
            return n;
        }
        int to = toRow * COLS + toCol;
        Piece target = cells[to];
        if (target != null && target.isRed() == side) {
            return n;
        }
        out[n] = pack(from, to, target);
        return n + 1;
    }

    /** 当前行棋方的全部合法着法。 */
    public List<Move> legalMoves() {
        return legalMoves(redTurn);
    }

    /** 指定一方的全部合法着法（用于将死/困毙判定，可传非行棋方）。 */
    public List<Move> legalMoves(boolean side) {
        int[] buffer = new int[128];
        int n = generate(buffer, side);
        List<Move> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int move = buffer[i];
            MoveRecord record = apply(moveFrom(move), moveTo(move));
            boolean safe = !inCheck(side);
            revert(record);
            if (safe) {
                result.add(Move.of(moveFrom(move), moveTo(move)));
            }
        }
        return result;
    }

    public int legalMoveCount(boolean side) {
        int[] buffer = new int[128];
        int n = generate(buffer, side);
        int count = 0;
        for (int i = 0; i < n; i++) {
            int move = buffer[i];
            MoveRecord record = apply(moveFrom(move), moveTo(move));
            boolean safe = !inCheck(side);
            revert(record);
            if (safe) {
                count++;
            }
        }
        return count;
    }

    // ==================== 规则校验 ====================

    /** 只校验棋子走法（不考虑是否自将、也不校验行棋方）。 */
    public boolean canMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (!onBoard(fromRow, fromCol) || !onBoard(toRow, toCol)) {
            return false;
        }
        if (fromRow == toRow && fromCol == toCol) {
            return false;
        }
        Piece piece = pieceAt(fromRow, fromCol);
        if (piece == null) {
            return false;
        }
        Piece target = pieceAt(toRow, toCol);
        if (target != null && target.isRed() == piece.isRed()) {
            return false;
        }
        return matchesPieceRule(piece, fromRow, fromCol, toRow, toCol);
    }

    private boolean matchesPieceRule(Piece piece, int fromRow, int fromCol, int toRow, int toCol) {
        int dr = toRow - fromRow;
        int dc = toCol - fromCol;
        boolean red = piece.isRed();
        int rowLow = red ? 7 : 0;
        int rowHigh = red ? 9 : 2;
        switch (piece.type()) {
            case KING -> {
                if (toCol < 3 || toCol > 5 || toRow < rowLow || toRow > rowHigh) {
                    return false;
                }
                return Math.abs(dr) + Math.abs(dc) == 1;
            }
            case ADVISOR -> {
                if (toCol < 3 || toCol > 5 || toRow < rowLow || toRow > rowHigh) {
                    return false;
                }
                return Math.abs(dr) == 1 && Math.abs(dc) == 1;
            }
            case ELEPHANT -> {
                if (red ? toRow < 5 : toRow > 4) {
                    return false;
                }
                if (Math.abs(dr) != 2 || Math.abs(dc) != 2) {
                    return false;
                }
                return pieceAt((fromRow + toRow) / 2, (fromCol + toCol) / 2) == null;
            }
            case HORSE -> {
                if (!((Math.abs(dr) == 2 && Math.abs(dc) == 1)
                        || (Math.abs(dr) == 1 && Math.abs(dc) == 2))) {
                    return false;
                }
                int legRow = Math.abs(dr) == 2 ? (fromRow + toRow) / 2 : fromRow;
                int legCol = Math.abs(dr) == 2 ? fromCol : (fromCol + toCol) / 2;
                return pieceAt(legRow, legCol) == null;
            }
            case ROOK -> {
                if (fromRow != toRow && fromCol != toCol) {
                    return false;
                }
                return countBetween(fromRow, fromCol, toRow, toCol) == 0;
            }
            case CANNON -> {
                if (fromRow != toRow && fromCol != toCol) {
                    return false;
                }
                int between = countBetween(fromRow, fromCol, toRow, toCol);
                return pieceAt(toRow, toCol) == null ? between == 0 : between == 1;
            }
            case PAWN -> {
                if (red ? dr > 0 : dr < 0) {
                    return false;
                }
                boolean crossed = red ? fromRow <= 4 : fromRow >= 5;
                if (crossed) {
                    return (Math.abs(dr) == 1 && dc == 0) || (dr == 0 && Math.abs(dc) == 1);
                }
                return Math.abs(dr) == 1 && dc == 0;
            }
            default -> {
                return false;
            }
        }
    }

    private int countBetween(int fromRow, int fromCol, int toRow, int toCol) {
        int count = 0;
        if (fromRow == toRow) {
            int lo = Math.min(fromCol, toCol);
            int hi = Math.max(fromCol, toCol);
            for (int col = lo + 1; col < hi; col++) {
                if (cells[fromRow * COLS + col] != null) {
                    count++;
                }
            }
        } else {
            int lo = Math.min(fromRow, toRow);
            int hi = Math.max(fromRow, toRow);
            for (int row = lo + 1; row < hi; row++) {
                if (cells[row * COLS + fromCol] != null) {
                    count++;
                }
            }
        }
        return count;
    }

    /** 合法着法：满足棋子走法，且走完己方不被将军、将帅不照面。 */
    public boolean isLegalMove(Move move) {
        if (move == null) {
            return false;
        }
        Piece piece = pieceAt(move.from());
        if (piece == null || piece.isRed() != redTurn) {
            return false;
        }
        if (!canMove(move.fromRow(), move.fromCol(), move.toRow(), move.toCol())) {
            return false;
        }
        MoveRecord record = apply(move.from(), move.to());
        boolean safe = !inCheck(piece.isRed());
        revert(record);
        return safe;
    }

    public boolean isLegalMove(int fromRow, int fromCol, int toRow, int toCol) {
        return isLegalMove(new Move(fromRow, fromCol, toRow, toCol));
    }

    // ==================== 被将判定 ====================

    public boolean inCheck(boolean isRed) {
        int king = kingSquare[isRed ? RED_INDEX : BLACK_INDEX];
        if (king < 0) {
            return true;
        }
        return isSquareAttacked(king, !isRed);
    }

    /**
     * {@code square} 是否被 {@code byRed} 一方攻击。只用于将帅安全判定。
     *
     * <p>仕/相 被有意忽略：仕的活动范围是本方九宫、相不能过河，两者在几何上
     * 永远够不到对方将帅所在的九宫，判定它们只会白白增加热路径开销。</p>
     */
    public boolean isSquareAttacked(int square, boolean byRed) {
        int row = square / COLS;
        int col = square % COLS;

        // 车 / 炮 / 将（含将帅照面）
        for (int[] d : ORTHO) {
            int r = row + d[0];
            int c = col + d[1];
            boolean screened = false;
            while (r >= 0 && r < ROWS && c >= 0 && c < COLS) {
                Piece piece = cells[r * COLS + c];
                if (piece != null) {
                    if (!screened) {
                        if (piece.isRed() == byRed) {
                            if (piece.type() == Piece.Type.ROOK) {
                                return true;
                            }
                            if (piece.type() == Piece.Type.KING) {
                                if (Math.abs(r - row) + Math.abs(c - col) == 1) {
                                    return true;
                                }
                                if (c == col) {
                                    return true; // 同列无遮挡：将帅照面
                                }
                            }
                        }
                        screened = true;
                    } else {
                        if (piece.isRed() == byRed && piece.type() == Piece.Type.CANNON) {
                            return true;
                        }
                        break;
                    }
                }
                r += d[0];
                c += d[1];
            }
        }

        // 马：从目标格反推可能的马位，并检查该马的蹩腿点
        for (int[] d : HORSE_STEP) {
            int r = row + d[0];
            int c = col + d[1];
            if (r < 0 || r >= ROWS || c < 0 || c >= COLS) {
                continue;
            }
            Piece piece = cells[r * COLS + c];
            if (piece == null || piece.isRed() != byRed || piece.type() != Piece.Type.HORSE) {
                continue;
            }
            int legRow = Math.abs(d[0]) == 2 ? row + d[0] / 2 : r;
            int legCol = Math.abs(d[0]) == 2 ? c : col + d[1] / 2;
            if (cells[legRow * COLS + legCol] == null) {
                return true;
            }
        }

        // 兵/卒：正面一格，过河后还有左右两侧
        if (byRed) {
            if (row + 1 < ROWS) {
                Piece p = cells[(row + 1) * COLS + col];
                if (p != null && p.isRed() && p.type() == Piece.Type.PAWN) {
                    return true;
                }
            }
            if (row <= 4) {
                for (int dc = -1; dc <= 1; dc += 2) {
                    int c = col + dc;
                    if (c < 0 || c >= COLS) {
                        continue;
                    }
                    Piece p = cells[row * COLS + c];
                    if (p != null && p.isRed() && p.type() == Piece.Type.PAWN) {
                        return true;
                    }
                }
            }
        } else {
            if (row - 1 >= 0) {
                Piece p = cells[(row - 1) * COLS + col];
                if (p != null && !p.isRed() && p.type() == Piece.Type.PAWN) {
                    return true;
                }
            }
            if (row >= 5) {
                for (int dc = -1; dc <= 1; dc += 2) {
                    int c = col + dc;
                    if (c < 0 || c >= COLS) {
                        continue;
                    }
                    Piece p = cells[row * COLS + c];
                    if (p != null && !p.isRed() && p.type() == Piece.Type.PAWN) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ==================== 状态变更 ====================

    /**
     * 试走/落子：只做状态变更与 Zobrist 维护，不校验规则、不写历史。
     * 必须与 {@link #revert(MoveRecord)} 成对使用。
     */
    public MoveRecord apply(int from, int to) {
        Piece moving = cells[from];
        Piece captured = cells[to];
        int colorIndex = moving.isRed() ? RED_INDEX : BLACK_INDEX;
        int typeIndex = moving.type().ordinal();

        key ^= ZOBRIST[colorIndex][typeIndex][from];
        if (captured != null) {
            key ^= ZOBRIST[captured.isRed() ? RED_INDEX : BLACK_INDEX][captured.type().ordinal()][to];
            pieces.remove(captured);
        }
        key ^= ZOBRIST[colorIndex][typeIndex][to];
        key ^= ZOBRIST_TURN;

        cells[from] = null;
        cells[to] = moving;
        moving.setSquare(to);
        if (typeIndex == Piece.Type.KING.ordinal()) {
            kingSquare[colorIndex] = to;
        }

        MoveRecord record = new MoveRecord(from, to, moving, captured, redTurn, false);
        redTurn = !redTurn;
        return record;
    }

    public void revert(MoveRecord record) {
        int colorIndex = record.piece.isRed() ? RED_INDEX : BLACK_INDEX;
        int typeIndex = record.piece.type().ordinal();

        key ^= ZOBRIST[colorIndex][typeIndex][record.to];
        if (record.captured != null) {
            key ^= ZOBRIST[record.captured.isRed() ? RED_INDEX : BLACK_INDEX]
                    [record.captured.type().ordinal()][record.to];
            record.captured.setSquare(record.to);
            cells[record.to] = record.captured;
            pieces.add(record.captured);
        } else {
            cells[record.to] = null;
        }
        key ^= ZOBRIST[colorIndex][typeIndex][record.from];
        key ^= ZOBRIST_TURN;

        cells[record.from] = record.piece;
        record.piece.setSquare(record.from);
        if (typeIndex == Piece.Type.KING.ordinal()) {
            kingSquare[colorIndex] = record.from;
        }
        redTurn = record.redTurnBefore;
    }

    public void applyNull() {
        redTurn = !redTurn;
        key ^= ZOBRIST_TURN;
    }

    public void revertNull() {
        applyNull();
    }

    /**
     * 落子：校验行棋方、走法规则、自将，全部通过后写入历史并维护重复局面计数。
     *
     * @return 是否成功落子
     */
    public boolean play(Move move) {
        if (move == null || repetitionOutcome != RepetitionOutcome.NONE) {
            return false;
        }
        Piece piece = pieceAt(move.from());
        if (piece == null || piece.isRed() != redTurn) {
            return false;
        }
        if (!canMove(move.fromRow(), move.fromCol(), move.toRow(), move.toCol())) {
            return false;
        }
        MoveRecord record = apply(move.from(), move.to());
        if (inCheck(piece.isRed())) {
            revert(record);
            return false;
        }
        record.givesCheck = inCheck(!piece.isRed());
        record.chases = detectChase(record);
        record.pliesSinceCaptureBefore = pliesSinceCapture;
        pliesSinceCapture = record.captured == null ? pliesSinceCapture + 1 : 0;
        history.add(record);
        positionKeys.add(key);
        repetitions.merge(key, 1, Integer::sum);
        if (repetitions.get(key) >= 3) {
            RepetitionRules.Verdict verdict = RepetitionRules.adjudicate(positionKeys, history);
            repetitionOutcome = verdict.outcome();
            repetitionReason = verdict.reason();
        }
        return true;
    }

    /** 悔棋一步。 */
    public boolean undo() {
        if (history.isEmpty()) {
            return false;
        }
        MoveRecord record = history.remove(history.size() - 1);
        Long currentKey = positionKeys.remove(positionKeys.size() - 1);
        repetitions.merge(currentKey, -1, (oldValue, one) -> {
            int updated = oldValue + one;
            return updated <= 0 ? null : updated;
        });
        revert(record);
        pliesSinceCapture = record.pliesSinceCaptureBefore;
        repetitionOutcome = RepetitionOutcome.NONE;
        repetitionReason = null;
        return true;
    }

    /**
     * 判断这一手是否构成「捉」：走完之后，这枚子<em>新</em>攻击到一个有价值的敌子。
     *
     * <p>口径（工程近似，见 {@link RepetitionRules}）：</p>
     * <ul>
     *   <li>攻击方排除将帅与兵卒（兵卒抓子不按长捉处理，避免大量误判）；</li>
     *   <li>被攻击方排除将帅与兵卒；</li>
     *   <li>这枚子在走之前就能攻击到的目标不算「新捉」；</li>
     *   <li>目标有保护、且攻击方子力价值不低于目标时不算捉（兑子不是捉）。</li>
     * </ul>
     */
    private boolean detectChase(MoveRecord record) {
        Piece mover = record.piece;
        Piece.Type type = mover.type();
        if (type == Piece.Type.KING || type == Piece.Type.PAWN) {
            return false;
        }
        for (Piece target : pieces) {
            if (target.isRed() == mover.isRed()) {
                continue;
            }
            if (target.type() == Piece.Type.KING || target.type() == Piece.Type.PAWN) {
                continue;
            }
            int square = target.square();
            if (!attacksFrom(mover, record.to, -1, square)) {
                continue;
            }
            if (attacksFrom(mover, record.from, record.to, square)) {
                continue; // 走之前就能吃到，不是这手造成的威胁
            }
            if (isDefended(target, square) && type.value() >= target.type().value()) {
                continue; // 有根且换子不赚，不按捉处理
            }
            return true;
        }
        return false;
    }

    /** {@code target} 是否有同方棋子保护（将帅不计：被牵制的将不敢回吃）。 */
    private boolean isDefended(Piece target, int square) {
        boolean byRed = target.isRed();
        for (Piece guard : pieces) {
            if (guard == target || guard.isRed() != byRed || guard.type() == Piece.Type.KING) {
                continue;
            }
            if (attacksFrom(guard, guard.square(), -1, square)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 假设 {@code piece} 站在 {@code from} 格，它能否攻击到 {@code target} 格。
     *
     * <p>{@code ignoreBlockAt} 指定的格子在扫描时被当成空格，用于「这枚子已走开 / 还没走到」
     * 的假设摆位——否则它会挡住自己原来的射线。</p>
     */
    private boolean attacksFrom(Piece piece, int from, int ignoreBlockAt, int target) {
        int fromRow = from / COLS;
        int fromCol = from % COLS;
        int row = target / COLS;
        int col = target % COLS;
        int dr = row - fromRow;
        int dc = col - fromCol;
        if (dr == 0 && dc == 0) {
            return false;
        }
        switch (piece.type()) {
            case ROOK -> {
                if (dr != 0 && dc != 0) {
                    return false;
                }
                return countBlockers(fromRow, fromCol, row, col, ignoreBlockAt) == 0;
            }
            case CANNON -> {
                if (dr != 0 && dc != 0) {
                    return false;
                }
                return countBlockers(fromRow, fromCol, row, col, ignoreBlockAt) == 1;
            }
            case HORSE -> {
                if (!((Math.abs(dr) == 2 && Math.abs(dc) == 1)
                        || (Math.abs(dr) == 1 && Math.abs(dc) == 2))) {
                    return false;
                }
                int legRow = Math.abs(dr) == 2 ? (fromRow + row) / 2 : fromRow;
                int legCol = Math.abs(dr) == 2 ? fromCol : (fromCol + col) / 2;
                int leg = legRow * COLS + legCol;
                return leg == ignoreBlockAt || cells[leg] == null;
            }
            case ELEPHANT -> {
                if (Math.abs(dr) != 2 || Math.abs(dc) != 2) {
                    return false;
                }
                int eye = (fromRow + row) / 2 * COLS + (fromCol + col) / 2;
                return eye == ignoreBlockAt || cells[eye] == null;
            }
            case ADVISOR -> {
                return Math.abs(dr) == 1 && Math.abs(dc) == 1;
            }
            case KING -> {
                return Math.abs(dr) + Math.abs(dc) == 1;
            }
            case PAWN -> {
                int forward = piece.isRed() ? -1 : 1;
                if (dr == forward && dc == 0) {
                    return true;
                }
                boolean crossed = piece.isRed() ? fromRow <= 4 : fromRow >= 5;
                return crossed && dr == 0 && Math.abs(dc) == 1;
            }
            default -> {
                return false;
            }
        }
    }

    /** 射线上的棋子数（不含两端，{@code ignoreBlockAt} 视为空格）。 */
    private int countBlockers(int fromRow, int fromCol, int toRow, int toCol, int ignoreBlockAt) {
        int count = 0;
        if (fromRow == toRow) {
            int step = toCol > fromCol ? 1 : -1;
            for (int c = fromCol + step; c != toCol; c += step) {
                int square = fromRow * COLS + c;
                if (square != ignoreBlockAt && cells[square] != null) {
                    count++;
                }
            }
        } else {
            int step = toRow > fromRow ? 1 : -1;
            for (int r = fromRow + step; r != toRow; r += step) {
                int square = r * COLS + fromCol;
                if (square != ignoreBlockAt && cells[square] != null) {
                    count++;
                }
            }
        }
        return count;
    }

    // ==================== 终局 ====================

    public boolean isGameOver() {
        return result() != null;
    }

    /** 自然限着：连续 120 个半回合（60 回合）没有吃子，判和。 */
    public static final int NATURAL_DRAW_PLIES = 120;

    /** 对局结果；未结束时返回 {@code null}。 */
    public GameResult result() {
        switch (repetitionOutcome) {
            case DRAW:
                return GameResult.draw(repetitionReason == null
                        ? GameResult.Reason.REPETITION : repetitionReason);
            case RED_LOSES:
                return GameResult.blackWins(repetitionReason == null
                        ? GameResult.Reason.PERPETUAL_CHECK : repetitionReason);
            case BLACK_LOSES:
                return GameResult.redWins(repetitionReason == null
                        ? GameResult.Reason.PERPETUAL_CHECK : repetitionReason);
            default:
                break;
        }
        boolean redKing = false;
        boolean blackKing = false;
        for (Piece piece : pieces) {
            if (piece.type() == Piece.Type.KING) {
                if (piece.isRed()) {
                    redKing = true;
                } else {
                    blackKing = true;
                }
            }
        }
        if (!redKing) {
            return GameResult.blackWins(GameResult.Reason.CHECKMATE);
        }
        if (!blackKing) {
            return GameResult.redWins(GameResult.Reason.CHECKMATE);
        }
        if (legalMoveCount(redTurn) == 0) {
            boolean checked = inCheck(redTurn);
            GameResult.Reason reason = checked ? GameResult.Reason.CHECKMATE : GameResult.Reason.STALEMATE;
            return redTurn ? GameResult.blackWins(reason) : GameResult.redWins(reason);
        }
        if (pliesSinceCapture >= NATURAL_DRAW_PLIES) {
            return GameResult.draw(GameResult.Reason.MOVE_LIMIT);
        }
        if (insufficientMaterial()) {
            return GameResult.draw(GameResult.Reason.INSUFFICIENT_MATERIAL);
        }
        return null;
    }

    /**
     * 双方都没有车、马、炮、兵（只剩将士象）时无法成杀，判和。
     * 兵被计入「有攻击力」的一方：单兵过河仍可能取胜，保守不判和。
     */
    private boolean insufficientMaterial() {
        for (Piece piece : pieces) {
            switch (piece.type()) {
                case ROOK, CANNON, HORSE, PAWN -> {
                    return false;
                }
                default -> {
                }
            }
        }
        return true;
    }

    /** 距离上一次吃子的半回合数（FEN 的半回合计数）。 */
    public int pliesSinceCapture() {
        return pliesSinceCapture;
    }

    /** 供 FEN 解析还原半回合计数。 */
    public void setPliesSinceCapture(int plies) {
        this.pliesSinceCapture = Math.max(0, plies);
    }

    public boolean isCheckmate(boolean isRed) {
        return inCheck(isRed) && legalMoveCount(isRed) == 0;
    }

    // ==================== 历史 / 重置 / 复制 ====================

    public List<Move> historyMoves() {
        List<Move> moves = new ArrayList<>(history.size());
        for (MoveRecord record : history) {
            moves.add(Move.of(record.from, record.to));
        }
        return moves;
    }

    public List<MoveRecord> historyRecords() {
        return Collections.unmodifiableList(history);
    }

    /** 历史局面键序列，{@code get(0)} 为初始局面，最后一个为当前局面。 */
    public List<Long> positionKeys() {
        return Collections.unmodifiableList(positionKeys);
    }

    public Map<Long, Integer> repetitionCounts() {
        return new HashMap<>(repetitions);
    }

    public void reset() {
        Arrays.fill(cells, null);
        pieces.clear();
        history.clear();
        positionKeys.clear();
        repetitions.clear();
        kingSquare[RED_INDEX] = -1;
        kingSquare[BLACK_INDEX] = -1;
        redTurn = true;
        fromInitialPosition = true;
        key = 0;
        repetitionOutcome = RepetitionOutcome.NONE;
        repetitionReason = null;
        pliesSinceCapture = 0;
        setupInitialPosition();
        positionKeys.add(key);
        repetitions.put(key, 1);
    }

    private void setupInitialPosition() {
        put(Piece.Type.ROOK, true, 9, 0);
        put(Piece.Type.HORSE, true, 9, 1);
        put(Piece.Type.ELEPHANT, true, 9, 2);
        put(Piece.Type.ADVISOR, true, 9, 3);
        put(Piece.Type.KING, true, 9, 4);
        put(Piece.Type.ADVISOR, true, 9, 5);
        put(Piece.Type.ELEPHANT, true, 9, 6);
        put(Piece.Type.HORSE, true, 9, 7);
        put(Piece.Type.ROOK, true, 9, 8);
        put(Piece.Type.CANNON, true, 7, 1);
        put(Piece.Type.CANNON, true, 7, 7);
        for (int col = 0; col < COLS; col += 2) {
            put(Piece.Type.PAWN, true, 6, col);
        }

        put(Piece.Type.ROOK, false, 0, 0);
        put(Piece.Type.HORSE, false, 0, 1);
        put(Piece.Type.ELEPHANT, false, 0, 2);
        put(Piece.Type.ADVISOR, false, 0, 3);
        put(Piece.Type.KING, false, 0, 4);
        put(Piece.Type.ADVISOR, false, 0, 5);
        put(Piece.Type.ELEPHANT, false, 0, 6);
        put(Piece.Type.HORSE, false, 0, 7);
        put(Piece.Type.ROOK, false, 0, 8);
        put(Piece.Type.CANNON, false, 2, 1);
        put(Piece.Type.CANNON, false, 2, 7);
        for (int col = 0; col < COLS; col += 2) {
            put(Piece.Type.PAWN, false, 3, col);
        }
    }

    private void put(Piece.Type type, boolean isRed, int row, int col) {
        Piece piece = new Piece(type, isRed, row, col);
        int square = row * COLS + col;
        cells[square] = piece;
        pieces.add(piece);
        key ^= ZOBRIST[isRed ? RED_INDEX : BLACK_INDEX][type.ordinal()][square];
        if (type == Piece.Type.KING) {
            kingSquare[isRed ? RED_INDEX : BLACK_INDEX] = square;
        }
    }

    /** 深拷贝：棋子、历史、重复计数全部独立，AI 搜索使用副本。 */
    public Board copy() {
        return new Board(this);
    }

    @Override
    public Board clone() {
        return copy();
    }

    // ==================== 摆子（测试 / 残局模式） ====================

    /** 清空棋盘，保留空历史。 */
    public void clear() {
        Arrays.fill(cells, null);
        pieces.clear();
        history.clear();
        positionKeys.clear();
        repetitions.clear();
        kingSquare[RED_INDEX] = -1;
        kingSquare[BLACK_INDEX] = -1;
        redTurn = true;
        fromInitialPosition = false;
        key = 0;
        repetitionOutcome = RepetitionOutcome.NONE;
        repetitionReason = null;
        pliesSinceCapture = 0;
        positionKeys.add(0L);
        repetitions.put(0L, 1);
    }

    /** 历史是否从标准初始局面开始（决定能否用 UCI 的 {@code startpos moves}）。 */
    public boolean startsFromInitialPosition() {
        return fromInitialPosition;
    }

    /** 在空棋盘上摆一个棋子（不做规则校验）。 */
    public void place(Piece.Type type, boolean isRed, int row, int col) {
        put(type, isRed, row, col);
        resetRepetitionBaseline();
    }

    /**
     * 指定行棋方。仅在摆子阶段使用（此时历史为空），
     * 会同步重置重复局面的计数基线，否则基线会停留在换手之前的键上。
     */
    public void setRedTurn(boolean redTurn) {
        if (this.redTurn != redTurn) {
            applyNull();
            resetRepetitionBaseline();
        }
    }

    private void resetRepetitionBaseline() {
        positionKeys.clear();
        positionKeys.add(key);
        repetitions.clear();
        repetitions.put(key, 1);
    }

    /**
     * 一步棋的完整还原信息。搜索里每节点产生一个，只保存必要字段。
     */
    public static final class MoveRecord {
        private final int from;
        private final int to;
        private final Piece piece;
        private final Piece captured;
        private final boolean redTurnBefore;
        private boolean givesCheck;
        /** 这一手走完是否「新捉到」对方一个有价值的子（长捉裁决用）。 */
        private boolean chases;
        /** 走这手之前「距离上次吃子」的半回合数，悔棋时还原。 */
        private int pliesSinceCaptureBefore;

        MoveRecord(int from, int to, Piece piece, Piece captured, boolean redTurnBefore,
                   boolean givesCheck, boolean chases, int pliesSinceCaptureBefore) {
            this.from = from;
            this.to = to;
            this.piece = piece;
            this.captured = captured;
            this.redTurnBefore = redTurnBefore;
            this.givesCheck = givesCheck;
            this.chases = chases;
            this.pliesSinceCaptureBefore = pliesSinceCaptureBefore;
        }

        MoveRecord(int from, int to, Piece piece, Piece captured, boolean redTurnBefore, boolean givesCheck) {
            this(from, to, piece, captured, redTurnBefore, givesCheck, false, 0);
        }

        public int from() {
            return from;
        }

        public int to() {
            return to;
        }

        public Move move() {
            return Move.of(from, to);
        }

        public Piece piece() {
            return piece;
        }

        public Piece captured() {
            return captured;
        }

        public boolean givesCheck() {
            return givesCheck;
        }

        /** 这一手是否构成「捉」（长捉裁决用）。 */
        public boolean chases() {
            return chases;
        }
    }
}
