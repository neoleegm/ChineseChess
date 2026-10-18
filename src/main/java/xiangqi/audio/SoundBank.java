package xiangqi.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.util.EnumMap;
import java.util.Map;

/**
 * 音效库：启动时用代码合成 PCM 并缓存为 {@link Clip}，之后每次播放只是
 * {@code setFramePosition(0) + start()}，不再重新打开音频线路。
 *
 * <p>旧实现每次播放都新建 Clip、生成波形、再 {@code sleep} 等播完，
 * 导致连续走子时音效明显滞后于落子。这里把合成和播放彻底分开：
 * 波形只在首次使用时渲染一次，播放永不阻塞调用线程。</p>
 */
public final class SoundBank {

    public enum Effect {
        SELECT, MOVE, CAPTURE, CHECK, WIN, LOSE
    }

    private static final int SAMPLE_RATE = 22_050;
    private static final AudioFormat FORMAT =
            new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    private final Map<Effect, Clip> clips = new EnumMap<>(Effect.class);
    private volatile boolean enabled = true;
    private volatile boolean available = true;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 预渲染全部音效，建议在窗口显示后调用一次。 */
    public void warmUp() {
        for (Effect effect : Effect.values()) {
            clip(effect);
        }
    }

    public void play(Effect effect) {
        if (!enabled || !available) {
            return;
        }
        Clip clip = clip(effect);
        if (clip == null) {
            return;
        }
        try {
            clip.stop();
            clip.setFramePosition(0);
            clip.start();
        } catch (Exception ignored) {
            // 音频设备被占用等情况直接放弃本次播放
        }
    }

    private synchronized Clip clip(Effect effect) {
        if (!available) {
            return null;
        }
        Clip cached = clips.get(effect);
        if (cached != null) {
            return cached;
        }
        try {
            byte[] pcm = render(effect);
            Clip clip = AudioSystem.getClip();
            clip.open(FORMAT, pcm, 0, pcm.length);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.setFramePosition(0);
                }
            });
            clips.put(effect, clip);
            return clip;
        } catch (Exception e) {
            available = false;
            return null;
        }
    }

    public void dispose() {
        for (Clip clip : clips.values()) {
            try {
                clip.close();
            } catch (Exception ignored) {
                // 关闭失败无需处理
            }
        }
        clips.clear();
    }

    // ==================== 波形合成 ====================

    private static byte[] render(Effect effect) {
        return switch (effect) {
            case SELECT -> blip(1_100, 45, 0.22);
            case MOVE -> wooden(330, 70, 0.45, 0.55);
            case CAPTURE -> wooden(180, 130, 0.6, 0.75);
            case CHECK -> concat(blip(880, 90, 0.3), gap(50), blip(1_180, 110, 0.3));
            case WIN -> concat(
                    blip(523, 160, 0.28), gap(40),
                    blip(659, 160, 0.28), gap(40),
                    blip(784, 160, 0.28), gap(40),
                    blip(1_047, 320, 0.32));
            case LOSE -> concat(
                    blip(440, 180, 0.26), gap(50),
                    blip(370, 180, 0.26), gap(50),
                    blip(294, 380, 0.26));
        };
    }

    private static byte[] gap(int millis) {
        return new byte[(int) (millis / 1000.0 * SAMPLE_RATE) * 2];
    }

    /** 带指数衰减的纯音，适合提示音。 */
    private static byte[] blip(double frequency, int millis, double amplitude) {
        int samples = (int) (millis / 1000.0 * SAMPLE_RATE);
        byte[] data = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            double t = i / (double) SAMPLE_RATE;
            double envelope = Math.exp(-4.5 * i / samples);
            double value = Math.sin(2 * Math.PI * frequency * t) * amplitude * envelope;
            writeSample(data, i, value);
        }
        return data;
    }

    /**
     * 木质敲击声：低频基音 + 短促噪声，模拟棋子落在木盘上的「嗒」。
     */
    private static byte[] wooden(double frequency, int millis, double amplitude, double noiseAmount) {
        int samples = (int) (millis / 1000.0 * SAMPLE_RATE);
        byte[] data = new byte[samples * 2];
        java.util.Random random = new java.util.Random(7);
        double noise = 0;
        for (int i = 0; i < samples; i++) {
            double t = i / (double) SAMPLE_RATE;
            double envelope = Math.exp(-14.0 * i / samples);
            double body = Math.sin(2 * Math.PI * frequency * t) * 0.8
                    + Math.sin(2 * Math.PI * frequency * 2.02 * t) * 0.2;
            noise = noise * 0.55 + (random.nextDouble() * 2 - 1) * 0.45;
            double value = (body * (1 - noiseAmount) + noise * noiseAmount) * amplitude * envelope;
            writeSample(data, i, value);
        }
        return data;
    }

    private static void writeSample(byte[] data, int index, double value) {
        short sample = (short) (Math.max(-1, Math.min(1, value)) * 32_000);
        data[index * 2] = (byte) (sample & 0xFF);
        data[index * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
    }

    private static byte[] concat(byte[]... chunks) {
        int length = 0;
        for (byte[] chunk : chunks) {
            length += chunk.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, result, offset, chunk.length);
            offset += chunk.length;
        }
        return result;
    }
}
