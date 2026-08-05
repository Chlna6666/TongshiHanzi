/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.data.dictionary;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "pronunciations",
        foreignKeys = @ForeignKey(entity = CharacterEntity.class, parentColumns = "id", childColumns = "character_id", onDelete = ForeignKey.CASCADE),
        indices = {@Index("character_id"), @Index("pinyin_plain")})
public class PronunciationEntity {
    @PrimaryKey public int id;
    @ColumnInfo(name = "character_id") public int characterId;
    @NonNull @ColumnInfo(name = "pinyin_tone") public String pinyinTone = "";
    @NonNull @ColumnInfo(name = "pinyin_plain") public String pinyinPlain = "";
    @NonNull @ColumnInfo(name = "pinyin_number") public String pinyinNumber = "";
    public int tone;
    @ColumnInfo(name = "is_primary") public boolean primary;
    @NonNull @ColumnInfo(name = "speak_word") public String speakWord = "";
    @ColumnInfo(name = "display_order") public int displayOrder;
}
