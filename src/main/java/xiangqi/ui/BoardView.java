package xiangqi.ui;

import xiangqi.core.Board;
import xiangqi.core.Move;
import xiangqi.core.Piece;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 棋盘视图：负责绘制与鼠标交互，不含任何对局规则判断（合法性全部问 {@link Board}）。
 *
 * <p>交互同时支持「点选两步走」和「按住拖拽」；视图尺寸自适应，
 * 窗口拉大时棋子与棋盘一起放大。</p>
 */
public final class BoardView extends JPanel {

    private static final int MIN_PADDING = 26;
    /** 棋子半径 / 格宽。略小于 0.5 才能留出棋子之间的缝隙。 */
    private static final double PIECE_SCALE = 0.42;
    /** 棋盘纸相对网格每边多出的宽度（格宽倍数）。 */
    private static final double PAPER_PAD_SCALE = 0.48;
    private static final String[] RED_NUMERALS = {"一", "二", "三", "四", "五", "六", "七", "八", "九"};

    private Board board = new Board();
    private Move lastMove;
    private Move hintMove;
    private Consumer<Move> moveHandler = move -> {
    };
    private Theme theme = Theme.WOOD;
    private boolean flipped;
    private boolean showCoordinates = true;
    private boolean interactive = true;
    private String busyText = "";

    private int cell = 60;
    private int originX = 40;
    private int originY = 40;
    private Font pieceFont = new Font(Font.SANS_SERIF, Font.BOLD, 28);
    private Font coordFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private Font riverFont = new Font(Font.SANS_SERIF, Font.BOLD, 20);

    private int selectedRow = -1;
    private int selectedCol = -1;
    private final List<Move> targets = new ArrayList<>();
    private int hoverRow = -1;
    private int hoverCol = -1;

    private boolean dragging;
    private int dragX;
    private int dragY;

    private boolean inCheck;
    private float checkPulse;
    private final Timer pulseTimer;

    public BoardView() {
        setOpaque(true);
        setBackground(Theme.WOOD.panelBg);
        setPreferredSize(new Dimension(620, 700));
        setFocusable(true);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                onPress(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (selectedRow >= 0) {
                    dragging = true;
                    dragX = e.getX();
                    dragY = e.getY();
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                onRelease(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                onMove(e);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoverRow != -1 || hoverCol != -1) {
                    hoverRow = -1;
                    hoverCol = -1;
                    repaint();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        pulseTimer = new Timer(60, e -> {
            checkPulse += 0.09f;
            if (checkPulse > 1f) {
                checkPulse -= 1f;
            }
            repaint();
        });
    }

    // ==================== 外部接口 ====================

    public void setBoard(Board board) {
        this.board = board;
        clearSelection();
        refreshCheckState();
        repaint();
    }

    public void setLastMove(Move move) {
        this.lastMove = move;
        repaint();
    }

    public void setHintMove(Move move) {
        this.hintMove = move;
        repaint();
    }

    public void setMoveHandler(Consumer<Move> handler) {
        this.moveHandler = handler == null ? move -> {
        } : handler;
    }

    public void setTheme(Theme theme) {
        this.theme = theme;
        setBackground(theme.panelBg);
        repaint();
    }

    public boolean isFlipped() {
        return flipped;
    }

    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
        clearSelection();
        repaint();
    }

    public void setShowCoordinates(boolean showCoordinates) {
        this.showCoordinates = showCoordinates;
        repaint();
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
        if (!interactive) {
            clearSelection();
        }
        repaint();
    }

    /** 显示「AI 思考中」气泡并锁定输入；传空字符串恢复。 */
    public void setBusy(String text) {
        this.busyText = text == null ? "" : text;
        if (!busyText.isEmpty()) {
            clearSelection();
        }
        repaint();
    }

    private void refreshCheckState() {
        inCheck = board.kingSquare(board.isRedTurn()) >= 0 && board.inCheck(board.isRedTurn());
        if (inCheck && !pulseTimer.isRunning()) {
            pulseTimer.start();
        } else if (!inCheck && pulseTimer.isRunning()) {
            pulseTimer.stop();
        }
    }

    // ==================== 交互 ====================

    private void onPress(MouseEvent e) {
        requestFocusInWindow();
        if (!interactive || !busyText.isEmpty() || board.isGameOver()) {
            return;
        }
        int[] square = squareAt(e.getX(), e.getY());
        if (square == null) {
            return;
        }
        int row = square[0];
        int col = square[1];
        Piece piece = board.pieceAt(row, col);

        if (selectedRow >= 0) {
            if (row == selectedRow && col == selectedCol) {
                clearSelection();
                repaint();
                return;
            }
            if (tryPlay(selectedRow, selectedCol, row, col)) {
                return;
            }
            if (piece != null && piece.isRed() == board.isRedTurn()) {
                select(row, col);
                return;
            }
            clearSelection();
            repaint();
            return;
        }

        if (piece != null && piece.isRed() == board.isRedTurn()) {
            select(row, col);
        }
    }

    private void onRelease(MouseEvent e) {
        if (!dragging) {
            return;
        }
        dragging = false;
        int[] square = squareAt(e.getX(), e.getY());
        if (square != null && selectedRow >= 0
                && !(square[0] == selectedRow && square[1] == selectedCol)) {
            if (!tryPlay(selectedRow, selectedCol, square[0], square[1])) {
                repaint();
            }
        } else {
            repaint();
        }
    }

    private boolean tryPlay(int fromRow, int fromCol, int toRow, int toCol) {
        Move move = new Move(fromRow, fromCol, toRow, toCol);
        if (!board.isLegalMove(move)) {
            return false;
        }
        clearSelection();
        moveHandler.accept(move);
        return true;
    }

    private void onMove(MouseEvent e) {
        int[] square = squareAt(e.getX(), e.getY());
        int row = square == null ? -1 : square[0];
        int col = square == null ? -1 : square[1];
        if (row != hoverRow || col != hoverCol) {
            hoverRow = row;
            hoverCol = col;
            repaint();
        }
        boolean hand = false;
        if (interactive && busyText.isEmpty() && row >= 0) {
            Piece piece = board.pieceAt(row, col);
            hand = piece != null && piece.isRed() == board.isRedTurn();
            if (!hand && selectedRow >= 0) {
                hand = targets.stream().anyMatch(m -> m.toRow() == row && m.toCol() == col);
            }
        }
        setCursor(Cursor.getPredefinedCursor(hand ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    private void select(int row, int col) {
        selectedRow = row;
        selectedCol = col;
        targets.clear();
        for (Move move : board.legalMoves()) {
            if (move.fromRow() == row && move.fromCol() == col) {
                targets.add(move);
            }
        }
        repaint();
    }

    private void clearSelection() {
        selectedRow = -1;
        selectedCol = -1;
        targets.clear();
    }

    private int[] squareAt(int x, int y) {
        int screenCol = Math.round((x - originX) / (float) cell);
        int screenRow = Math.round((y - originY) / (float) cell);
        if (screenCol < 0 || screenCol > 8 || screenRow < 0 || screenRow > 9) {
            return null;
        }
        return new int[]{
                flipped ? 9 - screenRow : screenRow,
                flipped ? 8 - screenCol : screenCol
        };
    }

    // ==================== 坐标换算 ====================

    private int screenCol(int col) {
        return flipped ? 8 - col : col;
    }

    private int screenRow(int row) {
        return flipped ? 9 - row : row;
    }

    private int px(int col) {
        return originX + screenCol(col) * cell;
    }

    private int py(int row) {
        return originY + screenRow(row) * cell;
    }

    private void layoutBoard() {
        int width = Math.max(getWidth(), 1);
        int height = Math.max(getHeight(), 1);
        // 棋盘纸比网格每边多出 0.34 格，计算格宽时一并留出，保证外框不会被裁掉
        double across = 8 + 2 * PAPER_PAD_SCALE;
        double down = 9 + 2 * PAPER_PAD_SCALE;
        int cellW = (int) ((width - 2 * MIN_PADDING) / across);
        int cellH = (int) ((height - 2 * MIN_PADDING) / down);
        cell = Math.max(22, Math.min(cellW, cellH));

        int pad = (int) (cell * PAPER_PAD_SCALE);
        int paperWidth = cell * 8 + pad * 2;
        int paperHeight = cell * 9 + pad * 2;
        originX = (width - paperWidth) / 2 + pad;
        originY = (height - paperHeight) / 2 + pad;

        pieceFont = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(11, (int) (cell * 0.46)));
        coordFont = new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(9, (int) (cell * 0.19)));
        riverFont = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(12, (int) (cell * 0.34)));
    }

    // ==================== 绘制 ====================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        layoutBoard();
        drawBoardPaper(g2);
        drawGrid(g2);
        drawPalaces(g2);
        drawRiver(g2);
        drawMarks(g2);
        if (showCoordinates) {
            drawCoordinates(g2);
        }
        drawLastMove(g2);
        drawTargets(g2);
        drawSelection(g2);
        drawHover(g2);
        drawPieces(g2);
        drawHint(g2);
        drawCheck(g2);
        drawBusyPill(g2);

        g2.dispose();
    }

    private void drawBoardPaper(Graphics2D g2) {
        int pad = (int) (cell * PAPER_PAD_SCALE);
        int x = originX - pad;
        int y = originY - pad;
        int width = cell * 8 + pad * 2;
        int height = cell * 9 + pad * 2;

        g2.setColor(new Color(0, 0, 0, 36));
        g2.fillRoundRect(x + 3, y + 4, width, height, 16, 16);

        g2.setPaint(new GradientPaint(x, y, theme.boardTop, x + width, y + height, theme.boardBottom));
        g2.fillRoundRect(x, y, width, height, 16, 16);

        g2.setColor(theme.boardEdge);
        g2.setStroke(new BasicStroke(2.4f));
        g2.drawRoundRect(x, y, width, height, 16, 16);
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(theme.boardEdge.getRed(), theme.boardEdge.getGreen(),
                theme.boardEdge.getBlue(), 120));
        g2.drawRoundRect(x + 5, y + 5, width - 10, height - 10, 12, 12);
    }

    private void drawGrid(Graphics2D g2) {
        int right = originX + cell * 8;
        int bottom = originY + cell * 9;
        g2.setColor(theme.line);
        g2.setStroke(new BasicStroke(Math.max(1f, cell * 0.022f)));

        for (int row = 0; row <= 9; row++) {
            int y = originY + row * cell;
            g2.drawLine(originX, y, right, y);
        }
        for (int col = 0; col <= 8; col++) {
            int x = originX + col * cell;
            if (col == 0 || col == 8) {
                // 真实棋盘最外两条竖线是贯通的
                g2.drawLine(x, originY, x, bottom);
            } else {
                g2.drawLine(x, originY, x, originY + cell * 4);
                g2.drawLine(x, originY + cell * 5, x, bottom);
            }
        }

        g2.setStroke(new BasicStroke(Math.max(1.6f, cell * 0.034f)));
        g2.drawRect(originX, originY, cell * 8, cell * 9);
    }

    private void drawPalaces(Graphics2D g2) {
        g2.setStroke(new BasicStroke(Math.max(1f, cell * 0.022f)));
        g2.setColor(theme.line);
        int left = originX + cell * 3;
        int right = originX + cell * 5;
        g2.drawLine(left, originY, right, originY + cell * 2);
        g2.drawLine(right, originY, left, originY + cell * 2);
        g2.drawLine(left, originY + cell * 7, right, originY + cell * 9);
        g2.drawLine(right, originY + cell * 7, left, originY + cell * 9);
    }

    private void drawRiver(Graphics2D g2) {
        g2.setFont(riverFont);
        g2.setColor(theme.line);
        FontMetrics fm = g2.getFontMetrics();
        int centerY = originY + (int) (cell * 4.5);
        int baseline = centerY + (fm.getAscent() - fm.getDescent()) / 2;

        String left = "楚　河";
        String right = "汉　界";
        int leftX = originX + cell * 2 - fm.stringWidth(left) / 2;
        int rightX = originX + cell * 6 - fm.stringWidth(right) / 2;
        g2.drawString(left, leftX, baseline);
        g2.drawString(right, rightX, baseline);
    }

    private void drawMarks(Graphics2D g2) {
        int[][] marks = {{2, 1}, {2, 7}, {7, 1}, {7, 7},
                {3, 0}, {3, 2}, {3, 4}, {3, 6}, {3, 8},
                {6, 0}, {6, 2}, {6, 4}, {6, 6}, {6, 8}};
        for (int[] mark : marks) {
            drawMark(g2, mark[0], mark[1]);
        }
    }

    private void drawMark(Graphics2D g2, int row, int col) {
        int x = px(col);
        int y = py(row);
        int gap = Math.max(3, (int) (cell * 0.08));
        int len = Math.max(5, (int) (cell * 0.16));
        g2.setColor(theme.line);
        g2.setStroke(new BasicStroke(Math.max(1f, cell * 0.018f)));

        if (col > 0) {
            g2.drawLine(x - gap - len, y - gap, x - gap, y - gap);
            g2.drawLine(x - gap, y - gap - len, x - gap, y - gap);
            g2.drawLine(x - gap - len, y + gap, x - gap, y + gap);
            g2.drawLine(x - gap, y + gap + len, x - gap, y + gap);
        }
        if (col < 8) {
            g2.drawLine(x + gap + len, y - gap, x + gap, y - gap);
            g2.drawLine(x + gap, y - gap - len, x + gap, y - gap);
            g2.drawLine(x + gap + len, y + gap, x + gap, y + gap);
            g2.drawLine(x + gap, y + gap + len, x + gap, y + gap);
        }
    }

    private void drawCoordinates(Graphics2D g2) {
        int padBottom = (int) (cell * PAPER_PAD_SCALE);
        g2.setFont(coordFont);
        g2.setColor(new Color(theme.line.getRed(), theme.line.getGreen(), theme.line.getBlue(), 200));
        FontMetrics fm = g2.getFontMetrics();
        boolean bottomIsRed = !flipped;

        for (int screen = 0; screen <= 8; screen++) {
            int col = flipped ? 8 - screen : screen;
            String red = RED_NUMERALS[8 - col];
            String black = String.valueOf(col + 1);
            String bottom = bottomIsRed ? red : black;
            String top = bottomIsRed ? black : red;

            int x = originX + screen * cell;
            // 编号画在棋盘纸外侧，避免与底线棋子重叠
            g2.drawString(bottom, x - fm.stringWidth(bottom) / 2,
                    originY + cell * 9 + padBottom + fm.getAscent() + 3);
            g2.drawString(top, x - fm.stringWidth(top) / 2,
                    originY - padBottom - 6);
        }
    }

    private void drawLastMove(Graphics2D g2) {
        if (lastMove == null) {
            return;
        }
        g2.setColor(theme.lastMove);
        int size = (int) (cell * 0.86);
        int offset = size / 2;
        g2.fill(new RoundRectangle2D.Float(
                px(lastMove.fromCol()) - offset, py(lastMove.fromRow()) - offset, size, size, 12, 12));
        g2.fill(new RoundRectangle2D.Float(
                px(lastMove.toCol()) - offset, py(lastMove.toRow()) - offset, size, size, 12, 12));
    }

    private void drawTargets(Graphics2D g2) {
        if (targets.isEmpty()) {
            return;
        }
        int radius = (int) (cell * PIECE_SCALE);
        for (Move move : targets) {
            int x = px(move.toCol());
            int y = py(move.toRow());
            boolean capture = board.pieceAt(move.toRow(), move.toCol()) != null;
            if (capture) {
                g2.setColor(new Color(theme.check.getRed(), theme.check.getGreen(), theme.check.getBlue(), 170));
                g2.setStroke(new BasicStroke(Math.max(2f, cell * 0.05f)));
                g2.drawOval(x - radius - 3, y - radius - 3, (radius + 3) * 2, (radius + 3) * 2);
            } else {
                g2.setColor(new Color(theme.accent.getRed(), theme.accent.getGreen(),
                        theme.accent.getBlue(), 190));
                int dot = Math.max(3, (int) (cell * 0.1));
                g2.fillOval(x - dot, y - dot, dot * 2, dot * 2);
            }
        }
    }

    private void drawSelection(Graphics2D g2) {
        if (selectedRow < 0) {
            return;
        }
        int x = px(selectedCol);
        int y = py(selectedRow);
        int radius = (int) (cell * (PIECE_SCALE + 0.05));
        g2.setColor(theme.accentSoft);
        g2.fillOval(x - radius, y - radius, radius * 2, radius * 2);
        g2.setColor(theme.accent);
        g2.setStroke(new BasicStroke(Math.max(2f, cell * 0.045f)));
        g2.drawOval(x - radius, y - radius, radius * 2, radius * 2);
    }

    private void drawHover(Graphics2D g2) {
        if (hoverRow < 0 || selectedRow >= 0 || !busyText.isEmpty()) {
            return;
        }
        Piece piece = board.pieceAt(hoverRow, hoverCol);
        if (piece == null || piece.isRed() != board.isRedTurn()) {
            return;
        }
        int x = px(hoverCol);
        int y = py(hoverRow);
        int radius = (int) (cell * (PIECE_SCALE + 0.04));
        g2.setColor(new Color(theme.accent.getRed(), theme.accent.getGreen(), theme.accent.getBlue(), 120));
        g2.setStroke(new BasicStroke(Math.max(1.5f, cell * 0.03f)));
        g2.drawOval(x - radius, y - radius, radius * 2, radius * 2);
    }

    private void drawPieces(Graphics2D g2) {
        for (Piece piece : board.pieces()) {
            if (dragging && piece.row() == selectedRow && piece.col() == selectedCol) {
                continue;
            }
            drawPiece(g2, piece, px(piece.col()), py(piece.row()));
        }
        if (dragging && selectedRow >= 0) {
            Piece piece = board.pieceAt(selectedRow, selectedCol);
            if (piece != null) {
                drawPiece(g2, piece, dragX, dragY);
            }
        }
    }

    private void drawPiece(Graphics2D g2, Piece piece, int cx, int cy) {
        int radius = (int) (cell * PIECE_SCALE);
        Color ink = piece.isRed() ? theme.redInk : theme.blackInk;

        g2.setColor(new Color(0, 0, 0, 42));
        g2.fillOval(cx - radius + 2, cy - radius + 3, radius * 2, radius * 2);

        g2.setColor(theme.pieceFace);
        g2.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);

        g2.setColor(theme.pieceFaceShade);
        int inner = radius - Math.max(2, (int) (cell * 0.045));
        g2.fillOval(cx - inner, cy - inner, inner * 2, inner * 2);

        g2.setColor(ink);
        g2.setStroke(new BasicStroke(Math.max(1.4f, cell * 0.032f)));
        g2.drawOval(cx - radius + 1, cy - radius + 1, (radius - 1) * 2, (radius - 1) * 2);

        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), 140));
        int ring = (int) (radius * 0.8);
        g2.drawOval(cx - ring, cy - ring, ring * 2, ring * 2);

        g2.setFont(pieceFont);
        FontMetrics fm = g2.getFontMetrics();
        String name = piece.name();
        int textX = cx - fm.stringWidth(name) / 2;
        int textY = cy + (fm.getAscent() - fm.getDescent()) / 2;
        g2.setColor(ink);
        g2.drawString(name, textX, textY);
    }

    private void drawHint(Graphics2D g2) {
        if (hintMove == null) {
            return;
        }
        int x1 = px(hintMove.fromCol());
        int y1 = py(hintMove.fromRow());
        int x2 = px(hintMove.toCol());
        int y2 = py(hintMove.toRow());
        int radius = (int) (cell * (PIECE_SCALE + 0.07));

        g2.setColor(theme.hint);
        g2.setStroke(new BasicStroke(Math.max(2.5f, cell * 0.055f), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g2.drawOval(x1 - radius, y1 - radius, radius * 2, radius * 2);
        g2.drawOval(x2 - radius, y2 - radius, radius * 2, radius * 2);

        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.hypot(dx, dy);
        if (length < 1) {
            return;
        }
        double ux = dx / length;
        double uy = dy / length;
        int startX = (int) Math.round(x1 + ux * (radius + 2));
        int startY = (int) Math.round(y1 + uy * (radius + 2));
        int endX = (int) Math.round(x2 - ux * (radius + 4));
        int endY = (int) Math.round(y2 - uy * (radius + 4));
        g2.drawLine(startX, startY, endX, endY);

        double angle = Math.atan2(dy, dx);
        int head = Math.max(9, (int) (cell * 0.22));
        g2.drawLine(endX, endY,
                (int) Math.round(endX + head * Math.cos(angle + Math.toRadians(150))),
                (int) Math.round(endY + head * Math.sin(angle + Math.toRadians(150))));
        g2.drawLine(endX, endY,
                (int) Math.round(endX + head * Math.cos(angle - Math.toRadians(150))),
                (int) Math.round(endY + head * Math.sin(angle - Math.toRadians(150))));
    }

    private void drawCheck(Graphics2D g2) {
        if (!inCheck) {
            return;
        }
        int king = board.kingSquare(board.isRedTurn());
        if (king < 0) {
            return;
        }
        int row = king / Board.COLS;
        int col = king % Board.COLS;
        int x = px(col);
        int y = py(row);
        float alpha = 0.45f + 0.4f * (float) Math.abs(Math.sin(checkPulse * Math.PI));
        int radius = (int) (cell * (PIECE_SCALE + 0.09));
        g2.setColor(new Color(theme.check.getRed(), theme.check.getGreen(), theme.check.getBlue(),
                (int) (alpha * 255)));
        g2.setStroke(new BasicStroke(Math.max(2.5f, cell * 0.06f)));
        g2.drawOval(x - radius, y - radius, radius * 2, radius * 2);
    }

    private void drawBusyPill(Graphics2D g2) {
        if (busyText.isEmpty()) {
            return;
        }
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(11, (int) (cell * 0.22))));
        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(busyText);
        int padX = 14;
        int padY = 7;
        int width = textWidth + padX * 2;
        int height = fm.getHeight() + padY * 2 - 2;
        int x = originX + cell * 4 - width / 2;
        int y = originY + cell * 5 - height / 2;

        g2.setColor(theme.pillBg);
        g2.fillRoundRect(x, y, width, height, height, height);
        g2.setColor(Color.WHITE);
        g2.drawString(busyText, x + padX, y + padY + fm.getAscent() - 1);
    }

    /** 供离屏渲染测试使用：把当前画面画到指定 Graphics。 */
    public void paintTo(Graphics2D g2, int width, int height) {
        setSize(width, height);
        paintComponent(g2);
    }

    /** 供测试读取当前单元格尺寸。 */
    public int cellSize() {
        return cell;
    }
}
