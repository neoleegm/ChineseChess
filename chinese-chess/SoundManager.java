import javax.sound.sampled.*;

/**
 * 音效管理器
 */
public class SoundManager {
    private static final float RATE = 44100f;
    private byte[] selectSound, moveSound, captureSound, winSound;
    private boolean enabled = true;
    
    public SoundManager() {
        selectSound = generateTone(800, 0.08, 15, 0.3);
        moveSound = generateNoise(400, 0.12, 10, 0.4);
        captureSound = generateImpact(0.18, 0.5);
        winSound = generateMelody();
    }
    
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public void playSelectSound() { play(selectSound); }
    public void playMoveSound() { play(moveSound); }
    public void playCaptureSound() { play(captureSound); }
    public void playWinSound() { play(winSound); }
    
    private void play(byte[] data) {
        if (!enabled || data == null) return;
        new Thread(() -> {
            try {
                AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
                AudioInputStream ais = new AudioInputStream(
                    new java.io.ByteArrayInputStream(data), fmt, data.length / 2);
                Clip clip = AudioSystem.getClip();
                clip.open(ais);
                clip.start();
                Thread.sleep(clip.getMicrosecondLength() / 1000 + 50);
                clip.close();
            } catch (Exception ignored) {}
        }).start();
    }
    
    private byte[] generateTone(double freq, double duration, double decay, double amp) {
        int samples = (int) (duration * RATE);
        byte[] data = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            double t = i / RATE;
            double v = Math.sin(2 * Math.PI * freq * t) * Math.exp(-t * decay) * amp;
            writeSample(data, i, v);
        }
        return data;
    }
    
    private byte[] generateNoise(double freq, double duration, double decay, double amp) {
        int samples = (int) (duration * RATE);
        byte[] data = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            double t = i / RATE;
            double v = (Math.sin(2 * Math.PI * freq * t) * 0.7 + (Math.random() - 0.5) * 0.3) 
                       * Math.exp(-t * decay) * amp;
            writeSample(data, i, v);
        }
        return data;
    }
    
    private byte[] generateImpact(double duration, double amp) {
        int samples = (int) (duration * RATE);
        byte[] data = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            double t = i / RATE;
            double base = 200;
            double v = (Math.sin(2 * Math.PI * base * t) 
                      + Math.sin(2 * Math.PI * base * 2 * t) * 0.5
                      + Math.sin(2 * Math.PI * base * 0.5 * t) * 0.3
                      + (Math.random() - 0.5) * 0.5)
                      * Math.min(1, t * 50) * Math.exp(-t * 8) * amp;
            writeSample(data, i, v);
        }
        return data;
    }
    
    private byte[] generateMelody() {
        double[] freqs = {523.25, 659.25, 783.99, 1046.50};
        double noteDur = 0.15;
        int totalSamples = (int) (freqs.length * noteDur * RATE);
        byte[] data = new byte[totalSamples * 2];
        int idx = 0;
        
        for (double freq : freqs) {
            int noteSamples = (int) (noteDur * RATE);
            for (int i = 0; i < noteSamples; i++) {
                double t = i / RATE;
                double v = (Math.sin(2 * Math.PI * freq * t) 
                          + Math.sin(2 * Math.PI * freq * 2 * t) * 0.2)
                          * Math.min(1, t * 20) * Math.exp(-t * 5) * 0.3;
                writeSample(data, idx++, v);
            }
        }
        return data;
    }
    
    private void writeSample(byte[] data, int idx, double value) {
        short s = (short) Math.max(-32768, Math.min(32767, value * 32767));
        data[idx * 2] = (byte) (s & 0xFF);
        data[idx * 2 + 1] = (byte) ((s >> 8) & 0xFF);
    }
}
