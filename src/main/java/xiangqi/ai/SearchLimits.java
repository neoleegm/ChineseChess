package xiangqi.ai;

/**
 * 搜索限制：深度上限 + 时间上限（毫秒）。两者任一先到即停止加深。
 */
public record SearchLimits(int maxDepth, long timeMs) {

    public static SearchLimits of(int maxDepth, long timeMs) {
        return new SearchLimits(maxDepth, timeMs);
    }
}
