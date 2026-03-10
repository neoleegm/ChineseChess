/*
 * Decompiled with CFR 0.152.
 */
import java.io.ByteArrayInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

public class SoundManager {
    private static final float RATE = 44100.0f;
    private byte[] selectSound = this.generateTone(800.0, 0.08, 15.0, 0.3);
    private byte[] moveSound = this.generateNoise(400.0, 0.12, 10.0, 0.4);
    private byte[] captureSound = this.generateImpact(0.18, 0.5);
    private byte[] winSound = this.generateMelody();
    private boolean enabled = true;

    public void setEnabled(boolean bl) {
        this.enabled = bl;
    }

    public void playSelectSound() {
        this.play(this.selectSound);
    }

    public void playMoveSound() {
        this.play(this.moveSound);
    }

    public void playCaptureSound() {
        this.play(this.captureSound);
    }

    public void playWinSound() {
        this.play(this.winSound);
    }

    private void play(byte[] byArray) {
        if (!this.enabled || byArray == null) {
            return;
        }
        new Thread(() -> {
            try {
                AudioFormat audioFormat = new AudioFormat(44100.0f, 16, 1, true, false);
                AudioInputStream audioInputStream = new AudioInputStream(new ByteArrayInputStream(byArray), audioFormat, byArray.length / 2);
                Clip clip = AudioSystem.getClip();
                clip.open(audioInputStream);
                clip.start();
                Thread.sleep(clip.getMicrosecondLength() / 1000L + 50L);
                clip.close();
            }
            catch (Exception exception) {
                // empty catch block
            }
        }).start();
    }

    private byte[] generateTone(double d, double d2, double d3, double d4) {
        int n = (int)(d2 * 44100.0);
        byte[] byArray = new byte[n * 2];
        for (int i = 0; i < n; ++i) {
            double d5 = (float)i / 44100.0f;
            double d6 = Math.sin(Math.PI * 2 * d * d5) * Math.exp(-d5 * d3) * d4;
            this.writeSample(byArray, i, d6);
        }
        return byArray;
    }

    private byte[] generateNoise(double d, double d2, double d3, double d4) {
        int n = (int)(d2 * 44100.0);
        byte[] byArray = new byte[n * 2];
        for (int i = 0; i < n; ++i) {
            double d5 = (float)i / 44100.0f;
            double d6 = (Math.sin(Math.PI * 2 * d * d5) * 0.7 + (Math.random() - 0.5) * 0.3) * Math.exp(-d5 * d3) * d4;
            this.writeSample(byArray, i, d6);
        }
        return byArray;
    }

    private byte[] generateImpact(double d, double d2) {
        int n = (int)(d * 44100.0);
        byte[] byArray = new byte[n * 2];
        for (int i = 0; i < n; ++i) {
            double d3 = (float)i / 44100.0f;
            double d4 = 200.0;
            double d5 = (Math.sin(Math.PI * 2 * d4 * d3) + Math.sin(Math.PI * 2 * d4 * 2.0 * d3) * 0.5 + Math.sin(Math.PI * 2 * d4 * 0.5 * d3) * 0.3 + (Math.random() - 0.5) * 0.5) * Math.min(1.0, d3 * 50.0) * Math.exp(-d3 * 8.0) * d2;
            this.writeSample(byArray, i, d5);
        }
        return byArray;
    }

    private byte[] generateMelody() {
        double[] dArray = new double[]{523.25, 659.25, 783.99, 1046.5};
        double d = 0.15;
        int n = (int)((double)dArray.length * d * 44100.0);
        byte[] byArray = new byte[n * 2];
        int n2 = 0;
        double[] dArray2 = dArray;
        int n3 = dArray.length;
        for (int i = 0; i < n3; ++i) {
            double d2 = dArray2[i];
            int n4 = (int)(d * 44100.0);
            for (int j = 0; j < n4; ++j) {
                double d3 = (float)j / 44100.0f;
                double d4 = (Math.sin(Math.PI * 2 * d2 * d3) + Math.sin(Math.PI * 2 * d2 * 2.0 * d3) * 0.2) * Math.min(1.0, d3 * 20.0) * Math.exp(-d3 * 5.0) * 0.3;
                this.writeSample(byArray, n2++, d4);
            }
        }
        return byArray;
    }

    private void writeSample(byte[] byArray, int n, double d) {
        short s = (short)Math.max(-32768.0, Math.min(32767.0, d * 32767.0));
        byArray[n * 2] = (byte)(s & 0xFF);
        byArray[n * 2 + 1] = (byte)(s >> 8 & 0xFF);
    }
}
