/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.data.dictionary;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "wubi_codes",
        foreignKeys = @ForeignKey(entity = CharacterEntity.class, parentColumns = "id", childColumns = "character_id", onDelete = ForeignKey.CASCADE),
        indices = {@Index("character_id"), @Index("code")})
public class WubiEntity {
    @PrimaryKey public int id;
    @ColumnInfo(name = "character_id") public int characterId;
    @NonNull public String scheme = "WUBI86";
    @NonNull public String code = "";
    @ColumnInfo(name = "is_primary") public boolean primary;
}
