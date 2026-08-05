/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.data.dictionary;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "definitions",
        foreignKeys = {
                @ForeignKey(entity = CharacterEntity.class, parentColumns = "id", childColumns = "character_id", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = PronunciationEntity.class, parentColumns = "id", childColumns = "pronunciation_id", onDelete = ForeignKey.CASCADE)
        }, indices = {@Index("character_id"), @Index("pronunciation_id")})
public class DefinitionEntity {
    @PrimaryKey public int id;
    @ColumnInfo(name = "character_id") public int characterId;
    @ColumnInfo(name = "pronunciation_id") public int pronunciationId;
    @NonNull public String kind = "BASIC";
    @NonNull public String text = "";
    @ColumnInfo(name = "display_order") public int displayOrder;
    @NonNull @ColumnInfo(name = "source_id") public String sourceId = "project";
}
