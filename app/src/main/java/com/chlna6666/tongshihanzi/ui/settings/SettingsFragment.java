/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.chlna6666.tongshihanzi.R;
import com.chlna6666.tongshihanzi.util.MotionEffects;

public final class SettingsFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle state
    ) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        MotionEffects.enterPage(view);
        if (state == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.settings_container, new SettingsPreferenceFragment())
                    .commit();
        }
    }
}
