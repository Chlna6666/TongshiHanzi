/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.ui.settings;

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

import com.chlna.tongshihanzi.BuildConfig;
import com.chlna.tongshihanzi.R;
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
    private static final String MANIFEST_ASSET =
            "dictionary/full_dictionary_manifest.json";

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
        ((MaterialToolbar) view.findViewById(R.id.toolbar))
                .setNavigationOnClickListener(ignored ->
                        NavHostFragment.findNavController(this).navigateUp());

        ((TextView) view.findViewById(R.id.version)).setText(
                "版本 " + BuildConfig.VERSION_NAME + "（" + BuildConfig.VERSION_CODE + "）");

        view.findViewById(R.id.source_button)
                .setOnClickListener(ignored -> openUrl(PROJECT_URL));
        view.findViewById(R.id.profile_button)
                .setOnClickListener(ignored -> openUrl(PROFILE_URL));

        TextView dataVersion = view.findViewById(R.id.data_version);
        dataVersion.setText(readDictionarySummary());
    }

    private String readDictionarySummary() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                requireContext().getAssets().open(MANIFEST_ASSET),
                StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            JSONObject manifest = new JSONObject(json.toString());
            int count = manifest.optInt("characterCount", 0);
            String version = manifest.optString("dataVersion", "未知");
            String commit = manifest.optString("sourceCommit", "");
            String formattedCount = NumberFormat.getIntegerInstance(Locale.CHINA)
                    .format(count);
            String shortCommit = commit.length() > 12 ? commit.substring(0, 12) : commit;
            return "内置汉字：" + formattedCount + " 个\n"
                    + "审校覆盖：25 个核心汉字\n"
                    + "扩展来源：mapull/chinese-dictionary\n"
                    + "数据版本：" + version + "\n"
                    + "固定提交：" + shortCommit;
        } catch (Exception ignored) {
            return getString(R.string.about_dictionary_fallback);
        }
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
