import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 音效管理类
 * 使用程序生成简单的音效，无需外部音频文件。
 * 所有音效提交到后台单线程顺序播放，避免阻塞 Swing 事件线程造成界面卡顿。
 */
public class SoundManager {
    private volatile boolean enabled = true;

    // 音频参数
    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_SIZE = 2; // 16-bit

    // 单线程播放队列：音效按提交顺序播放，不会互相叠加
    private final ExecutorService player = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sound-player");
        t.setDaemon(true);
        return t;
    });

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 播放选择棋子的音效
     */
    public void playSelectSound() {
        if (!enabled) return;
        // 清脆的短音
        player.submit(() -> playTone(800, 60, 0.15));
    }

    /**
     * 播放移动音效
     */
    public void playMoveSound(boolean captured) {
        if (!enabled) return;
        if (captured) {
            // 吃子音效 - 较重
            player.submit(() -> playTone(200, 150, 0.3));
        } else {
            // 普通移动 - 轻快
            player.submit(() -> playTone(400, 80, 0.15));
        }
    }

    /**
     * 播放将军提示音：两声短促高音
     */
    public void playCheckSound() {
        if (!enabled) return;
        player.submit(() -> {
            playTone(880, 90, 0.25);
            playTone(880, 90, 0.25);
        });
    }

    /**
     * 播放获胜音效
     */
    public void playWinSound() {
        if (!enabled) return;
        // 胜利音效序列
        player.submit(() -> {
            playTone(523, 200, 0.2);  // C5
            sleep(50);
            playTone(659, 200, 0.2);  // E5
            sleep(50);
            playTone(784, 200, 0.2);  // G5
            sleep(50);
            playTone(1047, 400, 0.25); // C6
        });
    }

    /**
     * 生成并播放指定频率的音效（在播放线程上阻塞至播放完成）
     */
    private void playTone(int frequency, int durationMs, double amplitude) {
        try {
            int numSamples = (int) ((durationMs / 1000.0) * SAMPLE_RATE);
            byte[] audioData = new byte[numSamples * SAMPLE_SIZE];

            // 生成正弦波
            for (int i = 0; i < numSamples; i++) {
                double time = i / (double) SAMPLE_RATE;
                // 添加衰减使声音更自然
                double envelope = Math.exp(-3.0 * i / numSamples);
                double sample = Math.sin(2 * Math.PI * frequency * time) * amplitude * envelope;

                // 转换为 16-bit PCM
                short sampleShort = (short) (sample * 32767);
                audioData[i * 2] = (byte) (sampleShort & 0xFF);
                audioData[i * 2 + 1] = (byte) ((sampleShort >> 8) & 0xFF);
            }

            // 创建音频流
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
            AudioInputStream ais = new AudioInputStream(bais, format, numSamples);

            // 播放
            DataLine.Info info = new DataLine.Info(Clip.class, format);
            Clip clip = (Clip) AudioSystem.getLine(info);
            clip.open(ais);
            clip.start();

            // 等待播放完成（只阻塞后台播放线程）
            Thread.sleep(durationMs + 10);
            clip.close();

        } catch (Exception e) {
            // 静默处理音频错误
        }
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
