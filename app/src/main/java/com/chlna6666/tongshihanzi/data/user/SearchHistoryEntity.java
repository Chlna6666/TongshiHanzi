/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.data.user;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "search_history", indices = @Index(value = "query", unique = true))
public class SearchHistoryEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public String query = "";
    @ColumnInfo(name = "character_id") public int characterId;
    @ColumnInfo(name = "searched_at") public long searchedAt;
}
