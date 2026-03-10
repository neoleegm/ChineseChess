/*
 * Decompiled with CFR 0.152.
 */
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

public class ChessPanel
extends JPanel {
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
    private int selRow = -1;
    private int selCol = -1;
    private int lastFromRow = -1;
    private int lastFromCol;
    private int lastToRow;
    private int lastToCol;
    private boolean aiThinking = false;
    private int aiProgress = 0;
    private Timer aiTimer;
    private BufferedImage woodTexture;
    private GameMode mode = GameMode.PVE;
    private boolean playerIsRed = true;

    public ChessPanel(ChessBoard chessBoard, JLabel jLabel) {
        this.board = chessBoard;
        this.statusLabel = jLabel;
        this.ai = new ChessAI(ChessAI.Difficulty.MEDIUM);
        this.soundManager = new SoundManager();
        this.setPreferredSize(new Dimension(630, 745));
        this.generateWoodTexture();
        this.addMouseListener(new MouseAdapter(this){
            final /* synthetic */ ChessPanel this$0;
            {
                ChessPanel chessPanel2 = chessPanel;
                Objects.requireNonNull(chessPanel2);
                this.this$0 = chessPanel2;
            }

            @Override
            public void mousePressed(MouseEvent mouseEvent) {
                if (!this.this$0.aiThinking) {
                    this.this$0.handleClick(mouseEvent.getX(), mouseEvent.getY());
                }
            }
        });
        this.aiTimer = new Timer(80, actionEvent -> {
            this.aiProgress = (this.aiProgress + 5) % 100;
            this.repaint();
        });
        this.updateStatus();
    }

    private void generateWoodTexture() {
        int n = Math.max(1, this.getPreferredSize().width);
        int n2 = Math.max(1, this.getPreferredSize().height);
        this.woodTexture = new BufferedImage(n, n2, 1);
        Graphics2D graphics2D = this.woodTexture.createGraphics();
        graphics2D.setPaint(new GradientPaint(0.0f, 0.0f, WOOD_LIGHT, n, n2, WOOD_DARK));
        graphics2D.fillRect(0, 0, n, n2);
        graphics2D.setColor(new Color(200, 170, 120, 40));
        for (int i = 0; i < n; i += 3) {
            graphics2D.drawLine(i, 0, i + (int)(Math.sin((double)i * 0.02) * 20.0), n2);
        }
        graphics2D.dispose();
    }

    public void setGameMode(GameMode gameMode) {
        this.mode = gameMode;
        this.reset();
    }

    public void setDifficulty(ChessAI.Difficulty difficulty) {
        this.ai.setDifficulty(difficulty);
        if (this.mode == GameMode.PVE) {
            this.reset();
        }
    }

    public void setPlayerSide(boolean bl) {
        this.playerIsRed = bl;
        if (this.mode == GameMode.PVE) {
            this.reset();
        }
    }

    public void setSoundEnabled(boolean bl) {
        this.soundManager.setEnabled(bl);
    }

    public void setOnGameOver(Runnable runnable) {
        this.onGameOver = runnable;
    }

    private void handleClick(int n, int n2) {
        if (this.board.isGameOver()) {
            return;
        }
        if (this.mode == GameMode.PVE && this.board.isRedTurn() != this.playerIsRed) {
            return;
        }
        int n3 = Math.round((float)(n - 55) / 65.0f);
        int n4 = Math.round((float)(n2 - 55) / 65.0f);
        if (n4 < 0 || n4 >= 10 || n3 < 0 || n3 >= 9) {
            return;
        }
        ChessPiece chessPiece = this.board.getPiece(n4, n3);
        if (this.selRow == -1) {
            if (chessPiece != null && chessPiece.isRed() == this.board.isRedTurn()) {
                this.selRow = n4;
                this.selCol = n3;
                this.soundManager.playSelectSound();
                this.repaint();
            }
        } else if (n4 == this.selRow && n3 == this.selCol) {
            this.selCol = -1;
            this.selRow = -1;
            this.repaint();
        } else {
            boolean bl = this.board.getPiece(n4, n3) != null;
            boolean bl2 = bl;
            if (this.board.movePiece(this.selRow, this.selCol, n4, n3)) {
                this.lastFromRow = this.selRow;
                this.lastFromCol = this.selCol;
                this.lastToRow = n4;
                this.lastToCol = n3;
                this.selCol = -1;
                this.selRow = -1;
                this.playSound(bl);
                this.repaint();
                this.updateStatus();
                if (this.board.isGameOver()) {
                    this.soundManager.playWinSound();
                    if (this.onGameOver != null) {
                        this.onGameOver.run();
                    }
                } else if (this.mode == GameMode.PVE) {
                    this.makeAIMove();
                }
            } else if (chessPiece != null && chessPiece.isRed() == this.board.isRedTurn()) {
                this.selRow = n4;
                this.selCol = n3;
                this.soundManager.playSelectSound();
                this.repaint();
            }
        }
    }

    public void undo() {
        if (this.board.getHistorySize() == 0) {
            return;
        }
        if (this.aiThinking) {
            this.aiThinking = false;
            this.aiTimer.stop();
            this.selCol = -1;
            this.selRow = -1;
        }
        if (this.mode == GameMode.PVE && this.board.getHistorySize() >= 2) {
            this.board.undo();
        }
        this.board.undo();
        this.lastFromRow = -1;
        this.updateStatus();
        this.repaint();
        if (this.mode == GameMode.PVE && this.board.isRedTurn() != this.playerIsRed) {
            this.makeAIMove();
        }
    }

    private void playSound(boolean bl) {
        if (bl) {
            this.soundManager.playCaptureSound();
        } else {
            this.soundManager.playMoveSound();
        }
    }

    private void makeAIMove() {
        if (this.board.isGameOver()) {
            return;
        }
        this.aiThinking = true;
        this.aiTimer.start();
        this.updateStatus();
        this.repaint();
        new SwingWorker<int[], Void>(this){
            final /* synthetic */ ChessPanel this$0;
            {
                ChessPanel chessPanel2 = chessPanel;
                Objects.requireNonNull(chessPanel2);
                this.this$0 = chessPanel2;
            }

            @Override
            protected int[] doInBackground() throws Exception {
                Thread.sleep(switch (this.this$0.ai.getDifficulty()) {
                    case ChessAI.Difficulty.EASY -> 400L;
                    case ChessAI.Difficulty.HARD -> 1200L;
                    default -> 700L;
                });
                return this.this$0.ai.getNextMove(this.this$0.board);
            }

            @Override
            protected void done() {
                try {
                    int[] nArray = (int[])this.get();
                    if (nArray != null) {
                        this.this$0.selRow = nArray[0];
                        this.this$0.selCol = nArray[1];
                        this.this$0.repaint();
                        new Timer(this, 250, actionEvent -> {
                            boolean bl = this.this$0.board.getPiece(nArray[2], nArray[3]) != null;
                            this.this$0.lastFromRow = nArray[0];
                            this.this$0.lastFromCol = nArray[1];
                            this.this$0.lastToRow = nArray[2];
                            this.this$0.lastToCol = nArray[3];
                            this.this$0.board.movePiece(nArray[0], nArray[1], nArray[2], nArray[3]);
                            this.this$0.selCol = -1;
                            this.this$0.selRow = -1;
                            this.this$0.aiThinking = false;
                            this.this$0.aiTimer.stop();
                            this.this$0.playSound(bl);
                            this.this$0.updateStatus();
                            this.this$0.repaint();
                            if (this.this$0.board.isGameOver()) {
                                this.this$0.soundManager.playWinSound();
                                if (this.this$0.onGameOver != null) {
                                    this.this$0.onGameOver.run();
                                }
                            }
                        }){
                            final /* synthetic */ 2 this$1;
                            {
                                2 v0 = var1_1;
                                Objects.requireNonNull(v0);
                                this.this$1 = v0;
                                super(n, actionListener);
                                this.setRepeats(false);
                                this.start();
                            }
                        };
                    } else {
                        this.this$0.aiThinking = false;
                        this.this$0.aiTimer.stop();
                        this.this$0.updateStatus();
                        this.this$0.repaint();
                    }
                }
                catch (Exception exception) {
                    this.this$0.aiThinking = false;
                    this.this$0.aiTimer.stop();
                }
            }
        }.execute();
    }

    private void updateStatus() {
        if (this.board.isGameOver()) {
            this.statusLabel.setText(this.board.getWinner());
        } else if (this.aiThinking) {
            this.statusLabel.setText("AI \u601d\u8003\u4e2d...");
        } else if (this.mode == GameMode.PVE) {
            this.statusLabel.setText(this.board.isRedTurn() == this.playerIsRed ? "\u4f60\u7684\u56de\u5408" : "AI \u601d\u8003\u4e2d...");
        } else {
            this.statusLabel.setText(this.board.isRedTurn() ? "\u7ea2\u65b9\u8d70\u68cb" : "\u9ed1\u65b9\u8d70\u68cb");
        }
    }

    public void reset() {
        this.board.reset();
        this.selCol = -1;
        this.selRow = -1;
        this.lastFromRow = -1;
        this.aiThinking = false;
        this.aiTimer.stop();
        this.updateStatus();
        this.repaint();
        if (this.mode == GameMode.PVE && !this.playerIsRed) {
            SwingUtilities.invokeLater(this::makeAIMove);
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D graphics2D = (Graphics2D)graphics;
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (this.woodTexture != null) {
            graphics2D.drawImage((Image)this.woodTexture, 0, 0, null);
        }
        this.drawBoard(graphics2D);
        this.drawPieces(graphics2D);
        if (this.selRow != -1) {
            this.drawSelection(graphics2D, this.selRow, this.selCol);
        }
        if (this.lastFromRow != -1) {
            this.drawLastMove(graphics2D);
        }
        if (this.aiThinking) {
            this.drawAIThinking(graphics2D);
        }
    }

    private void drawBoard(Graphics2D graphics2D) {
        int n;
        int n2 = 55;
        int n3 = 55;
        int n4 = n2 + 520;
        int n5 = n3 + 585;
        graphics2D.setColor(new Color(0, 0, 0, 60));
        graphics2D.fillRoundRect(n2 - 4, n3 - 4, 536, 601, 12, 12);
        graphics2D.setColor(new Color(100, 70, 40));
        graphics2D.setStroke(new BasicStroke(3.0f));
        graphics2D.drawRoundRect(n2 - 8, n3 - 8, 536, 601, 10, 10);
        graphics2D.setColor(LINE_COLOR);
        graphics2D.setStroke(new BasicStroke(2.2f, 1, 1));
        for (int i = 0; i < 10; ++i) {
            graphics2D.drawLine(n2, n3 + i * 65, n4, n3 + i * 65);
        }
        for (int i = 0; i < 9; ++i) {
            n = n2 + i * 65;
            graphics2D.drawLine(n, n3, n, n3 + 260);
            graphics2D.drawLine(n, n3 + 325, n, n5);
        }
        n = n2 + 195;
        int n6 = n2 + 325;
        graphics2D.setStroke(new BasicStroke(2.0f));
        graphics2D.drawLine(n, n3, n6, n3 + 130);
        graphics2D.drawLine(n6, n3, n, n3 + 130);
        graphics2D.drawLine(n, n3 + 455, n6, n5);
        graphics2D.drawLine(n6, n3 + 455, n, n5);
        graphics2D.setFont(new Font("KaiTi", 1, 42));
        graphics2D.setColor(new Color(120, 80, 50));
        FontMetrics fontMetrics = graphics2D.getFontMetrics();
        String string = "\u695a\u6cb3";
        String string2 = "\u6c49\u754c";
        int n7 = n3 + 260 + 32 + fontMetrics.getAscent() / 3;
        graphics2D.drawString(string, n2 + 130 - fontMetrics.stringWidth(string) / 2, n7);
        graphics2D.drawString(string2, n2 + 390 - fontMetrics.stringWidth(string2) / 2, n7);
        graphics2D.setStroke(new BasicStroke(1.8f));
        int[][] nArrayArray = new int[14][];
        nArrayArray[0] = new int[]{2, 1};
        nArrayArray[1] = new int[]{2, 7};
        nArrayArray[2] = new int[]{7, 1};
        nArrayArray[3] = new int[]{7, 7};
        int[] nArray = new int[2];
        nArray[0] = 3;
        nArrayArray[4] = nArray;
        nArrayArray[5] = new int[]{3, 2};
        nArrayArray[6] = new int[]{3, 4};
        nArrayArray[7] = new int[]{3, 6};
        nArrayArray[8] = new int[]{3, 8};
        int[] nArray2 = new int[2];
        nArray2[0] = 6;
        nArrayArray[9] = nArray2;
        nArrayArray[10] = new int[]{6, 2};
        nArrayArray[11] = new int[]{6, 4};
        nArrayArray[12] = new int[]{6, 6};
        nArrayArray[13] = new int[]{6, 8};
        int[][] nArrayArray2 = nArrayArray;
        int[][] nArrayArray3 = nArrayArray;
        int n8 = nArrayArray2.length;
        for (int i = 0; i < n8; ++i) {
            int[] nArray3 = nArrayArray3[i];
            this.drawMark(graphics2D, nArray3[0], nArray3[1]);
        }
    }

    private void drawMark(Graphics2D graphics2D, int n, int n2) {
        int n3 = 55 + n2 * 65;
        int n4 = 55 + n * 65;
        int n5 = 10;
        int n6 = 4;
        if (n2 > 0) {
            graphics2D.drawLine(n3 - n5 - n6, n4 - n6, n3 - n6, n4 - n6);
            graphics2D.drawLine(n3 - n6, n4 - n5 - n6, n3 - n6, n4 - n6);
        }
        if (n2 < 8) {
            graphics2D.drawLine(n3 + n6, n4 - n6, n3 + n5 + n6, n4 - n6);
            graphics2D.drawLine(n3 + n6, n4 - n5 - n6, n3 + n6, n4 - n6);
        }
        if (n2 > 0) {
            graphics2D.drawLine(n3 - n5 - n6, n4 + n6, n3 - n6, n4 + n6);
            graphics2D.drawLine(n3 - n6, n4 + n6, n3 - n6, n4 + n5 + n6);
        }
        if (n2 < 8) {
            graphics2D.drawLine(n3 + n6, n4 + n6, n3 + n5 + n6, n4 + n6);
            graphics2D.drawLine(n3 + n6, n4 + n6, n3 + n6, n4 + n5 + n6);
        }
    }

    private void drawPieces(Graphics2D graphics2D) {
        for (int i = 0; i < 10; ++i) {
            for (int j = 0; j < 9; ++j) {
                ChessPiece chessPiece = this.board.getPiece(i, j);
                if (chessPiece == null) continue;
                this.drawPiece(graphics2D, chessPiece, i, j);
            }
        }
    }

    private void drawPiece(Graphics2D graphics2D, ChessPiece chessPiece, int n, int n2) {
        int n3 = 55 + n2 * 65;
        int n4 = 55 + n * 65;
        boolean bl = chessPiece.isRed();
        Color color = bl ? new Color(180, 40, 40) : new Color(30, 30, 30);
        Color color2 = bl ? new Color(220, 60, 60) : new Color(60, 60, 60);
        Color color3 = bl ? new Color(255, 120, 120) : new Color(120, 120, 120);
        for (int i = 4; i >= 1; --i) {
            graphics2D.setColor(new Color(0, 0, 0, 25 - i * 4));
            graphics2D.fillOval(n3 - 28 + i, n4 - 28 + i + 2, 56, 56);
        }
        graphics2D.setColor(color);
        graphics2D.fillOval(n3 - 28, n4 - 28, 56, 56);
        RadialGradientPaint radialGradientPaint = new RadialGradientPaint(n3 - 5, (float)(n4 - 5), 28.0f, new float[]{0.0f, 0.7f, 1.0f}, new Color[]{color3, color2, color});
        graphics2D.setPaint(radialGradientPaint);
        graphics2D.fillOval(n3 - 28 + 2, n4 - 28 + 2, 52, 52);
        graphics2D.setColor(new Color(255, 250, 240));
        graphics2D.fillOval(n3 - 28 + 6, n4 - 28 + 6, 44, 44);
        graphics2D.setFont(new Font("KaiTi", 1, 30));
        String string = chessPiece.getName();
        FontMetrics fontMetrics = graphics2D.getFontMetrics();
        int n5 = n3 - fontMetrics.stringWidth(string) / 2;
        int n6 = n4 + fontMetrics.getAscent() / 3;
        graphics2D.setColor(new Color(0, 0, 0, 30));
        graphics2D.drawString(string, n5 + 1, n6 + 1);
        graphics2D.setColor(bl ? new Color(200, 30, 30) : new Color(20, 20, 20));
        graphics2D.drawString(string, n5, n6);
        graphics2D.setColor(new Color(255, 255, 255, 150));
        graphics2D.fillOval(n3 - 28 + 8, n4 - 28 + 8, 8, 6);
    }

    private void drawSelection(Graphics2D graphics2D, int n, int n2) {
        int n3 = 55 + n2 * 65;
        int n4 = 55 + n * 65;
        int n5 = 36;
        for (int i = 3; i >= 0; --i) {
            graphics2D.setColor(new Color(50, 200, 50, 100 - i * 20));
            graphics2D.drawOval(n3 - n5 - i * 3, n4 - n5 - i * 3, (n5 + i * 3) * 2, (n5 + i * 3) * 2);
        }
        graphics2D.setStroke(new BasicStroke(2.5f));
        graphics2D.setColor(new Color(0, 180, 0));
        graphics2D.drawOval(n3 - n5, n4 - n5, n5 * 2, n5 * 2);
    }

    private void drawLastMove(Graphics2D graphics2D) {
        int n = 55 + this.lastFromCol * 65;
        int n2 = 55 + this.lastFromRow * 65;
        int n3 = 55 + this.lastToCol * 65;
        int n4 = 55 + this.lastToRow * 65;
        Color color = new Color(30, 144, 255);
        graphics2D.setColor(color);
        graphics2D.setStroke(new BasicStroke(3.0f, 1, 1, 10.0f, new float[]{8.0f, 6.0f}, 0.0f));
        graphics2D.drawLine(n, n2, n3, n4);
        int n5 = 10;
        graphics2D.setStroke(new BasicStroke(2.5f));
        graphics2D.drawOval(n - n5, n2 - n5, n5 * 2, n5 * 2);
        graphics2D.setColor(new Color(30, 144, 255, 60));
        graphics2D.fillOval(n - n5, n2 - n5, n5 * 2, n5 * 2);
        graphics2D.setColor(color);
        n5 = 12;
        graphics2D.drawOval(n3 - n5, n4 - n5, n5 * 2, n5 * 2);
        graphics2D.setColor(new Color(30, 144, 255, 100));
        graphics2D.fillOval(n3 - n5, n4 - n5, n5 * 2, n5 * 2);
        double d = Math.atan2(n4 - n2, n3 - n);
        int n6 = n3 - (int)(Math.cos(d) * 28.0);
        int n7 = n4 - (int)(Math.sin(d) * 28.0);
        int n8 = 10;
        int[] nArray = new int[]{n6, n6 - (int)(Math.cos(d - 0.5235987755982988) * (double)n8), n6 - (int)(Math.cos(d + 0.5235987755982988) * (double)n8)};
        int[] nArray2 = new int[]{n7, n7 - (int)(Math.sin(d - 0.5235987755982988) * (double)n8), n7 - (int)(Math.sin(d + 0.5235987755982988) * (double)n8)};
        graphics2D.fillPolygon(nArray, nArray2, 3);
    }

    private void drawAIThinking(Graphics2D graphics2D) {
        int n = 160;
        int n2 = 36;
        int n3 = (this.getWidth() - n) / 2;
        int n4 = 15;
        graphics2D.setColor(new Color(40, 40, 40, 200));
        graphics2D.fillRoundRect(n3, n4, n, n2, 18, 18);
        graphics2D.setColor(new Color(100, 100, 100));
        graphics2D.drawRoundRect(n3, n4, n, n2, 18, 18);
        graphics2D.setColor(new Color(100, 200, 100));
        graphics2D.fillRoundRect(n3 + 8, n4 + n2 - 10, (int)((double)((n - 16) * this.aiProgress) / 100.0), 4, 2, 2);
        graphics2D.setColor(Color.WHITE);
        graphics2D.setFont(new Font("SimSun", 1, 14));
        String string = "AI \u601d\u8003\u4e2d...";
        FontMetrics fontMetrics = graphics2D.getFontMetrics();
        graphics2D.drawString(string, n3 + (n - fontMetrics.stringWidth(string)) / 2, n4 + (n2 - 10) / 2 + fontMetrics.getAscent() / 2 - 2);
    }

    public static enum GameMode {
        PVP,
        PVE;

    }
}
