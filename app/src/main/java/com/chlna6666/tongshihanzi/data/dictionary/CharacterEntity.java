/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.data.dictionary;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "characters", indices = {
        @Index(value = "character_text", unique = true),
        @Index("total_strokes"),
        @Index("frequency_rank")
})
public class CharacterEntity {
    @PrimaryKey public int id;
    @NonNull @ColumnInfo(name = "character_text") public String character = "";
    @NonNull public String simplified = "";
    @NonNull public String traditional = "";
    @NonNull @ColumnInfo(name = "unicode_codepoint") public String unicodeCodepoint = "";
    @NonNull public String radical = "";
    @ColumnInfo(name = "total_strokes") public int totalStrokes;
    @NonNull public String structure = "";
    @NonNull @ColumnInfo(name = "stroke_number") public String strokeNumber = "";
    @ColumnInfo(name = "frequency_rank") public int frequencyRank;
    @ColumnInfo(name = "is_common") public boolean common;
    @NonNull @ColumnInfo(name = "source_id") public String sourceId = "project";
}
