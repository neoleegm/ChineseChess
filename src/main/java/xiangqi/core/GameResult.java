package xiangqi.core;

/**
 * 对局结果：胜方 + 结束原因。UI 只依赖 {@link #text()}，规则层只负责给出结构化结果。
 */
public record GameResult(Outcome outcome, Reason reason) {

    public enum Outcome {
        RED_WINS, BLACK_WINS, DRAW
    }

    public enum Reason {
        CHECKMATE("将死"),
        STALEMATE("困毙"),
        PERPETUAL_CHECK("长将作负"),
        PERPETUAL_CHASE("长捉作负"),
        REPETITION("三次重复局面"),
        MOVE_LIMIT("自然限着判和"),
        INSUFFICIENT_MATERIAL("子力不足判和"),
        RESIGNATION("认输"),
        TIMEOUT("超时"),
        AGREEMENT("双方同意");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static GameResult redWins(Reason reason) {
        return new GameResult(Outcome.RED_WINS, reason);
    }

    public static GameResult blackWins(Reason reason) {
        return new GameResult(Outcome.BLACK_WINS, reason);
    }

    public static GameResult draw(Reason reason) {
        return new GameResult(Outcome.DRAW, reason);
    }

    public boolean isDraw() {
        return outcome == Outcome.DRAW;
    }

    public boolean winnerIsRed() {
        return outcome == Outcome.RED_WINS;
    }

    /** 中文结果文案，例如「红方被将死，黑方获胜」。 */
    public String text() {
        if (outcome == Outcome.DRAW) {
            return "和棋：" + reason.label();
        }
        boolean redWon = outcome == Outcome.RED_WINS;
        String winner = redWon ? "红方" : "黑方";
        String loser = redWon ? "黑方" : "红方";
        return switch (reason) {
            case CHECKMATE -> loser + "被将死，" + winner + "获胜";
            case STALEMATE -> loser + "困毙，" + winner + "获胜";
            case PERPETUAL_CHECK -> loser + "长将作负，" + winner + "获胜";
            case PERPETUAL_CHASE -> loser + "长捉作负，" + winner + "获胜";
            case RESIGNATION -> loser + "认输，" + winner + "获胜";
            case TIMEOUT -> loser + "超时，" + winner + "获胜";
            default -> winner + "获胜";
        };
    }

    /** PGN 风格的比分串，用于棋谱头。 */
    public String score() {
        return switch (outcome) {
            case RED_WINS -> "1-0";
            case BLACK_WINS -> "0-1";
            case DRAW -> "1/2-1/2";
        };
    }
}
