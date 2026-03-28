import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 棋盘面板类
 * 负责绘制棋盘和棋子，处理用户交互
 */
public class ChessPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    
    // 棋盘参数
    private static final int CELL_SIZE = 60;      // 格子大小
    private static final int MARGIN = 50;         // 边距
    private static final int PIECE_RADIUS = 26;   // 棋子半径
    private static final int BOARD_WIDTH = CELL_SIZE * 8;
    private static final int BOARD_HEIGHT = CELL_SIZE * 9;
    
    // 颜色定义
    private static final Color BOARD_COLOR = new Color(238, 203, 140);
    private static final Color LINE_COLOR = new Color(80, 50, 20);
    private static final Color SELECT_COLOR = new Color(100, 200, 100, 150);
    private static final Color LAST_MOVE_COLOR = new Color(255, 255, 0, 100);
    private static final Color RED_PIECE = new Color(220, 50, 50);
    private static final Color BLACK_PIECE = new Color(30, 30, 30);
    private static final Color PIECE_BG = new Color(245, 222, 179);
    private static final Color AI_THINKING_COLOR = new Color(100, 150, 255, 100);
    
    private ChessBoard board;
    private ChessAI ai;
    private JLabel statusLabel;
    private SoundManager soundManager;
    
    // 选中状态
    private int selectedRow = -1;
    private int selectedCol = -1;
    
    // 最后一步
    private int lastFromRow = -1, lastFromCol = -1;
    private int lastToRow = -1, lastToCol = -1;
    
    // 游戏模式
    public enum GameMode {
        PVP("人人对战"),
        PVE("人机对战");
        
        private final String displayName;
        GameMode(String name) { this.displayName = name; }
        public String getDisplayName() { return displayName; }
    }
    
    private GameMode mode = GameMode.PVE;
    private boolean playerIsRed = true;  // 玩家执红
    private boolean aiThinking = false;
    
    public ChessPanel(ChessBoard board, JLabel statusLabel) {
        this.board = board;
        this.statusLabel = statusLabel;
        this.ai = new ChessAI(ChessAI.Difficulty.MEDIUM);
        this.soundManager = new SoundManager();
        
        setPreferredSize(new Dimension(MARGIN * 2 + BOARD_WIDTH, MARGIN * 2 + BOARD_HEIGHT));
        setBackground(BOARD_COLOR);
        
        // 鼠标事件
        addMouseListener(new MouseInputAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleMouseClick(e.getX(), e.getY());
            }
        });
    }
    
    public void setGameMode(GameMode mode) {
        this.mode = mode;
        reset();
    }
    
    public void setDifficulty(ChessAI.Difficulty difficulty) {
        ai.setDifficulty(difficulty);
        if (mode == GameMode.PVE) reset();
    }
    
    public void setPlayerSide(boolean isRed) {
        this.playerIsRed = isRed;
        if (mode == GameMode.PVE) reset();
    }
    
    public void setSoundEnabled(boolean enabled) {
        soundManager.setEnabled(enabled);
    }
    
    private void handleMouseClick(int x, int y) {
        if (aiThinking) return;
        if (board.isGameOver()) return;
        
        // 转换为棋盘坐标
        int col = Math.round((float)(x - MARGIN) / CELL_SIZE);
        int row = Math.round((float)(y - MARGIN) / CELL_SIZE);
        
        if (!board.isValidPos(row, col)) return;
        
        // 人机模式下，检查是否轮到玩家
        if (mode == GameMode.PVE && board.isRedTurn() != playerIsRed) {
            return;
        }
        
        ChessPiece piece = board.getPiece(row, col);
        
        if (selectedRow == -1) {
            // 选择棋子
            if (piece != null && piece.isRed() == board.isRedTurn()) {
                selectedRow = row;
                selectedCol = col;
                soundManager.playSelectSound();
                repaint();
            }
        } else if (row == selectedRow && col == selectedCol) {
            // 取消选择
            selectedRow = -1;
            selectedCol = -1;
            repaint();
        } else {
            // 尝试移动
            if (board.canMove(selectedRow, selectedCol, row, col)) {
                boolean captured = board.getPiece(row, col) != null;
                if (board.movePiece(selectedRow, selectedCol, row, col)) {
                    // 记录最后一步
                    lastFromRow = selectedRow;
                    lastFromCol = selectedCol;
                    lastToRow = row;
                    lastToCol = col;
                    
                    selectedRow = -1;
                    selectedCol = -1;
                    
                    soundManager.playMoveSound(captured);
                    updateStatus();
                    repaint();
                    
                    // 检查游戏结束
                    if (board.isGameOver()) {
                        soundManager.playWinSound();
                        JOptionPane.showMessageDialog(this, board.getWinner());
                        return;
                    }
                    
                    // 人机模式下，AI走棋
                    if (mode == GameMode.PVE) {
                        makeAIMove();
                    }
                }
            } else if (piece != null && piece.isRed() == board.isRedTurn()) {
                // 换选其他棋子
                selectedRow = row;
                selectedCol = col;
                soundManager.playSelectSound();
                repaint();
            }
        }
    }
    
    private void makeAIMove() {
        aiThinking = true;
        updateStatus();
        repaint();
        
        SwingWorker<int[], Void> worker = new SwingWorker<>() {
            @Override
            protected int[] doInBackground() throws Exception {
                // 根据难度调整思考时间
                int delay = switch (ai.getDifficulty()) {
                    case EASY -> 300;
                    case MEDIUM -> 600;
                    case HARD -> 1000;
                };
                Thread.sleep(delay);
                return ai.getNextMove(board);
            }
            
            @Override
            protected void done() {
                try {
                    int[] move = get();
                    if (move != null) {
                        ChessPiece target = board.getPiece(move[2], move[3]);
                        boolean captured = target != null;
                        
                        // 显示AI正在选择棋子
                        selectedRow = move[0];
                        selectedCol = move[1];
                        repaint();
                        Thread.sleep(300);
                        
                        board.movePiece(move[0], move[1], move[2], move[3]);
                        
                        lastFromRow = move[0];
                        lastFromCol = move[1];
                        lastToRow = move[2];
                        lastToCol = move[3];
                        selectedRow = -1;
                        selectedCol = -1;
                        
                        soundManager.playMoveSound(captured);
                        updateStatus();
                        repaint();
                        
                        if (board.isGameOver()) {
                            soundManager.playWinSound();
                            JOptionPane.showMessageDialog(ChessPanel.this, board.getWinner());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    aiThinking = false;
                    updateStatus();
                    repaint();
                }
            }
        };
        worker.execute();
    }
    
    public void undo() {
        if (board.getHistorySize() == 0) return;
        if (aiThinking) return;
        
        // 人机模式下需要悔两步
        board.undo();
        if (mode == GameMode.PVE && board.getHistorySize() > 0) {
            board.undo();
        }
        
        // 清除最后一步标记
        lastFromRow = -1;
        lastFromCol = -1;
        lastToRow = -1;
        lastToCol = -1;
        selectedRow = -1;
        selectedCol = -1;
        
        updateStatus();
        repaint();
    }
    
    public void reset() {
        board.reset();
        selectedRow = -1;
        selectedCol = -1;
        lastFromRow = -1;
        lastFromCol = -1;
        lastToRow = -1;
        lastToCol = -1;
        aiThinking = false;
        updateStatus();
        repaint();
        
        // 如果玩家选择执黑，AI先走
        if (mode == GameMode.PVE && !playerIsRed) {
            makeAIMove();
        }
    }
    
    private void updateStatus() {
        if (board.isGameOver()) {
            statusLabel.setText(board.getWinner());
        } else if (aiThinking) {
            statusLabel.setText("AI 思考中...");
        } else if (mode == GameMode.PVE) {
            statusLabel.setText(board.isRedTurn() == playerIsRed ? "你的回合" : "AI 思考中...");
        } else {
            statusLabel.setText(board.isRedTurn() ? "红方走棋" : "黑方走棋");
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 绘制棋盘背景
        drawBoard(g2d);
        
        // 绘制最后一步标记
        if (lastFromRow != -1) {
            drawHighlight(g2d, lastFromRow, lastFromCol, LAST_MOVE_COLOR);
            drawHighlight(g2d, lastToRow, lastToCol, LAST_MOVE_COLOR);
        }
        
        // 绘制选中标记
        if (selectedRow != -1) {
            drawHighlight(g2d, selectedRow, selectedCol, SELECT_COLOR);
        }
        
        // 绘制AI思考提示
        if (aiThinking) {
            g2d.setColor(AI_THINKING_COLOR);
            g2d.fillRect(0, 0, getWidth(), getHeight());
        }
        
        // 绘制棋子
        for (ChessPiece piece : board.getPieces()) {
            drawPiece(g2d, piece);
        }
    }
    
    private void drawBoard(Graphics2D g2d) {
        g2d.setColor(LINE_COLOR);
        g2d.setStroke(new BasicStroke(2));
        
        // 绘制横线
        for (int row = 0; row < 10; row++) {
            int y = MARGIN + row * CELL_SIZE;
            g2d.drawLine(MARGIN, y, MARGIN + BOARD_WIDTH, y);
        }
        
        // 绘制竖线（上下分开，中间是楚河汉界）
        for (int col = 0; col < 9; col++) {
            int x = MARGIN + col * CELL_SIZE;
            g2d.drawLine(x, MARGIN, x, MARGIN + 4 * CELL_SIZE);
            g2d.drawLine(x, MARGIN + 5 * CELL_SIZE, x, MARGIN + BOARD_HEIGHT);
        }
        
        // 绘制九宫格斜线
        g2d.drawLine(MARGIN + 3 * CELL_SIZE, MARGIN, MARGIN + 5 * CELL_SIZE, MARGIN + 2 * CELL_SIZE);
        g2d.drawLine(MARGIN + 5 * CELL_SIZE, MARGIN, MARGIN + 3 * CELL_SIZE, MARGIN + 2 * CELL_SIZE);
        g2d.drawLine(MARGIN + 3 * CELL_SIZE, MARGIN + 7 * CELL_SIZE, MARGIN + 5 * CELL_SIZE, MARGIN + 9 * CELL_SIZE);
        g2d.drawLine(MARGIN + 5 * CELL_SIZE, MARGIN + 7 * CELL_SIZE, MARGIN + 3 * CELL_SIZE, MARGIN + 9 * CELL_SIZE);
        
        // 绘制炮和兵的位置标记
        drawPositionMark(g2d, 2, 1);
        drawPositionMark(g2d, 2, 7);
        drawPositionMark(g2d, 7, 1);
        drawPositionMark(g2d, 7, 7);
        for (int col = 0; col < 9; col += 2) {
            drawPositionMark(g2d, 3, col);
            drawPositionMark(g2d, 6, col);
        }
        
        // 绘制楚河汉界文字
        g2d.setFont(new Font("宋体", Font.BOLD, 24));
        g2d.setColor(LINE_COLOR);
        String chuHan = "楚 河    汉 界";
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(chuHan);
        int x = MARGIN + (BOARD_WIDTH - textWidth) / 2;
        int y = MARGIN + 4 * CELL_SIZE + CELL_SIZE / 2 + fm.getAscent() / 2 - 5;
        g2d.drawString(chuHan, x, y);
    }
    
    private void drawPositionMark(Graphics2D g2d, int row, int col) {
        int x = MARGIN + col * CELL_SIZE;
        int y = MARGIN + row * CELL_SIZE;
        int len = 8;
        int offset = 4;
        
        g2d.setStroke(new BasicStroke(1.5f));
        
        // 根据位置绘制L形标记
        if (col > 0) {
            // 左上
            g2d.drawLine(x - offset, y - len - offset, x - offset, y - offset);
            g2d.drawLine(x - offset, y - offset, x - len - offset, y - offset);
            // 左下
            g2d.drawLine(x - offset, y + len + offset, x - offset, y + offset);
            g2d.drawLine(x - offset, y + offset, x - len - offset, y + offset);
        }
        if (col < 8) {
            // 右上
            g2d.drawLine(x + offset, y - len - offset, x + offset, y - offset);
            g2d.drawLine(x + offset, y - offset, x + len + offset, y - offset);
            // 右下
            g2d.drawLine(x + offset, y + len + offset, x + offset, y + offset);
            g2d.drawLine(x + offset, y + offset, x + len + offset, y + offset);
        }
    }
    
    private void drawHighlight(Graphics2D g2d, int row, int col, Color color) {
        int x = MARGIN + col * CELL_SIZE;
        int y = MARGIN + row * CELL_SIZE;
        g2d.setColor(color);
        g2d.fillOval(x - PIECE_RADIUS - 2, y - PIECE_RADIUS - 2, 
                     (PIECE_RADIUS + 2) * 2, (PIECE_RADIUS + 2) * 2);
    }
    
    private void drawPiece(Graphics2D g2d, ChessPiece piece) {
        int x = MARGIN + piece.getCol() * CELL_SIZE;
        int y = MARGIN + piece.getRow() * CELL_SIZE;
        
        boolean isRed = piece.isRed();
        Color mainColor = isRed ? RED_PIECE : BLACK_PIECE;
        
        // 绘制棋子阴影
        g2d.setColor(new Color(0, 0, 0, 50));
        g2d.fillOval(x - PIECE_RADIUS + 2, y - PIECE_RADIUS + 2, 
                     PIECE_RADIUS * 2, PIECE_RADIUS * 2);
        
        // 绘制棋子外圈（立体效果）
        g2d.setColor(mainColor);
        g2d.fillOval(x - PIECE_RADIUS, y - PIECE_RADIUS, 
                     PIECE_RADIUS * 2, PIECE_RADIUS * 2);
        
        // 绘制棋子内圈（浅色）
        int innerRadius = PIECE_RADIUS - 3;
        g2d.setColor(PIECE_BG);
        g2d.fillOval(x - innerRadius, y - innerRadius, 
                     innerRadius * 2, innerRadius * 2);
        
        // 绘制棋子文字
        g2d.setColor(mainColor);
        g2d.setFont(new Font("宋体", Font.BOLD, 28));
        String name = piece.getName();
        FontMetrics fm = g2d.getFontMetrics();
        int textX = x - fm.stringWidth(name) / 2;
        int textY = y + fm.getAscent() / 2 - 3;
        g2d.drawString(name, textX, textY);
        
        // 绘制内圈边框
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.setColor(mainColor);
        g2d.drawOval(x - innerRadius + 3, y - innerRadius + 3, 
                     innerRadius * 2 - 6, innerRadius * 2 - 6);
    }
}
