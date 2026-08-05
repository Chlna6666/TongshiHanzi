/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.domain.search;

public final class SearchRanker {
    private SearchRanker() {}
    public static int score(String normalizedQuery, String character, String pinyinPlain, String wubi, int frequencyRank, String matchType) {
        int score = switch (matchType) {
            case "汉字匹配" -> 1000; case "拼音精确" -> 900; case "五笔精确" -> 850;
            case "拼音前缀" -> 750; case "五笔前缀" -> 700; case "笔画匹配" -> 600; default -> 400;
        };
        if (character.equals(normalizedQuery)) score += 200;
        if (pinyinPlain != null && pinyinPlain.equals(normalizedQuery)) score += 100;
        if (wubi != null && wubi.equalsIgnoreCase(normalizedQuery)) score += 100;
        if (frequencyRank > 0) score += Math.max(0, 100 - Math.min(100, frequencyRank / 20));
        return score;
    }
}
