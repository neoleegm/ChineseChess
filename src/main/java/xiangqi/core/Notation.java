package xiangqi.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文纵线记谱（炮二平五、马8进7 等）。
 *
 * <ul>
 *   <li>红方用汉字数字，黑方用阿拉伯数字；</li>
 *   <li>纵线号从各自右手边数起：红方列 8 为「一」，黑方列 0 为「1」；</li>
 *   <li>向对方底线为「进」，向己方底线为「退」，横走为「平」；</li>
 *   <li>车/炮/兵/帅等直线子进退记步数，马/仕/相记目标纵线；</li>
 *   <li>同一纵线有多个同名棋子时用「前/中/后」或序号代替线号。</li>
 * </ul>
 *
 * <p>注意：必须在落子<em>之前</em>调用，需要走子前的局面判断前后关系。</p>
 */
public final class Notation {

    private static final String[] RED_NAMES = {"帅", "仕", "相", "马", "车", "炮", "兵"};
    private static final String[] BLACK_NAMES = {"将", "士", "象", "马", "车", "炮", "卒"};
    private static final String[] RED_DIGITS = {"一", "二", "三", "四", "五", "六", "七", "八", "九"};

    private Notation() {
    }

    public static String chinese(Board board, Move move) {
        Piece piece = board.pieceAt(move.from());
        if (piece == null) {
            return "";
        }
        boolean red = piece.isRed();
        String name = (red ? RED_NAMES : BLACK_NAMES)[piece.type().ordinal()];

        List<Piece> sameFile = new ArrayList<>(3);
        for (Piece other : board.pieces()) {
            if (other != piece && other.isRed() == red
                    && other.type() == piece.type() && other.col() == move.fromCol()) {
                sameFile.add(other);
            }
        }

        String prefix;
        if (sameFile.isEmpty()) {
            prefix = name + fileName(red, move.fromCol());
        } else {
            List<Piece> all = new ArrayList<>(sameFile);
            all.add(piece);
            // 靠近对方底线的为「前」
            all.sort((a, b) -> red
                    ? Integer.compare(a.row(), b.row())
                    : Integer.compare(b.row(), a.row()));
            int index = all.indexOf(piece);
            String position;
            if (all.size() == 2) {
                position = index == 0 ? "前" : "后";
            } else if (all.size() == 3) {
                position = index == 0 ? "前" : (index == 1 ? "中" : "后");
            } else if (index == 0) {
                position = "前";
            } else if (index == all.size() - 1) {
                position = "后";
            } else {
                position = RED_DIGITS[index];
            }
            prefix = position + name;
        }

        int dr = move.toRow() - move.fromRow();
        boolean forward = red ? dr < 0 : dr > 0;
        boolean lineMover = switch (piece.type()) {
            case ROOK, CANNON, PAWN, KING -> true;
            default -> false;
        };

        if (dr == 0) {
            return prefix + "平" + fileName(red, move.toCol());
        }
        String direction = forward ? "进" : "退";
        String parameter = lineMover ? stepsName(red, Math.abs(dr)) : fileName(red, move.toCol());
        return prefix + direction + parameter;
    }

    private static String fileName(boolean red, int col) {
        return red ? RED_DIGITS[8 - col] : String.valueOf(col + 1);
    }

    private static String stepsName(boolean red, int steps) {
        return red ? RED_DIGITS[steps - 1] : String.valueOf(steps);
    }
}
