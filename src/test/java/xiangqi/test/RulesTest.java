package xiangqi.test;

import xiangqi.core.Board;
import xiangqi.core.Fen;
import xiangqi.core.GameResult;
import xiangqi.core.Move;
import xiangqi.core.Notation;
import xiangqi.core.Piece;
import xiangqi.core.Uci;

import java.util.List;
import java.util.Random;

/**
 * 规则层测试：走法规则、将军判定、终局裁决、悔棋一致性、FEN、记谱，以及 perft 校验。
 *
 * <p>perft 是走法生成的黄金标准：从初始局面按深度穷举合法着法总数，
 * 结果必须与公开基准值 44 / 1920 / 79666 / 3290240 完全一致。
 * 任何走法生成或自将判定的疏漏都会立刻让数字对不上。</p>
 */
final class RulesTest {

    private static final long[] PERFT_EXPECTED = {1, 44, 1920, 79_666, 3_290_240};

    private RulesTest() {
    }

    static void run() {
        System.out.println("[规则与走法]");
        Checks.test("初始 FEN", RulesTest::initialFen);
        Checks.test("FEN 解析往返", RulesTest::fenRoundTrip);
        Checks.test("UCI 坐标往返", RulesTest::uciRoundTrip);
        Checks.test("perft 1-4 与基准值一致", RulesTest::perft);
        Checks.test("棋子走法规则", RulesTest::pieceRules);
        Checks.test("被牵制棋子不能离线", RulesTest::pinnedPiece);
        Checks.test("将帅照面判非法", RulesTest::kingsFacing);
        Checks.test("将死判定", RulesTest::checkmate);
        Checks.test("困毙判定", RulesTest::stalemate);
        Checks.test("三次重复判和", RulesTest::threefoldDraw);
        Checks.test("长将作负", RulesTest::perpetualCheckLoses);
        Checks.test("长捉作负", RulesTest::perpetualChaseLoses);
        Checks.test("双方都有捉但都不连续判和", RulesTest::mutualChaseIsDraw);
        Checks.test("非长将的重复不判负", RulesTest::repetitionWithoutCheckIsDraw);
        Checks.test("自然限着判和", RulesTest::naturalMoveLimitDraws);
        Checks.test("子力不足判和", RulesTest::insufficientMaterialDraws);
        Checks.test("FEN 半回合计数随吃子归零", RulesTest::halfmoveClockTracksCaptures);
        Checks.test("随机对局悔棋可完全还原", RulesTest::undoConsistency);
        Checks.test("克隆棋盘与原件互不影响", RulesTest::cloneIsolation);
        Checks.test("克隆悔棋可还原被吃子", RulesTest::cloneUndoWithCapture);
        Checks.test("中局 FEN", RulesTest::midGameFen);
        Checks.test("中文记谱", RulesTest::notation);
        Checks.test("非法坐标与非法走法被拒绝", RulesTest::invalidMoves);
        Checks.test("将军判定覆盖马后炮与马", RulesTest::attackDetection);
    }

    // ==================== 用例 ====================

    private static void initialFen() {
        Board board = new Board();
        Checks.equal("rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1",
                Fen.toFen(board), "初始 FEN");
        Checks.equal(32, board.pieceCount(), "初始棋子数");
    }

    private static void fenRoundTrip() {
        Board initial = new Board();
        Checks.equal(placement(Fen.toFen(initial)),
                placement(Fen.toFen(Fen.parse(Fen.toFen(initial)))), "初始局面往返");

        Board mid = new Board();
        mid.play(new Move(7, 1, 7, 4));
        mid.play(new Move(0, 1, 2, 2));
        String fen = Fen.toFen(mid);
        Board parsed = Fen.parse(fen);
        // 回合计数由历史推导，解析后历史为空，因此只比对「棋盘 + 行棋方」
        Checks.equal(placement(fen), placement(Fen.toFen(parsed)), "中局往返");
        Checks.equal(32, parsed.pieceCount(), "棋子数一致");
        Checks.isTrue(parsed.isRedTurn(), "行棋方一致");
        Checks.isTrue(parsed.play(new Move(9, 7, 7, 6)), "解析出的棋盘可继续走子");

        try {
            Fen.parse("9/9/9 w");
            Checks.isTrue(false, "行数不足应抛异常");
        } catch (IllegalArgumentException expected) {
            Checks.isTrue(true, "行数不足抛异常");
        }
    }

    private static String placement(String fen) {
        String[] parts = fen.split(" ");
        return parts[0] + " " + parts[1];
    }

    private static void uciRoundTrip() {
        Move move = Uci.fromUci("b2e2");
        Checks.notNull(move, "b2e2 应能解析");
        Checks.equal(new Move(7, 1, 7, 4), move, "b2e2 坐标");
        Checks.equal("b2e2", Uci.toUci(move), "b2e2 往返");
        Checks.equal(null, Uci.fromUci("z9z9"), "非法坐标返回 null");
    }

    private static void perft() {
        for (int depth = 1; depth < PERFT_EXPECTED.length; depth++) {
            long nodes = perft(new Board(), depth);
            Checks.equal(PERFT_EXPECTED[depth], nodes, "perft(" + depth + ")");
        }
    }

    private static long perft(Board board, int depth) {
        if (depth == 0) {
            return 1;
        }
        boolean side = board.isRedTurn();
        int[] buffer = new int[128];
        int count = board.generate(buffer, side);
        long total = 0;
        for (int i = 0; i < count; i++) {
            int move = buffer[i];
            Board.MoveRecord record = board.apply(Board.moveFrom(move), Board.moveTo(move));
            if (!board.inCheck(side)) {
                total += perft(board, depth - 1);
            }
            board.revert(record);
        }
        return total;
    }

    private static Board empty() {
        Board board = new Board();
        board.clear();
        return board;
    }

    private static void pieceRules() {
        // 马：日字与蹩马腿
        Board board = empty();
        board.place(Piece.Type.HORSE, true, 4, 4);
        Checks.isTrue(board.canMove(4, 4, 2, 3), "马走日字");
        Checks.isTrue(board.canMove(4, 4, 3, 2), "马横向日字");
        Checks.isFalse(board.canMove(4, 4, 3, 3), "马不能走直线");
        board.place(Piece.Type.PAWN, false, 3, 4);
        Checks.isFalse(board.canMove(4, 4, 2, 3), "蹩马腿");
        Checks.isTrue(board.canMove(4, 4, 3, 2), "另一方向仍可走");
        board.place(Piece.Type.PAWN, false, 4, 3);
        Checks.isFalse(board.canMove(4, 4, 3, 2), "横向蹩马腿");

        // 象：田字、塞象眼、不过河
        board = empty();
        board.place(Piece.Type.ELEPHANT, true, 9, 2);
        Checks.isTrue(board.canMove(9, 2, 7, 4), "象走田字");
        Checks.isTrue(board.canMove(9, 2, 7, 0), "象走边线田字");
        Checks.isFalse(board.canMove(9, 2, 8, 3), "象不能走斜一格");
        board.place(Piece.Type.PAWN, true, 8, 3);
        Checks.isFalse(board.canMove(9, 2, 7, 4), "塞象眼");
        Checks.isTrue(board.canMove(9, 2, 7, 0), "另一方向仍可走");
        board = empty();
        board.place(Piece.Type.ELEPHANT, true, 5, 2);
        Checks.isFalse(board.canMove(5, 2, 3, 4), "象不能过河");
        Checks.isTrue(board.canMove(5, 2, 7, 4), "象可以退回己方");

        // 士：九宫内斜线一格
        board = empty();
        board.place(Piece.Type.ADVISOR, true, 9, 3);
        Checks.isTrue(board.canMove(9, 3, 8, 4), "士斜走");
        Checks.isFalse(board.canMove(9, 3, 8, 3), "士不能直走");
        Checks.isFalse(board.canMove(9, 3, 7, 5), "士不能走两格");
        board.place(Piece.Type.ADVISOR, false, 0, 3);
        Checks.isFalse(board.canMove(0, 3, 1, 2), "士不能出九宫");

        // 帅：九宫内一步
        board = empty();
        board.place(Piece.Type.KING, true, 9, 4);
        Checks.isTrue(board.canMove(9, 4, 8, 4), "帅走一格");
        Checks.isTrue(board.canMove(9, 4, 9, 5), "帅横走一格");
        Checks.isFalse(board.canMove(9, 4, 8, 5), "帅不能斜走");
        Checks.isFalse(board.canMove(9, 4, 7, 4), "帅不能走两格");
        board.place(Piece.Type.KING, false, 1, 3);
        Checks.isFalse(board.canMove(1, 3, 1, 2), "将不能出九宫");

        // 炮：无架不吃、隔一可吃、隔两不可吃
        board = empty();
        board.place(Piece.Type.CANNON, true, 4, 1);
        board.place(Piece.Type.ROOK, false, 4, 7);
        Checks.isFalse(board.canMove(4, 1, 4, 7), "炮无架不能吃子");
        Checks.isTrue(board.canMove(4, 1, 4, 5), "炮空路直行");
        board.place(Piece.Type.PAWN, false, 4, 4);
        Checks.isTrue(board.canMove(4, 1, 4, 7), "炮隔一子吃子");
        Checks.isFalse(board.canMove(4, 1, 4, 5), "炮不能越子不吃");
        board.place(Piece.Type.PAWN, true, 4, 3);
        Checks.isFalse(board.canMove(4, 1, 4, 7), "炮隔两子不能吃");

        // 兵：过河前只能前进
        board = empty();
        board.place(Piece.Type.PAWN, true, 6, 4);
        Checks.isTrue(board.canMove(6, 4, 5, 4), "兵向前");
        Checks.isFalse(board.canMove(6, 4, 6, 5), "兵过河前不能横走");
        Checks.isFalse(board.canMove(6, 4, 7, 4), "兵不能后退");
        board = empty();
        board.place(Piece.Type.PAWN, true, 4, 4);
        Checks.isTrue(board.canMove(4, 4, 4, 5), "兵过河后可横走");
        Checks.isTrue(board.canMove(4, 4, 3, 4), "兵过河后仍可前进");
        Checks.isFalse(board.canMove(4, 4, 5, 4), "兵过河后不能后退");
        board = empty();
        board.place(Piece.Type.PAWN, false, 5, 0);
        Checks.isTrue(board.canMove(5, 0, 5, 1), "卒过河后可横走");
        Checks.isFalse(board.canMove(5, 0, 4, 0), "卒不能后退");

        // 车
        board = empty();
        board.place(Piece.Type.ROOK, true, 4, 4);
        Checks.isTrue(board.canMove(4, 4, 4, 8), "车横线");
        Checks.isTrue(board.canMove(4, 4, 0, 4), "车纵线");
        Checks.isFalse(board.canMove(4, 4, 5, 5), "车不能斜走");
        board.place(Piece.Type.PAWN, false, 4, 6);
        Checks.isFalse(board.canMove(4, 4, 4, 8), "车不能越子");
        Checks.isTrue(board.canMove(4, 4, 4, 6), "车可以吃子");
    }

    private static void pinnedPiece() {
        Board board = empty();
        board.place(Piece.Type.KING, true, 9, 4);
        board.place(Piece.Type.KING, false, 0, 0);
        board.place(Piece.Type.ROOK, true, 8, 4);
        board.place(Piece.Type.ROOK, false, 5, 4);
        Checks.isFalse(board.isLegalMove(8, 4, 8, 3), "被牵制的车不能离线");
        Checks.isTrue(board.isLegalMove(8, 4, 7, 4), "被牵制的车可沿牵制线移动");
        Checks.isTrue(board.isLegalMove(8, 4, 5, 4), "可以吃掉牵制的子");
    }

    private static void kingsFacing() {
        Board board = empty();
        board.place(Piece.Type.KING, false, 0, 4);
        board.place(Piece.Type.ROOK, true, 5, 4);
        board.place(Piece.Type.KING, true, 9, 4);
        Checks.isFalse(board.inCheck(true), "有遮挡时不算照面");
        Checks.isFalse(board.isLegalMove(5, 4, 5, 3), "移开遮挡子会形成照面，非法");

        Board facing = empty();
        facing.place(Piece.Type.KING, false, 0, 4);
        facing.place(Piece.Type.KING, true, 9, 4);
        Checks.isTrue(facing.inCheck(true), "红帅被照面");
        Checks.isTrue(facing.inCheck(false), "黑将也被照面");
    }

    private static void checkmate() {
        Board board = empty();
        board.place(Piece.Type.KING, false, 0, 4);
        board.place(Piece.Type.KING, true, 9, 3);
        board.place(Piece.Type.ROOK, true, 0, 0);
        board.place(Piece.Type.ROOK, true, 1, 0);
        board.setRedTurn(false);
        Checks.isTrue(board.isGameOver(), "双车错应将死");
        Checks.isTrue(board.isCheckmate(false), "黑方被将死");
        GameResult result = board.result();
        Checks.notNull(result, "应有结果");
        Checks.equal(GameResult.Outcome.RED_WINS, result.outcome(), "红方获胜");
        Checks.equal(GameResult.Reason.CHECKMATE, result.reason(), "原因是将死");
        Checks.isTrue(result.text().contains("将死"), "文案包含将死");
    }

    private static void stalemate() {
        Board board = empty();
        board.place(Piece.Type.KING, false, 0, 3);
        board.place(Piece.Type.KING, true, 9, 4);
        board.place(Piece.Type.ROOK, true, 1, 4);
        board.place(Piece.Type.ROOK, true, 1, 2);
        board.setRedTurn(false);
        Checks.isFalse(board.inCheck(false), "困毙时并未被将军");
        Checks.isTrue(board.isGameOver(), "困毙应结束对局");
        Checks.isFalse(board.isCheckmate(false), "困毙不是将死");
        Checks.equal(GameResult.Reason.STALEMATE, board.result().reason(), "原因是困毙");
    }

    private static void threefoldDraw() {
        Board board = new Board();
        int[][] cycle = {
                {9, 1, 7, 2}, {0, 1, 2, 2}, {7, 2, 9, 1}, {2, 2, 0, 1}
        };
        for (int[] move : cycle) {
            Checks.isTrue(board.play(new Move(move[0], move[1], move[2], move[3])), "马往返应可走");
        }
        Checks.isFalse(board.isGameOver(), "第二次出现不应终局");
        for (int[] move : cycle) {
            Checks.isTrue(board.play(new Move(move[0], move[1], move[2], move[3])), "马往返应可走");
        }
        Checks.isTrue(board.isGameOver(), "第三次出现应终局");
        Checks.equal(Board.RepetitionOutcome.DRAW, board.repetitionOutcome(), "判和");
        Checks.equal(GameResult.Reason.REPETITION, board.result().reason(), "原因是重复局面");
    }

    private static void perpetualCheckLoses() {
        Board board = empty();
        board.place(Piece.Type.KING, true, 9, 5);
        board.place(Piece.Type.KING, false, 0, 4);
        board.place(Piece.Type.ROOK, true, 5, 3);
        int[][] sequence = {
                {5, 3, 5, 4}, {0, 4, 0, 3}, {5, 4, 5, 3}, {0, 3, 0, 4},
                {5, 3, 5, 4}, {0, 4, 0, 3}, {5, 4, 5, 3}, {0, 3, 0, 4}
        };
        for (int i = 0; i < sequence.length; i++) {
            int[] move = sequence[i];
            Checks.isTrue(board.play(new Move(move[0], move[1], move[2], move[3])),
                    "第 " + (i + 1) + " 手长将序列应可走");
            if (i < sequence.length - 1) {
                Checks.isFalse(board.isGameOver(), "第三次重复之前不应终局");
            }
        }
        Checks.isTrue(board.isGameOver(), "第三次重复应终局");
        Checks.equal(Board.RepetitionOutcome.RED_LOSES, board.repetitionOutcome(), "红方长将作负");
        Checks.isTrue(board.result().text().contains("长将"), "文案包含长将");
    }

    private static void repetitionWithoutCheckIsDraw() {
        Board board = empty();
        board.place(Piece.Type.KING, true, 9, 5);
        board.place(Piece.Type.KING, false, 0, 3);
        board.place(Piece.Type.ROOK, true, 5, 0);
        board.place(Piece.Type.ROOK, false, 2, 8);
        int[][] cycle = {
                {5, 0, 5, 1}, {2, 8, 2, 7}, {5, 1, 5, 0}, {2, 7, 2, 8}
        };
        for (int round = 0; round < 2; round++) {
            for (int[] move : cycle) {
                Checks.isTrue(board.play(new Move(move[0], move[1], move[2], move[3])), "循环应可走");
            }
        }
        Checks.isTrue(board.isGameOver(), "第三次重复应终局");
        Checks.equal(Board.RepetitionOutcome.DRAW, board.repetitionOutcome(), "无将军应判和");
    }

    private static void undoConsistency() {
        Board board = new Board();
        String initialFen = Fen.toFen(board);
        long initialKey = board.key();
        Random random = new Random(42);
        int plies = 0;
        while (plies < 160 && !board.isGameOver()) {
            List<Move> moves = board.legalMoves();
            if (moves.isEmpty()) {
                break;
            }
            Move move = moves.get(random.nextInt(moves.size()));
            Checks.isTrue(board.play(move), "随机合法着法应可走");
            plies++;
        }
        int undone = 0;
        while (board.undo()) {
            undone++;
        }
        Checks.equal(plies, undone, "悔棋步数应与落子数一致");
        Checks.equal(initialFen, Fen.toFen(board), "全部悔棋后 FEN 应回到初始");
        Checks.equal(initialKey, board.key(), "全部悔棋后 Zobrist 应回到初始");
        Checks.isFalse(board.isGameOver(), "初始局面不应终局");
    }

    private static void cloneIsolation() {
        Board board = new Board();
        board.play(new Move(7, 1, 7, 4));
        board.play(new Move(0, 1, 2, 2));
        String before = Fen.toFen(board);

        Board clone = board.copy();
        Checks.isTrue(clone.undo(), "副本可悔棋");
        Checks.isTrue(clone.undo(), "副本可悔棋两次");
        Checks.equal(before, Fen.toFen(board), "副本悔棋不影响原棋盘");
        Checks.equal(32, board.pieceCount(), "原棋盘棋子数不变");
        Checks.equal("rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1",
                Fen.toFen(clone), "副本悔到底应等于初始局面");
    }

    private static void cloneUndoWithCapture() {
        Board board = new Board();
        Checks.isTrue(board.play(new Move(7, 1, 0, 1)), "炮吃马");
        Checks.isTrue(board.play(new Move(0, 0, 0, 1)), "车吃炮");
        Board clone = board.copy();
        Checks.isTrue(clone.undo(), "副本悔棋恢复炮");
        Checks.isTrue(clone.undo(), "副本继续悔棋恢复马");
        Checks.equal(Fen.toFen(new Board()), Fen.toFen(clone), "副本应完全回到初始局面");
    }

    private static void midGameFen() {
        Board board = new Board();
        Checks.isTrue(board.play(new Move(7, 1, 7, 4)), "炮八平五");
        Checks.isTrue(board.play(new Move(0, 1, 2, 2)), "马2进3");
        // 半回合计数：连续两手没有吃子，所以是 2（FEN 第 5 段）
        Checks.equal("r1bakabnr/9/1cn4c1/p1p1p1p1p/9/9/P1P1P1P1P/4C2C1/9/RNBAKABNR w - - 2 2",
                Fen.toFen(board), "中局 FEN");
    }

    /**
     * 长捉：红车在两格之间来回，每一手都新捉住一只无根黑马；黑马每次逃开。
     * 循环里红方每步都在捉，黑方只是在逃，按规则红方长捉作负。
     */
    private static void perpetualChaseLoses() {
        Board board = empty();
        board.place(Piece.Type.KING, true, 9, 4);
        board.place(Piece.Type.KING, false, 0, 3);
        board.place(Piece.Type.ROOK, true, 3, 0);
        board.place(Piece.Type.HORSE, false, 1, 7);

        int[][] cycle = {
                {3, 0, 1, 0}, {1, 7, 3, 6},
                {1, 0, 3, 0}, {3, 6, 1, 7}
        };
        for (int round = 0; round < 2; round++) {
            for (int[] move : cycle) {
                Checks.isTrue(board.play(new Move(move[0], move[1], move[2], move[3])),
                        "长捉序列应可走：" + move[0] + "," + move[1] + "->" + move[2] + "," + move[3]);
            }
        }
        Checks.isTrue(board.isGameOver(), "第三次重复应终局");
        Checks.equal(Board.RepetitionOutcome.RED_LOSES, board.repetitionOutcome(), "红方长捉作负");
        Checks.equal(GameResult.Reason.PERPETUAL_CHASE, board.result().reason(), "原因是长捉");
        Checks.isTrue(board.result().text().contains("长捉"), "文案包含长捉");
    }

    /**
     * 双方各有一次「捉」的循环：红车捉黑马、黑车捉红马，但两边都是「捉一手、逃一手」，
     * 不构成连续长打，应判和。
     */
    private static void mutualChaseIsDraw() {
        Board board = empty();
        board.place(Piece.Type.KING, true, 9, 4);
        board.place(Piece.Type.KING, false, 0, 3);
        board.place(Piece.Type.ROOK, true, 3, 0);
        board.place(Piece.Type.ROOK, false, 8, 0);
        board.place(Piece.Type.HORSE, false, 1, 7);
        board.place(Piece.Type.HORSE, true, 6, 2);

        int[][] cycle = {
                {3, 0, 1, 0}, {8, 0, 6, 0},   // 红车捉马 / 黑车捉马
                {6, 2, 8, 3}, {1, 7, 3, 6},   // 红马逃 / 黑马逃
                {1, 0, 3, 0}, {6, 0, 8, 0},   // 红车捉马 / 黑车捉马
                {8, 3, 6, 2}, {3, 6, 1, 7}    // 红马逃 / 黑马逃
        };
        for (int round = 0; round < 2; round++) {
            for (int[] move : cycle) {
                Checks.isTrue(board.play(new Move(move[0], move[1], move[2], move[3])),
                        "循环着法应可走：" + move[0] + "," + move[1] + "->" + move[2] + "," + move[3]);
            }
        }
        Checks.isTrue(board.isGameOver(), "第三次重复应终局");
        Checks.equal(Board.RepetitionOutcome.DRAW, board.repetitionOutcome(), "双方都不连续长打，判和");
        Checks.equal(GameResult.Reason.REPETITION, board.result().reason(), "原因是三次重复");
    }

    private static void naturalMoveLimitDraws() {
        // 从 FEN 还原到「已经 119 个半回合没吃子」，再走一步就到 120
        Board board = Fen.parse("3k5/9/9/9/9/9/9/9/9/4K4 w - - 119 60");
        Checks.equal(119, board.pliesSinceCapture(), "FEN 还原半回合计数");
        Checks.isTrue(board.play(new Move(9, 4, 8, 4)), "帅进一");
        Checks.equal(GameResult.Reason.MOVE_LIMIT, board.result().reason(), "自然限着判和");
        Checks.isTrue(board.result().isDraw(), "结果是和棋");

        // 吃子会清零
        Board eaten = Fen.parse("3k5/9/9/9/9/9/9/9/3pR4/4K4 w - - 40 40");
        Checks.isTrue(eaten.play(new Move(8, 4, 8, 3)), "车吃卒");
        Checks.equal(0, eaten.pliesSinceCapture(), "吃子后计数归零");
    }

    private static void insufficientMaterialDraws() {
        Board board = Fen.parse("2bakab2/9/9/9/9/9/9/9/9/3K5 w - - 0 40");
        Checks.equal(GameResult.Reason.INSUFFICIENT_MATERIAL, board.result().reason(), "只剩将士象判和");
        Checks.isTrue(board.result().isDraw(), "结果是和棋");

        Board withRook = Fen.parse("2bakab2/9/9/9/9/9/9/9/9/2RK5 w - - 0 40");
        Checks.equal(null, withRook.result(), "还有车时不能判和");
    }

    private static void halfmoveClockTracksCaptures() {
        Board board = new Board();
        Checks.equal(0, board.pliesSinceCapture(), "开局为 0");
        board.play(new Move(7, 1, 7, 4));
        board.play(new Move(0, 1, 2, 2));
        Checks.equal(2, board.pliesSinceCapture(), "两个半回合");
        Checks.isTrue(board.play(new Move(7, 4, 3, 4)), "炮打卒");
        Checks.equal(0, board.pliesSinceCapture(), "吃子后归零");
        Checks.isTrue(board.undo(), "悔棋");
        Checks.equal(2, board.pliesSinceCapture(), "悔棋还原计数");
    }

    private static void notation() {
        Board board = new Board();
        Checks.equal("炮二平五", Notation.chinese(board, new Move(7, 7, 7, 4)), "炮二平五");
        Checks.equal("马8进7", Notation.chinese(board, new Move(0, 7, 2, 6)), "马8进7");
        Checks.equal("马2进3", Notation.chinese(board, new Move(0, 1, 2, 2)), "马2进3");
        Checks.equal("马二进三", Notation.chinese(board, new Move(9, 7, 7, 6)), "马二进三");
        Checks.equal("兵三进一", Notation.chinese(board, new Move(6, 6, 5, 6)), "兵三进一");
        Checks.equal("车一平二", Notation.chinese(board, new Move(9, 8, 9, 7)), "车一平二");
        Checks.equal("将5平6", Notation.chinese(board, new Move(0, 4, 0, 5)), "将5平6");

        Board twoRooks = empty();
        twoRooks.place(Piece.Type.KING, true, 9, 4);
        twoRooks.place(Piece.Type.KING, false, 0, 0);
        twoRooks.place(Piece.Type.ROOK, true, 5, 4);
        twoRooks.place(Piece.Type.ROOK, true, 8, 4);
        Checks.equal("前车平六", Notation.chinese(twoRooks, new Move(5, 4, 5, 3)), "前车平六");
        Checks.equal("后车进一", Notation.chinese(twoRooks, new Move(8, 4, 7, 4)), "后车进一");

        Board king = empty();
        king.place(Piece.Type.KING, true, 9, 4);
        king.place(Piece.Type.KING, false, 0, 0);
        Checks.equal("帅五平四", Notation.chinese(king, new Move(9, 4, 9, 5)), "帅五平四");
        Checks.equal("帅五进一", Notation.chinese(king, new Move(9, 4, 8, 4)), "帅五进一");

        Board fourPawns = empty();
        for (int row = 3; row <= 6; row++) {
            fourPawns.place(Piece.Type.PAWN, true, row, 4);
        }
        Checks.equal("前兵平四", Notation.chinese(fourPawns, new Move(3, 4, 3, 5)), "四兵最前");
        Checks.equal("后兵平四", Notation.chinese(fourPawns, new Move(6, 4, 6, 5)), "四兵最后");

        Board fivePawns = empty();
        for (int row = 2; row <= 6; row++) {
            fivePawns.place(Piece.Type.PAWN, true, row, 4);
        }
        Checks.equal("二兵平四", Notation.chinese(fivePawns, new Move(3, 4, 3, 5)), "五兵第二位");
        Checks.equal("后兵平四", Notation.chinese(fivePawns, new Move(6, 4, 6, 5)), "五兵最后");
    }

    private static void invalidMoves() {
        Board board = new Board();
        Checks.isFalse(board.play(new Move(-1, 0, 0, 0)), "越界起点应被拒绝");
        Checks.isFalse(board.play(new Move(9, 0, 10, 0)), "越界终点应被拒绝");
        Checks.isFalse(board.play(new Move(4, 4, 5, 5)), "空格起点应被拒绝");
        Checks.isFalse(board.play(new Move(9, 0, 9, 1)), "不能吃自己的子");
    }

    private static void attackDetection() {
        // 马后炮：炮借马作架攻击黑将
        Board board = empty();
        board.place(Piece.Type.KING, false, 0, 4);
        board.place(Piece.Type.KING, true, 9, 4);
        board.place(Piece.Type.CANNON, true, 2, 4);
        board.place(Piece.Type.HORSE, true, 1, 4);
        Checks.isTrue(board.inCheck(false), "马后炮应判将军");

        // 马的攻击：马在 (2,3) 可攻击 (0,4)
        Board horse = empty();
        horse.place(Piece.Type.KING, false, 0, 4);
        horse.place(Piece.Type.KING, true, 9, 3);
        horse.place(Piece.Type.HORSE, true, 2, 3);
        Checks.isTrue(horse.inCheck(false), "马应攻击到将");
        horse.place(Piece.Type.PAWN, true, 1, 3);
        Checks.isFalse(horse.inCheck(false), "蹩马腿后不应攻击到将");

        // 兵的侧翼攻击：过河卒横向将军
        Board pawn = empty();
        pawn.place(Piece.Type.KING, false, 0, 4);
        pawn.place(Piece.Type.KING, true, 9, 0);
        pawn.place(Piece.Type.PAWN, true, 0, 3);
        Checks.isTrue(pawn.inCheck(false), "过河兵横向攻击应将");
    }
}
