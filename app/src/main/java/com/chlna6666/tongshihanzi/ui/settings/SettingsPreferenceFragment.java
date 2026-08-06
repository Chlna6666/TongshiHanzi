/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.ui.settings;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.Voice;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.chlna6666.tongshihanzi.R;
import com.chlna6666.tongshihanzi.data.user.UserRepository;
import com.chlna6666.tongshihanzi.speech.TtsManager;
import com.chlna6666.tongshihanzi.util.MotionEffects;
import com.chlna6666.tongshihanzi.util.ThemeManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

public final class SettingsPreferenceFragment extends PreferenceFragmentCompat {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable readyListener = this::updateVoices;
    private final RecyclerView.OnChildAttachStateChangeListener motionRows =
            new RecyclerView.OnChildAttachStateChangeListener() {
                @Override
                public void onChildViewAttachedToWindow(@NonNull View view) {
                    MotionEffects.installPressFeedback(view);
                }

                @Override
                public void onChildViewDetachedFromWindow(@NonNull View view) {
                }
            };

    private TtsManager tts;
    private RecyclerView preferenceList;

    @Override
    public void onCreatePreferences(@Nullable Bundle state, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        tts = TtsManager.getInstance(requireContext());
        tts.addReadyListener(readyListener);

        ListPreference voiceMode = findPreference("voice_mode");
        if (voiceMode != null) {
            voiceMode.setOnPreferenceChangeListener((preference, value) -> {
                tts.setVoiceMode(String.valueOf(value), this::updateVoiceControls);
                return true;
            });
        }

        ListPreference voiceName = findPreference("voice_name");
        if (voiceName != null) {
            voiceName.setOnPreferenceChangeListener((preference, value) -> {
                tts.setManualVoice(String.valueOf(value), this::updateVoiceControls);
                return true;
            });
        }

        Preference speechRate = findPreference("speech_rate");
        if (speechRate != null) {
            speechRate.setOnPreferenceChangeListener((preference, value) -> {
                main.post(tts::refreshPreferences);
                return true;
            });
        }

        Preference test = findPreference("test_voice");
        if (test != null) {
            test.setOnPreferenceClickListener(preference -> {
                boolean accepted = tts.speak(
                        "你好，我是童识汉字。点击汉字，就可以听到它的读音。");
                if (!accepted) {
                    Snackbar.make(requireView(), R.string.tts_unavailable, Snackbar.LENGTH_LONG)
                            .show();
                }
                return true;
            });
        }

        Preference theme = findPreference("theme_mode");
        if (theme != null) {
            theme.setOnPreferenceChangeListener((preference, value) -> {
                preference.getSharedPreferences().edit()
                        .putString("theme_mode", String.valueOf(value))
                        .apply();
                ThemeManager.applySavedTheme(requireContext());
                return true;
            });
        }

        Preference dynamic = findPreference("dynamic_color");
        if (dynamic != null) {
            dynamic.setOnPreferenceChangeListener((preference, value) -> {
                requireActivity().recreate();
                return true;
            });
        }

        ListPreference motion = findPreference(MotionEffects.PREFERENCE_KEY);
        if (motion != null) {
            motion.setOnPreferenceChangeListener((preference, value) -> {
                main.post(this::updateMotionSummary);
                return true;
            });
        }

        Preference clear = findPreference("clear_history");
        if (clear != null) {
            clear.setOnPreferenceClickListener(preference -> {
                new UserRepository(requireContext()).clearHistory(() ->
                        requireActivity().runOnUiThread(() ->
                                Snackbar.make(requireView(), "查询历史已清除",
                                        Snackbar.LENGTH_SHORT).show()));
                return true;
            });
        }

        Preference about = findPreference("about");
        if (about != null) {
            about.setOnPreferenceClickListener(preference -> {
                NavHostFragment.findNavController(requireParentFragment())
                        .navigate(R.id.action_settings_to_about);
                return true;
            });
        }

        updateVoiceControls();
        updateMotionSummary();
    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        if (preference instanceof ListPreference) {
            showRoundedListDialog((ListPreference) preference);
            return;
        }
        super.onDisplayPreferenceDialog(preference);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        preferenceList = getListView();
        preferenceList.setItemAnimator(null);
        preferenceList.addOnChildAttachStateChangeListener(motionRows);
        for (int index = 0; index < preferenceList.getChildCount(); index++) {
            MotionEffects.installPressFeedback(preferenceList.getChildAt(index));
        }
    }

    @Override
    public void onDestroyView() {
        if (preferenceList != null) {
            preferenceList.removeOnChildAttachStateChangeListener(motionRows);
            preferenceList = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (tts != null) {
            tts.removeReadyListener(readyListener);
        }
        super.onDestroy();
    }

    private void showRoundedListDialog(ListPreference preference) {
        CharSequence[] entries = preference.getEntries();
        CharSequence[] values = preference.getEntryValues();
        if (entries == null || values == null || entries.length != values.length) {
            super.onDisplayPreferenceDialog(preference);
            return;
        }

        CharSequence title = preference.getDialogTitle();
        if (title == null) {
            title = preference.getTitle();
        }
        int selected = preference.findIndexOfValue(preference.getValue());
        new MaterialAlertDialogBuilder(
                requireContext(),
                R.style.ThemeOverlay_TongshiHanzi_MaterialAlertDialog)
                .setTitle(title)
                .setSingleChoiceItems(entries, selected, (dialog, which) -> {
                    if (which < 0 || which >= values.length) {
                        return;
                    }
                    String value = String.valueOf(values[which]);
                    if (preference.callChangeListener(value)) {
                        preference.setValue(value);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateVoices() {
        if (!isAdded()) {
            return;
        }
        ListPreference preference = findPreference("voice_name");
        if (preference == null) {
            return;
        }

        List<Voice> voices = tts.getChineseVoices();
        CharSequence[] entries = new CharSequence[voices.size()];
        CharSequence[] values = new CharSequence[voices.size()];
        for (int index = 0; index < voices.size(); index++) {
            Voice voice = voices.get(index);
            entries[index] = tts.describeVoice(voice);
            values[index] = voice.getName();
        }
        preference.setEntries(entries);
        preference.setEntryValues(values);
        updateVoiceControls();
    }

    private void updateVoiceControls() {
        if (!isAdded() || tts == null) {
            return;
        }
        ListPreference modePreference = findPreference("voice_mode");
        ListPreference voicePreference = findPreference("voice_name");
        if (modePreference == null || voicePreference == null) {
            return;
        }

        String mode = modePreference.getValue();
        if (mode == null || mode.trim().isEmpty()) {
            mode = "auto";
        }
        int modeIndex = modePreference.findIndexOfValue(mode);
        String modeLabel = modeIndex >= 0
                ? String.valueOf(modePreference.getEntries()[modeIndex])
                : "自动选择";
        String currentVoice = tts.currentVoiceDescription();
        String active = currentVoice == null ? "" : "\n当前：" + currentVoice;
        if (("male".equals(mode) || "female".equals(mode))
                && !tts.hasRecognizedProfile(mode)) {
            String requested = "male".equals(mode) ? "成年男声" : "成年女声";
            modePreference.setSummary("设备未提供明确标注的" + requested
                    + "，已排除儿童声并应用该声音偏好的成年音色" + active);
        } else {
            modePreference.setSummary(modeLabel + active);
        }

        List<Voice> voices = tts.getChineseVoices();
        boolean manual = "manual".equals(mode);
        voicePreference.setEnabled(manual && !voices.isEmpty());
        if (voices.isEmpty()) {
            voicePreference.setSummary(R.string.tts_unavailable);
        } else if (!manual) {
            voicePreference.setSummary("当前声音偏好会自动选择并校准非儿童中文语音");
        } else {
            int selectedIndex = voicePreference.findIndexOfValue(voicePreference.getValue());
            voicePreference.setSummary(selectedIndex >= 0
                    ? voicePreference.getEntries()[selectedIndex]
                    : "请选择一个已安装的中文语音");
        }
    }

    private void updateMotionSummary() {
        ListPreference motion = findPreference(MotionEffects.PREFERENCE_KEY);
        if (motion == null) {
            return;
        }
        int selected = motion.findIndexOfValue(motion.getValue());
        String label = selected >= 0
                ? String.valueOf(motion.getEntries()[selected])
                : "增强弹性";
        motion.setSummary(label + " · 可打断弹簧，不使用悬浮或循环动画");
    }
}
