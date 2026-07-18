package com.example.lightdetector;

final class LightSignal {
    final int percent;
    final double frequencyHz;
    final double volume;
    final String label;

    LightSignal(int percent, double frequencyHz, double volume, String label) {
        this.percent = percent;
        this.frequencyHz = frequencyHz;
        this.volume = volume;
        this.label = label;
    }
}
