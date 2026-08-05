/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.data.dictionary;

import android.content.Context;
import android.util.Log;
import com.chlna.tongshihanzi.util.AppExecutors;
import java.util.concurrent.CompletableFuture;

public final class DictionaryStore {
    private static final String TAG = "DictionaryStore";
    private static volatile DictionaryStore instance;
    private final Context context;
    private final DictionaryDatabase database;
    private CompletableFuture<Void> initialization;

    private DictionaryStore(Context context) {
        this.context = context.getApplicationContext();
        this.database = DictionaryDatabase.create(this.context);
    }

    public static DictionaryStore getInstance(Context context) {
        if (instance == null) synchronized (DictionaryStore.class) {
            if (instance == null) instance = new DictionaryStore(context);
        }
        return instance;
    }

    public synchronized CompletableFuture<Void> initialize() {
        if (initialization != null) return initialization;
        initialization = CompletableFuture.runAsync(() -> {
            try {
                if (database.dictionaryDao().countCharacters() == 0) {
                    DictionarySeedLoader.load(context, database);
                }
            } catch (Exception error) {
                Log.e(TAG, "Unable to initialize dictionary", error);
                throw new IllegalStateException("无法初始化离线字典", error);
            }
        }, AppExecutors.io());
        return initialization;
    }

    public DictionaryDao dao() { return database.dictionaryDao(); }
}
