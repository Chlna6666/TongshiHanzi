/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.ui.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.chlna6666.tongshihanzi.BuildConfig;
import com.chlna6666.tongshihanzi.R;
import com.chlna6666.tongshihanzi.util.MotionEffects;
import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;

public final class AboutFragment extends Fragment {
    private static final String PROJECT_URL =
            "https://github.com/Chlna6666/TongshiHanzi";
    private static final String PROFILE_URL =
            "https://github.com/Chlna6666";
    private static final String DICTIONARY_MANIFEST_ASSET =
            "dictionary/full_dictionary_manifest.json";
    private static final String STROKE_MANIFEST_ASSET =
            "dictionary/stroke_pack_manifest.json";

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle state
    ) {
        return inflater.inflate(R.layout.fragment_about, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        MotionEffects.enterPage(view);
        ((MaterialToolbar) view.findViewById(R.id.toolbar))
                .setNavigationOnClickListener(ignored ->
                        NavHostFragment.findNavController(this).navigateUp());

        ((TextView) view.findViewById(R.id.version)).setText(
                "版本 " + BuildConfig.VERSION_NAME + "（" + BuildConfig.VERSION_CODE + "）");

        View sourceButton = view.findViewById(R.id.source_button);
        View profileButton = view.findViewById(R.id.profile_button);
        MotionEffects.installPressFeedback(sourceButton);
        MotionEffects.installPressFeedback(profileButton);
        sourceButton.setOnClickListener(ignored -> openUrl(PROJECT_URL));
        profileButton.setOnClickListener(ignored -> openUrl(PROFILE_URL));

        TextView dataVersion = view.findViewById(R.id.data_version);
        dataVersion.setText(readDataSummary());
    }

    private String readDataSummary() {
        try {
            JSONObject dictionary = readJsonAsset(DICTIONARY_MANIFEST_ASSET);
            JSONObject strokePack = readJsonAsset(STROKE_MANIFEST_ASSET);
            NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.CHINA);

            int dictionaryCount = dictionary.optInt("characterCount", 0);
            String dictionaryVersion = dictionary.optString("dataVersion", "未知");
            String dictionaryCommit = dictionary.optString("sourceCommit", "");
            int strokeCount = strokePack.optInt("characterCount", 0);
            int curatedStrokeCount = strokePack.optInt("validatedCuratedCount", 0);
            JSONObject sourceCounts = strokePack.optJSONObject("sourceCounts");
            int primaryStrokes = sourceCounts == null
                    ? 0 : sourceCounts.optInt("makemeahanzi", 0);
            int fallbackStrokes = sourceCounts == null
                    ? 0 : sourceCounts.optInt("animcjk-zh-hans", 0);

            return "内置汉字：" + numbers.format(dictionaryCount) + " 个\n"
                    + "儿童审校覆盖：25 个核心汉字\n"
                    + "词库来源：mapull/chinese-dictionary\n"
                    + "词库版本：" + dictionaryVersion + "\n"
                    + "词库固定提交：" + shortCommit(dictionaryCommit) + "\n\n"
                    + "矢量笔顺：" + numbers.format(strokeCount) + " 个汉字\n"
                    + "审校字笔顺校验：" + curatedStrokeCount + "/25\n"
                    + "Make Me a Hanzi：" + numbers.format(primaryStrokes) + " 个\n"
                    + "AnimCJK 简体补充：" + numbers.format(fallbackStrokes) + " 个\n"
                    + "规范：中华人民共和国大陆简体字形与笔顺优先";
        } catch (Exception ignored) {
            return getString(R.string.about_dictionary_fallback);
        }
    }

    private JSONObject readJsonAsset(String asset) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                requireContext().getAssets().open(asset),
                StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            return new JSONObject(json.toString());
        }
    }

    private static String shortCommit(String value) {
        return value.length() > 12 ? value.substring(0, 12) : value;
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(requireContext(), "设备上没有可打开链接的应用", Toast.LENGTH_SHORT)
                    .show();
        }
    }
}
