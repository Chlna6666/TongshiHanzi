/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.data.dictionary;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.io.InputStream;

@Database(entities = {
        CharacterEntity.class, PronunciationEntity.class, DefinitionEntity.class,
        WordEntity.class, WubiEntity.class, StrokeEntity.class,
        SearchAliasEntity.class, DataSourceEntity.class
}, version = 4, exportSchema = true)
public abstract class DictionaryDatabase extends RoomDatabase {
    private static final String PREBUILT_ASSET = "database/dictionary.db";

    public abstract DictionaryDao dictionaryDao();

    static DictionaryDatabase create(Context context) {
        RoomDatabase.Builder<DictionaryDatabase> builder = Room.databaseBuilder(
                context, DictionaryDatabase.class, "dictionary.db");
        if (assetExists(context, PREBUILT_ASSET)) {
            builder.createFromAsset(PREBUILT_ASSET);
        }
        return builder
                // Bundled dictionary content is reproducible and user state is stored
                // separately. Version 4 intentionally replaces slower runtime imports.
                .fallbackToDestructiveMigration(true)
                .build();
    }

    private static boolean assetExists(Context context, String path) {
        try (InputStream ignored = context.getAssets().open(path)) {
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
