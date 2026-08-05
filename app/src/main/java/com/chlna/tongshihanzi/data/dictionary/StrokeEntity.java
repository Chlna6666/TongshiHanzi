/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.data.dictionary;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "strokes",
        foreignKeys = @ForeignKey(
                entity = CharacterEntity.class,
                parentColumns = "id",
                childColumns = "character_id",
                onDelete = ForeignKey.CASCADE),
        indices = @Index("character_id"))
public class StrokeEntity {
    @PrimaryKey public int id;
    @ColumnInfo(name = "character_id") public int characterId;
    @ColumnInfo(name = "stroke_index") public int strokeIndex;
    @NonNull public String name = "";
    @NonNull @ColumnInfo(name = "path_data") public String pathData = "";
    /** JSON array of [x,y] points describing the brush centre line. */
    @NonNull @ColumnInfo(name = "median_data") public String medianData = "";
}
