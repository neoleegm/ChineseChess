package xiangqi.ui;

import xiangqi.ai.AiPlayer;
import xiangqi.core.Board;
import xiangqi.core.Move;

import javax.swing.SwingUtilities;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 后台思考调度器。
 *
 * <p>三件事必须同时成立才安全：</p>
 * <ol>
 *   <li>搜索只在一个后台线程上跑，绝不并发使用同一个引擎实例；</li>
 *   <li>结果回到 EDT 之前先比对代际号，过期结果直接丢弃（悔棋/重开不会落子到新局面上）；</li>
 *   <li>新请求到来时立刻给引擎发中止信号，旧搜索尽快让出 CPU。</li>
 * </ol>
 */
public final class AiWorker {

    private final AiPlayer ai;
    private final ExecutorService executor;
    private final AtomicInteger generation = new AtomicInteger();

    public AiWorker(AiPlayer ai, String threadName) {
        this.ai = ai;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    public void request(Board snapshot, Consumer<Move> onResult, Consumer<Exception> onError) {
        int current = generation.incrementAndGet();
        ai.abort();
        executor.submit(() -> {
            if (current != generation.get()) {
                return;
            }
            try {
                Move move = ai.chooseMove(snapshot);
                if (current != generation.get()) {
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    if (current == generation.get()) {
                        onResult.accept(move);
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if (current == generation.get()) {
                        onError.accept(e);
                    }
                });
            }
        });
    }

    public void cancel() {
        generation.incrementAndGet();
        ai.abort();
    }

    public void shutdown() {
        cancel();
        executor.shutdownNow();
    }
}
