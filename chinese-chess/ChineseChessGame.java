import javax.swing.*;
import java.awt.*;

public class ChineseChessGame extends JFrame {
    private static final Color BG = new Color(245, 243, 240);
    private static final Color SIDEBAR = new Color(50, 50, 55);
    private static final Color TEXT = new Color(220, 220, 220);
    private static final Color ACCENT = new Color(180, 80, 60);
    
    private ChessPanel chessPanel;
    
    public ChineseChessGame() {
        setTitle("中国象棋");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        
        ChessBoard board = new ChessBoard();
        JLabel status = new JLabel("红方走棋", JLabel.CENTER);
        status.setFont(new Font("Microsoft YaHei", Font.BOLD, 18));
        status.setForeground(new Color(80, 60, 50));
        status.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 0));
        
        chessPanel = new ChessPanel(board, status);
        chessPanel.setOnGameOver(() -> JOptionPane.showMessageDialog(this, board.getWinner(), "游戏结束", JOptionPane.INFORMATION_MESSAGE));
        
        JPanel main = new JPanel(new BorderLayout(0, 0));
        main.setBackground(BG);
        main.add(createSidebar(board), BorderLayout.WEST);
        
        JPanel gameArea = new JPanel(new BorderLayout());
        gameArea.setOpaque(false);
        gameArea.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 10));
        gameArea.add(chessPanel, BorderLayout.CENTER);
        gameArea.add(status, BorderLayout.SOUTH);
        main.add(gameArea, BorderLayout.CENTER);
        
        add(main);
        pack();
        setLocationRelativeTo(null);
    }
    
    private JPanel createSidebar(ChessBoard board) {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(180, 0));
        sidebar.setBackground(SIDEBAR);
        
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(SIDEBAR);
        content.setBorder(BorderFactory.createEmptyBorder(20, 15, 20, 15));
        
        JLabel title = new JLabel("⚙ 游戏设置");
        title.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        title.setForeground(ACCENT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(20));
        
        String[][] options = {{"游戏模式", "人机对战", "人人对战"},
                             {"AI 难度", "简单", "中等", "困难"},
                             {"执棋方", "红方", "黑方"}};
        
        JComboBox<String>[] combos = new JComboBox[3];
        for (int i = 0; i < 3; i++) {
            content.add(createRow(options[i][0], combos[i] = createCombo(java.util.Arrays.copyOfRange(options[i], 1, options[i].length))));
            content.add(Box.createVerticalStrut(12));
        }
        
        combos[0].addActionListener(e -> {
            boolean isPVE = combos[0].getSelectedIndex() == 0;
            combos[1].setEnabled(isPVE);
            combos[2].setEnabled(isPVE);
            chessPanel.setGameMode(isPVE ? ChessPanel.GameMode.PVE : ChessPanel.GameMode.PVP);
        });
        combos[1].setSelectedIndex(1);
        combos[1].addActionListener(e -> chessPanel.setDifficulty(
            switch (combos[1].getSelectedIndex()) { case 0 -> ChessAI.Difficulty.EASY; case 2 -> ChessAI.Difficulty.HARD; default -> ChessAI.Difficulty.MEDIUM; }));
        combos[2].addActionListener(e -> chessPanel.setPlayerSide(combos[2].getSelectedIndex() == 0));
        
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(80, 80, 85));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        content.add(sep);
        content.add(Box.createVerticalStrut(15));
        
        JCheckBox sound = new JCheckBox("启用音效", true);
        sound.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        sound.setForeground(TEXT);
        sound.setBackground(SIDEBAR);
        sound.setFocusPainted(false);
        sound.setAlignmentX(Component.LEFT_ALIGNMENT);
        sound.addActionListener(e -> chessPanel.setSoundEnabled(sound.isSelected()));
        content.add(sound);
        
        sidebar.add(content, BorderLayout.NORTH);
        
        JPanel buttons = new JPanel(new GridLayout(4, 1, 0, 8));
        buttons.setBackground(SIDEBAR);
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 15, 20, 15));
        buttons.add(createBtn("🔄 新游戏", () -> chessPanel.reset()));
        buttons.add(createBtn("↩️ 悔棋", () -> chessPanel.undo()));
        buttons.add(createBtn("📖 规则", () -> showRules()));
        buttons.add(createBtn("✕ 退出", () -> System.exit(0)));
        sidebar.add(buttons, BorderLayout.SOUTH);
        
        return sidebar;
    }
    
    private JPanel createRow(String label, JComponent c) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setBackground(SIDEBAR);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel l = new JLabel(label);
        l.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        l.setForeground(TEXT);
        l.setPreferredSize(new Dimension(60, 25));
        c.setPreferredSize(new Dimension(90, 28));
        row.add(l, BorderLayout.WEST);
        row.add(c, BorderLayout.CENTER);
        return row;
    }
    
    private JComboBox<String> createCombo(String[] items) {
        JComboBox<String> c = new JComboBox<>(items);
        c.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        c.setBackground(new Color(70, 70, 75));
        c.setForeground(TEXT);
        c.setFocusable(false);
        c.setBorder(BorderFactory.createLineBorder(new Color(90, 90, 95)));
        c.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
                setBackground(isSelected ? ACCENT : new Color(70, 70, 75));
                setForeground(isSelected ? Color.WHITE : TEXT);
                return this;
            }
        });
        return c;
    }
    
    private JButton createBtn(String text, Runnable action) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isPressed() ? new Color(60, 60, 65) : getModel().isRollover() ? new Color(70, 70, 75) : new Color(65, 65, 70));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(100, 100, 105));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.setColor(TEXT);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2, (getHeight() + fm.getAscent()) / 2 - 2);
            }
        };
        b.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        b.setPreferredSize(new Dimension(0, 36));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> action.run());
        return b;
    }
    
    private void showRules() {
        JTextArea area = new JTextArea("""
            【棋子走法】
            帅/将：九宫内一格一格移动
            仕/士：九宫内斜线走一格
            相/象：走"田"字，不能过河
            马/傌：走"日"字，马腿不能被蹩
            车/俥：直线走，不能越子
            炮/砲：直线走，吃子需隔一子
            兵/卒：向前走，过河可横走
            【胜负判定】吃掉对方将/帅获胜！
            【操作】鼠标滚轮缩放棋盘""");
        area.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        area.setEditable(false);
        area.setBackground(Color.WHITE);
        area.setLineWrap(true);
        JScrollPane sp = new JScrollPane(area);
        sp.setPreferredSize(new Dimension(320, 280));
        JOptionPane.showMessageDialog(this, sp, "游戏规则", JOptionPane.PLAIN_MESSAGE);
    }
    
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new ChineseChessGame().setVisible(true));
    }
}
