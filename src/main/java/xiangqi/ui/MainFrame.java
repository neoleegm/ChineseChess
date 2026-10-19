package xiangqi.ui;

import xiangqi.ai.AiPlayer;
import xiangqi.ai.Difficulty;
import xiangqi.ai.Evaluation;
import xiangqi.ai.SearchResult;
import xiangqi.audio.SoundBank;
import xiangqi.core.Board;
import xiangqi.core.GameResult;
import xiangqi.core.Move;
import xiangqi.core.Notation;
import xiangqi.core.Piece;
import xiangqi.game.GameRecord;
import xiangqi.game.GameSession;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBoxMenuItem;
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
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
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

    private final SidebarPanel sidebar;
    private final RecordPanel recordPanel;
    private final JLabel statusLabel;
    private final JLabel engineLabel;

    private Theme theme = Theme.WOOD;
    private boolean aiThinking;
    private boolean hintThinking;
    private Move hintMove;
    private boolean resultHandled;
    private String engineSummary = "内置引擎";
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
                shutdown();
            }
        });

        refresh();
        maybeStartAiTurn();
        SwingUtilities.invokeLater(sounds::warmUp);

        // 后台预热外部引擎：握手 + 加载神经网络约需数百毫秒，
        // 放在启动时做掉，第一次按「提示」就不用等
        Thread warmUp = new Thread(() -> {
            aiPlayer.warmUp();
            hintPlayer.warmUp();
        }, "engine-warmup");
        warmUp.setDaemon(true);
        warmUp.start();
    }

    // ==================== 菜单 ====================

    private JMenuBar createMenuBar() {
        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenuBar bar = new JMenuBar();

        JMenu game = new JMenu("对局");
        game.add(menuItem("重新开始", KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), this::newGame));
        game.add(menuItem("悔棋", KeyStroke.getKeyStroke(KeyEvent.VK_U, 0), this::undo));
        game.add(menuItem("重做", KeyStroke.getKeyStroke(KeyEvent.VK_Y, 0), this::redo));
        game.addSeparator();
        game.add(menuItem("提示", KeyStroke.getKeyStroke(KeyEvent.VK_H, 0), this::hint));
        game.addSeparator();
        game.add(menuItem("认输", null, this::resign));
        game.add(menuItem("求和", null, this::offerDraw));
        game.addSeparator();
        game.add(menuItem("退出", KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0), this::shutdown));
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
        sidebar.refresh(session, aiThinking || hintThinking, aiPlayer.enginePath());
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
            statusLabel.setText("复盘：第 " + ply + " 手后（点最新一手或按 U 返回对局）");
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
            if (!session.play(move)) {
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
        sounds.play(result.isDraw() ? SoundBank.Effect.CHECK
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
                session.agreeDraw();
            }
            return;
        }
        int balance = materialBalance();
        int playerBalance = session.playerIsRed() ? balance : -balance;
        if (playerBalance <= -150) {
            session.agreeDraw();
        } else {
            JOptionPane.showMessageDialog(this, "AI 拒绝了和棋请求，棋局继续。", "求和",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** 红方减黑方的子力差（兵=110 计）。 */
    private int materialBalance() {
        int balance = 0;
        for (Piece piece : session.board().pieces()) {
            int value = Evaluation.VALUE[piece.type().ordinal()];
            balance += piece.isRed() ? value : -value;
        }
        return balance;
    }

    @Override
    public void hint() {
        if (session.isOver() || session.mode() != GameSession.Mode.PVE
                || !session.isPlayerTurn() || aiThinking || hintThinking) {
            return;
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
        chooser.setSelectedFile(new File("对局-" + System.currentTimeMillis() % 100000 + ".xq"));
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
            resultHandled = false;
            hintMove = null;
            List<Move> moves = data.moves();
            int applied = session.loadMoves(moves, data.mode(), data.difficulty(), data.playerIsRed());
            prefs.put(PREF_MODE, data.mode().name());
            prefs.put(PREF_DIFFICULTY, data.difficulty().name());
            prefs.put(PREF_SIDE, data.playerIsRed() ? "red" : "black");
            aiPlayer.setDifficulty(data.difficulty());
            boardView.setFlipped(!data.playerIsRed());
            refresh();
            statusLabel.setText("已载入 " + applied + " 手棋谱");
            maybeStartAiTurn();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "载入失败：" + e.getMessage(), "载入棋谱",
                    JOptionPane.ERROR_MESSAGE);
        }
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

    private void applyEnginePath(String path) {
        aiPlayer.setEnginePath(path);
        hintPlayer.setEnginePath(path);
        engineSummary = path.isBlank() ? "内置引擎" : new File(path).getName();
        refresh();
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

        String engine = prefs.get(PREF_ENGINE, "");
        if (engine.isBlank()) {
            File local = new File("pikafish");
            if (local.isFile() && local.canExecute()) {
                engine = local.getAbsolutePath();
            }
        }
        if (!engine.isBlank()) {
            aiPlayer.setEnginePath(engine);
            hintPlayer.setEnginePath(engine);
            engineSummary = new File(engine).getName();
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

    private void shutdown() {
        aiWorker.shutdown();
        hintWorker.shutdown();
        aiPlayer.shutdown();
        hintPlayer.shutdown();
        sounds.dispose();
        prefs.putInt(PREF_WIDTH, Math.max(880, getWidth()));
        prefs.putInt(PREF_HEIGHT, Math.max(640, getHeight()));
        dispose();
        System.exit(0);
    }
}
