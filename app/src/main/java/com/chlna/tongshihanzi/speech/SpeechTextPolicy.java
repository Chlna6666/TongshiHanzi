/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.speech;

/** Pure text selection rules used before an utterance reaches Android TextToSpeech. */
public final class SpeechTextPolicy {
    private SpeechTextPolicy() {}

    public static String character(String text) {
        String value = normalize(text);
        if (value.isEmpty()) return value;
        int end = value.offsetByCodePoints(0, 1);
        return value.substring(0, end);
    }

    public static String word(String text) {
        return normalize(text);
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}
