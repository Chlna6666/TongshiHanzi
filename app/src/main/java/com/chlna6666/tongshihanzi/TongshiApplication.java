/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi;

import android.app.Application;
import androidx.preference.PreferenceManager;
import com.chlna6666.tongshihanzi.data.dictionary.DictionaryStore;
import com.chlna6666.tongshihanzi.speech.TtsManager;
import com.chlna6666.tongshihanzi.util.ThemeManager;
import com.google.android.material.color.DynamicColors;

public final class TongshiApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.applySavedTheme(this);
        boolean dynamic = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("dynamic_color", true);
        if (dynamic) {
            DynamicColors.applyToActivitiesIfAvailable(this);
        }
        DictionaryStore.getInstance(this).initialize();
        TtsManager.getInstance(this).initialize();
    }

    @Override
    public void onTerminate() {
        TtsManager.getInstance(this).shutdown();
        super.onTerminate();
    }
}
