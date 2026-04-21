import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;

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
    private static final Color BOARD_COLOR_DARK = new Color(210, 170, 100);
    private static final Color BOARD_COLOR_LIGHT = new Color(245, 215, 150);
    private static final Color LINE_COLOR = new Color(80, 50, 20);
    private static final Color SELECT_COLOR = new Color(80, 180, 80, 180);
    private static final Color HOVER_COLOR = new Color(120, 200, 120, 120);
    private static final Color LAST_MOVE_COLOR = new Color(30, 60, 150, 130);
    private static final Color RED_PIECE = new Color(200, 40, 40);
    private static final Color BLACK_PIECE = new Color(30, 30, 30);
    private static final Color PIECE_BG = new Color(250, 230, 190);
    private static final Color AI_THINKING_COLOR = new Color(100, 150, 255, 80);
    private static final Color MOVE_HINT_COLOR = new Color(60, 160, 60, 180);
    private static final Color CAPTURE_HINT_COLOR = new Color(200, 60, 60, 200);
    private static final Color CHECK_FLASH_COLOR = new Color(255, 0, 0, 180);
    
    private ChessBoard board;
    private ChessAI ai;
    private JLabel statusLabel;
    private SoundManager soundManager;
    
    // 选中状态
    private int selectedRow = -1;
    private int selectedCol = -1;
    
    // 悬停状态
    private int hoverRow = -1;
    private int hoverCol = -1;
    
    // 最后一步
    private int lastFromRow = -1, lastFromCol = -1;
    private int lastToRow = -1, lastToCol = -1;
    
    // 合法走法提示缓存
    private List<Move> hoverMoves = new ArrayList<>();
    
    // 将军闪烁
    private boolean checkFlashOn = false;
    private Timer checkFlashTimer;
    
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
    private String lastAiMessage = "";
    
    public ChessPanel(ChessBoard board, JLabel statusLabel) {
        this.board = board;
        this.statusLabel = statusLabel;
        this.ai = new ChessAI(ChessAI.Difficulty.MEDIUM);
        this.soundManager = new SoundManager();
        
        setPreferredSize(new Dimension(MARGIN * 2 + BOARD_WIDTH, MARGIN * 2 + BOARD_HEIGHT));
        setBackground(BOARD_COLOR_LIGHT);
        
        // 鼠标事件
        MouseInputAdapter mouseAdapter = new MouseInputAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleMouseClick(e.getX(), e.getY());
            }
            
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getX(), e.getY());
            }
            
            @Override
            public void mouseDragged(MouseEvent e) {
                updateHover(e.getX(), e.getY());
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
        
        // 将军闪烁定时器
        checkFlashTimer = new Timer(400, e -> {
            checkFlashOn = !checkFlashOn;
            repaint();
        });
        checkFlashTimer.start();
    }
    
    public void setGameMode(GameMode mode) {
        this.mode = mode;
        reset();
    }
    
    public void setDifficulty(ChessAI.Difficulty difficulty) {
        ai.setDifficulty(difficulty);
        if (mode == GameMode.PVE) reset();
    }

    public void setPikafishEnginePath(String path) {
        ai.setPikafishEnginePath(path);
        updateStatus();
    }

    public String getPikafishEnginePath() {
        return ai.getPikafishEnginePath();
    }

    public String getEngineMessage() {
        return ai.getLastEngineMessage();
    }
    
    public void setPlayerSide(boolean isRed) {
        this.playerIsRed = isRed;
        if (mode == GameMode.PVE) reset();
    }
    
    public void setSoundEnabled(boolean enabled) {
        soundManager.setEnabled(enabled);
    }
    
    private void updateHover(int x, int y) {
        int oldHoverRow = hoverRow;
        int oldHoverCol = hoverCol;
        
        ChessPiece hoveredPiece = findPieceAtPixel(x, y);
        if (hoveredPiece != null) {
            hoverRow = hoveredPiece.getRow();
            hoverCol = hoveredPiece.getCol();
        } else {
            int col = Math.round((float)(x - MARGIN) / CELL_SIZE);
            int row = Math.round((float)(y - MARGIN) / CELL_SIZE);
            if (board.isValidPos(row, col)) {
                hoverRow = row;
                hoverCol = col;
            } else {
                hoverRow = -1;
                hoverCol = -1;
            }
        }
        
        // 更新光标
        boolean overOwnPiece = false;
        if (hoverRow != -1 && !board.isGameOver() && !aiThinking) {
            if (mode == GameMode.PVP || board.isRedTurn() == playerIsRed) {
                ChessPiece p = board.getPiece(hoverRow, hoverCol);
                overOwnPiece = p != null && p.isRed() == board.isRedTurn();
            }
        }
        setCursor(overOwnPiece ? new Cursor(Cursor.HAND_CURSOR) : new Cursor(Cursor.DEFAULT_CURSOR));
        
        if (hoverRow != oldHoverRow || hoverCol != oldHoverCol) {
            repaint();
        }
    }
    
    private ChessPiece findPieceAtPixel(int x, int y) {
        ChessPiece best = null;
        double bestDist = Double.MAX_VALUE;
        for (ChessPiece piece : board.getPieces()) {
            int px = MARGIN + piece.getCol() * CELL_SIZE;
            int py = MARGIN + piece.getRow() * CELL_SIZE;
            double dist = Math.hypot(x - px, y - py);
            if (dist <= PIECE_RADIUS + 6 && dist < bestDist) {
                bestDist = dist;
                best = piece;
            }
        }
        return best;
    }
    
    private void handleMouseClick(int x, int y) {
        if (aiThinking) return;
        if (board.isGameOver()) return;
        
        // 人机模式下，检查是否轮到玩家
        if (mode == GameMode.PVE && board.isRedTurn() != playerIsRed) {
            return;
        }
        
        // 优先检测棋子圆形区域内的点击
        ChessPiece clickedPiece = findPieceAtPixel(x, y);
        int row, col;
        if (clickedPiece != null) {
            row = clickedPiece.getRow();
            col = clickedPiece.getCol();
        } else {
            col = Math.round((float)(x - MARGIN) / CELL_SIZE);
            row = Math.round((float)(y - MARGIN) / CELL_SIZE);
        }
        
        if (!board.isValidPos(row, col)) return;
        
        ChessPiece piece = board.getPiece(row, col);
        
        if (selectedRow == -1) {
            // 选择棋子
            if (piece != null && piece.isRed() == board.isRedTurn()) {
                selectedRow = row;
                selectedCol = col;
                soundManager.playSelectSound();
                repaint();
                SwingUtilities.invokeLater(() -> {
                    updateHoverMoves();
                    repaint();
                });
            }
        } else if (row == selectedRow && col == selectedCol) {
            // 取消选择
            selectedRow = -1;
            selectedCol = -1;
            hoverMoves.clear();
            repaint();
        } else {
            // 尝试移动
            if (board.isLegalMove(selectedRow, selectedCol, row, col)) {
                boolean captured = board.getPiece(row, col) != null;
                if (board.movePiece(selectedRow, selectedCol, row, col)) {
                    // 记录最后一步
                    lastFromRow = selectedRow;
                    lastFromCol = selectedCol;
                    lastToRow = row;
                    lastToCol = col;
                    
                    selectedRow = -1;
                    selectedCol = -1;
                    hoverMoves.clear();
                    
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
                SwingUtilities.invokeLater(() -> {
                    updateHoverMoves();
                    repaint();
                });
            }
        }
    }
    
    private void updateHoverMoves() {
        hoverMoves.clear();
        if (selectedRow == -1) return;
        for (int row = 0; row < ChessBoard.ROWS; row++) {
            for (int col = 0; col < ChessBoard.COLS; col++) {
                // canMove 轻量过滤，避免大量 isLegalMove 重型调用
                if (board.canMove(selectedRow, selectedCol, row, col)
                        && board.isLegalMove(selectedRow, selectedCol, row, col)) {
                    hoverMoves.add(new Move(selectedRow, selectedCol, row, col));
                }
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
                return ai.getNextMove(board, !playerIsRed);
            }
            
            @Override
            protected void done() {
                try {
                    int[] move = get();
                    if (move != null) {
                        ChessPiece target = board.getPiece(move[2], move[3]);
                        boolean captured = target != null;
                        
                        selectedRow = move[0];
                        selectedCol = move[1];
                        repaint();
                        
                        if (!board.movePiece(move[0], move[1], move[2], move[3])) {
                            lastAiMessage = "AI 返回非法走法，已跳过";
                            selectedRow = -1;
                            selectedCol = -1;
                            return;
                        }
                        ChessPiece movedPiece = board.getPiece(move[0], move[1]);
                        ChessPiece capturedPiece = board.getPiece(move[2], move[3]);
                        lastAiMessage = formatMoveDesc(move[0], move[1], move[2], move[3], movedPiece, capturedPiece)
                            + " | " + ai.getLastEngineMessage();
                        
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
        hoverMoves.clear();
        
        updateStatus();
        repaint();
    }
    
    public void reset() {
        board.reset();
        selectedRow = -1;
        selectedCol = -1;
        hoverRow = -1;
        hoverCol = -1;
        lastFromRow = -1;
        lastFromCol = -1;
        lastToRow = -1;
        lastToCol = -1;
        hoverMoves.clear();
        aiThinking = false;
        lastAiMessage = "";
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
            if (board.isRedTurn() == playerIsRed) {
                statusLabel.setText(lastAiMessage.isBlank() ? "你的回合" : "你的回合 - " + summarizeEngineMessage(lastAiMessage));
            } else {
                statusLabel.setText("AI 思考中...");
            }
        } else {
            statusLabel.setText(board.isRedTurn() ? "红方走棋" : "黑方走棋");
        }
    }

    private String summarizeEngineMessage(String message) {
        if (message.length() <= 40) {
            return message;
        }
        return message.substring(0, 40) + "...";
    }
    
    private String formatMoveDesc(int fromRow, int fromCol, int toRow, int toCol,
                                   ChessPiece piece, ChessPiece captured) {
        if (piece == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(piece.getName());
        sb.append("[").append(fromRow).append(",").append(fromCol).append("]");
        sb.append("→");
        sb.append("[").append(toRow).append(",").append(toCol).append("]");
        if (captured != null) sb.append(" 吃").append(captured.getName());
        return sb.toString();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        // 绘制棋盘背景
        drawBoard(g2d);
        
        // 绘制最后一步标记
        if (lastFromRow != -1) {
            drawHighlight(g2d, lastFromRow, lastFromCol, LAST_MOVE_COLOR);
            drawHighlight(g2d, lastToRow, lastToCol, LAST_MOVE_COLOR);
        }
        
        // 绘制合法走法提示
        drawMoveHints(g2d);
        
        // 绘制悬停高亮
        if (hoverRow != -1 && !aiThinking && !board.isGameOver()) {
            ChessPiece p = board.getPiece(hoverRow, hoverCol);
            boolean canSelect = p != null && p.isRed() == board.isRedTurn();
            if (canSelect && (mode == GameMode.PVP || board.isRedTurn() == playerIsRed)) {
                drawHoverHighlight(g2d, hoverRow, hoverCol);
            }
        }
        
        // 绘制选中标记
        if (selectedRow != -1) {
            drawSelectionHighlight(g2d, selectedRow, selectedCol);
        }
        
        // 绘制棋子
        for (ChessPiece piece : board.getPieces()) {
            drawPiece(g2d, piece);
        }
        
        // 绘制将军闪烁提示
        if (!board.isGameOver() && checkFlashOn) {
            boolean redInCheck = board.isKingAttacked(true);
            boolean blackInCheck = board.isKingAttacked(false);
            if (redInCheck) drawCheckFlash(g2d, true);
            if (blackInCheck) drawCheckFlash(g2d, false);
        }
        
        // 绘制AI思考提示
        if (aiThinking) {
            g2d.setColor(AI_THINKING_COLOR);
            g2d.fillRect(0, 0, getWidth(), getHeight());
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("微软雅黑", Font.BOLD, 28));
            String msg = "AI 思考中...";
            FontMetrics fm = g2d.getFontMetrics();
            int tx = (getWidth() - fm.stringWidth(msg)) / 2;
            int ty = getHeight() / 2 + fm.getAscent() / 2 - 5;
            g2d.drawString(msg, tx, ty);
        }
    }
    
    private void drawBoard(Graphics2D g2d) {
        // 棋盘外边框阴影
        g2d.setColor(new Color(0, 0, 0, 40));
        g2d.fillRoundRect(MARGIN - 12, MARGIN - 12, BOARD_WIDTH + 24, BOARD_HEIGHT + 24, 16, 16);
        
        // 棋盘背景渐变
        GradientPaint gp = new GradientPaint(
            MARGIN, MARGIN, BOARD_COLOR_LIGHT,
            MARGIN + BOARD_WIDTH, MARGIN + BOARD_HEIGHT, BOARD_COLOR_DARK
        );
        g2d.setPaint(gp);
        g2d.fillRoundRect(MARGIN - 8, MARGIN - 8, BOARD_WIDTH + 16, BOARD_HEIGHT + 16, 12, 12);
        
        // 棋盘边框
        g2d.setColor(LINE_COLOR);
        g2d.setStroke(new BasicStroke(3));
        g2d.drawRoundRect(MARGIN - 8, MARGIN - 8, BOARD_WIDTH + 16, BOARD_HEIGHT + 16, 12, 12);
        
        // 绘制横线
        g2d.setStroke(new BasicStroke(2));
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
        g2d.setFont(new Font("楷体", Font.BOLD, 26));
        g2d.setColor(LINE_COLOR);
        FontMetrics fm = g2d.getFontMetrics();
        String chuHan = "楚 河    汉 界";
        int textWidth = fm.stringWidth(chuHan);
        int x = MARGIN + (BOARD_WIDTH - textWidth) / 2;
        int y = MARGIN + 4 * CELL_SIZE + CELL_SIZE / 2 + fm.getAscent() / 2 - 4;
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
    
    private void drawHoverHighlight(Graphics2D g2d, int row, int col) {
        int x = MARGIN + col * CELL_SIZE;
        int y = MARGIN + row * CELL_SIZE;
        int r = PIECE_RADIUS + 4;
        g2d.setColor(HOVER_COLOR);
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawOval(x - r, y - r, r * 2, r * 2);
    }
    
    private void drawSelectionHighlight(Graphics2D g2d, int row, int col) {
        int x = MARGIN + col * CELL_SIZE;
        int y = MARGIN + row * CELL_SIZE;
        int r = PIECE_RADIUS + 5;
        g2d.setColor(SELECT_COLOR);
        g2d.setStroke(new BasicStroke(3f));
        g2d.drawOval(x - r, y - r, r * 2, r * 2);
        
        // 内部淡绿填充
        g2d.setColor(new Color(100, 200, 100, 40));
        g2d.fillOval(x - r + 2, y - r + 2, r * 2 - 4, r * 2 - 4);
    }
    
    private void drawMoveHints(Graphics2D g2d) {
        if (selectedRow == -1 || hoverMoves.isEmpty()) return;
        for (Move move : hoverMoves) {
            int x = MARGIN + move.toCol * CELL_SIZE;
            int y = MARGIN + move.toRow * CELL_SIZE;
            ChessPiece target = board.getPiece(move.toRow, move.toCol);
            if (target != null) {
                // 吃子提示：红色圆环
                g2d.setColor(CAPTURE_HINT_COLOR);
                g2d.setStroke(new BasicStroke(2.5f));
                g2d.drawOval(x - PIECE_RADIUS - 4, y - PIECE_RADIUS - 4,
                             (PIECE_RADIUS + 4) * 2, (PIECE_RADIUS + 4) * 2);
            } else {
                // 可走提示：绿色小点
                g2d.setColor(MOVE_HINT_COLOR);
                g2d.fillOval(x - 5, y - 5, 10, 10);
            }
        }
    }
    
    private void drawCheckFlash(Graphics2D g2d, boolean isRed) {
        for (ChessPiece piece : board.getPieces()) {
            if (piece.getType() == ChessPiece.Type.KING && piece.isRed() == isRed) {
                int x = MARGIN + piece.getCol() * CELL_SIZE;
                int y = MARGIN + piece.getRow() * CELL_SIZE;
                int r = PIECE_RADIUS + 8;
                g2d.setColor(CHECK_FLASH_COLOR);
                g2d.setStroke(new BasicStroke(4f));
                g2d.drawOval(x - r, y - r, r * 2, r * 2);
                break;
            }
        }
    }
    
    private void drawPiece(Graphics2D g2d, ChessPiece piece) {
        int x = MARGIN + piece.getCol() * CELL_SIZE;
        int y = MARGIN + piece.getRow() * CELL_SIZE;
        
        boolean isRed = piece.isRed();
        Color mainColor = isRed ? RED_PIECE : BLACK_PIECE;
        
        // 阴影
        g2d.setColor(new Color(0, 0, 0, 45));
        g2d.fillOval(x - PIECE_RADIUS + 3, y - PIECE_RADIUS + 3, 
                     PIECE_RADIUS * 2, PIECE_RADIUS * 2);
        
        // 外圈立体渐变
        Color edgeColor = isRed ? new Color(140, 20, 20) : new Color(20, 20, 20);
        Color centerColor = isRed ? new Color(255, 100, 100) : new Color(100, 100, 100);
        RadialGradientPaint rgp = new RadialGradientPaint(
            x, y, PIECE_RADIUS,
            new float[]{0.0f, 0.6f, 1.0f},
            new Color[]{centerColor, mainColor, edgeColor}
        );
        g2d.setPaint(rgp);
        g2d.fillOval(x - PIECE_RADIUS, y - PIECE_RADIUS, 
                     PIECE_RADIUS * 2, PIECE_RADIUS * 2);
        
        // 内圈浅色背景
        int innerRadius = PIECE_RADIUS - 4;
        g2d.setColor(PIECE_BG);
        g2d.fillOval(x - innerRadius, y - innerRadius, 
                     innerRadius * 2, innerRadius * 2);
        
        // 内圈边框
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.setColor(mainColor);
        g2d.drawOval(x - innerRadius, y - innerRadius, 
                     innerRadius * 2, innerRadius * 2);
        
        // 文字阴影
        g2d.setColor(new Color(0, 0, 0, 60));
        g2d.setFont(new Font("宋体", Font.BOLD, 26));
        String name = piece.getName();
        FontMetrics fm = g2d.getFontMetrics();
        int textX = x - fm.stringWidth(name) / 2 + 1;
        int textY = y + fm.getAscent() / 2 - 3 + 1;
        g2d.drawString(name, textX, textY);
        
        // 文字主体
        g2d.setColor(mainColor);
        textX = x - fm.stringWidth(name) / 2;
        textY = y + fm.getAscent() / 2 - 3;
        g2d.drawString(name, textX, textY);
    }
}
