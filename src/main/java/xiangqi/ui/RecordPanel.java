package xiangqi.ui;

import xiangqi.core.Board;
import xiangqi.core.Piece;
import xiangqi.game.GameSession;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 右侧记录栏：双方用时、着法表（点击可复盘）、双方被吃子。
 */
public final class RecordPanel extends JPanel {

    private static final int[] INITIAL_COUNTS = {1, 2, 2, 2, 2, 2, 5};

    private final IntConsumer onReviewPly;
    private final DefaultTableModel model;
    private final JTable table;
    private final JLabel titleLabel;
    private final JLabel redClock;
    private final JLabel blackClock;
    private final JLabel redCaptured;
    private final JLabel blackCaptured;
    private final JScrollPane scroll;

    private Theme theme;
    private boolean updating;

    public RecordPanel(IntConsumer onReviewPly, Theme theme) {
        this.onReviewPly = onReviewPly;
        this.theme = theme;
        setLayout(new BorderLayout(0, 8));
        setPreferredSize(new Dimension(232, 0));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        titleLabel = Ui.label("对局记录", Font.BOLD, 16, theme.panelText);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(titleLabel);
        header.add(Box.createVerticalStrut(8));

        JPanel clocks = new JPanel(new java.awt.GridLayout(1, 2, 8, 0));
        clocks.setOpaque(false);
        clocks.setAlignmentX(Component.LEFT_ALIGNMENT);
        redClock = clockLabel(theme.redInk);
        blackClock = clockLabel(theme.panelText);
        clocks.add(redClock);
        clocks.add(blackClock);
        clocks.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        header.add(clocks);
        add(header, BorderLayout.NORTH);

        model = new DefaultTableModel(new Object[]{"回合", "红方", "黑方"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(model);
        table.setFont(Ui.font(Font.PLAIN, 13));
        table.setRowHeight(25);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFocusable(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setFont(Ui.font(Font.BOLD, 12));
        table.getColumnModel().getColumn(0).setPreferredWidth(38);
        table.getColumnModel().getColumn(0).setMaxWidth(46);
        table.setDefaultRenderer(Object.class, new MoveCellRenderer());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (updating) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                int column = table.columnAtPoint(e.getPoint());
                if (row < 0) {
                    return;
                }
                int ply = column == 2 ? row * 2 + 2 : row * 2 + 1;
                onReviewPly.accept(ply);
            }
        });

        scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(theme.panelBorder));
        scroll.getViewport().setBackground(theme.recordBg);
        add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setOpaque(false);
        footer.add(Ui.section("黑方被吃", theme));
        blackCaptured = capturedLabel();
        footer.add(blackCaptured);
        footer.add(Box.createVerticalStrut(6));
        footer.add(Ui.section("红方被吃", theme));
        redCaptured = capturedLabel();
        footer.add(redCaptured);
        add(footer, BorderLayout.SOUTH);

        applyTheme(theme);
    }

    private JLabel clockLabel(Color color) {
        JLabel label = new JLabel("00:00", SwingConstants.CENTER);
        label.setFont(Ui.font(Font.BOLD, 14));
        label.setForeground(color);
        label.setOpaque(true);
        label.setBorder(new EmptyBorder(4, 4, 4, 4));
        return label;
    }

    private JLabel capturedLabel() {
        JLabel label = new JLabel("—");
        label.setFont(Ui.font(Font.PLAIN, 13));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(2, 2, 2, 2));
        return label;
    }

    public void update(GameSession session) {
        updating = true;
        try {
            List<String[]> rows = session.moveRows();
            model.setRowCount(0);
            for (String[] row : rows) {
                model.addRow(row);
            }

            int reviewPly = session.reviewPly();
            int highlightRow = reviewPly >= 0
                    ? Math.max(0, (reviewPly - 1) / 2)
                    : rows.size() - 1;
            if (highlightRow >= 0 && highlightRow < rows.size()) {
                table.setRowSelectionInterval(highlightRow, highlightRow);
                table.scrollRectToVisible(table.getCellRect(highlightRow, 0, true));
            } else {
                table.clearSelection();
            }

            updateClocks(session);

            Board board = session.displayBoard();
            redCaptured.setText(capturedText(board, true));
            blackCaptured.setText(capturedText(board, false));
        } finally {
            updating = false;
        }
    }

    /**
     * 只刷新两个时钟（含「该谁走」高亮）。主窗口用 500ms 定时器单独驱动它，
     * 不必为了走秒而重建着法表。
     */
    public void updateClocks(GameSession session) {
        redClock.setText("红 " + GameSession.formatClock(session.liveRedMillis()));
        blackClock.setText("黑 " + GameSession.formatClock(session.liveBlackMillis()));
        boolean live = !session.isOver() && !session.reviewing();
        // 用实时局面判断该谁走：复盘时 displayBoard() 是历史局面，会把高亮画错
        boolean redActive = live && session.board().isRedTurn();
        Color activeBg = Ui.tint(theme.recordBg, theme.accent, 0.34f);
        redClock.setBackground(redActive ? activeBg : theme.recordBg);
        blackClock.setBackground(live && !redActive ? activeBg : theme.recordBg);
    }

    private String capturedText(Board board, boolean red) {
        int[] counts = new int[Piece.Type.values().length];
        for (Piece piece : board.pieces()) {
            if (piece.isRed() == red) {
                counts[piece.type().ordinal()]++;
            }
        }
        StringBuilder text = new StringBuilder();
        for (Piece.Type type : Piece.Type.values()) {
            int missing = INITIAL_COUNTS[type.ordinal()] - counts[type.ordinal()];
            for (int i = 0; i < missing; i++) {
                text.append(type.name(red));
            }
        }
        return text.length() == 0 ? "—" : text.toString();
    }

    public void applyTheme(Theme theme) {
        this.theme = theme;
        setBackground(theme.panelBg);
        titleLabel.setForeground(theme.panelText);
        redClock.setForeground(theme.redInk);
        blackClock.setForeground(theme.panelText);
        redClock.setBackground(theme.recordBg);
        blackClock.setBackground(theme.recordBg);
        redCaptured.setForeground(theme.panelText);
        blackCaptured.setForeground(theme.panelText);

        table.setBackground(theme.recordBg);
        table.setForeground(theme.panelText);
        table.setSelectionBackground(Ui.tint(theme.recordBg, theme.accent, 0.2f));
        table.setSelectionForeground(theme.panelText);
        table.getTableHeader().setBackground(theme.buttonBg);
        table.getTableHeader().setForeground(theme.buttonText);
        scroll.setBorder(BorderFactory.createLineBorder(theme.panelBorder));
        scroll.getViewport().setBackground(theme.recordBg);
        for (Component component : getComponents()) {
            if (component instanceof JPanel panel) {
                for (Component child : panel.getComponents()) {
                    if (child instanceof JLabel label && label.getFont().getStyle() == Font.BOLD
                            && label.getFont().getSize() <= 12) {
                        label.setForeground(theme.panelMuted);
                    }
                }
            }
        }
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, theme.panelBorder),
                new EmptyBorder(14, 14, 14, 14)));
        repaint();
    }

    /** 让着法单元格随主题变化。 */
    private final class MoveCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setFont(Ui.font(column == 0 ? Font.PLAIN : Font.PLAIN, 13));
            setBorder(new EmptyBorder(0, 6, 0, 6));
            setHorizontalAlignment(column == 0 ? SwingConstants.RIGHT : SwingConstants.LEFT);
            if (selected) {
                setBackground(Ui.tint(theme.recordBg, theme.accent, 0.2f));
                setForeground(theme.panelText);
            } else {
                setBackground(row % 2 == 0 ? theme.recordBg : theme.recordAlt);
                setForeground(column == 0 ? theme.panelMuted : theme.panelText);
            }
            return this;
        }
    }
}
