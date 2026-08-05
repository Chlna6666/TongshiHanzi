/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.domain.search;

import java.util.regex.Pattern;

public final class QueryTypeDetector {
    private static final Pattern STROKE = Pattern.compile("^\\s*\\d{1,2}(?:\\s*[-~到至]\\s*\\d{1,2})?\\s*$");
    private static final Pattern LATIN = Pattern.compile("^[A-Za-züÜvV:0-5'\\s]+$");
    private QueryTypeDetector() {}
    public static SearchMode detect(String raw) {
        String query = raw == null ? "" : raw.trim();
        if (query.isEmpty()) return SearchMode.AUTO;
        if (query.codePoints().anyMatch(QueryTypeDetector::isHanCodePoint)) return SearchMode.CHARACTER;
        if (STROKE.matcher(query).matches()) return SearchMode.STROKE;
        if (LATIN.matcher(query).matches()) {
            String letters = query.replaceAll("[^A-Za-z]", "");
            if (letters.length() <= 4 && letters.equals(letters.toUpperCase())) return SearchMode.WUBI;
            return SearchMode.PINYIN;
        }
        return SearchMode.AUTO;
    }
    private static boolean isHanCodePoint(int codePoint) { return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN; }
}
