package xiangqi.test;

import xiangqi.ai.AiPlayer;
import xiangqi.ai.Difficulty;
import xiangqi.ai.SearchEngine;
import xiangqi.ai.SearchLimits;
import xiangqi.ai.SearchResult;
import xiangqi.ai.UciEngine;
import xiangqi.core.Board;
import xiangqi.core.Move;
import xiangqi.core.Piece;

/**
 * 引擎测试：战术正确性、着法合法性、时间约束、长将自保。
 */
final class EngineTest {

    private EngineTest() {
    }

    static void run() {
        System.out.println("[AI 引擎]");
        Checks.test("吃掉白送的车", EngineTest::takesHangingRook);
        Checks.test("找到一步杀", EngineTest::findsMateInOne);
        Checks.test("各档位都返回合法着法", EngineTest::allDifficultiesLegal);
        Checks.test("遵守思考时间上限", EngineTest::respectsTimeLimit);
        Checks.test("避免立即长将作负", EngineTest::avoidsPerpetualLoss);
        Checks.test("简单档不会把车留在卒口", EngineTest::easyDoesNotHangPieces);
        Checks.test("UCI 局面命令不重复叠加历史", EngineTest::uciPositionCommand);
        Checks.test("UCI 杀棋步数按回合换算", EngineTest::uciMateConversion);
        Checks.test("内置引擎给出主变着", EngineTest::internalEngineHasPv);
        Checks.test("自对弈 30 手全部合法", EngineTest::selfPlayLegal);
    }

    private static Board empty() {
        Board board = new Board();
        board.clear();
        return board;
    }

    private static void takesHangingRook() {
        Board board = empty();
        board.place(Piece.Type.KING, true, 9, 4);
        board.place(Piece.Type.KING, false, 0, 3);
        board.place(Piece.Type.ROOK, true, 4, 0);
        board.place(Piece.Type.ROOK, false, 4, 8);

        SearchEngine engine = new SearchEngine();
        SearchResult result = engine.search(board, SearchLimits.of(8, 800));
        Checks.equal(new Move(4, 0, 4, 8), result.move(), "应吃掉白送的车");
    }

    private static void findsMateInOne() {
        Board board = empty();
        board.place(Piece.Type.KING, false, 0, 3);
        board.place(Piece.Type.KING, true, 9, 5);
        board.place(Piece.Type.ROOK, true, 1, 5);
        board.place(Piece.Type.ROOK, true, 5, 0);

        SearchEngine engine = new SearchEngine();
        SearchResult result = engine.search(board, SearchLimits.of(6, 1_000));
        Checks.notNull(result.move(), "应返回着法");
        Checks.isTrue(board.play(result.move()), "杀着应可执行");
        Checks.isTrue(board.isGameOver(), "一步应成杀");
    }

    private static void allDifficultiesLegal() {
        for (Difficulty difficulty : Difficulty.values()) {
            AiPlayer player = new AiPlayer();
            player.setDifficulty(difficulty);
            player.setTimeOverride(300);

            Board board = new Board();
            Move move = player.chooseMove(board);
            Checks.notNull(move, difficulty.label() + " 应返回着法");
            Checks.isTrue(board.isLegalMove(move), difficulty.label() + " 着法应合法");

            Board black = new Board();
            black.setRedTurn(false);
            Move blackMove = player.chooseMove(black);
            Checks.notNull(blackMove, difficulty.label() + " 执黑应返回着法");
            Checks.isTrue(black.isLegalMove(blackMove), difficulty.label() + " 黑方着法应合法");
        }
    }

    private static void respectsTimeLimit() {
        Board board = new Board();
        SearchEngine engine = new SearchEngine();
        long start = System.nanoTime();
        SearchResult result = engine.search(board, SearchLimits.of(64, 300));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        Checks.notNull(result.move(), "应返回着法");
        Checks.isTrue(elapsedMs < 2_500, "300ms 预算不应跑 " + elapsedMs + "ms");
        Checks.isTrue(result.depth() >= 1, "至少完成一层");
    }

    /**
     * 长将自保：摆一个「红车每步都将军、黑将左右闪避」的循环，走到第三次重复的前一手。
     * 此时红方若再走 5,3→5,4 就会因长将作负，三档 AI 都必须避开。
     */
    private static void avoidsPerpetualLoss() {
        Board board = empty();
        board.place(Piece.Type.KING, true, 9, 5);
        board.place(Piece.Type.KING, false, 0, 4);
        board.place(Piece.Type.ROOK, true, 5, 4);
        board.setRedTurn(false);

        int[][] cycle = {
                {0, 4, 0, 3}, {5, 4, 5, 3},
                {0, 3, 0, 4}, {5, 3, 5, 4},
                {0, 4, 0, 3}, {5, 4, 5, 3},
                {0, 3, 0, 4}
        };
        for (int[] move : cycle) {
            Checks.isTrue(board.play(new Move(move[0], move[1], move[2], move[3])),
                    "循环着法应可走：" + move[0] + "," + move[1] + "->" + move[2] + "," + move[3]);
        }
        Checks.isFalse(board.isGameOver(), "此时尚未终局");

        // 确认这一手确实是「立即判负」的那一手，否则用例本身失去意义
        Board probe = board.copy();
        Checks.isTrue(probe.play(new Move(5, 3, 5, 4)), "探针着法应可走");
        Checks.equal(Board.RepetitionOutcome.RED_LOSES, probe.repetitionOutcome(),
                "探针着法应触发红方长将作负");

        for (Difficulty difficulty : Difficulty.values()) {
            AiPlayer player = new AiPlayer();
            player.setDifficulty(difficulty);
            player.setTimeOverride(300);
            Move move = player.chooseMove(board);
            Checks.notNull(move, difficulty.label() + " 应返回着法");
            Checks.isFalse(move.fromRow() == 5 && move.fromCol() == 3
                            && move.toRow() == 5 && move.toCol() == 4,
                    difficulty.label() + " 不应走出立即判负的长将");
        }
    }

    /**
     * 简单档的底线：可以算得浅、可以走闲棋，但不能把大子白送。
     * 旧实现只做一步静态评估，看不到对方回吃，经常主动送车。
     */
    private static void easyDoesNotHangPieces() {
        Board board = empty();
        board.place(Piece.Type.KING, true, 9, 4);
        board.place(Piece.Type.KING, false, 0, 3);
        board.place(Piece.Type.ROOK, true, 5, 4);
        board.place(Piece.Type.PAWN, false, 4, 4); // 过河卒正威胁吃车

        AiPlayer player = new AiPlayer();
        player.setDifficulty(Difficulty.EASY);
        player.setTimeOverride(400);

        Move move = player.chooseMove(board);
        Checks.notNull(move, "简单档应返回着法");
        Checks.isTrue(board.play(move), "简单档着法应可执行");
        boolean stillHanging = board.pieceAt(5, 4) != null
                && board.pieceAt(5, 4).type() == Piece.Type.ROOK
                && board.pieceAt(4, 4) != null;
        Checks.isFalse(stillHanging, "简单档不应把车留在卒口，实际走了 " + move);
    }

    /**
     * 回归用例：{@code position fen X moves ...} 会从 X 再走一遍 moves，
     * 若 X 已是走完之后的局面就会把历史叠加两次。有完整历史时必须用 startpos。
     */
    private static void uciPositionCommand() {
        Board fresh = new Board();
        Checks.isTrue(UciEngine.positionCommand(fresh).startsWith("position fen "),
                "无历史时用 fen");

        Board played = new Board();
        played.play(new Move(7, 1, 7, 4));
        Checks.equal("position startpos moves b2e2", UciEngine.positionCommand(played),
                "一手历史");
        played.play(new Move(0, 1, 2, 2));
        Checks.equal("position startpos moves b2e2 b9c7", UciEngine.positionCommand(played),
                "两手历史");

        Board placed = empty();
        placed.place(Piece.Type.KING, true, 9, 4);
        placed.place(Piece.Type.KING, false, 0, 3);
        Checks.isTrue(UciEngine.positionCommand(placed).startsWith("position fen "),
                "摆出来的局面没有历史，用 fen");
    }

    /**
     * UCI 的 {@code score mate N} 单位是回合，内部评分是半回合。
     * 直接当半回合用会让「三步杀」显示成「杀棋 +2」。
     */
    private static void uciMateConversion() {
        UciEngine.Info mate = UciEngine.parseInfoLine(
                "info depth 18 score mate 3 pv h2e2 h9g7 h0g2", UciEngine.Info.EMPTY);
        SearchResult result = new SearchResult(null, mate.score(), mate.depth(), 0);
        Checks.equal("杀棋 +3", result.scoreText(), "mate 3 应显示三步杀");
        Checks.equal(18, mate.depth(), "深度解析");
        Checks.equal(3, mate.pv().size(), "主变着解析");

        UciEngine.Info mated = UciEngine.parseInfoLine(
                "info depth 20 score mate -2", UciEngine.Info.EMPTY);
        Checks.equal("被杀 -2",
                new SearchResult(null, mated.score(), mated.depth(), 0).scoreText(), "mate -2");

        UciEngine.Info cp = UciEngine.parseInfoLine(
                "info depth 12 score cp 35 pv b2e2", UciEngine.Info.EMPTY);
        Checks.equal(35, cp.score(), "cp 分值原样保留");
        Checks.equal("+0.35", new SearchResult(null, cp.score(), cp.depth(), 0).scoreText(), "cp 文案");
    }

    private static void internalEngineHasPv() {
        Board board = new Board();
        SearchResult result = new SearchEngine().search(board, SearchLimits.of(5, 600));
        Checks.notNull(result.move(), "应返回着法");
        Checks.isTrue(result.pv().size() >= 2, "内置引擎应给出至少两手主变着，实际 " + result.pv().size());
        Checks.equal(result.move(), result.pv().get(0), "主变着首手应等于最佳着法");
    }

    private static void selfPlayLegal() {
        Board board = new Board();
        SearchEngine engine = new SearchEngine();
        int plies = 0;
        while (plies < 30 && !board.isGameOver()) {
            SearchResult result = engine.search(board, SearchLimits.of(5, 120));
            if (result.move() == null) {
                break;
            }
            Checks.isTrue(board.isLegalMove(result.move()), "第 " + (plies + 1) + " 手应合法");
            Checks.isTrue(board.play(result.move()), "第 " + (plies + 1) + " 手应可执行");
            plies++;
        }
        Checks.isTrue(plies >= 10, "自对弈至少走 10 手，实际 " + plies);
    }
}
