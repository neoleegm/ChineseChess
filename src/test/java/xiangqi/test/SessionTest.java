package xiangqi.test;

import xiangqi.ai.Difficulty;
import xiangqi.core.Board;
import xiangqi.core.GameResult;
import xiangqi.core.Move;
import xiangqi.core.Piece;
import xiangqi.core.Uci;
import xiangqi.game.DrawPolicy;
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
        Checks.test("实时用时随回合推进", SessionTest::liveClock);
        Checks.test("AI 只在自身不占优时接受和棋", SessionTest::drawPolicy);
        Checks.test("认输后不能再悔棋", SessionTest::cannotUndoResignation);
        Checks.test("载入终局棋谱保留结果", SessionTest::loadFinishedRecord);
        Checks.test("AI 落子不打断复盘", SessionTest::aiMoveKeepsReview);
        Checks.test("载入后可选择从终局继续", SessionTest::resumeFromFinished);
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

    /** 实时时钟：轮到谁，谁的用时就在涨；落子后停住并计入累计。 */
    private static void liveClock() {
        long[] nanos = {0};
        GameSession session = new GameSession(() -> nanos[0]);
        session.setMode(GameSession.Mode.PVE);
        session.setPlayerIsRed(true);
        session.setDifficulty(Difficulty.MEDIUM);

        Checks.equal(0L, session.liveRedMillis(), "开局红方时间为 0");
        nanos[0] = 5_000_000_000L; // 过了 5 秒
        Checks.equal(5_000L, session.liveRedMillis(), "红方实时用时");
        Checks.equal(0L, session.liveBlackMillis(), "黑方还没走，不计时");

        playUci(session, "h2e2");
        Checks.equal(5_000L, session.redMillis(), "落子后红方累计 5 秒");
        nanos[0] += 3_000_000_000L;
        Checks.equal(3_000L, session.liveBlackMillis(), "轮到黑方计时");
        Checks.equal(5_000L, session.liveRedMillis(), "红方停表");

        session.resign();
        long frozen = session.liveBlackMillis();
        nanos[0] += 10_000_000_000L;
        Checks.equal(frozen, session.liveBlackMillis(), "终局后时钟停住");
    }

    /** 求和策略：AI 只有在自己不占优时才接受。 */
    private static void drawPolicy() {
        Board board = new Board();
        Checks.isFalse(DrawPolicy.aiAcceptsDraw(board, true, 0, 4), "开局太早应拒绝");
        Checks.isTrue(DrawPolicy.aiAcceptsDraw(board, true, 0, 30), "均势应接受");
        Checks.isFalse(DrawPolicy.aiAcceptsDraw(board, true, 200, 30), "AI 领先应拒绝");
        Checks.isTrue(DrawPolicy.aiAcceptsDraw(board, true, -200, 30), "AI 落后应接受");

        // 没有评分时退回子力差：黑方多一只车，AI 执黑（玩家执红）应拒绝
        Board blackAhead = new Board();
        blackAhead.clear();
        blackAhead.place(Piece.Type.KING, true, 9, 4);
        blackAhead.place(Piece.Type.KING, false, 0, 3);
        blackAhead.place(Piece.Type.ROOK, false, 5, 8);
        Checks.isFalse(DrawPolicy.aiAcceptsDraw(blackAhead, true, null, 30), "AI 多子应拒绝");
        Checks.isTrue(DrawPolicy.aiAcceptsDraw(blackAhead, false, null, 30), "玩家执黑时 AI 是红方，落后应接受");
    }

    private static void cannotUndoResignation() {
        GameSession session = pveSession();
        playUci(session, "h2e2");
        playUci(session, "h9g7");
        Checks.isTrue(session.canUndo(), "正常局面可以悔棋");
        session.resign();
        Checks.isFalse(session.canUndo(), "认输后不能再悔棋把对局复活");
        Checks.isFalse(session.undo(), "悔棋应被拒绝");
        Checks.isTrue(session.isOver(), "对局仍是结束状态");

        GameSession agreed = pveSession();
        playUci(agreed, "h2e2");
        playUci(agreed, "h9g7");
        agreed.agreeDraw();
        Checks.isFalse(agreed.canUndo(), "议和后同样不能悔棋");
    }

    private static void loadFinishedRecord() {
        try {
            GameSession session = pveSession();
            playUci(session, "h2e2");
            playUci(session, "h9g7");
            session.resign();
            String text = GameRecord.render(session);
            Checks.isTrue(text.contains("[Reason \"RESIGNATION\"]"), "棋谱应记录结束原因");

            Path file = Files.createTempFile("xiangqi-end-", ".xq");
            try {
                Files.writeString(file, text, java.nio.charset.StandardCharsets.UTF_8);
                GameRecord.Data data = GameRecord.load(file);
                Checks.notNull(data.result(), "应还原结果");
                Checks.equal(GameResult.Reason.RESIGNATION, data.result().reason(), "原因还原");

                GameSession restored = new GameSession();
                restored.loadMoves(data.moves(), data.mode(), data.difficulty(),
                        data.playerIsRed(), data.result());
                Checks.isTrue(restored.isOver(), "载入后仍是终局");
                Checks.equal(GameResult.Reason.RESIGNATION, restored.result().reason(), "载入后原因一致");
                Checks.isFalse(restored.awaitingAi(), "不该再叫 AI 接着走");
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static void aiMoveKeepsReview() {
        GameSession session = pveSession();
        playUci(session, "h2e2");
        playUci(session, "h9g7");
        playUci(session, "h0g2");
        session.setReviewPly(1);
        Checks.isTrue(session.reviewing(), "应处于复盘");

        Checks.isTrue(session.play(Uci.fromUci("b9c7"), true), "AI 应着应成功");
        Checks.isTrue(session.reviewing(), "复盘状态应保留");
        Checks.equal(1, session.reviewPly(), "复盘手数不变");
        Checks.equal(4, session.plyCount(), "着法表仍应更新");
    }

    private static void resumeFromFinished() {
        GameSession session = pveSession();
        playUci(session, "h2e2");
        playUci(session, "h9g7");
        session.resign();
        Checks.isTrue(session.resumePlay(), "认输局面可以继续下");
        Checks.isFalse(session.isOver(), "结果已清除");
        Checks.isTrue(session.isPlayerTurn(), "继续后轮到玩家走棋");
    }
}
