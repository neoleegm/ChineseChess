package xiangqi.app;

import xiangqi.ui.MainFrame;

import javax.swing.SwingUtilities;

/**
 * 程序入口。
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // 让 Swing 跟随系统缩放（Retina / 高分屏），避免界面模糊
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
