/*
 * Decompiled with CFR 0.152.
 */
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class ChineseChessGame
extends JFrame {
    private static final Color BG = new Color(240, 210, 160);
    private static final Color SIDEBAR = new Color(230, 200, 150);
    private static final Color TEXT = new Color(80, 50, 30);
    private static final Color ACCENT = new Color(180, 80, 50);
    private static final Color WOOD_DEEP = new Color(160, 130, 90);
    private static final Color BUTTON_BG = new Color(210, 180, 130);
    private static final Color BUTTON_HOVER = new Color(220, 190, 140);
    private static final Color BUTTON_PRESS = new Color(190, 160, 110);
    private ChessPanel chessPanel;

    public ChineseChessGame() {
        this.setTitle("\u4e2d\u56fd\u8c61\u68cb");
        this.setDefaultCloseOperation(3);
        this.setResizable(false);
        ChessBoard chessBoard = new ChessBoard();
        JLabel jLabel = new JLabel("\u7ea2\u65b9\u8d70\u68cb", 0);
        jLabel.setFont(new Font("Microsoft YaHei", 1, 18));
        jLabel.setForeground(TEXT);
        jLabel.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 0));
        this.chessPanel = new ChessPanel(chessBoard, jLabel);
        this.chessPanel.setOnGameOver(() -> JOptionPane.showMessageDialog(this, chessBoard.getWinner(), "\u6e38\u620f\u7ed3\u675f", 1));
        JPanel jPanel = new JPanel(new BorderLayout(0, 0));
        jPanel.setBackground(BG);
        jPanel.add((Component)this.createSidebar(chessBoard), "West");
        JPanel jPanel2 = new JPanel(new BorderLayout());
        jPanel2.setOpaque(false);
        jPanel2.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 10));
        jPanel2.add((Component)this.chessPanel, "Center");
        jPanel2.add((Component)jLabel, "South");
        jPanel.add((Component)jPanel2, "Center");
        this.add(jPanel);
        this.pack();
        this.setLocationRelativeTo(null);
    }

    private JPanel createSidebar(ChessBoard chessBoard) {
        JComponent jComponent;
        JPanel jPanel = new JPanel(new BorderLayout());
        jPanel.setPreferredSize(new Dimension(220, 0));
        jPanel.setBackground(SIDEBAR);
        jPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 0, 2, WOOD_DEEP), BorderFactory.createEmptyBorder(20, 12, 20, 12)));
        JPanel jPanel2 = new JPanel();
        jPanel2.setLayout(new BoxLayout(jPanel2, 1));
        jPanel2.setBackground(SIDEBAR);
        JLabel jLabel = new JLabel("\u2699 \u6e38\u620f\u8bbe\u7f6e");
        jLabel.setFont(new Font("Microsoft YaHei", 1, 18));
        jLabel.setForeground(ACCENT);
        jLabel.setAlignmentX(0.0f);
        jPanel2.add(jLabel);
        jPanel2.add(Box.createVerticalStrut(25));
        String[][] stringArrayArray = new String[][]{{"\u6e38\u620f\u6a21\u5f0f", "\u4eba\u673a\u5bf9\u6218", "\u4eba\u4eba\u5bf9\u6218"}, {"AI \u96be\u5ea6", "\u7b80\u5355", "\u4e2d\u7b49", "\u56f0\u96be"}, {"\u6267\u68cb\u65b9", "\u7ea2\u65b9", "\u9ed1\u65b9"}};
        ArrayList<JComboBox<String>> arrayList = new ArrayList<JComboBox<String>>();
        for (int i = 0; i < 3; ++i) {
            jComponent = this.createCombo(Arrays.copyOfRange(stringArrayArray[i], 1, stringArrayArray[i].length));
            arrayList.add((JComboBox<String>)jComponent);
            jPanel2.add(this.createRow(stringArrayArray[i][0], jComponent));
            jPanel2.add(Box.createVerticalStrut(15));
        }
        ((JComboBox)arrayList.get(0)).addActionListener(actionEvent -> {
            boolean bl = ((JComboBox)arrayList.get(0)).getSelectedIndex() == 0;
            ((JComboBox)arrayList.get(1)).setEnabled(bl);
            ((JComboBox)arrayList.get(2)).setEnabled(bl);
            this.chessPanel.setGameMode(bl ? ChessPanel.GameMode.PVE : ChessPanel.GameMode.PVP);
        });
        ((JComboBox)arrayList.get(1)).setSelectedIndex(1);
        ((JComboBox)arrayList.get(1)).addActionListener(actionEvent -> {
            int n = ((JComboBox)arrayList.get(1)).getSelectedIndex();
            ChessAI.Difficulty difficulty = switch (n) {
                case 0 -> ChessAI.Difficulty.EASY;
                case 2 -> ChessAI.Difficulty.HARD;
                default -> ChessAI.Difficulty.MEDIUM;
            };
            this.chessPanel.setDifficulty(difficulty);
        });
        ((JComboBox)arrayList.get(2)).addActionListener(actionEvent -> this.chessPanel.setPlayerSide(((JComboBox)arrayList.get(2)).getSelectedIndex() == 0));
        JSeparator jSeparator = new JSeparator();
        jSeparator.setForeground(WOOD_DEEP);
        jSeparator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        jPanel2.add(jSeparator);
        jPanel2.add(Box.createVerticalStrut(20));
        jComponent = new JCheckBox("\u542f\u7528\u97f3\u6548", true);
        jComponent.setFont(new Font("Microsoft YaHei", 0, 14));
        jComponent.setForeground(TEXT);
        jComponent.setBackground(SIDEBAR);
        ((AbstractButton)jComponent).setFocusPainted(false);
        jComponent.setAlignmentX(0.0f);
        ((AbstractButton)jComponent).addActionListener(arg_0 -> this.lambda$createSidebar$3((JCheckBox)jComponent, arg_0));
        jPanel2.add(jComponent);
        jPanel.add((Component)jPanel2, "North");
        JPanel jPanel3 = new JPanel(new GridLayout(4, 1, 0, 10));
        jPanel3.setBackground(SIDEBAR);
        jPanel3.setBorder(BorderFactory.createEmptyBorder(20, 3, 0, 3));
        jPanel3.add(this.createBtn("\ud83d\udd04 \u65b0\u6e38\u620f", () -> this.chessPanel.reset()));
        jPanel3.add(this.createBtn("\u21a9\ufe0f \u6094\u68cb", () -> this.chessPanel.undo()));
        jPanel3.add(this.createBtn("\ud83d\udcd6 \u89c4\u5219", () -> this.showRules()));
        jPanel3.add(this.createBtn("\u2715 \u9000\u51fa", () -> System.exit(0)));
        jPanel.add((Component)jPanel3, "South");
        return jPanel;
    }

    private JPanel createRow(String string, JComponent jComponent) {
        JPanel jPanel = new JPanel(new BorderLayout(8, 0));
        jPanel.setBackground(SIDEBAR);
        jPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        jPanel.setAlignmentX(0.0f);
        JLabel jLabel = new JLabel(string);
        jLabel.setFont(new Font("Microsoft YaHei", 0, 13));
        jLabel.setForeground(TEXT);
        jLabel.setPreferredSize(new Dimension(60, 30));
        jComponent.setPreferredSize(new Dimension(125, 28));
        jPanel.add((Component)jLabel, "West");
        jPanel.add((Component)jComponent, "Center");
        return jPanel;
    }

    private JComboBox<String> createCombo(String[] stringArray) {
        JComboBox<String> jComboBox = new JComboBox<String>(stringArray);
        jComboBox.setFont(new Font("Microsoft YaHei", 0, 12));
        jComboBox.setBackground(Color.WHITE);
        jComboBox.setForeground(TEXT);
        jComboBox.setFocusable(false);
        return jComboBox;
    }

    private JButton createBtn(String string, Runnable runnable) {
        JButton jButton = new JButton(this, string){
            final /* synthetic */ ChineseChessGame this$0;
            {
                ChineseChessGame chineseChessGame2 = chineseChessGame;
                Objects.requireNonNull(chineseChessGame2);
                this.this$0 = chineseChessGame2;
                super(string);
            }

            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D graphics2D = (Graphics2D)graphics;
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (this.getModel().isPressed()) {
                    graphics2D.setColor(BUTTON_PRESS);
                } else if (this.getModel().isRollover()) {
                    graphics2D.setColor(BUTTON_HOVER);
                } else {
                    graphics2D.setColor(BUTTON_BG);
                }
                graphics2D.fillRoundRect(0, 0, this.getWidth(), this.getHeight(), 10, 10);
                graphics2D.setColor(WOOD_DEEP);
                graphics2D.drawRoundRect(0, 0, this.getWidth() - 1, this.getHeight() - 1, 10, 10);
                graphics2D.setColor(TEXT);
                graphics2D.setFont(this.getFont());
                FontMetrics fontMetrics = graphics2D.getFontMetrics();
                int n = (this.getWidth() - fontMetrics.stringWidth(this.getText())) / 2;
                int n2 = (this.getHeight() + fontMetrics.getAscent()) / 2 - 3;
                graphics2D.drawString(this.getText(), n, n2);
            }
        };
        jButton.setFont(new Font("Microsoft YaHei", 0, 14));
        jButton.setPreferredSize(new Dimension(0, 40));
        jButton.setFocusPainted(false);
        jButton.setBorderPainted(false);
        jButton.setContentAreaFilled(false);
        jButton.setCursor(new Cursor(12));
        jButton.addActionListener(actionEvent -> runnable.run());
        return jButton;
    }

    private void showRules() {
        JTextArea jTextArea = new JTextArea("\u3010\u68cb\u5b50\u8d70\u6cd5\u3011\n\u5e05/\u5c06\uff1a\u4e5d\u5bab\u5185\u4e00\u683c\u4e00\u683c\u79fb\u52a8\n\u4ed5/\u58eb\uff1a\u4e5d\u5bab\u5185\u659c\u7ebf\u8d70\u4e00\u683c\n\u76f8/\u8c61\uff1a\u8d70\"\u7530\"\u5b57\uff0c\u4e0d\u80fd\u8fc7\u6cb3\n\u9a6c/\u508c\uff1a\u8d70\"\u65e5\"\u5b57\uff0c\u9a6c\u817f\u4e0d\u80fd\u88ab\u8e69\n\u8f66/\u4fe5\uff1a\u76f4\u7ebf\u8d70\uff0c\u4e0d\u80fd\u8d8a\u5b50\n\u70ae/\u7832\uff1a\u76f4\u7ebf\u8d70\uff0c\u5403\u5b50\u9700\u9694\u4e00\u5b50\n\u5175/\u5352\uff1a\u5411\u524d\u8d70\uff0c\u8fc7\u6cb3\u53ef\u6a2a\u8d70\n\n\u3010\u80dc\u8d1f\u5224\u5b9a\u3011\u5403\u6389\u5bf9\u65b9\u5c06/\u5e05\u83b7\u80dc\uff01\n\n\u3010\u64cd\u4f5c\u3011\u9f20\u6807\u70b9\u51fb\u9009\u62e9\u68cb\u5b50\uff0c\u518d\u70b9\u51fb\u76ee\u6807\u4f4d\u7f6e\u79fb\u52a8");
        jTextArea.setFont(new Font("Microsoft YaHei", 0, 14));
        jTextArea.setEditable(false);
        jTextArea.setBackground(BG);
        jTextArea.setForeground(TEXT);
        jTextArea.setLineWrap(true);
        jTextArea.setWrapStyleWord(true);
        jTextArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JScrollPane jScrollPane = new JScrollPane(jTextArea);
        jScrollPane.setPreferredSize(new Dimension(350, 300));
        jScrollPane.setBorder(BorderFactory.createLineBorder(WOOD_DEEP, 1));
        JOptionPane.showMessageDialog(this, jScrollPane, "\u6e38\u620f\u89c4\u5219", -1);
    }

    public static void main(String[] stringArray) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception exception) {
            // empty catch block
        }
        SwingUtilities.invokeLater(() -> new ChineseChessGame().setVisible(true));
    }

    private /* synthetic */ void lambda$createSidebar$3(JCheckBox jCheckBox, ActionEvent actionEvent) {
        this.chessPanel.setSoundEnabled(jCheckBox.isSelected());
    }
}
