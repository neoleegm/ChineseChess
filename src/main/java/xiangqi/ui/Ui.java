package xiangqi.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * 界面小工具：统一字体与按钮外观，避免样式散落在各个面板里。
 */
final class Ui {

    private Ui() {
    }

    static Font font(int style, int size) {
        return new Font(Font.SANS_SERIF, style, size);
    }

    static JLabel label(String text, int style, int size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font(style, size));
        label.setForeground(color);
        return label;
    }

    static JLabel section(String text, Theme theme) {
        JLabel label = label(text, Font.BOLD, 12, theme.panelMuted);
        label.setBorder(new EmptyBorder(8, 0, 3, 0));
        return label;
    }

    static JButton button(String text, Theme theme, Runnable action) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (!isEnabled()) {
                    g2.setColor(theme.buttonBg);
                } else if (getModel().isPressed()) {
                    g2.setColor(theme.buttonPressed);
                } else if (getModel().isRollover()) {
                    g2.setColor(theme.buttonHover);
                } else {
                    g2.setColor(theme.buttonBg);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 9, 9);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        button.setFont(font(Font.BOLD, 13));
        button.setForeground(theme.buttonText);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createEmptyBorder(7, 6, 7, 6));
        if (action != null) {
            button.addActionListener(e -> action.run());
        }
        return button;
    }

    static void applyTheme(JComponent component, Theme theme) {
        component.setBackground(theme.panelBg);
        component.setForeground(theme.panelText);
    }

    static void installGlobalDefaults() {
        UIManager.put("Panel.background", Theme.WOOD.panelBg);
        UIManager.put("OptionPane.messageFont", font(Font.PLAIN, 13));
        UIManager.put("OptionPane.buttonFont", font(Font.PLAIN, 13));
    }
}
