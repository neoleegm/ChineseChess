package xiangqi.ui;

import xiangqi.ai.AiPlayer;
import xiangqi.ai.Difficulty;
import xiangqi.ai.SearchResult;
import xiangqi.ai.UciEngine;
import xiangqi.audio.SoundBank;
import xiangqi.core.Board;
import xiangqi.core.GameResult;
import xiangqi.core.Move;
import xiangqi.core.Notation;
import xiangqi.game.DrawPolicy;
import xiangqi.game.GameRecord;
import xiangqi.game.GameSession;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * 主窗口：把会话、AI 调度、视图三者接起来，并负责持久化与文件操作。
 *
 * <p>这里只做编排，不实现任何规则或绘制逻辑——规则在 {@code core}，
 * 搜索在 {@code ai}，绘制在 {@link BoardView}。</p>
 */
public final class MainFrame extends JFrame implements SidebarPanel.Actions {

    private static final String PREF_MODE = "mode";
    private static final String PREF_DIFFICULTY = "difficulty";
    private static final String PREF_SIDE = "side";
    private static final String PREF_SOUND = "sound";
    private static final String PREF_THEME = "theme";
    private static final String PREF_COORDS = "coords";
    private static final String PREF_FLIP = "flip";
    private static final String PREF_ENGINE = "enginePath";
    private static final String PREF_WIDTH = "windowWidth";
    private static final String PREF_HEIGHT = "windowHeight";

    private final Preferences prefs = Preferences.userNodeForPackage(MainFrame.class);

    private final GameSession session = new GameSession();
    private final AiPlayer aiPlayer = new AiPlayer();
    private final AiPlayer hintPlayer = new AiPlayer();
    private final SoundBank sounds = new SoundBank();
    private final BoardView boardView = new BoardView();
    private final AiWorker aiWorker = new AiWorker(aiPlayer, "xiangqi-ai");
    private final AiWorker hintWorker = new AiWorker(hintPlayer, "xiangqi-hint");
    /** 人机对局与提示共用的外部引擎实例（{@code null} 表示只用内置引擎）。 */
    private UciEngine engine;

    private final SidebarPanel sidebar;
    private final RecordPanel recordPanel;
    private final JLabel statusLabel;
    private final JLabel engineLabel;
    /** 每 500ms 只刷新时钟，避免为了走秒重建整个界面。 */
    private final Timer clockTimer;

    private Theme theme = Theme.WOOD;
    private boolean aiThinking;
    private boolean hintThinking;
    private Move hintMove;
    private boolean resultHandled;
    private String engineSummary = "内置引擎";
    /** 外部引擎健康检查结果：{@code null} 表示未知，空串表示正常，其余为失败原因。 */
    private String engineHealth;
    /** 提示结果文案；非空时状态栏优先显示它，直到玩家落子。 */
    private String hintText = "";

    public MainFrame() {
        super("中国象棋");
        Ui.installGlobalDefaults();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());

        sidebar = new SidebarPanel(this, theme);
        recordPanel = new RecordPanel(this::onReviewPly, theme);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(theme.panelBg);
        content.add(sidebar, BorderLayout.WEST);
        content.add(boardView, BorderLayout.CENTER);
        content.add(recordPanel, BorderLayout.EAST);

        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, theme.panelBorder),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        statusLabel = Ui.label("红方走棋", Font.BOLD, 13, theme.panelText);
        engineLabel = Ui.label("内置引擎", Font.PLAIN, 12, theme.panelMuted);
        status.add(statusLabel, BorderLayout.WEST);
        status.add(engineLabel, BorderLayout.EAST);

        add(content, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        boardView.setMoveHandler(this::onHumanMove);
        session.addListener(this::refresh);
        setJMenuBar(createMenuBar());
        installReviewKeys();

        clockTimer = new Timer(500, e -> recordPanel.updateClocks(session));
        clockTimer.start();

        restoreSettings();

        int width = prefs.getInt(PREF_WIDTH, 1080);
        int height = prefs.getInt(PREF_HEIGHT, 760);
        setPreferredSize(new Dimension(width, height));
        setMinimumSize(new Dimension(880, 640));
        pack();
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                requestShutdown();
            }
        });

        // 兜底清理：无论从菜单、Cmd+Q 还是系统强退，都别把 Pikafish 进程留在后台
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            UciEngine current = engine;
            if (current != null) {
                current.shutdown();
            }
        }, "engine-cleanup"));

        refresh();
        maybeStartAiTurn();
        SwingUtilities.invokeLater(sounds::warmUp);

        // 外面引擎只在需要时预热：简单/中等档根本不用它，开局就拉一个 4 线程进程纯属浪费
        if (session.difficulty() == Difficulty.HARD) {
            warmEngine();
        }
    }

    // ==================== 复盘快捷键 ====================

    /**
     * 复盘导航用方向键与 Esc，绑在根面板的输入映射上。
     *
     * <p>刻意不复用菜单加速键：悔棋已经是 Ctrl/Cmd+Z，如果复盘返回也挂在 U 上，
     * 玩家想退出复盘就会顺手悔掉一手棋。</p>
     */
    private void installReviewKeys() {
        InputMap input = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actions = getRootPane().getActionMap();
        bindKey(input, actions, "review-prev", KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), this::reviewBack);
        bindKey(input, actions, "review-next", KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), this::reviewForward);
        bindKey(input, actions, "review-start", KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), this::reviewStart);
        bindKey(input, actions, "review-end", KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), this::reviewEnd);
        bindKey(input, actions, "review-exit", KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), this::reviewEnd);
    }

    private void bindKey(InputMap input, ActionMap actions, String name, KeyStroke key, Runnable action) {
        input.put(key, name);
        actions.put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void reviewBack() {
        if (session.reviewing()) {
            session.setReviewPly(session.reviewPly() - 1);
        }
    }

    private void reviewForward() {
        if (session.reviewing()) {
            session.setReviewPly(session.reviewPly() + 1);
        }
    }

    private void reviewStart() {
        if (session.plyCount() > 0) {
            session.setReviewPly(0);
        }
    }

    private void reviewEnd() {
        session.exitReview();
    }

    // ==================== 菜单 ====================

    private JMenuBar createMenuBar() {
        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenuBar bar = new JMenuBar();

        JMenu game = new JMenu("对局");
        game.add(menuItem("重新开始", KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcut), this::newGame));
        game.add(menuItem("悔棋", KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcut), this::undo));
        game.add(menuItem("重做", KeyStroke.getKeyStroke(KeyEvent.VK_Z,
                shortcut | InputEvent.SHIFT_DOWN_MASK), this::redo));
        game.addSeparator();
        game.add(menuItem("提示", KeyStroke.getKeyStroke(KeyEvent.VK_H, 0), this::hint));
        game.addSeparator();
        game.add(menuItem("认输", null, this::resign));
        game.add(menuItem("求和", null, this::offerDraw));
        game.addSeparator();
        game.add(menuItem("退出", KeyStroke.getKeyStroke(KeyEvent.VK_Q, shortcut), this::requestShutdown));
        bar.add(game);

        JMenu record = new JMenu("棋谱");
        record.add(menuItem("保存棋谱…", KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcut), this::saveRecord));
        record.add(menuItem("载入棋谱…", KeyStroke.getKeyStroke(KeyEvent.VK_O, shortcut), this::loadRecord));
        record.add(menuItem("导出中文记谱…", null, this::exportRecord));
        bar.add(record);

        JMenu view = new JMenu("视图");
        view.add(menuItem("翻转棋盘", KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), this::toggleFlip));
        JCheckBoxMenuItem coords = new JCheckBoxMenuItem("显示纵线编号");
        coords.setFont(Ui.font(Font.PLAIN, 13));
        coords.setSelected(prefs.getBoolean(PREF_COORDS, true));
        coords.addActionListener(e -> {
            boolean next = coords.isSelected();
            boardView.setShowCoordinates(next);
            sidebar.setCoordinatesSelected(next);
            prefs.putBoolean(PREF_COORDS, next);
        });
        view.add(coords);
        JMenu themeMenu = new JMenu("棋盘主题");
        for (Theme candidate : Theme.ALL) {
            JMenuItem item = new JMenuItem(candidate.name);
            item.addActionListener(e -> setTheme(candidate));
            themeMenu.add(item);
        }
        view.add(themeMenu);
        bar.add(view);

        JMenu help = new JMenu("帮助");
        help.add(menuItem("游戏规则", KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0),
                () -> Dialogs.showRules(this, theme)));
        help.add(menuItem("关于", null,
                () -> Dialogs.showAbout(this, theme, aiPlayer.enginePath().isBlank()
                        ? "未配置" : new File(aiPlayer.enginePath()).getName())));
        bar.add(help);

        return bar;
    }

    private JMenuItem menuItem(String text, KeyStroke accelerator, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setFont(Ui.font(Font.PLAIN, 13));
        if (accelerator != null) {
            item.setAccelerator(accelerator);
        }
        item.addActionListener(e -> action.run());
        return item;
    }

    // ==================== 状态刷新 ====================

    private void refresh() {
        Board board = session.displayBoard();
        boardView.setBoard(board);
        boardView.setLastMove(session.displayLastMove());
        boardView.setHintMove(hintMove);
        boardView.setInteractive(canInteract());
        boardView.setBusy(aiThinking ? "AI 思考中…" : (hintThinking ? "提示计算中…" : ""));

        recordPanel.update(session);
        sidebar.refresh(session, aiThinking || hintThinking, aiPlayer.enginePath(), engineHealth);
        updateStatus();
        engineLabel.setText(engineSummary);
        maybeAnnounceResult();
    }

    private boolean canInteract() {
        return !session.reviewing() && !aiThinking && !hintThinking
                && !session.isOver() && session.isPlayerTurn();
    }

    private void updateStatus() {
        if (session.reviewing()) {
            int ply = session.reviewPly();
            statusLabel.setText("复盘：第 " + ply + " 手后（←/→ 翻看，Esc 返回对局）");
            return;
        }
        if (session.isOver()) {
            statusLabel.setText(session.result().text());
            return;
        }
        if (aiThinking) {
            statusLabel.setText("AI 思考中…");
            return;
        }
        if (hintThinking) {
            statusLabel.setText("提示计算中…");
            return;
        }
        if (!hintText.isEmpty() && session.isPlayerTurn()) {
            statusLabel.setText(hintText);
            return;
        }
        boolean red = session.board().isRedTurn();
        boolean check = session.board().inCheck(red);
        String side = red ? "红方" : "黑方";
        if (session.mode() == GameSession.Mode.PVE) {
            statusLabel.setText((session.isPlayerTurn() ? "轮到你走棋 · " + side : side + "走棋")
                    + (check ? " · 将军！" : ""));
        } else {
            statusLabel.setText(side + "走棋" + (check ? " · 将军！" : ""));
        }
    }

    // ==================== 走棋流程 ====================

    private void onHumanMove(Move move) {
        if (session.reviewing()) {
            session.exitReview();
        }
        if (!session.isPlayerTurn() || session.isOver()) {
            return;
        }
        boolean capture = session.board().pieceAt(move.to()) != null;
        hintMove = null;
        hintText = "";
        if (!session.play(move)) {
            return;
        }
        sounds.play(capture ? SoundBank.Effect.CAPTURE : SoundBank.Effect.MOVE);
        if (!session.isOver() && session.board().inCheck(session.board().isRedTurn())) {
            sounds.play(SoundBank.Effect.CHECK);
        }
        maybeStartAiTurn();
    }

    private void maybeStartAiTurn() {
        if (!session.awaitingAi() || aiThinking) {
            return;
        }
        aiThinking = true;
        hintMove = null;
        hintText = "";
        refresh();

        Board snapshot = session.board().copy();
        aiWorker.request(snapshot, move -> {
            aiThinking = false;
            if (move == null) {
                refresh();
                return;
            }
            boolean capture = session.board().pieceAt(move.to()) != null;
            // 传 keepReview=true：玩家可能正在复盘，AI 应着不应该把他拉回实时局面
            if (!session.play(move, true)) {
                refresh();
                return;
            }
            sounds.play(capture ? SoundBank.Effect.CAPTURE : SoundBank.Effect.MOVE);
            if (!session.isOver() && session.board().inCheck(session.board().isRedTurn())) {
                sounds.play(SoundBank.Effect.CHECK);
            }
            engineSummary = aiPlayer.lastInfo() + " " + aiPlayer.lastResult().summary();
            refresh();
        }, error -> {
            aiThinking = false;
            engineSummary = "AI 计算失败";
            refresh();
        });
    }

    private void maybeAnnounceResult() {
        if (!session.isOver() || resultHandled || session.reviewing()) {
            return;
        }
        resultHandled = true;
        GameResult result = session.result();
        boolean playerWon = session.playerIsRed() == result.winnerIsRed() && !result.isDraw();
        sounds.play(result.isDraw() ? SoundBank.Effect.DRAW
                : (playerWon ? SoundBank.Effect.WIN : SoundBank.Effect.LOSE));
        SwingUtilities.invokeLater(this::showResultDialog);
    }

    private void showResultDialog() {
        GameResult result = session.result();
        if (result == null) {
            return;
        }
        Object[] options = {"再来一局", "复盘", "关闭"};
        int choice = JOptionPane.showOptionDialog(this, result.text(), "对局结束",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
        if (choice == 0) {
            newGame();
        } else if (choice == 1 && session.plyCount() > 0) {
            session.setReviewPly(0);
        }
    }

    private void onReviewPly(int ply) {
        if (ply <= 0 || ply > session.plyCount()) {
            session.exitReview();
            return;
        }
        if (ply >= session.plyCount()) {
            session.exitReview();
        } else {
            session.setReviewPly(ply);
        }
    }

    // ==================== 用户操作 ====================

    @Override
    public void newGame() {
        cancelThinking();
        resultHandled = false;
        hintMove = null;
        hintText = "";
        engineSummary = "内置引擎";
        session.newGame();
        refresh();
        maybeStartAiTurn();
    }

    @Override
    public void undo() {
        // 复盘时按悔棋键，只退出复盘，不真的悔棋——否则玩家会莫名其妙丢一手
        if (session.reviewing()) {
            session.exitReview();
            return;
        }
        if (!session.canUndo()) {
            return;
        }
        cancelThinking();
        hintMove = null;
        hintText = "";
        resultHandled = false;
        session.undo();
        refresh();
        maybeStartAiTurn();
    }

    @Override
    public void redo() {
        if (!session.canRedo()) {
            return;
        }
        cancelThinking();
        hintMove = null;
        hintText = "";
        session.redo();
        refresh();
        maybeStartAiTurn();
    }

    @Override
    public void resign() {
        if (session.isOver()) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "确定认输吗？", "认输",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            cancelThinking();
            session.resign();
        }
    }

    @Override
    public void offerDraw() {
        if (session.isOver()) {
            return;
        }
        if (session.mode() == GameSession.Mode.PVP) {
            int confirm = JOptionPane.showConfirmDialog(this, "对方同意和棋吗？", "求和",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                cancelThinking();
                session.agreeDraw();
            }
            return;
        }
        cancelThinking();
        SearchResult last = aiPlayer.lastResult();
        Integer aiScore = last.move() == null ? null : last.score();
        if (DrawPolicy.aiAcceptsDraw(session.board(), session.playerIsRed(), aiScore, session.plyCount())) {
            session.agreeDraw();
        } else {
            JOptionPane.showMessageDialog(this, "AI 拒绝了和棋请求，棋局继续。", "求和",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    @Override
    public void hint() {
        if (session.isOver() || session.mode() != GameSession.Mode.PVE
                || !session.isPlayerTurn() || aiThinking || hintThinking) {
            return;
        }
        if (engine != null && engineHealth == null) {
            warmEngine(); // 第一次用提示时再拉起引擎，避免开局常驻进程
        }
        hintThinking = true;
        hintMove = null;
        hintText = "";
        refresh();

        Board snapshot = session.board().copy();
        hintWorker.request(snapshot, move -> {
            hintThinking = false;
            SearchResult info = hintPlayer.lastResult();
            if (move != null) {
                hintMove = move;
                hintText = describeHint(session.board(), move, info);
                engineSummary = hintPlayer.lastInfo() + " " + info.summary();
            }
            refresh();
        }, error -> {
            hintThinking = false;
            hintText = "提示计算失败：" + error.getMessage();
            refresh();
        });
    }

    /** 提示文案：推荐着法 + 引擎主变着的前两手 + 评估。 */
    private String describeHint(Board board, Move move, SearchResult info) {
        StringBuilder text = new StringBuilder("提示：").append(Notation.chinese(board, move));
        String line = pvLine(board, info.pv(), 1, 2);
        if (!line.isEmpty()) {
            text.append("　→　").append(line);
        }
        text.append("　·　").append(info.summary());
        return text.toString();
    }

    /** 把主变着里第 {@code skip} 手起的 {@code count} 手转成中文记谱。 */
    private String pvLine(Board board, List<Move> pv, int skip, int count) {
        Board copy = board.copy();
        StringBuilder text = new StringBuilder();
        int taken = 0;
        for (int i = 0; i < pv.size() && taken < count; i++) {
            Move move = pv.get(i);
            if (!copy.isLegalMove(move)) {
                break;
            }
            if (i >= skip) {
                if (taken > 0) {
                    text.append(' ');
                }
                text.append(Notation.chinese(copy, move));
                taken++;
            }
            copy.play(move);
        }
        return text.toString();
    }

    @Override
    public void chooseEngine() {
        JFileChooser chooser = new JFileChooser();
        String current = aiPlayer.enginePath();
        if (!current.isBlank()) {
            chooser.setSelectedFile(new File(current));
        }
        chooser.setDialogTitle("选择 Pikafish 可执行文件");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!file.isFile() || !file.canExecute()) {
            JOptionPane.showMessageDialog(this, "所选文件不存在或不可执行：\n" + file.getAbsolutePath(),
                    "外部引擎", JOptionPane.ERROR_MESSAGE);
            return;
        }
        prefs.put(PREF_ENGINE, file.getAbsolutePath());
        applyEnginePath(file.getAbsolutePath());
        JOptionPane.showMessageDialog(this,
                "已启用 " + file.getName() + "\n困难难度会优先使用它，失败时自动回退内置引擎。",
                "外部引擎", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void modeChanged(GameSession.Mode mode) {
        if (session.mode() == mode) {
            return;
        }
        if (!confirmAbandon()) {
            refresh();
            return;
        }
        cancelThinking();
        prefs.put(PREF_MODE, mode.name());
        session.setMode(mode);
        resultHandled = false;
        maybeStartAiTurn();
    }

    @Override
    public void difficultyChanged(Difficulty difficulty) {
        if (session.difficulty() == difficulty) {
            return;
        }
        if (session.mode() == GameSession.Mode.PVE && !confirmAbandon()) {
            refresh();
            return;
        }
        cancelThinking();
        prefs.put(PREF_DIFFICULTY, difficulty.name());
        aiPlayer.setDifficulty(difficulty);
        session.setDifficulty(difficulty);
        resultHandled = false;
        maybeStartAiTurn();
    }

    @Override
    public void sideChanged(boolean red) {
        if (session.playerIsRed() == red) {
            return;
        }
        if (session.mode() == GameSession.Mode.PVE && !confirmAbandon()) {
            refresh();
            return;
        }
        cancelThinking();
        prefs.put(PREF_SIDE, red ? "red" : "black");
        session.setPlayerIsRed(red);
        boardView.setFlipped(!red);
        prefs.putBoolean(PREF_FLIP, !red);
        resultHandled = false;
        maybeStartAiTurn();
    }

    @Override
    public void themeChanged(Theme next) {
        setTheme(next);
    }

    @Override
    public void soundChanged(boolean on) {
        sounds.setEnabled(on);
        prefs.putBoolean(PREF_SOUND, on);
    }

    @Override
    public void coordinatesChanged(boolean on) {
        boardView.setShowCoordinates(on);
        prefs.putBoolean(PREF_COORDS, on);
    }

    private void toggleFlip() {
        boolean flipped = !boardView.isFlipped();
        boardView.setFlipped(flipped);
        prefs.putBoolean(PREF_FLIP, flipped);
    }

    private void setTheme(Theme next) {
        theme = next;
        prefs.put(PREF_THEME, next.name);
        getContentPane().setBackground(next.panelBg);
        boardView.setTheme(next);
        sidebar.applyTheme(next);
        recordPanel.applyTheme(next);
        statusLabel.setForeground(next.panelText);
        engineLabel.setForeground(next.panelMuted);
        repaint();
    }

    // ==================== 文件 ====================

    private void saveRecord() {
        if (session.plyCount() == 0) {
            JOptionPane.showMessageDialog(this, "当前还没有走过棋。", "保存棋谱",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("保存棋谱");
        chooser.setFileFilter(new FileNameExtensionFilter("象棋棋谱 (*.xq)", "xq"));
        chooser.setSelectedFile(new File("对局-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".xq"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = ensureExtension(chooser.getSelectedFile().toPath(), ".xq");
        try {
            GameRecord.save(path, session);
            statusLabel.setText("已保存：" + path.getFileName());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "保存失败：" + e.getMessage(), "保存棋谱",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportRecord() {
        if (session.plyCount() == 0) {
            JOptionPane.showMessageDialog(this, "当前还没有走过棋。", "导出记谱",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("导出中文记谱");
        chooser.setFileFilter(new FileNameExtensionFilter("文本文件 (*.txt)", "txt"));
        chooser.setSelectedFile(new File("对局记谱.txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = ensureExtension(chooser.getSelectedFile().toPath(), ".txt");
        try {
            GameRecord.exportText(path, session);
            statusLabel.setText("已导出：" + path.getFileName());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "导出失败：" + e.getMessage(), "导出记谱",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadRecord() {
        if (!confirmAbandon()) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("载入棋谱");
        chooser.setFileFilter(new FileNameExtensionFilter("象棋棋谱 (*.xq)", "xq"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            GameRecord.Data data = GameRecord.load(chooser.getSelectedFile().toPath());
            cancelThinking();
            hintMove = null;
            hintText = "";
            // 先假定不需要弹「对局结束」，等确认后再按需覆盖
            resultHandled = true;
            List<Move> moves = data.moves();
            int applied = session.loadMoves(moves, data.mode(), data.difficulty(),
                    data.playerIsRed(), data.result());
            prefs.put(PREF_MODE, data.mode().name());
            prefs.put(PREF_DIFFICULTY, data.difficulty().name());
            prefs.put(PREF_SIDE, data.playerIsRed() ? "red" : "black");
            aiPlayer.setDifficulty(data.difficulty());
            boardView.setFlipped(!data.playerIsRed());
            refresh();
            if (data.result() != null) {
                // 棋谱已终局：默认进入复盘，绝不叫 AI 接着走（那会改掉原谱）
                session.setReviewPly(0);
                statusLabel.setText("已载入终局棋谱：" + data.result().text());
                askResumeLoaded();
            } else {
                statusLabel.setText("已载入 " + applied + " 手棋谱");
                maybeStartAiTurn();
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "载入失败：" + e.getMessage(), "载入棋谱",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /** 载入终局棋谱后询问：只复盘，还是从终局继续（仅限盘面本身还没结束的情况）。 */
    private void askResumeLoaded() {
        Object[] options = {"仅复盘", "从终局继续"};
        int choice = JOptionPane.showOptionDialog(this,
                "这盘棋已结束。可以从开局复盘，也可以从最后局面接着下。",
                "载入棋谱", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (choice != 1) {
            return;
        }
        if (!session.resumePlay()) {
            JOptionPane.showMessageDialog(this, "当前局面已经结束（将死或困毙），无法继续。", "载入棋谱",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        resultHandled = false;
        maybeStartAiTurn();
    }

    private Path ensureExtension(Path path, String extension) {
        return path.toString().toLowerCase().endsWith(extension)
                ? path : Path.of(path + extension);
    }

    // ==================== 生命周期 ====================

    private void cancelThinking() {
        aiWorker.cancel();
        hintWorker.cancel();
        aiThinking = false;
        hintThinking = false;
    }

    private boolean confirmAbandon() {
        if (session.plyCount() == 0 || session.isOver()) {
            return true;
        }
        int choice = JOptionPane.showConfirmDialog(this, "对局尚未结束，确定放弃当前对局吗？",
                "重新开始", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    /** 换引擎：先停掉正在跑的两路搜索，再整体切换，最后销毁旧进程。 */
    private void applyEnginePath(String path) {
        cancelThinking();
        UciEngine previous = engine;
        UciEngine next = path == null || path.isBlank() ? null : new UciEngine(path);
        engine = next;
        engineHealth = null;
        aiPlayer.setEngine(next);
        hintPlayer.setEngine(next); // 共用同一个实例：UCI 搜索本身串行，开两个进程只会抢 CPU
        if (previous != null && previous != next) {
            previous.shutdown();
        }
        engineSummary = next == null ? "内置引擎" : new File(path).getName();
        refresh();
        if (next != null) {
            warmEngine();
        }
    }

    /** 后台做一次握手 + 健康检查，把「缺 nnue / 平台不对」这类问题显示到侧栏。 */
    private void warmEngine() {
        UciEngine current = engine;
        if (current == null) {
            return;
        }
        Thread warmUp = new Thread(() -> {
            String problem = current.healthCheck();
            SwingUtilities.invokeLater(() -> {
                if (engine != current) {
                    return; // 期间换过引擎，结果作废
                }
                engineHealth = problem == null ? "已就绪" : "不可用：" + problem;
                if (problem != null) {
                    engineSummary = "Pikafish 不可用，回退内置引擎";
                }
                refresh();
            });
        }, "engine-warmup");
        warmUp.setDaemon(true);
        warmUp.start();
    }

    /**
     * 查找内置引擎：优先用户配置，其次 jar 所在目录、当前工作目录、{@code engines/} 子目录。
     * 双击 jar 启动时工作目录不是项目根目录，只认 cwd 会找不到。
     */
    private String locateEngine(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        List<Path> candidates = new java.util.ArrayList<>();
        try {
            Path jar = Path.of(MainFrame.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI()).toAbsolutePath();
            Path dir = jar.getParent();
            if (dir != null) {
                candidates.add(dir.resolve("pikafish"));
                candidates.add(dir.resolve("pikafish.exe"));
                candidates.add(dir.resolve("engines/pikafish"));
                candidates.add(dir.resolve("engines/pikafish.exe"));
            }
        } catch (Exception ignored) {
            // 取不到自身位置时退回工作目录
        }
        candidates.add(Path.of("pikafish").toAbsolutePath());
        candidates.add(Path.of("pikafish.exe").toAbsolutePath());
        candidates.add(Path.of("engines/pikafish").toAbsolutePath());
        candidates.add(Path.of("engines/pikafish.exe").toAbsolutePath());
        for (Path candidate : candidates) {
            File file = candidate.toFile();
            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return "";
    }

    private void restoreSettings() {
        String mode = prefs.get(PREF_MODE, GameSession.Mode.PVE.name());
        Difficulty difficulty = Difficulty.valueOf(prefs.get(PREF_DIFFICULTY, Difficulty.MEDIUM.name()));
        boolean red = !"black".equals(prefs.get(PREF_SIDE, "red"));
        boolean sound = prefs.getBoolean(PREF_SOUND, true);
        theme = Theme.byName(prefs.get(PREF_THEME, Theme.WOOD.name));
        boolean coords = prefs.getBoolean(PREF_COORDS, true);
        boolean flip = prefs.getBoolean(PREF_FLIP, !red);

        aiPlayer.setDifficulty(difficulty);
        hintPlayer.setDifficulty(Difficulty.HARD);
        hintPlayer.setTimeOverride(2_500);
        sounds.setEnabled(sound);

        String enginePath = locateEngine(prefs.get(PREF_ENGINE, ""));
        if (!enginePath.isBlank()) {
            UciEngine shared = new UciEngine(enginePath);
            engine = shared;
            aiPlayer.setEngine(shared);
            hintPlayer.setEngine(shared);
            engineSummary = new File(enginePath).getName();
        }

        session.setMode(GameSession.Mode.valueOf(mode));
        session.setPlayerIsRed(red);
        session.setDifficulty(difficulty);

        sidebar.setSoundSelected(sound);
        sidebar.setCoordinatesSelected(coords);
        boardView.setShowCoordinates(coords);
        boardView.setFlipped(flip);
        setTheme(theme);
    }

    /** 用户主动退出：对局未结束先确认。 */
    private void requestShutdown() {
        if (session.plyCount() > 0 && !session.isOver()) {
            int choice = JOptionPane.showConfirmDialog(this, "对局尚未结束，确定退出吗？\n（当前棋谱不会自动保存）",
                    "退出", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }
        shutdown();
    }

    private void shutdown() {
        clockTimer.stop();
        aiWorker.shutdown();
        hintWorker.shutdown();
        if (engine != null) {
            engine.shutdown();
        }
        sounds.dispose();
        prefs.putInt(PREF_WIDTH, Math.max(880, getWidth()));
        prefs.putInt(PREF_HEIGHT, Math.max(640, getHeight()));
        dispose();
        System.exit(0);
    }
}
