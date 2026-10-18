package xiangqi.ui;

import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 分段选择控件，替代 {@code JComboBox}。
 *
 * <p>原生下拉框在 macOS 上是系统绘制的，无法跟随主题换色（暗色主题下会出现
 * 白底白字）。这里用一排互斥按钮自绘，既能完全跟随主题，也省掉「展开再选」
 * 的一次点击。</p>
 */
final class SegmentedControl extends JPanel {

    private final List<JToggleButton> buttons = new ArrayList<>();
    private Theme theme;
    private int selected;
    private boolean updating;
    private IntConsumer listener;

    SegmentedControl(String[] labels, Theme theme) {
        this.theme = theme;
        setLayout(new GridLayout(1, labels.length, 4, 0));
        setOpaque(false);
        setAlignmentX(LEFT_ALIGNMENT);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        setPreferredSize(new Dimension(0, 28));

        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            JToggleButton button = new JToggleButton(labels[i]) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    Color fill;
                    if (isSelected()) {
                        fill = SegmentedControl.this.theme.accent;
                    } else if (getModel().isRollover()) {
                        fill = SegmentedControl.this.theme.buttonHover;
                    } else {
                        fill = SegmentedControl.this.theme.buttonBg;
                    }
                    g2.setColor(fill);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            button.setFont(Ui.font(Font.PLAIN, 12));
            button.setForeground(theme.buttonText);
            button.setFocusPainted(false);
            button.setBorderPainted(false);
            button.setContentAreaFilled(false);
            button.setOpaque(false);
            // 系统 LAF 给按钮预留了较宽的立体边框内衬，会把中文挤成省略号；
            // 这里显式压掉内衬，文字才能真正居中显示完整。
            button.setMargin(new java.awt.Insets(0, 0, 0, 0));
            button.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 2, 2, 2));
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.addActionListener(e -> {
                if (!updating) {
                    selected = index;
                    refreshForegrounds();
                    if (listener != null) {
                        listener.accept(index);
                    }
                }
            });
            group.add(button);
            buttons.add(button);
            add(button);
        }
        buttons.get(0).setSelected(true);
        refreshForegrounds();
    }

    void setListener(IntConsumer listener) {
        this.listener = listener;
    }

    int selectedIndex() {
        return selected;
    }

    /** 程序化设置选中项，不触发回调。 */
    void setSelectedIndex(int index) {
        if (index < 0 || index >= buttons.size() || index == selected) {
            return;
        }
        updating = true;
        try {
            selected = index;
            buttons.get(index).setSelected(true);
            refreshForegrounds();
        } finally {
            updating = false;
        }
    }

    /** 整体启用/禁用（禁用时保留选中态，只是不响应点击）。 */
    void setEnabledAll(boolean enabled) {
        for (JToggleButton button : buttons) {
            button.setEnabled(enabled);
            button.setCursor(Cursor.getPredefinedCursor(
                    enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        }
    }

    void applyTheme(Theme theme) {
        this.theme = theme;
        refreshForegrounds();
        repaint();
    }

    private void refreshForegrounds() {
        for (int i = 0; i < buttons.size(); i++) {
            JToggleButton button = buttons.get(i);
            button.setForeground(i == selected ? Color.WHITE : theme.buttonText);
            button.repaint();
        }
    }
}
