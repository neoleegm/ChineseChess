package xiangqi.ai;

/**
 * 搜索限制。
 *
 * @param maxDepth   深度上限
 * @param timeMs     时间上限（毫秒）
 * @param rootMargin 根节点容忍度（厘兵）。大于 0 时，搜索会算出每个根着法的真实分数，
 *                   然后在「与最优着法差距不超过该值」的着法里随机挑一手。
 *                   这是低难度档「算得浅但不会白送子」的实现方式：故意不选最优，
 *                   但绝不会选到明显亏损的着法。
 */
public record SearchLimits(int maxDepth, long timeMs, int rootMargin) {

    public static SearchLimits of(int maxDepth, long timeMs) {
        return new SearchLimits(maxDepth, timeMs, 0);
    }

    public static SearchLimits of(int maxDepth, long timeMs, int rootMargin) {
        return new SearchLimits(maxDepth, timeMs, Math.max(0, rootMargin));
    }

    public boolean hasRootMargin() {
        return rootMargin > 0;
    }
}
