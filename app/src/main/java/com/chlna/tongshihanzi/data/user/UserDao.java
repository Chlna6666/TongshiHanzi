/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.data.user;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface UserDao {
    @Query("SELECT character_id FROM favorites ORDER BY created_at DESC") List<Integer> favoriteIds();
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE character_id=:id)") boolean isFavorite(int id);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void addFavorite(FavoriteEntity entity);
    @Query("DELETE FROM favorites WHERE character_id=:id") void removeFavorite(int id);
    @Insert(onConflict = OnConflictStrategy.REPLACE) long saveHistory(SearchHistoryEntity entity);
    @Query("SELECT * FROM search_history ORDER BY searched_at DESC LIMIT :limit") List<SearchHistoryEntity> recentHistory(int limit);
    @Query("DELETE FROM search_history") void clearHistory();
}
