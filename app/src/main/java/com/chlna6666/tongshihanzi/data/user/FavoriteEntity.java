/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.data.user;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "favorites")
public class FavoriteEntity {
    @PrimaryKey @ColumnInfo(name = "character_id") public int characterId;
    @ColumnInfo(name = "created_at") public long createdAt;
}
