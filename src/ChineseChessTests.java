/**
 * Lightweight command-line checks for the board rules and engine adapters.
 */
public class ChineseChessTests {
    public static void main(String[] args) {
        testInitialFen();
        testMoveCodec();
        testKingsFacingRule();
        testAiMovesAreExecutable();
        testBestMoveParsing();
        testPieceRules();
        testPinnedPiece();
        testCheckmate();
        testStalemate();
        testUndoConsistency();
        testMidGameFen();
        testThreefoldRepetitionDraw();
        testPerpetualCheckLoses();
        testCloneUndoIsolation();
        testAiTakesHangingRook();
        testAiFindsMateInOne();
        testMoveNotation();
        testAiAvoidsPerpetualCheck();
        System.out.println("All ChineseChess tests passed.");
    }

    private static void testInitialFen() {
        ChessBoard board = new ChessBoard();
        String expected = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";
        assertEquals(expected, FenCodec.toFen(board), "initial FEN");
    }

    private static void testMoveCodec() {
        Move move = MoveCodec.fromUci("b2e2");
        assertTrue(move != null, "b2e2 should parse");
        assertEquals(new Move(7, 1, 7, 4), move, "b2e2 coordinates");
        assertEquals("b2e2", MoveCodec.toUci(move), "b2e2 round trip");
    }

    private static void testKingsFacingRule() {
        ChessBoard board = ChessBoard.createEmptyForTesting();
        board.addPieceForTesting(ChessPiece.Type.KING, false, 0, 4);
        board.addPieceForTesting(ChessPiece.Type.ROOK, true, 5, 4);
        board.addPieceForTesting(ChessPiece.Type.KING, true, 9, 4);

        assertTrue(!board.isKingAttacked(true), "blocking piece should prevent face-off check");
        assertTrue(!board.isLegalMove(5, 4, 5, 3), "moving blocker should expose illegal king face-off");

        ChessBoard facingBoard = ChessBoard.createEmptyForTesting();
        facingBoard.addPieceForTesting(ChessPiece.Type.KING, false, 0, 4);
        facingBoard.addPieceForTesting(ChessPiece.Type.KING, true, 9, 4);
        assertTrue(facingBoard.isKingAttacked(true), "red king should be attacked by face-off");
        assertTrue(facingBoard.isKingAttacked(false), "black king should be attacked by face-off");
    }

    private static void testAiMovesAreExecutable() {
        for (ChessAI.Difficulty difficulty : ChessAI.Difficulty.values()) {
            ChessBoard redBoard = new ChessBoard();
            ChessAI redAi = new ChessAI(difficulty);
            int[] redMove = redAi.getNextMove(redBoard, true);
            assertTrue(redMove != null, difficulty + " should return a red move");
            assertTrue(redBoard.movePiece(redMove[0], redMove[1], redMove[2], redMove[3]),
                difficulty + " red move should execute");

            ChessBoard board = new ChessBoard();
            board.setRedTurnForTesting(false);
            ChessAI ai = new ChessAI(difficulty);
            int[] move = ai.getNextMove(board, false);
            assertTrue(move != null, difficulty + " should return a move");
            assertTrue(board.movePiece(move[0], move[1], move[2], move[3]),
                difficulty + " move should execute");
        }
    }

    private static void testBestMoveParsing() {
        Move move = PikafishEngine.parseBestMoveLine("bestmove b2e2 ponder h9g7");
        assertEquals(new Move(7, 1, 7, 4), move, "Pikafish bestmove parsing");
    }

    /**
     * 各棋子走法规则（canMove 只校验走法，不涉及将军，因此无需摆将/帅）
     */
    private static void testPieceRules() {
        // 马：日字与蹩马腿
        ChessBoard b = ChessBoard.createEmptyForTesting();
        b.addPieceForTesting(ChessPiece.Type.HORSE, true, 4, 4);
        assertTrue(b.canMove(4, 4, 2, 3), "horse 日字 should be legal");
        assertTrue(b.canMove(4, 4, 3, 2), "horse 横向日字 should be legal");
        assertTrue(!b.canMove(4, 4, 3, 3), "horse 非日字 should be illegal");
        b.addPieceForTesting(ChessPiece.Type.PAWN, false, 3, 4);
        assertTrue(!b.canMove(4, 4, 2, 3), "horse 蹩马腿 should be illegal");
        assertTrue(b.canMove(4, 4, 3, 2), "horse 另一方向 should still be legal");
        b.addPieceForTesting(ChessPiece.Type.PAWN, false, 4, 3);
        assertTrue(!b.canMove(4, 4, 3, 2), "horse 横向蹩马腿 should be illegal");

        // 象：田字、塞象眼、不过河
        b = ChessBoard.createEmptyForTesting();
        b.addPieceForTesting(ChessPiece.Type.ELEPHANT, true, 9, 2);
        assertTrue(b.canMove(9, 2, 7, 4), "elephant 田字 should be legal");
        assertTrue(b.canMove(9, 2, 7, 0), "elephant 田字(边) should be legal");
        assertTrue(!b.canMove(9, 2, 8, 3), "elephant 非田字 should be illegal");
        b.addPieceForTesting(ChessPiece.Type.PAWN, true, 8, 3);
        assertTrue(!b.canMove(9, 2, 7, 4), "elephant 塞象眼 should be illegal");
        assertTrue(b.canMove(9, 2, 7, 0), "elephant 另一方向 should still be legal");
        b = ChessBoard.createEmptyForTesting();
        b.addPieceForTesting(ChessPiece.Type.ELEPHANT, true, 5, 2);
        assertTrue(!b.canMove(5, 2, 3, 4), "elephant 过河 should be illegal");
        assertTrue(b.canMove(5, 2, 7, 4), "elephant 不过河 should be legal");

        // 士：九宫内斜线一步
        b = ChessBoard.createEmptyForTesting();
        b.addPieceForTesting(ChessPiece.Type.ADVISOR, true, 9, 3);
        assertTrue(b.canMove(9, 3, 8, 4), "advisor 斜线 should be legal");
        assertTrue(!b.canMove(9, 3, 8, 3), "advisor 直线 should be illegal");
        assertTrue(!b.canMove(9, 3, 7, 5), "advisor 两步 should be illegal");
        b.addPieceForTesting(ChessPiece.Type.ADVISOR, false, 0, 3);
        assertTrue(!b.canMove(0, 3, 1, 2), "advisor 出九宫 should be illegal");

        // 将/帅：九宫内一步一格
        b = ChessBoard.createEmptyForTesting();
        b.addPieceForTesting(ChessPiece.Type.KING, true, 9, 4);
        assertTrue(b.canMove(9, 4, 8, 4), "king 一步 should be legal");
        assertTrue(b.canMove(9, 4, 9, 5), "king 横移 should be legal");
        assertTrue(!b.canMove(9, 4, 8, 5), "king 斜走 should be illegal");
        assertTrue(!b.canMove(9, 4, 7, 4), "king 两步 should be illegal");
        b.addPieceForTesting(ChessPiece.Type.KING, false, 1, 3);
        assertTrue(!b.canMove(1, 3, 1, 2), "king 出九宫 should be illegal");

        // 炮：无架不吃、隔一可吃、隔两子不可吃
        b = ChessBoard.createEmptyForTesting();
        b.addPieceForTesting(ChessPiece.Type.CANNON, true, 4, 1);
        b.addPieceForTesting(ChessPiece.Type.ROOK, false, 4, 7);
        assertTrue(!b.canMove(4, 1, 4, 7), "cannon 无架吃子 should be illegal");
        assertTrue(b.canMove(4, 1, 4, 5), "cannon 空路直行 should be legal");
        b.addPieceForTesting(ChessPiece.Type.PAWN, false, 4, 4);
        assertTrue(b.canMove(4, 1, 4, 7), "cannon 隔一吃子 should be legal");
        assertTrue(!b.canMove(4, 1, 4, 5), "cannon 越子不吃 should be illegal");
        b.addPieceForTesting(ChessPiece.Type.PAWN, true, 4, 3);
        assertTrue(!b.canMove(4, 1, 4, 7), "cannon 隔两子吃 should be illegal");

        // 兵/卒：过河前只能向前，过河后可横移，不能后退
        b = ChessBoard.createEmptyForTesting();
        b.addPieceForTesting(ChessPiece.Type.PAWN, true, 6, 4);
        assertTrue(b.canMove(6, 4, 5, 4), "pawn 向前 should be legal");
        assertTrue(!b.canMove(6, 4, 6, 5), "pawn 过河前横移 should be illegal");
        assertTrue(!b.canMove(6, 4, 7, 4), "pawn 后退 should be illegal");
        b = ChessBoard.createEmptyForTesting();
        b.addPieceForTesting(ChessPiece.Type.PAWN, true, 4, 4);
        assertTrue(b.canMove(4, 4, 4, 5), "pawn 过河后横移 should be legal");
        assertTrue(b.canMove(4, 4, 3, 4), "pawn 过河后向前 should be legal");
        assertTrue(!b.canMove(4, 4, 5, 4), "pawn 过河后后退 should be illegal");
        b = ChessBoard.createEmptyForTesting();
        b.addPieceForTesting(ChessPiece.Type.PAWN, false, 5, 0);
        assertTrue(b.canMove(5, 0, 5, 1), "black pawn 过河后横移 should be legal");
        assertTrue(!b.canMove(5, 0, 4, 0), "black pawn 后退 should be illegal");

        // 车：直线、不越子
        b = ChessBoard.createEmptyForTesting();
        b.addPieceForTesting(ChessPiece.Type.ROOK, true, 4, 4);
        assertTrue(b.canMove(4, 4, 4, 8), "rook 横线 should be legal");
        assertTrue(b.canMove(4, 4, 0, 4), "rook 纵线 should be legal");
        assertTrue(!b.canMove(4, 4, 5, 5), "rook 斜走 should be illegal");
        b.addPieceForTesting(ChessPiece.Type.PAWN, false, 4, 6);
        assertTrue(!b.canMove(4, 4, 4, 8), "rook 越子 should be illegal");
        assertTrue(b.canMove(4, 4, 4, 6), "rook 吃子 should be legal");
    }

    /**
     * 送将保护：被牵制的棋子离开牵制线判非法
     */
    private static void testPinnedPiece() {
        ChessBoard b = ChessBoard.createEmptyForTesting();
        b.addPieceForTesting(ChessPiece.Type.KING, true, 9, 4);
        b.addPieceForTesting(ChessPiece.Type.KING, false, 0, 0);
        b.addPieceForTesting(ChessPiece.Type.ROOK, true, 8, 4);
        b.addPieceForTesting(ChessPiece.Type.ROOK, false, 5, 4);
        assertTrue(!b.isLegalMove(8, 4, 8, 3), "pinned rook leaving the file should be illegal");
        assertTrue(b.isLegalMove(8, 4, 7, 4), "pinned rook moving along the file should be legal");
        assertTrue(b.isLegalMove(8, 4, 5, 4), "pinned rook capturing the pinner should be legal");
    }

    /**
     * 将死：双车错，黑将无处可逃
     */
    private static void testCheckmate() {
        ChessBoard b = ChessBoard.createEmptyForTesting();
        b.addPieceForTesting(ChessPiece.Type.KING, false, 0, 4);
        b.addPieceForTesting(ChessPiece.Type.KING, true, 9, 3);
        b.addPieceForTesting(ChessPiece.Type.ROOK, true, 0, 0);
        b.addPieceForTesting(ChessPiece.Type.ROOK, true, 1, 0);
        b.setRedTurnForTesting(false);
        assertTrue(b.isGameOver(), "checkmate position should be game over");
        assertTrue(b.isCheckmate(false), "black should be checkmated");
        String winner = b.getWinner();
        assertTrue(winner != null && winner.contains("将死") && winner.contains("红方获胜"),
            "checkmate text should say 将死 and 红方获胜, got: " + winner);
    }

    /**
     * 困毙：黑将未被将军但无任何合法走法，判负
     */
    private static void testStalemate() {
        ChessBoard b = ChessBoard.createEmptyForTesting();
        b.addPieceForTesting(ChessPiece.Type.KING, false, 0, 3);
        b.addPieceForTesting(ChessPiece.Type.KING, true, 9, 4);
        b.addPieceForTesting(ChessPiece.Type.ROOK, true, 1, 4);
        b.addPieceForTesting(ChessPiece.Type.ROOK, true, 1, 2);
        b.setRedTurnForTesting(false);
        assertTrue(!b.isKingAttacked(false), "stalemate king should NOT be in check");
        assertTrue(b.isGameOver(), "stalemate should be game over");
        assertTrue(!b.isCheckmate(false), "stalemate is not checkmate");
        String winner = b.getWinner();
        assertTrue(winner != null && winner.contains("困毙") && winner.contains("红方获胜"),
            "stalemate text should say 困毙 and 红方获胜, got: " + winner);
    }

    /**
     * 随机对局后全部悔棋，FEN 与 Zobrist 必须回到初始值
     */
    private static void testUndoConsistency() {
        ChessBoard b = new ChessBoard();
        String initialFen = FenCodec.toFen(b);
        long initialKey = b.getZobristKey();
        java.util.Random rand = new java.util.Random(42);
        int plies = 0;
        while (plies < 200 && !b.isGameOver()) {
            java.util.List<Move> moves = b.getLegalMoves(b.isRedTurn());
            if (moves.isEmpty()) break;
            Move m = moves.get(rand.nextInt(moves.size()));
            assertTrue(b.movePiece(m.fromRow, m.fromCol, m.toRow, m.toCol), "random legal move should execute");
            plies++;
        }
        int undone = 0;
        while (b.undo()) undone++;
        assertEquals(plies, undone, "undo count should match played plies");
        assertEquals(initialFen, FenCodec.toFen(b), "FEN should return to initial after undoing all");
        assertEquals(initialKey, b.getZobristKey(), "zobrist should return to initial after undoing all");
        assertTrue(!b.isGameOver(), "initial position should not be game over");
    }

    /**
     * 非初始局面 FEN（炮八平五、马 2 进 3 后的局面）
     */
    private static void testMidGameFen() {
        ChessBoard b = new ChessBoard();
        assertTrue(b.movePiece(7, 1, 7, 4), "red cannon to center should execute");
        assertTrue(b.movePiece(0, 1, 2, 2), "black horse should execute");
        assertEquals("r1bakabnr/9/1cn4c1/p1p1p1p1p/9/9/P1P1P1P1P/4C2C1/9/RNBAKABNR w - - 0 1",
            FenCodec.toFen(b), "mid-game FEN");
    }

    /**
     * 三次重复局面（双方双马往返，无将军）判和棋
     */
    private static void testThreefoldRepetitionDraw() {
        ChessBoard b = new ChessBoard();
        int[][] cycle = {
            {9, 1, 7, 2}, {0, 1, 2, 2}, {7, 2, 9, 1}, {2, 2, 0, 1}
        };
        for (int[] m : cycle) {
            assertTrue(b.movePiece(m[0], m[1], m[2], m[3]), "knight shuttle should execute");
        }
        assertTrue(!b.isGameOver(), "second occurrence should not end the game");
        for (int[] m : cycle) {
            assertTrue(b.movePiece(m[0], m[1], m[2], m[3]), "knight shuttle should execute");
        }
        assertTrue(b.isGameOver(), "third occurrence should end the game");
        assertEquals(ChessBoard.RepetitionOutcome.DRAW, b.getRepetitionOutcome(),
            "threefold repetition should be a draw");
        String winner = b.getWinner();
        assertTrue(winner != null && winner.contains("和棋"),
            "threefold text should mention 和棋, got: " + winner);
    }

    /**
     * 长将作负：红车每步将军，同一局面第三次出现判红方负
     */
    private static void testPerpetualCheckLoses() {
        ChessBoard b = ChessBoard.createEmptyForTesting();
        b.addPieceForTesting(ChessPiece.Type.KING, true, 9, 5);
        b.addPieceForTesting(ChessPiece.Type.KING, false, 0, 4);
        b.addPieceForTesting(ChessPiece.Type.ROOK, true, 5, 3);
        int[][] seq = {
            {5, 3, 5, 4}, // 车将军
            {0, 4, 0, 3}, // 将躲闪
            {5, 4, 5, 3}, // 车再将军
            {0, 3, 0, 4},
            {5, 3, 5, 4}, // 局面第 2 次重复
            {0, 4, 0, 3},
            {5, 4, 5, 3},
            {0, 3, 0, 4},
        };
        for (int[] m : seq) {
            assertTrue(b.movePiece(m[0], m[1], m[2], m[3]), "perpetual sequence move should execute");
            assertTrue(!b.isGameOver(), "game should not end before the third occurrence");
        }
        assertTrue(b.movePiece(5, 3, 5, 4), "final checking move should execute");
        assertTrue(b.isGameOver(), "perpetual check should end the game at the third occurrence");
        assertEquals(ChessBoard.RepetitionOutcome.RED_LOSES, b.getRepetitionOutcome(),
            "perpetual check should rule red loses");
        String winner = b.getWinner();
        assertTrue(winner != null && winner.contains("长将") && winner.contains("黑方获胜"),
            "perpetual text should say 长将 and 黑方获胜, got: " + winner);
    }

    /**
     * 克隆棋盘上的悔棋不得影响原棋盘
     */
    private static void testCloneUndoIsolation() {
        ChessBoard b = new ChessBoard();
        b.movePiece(7, 1, 7, 4);
        b.movePiece(0, 1, 2, 2);
        String fenBefore = FenCodec.toFen(b);

        ChessBoard clone = b.clone();
        assertTrue(clone.undo(), "clone should undo");
        assertTrue(clone.undo(), "clone should undo twice");

        assertEquals(fenBefore, FenCodec.toFen(b), "original board must be unchanged by clone undo");
        assertEquals(32, b.getPieces().size(), "original board piece count must stay 32");
        assertEquals("rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1",
            FenCodec.toFen(clone), "fully undone clone should equal the initial position");
    }

    /**
     * 战术 sanity：白送的车必须吃（中等/困难）
     */
    private static void testAiTakesHangingRook() {
        for (ChessAI.Difficulty d : new ChessAI.Difficulty[]{ChessAI.Difficulty.MEDIUM, ChessAI.Difficulty.HARD}) {
            ChessBoard b = ChessBoard.createEmptyForTesting();
            b.addPieceForTesting(ChessPiece.Type.KING, true, 9, 4);
            b.addPieceForTesting(ChessPiece.Type.KING, false, 0, 3);
            b.addPieceForTesting(ChessPiece.Type.ROOK, true, 4, 0);
            b.addPieceForTesting(ChessPiece.Type.ROOK, false, 4, 8);
            Move move = new ChessAI(d).findBestMove(b, true);
            assertEquals(new Move(4, 0, 4, 8), move, d + " should capture the hanging rook");
        }
    }

    /**
     * 战术 sanity：必须找到一步胜（将死或困毙，规则上同胜）
     */
    private static void testAiFindsMateInOne() {
        for (ChessAI.Difficulty d : new ChessAI.Difficulty[]{ChessAI.Difficulty.MEDIUM, ChessAI.Difficulty.HARD}) {
            ChessBoard b = ChessBoard.createEmptyForTesting();
            b.addPieceForTesting(ChessPiece.Type.KING, false, 0, 3);
            b.addPieceForTesting(ChessPiece.Type.KING, true, 9, 5);
            b.addPieceForTesting(ChessPiece.Type.ROOK, true, 1, 5);
            b.addPieceForTesting(ChessPiece.Type.ROOK, true, 5, 0);
            assertTrue(!b.isGameOver(), d + " win-in-one position should not already be over");
            Move move = new ChessAI(d).findBestMove(b, true);
            assertTrue(move != null, d + " should return a move");
            assertTrue(b.movePiece(move.fromRow, move.fromCol, move.toRow, move.toCol),
                d + " winning move should execute");
            assertTrue(b.isGameOver(), d + " should win in one move, chose: " + move);
        }
    }

    /**
     * 中文记谱法：开局常见着法与前后同名子命名
     */
    private static void testMoveNotation() {
        ChessBoard b = new ChessBoard();
        assertEquals("炮二平五", MoveNotation.toChineseNotation(b, new Move(7, 7, 7, 4)), "炮二平五");
        assertEquals("马8进7", MoveNotation.toChineseNotation(b, new Move(0, 7, 2, 6)), "马8进7");
        assertEquals("马2进3", MoveNotation.toChineseNotation(b, new Move(0, 1, 2, 2)), "马2进3");
        assertEquals("马二进三", MoveNotation.toChineseNotation(b, new Move(9, 7, 7, 6)), "马二进三");
        assertEquals("兵三进一", MoveNotation.toChineseNotation(b, new Move(6, 6, 5, 6)), "兵三进一");
        assertEquals("车一平二", MoveNotation.toChineseNotation(b, new Move(9, 8, 9, 7)), "车一平二");
        assertEquals("将5平6", MoveNotation.toChineseNotation(b, new Move(0, 4, 0, 5)), "将5平6");

        // 同列双车：前车平六、后车进一
        ChessBoard two = ChessBoard.createEmptyForTesting();
        two.addPieceForTesting(ChessPiece.Type.KING, true, 9, 4);
        two.addPieceForTesting(ChessPiece.Type.KING, false, 0, 0);
        two.addPieceForTesting(ChessPiece.Type.ROOK, true, 5, 4);
        two.addPieceForTesting(ChessPiece.Type.ROOK, true, 8, 4);
        assertEquals("前车平六", MoveNotation.toChineseNotation(two, new Move(5, 4, 5, 3)), "前车平六");
        assertEquals("后车进一", MoveNotation.toChineseNotation(two, new Move(8, 4, 7, 4)), "后车进一");

        // 帅五平六、士角炮类
        ChessBoard k = ChessBoard.createEmptyForTesting();
        k.addPieceForTesting(ChessPiece.Type.KING, true, 9, 4);
        k.addPieceForTesting(ChessPiece.Type.KING, false, 0, 0);
        assertEquals("帅五平四", MoveNotation.toChineseNotation(k, new Move(9, 4, 9, 5)), "帅五平四");
        assertEquals("帅五进一", MoveNotation.toChineseNotation(k, new Move(9, 4, 8, 4)), "帅五进一");
    }

    /**
     * 长将作负适配：两轮长将循环后，AI 不得再走立即判负的将军着
     */
    private static void testAiAvoidsPerpetualCheck() {
        int[][] seq = {
            {5, 3, 5, 4}, {0, 4, 0, 3}, {5, 4, 5, 3}, {0, 3, 0, 4},
            {5, 3, 5, 4}, {0, 4, 0, 3}, {5, 4, 5, 3}, {0, 3, 0, 4},
        };
        for (ChessAI.Difficulty d : ChessAI.Difficulty.values()) {
            ChessBoard b = ChessBoard.createEmptyForTesting();
            b.addPieceForTesting(ChessPiece.Type.KING, true, 9, 5);
            b.addPieceForTesting(ChessPiece.Type.KING, false, 0, 4);
            b.addPieceForTesting(ChessPiece.Type.ROOK, true, 5, 3);
            for (int[] m : seq) {
                assertTrue(b.movePiece(m[0], m[1], m[2], m[3]), "cycle move should execute");
            }
            // 红方此时走 (5,3)->(5,4) 将军即第三次长将，立即判负
            Move move = new ChessAI(d).findBestMove(b, true);
            assertTrue(move != null, d + " should return a move");
            assertTrue(!(move.fromRow == 5 && move.fromCol == 3 && move.toRow == 5 && move.toCol == 4),
                d + " must avoid the immediate perpetual-check loss, chose " + move);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }
}
