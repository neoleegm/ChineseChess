import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.prefs.Preferences;

/**
 * 中国象棋游戏主类
 * 主窗口和侧边栏控制面板
 */
public class ChineseChessGame extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final String PREF_PIKAFISH_PATH = "pikafishEnginePath";
    
    // 颜色主题
    private static final Color WOOD_DARK = new Color(139, 90, 43);
    private static final Color WOOD_LIGHT = new Color(222, 184, 135);
    private static final Color BUTTON_BG = new Color(210, 180, 140);
    private static final Color BUTTON_HOVER = new Color(230, 200, 160);
    private static final Color PANEL_BG = new Color(245, 222, 179);
    
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
        statusLabel.setFont(new Font("宋体", Font.BOLD, 16));
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
        
        // 创建侧边栏
        JPanel sidebar = createSidebar();
        mainPanel.add(sidebar, BorderLayout.WEST);
        
        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(null);
        
        // 键盘快捷键
        setupKeyboardShortcuts();
    }
    
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
        titleLabel.setFont(new Font("宋体", Font.BOLD, 18));
        titleLabel.setForeground(WOOD_DARK);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(20));
        
        // 游戏模式
        panel.add(createLabel("游戏模式:"));
        modeCombo = new JComboBox<>(new String[]{"人机对战", "人人对战"});
        modeCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        modeCombo.setFont(new Font("宋体", Font.PLAIN, 14));
        modeCombo.addActionListener(e -> {
            String selected = (String) modeCombo.getSelectedItem();
            chessPanel.setGameMode("人机对战".equals(selected) ? 
                ChessPanel.GameMode.PVE : ChessPanel.GameMode.PVP);
            updateControlState();
        });
        panel.add(modeCombo);
        panel.add(Box.createVerticalStrut(15));
        
        // AI 难度
        panel.add(createLabel("AI 难度:"));
        difficultyCombo = new JComboBox<>(new String[]{"简单", "中等", "困难"});
        difficultyCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        difficultyCombo.setFont(new Font("宋体", Font.PLAIN, 14));
        difficultyCombo.addActionListener(e -> {
            String selected = (String) difficultyCombo.getSelectedItem();
            ChessAI.Difficulty diff = switch (selected) {
                case "简单" -> ChessAI.Difficulty.EASY;
                case "困难" -> ChessAI.Difficulty.HARD;
                default -> ChessAI.Difficulty.MEDIUM;
            };
            chessPanel.setDifficulty(diff);
        });
        panel.add(difficultyCombo);
        panel.add(Box.createVerticalStrut(15));

        // 外部引擎
        panel.add(createLabel("困难模式引擎:"));
        engineButton = createButton("选择 Pikafish 引擎", e -> choosePikafishEngine());
        panel.add(engineButton);
        panel.add(Box.createVerticalStrut(6));
        engineStatusLabel = new JLabel();
        engineStatusLabel.setFont(new Font("宋体", Font.PLAIN, 12));
        engineStatusLabel.setForeground(WOOD_DARK);
        engineStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        engineStatusLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        panel.add(engineStatusLabel);
        updateEngineStatusLabel();
        panel.add(Box.createVerticalStrut(15));
        
        // 执棋方
        panel.add(createLabel("执棋方:"));
        sideCombo = new JComboBox<>(new String[]{"红方（先手）", "黑方（后手）"});
        sideCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        sideCombo.setFont(new Font("宋体", Font.PLAIN, 14));
        sideCombo.addActionListener(e -> {
            boolean isRed = sideCombo.getSelectedIndex() == 0;
            chessPanel.setPlayerSide(isRed);
        });
        panel.add(sideCombo);
        panel.add(Box.createVerticalStrut(15));
        
        // 音效开关
        soundCheckBox = new JCheckBox("开启音效", true);
        soundCheckBox.setFont(new Font("宋体", Font.PLAIN, 14));
        soundCheckBox.setBackground(PANEL_BG);
        soundCheckBox.setForeground(WOOD_DARK);
        soundCheckBox.addActionListener(e -> {
            chessPanel.setSoundEnabled(soundCheckBox.isSelected());
        });
        panel.add(soundCheckBox);
        panel.add(Box.createVerticalStrut(25));
        
        // 按钮
        panel.add(createButton("重新开始", e -> chessPanel.reset()));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createButton("悔棋", e -> chessPanel.undo()));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createButton("游戏规则", e -> showRules()));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createButton("关于", e -> showAbout()));
        
        panel.add(Box.createVerticalGlue());
        return panel;
    }
    
    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("宋体", Font.BOLD, 14));
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
        button.setFont(new Font("宋体", Font.BOLD, 14));
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
                if (e.getID() == KeyEvent.KEY_PRESSED) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_U -> chessPanel.undo();
                        case KeyEvent.VK_R -> chessPanel.reset();
                        case KeyEvent.VK_Q -> System.exit(0);
                    }
                }
                return false;
            });
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
            吃掉对方将/帅获胜
            对方无合法走法（困毙）获胜
            
            【快捷键】
            U - 悔棋
            R - 重新开始
            Q - 退出游戏
            """;
        
        JTextArea textArea = new JTextArea(rules);
        textArea.setFont(new Font("宋体", Font.PLAIN, 14));
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
            中国象棋 v2.0
            
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
