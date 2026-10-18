package xiangqi.test;

import xiangqi.core.Board;
import xiangqi.core.Move;
import xiangqi.ui.BoardView;
import xiangqi.ui.Theme;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

/**
 * 界面渲染测试：无头把棋盘画到内存图像上，验证绘制链路不抛异常且确实画出了内容。
 * 这类测试能挡住「改了绘制代码导致空指针/除零」的低级回归。
 */
final class UiTest {

    private UiTest() {
    }

    static void run() {
        System.out.println("[界面渲染]");
        Checks.test("三套主题都能正常绘制", UiTest::renderAllThemes);
        Checks.test("翻转棋盘会改变画面", UiTest::flipChangesImage);
        Checks.test("复盘局面可绘制", UiTest::renderHistoricalPosition);
    }

    private static Board sampleBoard() {
        Board board = new Board();
        board.play(new Move(7, 1, 7, 4));
        board.play(new Move(0, 1, 2, 2));
        board.play(new Move(9, 7, 7, 6));
        return board;
    }

    private static BufferedImage render(BoardView view, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        view.paintTo(g2, width, height);
        g2.dispose();
        return image;
    }

    private static int distinctColors(BufferedImage image) {
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y += 3) {
            for (int x = 0; x < image.getWidth(); x += 3) {
                colors.add(image.getRGB(x, y));
            }
        }
        return colors.size();
    }

    private static void renderAllThemes() {
        for (Theme theme : Theme.ALL) {
            BoardView view = new BoardView();
            view.setTheme(theme);
            view.setBoard(sampleBoard());
            view.setLastMove(new Move(9, 7, 7, 6));
            BufferedImage image = render(view, 620, 700);
            Checks.isTrue(distinctColors(image) > 40,
                    theme.name + " 主题应画出足够多的颜色，实际 " + distinctColors(image));
        }
    }

    private static void flipChangesImage() {
        BoardView normal = new BoardView();
        normal.setBoard(sampleBoard());
        BufferedImage first = render(normal, 620, 700);

        BoardView flipped = new BoardView();
        flipped.setBoard(sampleBoard());
        flipped.setFlipped(true);
        BufferedImage second = render(flipped, 620, 700);

        int differing = 0;
        for (int y = 0; y < first.getHeight(); y += 4) {
            for (int x = 0; x < first.getWidth(); x += 4) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    differing++;
                }
            }
        }
        Checks.isTrue(differing > 1_000, "翻转后画面应有大量像素不同，实际 " + differing);
    }

    private static void renderHistoricalPosition() {
        BoardView view = new BoardView();
        Board historical = new Board();
        historical.play(new Move(7, 1, 7, 4));
        view.setBoard(historical);
        BufferedImage image = render(view, 560, 640);
        Checks.isTrue(distinctColors(image) > 40, "复盘局面应正常绘制");
    }
}
