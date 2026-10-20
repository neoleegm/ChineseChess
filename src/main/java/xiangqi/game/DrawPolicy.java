package xiangqi.game;

import xiangqi.core.Board;
import xiangqi.core.Piece;

/**
 * 人机对局里「AI 是否接受和棋」的判定。
 *
 * <p>纯函数、不依赖界面，便于单元测试。原则很简单：<strong>只有 AI 自己不占优时才接受和棋</strong>。</p>
 * <ul>
 *   <li>棋局太早（不足 {@value #MIN_PLIES} 手）一律拒绝，避免开局就草草和棋；</li>
 *   <li>有引擎评分时用评分判断（{@code aiScore} 为 AI 视角，正数表示 AI 占优）；</li>
 *   <li>没有评分（例如从未搜索过）时退回子力差判断。</li>
 * </ul>
 */
public final class DrawPolicy {

    /** 少于这个手数不接受和棋。 */
    public static final int MIN_PLIES = 20;

    /** AI 领先超过这个分数（厘兵）就拒绝和棋。 */
    public static final int ACCEPT_MARGIN = 30;

    private DrawPolicy() {
    }

    /**
     * @param board       当前局面
     * @param playerIsRed 玩家是否执红
     * @param aiScore     AI 最近一次搜索的评分（AI 视角），未知时传 {@code null}
     * @param plyCount    已走手数
     */
    public static boolean aiAcceptsDraw(Board board, boolean playerIsRed, Integer aiScore, int plyCount) {
        if (plyCount < MIN_PLIES) {
            return false;
        }
        if (aiScore != null) {
            return aiScore <= ACCEPT_MARGIN;
        }
        return aiMaterialBalance(board, playerIsRed) <= 0;
    }

    /** 红方减黑方的子力差。 */
    public static int materialBalance(Board board) {
        int balance = 0;
        for (Piece piece : board.pieces()) {
            int value = piece.type().value();
            balance += piece.isRed() ? value : -value;
        }
        return balance;
    }

    /** 以 AI 视角表示的子力差（正数表示 AI 占优）。 */
    public static int aiMaterialBalance(Board board, boolean playerIsRed) {
        int balance = materialBalance(board);
        return playerIsRed ? -balance : balance;
    }
}
