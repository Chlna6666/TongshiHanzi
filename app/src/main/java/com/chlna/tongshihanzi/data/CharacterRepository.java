/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.data;

import android.content.Context;
import com.chlna.tongshihanzi.data.dictionary.CharacterWithDetails;
import com.chlna.tongshihanzi.data.dictionary.DataSourceEntity;
import com.chlna.tongshihanzi.data.dictionary.DictionaryStore;
import com.chlna.tongshihanzi.data.dictionary.SearchRow;
import com.chlna.tongshihanzi.domain.search.SearchResult;
import com.chlna.tongshihanzi.util.AppExecutors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public final class CharacterRepository {
    private final DictionaryStore store;

    public CharacterRepository(Context context) {
        store = DictionaryStore.getInstance(context);
    }

    public void loadCharacter(int id, String fallbackCharacter,
                              Consumer<CharacterWithDetails> success,
                              Consumer<Throwable> failure) {
        AppExecutors.io().execute(() -> {
            try {
                if (id < 0 && UnicodeCharacterFallback.isSingleCjkCharacter(fallbackCharacter)) {
                    success.accept(UnicodeCharacterFallback.create(fallbackCharacter));
                    return;
                }
                store.initialize().join();
                success.accept(store.dao().getCharacter(id));
            } catch (Throwable error) {
                failure.accept(error);
            }
        });
    }

    public void loadCharacter(int id, Consumer<CharacterWithDetails> success,
                              Consumer<Throwable> failure) {
        loadCharacter(id, null, success, failure);
    }

    public void loadSource(String id, Consumer<DataSourceEntity> success) {
        AppExecutors.io().execute(() -> success.accept(store.dao().getSource(id)));
    }

    public void rowsByIds(List<Integer> ids, Consumer<List<SearchResult>> success) {
        AppExecutors.io().execute(() -> {
            store.initialize().join();
            if (ids == null || ids.isEmpty()) {
                success.accept(Collections.emptyList());
                return;
            }
            List<SearchResult> result = new ArrayList<>();
            for (SearchRow row : store.dao().getRowsByIds(ids)) {
                result.add(new SearchResult(row.characterId, row.character, row.pinyin,
                        row.radical, row.totalStrokes, row.definition, "收藏", 0));
            }
            success.accept(result);
        });
    }

    public void common(int limit, Consumer<List<SearchResult>> success) {
        AppExecutors.io().execute(() -> {
            store.initialize().join();
            List<SearchResult> result = new ArrayList<>();
            for (SearchRow row : store.dao().commonCharacters(limit)) {
                result.add(new SearchResult(row.characterId, row.character, row.pinyin,
                        row.radical, row.totalStrokes, row.definition, "常用字", 0));
            }
            success.accept(result);
        });
    }
}
