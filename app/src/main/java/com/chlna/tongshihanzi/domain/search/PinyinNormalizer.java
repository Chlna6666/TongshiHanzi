/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.domain.search;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class PinyinNormalizer {
    private static final Map<Character, Integer> TONE_MARKS = new HashMap<>();

    static {
        put("āēīōūǖ", 1);
        put("áéíóúǘ", 2);
        put("ǎěǐǒǔǚ", 3);
        put("àèìòùǜ", 4);
    }

    private PinyinNormalizer() {
    }

    private static void put(String value, int tone) {
        for (char character : value.toCharArray()) {
            TONE_MARKS.put(character, tone);
        }
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }

        String value = raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace("u:", "v")
                .replace('ü', 'v')
                .replace('ǖ', 'v')
                .replace('ǘ', 'v')
                .replace('ǚ', 'v')
                .replace('ǜ', 'v')
                .replaceAll("[\\s'’-]+", "")
                .replaceAll("[1-5]$", "");

        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        StringBuilder output = new StringBuilder(decomposed.length());
        for (int index = 0; index < decomposed.length(); index++) {
            char character = decomposed.charAt(index);
            if (Character.getType(character) != Character.NON_SPACING_MARK) {
                output.append(character);
            }
        }
        return output.toString();
    }

    public static int extractTone(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0;
        }

        String value = raw.trim().toLowerCase(Locale.ROOT);
        char last = value.charAt(value.length() - 1);
        if (last >= '1' && last <= '5') {
            return last - '0';
        }

        for (char character : value.toCharArray()) {
            Integer tone = TONE_MARKS.get(character);
            if (tone != null) {
                return tone;
            }
        }
        return 0;
    }

    public static String looseInitialVariant(String normalized) {
        if (normalized.startsWith("zh")) {
            return "z" + normalized.substring(2);
        }
        if (normalized.startsWith("ch")) {
            return "c" + normalized.substring(2);
        }
        if (normalized.startsWith("sh")) {
            return "s" + normalized.substring(2);
        }
        if (normalized.startsWith("z")) {
            return "zh" + normalized.substring(1);
        }
        if (normalized.startsWith("c")) {
            return "ch" + normalized.substring(1);
        }
        if (normalized.startsWith("s")) {
            return "sh" + normalized.substring(1);
        }
        return normalized;
    }
}
