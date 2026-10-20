package xiangqi.game;

import xiangqi.ai.Difficulty;
import xiangqi.core.GameResult;
import xiangqi.core.Move;
import xiangqi.core.Uci;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 棋谱读写。
 *
 * <p>采用「PGN 头 + 中文记谱 + 大括号内 UCI 坐标」的文本格式：人可以直接读，
 * 程序载入时只解析大括号里的坐标并逐手重演，因此不需要实现记谱反解析。</p>
 */
public final class GameRecord {

    private static final Pattern MOVE_TOKEN =
            Pattern.compile("\\{([a-i][0-9][a-i][0-9])\\}");

    private GameRecord() {
    }

    /** 载入结果。{@code result} 为棋谱里记录的结果，没有记录时为 {@code null}。 */
    public record Data(List<Move> moves, GameSession.Mode mode, Difficulty difficulty,
                       boolean playerIsRed, String resultText, GameResult result) {
    }

    public static void save(Path path, GameSession session) throws IOException {
        Files.writeString(path, render(session), StandardCharsets.UTF_8);
    }

    /** 导出为纯中文记谱文本（便于复制分享）。 */
    public static void exportText(Path path, GameSession session) throws IOException {
        StringBuilder text = new StringBuilder();
        text.append("中国象棋对局记录 ").append(LocalDate.now()).append('\n');
        text.append("模式：").append(session.mode().label())
                .append("　难度：").append(session.difficulty().label()).append('\n');
        if (session.result() != null) {
            text.append("结果：").append(session.result().text()).append('\n');
        }
        text.append('\n');
        for (String[] row : session.moveRows()) {
            text.append(row[0]).append(". ").append(row[1]);
            if (!row[2].isEmpty()) {
                text.append("    ").append(row[2]);
            }
            text.append('\n');
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    public static String render(GameSession session) {
        StringBuilder out = new StringBuilder(512);
        out.append("[Event \"中国象棋对局\"]\n");
        out.append("[Date \"").append(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")))
                .append("\"]\n");
        out.append("[Mode \"").append(session.mode().label()).append("\"]\n");
        out.append("[Difficulty \"").append(session.difficulty().label()).append("\"]\n");
        out.append("[PlayerSide \"").append(session.playerIsRed() ? "红方" : "黑方").append("\"]\n");
        out.append("[Result \"").append(session.result() == null ? "*" : session.result().score()).append("\"]\n");
        if (session.result() != null) {
            // 认输 / 议和无法从着法重演，必须把原因写进头里，载入时才能还原
            out.append("[Reason \"").append(session.result().reason().name()).append("\"]\n");
            out.append("[ResultText \"").append(session.result().text()).append("\"]\n");
        }
        out.append('\n');

        List<GameSession.MoveEntry> entries = session.entries();
        for (int i = 0; i < entries.size(); i += 2) {
            out.append(i / 2 + 1).append(". ").append(entries.get(i).notation())
                    .append(" {").append(Uci.toUci(entries.get(i).move())).append('}');
            if (i + 1 < entries.size()) {
                out.append(' ').append(entries.get(i + 1).notation())
                        .append(" {").append(Uci.toUci(entries.get(i + 1).move())).append('}');
            }
            out.append('\n');
        }
        return out.toString();
    }

    public static Data load(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        Map<String, String> headers = new LinkedHashMap<>();
        StringBuilder body = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                int space = trimmed.indexOf(' ');
                if (space > 1) {
                    String key = trimmed.substring(1, space);
                    String value = trimmed.substring(space + 1, trimmed.length() - 1).replace("\"", "");
                    headers.put(key, value);
                }
            } else {
                body.append(line).append('\n');
            }
        }

        List<Move> moves = new ArrayList<>();
        Matcher matcher = MOVE_TOKEN.matcher(body);
        while (matcher.find()) {
            Move move = Uci.fromUci(matcher.group(1));
            if (move != null) {
                moves.add(move);
            }
        }
        if (moves.isEmpty()) {
            throw new IOException("棋谱中没有可识别的着法");
        }

        GameSession.Mode mode = GameSession.Mode.fromLabel(headers.getOrDefault("Mode", "人机对战"));
        Difficulty difficulty = Difficulty.fromLabel(headers.getOrDefault("Difficulty", "中等"));
        boolean playerIsRed = !"黑方".equals(headers.getOrDefault("PlayerSide", "红方"));
        return new Data(moves, mode, difficulty, playerIsRed,
                headers.getOrDefault("ResultText", ""), parseResult(headers));
    }

    /** 从 {@code [Result]} + {@code [Reason]} 还原对局结果；信息不全时返回 {@code null}。 */
    private static GameResult parseResult(Map<String, String> headers) {
        String reasonName = headers.get("Reason");
        String score = headers.getOrDefault("Result", "*");
        if (reasonName == null || reasonName.isBlank() || "*".equals(score)) {
            return null;
        }
        GameResult.Reason reason;
        try {
            reason = GameResult.Reason.valueOf(reasonName.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        return switch (score) {
            case "1-0" -> GameResult.redWins(reason);
            case "0-1" -> GameResult.blackWins(reason);
            case "1/2-1/2" -> GameResult.draw(reason);
            default -> null;
        };
    }
}
