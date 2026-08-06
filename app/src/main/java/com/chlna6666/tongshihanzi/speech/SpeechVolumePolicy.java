/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.speech;

/** Pure volume and software-gain rules shared by settings, playback and unit tests. */
public final class SpeechVolumePolicy {
    public static final int MIN_PERCENT = 50;
    public static final int DEFAULT_PERCENT = 100;
    public static final int MAX_PERCENT = 200;

    private SpeechVolumePolicy() {
    }

    public static int clampPercent(int value) {
        return Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, value));
    }

    /** Native TextToSpeech volume, whose documented useful range ends at 1.0. */
    public static float directVolume(int percent) {
        return Math.min(1f, clampPercent(percent) / 100f);
    }

    public static boolean requiresSoftwareBoost(int percent) {
        return clampPercent(percent) > 100;
    }

    /**
     * Converts an amplitude multiplier to Android LoudnessEnhancer millibels.
     * 200% is approximately +6.02 dB, or 602 mB.
     */
    public static int boostMillibels(int percent) {
        int safe = clampPercent(percent);
        if (safe <= 100) {
            return 0;
        }
        double ratio = safe / 100.0;
        return (int) Math.round(2000.0 * Math.log10(ratio));
    }
}
