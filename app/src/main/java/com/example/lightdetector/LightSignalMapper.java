package com.example.lightdetector;

final class LightSignalMapper {
    private LightSignalMapper() {
    }

    static LightSignal fromLuma(double averageY, int sensitivityPercent) {
        double raw = clamp(averageY / 255.0, 0.0, 1.0);
        double gain = clamp(sensitivityPercent / 100.0, 0.05, 2.0);
        double adjusted = clamp(raw * gain, 0.0, 1.0);

        int percent = (int) Math.round(adjusted * 100.0);
        double frequencyHz = 260.0 + (adjusted * 1840.0);
        double volume = 0.18 + (adjusted * 0.72);
        return new LightSignal(percent, frequencyHz, volume, labelFor(percent));
    }

    private static String labelFor(int percent) {
        if (percent < 15) {
            return "muy baja";
        }
        if (percent < 35) {
            return "baja";
        }
        if (percent < 65) {
            return "media";
        }
        if (percent < 85) {
            return "alta";
        }
        return "muy alta";
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
