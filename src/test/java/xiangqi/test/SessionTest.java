package xiangqi.test;

import xiangqi.ai.Difficulty;
import xiangqi.core.Board;
import xiangqi.core.GameResult;
import xiangqi.core.Move;
import xiangqi.game.GameRecord;
import xiangqi.game.GameSession;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 对局会话测试：悔棋/重做的对称性、复盘、认输、棋谱读写。
 */
final class SessionTest {

    private SessionTest() {
    }

    static void run() {
        System.out.println("[对局会话]");
        Checks.test("人机模式悔棋退回玩家回合", SessionTest::undoPairs);
        Checks.test("重做恢复被悔掉的着法", SessionTest::redoRestores);
        Checks.test("复盘显示历史局面", SessionTest::reviewShowsHistory);
        Checks.test("认输判定胜负", SessionTest::resign);
        Checks.test("棋谱保存与载入往返", SessionTest::recordRoundTrip);
        Checks.test("用时统计", SessionTest::clock);
        Checks.test("人机模式下 AI 回合判定", SessionTest::awaitingAi);
    }

    private static GameSession pveSession() {
        GameSession session = new GameSession();
        session.setMode(GameSession.Mode.PVE);
        session.setPlayerIsRed(true);
        session.setDifficulty(Difficulty.MEDIUM);
        return session;
    }

    private static void playUci(GameSession session, String uci) {
        Move move = xiangqi.core.Uci.fromUci(uci);
        Checks.notNull(move, "示例着法应可解析：" + uci);
        Checks.isTrue(session.play(move), "示例着法应可执行：" + uci);
    }

    private static void undoPairs() {
        GameSession session = pveSession();
        playUci(session, "h2e2");
        playUci(session, "h9g7");
        playUci(session, "h0g2");
        playUci(session, "i9h9");
        Checks.equal(4, session.plyCount(), "应有 4 手");

        Checks.isTrue(session.undo(), "应能悔棋");
        Checks.equal(2, session.plyCount(), "人机模式悔棋应退两步");
        Checks.isTrue(session.board().isRedTurn(), "悔棋后应轮到玩家（红方）");
        Checks.isTrue(session.isPlayerTurn(), "应回到玩家回合");
    }

    private static void redoRestores() {
        GameSession session = pveSession();
        playUci(session, "h2e2");
        playUci(session, "h9g7");
        playUci(session, "h0g2");
        playUci(session, "i9h9");
        String before = xiangqi.core.Fen.toFen(session.board());

        Checks.isTrue(session.undo(), "应能悔棋");
        Checks.isTrue(session.canRedo(), "应可重做");
        Checks.isTrue(session.redo(), "重做应成功");
        Checks.equal(4, session.plyCount(), "重做后应恢复 4 手");
        Checks.equal(before, xiangqi.core.Fen.toFen(session.board()), "重做后局面应与悔棋前一致");
    }

    private static void reviewShowsHistory() {
        GameSession session = pveSession();
        playUci(session, "h2e2");
        playUci(session, "h9g7");
        String afterTwo = xiangqi.core.Fen.toFen(session.board());

        session.setReviewPly(1);
        Checks.isTrue(session.reviewing(), "应进入复盘");
        Board shown = session.displayBoard();
        Checks.equal(1, shown.historySize(), "复盘棋盘应只有 1 手");
        Checks.notNull(session.displayLastMove(), "复盘应高亮最后一手");

        session.setReviewPly(2);
        Checks.isFalse(session.reviewing(), "回到最新局面应退出复盘");
        Checks.equal(afterTwo, xiangqi.core.Fen.toFen(session.displayBoard()), "退出复盘后应显示当前局面");
    }

    private static void resign() {
        GameSession session = pveSession();
        playUci(session, "h2e2");
        session.resign();
        Checks.isTrue(session.isOver(), "认输后应终局");
        Checks.equal(GameResult.Reason.RESIGNATION, session.result().reason(), "原因是认输");
        Checks.isTrue(session.result().text().contains("认输"), "文案包含认输");
    }

    private static void recordRoundTrip() throws RuntimeException {
        try {
            GameSession session = pveSession();
            playUci(session, "h2e2");
            playUci(session, "h9g7");
            playUci(session, "h0g2");
            playUci(session, "i9h9");
            String fen = xiangqi.core.Fen.toFen(session.board());

            Path file = Files.createTempFile("xiangqi-", ".xq");
            try {
                GameRecord.save(file, session);
                GameRecord.Data data = GameRecord.load(file);
                Checks.equal(4, data.moves().size(), "载入 4 手");
                Checks.equal(GameSession.Mode.PVE, data.mode(), "模式应还原");

                GameSession restored = new GameSession();
                int applied = restored.loadMoves(data.moves(), data.mode(),
                        data.difficulty(), data.playerIsRed());
                Checks.equal(4, applied, "应重演 4 手");
                Checks.equal(fen, xiangqi.core.Fen.toFen(restored.board()), "重演后局面应一致");

                String text = GameRecord.render(session);
                Checks.isTrue(text.contains("炮二平五"), "棋谱应含中文记谱");
                Checks.isTrue(text.contains("{h2e2}"), "棋谱应含 UCI 坐标");
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static void clock() {
        GameSession session = pveSession();
        playUci(session, "h2e2");
        playUci(session, "h9g7");
        Checks.isTrue(session.redMillis() >= 0, "红方用时应非负");
        Checks.isTrue(session.blackMillis() >= 0, "黑方用时应非负");
        Checks.equal("00:05", GameSession.formatClock(5_000), "计时格式");
        Checks.equal("01:05", GameSession.formatClock(65_000), "跨分钟格式");
    }

    private static void awaitingAi() {
        GameSession session = pveSession();
        Checks.isFalse(session.awaitingAi(), "红方玩家先手，开局应等玩家");
        playUci(session, "h2e2");
        Checks.isTrue(session.awaitingAi(), "玩家走完应轮到 AI");

        session.setPlayerIsRed(false);
        Checks.isTrue(session.awaitingAi(), "玩家执黑时开局应轮到 AI");

        session.setMode(GameSession.Mode.PVP);
        Checks.isFalse(session.awaitingAi(), "人人对战不应有 AI 回合");
    }
}
