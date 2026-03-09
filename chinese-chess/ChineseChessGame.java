import javax.swing.*;
import java.awt.*;

/**
 * 中国象棋游戏主类
 */
public class ChineseChessGame extends JFrame {
    private ChessBoard board;
    private ChessPanel chessPanel;
    private JLabel statusLabel;
    
    public ChineseChessGame() {
        setTitle("中国象棋");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        
        // 初始化棋盘
        board = new ChessBoard();
        
        // 创建状态栏
        statusLabel = new JLabel("红方走棋", JLabel.CENTER);
        statusLabel.setFont(new Font("SimSun", Font.BOLD, 18));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        
        // 创建棋盘面板
        chessPanel = new ChessPanel(board, statusLabel);
        chessPanel.setOnGameOver(() -> {
            JOptionPane.showMessageDialog(this, board.getWinner(), "游戏结束", JOptionPane.INFORMATION_MESSAGE);
        });
        
        // 创建按钮面板
        JPanel buttonPanel = new JPanel();
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        
        JButton newGameButton = new JButton("新游戏");
        newGameButton.setFont(new Font("SimSun", Font.PLAIN, 14));
        newGameButton.addActionListener(e -> {
            board.reset();
            chessPanel.reset();
        });
        
        JButton rulesButton = new JButton("规则说明");
        rulesButton.setFont(new Font("SimSun", Font.PLAIN, 14));
        rulesButton.addActionListener(e -> showRules());
        
        JButton exitButton = new JButton("退出");
        exitButton.setFont(new Font("SimSun", Font.PLAIN, 14));
        exitButton.addActionListener(e -> System.exit(0));
        
        buttonPanel.add(newGameButton);
        buttonPanel.add(rulesButton);
        buttonPanel.add(exitButton);
        
        // 组装界面
        setLayout(new BorderLayout());
        add(chessPanel, BorderLayout.CENTER);
        
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(statusLabel, BorderLayout.NORTH);
        southPanel.add(buttonPanel, BorderLayout.SOUTH);
        add(southPanel, BorderLayout.SOUTH);
        
        pack();
        setLocationRelativeTo(null);  // 居中显示
    }
    
    /**
     * 显示规则说明
     */
    private void showRules() {
        String rules = """
            中国象棋规则：
            
            【棋子走法】
            • 帅/将：在九宫内一格一格走，不能出九宫
            • 仕/士：在九宫内斜线走一格
            • 相/象：走田字，不能过河，象眼不能被塞
            • 傌/马：走日字，马腿不能被蹩
            • 俥/车：直线走，不能越过棋子
            • 炮/砲：直线走，吃子时必须隔一个棋子
            • 兵/卒：向前走，过河后可以横走
            
            【胜负判定】
            吃掉对方的帅/将即获胜！
            
            【操作说明】
            点击棋子选中，再点击目标位置移动。
            """;
        
        JTextArea textArea = new JTextArea(rules);
        textArea.setFont(new Font("SimSun", Font.PLAIN, 14));
        textArea.setEditable(false);
        textArea.setBackground(new Color(240, 240, 240));
        textArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(400, 350));
        
        JOptionPane.showMessageDialog(this, scrollPane, "游戏规则", JOptionPane.INFORMATION_MESSAGE);
    }
    
    public static void main(String[] args) {
        // 设置外观
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // 启动游戏
        SwingUtilities.invokeLater(() -> {
            ChineseChessGame game = new ChineseChessGame();
            game.setVisible(true);
        });
    }
}
