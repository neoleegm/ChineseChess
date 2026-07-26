import javax.swing.*;
import java.awt.event.MouseEvent;

/**
 * 端到端验收测试：全程使用"提示"功能与中等 AI 对弈。
 * 执红、执黑各一局并打到终局；每一手提示都必须返回合法着法（验证提示永不失效），
 * 且提示方必须全胜（验证提示质量达到高手水平）。
 *
 * 需要图形环境（会真实弹出窗口并自动对弈）：
 *     java -cp bin HintFlowTest
 */
public class HintFlowTest {
    private static final int MARGIN = 50;
    private static final int CELL_SIZE = 60;
    private static final long BUSY_TIMEOUT_MS = 30_000;

    public static void main(String[] args) {
        try {
            startDialogKiller();
            playGame(true);
            playGame(false);
            System.out.println("HintFlowTest PASSED: 提示功能全程可用，且执红执黑两局全胜。");
            System.exit(0);
        } catch (Throwable t) {
            System.err.println("HintFlowTest FAILED: " + t.getMessage());
            t.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 终局对话框是模态的，会阻塞 EDT；定时自动关闭它（等价于点"关闭"）。
     */
    private static void startDialogKiller() {
        Timer killer = new Timer(500, e -> {
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                if (w instanceof JDialog && w.isShowing()) {
                    w.dispose();
                }
            }
        });
        killer.start();
    }

    private static void playGame(boolean playerIsRed) throws Exception {
        ChessBoard board = new ChessBoard();
        JLabel status = new JLabel();
        ChessPanel panel = new ChessPanel(board, status);
        panel.setGameMode(ChessPanel.GameMode.PVE);
        panel.setDifficulty(ChessAI.Difficulty.MEDIUM);
        // 与真实应用一致：检测到本目录 pikafish 则提示引擎使用它
        java.io.File engine = new java.io.File("pikafish");
        if (engine.exists()) {
            panel.setPikafishEnginePath(engine.getAbsolutePath());
        }
        panel.setPlayerSide(playerIsRed);

        JFrame frame = new JFrame("HintFlowTest " + (playerIsRed ? "(hint plays red)" : "(hint plays black)"));
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.add(panel);
        frame.pack();
        frame.setLocation(playerIsRed ? 100 : 800, 100);
        SwingUtilities.invokeAndWait(() -> frame.setVisible(true));

        String side = playerIsRed ? "红" : "黑";
        int hints = 0;
        while (true) {
            // 等待界面空闲或终局
            waitUntil(() -> panel.isGameOverForTesting() || !panel.isBusyForTesting(),
                "等待界面空闲（第 " + (hints + 1) + " 手前）");
            if (panel.isGameOverForTesting()) break;

            // 请求提示并等待箭头出现
            SwingUtilities.invokeLater(panel::requestHint);
            waitUntil(() -> panel.isGameOverForTesting()
                    || (!panel.isBusyForTesting() && panel.getHintMoveForTesting() != null),
                "等待提示结果（第 " + (hints + 1) + " 手）");
            if (panel.isGameOverForTesting()) break;

            int[] hint = panel.getHintMoveForTesting();
            assertTrue(hint != null, side + "方第 " + (hints + 1) + " 手提示为空（提示功能失效）");

            // 模拟真实点击：先选起点棋子，再点终点落子
            clickSquare(panel, hint[0], hint[1]);
            clickSquare(panel, hint[2], hint[3]);
            hints++;

            // 等待 AI 回应完毕（或终局）
            waitUntil(() -> panel.isGameOverForTesting() || !panel.isBusyForTesting(),
                "等待 AI 走棋（第 " + hints + " 手后）");
        }

        String result = board.getWinner();
        System.out.println((playerIsRed ? "执红" : "执黑") + "局: 共 " + hints + " 手提示全部有效, 结果=" + result);
        assertTrue(result != null, "对局应正常结束");
        assertTrue(!result.contains("和棋"), side + "方不应被中等 AI 逼和: " + result);
        boolean playerWon = playerIsRed ? result.contains("红方获胜") : result.contains("黑方获胜");
        assertTrue(playerWon, side + "方提示应当战胜中等 AI，实际结果: " + result);

        panel.shutdownAI();
        SwingUtilities.invokeAndWait(frame::dispose);
    }

    private static void clickSquare(ChessPanel panel, int row, int col) throws Exception {
        int x = MARGIN + col * CELL_SIZE;
        int y = MARGIN + row * CELL_SIZE;
        SwingUtilities.invokeAndWait(() -> {
            MouseEvent e = new MouseEvent(panel, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1);
            panel.dispatchEvent(e);
        });
        Thread.sleep(80);
    }

    private interface Check {
        boolean check();
    }

    private static void waitUntil(Check condition, String what) throws Exception {
        long deadline = System.currentTimeMillis() + BUSY_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            final boolean[] ok = {false};
            SwingUtilities.invokeAndWait(() -> ok[0] = condition.check());
            if (ok[0]) return;
            Thread.sleep(100);
        }
        throw new AssertionError("超时（" + (BUSY_TIMEOUT_MS / 1000) + " 秒）: " + what);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
