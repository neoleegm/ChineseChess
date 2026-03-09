import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;

/**
 * 象棋棋盘绘制面板 - 修正版
 */
public class ChessPanel extends JPanel {
    private static final int CELL_SIZE = 60;  // 格子大小
    private static final int PIECE_RADIUS = 26;  // 棋子半径
    private static final int BOARD_MARGIN = 50;  // 棋盘边距
    
    private ChessBoard board;
    private int selectedRow = -1;
    private int selectedCol = -1;
    private JLabel statusLabel;
    private Runnable onGameOver;
    
    public ChessPanel(ChessBoard board, JLabel statusLabel) {
        this.board = board;
        this.statusLabel = statusLabel;
        setPreferredSize(new Dimension(
            BOARD_MARGIN * 2 + (ChessBoard.COLS - 1) * CELL_SIZE,
            BOARD_MARGIN * 2 + (ChessBoard.ROWS - 1) * CELL_SIZE + 40
        ));
        setBackground(new Color(222, 184, 135));  // 棋盘木色背景
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleClick(e.getX(), e.getY());
            }
        });
        
        updateStatus();
    }
    
    private void handleClick(int x, int y) {
        if (board.isGameOver()) return;
        
        int col = Math.round((float)(x - BOARD_MARGIN) / CELL_SIZE);
        int row = Math.round((float)(y - BOARD_MARGIN) / CELL_SIZE);
        
        if (row < 0 || row >= ChessBoard.ROWS || col < 0 || col >= ChessBoard.COLS) {
            return;
        }
        
        ChessPiece piece = board.getPiece(row, col);
        
        if (selectedRow == -1) {
            if (piece != null && piece.isRed() == board.isRedTurn()) {
                selectedRow = row;
                selectedCol = col;
                repaint();
            }
        } else {
            if (row == selectedRow && col == selectedCol) {
                selectedRow = -1;
                selectedCol = -1;
                repaint();
            } else {
                if (board.movePiece(selectedRow, selectedCol, row, col)) {
                    selectedRow = -1;
                    selectedCol = -1;
                    repaint();
                    updateStatus();
                    
                    if (board.isGameOver() && onGameOver != null) {
                        onGameOver.run();
                    }
                } else if (piece != null && piece.isRed() == board.isRedTurn()) {
                    selectedRow = row;
                    selectedCol = col;
                    repaint();
                }
            }
        }
    }
    
    private void updateStatus() {
        if (board.isGameOver()) {
            statusLabel.setText(board.getWinner());
        } else {
            statusLabel.setText(board.isRedTurn() ? "红方走棋" : "黑方走棋");
        }
    }
    
    public void setOnGameOver(Runnable onGameOver) {
        this.onGameOver = onGameOver;
    }
    
    public void reset() {
        selectedRow = -1;
        selectedCol = -1;
        updateStatus();
        repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 绘制棋盘背景阴影
        drawBoardShadow(g2d);
        
        // 绘制棋盘线
        drawBoard(g2d);
        
        // 绘制炮位和兵位标记
        drawPositionMarks(g2d);
        
        // 绘制棋子
        drawPieces(g2d);
        
        // 绘制选中标记
        if (selectedRow != -1) {
            drawSelection(g2d, selectedRow, selectedCol);
        }
    }
    
    /**
     * 绘制棋盘背景阴影
     */
    private void drawBoardShadow(Graphics2D g2d) {
        int startX = BOARD_MARGIN - 5;
        int startY = BOARD_MARGIN - 5;
        int width = (ChessBoard.COLS - 1) * CELL_SIZE + 10;
        int height = (ChessBoard.ROWS - 1) * CELL_SIZE + 10;
        
        g2d.setColor(new Color(180, 150, 100));
        g2d.fillRoundRect(startX + 3, startY + 3, width, height, 10, 10);
    }
    
    /**
     * 绘制棋盘网格 - 修正版
     */
    private void drawBoard(Graphics2D g2d) {
        g2d.setColor(new Color(60, 40, 20));
        g2d.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        
        int startX = BOARD_MARGIN;
        int startY = BOARD_MARGIN;
        int endX = startX + (ChessBoard.COLS - 1) * CELL_SIZE;
        int endY = startY + (ChessBoard.ROWS - 1) * CELL_SIZE;
        
        // 绘制外边框
        g2d.drawRoundRect(startX - 2, startY - 2, endX - startX + 4, endY - startY + 4, 5, 5);
        
        // 绘制所有横线（全部贯通）
        for (int i = 0; i < ChessBoard.ROWS; i++) {
            int y = startY + i * CELL_SIZE;
            g2d.drawLine(startX, y, endX, y);
        }
        
        // 绘制竖线
        for (int j = 0; j < ChessBoard.COLS; j++) {
            int x = startX + j * CELL_SIZE;
            // 上方从第0行到第4行
            g2d.drawLine(x, startY, x, startY + 4 * CELL_SIZE);
            // 下方从第5行到第9行
            g2d.drawLine(x, startY + 5 * CELL_SIZE, x, endY);
        }
        
        // 绘制九宫格斜线 - X型
        // 上方九宫 (0-2行, 3-5列)
        int topPalaceY1 = startY;
        int topPalaceY2 = startY + 2 * CELL_SIZE;
        int leftPalaceX = startX + 3 * CELL_SIZE;
        int rightPalaceX = startX + 5 * CELL_SIZE;
        
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawLine(leftPalaceX, topPalaceY1, rightPalaceX, topPalaceY2);
        g2d.drawLine(rightPalaceX, topPalaceY1, leftPalaceX, topPalaceY2);
        
        // 下方九宫 (7-9行, 3-5列)
        int bottomPalaceY1 = startY + 7 * CELL_SIZE;
        int bottomPalaceY2 = startY + 9 * CELL_SIZE;
        
        g2d.drawLine(leftPalaceX, bottomPalaceY1, rightPalaceX, bottomPalaceY2);
        g2d.drawLine(rightPalaceX, bottomPalaceY1, leftPalaceX, bottomPalaceY2);
        
        // 绘制楚河汉界文字
        g2d.setFont(new Font("SimSun", Font.BOLD, 36));
        g2d.setColor(new Color(60, 40, 20));
        
        String chuHe = "楚河";
        String hanJie = "汉界";
        
        // 楚河 - 右侧（黑方视角）
        g2d.drawString(chuHe, startX + 2 * CELL_SIZE - 10, startY + 4 * CELL_SIZE + CELL_SIZE / 2 + 10);
        // 汉界 - 左侧（黑方视角）
        g2d.drawString(hanJie, startX + 6 * CELL_SIZE - 10, startY + 4 * CELL_SIZE + CELL_SIZE / 2 + 10);
    }
    
    /**
     * 绘制炮位和兵位标记（十字标记）
     */
    private void drawPositionMarks(Graphics2D g2d) {
        g2d.setColor(new Color(60, 40, 20));
        g2d.setStroke(new BasicStroke(1.5f));
        
        int markLen = 8;
        int offset = 3;
        
        // 炮位标记 (2,1), (2,7), (7,1), (7,7)
        int[][] cannonPos = {{2, 1}, {2, 7}, {7, 1}, {7, 7}};
        for (int[] pos : cannonPos) {
            drawMark(g2d, pos[0], pos[1], markLen, offset);
        }
        
        // 兵位标记
        int[][] pawnPos = {{3, 0}, {3, 2}, {3, 4}, {3, 6}, {3, 8},
                          {6, 0}, {6, 2}, {6, 4}, {6, 6}, {6, 8}};
        for (int[] pos : pawnPos) {
            drawMark(g2d, pos[0], pos[1], markLen, offset);
        }
    }
    
    private void drawMark(Graphics2D g2d, int row, int col, int len, int offset) {
        int x = BOARD_MARGIN + col * CELL_SIZE;
        int y = BOARD_MARGIN + row * CELL_SIZE;
        
        // 左上角
        if (col > 0) {
            g2d.drawLine(x - len - offset, y - offset, x - offset, y - offset);
            g2d.drawLine(x - offset, y - len - offset, x - offset, y - offset);
        }
        // 右上角
        if (col < 8) {
            g2d.drawLine(x + offset, y - offset, x + len + offset, y - offset);
            g2d.drawLine(x + offset, y - len - offset, x + offset, y - offset);
        }
        // 左下角
        if (col > 0) {
            g2d.drawLine(x - len - offset, y + offset, x - offset, y + offset);
            g2d.drawLine(x - offset, y + offset, x - offset, y + len + offset);
        }
        // 右下角
        if (col < 8) {
            g2d.drawLine(x + offset, y + offset, x + len + offset, y + offset);
            g2d.drawLine(x + offset, y + offset, x + offset, y + len + offset);
        }
    }
    
    /**
     * 绘制所有棋子
     */
    private void drawPieces(Graphics2D g2d) {
        for (int row = 0; row < ChessBoard.ROWS; row++) {
            for (int col = 0; col < ChessBoard.COLS; col++) {
                ChessPiece piece = board.getPiece(row, col);
                if (piece != null) {
                    drawPiece(g2d, piece, row, col);
                }
            }
        }
    }
    
    /**
     * 绘制单个棋子 - 双层边框版
     */
    private void drawPiece(Graphics2D g2d, ChessPiece piece, int row, int col) {
        int x = BOARD_MARGIN + col * CELL_SIZE;
        int y = BOARD_MARGIN + row * CELL_SIZE;
        
        boolean isRed = piece.isRed();
        Color innerColor = isRed ? new Color(220, 50, 50) : new Color(30, 30, 30);
        Color outerColor = new Color(40, 40, 40);
        
        // 棋子阴影
        g2d.setColor(new Color(0, 0, 0, 80));
        g2d.fillOval(x - PIECE_RADIUS + 3, y - PIECE_RADIUS + 3, 
                     PIECE_RADIUS * 2, PIECE_RADIUS * 2);
        
        // 外层黑边
        g2d.setColor(outerColor);
        g2d.fillOval(x - PIECE_RADIUS, y - PIECE_RADIUS, 
                     PIECE_RADIUS * 2, PIECE_RADIUS * 2);
        
        // 棋子底色（象牙白）
        g2d.setColor(new Color(255, 248, 230));
        g2d.fillOval(x - PIECE_RADIUS + 2, y - PIECE_RADIUS + 2, 
                     PIECE_RADIUS * 2 - 4, PIECE_RADIUS * 2 - 4);
        
        // 内层边框
        g2d.setColor(innerColor);
        g2d.setStroke(new BasicStroke(2.5f));
        g2d.drawOval(x - PIECE_RADIUS + 5, y - PIECE_RADIUS + 5, 
                     PIECE_RADIUS * 2 - 10, PIECE_RADIUS * 2 - 10);
        
        // 再细一点的内圈装饰
        g2d.setStroke(new BasicStroke(1f));
        g2d.drawOval(x - PIECE_RADIUS + 9, y - PIECE_RADIUS + 9, 
                     PIECE_RADIUS * 2 - 18, PIECE_RADIUS * 2 - 18);
        
        // 绘制棋子文字
        g2d.setFont(new Font("SimSun", Font.BOLD, 26));
        FontMetrics fm = g2d.getFontMetrics();
        String text = piece.getName();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getAscent();
        
        // 文字阴影
        g2d.setColor(new Color(0, 0, 0, 50));
        g2d.drawString(text, x - textWidth / 2 + 1, y + textHeight / 3 + 1);
        
        // 文字本体
        g2d.setColor(innerColor);
        g2d.drawString(text, x - textWidth / 2, y + textHeight / 3);
    }
    
    /**
     * 绘制选中标记
     */
    private void drawSelection(Graphics2D g2d, int row, int col) {
        int x = BOARD_MARGIN + col * CELL_SIZE;
        int y = BOARD_MARGIN + row * CELL_SIZE;
        int size = PIECE_RADIUS + 6;
        
        g2d.setColor(new Color(50, 180, 50));
        g2d.setStroke(new BasicStroke(3f));
        
        int markLength = 12;
        int gap = 4;
        
        // 左上角
        g2d.drawLine(x - size, y - size + markLength, x - size, y - size + gap);
        g2d.drawLine(x - size + gap, y - size, x - size + markLength, y - size);
        
        // 右上角
        g2d.drawLine(x + size, y - size + markLength, x + size, y - size + gap);
        g2d.drawLine(x + size - markLength, y - size, x + size - gap, y - size);
        
        // 左下角
        g2d.drawLine(x - size, y + size - markLength, x - size, y + size - gap);
        g2d.drawLine(x - size + gap, y + size, x - size + markLength, y + size);
        
        // 右下角
        g2d.drawLine(x + size, y + size - markLength, x + size, y + size - gap);
        g2d.drawLine(x + size - markLength, y + size, x + size - gap, y + size);
    }
}
