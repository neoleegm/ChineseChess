import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 棋盘面板类
 * 负责绘制棋盘和棋子，处理用户交互
 *
 * 线程模型：对局棋盘只由事件分发线程（EDT）修改；
 * AI 后台线程通过 ChessAI 内部的克隆棋盘计算，不直接读写对局棋盘。
 * 终局/将军状态在每次走子后缓存，绘制与悬停不再重复全量计算。
 */
public class ChessPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    // 棋盘参数
    private static final int CELL_SIZE = 60;      // 格子大小
    private static final int MARGIN = 50;         // 边距
    private static final int PIECE_RADIUS = 26;   // 棋子半径
    private static final int BOARD_WIDTH = CELL_SIZE * 8;
    private static final int BOARD_HEIGHT = CELL_SIZE * 9;

    // 走子动画参数
    private static final int ANIM_FRAMES = 8;
    private static final int ANIM_INTERVAL_MS = 18;

    // 颜色定义
    private static final Color BOARD_COLOR_DARK = new Color(210, 170, 100);
    private static final Color BOARD_COLOR_LIGHT = new Color(245, 215, 150);
    private static final Color LINE_COLOR = new Color(80, 50, 20);
    private static final Color SELECT_COLOR = new Color(80, 180, 80, 180);
    private static final Color HOVER_COLOR = new Color(120, 200, 120, 120);
    private static final Color LAST_MOVE_COLOR = new Color(20, 120, 40, 130);
    private static final Color RED_PIECE = new Color(200, 40, 40);
    private static final Color BLACK_PIECE = new Color(30, 30, 30);
    private static final Color PIECE_BG = new Color(250, 230, 190);
    private static final Color AI_THINKING_COLOR = new Color(100, 150, 255, 80);
    private static final Color MOVE_HINT_COLOR = new Color(60, 160, 60, 180);
    private static final Color CAPTURE_HINT_COLOR = new Color(200, 60, 60, 200);
    private static final Color CHECK_FLASH_COLOR = new Color(255, 0, 0, 180);
    private static final Color HINT_ARROW_COLOR = new Color(60, 100, 220, 190);

    private final ChessBoard board;
    private final ChessAI ai;
    private final JLabel statusLabel;
    private final SoundManager soundManager;

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
    private final List<Move> hoverMoves = new ArrayList<>();

    // 将军闪烁
    private boolean checkFlashOn = false;
    private final Timer checkFlashTimer;

    // 终局/将军状态缓存（每次走子、悔棋、重置后刷新）
    private boolean gameOverCache = false;
    private boolean redInCheckCache = false;
    private boolean blackInCheckCache = false;

    // 走子动画状态
    private ChessPiece animPiece;
    private int animFromRow, animFromCol, animToRow, animToCol;
    private int animFrame;
    private boolean animating = false;
    private Timer animTimer;

    // AI 后台任务（代际计数防止重置后陈旧结果落子）
    private SwingWorker<int[], Void> aiWorker;
    private int aiGeneration = 0;

    // 提示功能：独立的高手引擎（始终困难档，与对局难度无关）
    private final ChessAI hintAi = new ChessAI(ChessAI.Difficulty.HARD);
    private SwingWorker<int[], Void> hintWorker;
    private int hintGeneration = 0;
    private boolean hintThinking = false;
    private int hintFromRow = -1, hintFromCol = -1, hintToRow = -1, hintToCol = -1;
    private String hintNotation = "";

    // 着法记谱（每手一条，偶数下标为红方）
    private final List<String> moveNotations = new ArrayList<>();

    // 对局事件监听（通知主窗口刷新着法列表与被吃子展示）
    public interface GameEventListener {
        void onGameStateChanged();
    }
    private GameEventListener gameEventListener;

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

        refreshBoardStateCache();
    }

    /**
     * 逻辑字体自动回退到系统中文字体，避免硬编码字体名在各平台缺字
     */
    static Font uiFont(int style, int size) {
        return new Font(Font.SANS_SERIF, style, size);
    }

    public void setGameEventListener(GameEventListener listener) {
        this.gameEventListener = listener;
    }

    public void setGameMode(GameMode mode) {
        this.mode = mode;
        reset();
    }

    public GameMode getGameMode() {
        return mode;
    }

    public void setDifficulty(ChessAI.Difficulty difficulty) {
        ai.setDifficulty(difficulty);
        if (mode == GameMode.PVE) reset();
    }

    public ChessAI.Difficulty getDifficulty() {
        return ai.getDifficulty();
    }

    public void setPikafishEnginePath(String path) {
        ai.setPikafishEnginePath(path);
        hintAi.setPikafishEnginePath(path);
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

    public boolean isPlayerRed() {
        return playerIsRed;
    }

    public void setSoundEnabled(boolean enabled) {
        soundManager.setEnabled(enabled);
    }

    public boolean isSoundEnabled() {
        return soundManager.isEnabled();
    }

    /**
     * 销毁外部引擎进程（游戏退出时调用）
     */
    public void shutdownAI() {
        cancelAI();
        cancelHint();
        ai.shutdown();
        hintAi.shutdown();
    }

    /**
     * 着法记谱列表（每手一条中文记谱，偶数下标为红方）
     */
    public List<String> getMoveNotations() {
        return new ArrayList<>(moveNotations);
    }

    /**
     * 对局是否进行中（有走子历史且未终局），用于重置前确认
     */
    public boolean isGameInProgress() {
        return board.getHistorySize() > 0 && !gameOverCache;
    }

    /**
     * 刷新终局/将军状态缓存。棋盘每次变化后必须调用一次（EDT）。
     */
    private void refreshBoardStateCache() {
        gameOverCache = board.isGameOver();
        redInCheckCache = !gameOverCache && board.isKingAttacked(true);
        blackInCheckCache = !gameOverCache && board.isKingAttacked(false);
    }

    private boolean sideToMoveInCheck() {
        return board.isRedTurn() ? redInCheckCache : blackInCheckCache;
    }

    private void fireGameEvent() {
        if (gameEventListener != null) {
            gameEventListener.onGameStateChanged();
        }
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
        if (hoverRow != -1 && !gameOverCache && !aiThinking && !animating && !hintThinking) {
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
        if (aiThinking || animating || hintThinking) return;
        if (gameOverCache) return;

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
                selectPiece(row, col);
            }
        } else if (row == selectedRow && col == selectedCol) {
            // 取消选择
            clearSelection();
            repaint();
        } else {
            // 尝试移动
            if (board.isLegalMove(selectedRow, selectedCol, row, col)) {
                applyMove(selectedRow, selectedCol, row, col, false);
            } else if (piece != null && piece.isRed() == board.isRedTurn()) {
                // 换选其他棋子
                selectPiece(row, col);
            }
        }
    }

    private void selectPiece(int row, int col) {
        selectedRow = row;
        selectedCol = col;
        soundManager.playSelectSound();
        repaint();
        SwingUtilities.invokeLater(() -> {
            updateHoverMoves();
            repaint();
        });
    }

    private void clearSelection() {
        selectedRow = -1;
        selectedCol = -1;
        hoverMoves.clear();
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

    /**
     * 执行一手棋（人类或 AI 共用）：记谱、音效、动画、终局判定、触发 AI。
     * 必须在 EDT 调用。
     */
    private void applyMove(int fromRow, int fromCol, int toRow, int toCol, boolean byAI) {
        // 记谱与走法描述需要走子前的棋盘状态，先于 movePiece 计算
        String notation = MoveNotation.toChineseNotation(board, new Move(fromRow, fromCol, toRow, toCol));
        ChessPiece movedPiece = board.getPiece(fromRow, fromCol);
        ChessPiece capturedPiece = board.getPiece(toRow, toCol);

        if (!board.movePiece(fromRow, fromCol, toRow, toCol)) {
            if (byAI) {
                lastAiMessage = "AI 返回非法走法，已跳过";
                updateStatus();
            }
            return;
        }

        clearHint();
        moveNotations.add(notation);

        lastFromRow = fromRow;
        lastFromCol = fromCol;
        lastToRow = toRow;
        lastToCol = toCol;
        clearSelection();

        soundManager.playMoveSound(capturedPiece != null);
        if (byAI) {
            // 走法描述：中文记谱 + 坐标（与棋盘边缘坐标对应）
            String desc = notation + " (" + fromRow + "," + fromCol + ")→(" + toRow + "," + toCol + ")";
            if (capturedPiece != null) {
                desc += " 吃" + capturedPiece.getName();
            }
            lastAiMessage = desc + " | " + ai.getLastEngineMessage();
        }

        refreshBoardStateCache();
        fireGameEvent();
        updateStatus();
        repaint();

        // 将军提示音（终局时由胜利音效接管）
        if (!gameOverCache && sideToMoveInCheck()) {
            soundManager.playCheckSound();
        }

        startMoveAnimation(movedPiece, fromRow, fromCol, toRow, toCol, () -> {
            if (gameOverCache) {
                soundManager.playWinSound();
                showGameOverDialog();
            } else if (mode == GameMode.PVE && board.isRedTurn() != playerIsRed) {
                makeAIMove();
            }
        });
    }

    /**
     * 走子动画：棋子从起点滑到终点，动画结束后执行回调（终局判定/触发 AI）。
     * 动画期间锁定棋盘输入，避免状态交错。
     */
    private void startMoveAnimation(ChessPiece piece, int fromRow, int fromCol, int toRow, int toCol, Runnable onDone) {
        animPiece = piece;
        animFromRow = fromRow;
        animFromCol = fromCol;
        animToRow = toRow;
        animToCol = toCol;
        animFrame = 0;
        animating = true;

        animTimer = new Timer(ANIM_INTERVAL_MS, null);
        animTimer.addActionListener(e -> {
            animFrame++;
            if (animFrame >= ANIM_FRAMES) {
                stopAnimation();
                repaint();
                onDone.run();
            } else {
                repaint();
            }
        });
        animTimer.start();
    }

    /**
     * 停止动画（重置/对局被打断时调用，不执行动画回调）
     */
    private void stopAnimation() {
        if (animTimer != null) {
            animTimer.stop();
            animTimer = null;
        }
        animating = false;
        animPiece = null;
    }

    private void makeAIMove() {
        aiThinking = true;
        final int generation = aiGeneration;
        updateStatus();
        repaint();

        aiWorker = new SwingWorker<>() {
            @Override
            protected int[] doInBackground() throws Exception {
                return ai.getNextMove(board, !playerIsRed);
            }

            @Override
            protected void done() {
                // 代际不符或已取消：说明期间发生了重置/切换，丢弃陈旧结果
                if (generation != aiGeneration || isCancelled()) {
                    return;
                }
                try {
                    int[] move = get();
                    if (move != null) {
                        applyMove(move[0], move[1], move[2], move[3], true);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (generation == aiGeneration) {
                        aiThinking = false;
                        updateStatus();
                        repaint();
                    }
                }
            }
        };
        aiWorker.execute();
    }

    /**
     * 取消进行中的 AI 计算并使代际递增，陈旧结果将被丢弃
     */
    private void cancelAI() {
        aiGeneration++;
        if (aiWorker != null) {
            aiWorker.cancel(true);
            aiWorker = null;
        }
        aiThinking = false;
    }

    /**
     * 高手提示：后台计算当前局面的推荐着法，并在棋盘上绘制箭头。
     * 提示引擎始终为困难档（配置了 Pikafish 则优先 Pikafish），与对局难度无关。
     */
    public void requestHint() {
        if (mode != GameMode.PVE) return;
        if (aiThinking || animating || hintThinking || gameOverCache) return;
        if (board.isRedTurn() != playerIsRed) return;

        clearHint();
        hintThinking = true;
        final int generation = ++hintGeneration;
        updateStatus();
        repaint();

        hintWorker = new SwingWorker<>() {
            @Override
            protected int[] doInBackground() throws Exception {
                return hintAi.getNextMove(board, playerIsRed);
            }

            @Override
            protected void done() {
                // 代际不符或已取消：说明期间发生了重置/切换，丢弃结果
                if (generation != hintGeneration || isCancelled()) {
                    return;
                }
                try {
                    int[] move = get();
                    if (move != null) {
                        hintFromRow = move[0];
                        hintFromCol = move[1];
                        hintToRow = move[2];
                        hintToCol = move[3];
                        // 提示文案：中文记谱 + 坐标（与棋盘边缘坐标对应）
                        hintNotation = MoveNotation.toChineseNotation(
                            board, new Move(move[0], move[1], move[2], move[3]))
                            + " (" + move[0] + "," + move[1] + ")→(" + move[2] + "," + move[3] + ")";
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (generation == hintGeneration) {
                        hintThinking = false;
                        updateStatus();
                        repaint();
                    }
                }
            }
        };
        hintWorker.execute();
    }

    private void clearHint() {
        hintFromRow = -1;
        hintNotation = "";
    }

    /**
     * 取消进行中的提示计算并清除提示箭头
     */
    private void cancelHint() {
        hintGeneration++;
        if (hintWorker != null) {
            hintWorker.cancel(true);
            hintWorker = null;
        }
        hintThinking = false;
        clearHint();
    }

    public void undo() {
        if (board.getHistorySize() == 0) return;
        if (aiThinking || animating || hintThinking) return;

        // 人机模式下需要悔两步
        board.undo();
        moveNotations.remove(moveNotations.size() - 1);
        if (mode == GameMode.PVE && board.getHistorySize() > 0) {
            board.undo();
            moveNotations.remove(moveNotations.size() - 1);
        }

        // 清除最后一步标记
        lastFromRow = -1;
        lastFromCol = -1;
        lastToRow = -1;
        lastToCol = -1;
        clearSelection();
        clearHint();

        refreshBoardStateCache();
        fireGameEvent();
        updateStatus();
        repaint();

        // 执黑悔到开局等情形：悔棋后轮到 AI，需要重新触发
        if (mode == GameMode.PVE && !gameOverCache && board.isRedTurn() != playerIsRed) {
            makeAIMove();
        }
    }

    public void reset() {
        cancelAI();
        cancelHint();
        stopAnimation();
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
        moveNotations.clear();
        lastAiMessage = "";

        refreshBoardStateCache();
        fireGameEvent();
        updateStatus();
        repaint();

        // 如果玩家选择执黑，AI先走
        if (mode == GameMode.PVE && !playerIsRed) {
            makeAIMove();
        }
    }

    private void showGameOverDialog() {
        Object[] options = {"再来一局", "关闭"};
        int choice = JOptionPane.showOptionDialog(this,
            board.getWinner(),
            "对局结束",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.INFORMATION_MESSAGE,
            null,
            options,
            options[0]);
        if (choice == JOptionPane.YES_OPTION) {
            reset();
        }
    }

    private void updateStatus() {
        if (gameOverCache) {
            statusLabel.setText(board.getWinner());
        } else if (aiThinking) {
            statusLabel.setText("AI 思考中...");
        } else if (hintThinking) {
            statusLabel.setText("提示计算中...");
        } else if (mode == GameMode.PVE) {
            String check = sideToMoveInCheck() ? "将军！" : "";
            if (board.isRedTurn() == playerIsRed) {
                if (hintFromRow != -1) {
                    statusLabel.setText(check + "提示：" + hintNotation);
                } else {
                    statusLabel.setText(check + (lastAiMessage.isBlank() ? "你的回合" : "你的回合 - " + summarizeEngineMessage(lastAiMessage)));
                }
            } else {
                statusLabel.setText("AI 思考中...");
            }
        } else {
            String check = sideToMoveInCheck() ? "将军！" : "";
            statusLabel.setText(check + (board.isRedTurn() ? "红方走棋" : "黑方走棋"));
        }
    }

    private String summarizeEngineMessage(String message) {
        if (message.length() <= 40) {
            return message;
        }
        return message.substring(0, 40) + "...";
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 绘制棋盘背景
        drawBoard(g2d);

        // 绘制边缘坐标（行号/列号，与状态栏走法坐标对应）
        drawCoordinates(g2d);

        // 绘制最后一步标记
        if (lastFromRow != -1) {
            drawHighlight(g2d, lastFromRow, lastFromCol, LAST_MOVE_COLOR);
            drawHighlight(g2d, lastToRow, lastToCol, LAST_MOVE_COLOR);
        }

        // 绘制合法走法提示
        drawMoveHints(g2d);

        // 绘制悬停高亮
        if (hoverRow != -1 && !aiThinking && !animating && !hintThinking && !gameOverCache) {
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

        // 绘制棋子（动画中的棋子单独按插值位置绘制）
        for (ChessPiece piece : board.getPieces()) {
            if (piece == animPiece) continue;
            drawPieceAt(g2d, piece, MARGIN + piece.getCol() * CELL_SIZE, MARGIN + piece.getRow() * CELL_SIZE);
        }
        if (animPiece != null) {
            double t = (double) animFrame / ANIM_FRAMES;
            double colF = animFromCol + (animToCol - animFromCol) * t;
            double rowF = animFromRow + (animToRow - animFromRow) * t;
            drawPieceAt(g2d, animPiece,
                MARGIN + (int) Math.round(colF * CELL_SIZE),
                MARGIN + (int) Math.round(rowF * CELL_SIZE));
        }

        // 绘制提示箭头（蓝色，区别于走法提示与最后一步高亮）
        if (hintFromRow != -1) {
            drawHintArrow(g2d);
        }

        // 绘制将军闪烁提示
        if (!gameOverCache && checkFlashOn) {
            if (redInCheckCache) drawCheckFlash(g2d, true);
            if (blackInCheckCache) drawCheckFlash(g2d, false);
        }

        // 绘制AI思考提示
        if (aiThinking) {
            g2d.setColor(AI_THINKING_COLOR);
            g2d.fillRect(0, 0, getWidth(), getHeight());
            g2d.setColor(Color.WHITE);
            g2d.setFont(uiFont(Font.BOLD, 28));
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
        g2d.setFont(uiFont(Font.BOLD, 26));
        g2d.setColor(LINE_COLOR);
        FontMetrics fm = g2d.getFontMetrics();
        String chuHan = "楚 河    汉 界";
        int textWidth = fm.stringWidth(chuHan);
        int x = MARGIN + (BOARD_WIDTH - textWidth) / 2;
        int y = MARGIN + 4 * CELL_SIZE + CELL_SIZE / 2 + fm.getAscent() / 2 - 4;
        g2d.drawString(chuHan, x, y);
    }

    /**
     * 绘制棋盘边缘坐标：左侧行号 0-9、底部列号 0-8，
     * 与状态栏走法描述中的 (行,列) 坐标一一对应
     */
    private void drawCoordinates(Graphics2D g2d) {
        g2d.setFont(uiFont(Font.PLAIN, 13));
        g2d.setColor(new Color(LINE_COLOR.getRed(), LINE_COLOR.getGreen(), LINE_COLOR.getBlue(), 200));
        FontMetrics fm = g2d.getFontMetrics();

        // 左侧行号
        for (int row = 0; row < ChessBoard.ROWS; row++) {
            String label = String.valueOf(row);
            int y = MARGIN + row * CELL_SIZE;
            g2d.drawString(label,
                MARGIN - 16 - fm.stringWidth(label) / 2,
                y + fm.getAscent() / 2 - 2);
        }

        // 底部列号
        for (int col = 0; col < ChessBoard.COLS; col++) {
            String label = String.valueOf(col);
            int x = MARGIN + col * CELL_SIZE;
            g2d.drawString(label,
                x - fm.stringWidth(label) / 2,
                MARGIN + BOARD_HEIGHT + 20);
        }
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

    /**
     * 绘制高手提示：起点与终点蓝色圆环 + 半透明箭头连线
     */
    private void drawHintArrow(Graphics2D g2d) {
        int x1 = MARGIN + hintFromCol * CELL_SIZE;
        int y1 = MARGIN + hintFromRow * CELL_SIZE;
        int x2 = MARGIN + hintToCol * CELL_SIZE;
        int y2 = MARGIN + hintToRow * CELL_SIZE;

        // 起止圆环
        g2d.setColor(HINT_ARROW_COLOR);
        g2d.setStroke(new BasicStroke(3.5f));
        int r = PIECE_RADIUS + 6;
        g2d.drawOval(x1 - r, y1 - r, r * 2, r * 2);
        g2d.drawOval(x2 - r, y2 - r, r * 2, r * 2);

        // 箭头连线（两端各缩一点，避免压住圆环）
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        if (len < 1) return;
        double ux = dx / len;
        double uy = dy / len;
        int sx = (int) Math.round(x1 + ux * (PIECE_RADIUS + 8));
        int sy = (int) Math.round(y1 + uy * (PIECE_RADIUS + 8));
        int ex = (int) Math.round(x2 - ux * (PIECE_RADIUS + 10));
        int ey = (int) Math.round(y2 - uy * (PIECE_RADIUS + 10));
        g2d.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine(sx, sy, ex, ey);

        // 箭头头部
        double angle = Math.atan2(dy, dx);
        int headLen = 14;
        double a1 = angle + Math.toRadians(150);
        double a2 = angle - Math.toRadians(150);
        g2d.drawLine(ex, ey,
            (int) Math.round(ex + headLen * Math.cos(a1)),
            (int) Math.round(ey + headLen * Math.sin(a1)));
        g2d.drawLine(ex, ey,
            (int) Math.round(ex + headLen * Math.cos(a2)),
            (int) Math.round(ey + headLen * Math.sin(a2)));
    }

    private void drawPieceAt(Graphics2D g2d, ChessPiece piece, int x, int y) {
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
        g2d.setFont(uiFont(Font.BOLD, 26));
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
