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
 * Reusable UCI adapter for Pikafish. Keeps the engine process alive across moves
 * to avoid UCI handshake overhead on every search.
 */
public class PikafishEngine implements Engine {
    private static final long UCI_TIMEOUT_MS = 5000;
    private static final long READY_TIMEOUT_MS = 5000;

    private final String enginePath;
    private Process process;
    private BufferedWriter writer;
    private BlockingQueue<String> outputQueue;
    private ExecutorService readerExecutor;
    private Future<?> readerTask;
    private volatile boolean running;

    public PikafishEngine(String enginePath) {
        this.enginePath = enginePath;
    }

    public String getEnginePath() {
        return enginePath;
    }

    private synchronized void ensureRunning() throws Exception {
        if (running && process != null && process.isAlive()) {
            return;
        }
        startProcess();
    }

    private void startProcess() throws Exception {
        shutdownQuietly();

        File executable = new File(enginePath);
        if (!executable.isFile()) {
            throw new IllegalStateException("Pikafish 引擎文件不存在: " + enginePath);
        }

        ProcessBuilder pb = new ProcessBuilder(executable.getAbsolutePath());
        File parent = executable.getParentFile();
        if (parent != null) pb.directory(parent);
        pb.redirectErrorStream(true);

        process = pb.start();
        outputQueue = new LinkedBlockingQueue<>();
        readerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "pikafish-reader");
            t.setDaemon(true);
            return t;
        });
        readerTask = readerExecutor.submit(this::readLoop);
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

        send("uci");
        waitFor("uciok", UCI_TIMEOUT_MS);
        send("isready");
        waitFor("readyok", READY_TIMEOUT_MS);
        send("setoption name Hash value 64");
        send("ucinewgame");
        send("isready");
        waitFor("readyok", READY_TIMEOUT_MS);
        running = true;
    }

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputQueue.offer(line);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public Move findBestMove(ChessBoard board, boolean aiIsRed, long timeMs) throws Exception {
        ensureRunning();

        send("position fen " + FenCodec.toFen(board));
        send("go movetime " + Math.max(500, timeMs));

        String bestMoveLine = waitFor("bestmove", Math.max(2000, timeMs + 4000));
        Move move = parseBestMoveLine(bestMoveLine);
        if (move == null) {
            throw new IllegalStateException("无法解析 Pikafish 走法: " + bestMoveLine);
        }
        return move;
    }

    public void shutdown() {
        shutdownQuietly();
    }

    private synchronized void shutdownQuietly() {
        running = false;
        if (writer != null) {
            try { send("quit"); } catch (Exception ignored) {}
        }
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ignored) {}
        }
        if (readerTask != null) {
            readerTask.cancel(true);
        }
        if (readerExecutor != null) {
            readerExecutor.shutdownNow();
        }
        process = null;
        writer = null;
    }

    private void send(String command) throws Exception {
        writer.write(command);
        writer.newLine();
        writer.flush();
    }

    private String waitFor(String token, long timeoutMs) throws Exception {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            long remainingMs = Math.max(1, (deadline - System.nanoTime()) / 1_000_000L);
            String line = outputQueue.poll(Math.min(100, remainingMs), TimeUnit.MILLISECONDS);
            if (line != null && line.startsWith(token)) {
                return line;
            }
            // If process died, restart on next call
            if (process != null && !process.isAlive()) {
                throw new IllegalStateException("Pikafish 进程意外退出");
            }
        }
        throw new IllegalStateException("等待 Pikafish 响应超时: " + token);
    }

    static Move parseBestMoveLine(String line) {
        if (line == null) return null;
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 2 || !"bestmove".equals(parts[0]) || "(none)".equals(parts[1])) {
            return null;
        }
        return MoveCodec.fromUci(parts[1]);
    }
}
