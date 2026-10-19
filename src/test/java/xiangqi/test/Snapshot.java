package xiangqi.test;

import xiangqi.ai.Difficulty;
import xiangqi.core.Move;
import xiangqi.game.GameSession;
import xiangqi.ui.BoardView;
import xiangqi.ui.RecordPanel;
import xiangqi.ui.SidebarPanel;
import xiangqi.ui.Theme;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 离屏渲染工具：把「侧栏 + 棋盘 + 记录栏」画成 PNG，用于 README 截图与视觉回归。
 *
 * <p>不依赖真实窗口显示（pack 后直接 printAll），因此可以在无头环境跑。</p>
 */
public final class Snapshot {

    private Snapshot() {
    }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length > 0 ? args[0] : "docs/screenshot.png");
        int width = args.length > 1 ? Integer.parseInt(args[1]) : 1080;
        int height = args.length > 2 ? Integer.parseInt(args[2]) : 760;
        Theme theme = Theme.byName(args.length > 3 ? args[3] : "木纹");

        SwingUtilities.invokeAndWait(() -> {
            try {
                render(output, width, height, theme);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println("snapshot -> " + output.toAbsolutePath());
    }

    private static void render(Path output, int width, int height, Theme theme) throws Exception {
        GameSession session = new GameSession();
        session.setMode(GameSession.Mode.PVE);
        session.setPlayerIsRed(true);
        session.setDifficulty(Difficulty.HARD);

        // 走一段常见开局，让截图里有着法、被吃子与最后一手高亮
        play(session, "h2e2");   // 炮二平五
        play(session, "h9g7");   // 马8进7
        play(session, "h0g2");   // 马二进三
        play(session, "i9h9");   // 车9平8
        play(session, "i0h0");   // 车一平二
        play(session, "b9c7");   // 马2进3
        play(session, "b2b6");   // 炮八进四
        play(session, "a9a8");   // 车1进1
        play(session, "g3g4");   // 兵七进一（走成黑方回合，便于检查「该谁走」的底色）

        SidebarPanel sidebar = new SidebarPanel(new NoopActions(), theme);
        RecordPanel record = new RecordPanel(ply -> {
        }, theme);
        BoardView board = new BoardView();
        board.setTheme(theme);
        board.setBoard(session.board());
        board.setLastMove(session.displayLastMove());
        board.setShowCoordinates(true);

        record.update(session);
        sidebar.refresh(session, false, "");

        JLabel status = new JLabel("AI 思考中…");
        status.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        status.setForeground(theme.panelText);
        JLabel engine = new JLabel("内置引擎 d8 +0.28");
        engine.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        engine.setForeground(theme.panelMuted);

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(theme.panelBg);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, theme.panelBorder),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        statusBar.add(status, BorderLayout.WEST);
        statusBar.add(engine, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(theme.panelBg);
        content.add(sidebar, BorderLayout.WEST);
        content.add(board, BorderLayout.CENTER);
        content.add(record, BorderLayout.EAST);
        content.add(statusBar, BorderLayout.SOUTH);
        content.setSize(width, height);

        // 借一个未显示的 JFrame 完成 peer 与布局计算
        JFrame holder = new JFrame();
        holder.setUndecorated(true);
        holder.setContentPane(content);
        holder.setPreferredSize(new Dimension(width, height));
        holder.pack();
        content.setSize(width, height);
        layoutTree(content);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(theme.panelBg);
        g2.fillRect(0, 0, width, height);
        content.printAll(g2);
        g2.dispose();
        holder.dispose();

        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        ImageIO.write(image, "png", new File(output.toString()));
    }

    /** 未显示的窗口不会自动完成布局，这里手动递归 doLayout。 */
    private static void layoutTree(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof java.awt.Container nested) {
                layoutTree(nested);
            }
        }
    }

    private static void play(GameSession session, String uci) {
        Move move = xiangqi.core.Uci.fromUci(uci);
        if (move == null || !session.play(move)) {
            throw new IllegalStateException("示例着法非法: " + uci);
        }
    }

    private static final class NoopActions implements SidebarPanel.Actions {
        @Override
        public void newGame() {
        }

        @Override
        public void undo() {
        }

        @Override
        public void redo() {
        }

        @Override
        public void resign() {
        }

        @Override
        public void offerDraw() {
        }

        @Override
        public void hint() {
        }

        @Override
        public void chooseEngine() {
        }

        @Override
        public void modeChanged(GameSession.Mode mode) {
        }

        @Override
        public void difficultyChanged(Difficulty difficulty) {
        }

        @Override
        public void sideChanged(boolean red) {
        }

        @Override
        public void themeChanged(Theme theme) {
        }

        @Override
        public void soundChanged(boolean on) {
        }

        @Override
        public void coordinatesChanged(boolean on) {
        }
    }
}
