package xiangqi.core;

import java.util.List;

/**
 * 重复局面裁决（长将 / 长捉 / 一将一捉 / 判和）。
 *
 * <p>规则层（{@link Board}）和搜索层（{@code ai.SearchEngine}）都需要这套判定，
 * 因此集中在这里，避免两处各写一份后逐渐走偏。</p>
 *
 * <h2>判定口径</h2>
 * <p>只统计「当前局面」与「上一次出现同一局面」之间的<em>最近一个循环</em>，
 * 更早的历史不参与，否则旧的长将记录会污染新的判和。</p>
 * <ul>
 *   <li>循环中一方<strong>每一步</strong>都将军、另一方不全是将军 → 长将方负；</li>
 *   <li>一方每步都「将军或捉子」（长打）、另一方不全是长打 → 长打方负；</li>
 *   <li>双方都是长打，或双方都不是 → 判和。</li>
 * </ul>
 *
 * <p>这是竞赛规则的工程近似：只识别「循环内每着都打」的显式长打，
 * 不做逐着的「捉子优先级 / 兑子 / 根子」完整裁定。</p>
 */
public final class RepetitionRules {

    private RepetitionRules() {
    }

    /**
     * 裁决结果。
     *
     * @param outcome {@link Board.RepetitionOutcome#NONE} 表示无法裁决
     * @param reason  对应 {@link GameResult.Reason}，{@code outcome} 为 NONE 时可能为 {@code null}
     */
    public record Verdict(Board.RepetitionOutcome outcome, GameResult.Reason reason) {

        public static final Verdict NONE = new Verdict(Board.RepetitionOutcome.NONE, null);

        public boolean decided() {
            return outcome != Board.RepetitionOutcome.NONE;
        }
    }

    /** 找出 {@code keys} 中「上一次出现 {@code target}」的下标；没有返回 -1。 */
    static int previousOccurrence(List<Long> keys, long target) {
        for (int i = keys.size() - 2; i >= 0; i--) {
            if (keys.get(i) == target) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 对最近一个循环做裁决。
     *
     * @param keys   局面键序列，最后一个元素为当前局面
     * @param history 着法记录，长度应为 {@code keys.size() - 1}
     */
    public static Verdict adjudicate(List<Long> keys, List<Board.MoveRecord> history) {
        if (keys.size() < 3 || history.isEmpty()) {
            return Verdict.NONE;
        }
        long target = keys.get(keys.size() - 1);
        int previous = previousOccurrence(keys, target);
        if (previous < 0) {
            // 同一局面第三次出现，但找不到上一个同局面（被裁剪过），保守判和
            return new Verdict(Board.RepetitionOutcome.DRAW, GameResult.Reason.REPETITION);
        }

        int redMoves = 0;
        int redChecks = 0;
        int redAttacks = 0;
        int blackMoves = 0;
        int blackChecks = 0;
        int blackAttacks = 0;
        for (int i = previous; i < history.size(); i++) {
            Board.MoveRecord record = history.get(i);
            boolean check = record.givesCheck();
            boolean attack = check || record.chases();
            if (record.piece().isRed()) {
                redMoves++;
                redChecks += check ? 1 : 0;
                redAttacks += attack ? 1 : 0;
            } else {
                blackMoves++;
                blackChecks += check ? 1 : 0;
                blackAttacks += attack ? 1 : 0;
            }
        }

        boolean redAllCheck = redMoves > 0 && redChecks == redMoves;
        boolean blackAllCheck = blackMoves > 0 && blackChecks == blackMoves;
        boolean redAllAttack = redMoves > 0 && redAttacks == redMoves;
        boolean blackAllAttack = blackMoves > 0 && blackAttacks == blackMoves;

        if (redAllCheck && !blackAllCheck) {
            return new Verdict(Board.RepetitionOutcome.RED_LOSES, GameResult.Reason.PERPETUAL_CHECK);
        }
        if (blackAllCheck && !redAllCheck) {
            return new Verdict(Board.RepetitionOutcome.BLACK_LOSES, GameResult.Reason.PERPETUAL_CHECK);
        }
        if (redAllAttack && !blackAllAttack) {
            return new Verdict(Board.RepetitionOutcome.RED_LOSES, GameResult.Reason.PERPETUAL_CHASE);
        }
        if (blackAllAttack && !redAllAttack) {
            return new Verdict(Board.RepetitionOutcome.BLACK_LOSES, GameResult.Reason.PERPETUAL_CHASE);
        }
        return new Verdict(Board.RepetitionOutcome.DRAW, GameResult.Reason.REPETITION);
    }
}
