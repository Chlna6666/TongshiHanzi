/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.domain.search;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class PinyinNormalizer {
    private static final Map<Character, Integer> TONE_MARKS = new HashMap<>();
    static { put("āēīōūǖ", 1); put("áéíóúǘ", 2); put("ǎěǐǒǔǚ", 3); put("àèìòùǜ", 4); }
    private PinyinNormalizer() {}
    private static void put(String value, int tone) { for (char c : value.toCharArray()) TONE_MARKS.put(c, tone); }

    public static String normalize(String raw) {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT).replace("u:", "ü").replace('v', 'ü').replaceAll("[\\s'’-]+", "");
        value = value.replaceAll("[1-5]$", "");
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            if (Character.getType(c) != Character.NON_SPACING_MARK) out.append(c == 'ü' ? 'v' : c);
        }
        return out.toString();
    }

    public static int extractTone(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0;
        char last = raw.charAt(raw.length() - 1);
        if (last >= '1' && last <= '5') return last - '0';
        for (char c : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            Integer tone = TONE_MARKS.get(c); if (tone != null) return tone;
        }
        return 0;
    }

    public static String looseInitialVariant(String normalized) {
        if (normalized.startsWith("zh")) return "z" + normalized.substring(2);
        if (normalized.startsWith("ch")) return "c" + normalized.substring(2);
        if (normalized.startsWith("sh")) return "s" + normalized.substring(2);
        if (normalized.startsWith("z") && !normalized.startsWith("zh")) return "zh" + normalized.substring(1);
        if (normalized.startsWith("c") && !normalized.startsWith("ch")) return "ch" + normalized.substring(1);
        if (normalized.startsWith("s") && !normalized.startsWith("sh")) return "sh" + normalized.substring(1);
        return normalized;
    }
}
