package xiangqi.ui;

import xiangqi.ai.Difficulty;
import xiangqi.game.GameSession;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;

/**
 * 左侧设置栏：对局模式、难度、执棋方、主题、音效与主要操作按钮。
 */
public final class SidebarPanel extends JPanel {

    /** 面板向主窗口回传的用户操作。 */
    public interface Actions {
        void newGame();

        void undo();

        void redo();

        void resign();

        void offerDraw();

        void hint();

        void chooseEngine();

        void modeChanged(GameSession.Mode mode);

        void difficultyChanged(Difficulty difficulty);

        void sideChanged(boolean red);

        void themeChanged(Theme theme);

        void soundChanged(boolean on);

        void coordinatesChanged(boolean on);
    }

    private static final String[] MODE_LABELS = {"人机对战", "人人对战"};
    private static final String[] SIDE_LABELS = {"执红", "执黑"};

    private final Actions actions;
    private Theme theme;

    private final SegmentedControl modeControl;
    private final SegmentedControl difficultyControl;
    private final SegmentedControl sideControl;
    private final SegmentedControl themeControl;

    private final JCheckBox soundCheck = new JCheckBox("开启音效", true);
    private final JCheckBox coordCheck = new JCheckBox("显示纵线编号", true);

    private final JButton hintButton;
    private final JButton undoButton;
    private final JButton redoButton;
    private final JButton newGameButton;
    private final JButton resignButton;
    private final JButton drawButton;
    private final JButton engineButton;
    private final JLabel engineStatus;
    private final JLabel titleLabel;

    private boolean updating;

    public SidebarPanel(Actions actions, Theme theme) {
        this.actions = actions;
        this.theme = theme;

        modeControl = new SegmentedControl(MODE_LABELS, theme);
        sideControl = new SegmentedControl(SIDE_LABELS, theme);

        String[] difficultyLabels = new String[Difficulty.values().length];
        for (Difficulty difficulty : Difficulty.values()) {
            difficultyLabels[difficulty.ordinal()] = difficulty.label();
        }
        difficultyControl = new SegmentedControl(difficultyLabels, theme);

        String[] themeLabels = new String[Theme.ALL.length];
        for (int i = 0; i < Theme.ALL.length; i++) {
            themeLabels[i] = Theme.ALL[i].name;
        }
        themeControl = new SegmentedControl(themeLabels, theme);

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setPreferredSize(new Dimension(208, 0));

        titleLabel = Ui.label("对局设置", Font.BOLD, 16, theme.panelText);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(titleLabel);
        add(Box.createVerticalStrut(2));

        add(sectionLabel("模式"));
        add(modeControl);
        add(Box.createVerticalStrut(7));

        add(sectionLabel("AI 难度"));
        add(difficultyControl);
        add(Box.createVerticalStrut(7));

        add(sectionLabel("执棋方"));
        add(sideControl);
        add(Box.createVerticalStrut(7));

        add(sectionLabel("棋盘主题"));
        add(themeControl);
        add(Box.createVerticalStrut(9));

        soundCheck.setFont(Ui.font(Font.PLAIN, 13));
        soundCheck.setFocusPainted(false);
        soundCheck.setOpaque(false);
        soundCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        soundCheck.addActionListener(e -> {
            if (!updating) {
                actions.soundChanged(soundCheck.isSelected());
            }
        });
        add(soundCheck);

        coordCheck.setFont(Ui.font(Font.PLAIN, 13));
        coordCheck.setFocusPainted(false);
        coordCheck.setOpaque(false);
        coordCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        coordCheck.addActionListener(e -> {
            if (!updating) {
                actions.coordinatesChanged(coordCheck.isSelected());
            }
        });
        add(coordCheck);
        add(Box.createVerticalStrut(4));

        add(sectionLabel("困难档外部引擎"));
        engineButton = Ui.button("选择 Pikafish…", theme, actions::chooseEngine);
        engineButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        engineButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        add(engineButton);
        engineStatus = Ui.label("未配置，使用内置引擎", Font.PLAIN, 11, theme.panelMuted);
        engineStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        engineStatus.setBorder(new EmptyBorder(4, 1, 0, 0));
        add(engineStatus);

        add(Box.createVerticalStrut(10));
        add(divider());
        add(Box.createVerticalStrut(10));

        hintButton = fullWidth(Ui.button("提示（H）", theme, actions::hint));
        add(hintButton);
        add(Box.createVerticalStrut(6));

        JPanel row = new JPanel(new GridLayout(1, 2, 6, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        undoButton = Ui.button("悔棋", theme, actions::undo);
        redoButton = Ui.button("重做", theme, actions::redo);
        row.add(undoButton);
        row.add(redoButton);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        add(row);
        add(Box.createVerticalStrut(6));

        newGameButton = fullWidth(Ui.button("重新开始（R）", theme, actions::newGame));
        add(newGameButton);
        add(Box.createVerticalStrut(6));

        JPanel row2 = new JPanel(new GridLayout(1, 2, 6, 0));
        row2.setOpaque(false);
        row2.setAlignmentX(Component.LEFT_ALIGNMENT);
        resignButton = Ui.button("认输", theme, actions::resign);
        drawButton = Ui.button("求和", theme, actions::offerDraw);
        row2.add(resignButton);
        row2.add(drawButton);
        row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        add(row2);

        add(Box.createVerticalGlue());

        modeControl.setListener(index -> {
            if (!updating) {
                actions.modeChanged(index == 0 ? GameSession.Mode.PVE : GameSession.Mode.PVP);
            }
        });
        difficultyControl.setListener(index -> {
            if (!updating) {
                actions.difficultyChanged(Difficulty.values()[index]);
            }
        });
        sideControl.setListener(index -> {
            if (!updating) {
                actions.sideChanged(index == 0);
            }
        });
        themeControl.setListener(index -> {
            if (!updating) {
                actions.themeChanged(Theme.ALL[index]);
            }
        });

        applyTheme(theme);
    }

    private JLabel sectionLabel(String text) {
        JLabel label = Ui.section(text, theme);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JButton fullWidth(JButton button) {
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        return button;
    }

    private JComponent divider() {
        JPanel line = new JPanel();
        line.setBackground(theme.panelBorder);
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        line.setPreferredSize(new Dimension(0, 1));
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        return line;
    }

    /** 根据会话状态刷新控件。 */
    public void refresh(GameSession session, boolean thinking, String enginePath) {
        updating = true;
        try {
            modeControl.setSelectedIndex(session.mode() == GameSession.Mode.PVE ? 0 : 1);
            difficultyControl.setSelectedIndex(session.difficulty().ordinal());
            sideControl.setSelectedIndex(session.playerIsRed() ? 0 : 1);
            for (int i = 0; i < Theme.ALL.length; i++) {
                if (Theme.ALL[i] == theme) {
                    themeControl.setSelectedIndex(i);
                    break;
                }
            }
        } finally {
            updating = false;
        }

        boolean pve = session.mode() == GameSession.Mode.PVE;
        difficultyControl.setEnabledAll(pve && !thinking);
        sideControl.setEnabledAll(pve && !thinking);
        engineButton.setEnabled(pve && !thinking);

        hintButton.setEnabled(pve && !thinking && !session.isOver() && session.isPlayerTurn());
        undoButton.setEnabled(session.canUndo() && !thinking);
        redoButton.setEnabled(session.canRedo() && !thinking);
        resignButton.setEnabled(!session.isOver());
        drawButton.setEnabled(!session.isOver());

        engineStatus.setText(enginePath == null || enginePath.isBlank()
                ? "未配置，使用内置引擎"
                : "已配置：" + new File(enginePath).getName());
        engineStatus.setToolTipText(enginePath);
    }

    public void setSoundSelected(boolean selected) {
        updating = true;
        soundCheck.setSelected(selected);
        updating = false;
    }

    public void setCoordinatesSelected(boolean selected) {
        updating = true;
        coordCheck.setSelected(selected);
        updating = false;
    }

    public void applyTheme(Theme theme) {
        this.theme = theme;
        setBackground(theme.panelBg);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, theme.panelBorder),
                new EmptyBorder(14, 14, 14, 14)));

        titleLabel.setForeground(theme.panelText);
        for (Component component : getComponents()) {
            if (component instanceof JLabel label && label != engineStatus && label != titleLabel) {
                label.setForeground(theme.panelMuted);
            }
            if (component instanceof JCheckBox check) {
                check.setForeground(theme.panelText);
            }
            if (component instanceof JPanel panel && panel.getLayout() instanceof GridLayout) {
                for (Component child : panel.getComponents()) {
                    if (child instanceof JButton button) {
                        button.setForeground(theme.buttonText);
                        button.repaint();
                    }
                }
            }
        }
        engineStatus.setForeground(theme.panelMuted);
        for (JButton button : new JButton[]{hintButton, undoButton, redoButton, newGameButton,
                resignButton, drawButton, engineButton}) {
            if (button != null) {
                button.setForeground(theme.buttonText);
                button.repaint();
            }
        }
        for (SegmentedControl control : new SegmentedControl[]{modeControl, difficultyControl,
                sideControl, themeControl}) {
            control.applyTheme(theme);
        }
        repaint();
    }
}
