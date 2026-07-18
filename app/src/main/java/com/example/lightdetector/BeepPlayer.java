package com.example.lightdetector;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;

final class BeepPlayer {
    private static final int SAMPLE_RATE = 22050;

    private final Object lock = new Object();
    private HandlerThread audioThread;
    private Handler audioHandler;
    private AudioTrack audioTrack;
    private boolean running;
    private double latestLuma;
    private int sensitivityPercent = 100;
    private int intervalMs = 1000;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }

            long startedAt = SystemClock.uptimeMillis();
            LightSignal signal;
            int currentInterval;
            synchronized (lock) {
                signal = LightSignalMapper.fromLuma(latestLuma, sensitivityPercent);
                currentInterval = intervalMs;
            }

            playSignal(signal, currentInterval);

            long elapsed = SystemClock.uptimeMillis() - startedAt;
            long delay = Math.max(0L, currentInterval - elapsed);
            if (running && audioHandler != null) {
                audioHandler.postDelayed(this, delay);
            }
        }
    };

    void start() {
        if (running) {
            return;
        }
        running = true;
        audioThread = new HandlerThread("LightDetectorAudio", Process.THREAD_PRIORITY_AUDIO);
        audioThread.start();
        audioHandler = new Handler(audioThread.getLooper());
        audioHandler.post(tick);
    }

    void stop() {
        running = false;
        Handler handler = audioHandler;
        HandlerThread thread = audioThread;
        if (handler != null) {
            handler.removeCallbacks(tick);
            handler.post(this::releaseTrack);
        }
        if (thread != null) {
            thread.quitSafely();
            try {
                thread.join();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        audioThread = null;
        audioHandler = null;
    }

    void setInput(double luma, int sensitivityPercent, int intervalMs) {
        synchronized (lock) {
            this.latestLuma = luma;
            this.sensitivityPercent = sensitivityPercent;
            this.intervalMs = intervalMs;
        }
    }

    private void playSignal(LightSignal signal, int currentIntervalMs) {
        AudioTrack track = ensureTrack();
        if (track == null) {
            return;
        }

        int durationMs = Math.max(40, Math.min(160, currentIntervalMs / 3));
        short[] samples = makeSineWave(signal.frequencyHz, signal.volume, durationMs);
        track.write(samples, 0, samples.length);
    }

    private AudioTrack ensureTrack() {
        if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            return audioTrack;
        }

        int minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        if (minBufferSize <= 0) {
            return null;
        }

        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(Math.max(minBufferSize, SAMPLE_RATE))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        audioTrack.play();
        return audioTrack;
    }

    private void releaseTrack() {
        if (audioTrack == null) {
            return;
        }
        try {
            audioTrack.pause();
            audioTrack.flush();
            audioTrack.release();
        } finally {
            audioTrack = null;
        }
    }

    private short[] makeSineWave(double frequencyHz, double volume, int durationMs) {
        int sampleCount = Math.max(1, (SAMPLE_RATE * durationMs) / 1000);
        short[] samples = new short[sampleCount];
        double phaseStep = (2.0 * Math.PI * frequencyHz) / SAMPLE_RATE;
        int rampSamples = Math.min(sampleCount / 2, SAMPLE_RATE / 200);

        for (int i = 0; i < sampleCount; i++) {
            double envelope = 1.0;
            if (rampSamples > 0 && i < rampSamples) {
                envelope = i / (double) rampSamples;
            } else if (rampSamples > 0 && i >= sampleCount - rampSamples) {
                envelope = (sampleCount - i - 1) / (double) rampSamples;
            }

            double value = Math.sin(i * phaseStep) * volume * envelope;
            samples[i] = (short) (value * Short.MAX_VALUE);
        }
        return samples;
    }
}
