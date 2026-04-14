import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Thin UCI adapter for Pikafish.
 */
public class PikafishEngine implements Engine {
    private static final long UCI_TIMEOUT_MS = 3000;
    private static final long READY_TIMEOUT_MS = 3000;

    private final String enginePath;

    public PikafishEngine(String enginePath) {
        this.enginePath = enginePath;
    }

    public String getEnginePath() {
        return enginePath;
    }

    @Override
    public Move findBestMove(ChessBoard board, boolean aiIsRed, long timeMs) throws Exception {
        File executable = new File(enginePath);
        if (!executable.isFile()) {
            throw new IllegalStateException("Pikafish 引擎文件不存在");
        }

        ProcessBuilder processBuilder = new ProcessBuilder(executable.getAbsolutePath());
        File parent = executable.getParentFile();
        if (parent != null) {
            processBuilder.directory(parent);
        }
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        BlockingQueue<String> output = new LinkedBlockingQueue<>();
        ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
        Future<?> readerTask = readerExecutor.submit(() -> readOutput(process, output));

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                process.getOutputStream(), StandardCharsets.UTF_8))) {
            send(writer, "uci");
            waitFor(output, "uciok", UCI_TIMEOUT_MS);

            send(writer, "isready");
            waitFor(output, "readyok", READY_TIMEOUT_MS);

            send(writer, "ucinewgame");
            send(writer, "isready");
            waitFor(output, "readyok", READY_TIMEOUT_MS);

            send(writer, "position fen " + FenCodec.toFen(board));
            send(writer, "go movetime " + Math.max(100, timeMs));

            String bestMoveLine = waitFor(output, "bestmove", Math.max(1000, timeMs + 3000));
            Move move = parseBestMoveLine(bestMoveLine);
            if (move == null) {
                throw new IllegalStateException("无法解析 Pikafish 走法: " + bestMoveLine);
            }
            return move;
        } finally {
            try {
                sendQuietly(process, "quit");
            } finally {
                process.destroy();
                if (!process.waitFor(300, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
                readerTask.cancel(true);
                readerExecutor.shutdownNow();
            }
        }
    }

    private static void readOutput(Process process, BlockingQueue<String> output) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.offer(line);
            }
        } catch (Exception ignored) {
            // The process is often destroyed as soon as bestmove is parsed.
        }
    }

    private static void send(BufferedWriter writer, String command) throws Exception {
        writer.write(command);
        writer.newLine();
        writer.flush();
    }

    private static void sendQuietly(Process process, String command) {
        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                process.getOutputStream(), StandardCharsets.UTF_8));
            send(writer, command);
        } catch (Exception ignored) {
        }
    }

    static Move parseBestMoveLine(String line) {
        if (line == null) {
            return null;
        }
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 2 || !"bestmove".equals(parts[0]) || "(none)".equals(parts[1])) {
            return null;
        }
        return MoveCodec.fromUci(parts[1]);
    }

    private static String waitFor(BlockingQueue<String> output, String token, long timeoutMs) throws Exception {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            long remainingMs = Math.max(1, (deadline - System.nanoTime()) / 1_000_000L);
            String line = output.poll(Math.min(100, remainingMs), TimeUnit.MILLISECONDS);
            if (line != null && line.startsWith(token)) {
                return line;
            }
        }
        throw new IllegalStateException("等待 Pikafish 响应超时: " + token);
    }
}
