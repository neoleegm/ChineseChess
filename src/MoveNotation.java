import java.util.ArrayList;
import java.util.List;

/**
 * 中文记谱法生成器（纵线记谱，如 炮二平五、马8进7）。
 *
 * 规则要点：
 * - 红方用汉字数字、黑方用阿拉伯数字；
 * - 纵线号双方均从各自右手边数起（红方列 8 为一路、黑方列 0 为1路；红"一"路与黑"9"路是同一条线）；
 * - 方向：向对方底线为"进"，向己方底线为"退"，横向为"平"；
 * - 车/炮/兵/帅（直线子）进退记步数，平记目标线；马/仕/相（斜线子）进退记目标线；
 * - 同列有两个以上同名棋子时用"前/中/后"代替线号（如 前车进一）。
 *
 * 注意：必须在走子之前调用（需要走子前的棋盘状态判断前后关系）。
 */
public final class MoveNotation {
    // 按 ChessPiece.Type 的 ordinal 顺序：KING, ADVISOR, ELEPHANT, HORSE, ROOK, CANNON, PAWN
    private static final String[] RED_NAMES = {"帅", "仕", "相", "马", "车", "炮", "兵"};
    private static final String[] BLACK_NAMES = {"将", "士", "象", "马", "车", "炮", "卒"};
    private static final String[] RED_DIGITS = {"一", "二", "三", "四", "五", "六", "七", "八", "九"};

    private MoveNotation() {
    }

    /**
     * 生成指定走法的中文记谱（走子前调用）。
     */
    public static String toChineseNotation(ChessBoard board, Move move) {
        ChessPiece piece = board.getPiece(move.fromRow, move.fromCol);
        if (piece == null) return "";
        boolean red = piece.isRed();
        String name = (red ? RED_NAMES : BLACK_NAMES)[piece.getType().ordinal()];

        // 同列同名同色的棋子（判断是否需要前/中/后命名）
        List<ChessPiece> sameFile = new ArrayList<>();
        for (ChessPiece p : board.getPieces()) {
            if (p != piece && p.isRed() == red
                    && p.getType() == piece.getType() && p.getCol() == move.fromCol) {
                sameFile.add(p);
            }
        }

        String prefix;
        if (sameFile.isEmpty()) {
            prefix = name + fileName(red, move.fromCol);
        } else {
            // 与对方底线更近的为"前"：红方行小在前，黑方行大在前
            List<ChessPiece> all = new ArrayList<>(sameFile);
            all.add(piece);
            all.sort((a, b) -> red
                    ? Integer.compare(a.getRow(), b.getRow())
                    : Integer.compare(b.getRow(), a.getRow()));
            int idx = all.indexOf(piece);
            String posName;
            if (all.size() == 2) {
                posName = idx == 0 ? "前" : "后";
            } else if (all.size() == 3) {
                posName = idx == 0 ? "前" : (idx == 1 ? "中" : "后");
            } else {
                // 四个以上同列（罕见，仅兵卒可能）：从前往后数
                posName = RED_DIGITS[idx];
            }
            prefix = posName + name;
        }

        int dr = move.toRow - move.fromRow;
        boolean forward = red ? dr < 0 : dr > 0;
        boolean lineMover = switch (piece.getType()) {
            case ROOK, CANNON, PAWN, KING -> true;
            default -> false;
        };

        String direction;
        String param;
        if (dr == 0) {
            direction = "平";
            param = fileName(red, move.toCol);
        } else if (forward) {
            direction = "进";
            param = lineMover ? stepsName(red, Math.abs(dr)) : fileName(red, move.toCol);
        } else {
            direction = "退";
            param = lineMover ? stepsName(red, Math.abs(dr)) : fileName(red, move.toCol);
        }
        return prefix + direction + param;
    }

    /**
     * 纵线名称：红方从己方右手边数（列 8 为一路，即 9-col），
     * 黑方从己方右手边数（列 0 为1路，即 col+1）；红"五"路与黑"5"路同为中路。
     */
    private static String fileName(boolean red, int col) {
        return red ? RED_DIGITS[8 - col] : String.valueOf(col + 1);
    }

    /**
     * 步数名称：红方汉字、黑方阿拉伯数字
     */
    private static String stepsName(boolean red, int steps) {
        return red ? RED_DIGITS[steps - 1] : String.valueOf(steps);
    }
}
