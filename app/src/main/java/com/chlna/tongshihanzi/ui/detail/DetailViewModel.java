/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.ui.detail;

import android.app.Application;import androidx.annotation.NonNull;import androidx.lifecycle.AndroidViewModel;import androidx.lifecycle.LiveData;import androidx.lifecycle.MutableLiveData;import com.chlna.tongshihanzi.data.CharacterRepository;import com.chlna.tongshihanzi.data.dictionary.CharacterWithDetails;import com.chlna.tongshihanzi.data.user.UserRepository;
public final class DetailViewModel extends AndroidViewModel{
    private final CharacterRepository characters;private final UserRepository users;private final MutableLiveData<CharacterWithDetails> character=new MutableLiveData<>();private final MutableLiveData<Boolean> favorite=new MutableLiveData<>(false);private final MutableLiveData<String> error=new MutableLiveData<>();private int id=-1;
    public DetailViewModel(@NonNull Application app){super(app);characters=new CharacterRepository(app);users=new UserRepository(app);}public LiveData<CharacterWithDetails> character(){return character;}public LiveData<Boolean> favorite(){return favorite;}public LiveData<String> error(){return error;}
    public void load(int value){if(value<0||id==value&&character.getValue()!=null)return;id=value;characters.loadCharacter(value,character::postValue,t->error.postValue(t.getMessage()==null?"读取汉字失败":t.getMessage()));users.isFavorite(value,favorite::postValue);}public void toggleFavorite(){boolean next=!Boolean.TRUE.equals(favorite.getValue());favorite.setValue(next);users.setFavorite(id,next,null);}
}
