/**
 * Common AI engine contract used by the Swing panel.
 */
public interface Engine {
    Move findBestMove(ChessBoard board, boolean aiIsRed, long timeMs) throws Exception;
}
