import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

@SuppressWarnings({"serial", "this-escape"})
public class ChessPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int CELL = 65;
    private static final int RADIUS = 28;
    private static final int MARGIN = 55;
    
    private static final Color WOOD_LIGHT = new Color(240, 210, 160);
    private static final Color WOOD_DARK = new Color(220, 185, 140);
    private static final Color LINE_COLOR = new Color(80, 50, 30);
    
    private ChessBoard board;
    private ChessAI ai;
    private JLabel statusLabel;
    private SoundManager soundManager;
    private Runnable onGameOver;
    
    private int selRow = -1, selCol = -1;
    private int lastFromRow = -1, lastFromCol, lastToRow, lastToCol;
    private boolean aiThinking = false;
    private int aiProgress = 0;
    private Timer aiTimer;
    private BufferedImage woodTexture;
    
    public enum GameMode { PVP, PVE }
    private GameMode mode = GameMode.PVE;
    private boolean playerIsRed = true;
    
    public ChessPanel(ChessBoard board, JLabel statusLabel) {
        this.board = board;
        this.statusLabel = statusLabel;
        this.ai = new ChessAI(ChessAI.Difficulty.MEDIUM);
        this.soundManager = new SoundManager();
        
        setPreferredSize(new Dimension(MARGIN * 2 + 8 * CELL, MARGIN * 2 + 9 * CELL + 50));
        generateWoodTexture();
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!aiThinking) handleClick(e.getX(), e.getY());
            }
        });
        
        aiTimer = new Timer(80, e -> { aiProgress = (aiProgress + 5) % 100; repaint(); });
        updateStatus();
    }
    
    private void generateWoodTexture() {
        int w = Math.max(1, getPreferredSize().width);
        int h = Math.max(1, getPreferredSize().height);
        woodTexture = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = woodTexture.createGraphics();
        g.setPaint(new GradientPaint(0, 0, WOOD_LIGHT, w, h, WOOD_DARK));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(200, 170, 120, 40));
        for (int i = 0; i < w; i += 3) {
            g.drawLine(i, 0, i + (int)(Math.sin(i * 0.02) * 20), h);
        }
        g.dispose();
    }
    
    public void setGameMode(GameMode m) { this.mode = m; reset(); }
    public void setDifficulty(ChessAI.Difficulty d) { ai.setDifficulty(d); if (mode == GameMode.PVE) reset(); }
    public void setPlayerSide(boolean red) { this.playerIsRed = red; if (mode == GameMode.PVE) reset(); }
    public void setSoundEnabled(boolean e) { soundManager.setEnabled(e); }
    public void setOnGameOver(Runnable r) { this.onGameOver = r; }
    
    private void handleClick(int x, int y) {
        if (board.isGameOver()) return;
        if (mode == GameMode.PVE && board.isRedTurn() != playerIsRed) return;
        
        int col = Math.round((float)(x - MARGIN) / CELL);
        int row = Math.round((float)(y - MARGIN) / CELL);
        if (row < 0 || row >= 10 || col < 0 || col >= 9) return;
        
        ChessPiece piece = board.getPiece(row, col);
        
        if (selRow == -1) {
            if (piece != null && piece.isRed() == board.isRedTurn()) {
                selRow = row; selCol = col;
                soundManager.playSelectSound();
                repaint();
            }
        } else if (row == selRow && col == selCol) {
            selRow = selCol = -1;
            repaint();
        } else {
            boolean capture = board.getPiece(row, col) != null;
            if (board.movePiece(selRow, selCol, row, col)) {
                lastFromRow = selRow; lastFromCol = selCol;
                lastToRow = row; lastToCol = col;
                selRow = selCol = -1;
                playSound(capture);
                repaint();
                updateStatus();
                
                if (board.isGameOver()) {
                    soundManager.playWinSound();
                    if (onGameOver != null) onGameOver.run();
                } else if (mode == GameMode.PVE) {
                    makeAIMove();
                }
            } else if (piece != null && piece.isRed() == board.isRedTurn()) {
                selRow = row; selCol = col;
                soundManager.playSelectSound();
                repaint();
            }
        }
    }
    
    /**
     * 悔棋
     */
    public void undo() {
        if (board.getHistorySize() == 0) return;
        
        // 如果AI正在思考，先停止
        if (aiThinking) {
            aiThinking = false;
            aiTimer.stop();
            selRow = selCol = -1;
        }
        
        // 人机模式下需要撤销两步（玩家+AI）
        if (mode == GameMode.PVE && board.getHistorySize() >= 2) {
            board.undo(); // 撤销AI的走法
        }
        board.undo(); // 撤销玩家的走法
        
        // 清除上一步标记
        lastFromRow = -1;
        
        updateStatus();
        repaint();
        
        // 如果悔棋后轮到AI走棋，立即触发AI
        if (mode == GameMode.PVE && board.isRedTurn() != playerIsRed) {
            makeAIMove();
        }
    }
    
    private void playSound(boolean capture) {
        if (capture) soundManager.playCaptureSound();
        else soundManager.playMoveSound();
    }
    
    private void makeAIMove() {
        if (board.isGameOver()) return;
        aiThinking = true;
        aiTimer.start();
        updateStatus();
        repaint();
        
        new SwingWorker<int[], Void>() {
            @Override
            protected int[] doInBackground() throws Exception {
                Thread.sleep(switch (ai.getDifficulty()) {
                    case EASY -> 400;
                    case HARD -> 1200;
                    default -> 700;
                });
                return ai.getNextMove(board);
            }
            
            @Override
            protected void done() {
                try {
                    int[] move = get();
                    if (move != null) {
                        selRow = move[0]; selCol = move[1];
                        repaint();
                        
                        new Timer(250, e -> {
                            boolean capture = board.getPiece(move[2], move[3]) != null;
                            lastFromRow = move[0]; lastFromCol = move[1];
                            lastToRow = move[2]; lastToCol = move[3];
                            board.movePiece(move[0], move[1], move[2], move[3]);
                            selRow = selCol = -1;
                            aiThinking = false;
                            aiTimer.stop();
                            playSound(capture);
                            updateStatus();
                            repaint();
                            if (board.isGameOver()) {
                                soundManager.playWinSound();
                                if (onGameOver != null) onGameOver.run();
                            }
                        }) {{ setRepeats(false); start(); }};
                    } else {
                        aiThinking = false;
                        aiTimer.stop();
                        updateStatus();
                        repaint();
                    }
                } catch (Exception ex) {
                    aiThinking = false;
                    aiTimer.stop();
                }
            }
        }.execute();
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
    
    public void reset() {
        board.reset();
        selRow = selCol = -1;
        lastFromRow = -1;
        aiThinking = false;
        aiTimer.stop();
        updateStatus();
        repaint();
        if (mode == GameMode.PVE && !playerIsRed) SwingUtilities.invokeLater(this::makeAIMove);
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        if (woodTexture != null) g2d.drawImage(woodTexture, 0, 0, null);
        drawBoard(g2d);
        drawPieces(g2d);
        if (selRow != -1) drawSelection(g2d, selRow, selCol);
        if (lastFromRow != -1) drawLastMove(g2d);
        if (aiThinking) drawAIThinking(g2d);
    }
    
    private void drawBoard(Graphics2D g) {
        int sx = MARGIN, sy = MARGIN;
        int ex = sx + 8 * CELL, ey = sy + 9 * CELL;
        
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRoundRect(sx - 4, sy - 4, 8 * CELL + 16, 9 * CELL + 16, 12, 12);
        g.setColor(new Color(100, 70, 40));
        g.setStroke(new BasicStroke(3));
        g.drawRoundRect(sx - 8, sy - 8, 8 * CELL + 16, 9 * CELL + 16, 10, 10);
        
        g.setColor(LINE_COLOR);
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 10; i++) g.drawLine(sx, sy + i * CELL, ex, sy + i * CELL);
        for (int j = 0; j < 9; j++) {
            int x = sx + j * CELL;
            g.drawLine(x, sy, x, sy + 4 * CELL);
            g.drawLine(x, sy + 5 * CELL, x, ey);
        }
        
        int lx = sx + 3 * CELL, rx = sx + 5 * CELL;
        g.setStroke(new BasicStroke(2));
        g.drawLine(lx, sy, rx, sy + 2 * CELL);
        g.drawLine(rx, sy, lx, sy + 2 * CELL);
        g.drawLine(lx, sy + 7 * CELL, rx, ey);
        g.drawLine(rx, sy + 7 * CELL, lx, ey);
        
        g.setFont(new Font("KaiTi", Font.BOLD, 42));
        g.setColor(new Color(120, 80, 50));
        FontMetrics fm = g.getFontMetrics();
        String chu = "楚河", han = "汉界";
        int cy = sy + 4 * CELL + CELL / 2 + fm.getAscent() / 3;
        g.drawString(chu, sx + 2 * CELL - fm.stringWidth(chu) / 2, cy);
        g.drawString(han, sx + 6 * CELL - fm.stringWidth(han) / 2, cy);
        
        // Position marks
        g.setStroke(new BasicStroke(1.8f));
        int[][] marks = {{2,1}, {2,7}, {7,1}, {7,7}, {3,0}, {3,2}, {3,4}, {3,6}, {3,8},
                        {6,0}, {6,2}, {6,4}, {6,6}, {6,8}};
        for (int[] m : marks) drawMark(g, m[0], m[1]);
    }
    
    private void drawMark(Graphics2D g, int r, int c) {
        int x = MARGIN + c * CELL, y = MARGIN + r * CELL;
        int len = 10, gap = 4;
        if (c > 0) {
            g.drawLine(x - len - gap, y - gap, x - gap, y - gap);
            g.drawLine(x - gap, y - len - gap, x - gap, y - gap);
        }
        if (c < 8) {
            g.drawLine(x + gap, y - gap, x + len + gap, y - gap);
            g.drawLine(x + gap, y - len - gap, x + gap, y - gap);
        }
        if (c > 0) {
            g.drawLine(x - len - gap, y + gap, x - gap, y + gap);
            g.drawLine(x - gap, y + gap, x - gap, y + len + gap);
        }
        if (c < 8) {
            g.drawLine(x + gap, y + gap, x + len + gap, y + gap);
            g.drawLine(x + gap, y + gap, x + gap, y + len + gap);
        }
    }
    
    private void drawPieces(Graphics2D g) {
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                ChessPiece p = board.getPiece(r, c);
                if (p != null) drawPiece(g, p, r, c);
            }
        }
    }
    
    private void drawPiece(Graphics2D g, ChessPiece p, int r, int c) {
        int cx = MARGIN + c * CELL, cy = MARGIN + r * CELL;
        boolean isRed = p.isRed();
        Color outer = isRed ? new Color(180, 40, 40) : new Color(30, 30, 30);
        Color inner = isRed ? new Color(220, 60, 60) : new Color(60, 60, 60);
        Color highlight = isRed ? new Color(255, 120, 120) : new Color(120, 120, 120);
        
        for (int i = 4; i >= 1; i--) {
            g.setColor(new Color(0, 0, 0, 25 - i * 4));
            g.fillOval(cx - RADIUS + i, cy - RADIUS + i + 2, RADIUS * 2, RADIUS * 2);
        }
        
        g.setColor(outer);
        g.fillOval(cx - RADIUS, cy - RADIUS, RADIUS * 2, RADIUS * 2);
        
        RadialGradientPaint grad = new RadialGradientPaint(cx - 5, cy - 5, RADIUS,
            new float[]{0f, 0.7f, 1f}, new Color[]{highlight, inner, outer});
        g.setPaint(grad);
        g.fillOval(cx - RADIUS + 2, cy - RADIUS + 2, RADIUS * 2 - 4, RADIUS * 2 - 4);
        
        g.setColor(new Color(255, 250, 240));
        g.fillOval(cx - RADIUS + 6, cy - RADIUS + 6, RADIUS * 2 - 12, RADIUS * 2 - 12);
        
        g.setFont(new Font("KaiTi", Font.BOLD, 30));
        String text = p.getName();
        FontMetrics fm = g.getFontMetrics();
        int tx = cx - fm.stringWidth(text) / 2, ty = cy + fm.getAscent() / 3;
        g.setColor(new Color(0, 0, 0, 30));
        g.drawString(text, tx + 1, ty + 1);
        g.setColor(isRed ? new Color(200, 30, 30) : new Color(20, 20, 20));
        g.drawString(text, tx, ty);
        
        g.setColor(new Color(255, 255, 255, 150));
        g.fillOval(cx - RADIUS + 8, cy - RADIUS + 8, 8, 6);
    }
    
    private void drawSelection(Graphics2D g, int r, int c) {
        int cx = MARGIN + c * CELL, cy = MARGIN + r * CELL;
        int size = RADIUS + 8;
        for (int i = 3; i >= 0; i--) {
            g.setColor(new Color(50, 200, 50, 100 - i * 20));
            g.drawOval(cx - size - i * 3, cy - size - i * 3, (size + i * 3) * 2, (size + i * 3) * 2);
        }
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(new Color(0, 180, 0));
        g.drawOval(cx - size, cy - size, size * 2, size * 2);
    }
    
    private void drawLastMove(Graphics2D g) {
        int fx = MARGIN + lastFromCol * CELL, fy = MARGIN + lastFromRow * CELL;
        int tx = MARGIN + lastToCol * CELL, ty = MARGIN + lastToRow * CELL;
        Color c = new Color(30, 144, 255);
        
        g.setColor(c);
        g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, new float[]{8, 6}, 0));
        g.drawLine(fx, fy, tx, ty);
        
        int r = 10;
        g.setStroke(new BasicStroke(2.5f));
        g.drawOval(fx - r, fy - r, r * 2, r * 2);
        g.setColor(new Color(30, 144, 255, 60));
        g.fillOval(fx - r, fy - r, r * 2, r * 2);
        
        g.setColor(c);
        r = 12;
        g.drawOval(tx - r, ty - r, r * 2, r * 2);
        g.setColor(new Color(30, 144, 255, 100));
        g.fillOval(tx - r, ty - r, r * 2, r * 2);
        
        double angle = Math.atan2(ty - fy, tx - fx);
        int ax = tx - (int)(Math.cos(angle) * RADIUS);
        int ay = ty - (int)(Math.sin(angle) * RADIUS);
        int s = 10;
        int[] xp = {ax, ax - (int)(Math.cos(angle - Math.PI/6) * s), ax - (int)(Math.cos(angle + Math.PI/6) * s)};
        int[] yp = {ay, ay - (int)(Math.sin(angle - Math.PI/6) * s), ay - (int)(Math.sin(angle + Math.PI/6) * s)};
        g.fillPolygon(xp, yp, 3);
    }
    
    private void drawAIThinking(Graphics2D g) {
        int w = 160, h = 36, x = (getWidth() - w) / 2, y = 15;
        g.setColor(new Color(40, 40, 40, 200));
        g.fillRoundRect(x, y, w, h, 18, 18);
        g.setColor(new Color(100, 100, 100));
        g.drawRoundRect(x, y, w, h, 18, 18);
        g.setColor(new Color(100, 200, 100));
        g.fillRoundRect(x + 8, y + h - 10, (int)((w - 16) * aiProgress / 100.0), 4, 2, 2);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SimSun", Font.BOLD, 14));
        String t = "AI 思考中...";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(t, x + (w - fm.stringWidth(t)) / 2, y + (h - 10) / 2 + fm.getAscent() / 2 - 2);
    }
}
