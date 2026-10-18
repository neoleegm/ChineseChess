package xiangqi.ui;

import java.awt.Color;

/**
 * 视觉主题。所有绘制颜色都从这里取，切换主题只需换一个实例。
 *
 * <p>提供木纹、青瓷、暗夜三套：暗夜主题下棋子底面与文字色会同步反转，
 * 避免「黑子黑字」在深色棋盘上看不见。</p>
 */
public final class Theme {

    public final String name;

    public final Color boardTop;
    public final Color boardBottom;
    public final Color boardEdge;
    public final Color line;

    public final Color pieceFace;
    public final Color pieceFaceShade;
    public final Color redInk;
    public final Color blackInk;

    public final Color panelBg;
    public final Color panelBorder;
    public final Color panelText;
    public final Color panelMuted;

    public final Color buttonBg;
    public final Color buttonHover;
    public final Color buttonPressed;
    public final Color buttonText;

    public final Color recordBg;
    public final Color recordAlt;

    public final Color accent;
    public final Color accentSoft;
    public final Color lastMove;
    public final Color hint;
    public final Color check;
    public final Color pillBg;

    private Theme(String name, Color boardTop, Color boardBottom, Color boardEdge, Color line,
                  Color pieceFace, Color pieceFaceShade, Color redInk, Color blackInk,
                  Color panelBg, Color panelBorder, Color panelText, Color panelMuted,
                  Color buttonBg, Color buttonHover, Color buttonPressed, Color buttonText,
                  Color recordBg, Color recordAlt,
                  Color accent, Color accentSoft, Color lastMove, Color hint, Color check, Color pillBg) {
        this.name = name;
        this.boardTop = boardTop;
        this.boardBottom = boardBottom;
        this.boardEdge = boardEdge;
        this.line = line;
        this.pieceFace = pieceFace;
        this.pieceFaceShade = pieceFaceShade;
        this.redInk = redInk;
        this.blackInk = blackInk;
        this.panelBg = panelBg;
        this.panelBorder = panelBorder;
        this.panelText = panelText;
        this.panelMuted = panelMuted;
        this.buttonBg = buttonBg;
        this.buttonHover = buttonHover;
        this.buttonPressed = buttonPressed;
        this.buttonText = buttonText;
        this.recordBg = recordBg;
        this.recordAlt = recordAlt;
        this.accent = accent;
        this.accentSoft = accentSoft;
        this.lastMove = lastMove;
        this.hint = hint;
        this.check = check;
        this.pillBg = pillBg;
    }

    public static final Theme WOOD = new Theme(
            "木纹",
            new Color(0xF6, 0xE0, 0xB2), new Color(0xE3, 0xBE, 0x84),
            new Color(0x8A, 0x5C, 0x2B), new Color(0x6B, 0x47, 0x1F),
            new Color(0xFB, 0xEE, 0xCF), new Color(0xE9, 0xD3, 0xA6),
            new Color(0xB6, 0x2C, 0x2C), new Color(0x25, 0x25, 0x25),
            new Color(0xF7, 0xEC, 0xD6), new Color(0x8A, 0x5C, 0x2B),
            new Color(0x5A, 0x3A, 0x18), new Color(0x9A, 0x7B, 0x58),
            new Color(0xE6, 0xCE, 0xA2), new Color(0xF1, 0xDD, 0xB6),
            new Color(0xDC, 0xBD, 0x8C), new Color(0x5A, 0x3A, 0x18),
            new Color(0xFD, 0xF6, 0xE6), new Color(0xF3, 0xE5, 0xCA),
            new Color(0x2E, 0x7D, 0x46), new Color(0x2E, 0x7D, 0x46, 40),
            new Color(0x2E, 0x7D, 0x46, 55), new Color(0x1E, 0x63, 0xC8),
            new Color(0xD3, 0x2F, 0x2F), new Color(0x33, 0x2A, 0x1E, 210));

    public static final Theme CELADON = new Theme(
            "青瓷",
            new Color(0xEC, 0xF4, 0xEE), new Color(0xCF, 0xE1, 0xD7),
            new Color(0x3F, 0x6B, 0x5A), new Color(0x45, 0x6B, 0x5C),
            new Color(0xFA, 0xFD, 0xFA), new Color(0xE2, 0xEE, 0xE7),
            new Color(0xB0, 0x3A, 0x48), new Color(0x2A, 0x38, 0x33),
            new Color(0xF3, 0xF8, 0xF4), new Color(0x3F, 0x6B, 0x5A),
            new Color(0x2B, 0x44, 0x3A), new Color(0x6E, 0x8B, 0x7E),
            new Color(0xDD, 0xEA, 0xE1), new Color(0xE8, 0xF2, 0xEA),
            new Color(0xCE, 0xE0, 0xD5), new Color(0x2B, 0x44, 0x3A),
            new Color(0xFA, 0xFD, 0xFA), new Color(0xEC, 0xF4, 0xEE),
            new Color(0x2F, 0x7D, 0x62), new Color(0x2F, 0x7D, 0x62, 40),
            new Color(0x2F, 0x7D, 0x62, 55), new Color(0x1B, 0x5C, 0xA8),
            new Color(0xC0, 0x2B, 0x2B), new Color(0x1E, 0x2E, 0x28, 210));

    public static final Theme NIGHT = new Theme(
            "暗夜",
            new Color(0x44, 0x44, 0x48), new Color(0x2E, 0x2E, 0x31),
            new Color(0x8C, 0x8C, 0x92), new Color(0xA8, 0xA8, 0xB0),
            new Color(0x3E, 0x3E, 0x42), new Color(0x33, 0x33, 0x36),
            new Color(0xE8, 0x7A, 0x6A), new Color(0xEC, 0xE8, 0xE0),
            new Color(0x1E, 0x1E, 0x21), new Color(0x4A, 0x4A, 0x50),
            new Color(0xE9, 0xE6, 0xDF), new Color(0x9A, 0x99, 0x94),
            new Color(0x2C, 0x2C, 0x31), new Color(0x3A, 0x3A, 0x40),
            new Color(0x24, 0x24, 0x28), new Color(0xEC, 0xE9, 0xE2),
            new Color(0x26, 0x26, 0x2A), new Color(0x30, 0x30, 0x35),
            new Color(0x5A, 0xB5, 0x86), new Color(0x5A, 0xB5, 0x86, 45),
            new Color(0x5A, 0xB5, 0x86, 60), new Color(0x6C, 0xA8, 0xF0),
            new Color(0xE0, 0x5A, 0x4A), new Color(0x0A, 0x0A, 0x0C, 220));

    public static final Theme[] ALL = {WOOD, CELADON, NIGHT};

    public static Theme byName(String name) {
        for (Theme theme : ALL) {
            if (theme.name.equals(name)) {
                return theme;
            }
        }
        return WOOD;
    }

    @Override
    public String toString() {
        return name;
    }
}
