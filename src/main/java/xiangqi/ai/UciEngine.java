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
    private volatile BufferedWriter writer;
    private BlockingQueue<String> output;
    private ExecutorService readerPool;
    private Future<?> readerTask;
    private boolean running;
    /** 是否有搜索正在进行；{@link #stop()} 只在这种情况下才发 stop。 */
    private volatile boolean searching;

    /** 写管道锁：{@link #stop()} 必须能和阻塞在 {@code waitFor} 里的搜索并行发指令。 */
    private final Object writeLock = new Object();
    /** 启动握手期间的输出，用于识别「缺 nnue」这类只能从日志看出来的失败。 */
    private final StringBuilder startupLog = new StringBuilder();

    private volatile String lastInfo = "";
    private volatile Info lastParsed = Info.EMPTY;

    /**
     * 一条 UCI {@code info} 行的解析结果。
     *
     * @param score 已换算成内部单位（厘兵），杀棋用 {@link SearchResult#MATE_SCORE} 附近的值表示
     */
    public record Info(int depth, int score, List<Move> pv, String raw) {

        public static final Info EMPTY = new Info(0, 0, List.of(), "");
    }

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
        lastParsed = Info.EMPTY;
        searching = true;
        try {
            // 清掉上一次搜索残留的输出（尤其是超时后迟到的 bestmove），
            // 否则 waitFor 可能把旧结果当成本次结果返回。
            BlockingQueue<String> queue = output;
            if (queue != null) {
                queue.clear();
            }
            send(positionCommand(board));
            send("go movetime " + timeMs);
            String best = waitFor("bestmove", timeMs + 5_000);
            Move move = parseBestMove(best);
            if (move == null) {
                resetAfterFailure();
                throw new IllegalStateException("无法解析 bestmove: " + best);
            }
            Info info = lastParsed;
            return new SearchResult(move, info.score(), info.depth(), 0, info.pv());
        } catch (Exception e) {
            resetAfterFailure();
            throw e instanceof RuntimeException runtime ? runtime
                    : new IllegalStateException(e.getMessage(), e);
        } finally {
            searching = false;
        }
    }

    /**
     * 请求中止当前搜索：发 UCI {@code stop}，让引擎尽快吐回 bestmove。
     *
     * <p>不能加 {@code synchronized}——搜索方法持有对象锁并阻塞在读取上，
     * 同锁会让 stop 一直排到搜索结束，等于没中止。</p>
     */
    public void stop() {
        if (!searching) {
            return;
        }
        try {
            synchronized (writeLock) {
                BufferedWriter out = writer;
                if (out == null) {
                    return;
                }
                out.write("stop");
                out.newLine();
                out.flush();
            }
        } catch (Exception ignored) {
            // 进程正在退出时写管道会抛异常，忽略即可，搜索侧会超时后重置
        }
    }

    /**
     * 尝试握手并跑一次极短搜索；成功返回 {@code null}，失败返回可读原因。
     *
     * <p>只握手不够：缺少 {@code pikafish.nnue} 的 Pikafish 能正常回 {@code uciok} 和
     * {@code readyok}，却会在第一次搜索时直接退出。必须真的让它算一步才算「可用」。</p>
     */
    public String healthCheck() {
        try {
            synchronized (this) {
                ensureRunning();
                Process current = process;
                if (current == null || !current.isAlive()) {
                    return "引擎进程已退出";
                }
            }
            SearchResult probe = search(new Board(), SearchLimits.of(1, 150));
            if (probe.move() == null) {
                return "引擎没有返回着法";
            }
            return null;
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            boolean suspicious = message.contains("退出") || message.contains("超时")
                    || message.contains("启动失败");
            return suspicious
                    ? message + "（常见原因：缺少 pikafish.nnue，或二进制与本机平台不匹配）"
                    : message;
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
        startupLog.setLength(0);

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

        String problem = startupProblem();
        if (problem != null) {
            shutdownQuietly();
            throw new IllegalStateException(problem);
        }
    }

    /**
     * 从握手期间的输出里找可读的失败原因。Pikafish 缺少 {@code pikafish.nnue} 时
     * 仍然会正常回 uciok，只在 info string 里报错，不主动看日志就会「静默变弱」。
     */
    private String startupProblem() {
        String log = startupLog.toString();
        if (log.isEmpty()) {
            return null;
        }
        for (String line : log.split("\\R")) {
            String lower = line.toLowerCase();
            if (lower.contains("error") || lower.contains("failed") || lower.contains("missing")) {
                return "引擎报告错误：" + line.trim();
            }
        }
        return null;
    }

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("info ")) {
                    Info parsed = parseInfoLine(line, lastParsed);
                    lastParsed = parsed;
                    lastInfo = line;
                }
                output.offer(line);
            }
        } catch (Exception ignored) {
            // 进程结束时读取线程自然退出
        }
    }

    /**
     * 解析一条 {@code info} 行。字段缺省时沿用 {@code previous} 的值，
     * 因为引擎可能只发 {@code info depth 12 score cp 30} 而不带完整信息。
     */
    public static Info parseInfoLine(String line, Info previous) {
        Info base = previous == null ? Info.EMPTY : previous;
        int depth = base.depth();
        int score = base.score();
        List<Move> pv = base.pv();
        String[] parts = line.split("\\s+");
        for (int i = 1; i < parts.length - 1; i++) {
            switch (parts[i]) {
                case "depth" -> depth = parseInt(parts[i + 1], depth);
                case "score" -> {
                    if ("cp".equals(parts[i + 1]) && i + 2 < parts.length) {
                        score = parseInt(parts[i + 2], score);
                    } else if ("mate".equals(parts[i + 1]) && i + 2 < parts.length) {
                        score = mateToScore(parseInt(parts[i + 2], 0));
                    }
                }
                case "pv" -> pv = parsePv(parts, i + 1);
                default -> {
                }
            }
        }
        return new Info(depth, score, pv, line);
    }

    /**
     * UCI 的 {@code score mate N} 单位是<strong>回合</strong>（红走一步 + 黑走一步算一回合），
     * 内部评分用<strong>半回合</strong>编码（{@code MATE_SCORE - plies}），这里做换算。
     * 直接把 N 当半回合会让「三步杀」显示成「杀棋 +2」。
     */
    public static int mateToScore(int mateIn) {
        if (mateIn == 0) {
            return -SearchResult.MATE_SCORE; // 轮到自己被将死
        }
        int plies = mateIn > 0 ? 2 * mateIn - 1 : 2 * Math.abs(mateIn);
        return mateIn > 0
                ? SearchResult.MATE_SCORE - plies
                : -SearchResult.MATE_SCORE + plies;
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
        synchronized (writeLock) {
            BufferedWriter out = writer;
            if (out == null) {
                throw new IllegalStateException("引擎未启动");
            }
            out.write(command);
            out.newLine();
            out.flush();
        }
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
            if (line != null && (!line.startsWith("info ") || line.startsWith("info string"))) {
                startupLog.append(line).append('\n');
            }
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
