/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.data.dictionary;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "search_alias", indices = {@Index("normalized_text"), @Index("character_id")})
public class SearchAliasEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @ColumnInfo(name = "character_id") public int characterId;
    @NonNull @ColumnInfo(name = "alias_type") public String aliasType = "";
    @NonNull @ColumnInfo(name = "normalized_text") public String normalizedText = "";
    public int weight;
}
