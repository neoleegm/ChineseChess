package xiangqi.ai;

import xiangqi.core.Board;
import xiangqi.core.Fen;
import xiangqi.core.Move;
import xiangqi.core.Uci;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 外部 UCI 引擎适配器（Pikafish 等）。
 *
 * <p>进程常驻复用，避免每步重做 UCI 握手。协议一旦错位（超时、解析失败）
 * 就销毁进程，下次调用重新握手，保证拿到的 bestmove 一定对应当前局面。</p>
 */
public final class UciEngine implements Engine {

    private static final long HANDSHAKE_TIMEOUT_MS = 8_000;
    private static final long READY_TIMEOUT_MS = 8_000;

    private final String enginePath;
    private Process process;
    private BufferedWriter writer;
    private BlockingQueue<String> output;
    private ExecutorService readerPool;
    private Future<?> readerTask;
    private boolean running;

    private volatile String lastInfo = "";
    private volatile int lastDepth;
    private volatile int lastScore;

    public UciEngine(String enginePath) {
        this.enginePath = enginePath;
    }

    public String enginePath() {
        return enginePath;
    }

    public String lastInfo() {
        return lastInfo;
    }

    @Override
    public String name() {
        return "Pikafish";
    }

    @Override
    public synchronized SearchResult search(Board board, SearchLimits limits) {
        try {
            ensureRunning();
        } catch (Exception e) {
            throw new IllegalStateException("引擎启动失败: " + e.getMessage(), e);
        }

        long timeMs = Math.max(200, limits.timeMs());
        StringBuilder position = new StringBuilder("position fen ").append(Fen.toFen(board));
        List<Move> history = board.historyMoves();
        if (!history.isEmpty()) {
            position.append(" moves");
            for (Move move : history) {
                position.append(' ').append(Uci.toUci(move));
            }
        }
        lastDepth = 0;
        lastScore = 0;

        try {
            send(position.toString());
            send("go movetime " + timeMs);
            String best = waitFor("bestmove", timeMs + 5_000);
            Move move = parseBestMove(best);
            if (move == null) {
                resetAfterFailure();
                throw new IllegalStateException("无法解析 bestmove: " + best);
            }
            return new SearchResult(move, lastScore, lastDepth, 0);
        } catch (Exception e) {
            resetAfterFailure();
            throw e instanceof RuntimeException runtime ? runtime
                    : new IllegalStateException(e.getMessage(), e);
        }
    }

    // ==================== 进程生命周期 ====================

    private void ensureRunning() throws Exception {
        if (running && process != null && process.isAlive()) {
            return;
        }
        start();
    }

    private void start() throws Exception {
        shutdownQuietly();

        File executable = new File(enginePath);
        if (!executable.isFile()) {
            throw new IllegalStateException("引擎文件不存在: " + enginePath);
        }
        if (!executable.canExecute()) {
            throw new IllegalStateException("引擎文件不可执行: " + enginePath);
        }

        ProcessBuilder builder = new ProcessBuilder(executable.getAbsolutePath());
        File parent = executable.getParentFile();
        if (parent != null) {
            builder.directory(parent);
        }
        builder.redirectErrorStream(true);
        process = builder.start();
        output = new LinkedBlockingQueue<>();
        readerPool = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "uci-reader");
            thread.setDaemon(true);
            return thread;
        });
        readerTask = readerPool.submit(this::readLoop);
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

        send("uci");
        waitFor("uciok", HANDSHAKE_TIMEOUT_MS);
        send("setoption name Threads value 1");
        send("setoption name Hash value 128");
        send("isready");
        waitFor("readyok", READY_TIMEOUT_MS);
        send("ucinewgame");
        send("isready");
        waitFor("readyok", READY_TIMEOUT_MS);
        running = true;
    }

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("info ")) {
                    parseInfo(line);
                }
                output.offer(line);
            }
        } catch (Exception ignored) {
            // 进程结束时读取线程自然退出
        }
    }

    private void parseInfo(String line) {
        String[] parts = line.split("\\s+");
        for (int i = 1; i < parts.length - 1; i++) {
            switch (parts[i]) {
                case "depth" -> lastDepth = parseInt(parts[i + 1], lastDepth);
                case "score" -> {
                    if ("cp".equals(parts[i + 1])) {
                        lastScore = parseInt(parts[i + 2], lastScore);
                    } else if ("mate".equals(parts[i + 1])) {
                        int mateIn = parseInt(parts[i + 2], 0);
                        lastScore = mateIn > 0
                                ? SearchResult.MATE_SCORE - mateIn
                                : -SearchResult.MATE_SCORE - mateIn;
                    }
                }
                default -> {
                }
            }
        }
        lastInfo = line;
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public synchronized void shutdown() {
        shutdownQuietly();
    }

    private void shutdownQuietly() {
        running = false;
        if (writer != null) {
            try {
                send("quit");
            } catch (Exception ignored) {
                // 进程可能已退出
            }
        }
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (readerTask != null) {
            readerTask.cancel(true);
        }
        if (readerPool != null) {
            readerPool.shutdownNow();
        }
        process = null;
        writer = null;
        output = null;
        readerTask = null;
        readerPool = null;
    }

    private void resetAfterFailure() {
        try {
            if (writer != null) {
                send("stop");
            }
        } catch (Exception ignored) {
            // 忽略
        }
        shutdownQuietly();
    }

    // ==================== 协议读写 ====================

    private void send(String command) throws Exception {
        if (writer == null) {
            throw new IllegalStateException("引擎未启动");
        }
        writer.write(command);
        writer.newLine();
        writer.flush();
    }

    private String waitFor(String token, long timeoutMs) throws Exception {
        BlockingQueue<String> queue = output;
        if (queue == null) {
            throw new IllegalStateException("引擎未启动");
        }
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            long remaining = Math.max(1, (deadline - System.nanoTime()) / 1_000_000L);
            String line = queue.poll(Math.min(100, remaining), TimeUnit.MILLISECONDS);
            if (line != null && line.startsWith(token)) {
                return line;
            }
            if (process != null && !process.isAlive()) {
                throw new IllegalStateException("引擎进程意外退出");
            }
        }
        throw new IllegalStateException("等待引擎响应超时: " + token);
    }

    static Move parseBestMove(String line) {
        if (line == null) {
            return null;
        }
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 2 || !"bestmove".equals(parts[0]) || "(none)".equals(parts[1])) {
            return null;
        }
        return Uci.fromUci(parts[1]);
    }
}
