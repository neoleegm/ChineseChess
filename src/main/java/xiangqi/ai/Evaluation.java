package xiangqi.ai;

import xiangqi.core.Board;
import xiangqi.core.Piece;

/**
 * 局面评估：子力价值 + 位置分（PST）+ 阵形结构。
 *
 * <p>所有 PST 以<strong>黑方视角</strong>书写（第 0 行为黑方底线），红方取
 * {@code 9 - row} 镜像。将/士/象三张表在初始化时按行镜像成对称表，
 * 保证双方拿到完全相同的位置分——旧实现里黑方将/士/象的 PST 恒为 0，
 * 是一个不易察觉的评估偏差。</p>
 */
public final class Evaluation {

    /** 索引顺序：将 0、士 1、象 2、马 3、车 4、炮 5、兵 6。取值来自 {@link Piece.Type#value()}。 */
    public static final int[] VALUE = {
            Piece.Type.KING.value(),
            Piece.Type.ADVISOR.value(),
            Piece.Type.ELEPHANT.value(),
            Piece.Type.HORSE.value(),
            Piece.Type.ROOK.value(),
            Piece.Type.CANNON.value(),
            Piece.Type.PAWN.value()
    };

    private static final int[][][] PST = new int[7][10][9];

    static {
        int[][] king = {
                {0, 0, 0, 8, 12, 8, 0, 0, 0},
                {0, 0, 0, 10, 16, 10, 0, 0, 0},
                {0, 0, 0, 8, 12, 8, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 8, 12, 8, 0, 0, 0},
                {0, 0, 0, 10, 16, 10, 0, 0, 0},
                {0, 0, 0, 8, 12, 8, 0, 0, 0}
        };
        int[][] advisor = {
                {0, 0, 0, 12, 0, 12, 0, 0, 0},
                {0, 0, 0, 0, 18, 0, 0, 0, 0},
                {0, 0, 0, 12, 0, 12, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 12, 0, 12, 0, 0, 0},
                {0, 0, 0, 0, 18, 0, 0, 0, 0},
                {0, 0, 0, 12, 0, 12, 0, 0, 0}
        };
        int[][] elephant = {
                {0, 0, 14, 0, 0, 0, 14, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {14, 0, 0, 0, 14, 0, 0, 0, 14},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 12, 0, 0, 0, 12, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 12, 0, 0, 0, 12, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {14, 0, 0, 0, 14, 0, 0, 0, 14},
                {0, 0, 14, 0, 0, 0, 14, 0, 0}
        };
        int[][] horse = {
                {-20, -8, 0, 4, 6, 4, 0, -8, -20},
                {-8, 4, 12, 16, 18, 16, 12, 4, -8},
                {0, 12, 22, 28, 32, 28, 22, 12, 0},
                {6, 18, 30, 38, 42, 38, 30, 18, 6},
                {8, 22, 36, 44, 50, 44, 36, 22, 8},
                {10, 24, 38, 48, 54, 48, 38, 24, 10},
                {8, 22, 34, 42, 48, 42, 34, 22, 8},
                {4, 14, 24, 30, 34, 30, 24, 14, 4},
                {-6, 6, 14, 18, 20, 18, 14, 6, -6},
                {-16, -6, 0, 4, 6, 4, 0, -6, -16}
        };
        int[][] rook = {
                {0, 4, 8, 10, 12, 10, 8, 4, 0},
                {4, 8, 12, 14, 16, 14, 12, 8, 4},
                {8, 12, 16, 18, 20, 18, 16, 12, 8},
                {10, 16, 20, 24, 26, 24, 20, 16, 10},
                {12, 18, 24, 28, 32, 28, 24, 18, 12},
                {12, 18, 24, 28, 32, 28, 24, 18, 12},
                {10, 16, 20, 24, 26, 24, 20, 16, 10},
                {8, 12, 16, 18, 20, 18, 16, 12, 8},
                {4, 8, 12, 14, 16, 14, 12, 8, 4},
                {0, 4, 8, 10, 12, 10, 8, 4, 0}
        };
        int[][] cannon = {
                {-6, 0, 4, 8, 10, 8, 4, 0, -6},
                {0, 6, 10, 14, 18, 14, 10, 6, 0},
                {4, 10, 16, 22, 28, 22, 16, 10, 4},
                {8, 14, 22, 30, 36, 30, 22, 14, 8},
                {8, 16, 24, 32, 38, 32, 24, 16, 8},
                {8, 16, 24, 32, 38, 32, 24, 16, 8},
                {6, 12, 20, 26, 30, 26, 20, 12, 6},
                {2, 8, 14, 18, 22, 18, 14, 8, 2},
                {-4, 2, 8, 10, 12, 10, 8, 2, -4},
                {-8, -2, 2, 4, 6, 4, 2, -2, -8}
        };
        int[][] pawn = {
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {8, 12, 16, 22, 26, 22, 16, 12, 8},
                {14, 18, 24, 32, 38, 32, 24, 18, 14},
                {22, 28, 36, 46, 52, 46, 36, 28, 22},
                {30, 38, 48, 60, 68, 60, 48, 38, 30},
                {22, 30, 40, 50, 56, 50, 40, 30, 22}
        };

        PST[Piece.Type.KING.ordinal()] = king;
        PST[Piece.Type.ADVISOR.ordinal()] = advisor;
        PST[Piece.Type.ELEPHANT.ordinal()] = elephant;
        PST[Piece.Type.HORSE.ordinal()] = horse;
        PST[Piece.Type.ROOK.ordinal()] = rook;
        PST[Piece.Type.CANNON.ordinal()] = cannon;
        PST[Piece.Type.PAWN.ordinal()] = pawn;
    }

    private Evaluation() {
    }

    /**
     * @param perspectiveIsRed 以红方视角返回正分；{@code false} 则以黑方视角
     */
    public static int evaluate(Board board, boolean perspectiveIsRed) {
        int score = 0;
        int redAdvisors = 0;
        int redElephants = 0;
        int blackAdvisors = 0;
        int blackElephants = 0;

        for (Piece piece : board.pieces()) {
            boolean red = piece.isRed();
            int type = piece.type().ordinal();
            int value = VALUE[type] + PST[type][red ? 9 - piece.row() : piece.row()][piece.col()];
            score += red ? value : -value;
            switch (piece.type()) {
                case ADVISOR -> {
                    if (red) {
                        redAdvisors++;
                    } else {
                        blackAdvisors++;
                    }
                }
                case ELEPHANT -> {
                    if (red) {
                        redElephants++;
                    } else {
                        blackElephants++;
                    }
                }
                default -> {
                }
            }
        }

        score += structure(board, true);
        score -= structure(board, false);
        score += redAdvisors >= 2 ? 30 : (redAdvisors == 1 ? 10 : -25);
        score -= blackAdvisors >= 2 ? 30 : (blackAdvisors == 1 ? 10 : -25);
        score += redElephants >= 2 ? 22 : (redElephants == 1 ? 8 : -18);
        score -= blackElephants >= 2 ? 22 : (blackElephants == 1 ? 8 : -18);

        return perspectiveIsRed ? score : -score;
    }

    /** 阵形分：车通头、炮架、兵型、将帅保护。 */
    private static int structure(Board board, boolean red) {
        int score = 0;
        for (Piece piece : board.pieces()) {
            if (piece.isRed() != red) {
                continue;
            }
            switch (piece.type()) {
                case ROOK -> {
                    boolean openFile = true;
                    for (int row = 0; row < Board.ROWS; row++) {
                        Piece other = board.pieceAt(row, piece.col());
                        if (other != null && other.type() == Piece.Type.PAWN) {
                            openFile = false;
                            break;
                        }
                    }
                    if (openFile) {
                        score += 18;
                    }
                    if (piece.row() == (red ? 4 : 5)) {
                        score += 12; // 巡河车
                    }
                }
                case CANNON -> {
                    int dir = red ? -1 : 1;
                    Piece screen = board.pieceAt(piece.row() + dir, piece.col());
                    if (screen != null && screen.isRed() == red) {
                        score += 14; // 有己方炮架
                    }
                }
                case PAWN -> {
                    for (int dc = -1; dc <= 1; dc += 2) {
                        Piece neighbour = board.pieceAt(piece.row(), piece.col() + dc);
                        if (neighbour != null && neighbour.isRed() == red
                                && neighbour.type() == Piece.Type.PAWN) {
                            score += 8; // 联兵
                        }
                    }
                }
                case KING -> {
                    for (int dr = -1; dr <= 1; dr++) {
                        for (int dc = -1; dc <= 1; dc++) {
                            if (dr == 0 && dc == 0) {
                                continue;
                            }
                            Piece neighbour = board.pieceAt(piece.row() + dr, piece.col() + dc);
                            if (neighbour != null && neighbour.isRed() == red
                                    && (neighbour.type() == Piece.Type.ADVISOR
                                    || neighbour.type() == Piece.Type.ELEPHANT)) {
                                score += 18;
                            }
                        }
                    }
                    boolean shielded = false;
                    for (int row = 0; row < Board.ROWS; row++) {
                        Piece other = board.pieceAt(row, piece.col());
                        if (other != null && other.isRed() == red && other.type() == Piece.Type.PAWN) {
                            shielded = true;
                            break;
                        }
                    }
                    if (!shielded) {
                        score -= 15;
                    }
                }
                default -> {
                }
            }
        }
        return score;
    }
}
