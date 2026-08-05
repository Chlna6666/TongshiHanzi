/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.ui.detail;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.chlna6666.tongshihanzi.data.CharacterRepository;
import com.chlna6666.tongshihanzi.data.dictionary.CharacterWithDetails;
import com.chlna6666.tongshihanzi.data.user.UserRepository;

public final class DetailViewModel extends AndroidViewModel {
    private final CharacterRepository characters;
    private final UserRepository users;
    private final MutableLiveData<CharacterWithDetails> character = new MutableLiveData<>();
    private final MutableLiveData<Boolean> favorite = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private int id = -1;
    private String characterText = "";

    public DetailViewModel(@NonNull Application app) {
        super(app);
        characters = new CharacterRepository(app);
        users = new UserRepository(app);
    }

    public LiveData<CharacterWithDetails> character() { return character; }
    public LiveData<Boolean> favorite() { return favorite; }
    public LiveData<String> error() { return error; }

    public void load(int value, String fallbackText) {
        String safeText = fallbackText == null ? "" : fallbackText.trim();
        if (id == value && characterText.equals(safeText) && character.getValue() != null) return;
        id = value;
        characterText = safeText;
        characters.loadCharacter(
                value,
                safeText,
                character::postValue,
                throwable -> error.postValue(throwable.getMessage() == null
                        ? "读取汉字失败" : throwable.getMessage()));
        if (value >= 0) users.isFavorite(value, favorite::postValue);
        else favorite.postValue(false);
    }

    public void load(int value) {
        load(value, "");
    }

    public void toggleFavorite() {
        if (id < 0) {
            error.setValue("该生僻字尚未进入审校词库，暂不支持收藏");
            return;
        }
        boolean next = !Boolean.TRUE.equals(favorite.getValue());
        favorite.setValue(next);
        users.setFavorite(id, next, null);
    }
}
