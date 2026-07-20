import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * 中国象棋游戏主类
 * 主窗口：左侧游戏设置，中间棋盘，右侧对局记录（着法与被吃子）
 */
public class ChineseChessGame extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final String PREF_PIKAFISH_PATH = "pikafishEnginePath";
    private static final String PREF_MODE = "gameMode";
    private static final String PREF_DIFFICULTY = "difficulty";
    private static final String PREF_SIDE = "playerSide";
    private static final String PREF_SOUND = "soundEnabled";

    // 颜色主题
    private static final Color WOOD_DARK = new Color(139, 90, 43);
    private static final Color WOOD_LIGHT = new Color(222, 184, 135);
    private static final Color BUTTON_BG = new Color(210, 180, 140);
    private static final Color BUTTON_HOVER = new Color(230, 200, 160);
    private static final Color PANEL_BG = new Color(245, 222, 179);
    private static final Color RECORD_BG = new Color(252, 240, 210);

    // 各棋子初始数量（按 ChessPiece.Type 的 ordinal：将帅、仕士、相象、马、车、炮、兵卒）
    private static final int[] INITIAL_PIECE_COUNTS = {1, 2, 2, 2, 2, 2, 5};

    private ChessBoard board;
    private ChessPanel chessPanel;
    private JLabel statusLabel;
    private Preferences preferences;

    // 控制组件
    private JComboBox<String> modeCombo;
    private JComboBox<String> difficultyCombo;
    private JComboBox<String> sideCombo;
    private JCheckBox soundCheckBox;
    private JButton engineButton;
    private JLabel engineStatusLabel;
    private JButton hintButton;
    private JLabel redCapturedLabel;
    private JLabel blackCapturedLabel;
    private DefaultListModel<String> moveListModel;
    private JList<String> moveList;

    // 恢复组合框选中项时抑制监听器，避免重复触发重置
    private boolean updatingCombos = false;

    public ChineseChessGame() {
        setTitle("中国象棋");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        // 初始化棋盘
        board = new ChessBoard();
        preferences = Preferences.userNodeForPackage(ChineseChessGame.class);

        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(WOOD_LIGHT);
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 创建状态栏
        statusLabel = new JLabel("红方走棋", SwingConstants.CENTER);
        statusLabel.setFont(ChessPanel.uiFont(Font.BOLD, 16));
        statusLabel.setForeground(Color.DARK_GRAY);
        statusLabel.setPreferredSize(new Dimension(0, 30));
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        // 创建棋盘面板
        chessPanel = new ChessPanel(board, statusLabel);
        String savedEnginePath = preferences.get(PREF_PIKAFISH_PATH, "");
        if (!savedEnginePath.isBlank()) {
            chessPanel.setPikafishEnginePath(savedEnginePath);
        } else {
            // 自动检测当前目录下的 pikafish 引擎
            File localEngine = new File("pikafish");
            if (localEngine.exists() && localEngine.canExecute()) {
                String absolutePath = localEngine.getAbsolutePath();
                chessPanel.setPikafishEnginePath(absolutePath);
                preferences.put(PREF_PIKAFISH_PATH, absolutePath);
            }
        }
        mainPanel.add(chessPanel, BorderLayout.CENTER);

        // 左侧：游戏设置；右侧：对局记录
        mainPanel.add(createSidebar(), BorderLayout.WEST);
        mainPanel.add(createRecordPanel(), BorderLayout.EAST);

        // 走子/悔棋/重置时刷新着法列表与被吃子展示
        chessPanel.setGameEventListener(this::refreshMoveDisplays);

        // 恢复上次设置（直接同步到面板，不触发重置确认）
        restoreSettings();

        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(null);

        // 键盘快捷键
        setupKeyboardShortcuts();

        // 退出时销毁外部引擎进程
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                chessPanel.shutdownAI();
            }
        });
    }

    /**
     * 左侧边栏：游戏设置与操作按钮
     */
    private JPanel createSidebar() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(PANEL_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(WOOD_DARK, 2),
            new EmptyBorder(15, 15, 15, 15)
        ));
        panel.setPreferredSize(new Dimension(210, 0));

        // 标题
        JLabel titleLabel = new JLabel("游戏设置");
        titleLabel.setFont(ChessPanel.uiFont(Font.BOLD, 18));
        titleLabel.setForeground(WOOD_DARK);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(15));

        // 游戏模式
        panel.add(createLabel("游戏模式:"));
        modeCombo = new JComboBox<>(new String[]{"人机对战", "人人对战"});
        modeCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        modeCombo.setFont(ChessPanel.uiFont(Font.PLAIN, 14));
        modeCombo.addActionListener(e -> {
            if (updatingCombos) return;
            if (!confirmResetIfInProgress()) {
                revertCombo(modeCombo, chessPanel.getGameMode() == ChessPanel.GameMode.PVE ? 0 : 1);
                return;
            }
            String selected = (String) modeCombo.getSelectedItem();
            chessPanel.setGameMode("人机对战".equals(selected) ?
                ChessPanel.GameMode.PVE : ChessPanel.GameMode.PVP);
            preferences.putInt(PREF_MODE, modeCombo.getSelectedIndex());
            updateControlState();
        });
        panel.add(modeCombo);
        panel.add(Box.createVerticalStrut(12));

        // AI 难度
        panel.add(createLabel("AI 难度:"));
        difficultyCombo = new JComboBox<>(new String[]{"简单", "中等", "困难"});
        difficultyCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        difficultyCombo.setFont(ChessPanel.uiFont(Font.PLAIN, 14));
        difficultyCombo.addActionListener(e -> {
            if (updatingCombos) return;
            if (!confirmResetIfInProgress()) {
                revertCombo(difficultyCombo, chessPanel.getDifficulty().ordinal());
                return;
            }
            String selected = (String) difficultyCombo.getSelectedItem();
            ChessAI.Difficulty diff = switch (selected) {
                case "简单" -> ChessAI.Difficulty.EASY;
                case "困难" -> ChessAI.Difficulty.HARD;
                default -> ChessAI.Difficulty.MEDIUM;
            };
            chessPanel.setDifficulty(diff);
            preferences.putInt(PREF_DIFFICULTY, difficultyCombo.getSelectedIndex());
        });
        panel.add(difficultyCombo);
        panel.add(Box.createVerticalStrut(12));

        // 外部引擎
        panel.add(createLabel("困难模式引擎:"));
        engineButton = createButton("选择 Pikafish 引擎", e -> choosePikafishEngine());
        panel.add(engineButton);
        panel.add(Box.createVerticalStrut(6));
        engineStatusLabel = new JLabel();
        engineStatusLabel.setFont(ChessPanel.uiFont(Font.PLAIN, 12));
        engineStatusLabel.setForeground(WOOD_DARK);
        engineStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        engineStatusLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        panel.add(engineStatusLabel);
        updateEngineStatusLabel();
        panel.add(Box.createVerticalStrut(12));

        // 执棋方
        panel.add(createLabel("执棋方:"));
        sideCombo = new JComboBox<>(new String[]{"红方（先手）", "黑方（后手）"});
        sideCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        sideCombo.setFont(ChessPanel.uiFont(Font.PLAIN, 14));
        sideCombo.addActionListener(e -> {
            if (updatingCombos) return;
            if (!confirmResetIfInProgress()) {
                revertCombo(sideCombo, chessPanel.isPlayerRed() ? 0 : 1);
                return;
            }
            boolean isRed = sideCombo.getSelectedIndex() == 0;
            chessPanel.setPlayerSide(isRed);
            preferences.putInt(PREF_SIDE, sideCombo.getSelectedIndex());
        });
        panel.add(sideCombo);
        panel.add(Box.createVerticalStrut(12));

        // 音效开关
        soundCheckBox = new JCheckBox("开启音效", true);
        soundCheckBox.setFont(ChessPanel.uiFont(Font.PLAIN, 14));
        soundCheckBox.setBackground(PANEL_BG);
        soundCheckBox.setForeground(WOOD_DARK);
        soundCheckBox.addActionListener(e -> {
            chessPanel.setSoundEnabled(soundCheckBox.isSelected());
            preferences.putBoolean(PREF_SOUND, soundCheckBox.isSelected());
        });
        panel.add(soundCheckBox);
        panel.add(Box.createVerticalStrut(16));

        // 按钮
        hintButton = createButton("提示", e -> chessPanel.requestHint());
        panel.add(hintButton);
        panel.add(Box.createVerticalStrut(10));
        panel.add(createButton("重新开始", e -> {
            if (confirmResetIfInProgress()) chessPanel.reset();
        }));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createButton("悔棋", e -> chessPanel.undo()));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createButton("游戏规则", e -> showRules()));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createButton("关于", e -> showAbout()));

        panel.add(Box.createVerticalGlue());
        return panel;
    }

    /**
     * 右侧栏：对局记录（黑方被吃在顶部，着法列表占满中部，红方被吃在底部）
     */
    private JPanel createRecordPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBackground(PANEL_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(WOOD_DARK, 2),
            new EmptyBorder(15, 15, 15, 15)
        ));
        panel.setPreferredSize(new Dimension(210, 0));

        // 顶部：标题 + 黑方被吃（靠近黑方一侧）
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
        northPanel.setBackground(PANEL_BG);
        JLabel titleLabel = new JLabel("对局记录");
        titleLabel.setFont(ChessPanel.uiFont(Font.BOLD, 18));
        titleLabel.setForeground(WOOD_DARK);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        northPanel.add(titleLabel);
        northPanel.add(Box.createVerticalStrut(12));
        northPanel.add(createLabel("黑方被吃:"));
        blackCapturedLabel = createCapturedLabel();
        northPanel.add(blackCapturedLabel);
        panel.add(northPanel, BorderLayout.NORTH);

        // 中部：着法列表，占满剩余全部高度
        JPanel centerPanel = new JPanel(new BorderLayout(4, 4));
        centerPanel.setBackground(PANEL_BG);
        centerPanel.add(createLabel("着法:"), BorderLayout.NORTH);
        moveListModel = new DefaultListModel<>();
        moveList = new JList<>(moveListModel);
        moveList.setFont(ChessPanel.uiFont(Font.PLAIN, 14));
        moveList.setFixedCellHeight(26);
        moveList.setBackground(RECORD_BG);
        moveList.setForeground(WOOD_DARK);
        moveList.setSelectionBackground(new Color(120, 170, 110));
        moveList.setSelectionForeground(Color.WHITE);
        JScrollPane moveScroll = new JScrollPane(moveList);
        centerPanel.add(moveScroll, BorderLayout.CENTER);
        panel.add(centerPanel, BorderLayout.CENTER);

        // 底部：红方被吃（靠近红方一侧）
        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
        southPanel.setBackground(PANEL_BG);
        southPanel.add(createLabel("红方被吃:"));
        redCapturedLabel = createCapturedLabel();
        southPanel.add(redCapturedLabel);
        panel.add(southPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JLabel createCapturedLabel() {
        JLabel label = new JLabel("无");
        label.setFont(ChessPanel.uiFont(Font.PLAIN, 14));
        label.setForeground(WOOD_DARK);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(ChessPanel.uiFont(Font.BOLD, 14));
        label.setForeground(WOOD_DARK);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JButton createButton(String text, java.util.function.Consumer<java.awt.event.ActionEvent> action) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                if (getModel().isPressed()) {
                    g.setColor(BUTTON_BG.darker());
                } else if (getModel().isRollover()) {
                    g.setColor(BUTTON_HOVER);
                } else {
                    g.setColor(BUTTON_BG);
                }
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                super.paintComponent(g);
            }
        };
        button.setFont(ChessPanel.uiFont(Font.BOLD, 14));
        button.setForeground(WOOD_DARK);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addActionListener(action::accept);
        return button;
    }

    private void updateControlState() {
        boolean isPVE = modeCombo.getSelectedIndex() == 0;
        difficultyCombo.setEnabled(isPVE);
        sideCombo.setEnabled(isPVE);
        engineButton.setEnabled(isPVE);
        hintButton.setEnabled(isPVE);
    }

    /**
     * 恢复上次的模式/难度/执棋方/音效设置
     */
    private void restoreSettings() {
        int savedMode = preferences.getInt(PREF_MODE, 0);
        int savedDifficulty = preferences.getInt(PREF_DIFFICULTY, 1);
        int savedSide = preferences.getInt(PREF_SIDE, 0);
        boolean savedSound = preferences.getBoolean(PREF_SOUND, true);

        updatingCombos = true;
        modeCombo.setSelectedIndex(savedMode);
        difficultyCombo.setSelectedIndex(savedDifficulty);
        sideCombo.setSelectedIndex(savedSide);
        soundCheckBox.setSelected(savedSound);
        updatingCombos = false;

        chessPanel.setGameMode(savedMode == 0 ? ChessPanel.GameMode.PVE : ChessPanel.GameMode.PVP);
        chessPanel.setDifficulty(ChessAI.Difficulty.values()[savedDifficulty]);
        chessPanel.setPlayerSide(savedSide == 0);
        chessPanel.setSoundEnabled(savedSound);
        updateControlState();
        refreshMoveDisplays();
    }

    /**
     * 对局进行中时弹出确认，返回是否允许放弃当前对局
     */
    private boolean confirmResetIfInProgress() {
        if (!chessPanel.isGameInProgress()) return true;
        int choice = JOptionPane.showConfirmDialog(this,
            "对局尚未结束，确定要放弃当前对局吗？",
            "重新开始",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    /**
     * 用户取消重置时把组合框选项改回当前实际状态
     */
    private void revertCombo(JComboBox<String> combo, int index) {
        updatingCombos = true;
        combo.setSelectedIndex(index);
        updatingCombos = false;
    }

    /**
     * 刷新着法列表与被吃子展示（走子/悔棋/重置后由棋盘面板回调）
     */
    private void refreshMoveDisplays() {
        redCapturedLabel.setText(capturedText(true));
        blackCapturedLabel.setText(capturedText(false));

        moveListModel.clear();
        List<String> notations = chessPanel.getMoveNotations();
        for (int i = 0; i < notations.size(); i += 2) {
            String row = (i / 2 + 1) + ". " + notations.get(i);
            if (i + 1 < notations.size()) {
                row += "  " + notations.get(i + 1);
            }
            moveListModel.addElement(row);
        }
        if (!notations.isEmpty()) {
            int last = moveListModel.getSize() - 1;
            moveList.setSelectedIndex(last);
            moveList.ensureIndexIsVisible(last);
        }
    }

    /**
     * 根据棋盘现有棋子推算某方被吃的棋子
     */
    private String capturedText(boolean red) {
        int[] counts = new int[INITIAL_PIECE_COUNTS.length];
        for (ChessPiece p : board.getPieces()) {
            if (p.isRed() == red) {
                counts[p.getType().ordinal()]++;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (ChessPiece.Type type : ChessPiece.Type.values()) {
            int missing = INITIAL_PIECE_COUNTS[type.ordinal()] - counts[type.ordinal()];
            for (int i = 0; i < missing; i++) {
                sb.append(type.getName(red));
            }
        }
        return sb.length() == 0 ? "无" : sb.toString();
    }

    private void choosePikafishEngine() {
        JFileChooser chooser = new JFileChooser();
        String currentPath = chessPanel.getPikafishEnginePath();
        if (!currentPath.isBlank()) {
            chooser.setSelectedFile(new File(currentPath));
        }
        chooser.setDialogTitle("选择 Pikafish 可执行文件");

        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selectedFile = chooser.getSelectedFile();
        String path = selectedFile.getAbsolutePath();
        // 校验可执行性，失败则不生效也不持久化
        if (!selectedFile.isFile() || !selectedFile.canExecute()) {
            JOptionPane.showMessageDialog(this,
                "所选文件不存在或不可执行：\n" + path,
                "Pikafish 引擎",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        preferences.put(PREF_PIKAFISH_PATH, path);
        chessPanel.setPikafishEnginePath(path);
        updateEngineStatusLabel();
        JOptionPane.showMessageDialog(this,
            "已选择 Pikafish 引擎：\n" + path + "\n\n困难难度会优先使用它，失败时自动回退内置 AI。",
            "Pikafish 引擎",
            JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateEngineStatusLabel() {
        if (engineStatusLabel == null) return;

        String path = chessPanel.getPikafishEnginePath();
        if (path.isBlank()) {
            engineStatusLabel.setText("<html>未配置<br>困难模式使用内置 AI</html>");
            engineStatusLabel.setToolTipText("未配置 Pikafish");
        } else {
            File file = new File(path);
            engineStatusLabel.setText("<html>已配置<br>" + file.getName() + "</html>");
            engineStatusLabel.setToolTipText(path);
        }
    }

    private void setupKeyboardShortcuts() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .addKeyEventDispatcher(e -> {
                // 只在主窗口激活、无修饰键、焦点不在文本组件时响应，
                // 避免模态对话框/文件选择器中误触快捷键
                if (e.getID() != KeyEvent.KEY_PRESSED || e.getModifiersEx() != 0) {
                    return false;
                }
                KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                if (kfm.getActiveWindow() != this) {
                    return false;
                }
                Component focusOwner = kfm.getFocusOwner();
                if (focusOwner instanceof javax.swing.text.JTextComponent) {
                    return false;
                }
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_U -> chessPanel.undo();
                    case KeyEvent.VK_R -> {
                        if (confirmResetIfInProgress()) chessPanel.reset();
                    }
                    case KeyEvent.VK_H -> chessPanel.requestHint();
                    case KeyEvent.VK_Q -> quitGame();
                }
                return false;
            });
    }

    private void quitGame() {
        chessPanel.shutdownAI();
        System.exit(0);
    }

    private void showRules() {
        String rules = """
            中国象棋规则

            【棋子走法】
            帅/将：九宫内，横竖一格
            仕/士：九宫内，斜线一格
            相/象：走田字，不能过河，不能被塞象眼
            傌/马：走日字，不能被蹩马腿
            俥/车：横竖直线，不能越子
            炮/砲：横竖直线，吃子需隔一子
            兵/卒：过河前只能前进，过河后可横移

            【特殊规则】
            楚河汉界：相/象、兵/卒不能越过
            九宫：帅/将、仕/士不能出宫
            将帅照面：双方将/帅不能同列无遮挡
            将军：将/帅不能被吃，被将军必须解将

            【胜负判定】
            将死对方或对方无棋可走（困毙）获胜
            同一局面第三次出现：一方步步将军判长将作负，否则和棋

            【快捷键】
            U - 悔棋
            R - 重新开始
            H - 提示（人机对战）
            Q - 退出游戏
            """;

        JTextArea textArea = new JTextArea(rules);
        textArea.setFont(ChessPanel.uiFont(Font.PLAIN, 14));
        textArea.setEditable(false);
        textArea.setBackground(PANEL_BG);
        textArea.setBorder(new EmptyBorder(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(400, 400));

        JOptionPane.showMessageDialog(this, scrollPane, "游戏规则",
            JOptionPane.INFORMATION_MESSAGE);
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this,
            """
            中国象棋 v3.1

            使用 Java Swing 开发
            支持人机对战和人人对战

            祝您游戏愉快！
            """,
            "关于",
            JOptionPane.INFORMATION_MESSAGE);
    }

    public static void main(String[] args) {
        // 设置系统外观
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // 使用默认外观
        }

        // 启动游戏
        SwingUtilities.invokeLater(() -> {
            ChineseChessGame game = new ChineseChessGame();
            game.setVisible(true);
        });
    }
}
