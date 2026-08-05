/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.util;
import android.content.Context;import androidx.appcompat.app.AppCompatDelegate;import androidx.preference.PreferenceManager;
public final class ThemeManager {private ThemeManager(){}public static void applySavedTheme(Context context){String mode=PreferenceManager.getDefaultSharedPreferences(context).getString("theme_mode","system");int value=switch(mode){case "light"->AppCompatDelegate.MODE_NIGHT_NO;case "dark"->AppCompatDelegate.MODE_NIGHT_YES;default->AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;};AppCompatDelegate.setDefaultNightMode(value);}}
