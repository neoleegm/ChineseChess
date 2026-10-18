package xiangqi.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

/**
 * 规则说明与关于对话框。文案集中在这里，主窗口不必被长字符串撑大。
 */
final class Dialogs {

    private static final String RULES = """
            【棋子走法】
            帅 / 将    九宫内横竖一格，不能与对方将帅同列无遮挡（将帅照面）
            仕 / 士    九宫内斜线一格
            相 / 象    走田字，不能过河，塞象眼则不能走
            傌 / 马    走日字，蹩马腿则不能走
            俥 / 车    横竖直线，不能越子
            炮 / 砲    横竖直线，吃子必须隔一个棋子（炮架）
            兵 / 卒    过河前只能前进；过河后可前进或左右横移，永不后退

            【胜负判定】
            将死对方        对方将帅被攻击且无任何合法应着
            困毙            对方无子可走（未被将军也算负，中国象棋规则）
            长将作负        同一局面第三次出现，且循环中一方每步都在将军
            三次重复        同一局面第三次出现且不构成长将，判和

            【操作】
            点选棋子后点目标格，或直接按住棋子拖到目标格
            点击右侧着法表中的任意一手，可回看当时局面

            【快捷键】
            U  悔棋          Y  重做
            R  重新开始      H  提示（人机对战）
            F  翻转棋盘      Ctrl+S  保存棋谱
            Q  退出
            """;

    private Dialogs() {
    }

    static void showRules(Component parent, Theme theme) {
        JTextArea area = new JTextArea(RULES);
        area.setEditable(false);
        area.setFont(Ui.font(Font.PLAIN, 13));
        area.setLineWrap(false);
        area.setBackground(theme.recordBg);
        area.setForeground(theme.panelText);
        area.setBorder(new EmptyBorder(12, 14, 12, 14));

        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createLineBorder(theme.panelBorder));
        scroll.setPreferredSize(new Dimension(520, 460));

        JOptionPane.showMessageDialog(parent, scroll, "中国象棋规则", JOptionPane.PLAIN_MESSAGE);
    }

    static void showAbout(Component parent, Theme theme, String engineInfo) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(theme.panelBg);

        JLabel title = new JLabel("中国象棋 v4.0");
        title.setFont(Ui.font(Font.BOLD, 20));
        title.setForeground(theme.panelText);
        panel.add(title, BorderLayout.NORTH);

        JTextArea area = new JTextArea("""
                Java Swing 单机中国象棋

                引擎：内置 alpha-beta 搜索（可选 Pikafish 外部引擎）
                当前外部引擎：""" + " " + engineInfo + """


                特性：人人对战 / 三档人机、悔棋重做、高手提示、
                棋谱保存载入、复盘回看、三套棋盘主题。
                """);
        area.setEditable(false);
        area.setOpaque(false);
        area.setFont(Ui.font(Font.PLAIN, 13));
        area.setForeground(theme.panelText);
        panel.add(area, BorderLayout.CENTER);

        JOptionPane.showMessageDialog(parent, panel, "关于", JOptionPane.PLAIN_MESSAGE);
    }
}
