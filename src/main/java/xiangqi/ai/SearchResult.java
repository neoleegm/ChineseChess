package xiangqi.ai;

import xiangqi.core.Move;

/**
 * 搜索输出：最佳着法 + 评估信息。
 *
 * @param move  最佳着法，无棋可走时为 {@code null}
 * @param score 分数，正数表示<em>行棋方</em>占优；绝对值接近 {@link #MATE_SCORE} 表示杀棋
 * @param depth 实际完成的搜索深度
 * @param nodes 搜索节点数
 */
public record SearchResult(Move move, int score, int depth, long nodes) {

    public static final int MATE_SCORE = 100_000;

    public static SearchResult empty() {
        return new SearchResult(null, 0, 0, 0);
    }

    public boolean mateFound() {
        return Math.abs(score) > MATE_SCORE - 1_000;
    }

    /** 以「兵 = 1.0」为单位的分数，便于界面显示。 */
    public double pawns() {
        return score / 100.0;
    }

    /** 面向用户的评估文案，例如「+1.25」「杀棋 +3」。 */
    public String scoreText() {
        if (mateFound()) {
            int plies = MATE_SCORE - Math.abs(score);
            int moves = Math.max(1, (plies + 1) / 2);
            return (score > 0 ? "杀棋 +" : "被杀 -") + moves;
        }
        return String.format("%+.2f", pawns());
    }
}
