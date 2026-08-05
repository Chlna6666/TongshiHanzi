/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.data.user;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {FavoriteEntity.class, SearchHistoryEntity.class}, version = 1, exportSchema = true)
public abstract class UserDatabase extends RoomDatabase {
    public abstract UserDao userDao();
    private static volatile UserDatabase instance;
    public static UserDatabase getInstance(Context context) {
        if (instance == null) synchronized (UserDatabase.class) {
            if (instance == null) instance = Room.databaseBuilder(context.getApplicationContext(), UserDatabase.class, "user.db").build();
        }
        return instance;
    }
}
