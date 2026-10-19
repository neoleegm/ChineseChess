package xiangqi.ai;

import xiangqi.core.Move;

import java.util.List;

/**
 * 搜索输出：最佳着法 + 评估信息 + 主变着。
 *
 * @param move  最佳着法，无棋可走时为 {@code null}
 * @param score 分数，正数表示<em>行棋方</em>占优；绝对值接近 {@link #MATE_SCORE} 表示杀棋
 * @param depth 实际完成的搜索深度
 * @param nodes 搜索节点数
 * @param pv    主变着（Principal Variation），外部引擎会给出，内置引擎留空
 */
public record SearchResult(Move move, int score, int depth, long nodes, List<Move> pv) {

    public static final int MATE_SCORE = 100_000;

    public SearchResult {
        pv = pv == null ? List.of() : List.copyOf(pv);
    }

    public SearchResult(Move move, int score, int depth, long nodes) {
        this(move, score, depth, nodes, List.of());
    }

    public static SearchResult empty() {
        return new SearchResult(null, 0, 0, 0, List.of());
    }

    public boolean mateFound() {
        return Math.abs(score) > MATE_SCORE - 1_000;
    }

    /** 以「兵 = 1.0」为单位的分数，便于界面显示。 */
    public double pawns() {
        return score / 100.0;
    }

    /**
     * 深度文案。找到杀棋时引擎会上报一个很大的 depth（Pikafish 用 MAX_PLY 表示），
     * 直接显示「d245」会让人困惑，这里统一显示为「杀棋」。
     */
    public String depthText() {
        if (mateFound()) {
            return "杀棋";
        }
        return depth > 0 ? "d" + depth : "";
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

    /** 一行摘要，例如「d24 +0.35」。 */
    public String summary() {
        String depth = depthText();
        return depth.isEmpty() ? scoreText() : depth + " " + scoreText();
    }
}
