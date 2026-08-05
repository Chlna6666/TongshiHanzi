/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.data.dictionary;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {
        CharacterEntity.class, PronunciationEntity.class, DefinitionEntity.class,
        WordEntity.class, WubiEntity.class, StrokeEntity.class,
        SearchAliasEntity.class, DataSourceEntity.class
}, version = 3, exportSchema = true)
public abstract class DictionaryDatabase extends RoomDatabase {
    public abstract DictionaryDao dictionaryDao();

    static DictionaryDatabase create(Context context) {
        return Room.databaseBuilder(context, DictionaryDatabase.class, "dictionary.db")
                // Bundled dictionary content is reproducible. A data-version change
                // intentionally rebuilds only this generated database; user favorites
                // and history live in the separate user database.
                .fallbackToDestructiveMigration(true)
                .build();
    }
}
