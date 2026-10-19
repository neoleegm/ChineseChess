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
    /** 搜索线程数上限。2.5 秒的提示预算下，4 线程已能比单线程多算数倍节点。 */
    private static final int MAX_THREADS = 4;
    private static final int HASH_MB = 128;

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
    private volatile List<Move> lastPv = List.of();

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
        lastDepth = 0;
        lastScore = 0;
        lastPv = List.of();

        try {
            send(positionCommand(board));
            send("go movetime " + timeMs);
            String best = waitFor("bestmove", timeMs + 5_000);
            Move move = parseBestMove(best);
            if (move == null) {
                resetAfterFailure();
                throw new IllegalStateException("无法解析 bestmove: " + best);
            }
            return new SearchResult(move, lastScore, lastDepth, 0, lastPv);
        } catch (Exception e) {
            resetAfterFailure();
            throw e instanceof RuntimeException runtime ? runtime
                    : new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * 构造 UCI 的 position 命令。
     *
     * <p>关键点：{@code position fen X moves ...} 会从 X 开始<em>再走一遍</em> moves。
     * 如果 X 已经是走完这些着法之后的局面，就等于把历史叠加了两次，引擎会分析到
     * 一个错误局面（大多数情况因为首着不合法而被忽略，但局面一旦回到起点就会真的错）。
     * 因此这里优先用 {@code position startpos moves ...}，只有在棋盘不是从标准初始
     * 局面摆出来的时候才退回 {@code position fen}。</p>
     */
    public static String positionCommand(Board board) {
        List<Move> history = board.historyMoves();
        if (!history.isEmpty() && board.startsFromInitialPosition()) {
            StringBuilder command = new StringBuilder("position startpos moves");
            for (Move move : history) {
                command.append(' ').append(Uci.toUci(move));
            }
            return command.toString();
        }
        return "position fen " + Fen.toFen(board);
    }

    /** 预启动引擎进程并完成握手，避免第一次提示时把 1 秒握手算进等待时间。 */
    public void warmUp() {
        try {
            synchronized (this) {
                ensureRunning();
            }
        } catch (Exception ignored) {
            // 预热失败不影响后续调用：真正搜索时会再次尝试并给出明确错误
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
        int threads = Math.max(1, Math.min(MAX_THREADS, Runtime.getRuntime().availableProcessors()));
        send("setoption name Threads value " + threads);
        send("setoption name Hash value " + HASH_MB);
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
                case "pv" -> lastPv = parsePv(parts, i + 1);
                default -> {
                }
            }
        }
        lastInfo = line;
    }

    private static List<Move> parsePv(String[] parts, int from) {
        List<Move> pv = new java.util.ArrayList<>();
        for (int i = from; i < parts.length; i++) {
            Move move = Uci.fromUci(parts[i]);
            if (move == null) {
                break;
            }
            pv.add(move);
        }
        return pv;
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
