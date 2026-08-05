/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.data.user;

import android.content.Context;
import com.chlna6666.tongshihanzi.util.AppExecutors;
import java.util.List;
import java.util.function.Consumer;

public final class UserRepository {
    private final UserDao dao;
    public UserRepository(Context context) { dao = UserDatabase.getInstance(context).userDao(); }
    public void favoriteIds(Consumer<List<Integer>> callback) { AppExecutors.io().execute(() -> callback.accept(dao.favoriteIds())); }
    public void history(Consumer<List<SearchHistoryEntity>> callback) { AppExecutors.io().execute(() -> callback.accept(dao.recentHistory(50))); }
    public void isFavorite(int id, Consumer<Boolean> callback) { AppExecutors.io().execute(() -> callback.accept(dao.isFavorite(id))); }
    public void setFavorite(int id, boolean favorite, Runnable done) { AppExecutors.io().execute(() -> { if (favorite) { FavoriteEntity entity = new FavoriteEntity(); entity.characterId=id; entity.createdAt=System.currentTimeMillis(); dao.addFavorite(entity); } else dao.removeFavorite(id); if (done != null) done.run(); }); }
    public void saveHistory(String query, int characterId) { AppExecutors.io().execute(() -> { SearchHistoryEntity entity=new SearchHistoryEntity(); entity.query=query; entity.characterId=characterId; entity.searchedAt=System.currentTimeMillis(); dao.saveHistory(entity); }); }
    public void clearHistory(Runnable done) { AppExecutors.io().execute(() -> { dao.clearHistory(); if(done!=null) done.run(); }); }
}
