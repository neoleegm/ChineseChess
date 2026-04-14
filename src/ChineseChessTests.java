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
