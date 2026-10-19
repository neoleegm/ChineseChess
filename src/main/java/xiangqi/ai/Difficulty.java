package xiangqi.ai;

/**
 * 难度档位：既决定搜索深度上限，也决定思考时间预算。
 *
 * <p>三档刻意拉开差距：简单档搜两层并带根节点随机性（会吃子、不白送，但看不到三步以上的战术），
 * 中等档是完整的 alpha-beta + 静态搜索（约业余中段），困难档迭代加深到 8 秒，
 * 若配置了 Pikafish 则直接交给专业引擎。</p>
 */
public enum Difficulty {
    EASY("简单", 2, 600),
    MEDIUM("中等", 6, 1200),
    HARD("困难", 64, 8000);

    private final String label;
    private final int maxDepth;
    private final long timeMs;

    Difficulty(String label, int maxDepth, long timeMs) {
        this.label = label;
        this.maxDepth = maxDepth;
        this.timeMs = timeMs;
    }

    public String label() {
        return label;
    }

    public int maxDepth() {
        return maxDepth;
    }

    public long timeMs() {
        return timeMs;
    }

    public static Difficulty fromLabel(String label) {
        for (Difficulty difficulty : values()) {
            if (difficulty.label.equals(label)) {
                return difficulty;
            }
        }
        return MEDIUM;
    }
}
