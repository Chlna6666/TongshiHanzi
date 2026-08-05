/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.data;

import android.content.Context;
import androidx.preference.PreferenceManager;
import com.chlna.tongshihanzi.data.dictionary.DictionaryDao;
import com.chlna.tongshihanzi.data.dictionary.DictionaryStore;
import com.chlna.tongshihanzi.data.dictionary.SearchRow;
import com.chlna.tongshihanzi.domain.search.PinyinNormalizer;
import com.chlna.tongshihanzi.domain.search.QueryTypeDetector;
import com.chlna.tongshihanzi.domain.search.SearchMode;
import com.chlna.tongshihanzi.domain.search.SearchRanker;
import com.chlna.tongshihanzi.domain.search.SearchResult;
import com.chlna.tongshihanzi.domain.search.StrokeRange;
import com.chlna.tongshihanzi.util.AppExecutors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class SearchRepository {
    private static final int LIMIT = 50;
    private final Context context;
    private final DictionaryStore store;

    public SearchRepository(Context context) {
        this.context = context.getApplicationContext();
        this.store = DictionaryStore.getInstance(context);
    }

    public void search(String rawQuery, SearchMode requestedMode,
                       Consumer<List<SearchResult>> success, Consumer<Throwable> failure) {
        AppExecutors.io().execute(() -> {
            try {
                store.initialize().join();
                String query = rawQuery == null ? "" : rawQuery.trim();
                if (query.isEmpty()) {
                    success.accept(Collections.emptyList());
                    return;
                }
                SearchMode mode = requestedMode == SearchMode.AUTO
                        ? QueryTypeDetector.detect(query) : requestedMode;
                DictionaryDao dao = store.dao();
                Map<Integer, SearchResult> result = new LinkedHashMap<>();
                switch (mode) {
                    case CHARACTER -> add(result, dao.searchCharacters(query, LIMIT), query, "汉字匹配");
                    case PINYIN -> searchPinyin(dao, result, query, requestedMode == SearchMode.AUTO);
                    case STROKE -> {
                        StrokeRange range = StrokeRange.parse(query);
                        add(result, dao.searchStrokes(range.min(), range.max(), LIMIT), query, "笔画匹配");
                    }
                    case WUBI -> searchWubi(dao, result, query, requestedMode == SearchMode.AUTO);
                    case AUTO -> {
                        add(result, dao.searchCharacters(query, LIMIT), query, "汉字匹配");
                        searchPinyin(dao, result, query, true);
                        searchWubi(dao, result, query, true);
                    }
                }
                addUnicodeFallback(result, query);
                List<SearchResult> values = new ArrayList<>(result.values());
                values.sort(Comparator.comparingInt(SearchResult::getScore).reversed()
                        .thenComparingInt(SearchResult::getCharacterId));
                success.accept(values.size() > LIMIT ? values.subList(0, LIMIT) : values);
            } catch (Throwable error) {
                failure.accept(error);
            }
        });
    }

    private void searchPinyin(DictionaryDao dao, Map<Integer, SearchResult> out,
                              String raw, boolean includeWubi) {
        String normalized = PinyinNormalizer.normalize(raw);
        if (normalized.isEmpty()) return;
        add(out, dao.searchPinyinExact(normalized, LIMIT), normalized, "拼音精确");
        add(out, dao.searchPinyinPrefix(normalized, LIMIT), normalized, "拼音前缀");
        add(out, dao.searchAliases(normalized, LIMIT), normalized, "相关匹配");
        boolean loose = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("loose_pinyin", false);
        if (loose) {
            String variant = PinyinNormalizer.looseInitialVariant(normalized);
            if (!variant.equals(normalized)) {
                add(out, dao.searchPinyinPrefix(variant, 20), normalized, "近似拼音");
            }
        }
        if (includeWubi && normalized.length() <= 4) {
            add(out, dao.searchWubiPrefix(normalized, 20), normalized, "五笔前缀");
        }
    }

    private void searchWubi(DictionaryDao dao, Map<Integer, SearchResult> out,
                            String raw, boolean includePinyin) {
        String normalized = raw.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) return;
        add(out, dao.searchWubiExact(normalized, LIMIT), normalized, "五笔精确");
        add(out, dao.searchWubiPrefix(normalized, LIMIT), normalized, "五笔前缀");
        if (includePinyin) {
            String pinyin = PinyinNormalizer.normalize(raw);
            add(out, dao.searchPinyinPrefix(pinyin, 20), pinyin, "拼音前缀");
        }
    }

    private static void addUnicodeFallback(Map<Integer, SearchResult> out, String query) {
        if (!UnicodeCharacterFallback.isSingleCjkCharacter(query)) return;
        for (SearchResult value : out.values()) {
            if (query.equals(value.getCharacter())) return;
        }
        int id = UnicodeCharacterFallback.syntheticId(query);
        out.put(id, new SearchResult(
                id,
                query,
                "读音待补充",
                "待补充",
                0,
                "内置审校词库尚未收录该字，可查看 Unicode 基础信息。",
                "Unicode 生僻字兜底",
                10000));
    }

    private static void add(Map<Integer, SearchResult> out, List<SearchRow> rows,
                            String query, String matchType) {
        for (SearchRow row : rows) {
            int score = SearchRanker.score(query, row.character, firstPinyin(row.pinyin),
                    row.wubi, row.frequencyRank, matchType);
            SearchResult item = new SearchResult(row.characterId, row.character,
                    safe(row.pinyin), safe(row.radical), row.totalStrokes,
                    safe(row.definition), matchType, score);
            SearchResult old = out.get(row.characterId);
            if (old == null || item.getScore() > old.getScore()) out.put(row.characterId, item);
        }
    }

    private static String firstPinyin(String pinyin) {
        if (pinyin == null) return "";
        int split = pinyin.indexOf(" / ");
        return PinyinNormalizer.normalize(split < 0 ? pinyin : pinyin.substring(0, split));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
