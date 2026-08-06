/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.ui.detail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.PreferenceManager;

import com.chlna6666.tongshihanzi.R;
import com.chlna6666.tongshihanzi.data.dictionary.CharacterWithDetails;
import com.chlna6666.tongshihanzi.data.dictionary.DefinitionEntity;
import com.chlna6666.tongshihanzi.data.dictionary.PronunciationEntity;
import com.chlna6666.tongshihanzi.data.dictionary.StrokeEntity;
import com.chlna6666.tongshihanzi.data.dictionary.WordEntity;
import com.chlna6666.tongshihanzi.data.stroke.StrokePackRepository;
import com.chlna6666.tongshihanzi.speech.TtsManager;
import com.chlna6666.tongshihanzi.util.MotionEffects;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class CharacterDetailFragment extends Fragment {
    private DetailViewModel viewModel;
    private CharacterWithDetails details;
    private PronunciationEntity selected;
    private TextView character;
    private TextView basicInfo;
    private TextView professionalInfo;
    private TextView strokeText;
    private TextView source;
    private LinearLayout definitions;
    private ChipGroup pronunciations;
    private ChipGroup words;
    private StrokeOrderView strokeView;
    private MaterialButton speakButton;
    private MaterialButton favorite;
    private CircularProgressIndicator progress;
    private List<StrokeEntity> renderedStrokes = Collections.emptyList();
    private boolean autoSpoken;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle state
    ) {
        return inflater.inflate(R.layout.fragment_character_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        MotionEffects.enterPage(view);
        viewModel = new ViewModelProvider(this).get(DetailViewModel.class);
        character = view.findViewById(R.id.character);
        basicInfo = view.findViewById(R.id.basic_info);
        professionalInfo = view.findViewById(R.id.professional_info);
        definitions = view.findViewById(R.id.definition_container);
        pronunciations = view.findViewById(R.id.pronunciations);
        words = view.findViewById(R.id.word_container);
        strokeView = view.findViewById(R.id.stroke_view);
        strokeText = view.findViewById(R.id.stroke_text);
        source = view.findViewById(R.id.source);
        speakButton = view.findViewById(R.id.speak_button);
        favorite = view.findViewById(R.id.favorite_button);
        progress = view.findViewById(R.id.progress);

        ((MaterialToolbar) view.findViewById(R.id.toolbar)).setNavigationOnClickListener(
                ignored -> NavHostFragment.findNavController(this).navigateUp());
        MotionEffects.installPressFeedback(speakButton);
        MotionEffects.installPressFeedback(favorite);
        MotionEffects.installPressFeedback(strokeText);
        speakButton.setOnClickListener(ignored -> speakCharacter());
        favorite.setOnClickListener(ignored -> viewModel.toggleFavorite());
        strokeText.setOnClickListener(ignored -> strokeView.startAnimation());
        strokeText.setContentDescription("点击文字开始播放笔顺动画");
        strokeView.setStepListener((index, name) -> {
            if (index >= 0 && index < renderedStrokes.size()) {
                strokeText.setText("第 " + (index + 1) + " 笔：" + name);
            } else {
                strokeText.setText(name);
            }
            strokeText.setContentDescription(strokeText.getText() + "，点击开始或重新播放");
        });

        viewModel.character().observe(getViewLifecycleOwner(), value -> {
            progress.setVisibility(View.GONE);
            if (value == null) {
                Snackbar.make(view, "未找到该汉字", Snackbar.LENGTH_LONG).show();
                return;
            }
            details = value;
            render();
        });
        viewModel.favorite().observe(getViewLifecycleOwner(), value -> {
            boolean saved = Boolean.TRUE.equals(value);
            favorite.setText(saved ? R.string.unfavorite : R.string.favorite);
            favorite.setChecked(saved);
        });
        viewModel.error().observe(getViewLifecycleOwner(), message -> {
            progress.setVisibility(View.GONE);
            if (message != null) {
                Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
            }
        });

        Bundle arguments = getArguments();
        int characterId = arguments == null ? -1 : arguments.getInt("characterId", -1);
        String characterText = arguments == null
                ? "" : arguments.getString("characterText", "");
        viewModel.load(characterId, characterText);
    }

    private void render() {
        character.setText(details.character.character);
        character.setContentDescription("汉字" + details.character.character);
        favorite.setEnabled(details.character.id >= 0);

        List<PronunciationEntity> readings = details.pronunciations.stream()
                .sorted(Comparator.comparingInt(value -> value.displayOrder))
                .collect(Collectors.toList());
        pronunciations.removeAllViews();
        selected = null;
        for (PronunciationEntity reading : readings) {
            Chip chip = new Chip(requireContext());
            chip.setText(reading.pinyinTone);
            chip.setCheckable(true);
            chip.setTag(reading);
            chip.setContentDescription("选择读音" + reading.pinyinTone);
            chip.setOnClickListener(value -> select((PronunciationEntity) value.getTag()));
            MotionEffects.installPressFeedback(chip);
            pronunciations.addView(chip);
            if (selected == null && reading.primary) {
                chip.setChecked(true);
                selected = reading;
            }
        }
        if (selected == null && !readings.isEmpty()) {
            selected = readings.get(0);
            ((Chip) pronunciations.getChildAt(0)).setChecked(true);
        }

        String traditional = details.character.traditional.equals(details.character.character)
                ? "同简体" : details.character.traditional;
        String wubi = details.wubiCodes.isEmpty() ? "待补充" : details.wubiCodes.get(0).code;
        String totalStrokes = details.character.totalStrokes <= 0
                ? "待补充" : String.valueOf(details.character.totalStrokes);
        basicInfo.setText(
                "拼音：" + joinPinyin(readings)
                        + "\n部首：" + details.character.radical
                        + "\n总笔画：" + totalStrokes
                        + "\n字形结构：" + details.character.structure
                        + "\n繁体字：" + traditional
                        + "\n五笔 86：" + wubi);
        professionalInfo.setText(
                "Unicode：" + details.character.unicodeCodepoint
                        + "\n笔顺编号：" + empty(details.character.strokeNumber)
                        + "\n常用字：" + (details.character.common ? "是" : "否")
                        + "\n频率排序：" + (details.character.frequencyRank >= 99999
                        ? "待补充" : details.character.frequencyRank));

        List<StrokeEntity> reviewedNames = new ArrayList<>(details.strokes);
        reviewedNames.sort(Comparator.comparingInt(value -> value.strokeIndex));
        StrokePackRepository strokeRepository = StrokePackRepository.getInstance(requireContext());
        renderedStrokes = strokeRepository.load(details.character.character, reviewedNames);
        strokeView.setData(details.character.character, renderedStrokes);
        strokeText.setEnabled(!renderedStrokes.isEmpty());

        String strokeAvailability = renderedStrokes.isEmpty()
                ? "该字暂无可验证的矢量笔顺，界面不会从字体轮廓伪造笔顺。"
                : "已从完整离线笔顺包按需读取 " + renderedStrokes.size() + " 笔矢量数据。";
        source.setText(details.character.id < 0
                ? "该字通过 Unicode 生僻字兜底展示，读音、部首和释义尚未经过项目审校。\n"
                + strokeAvailability
                : "数据来源标识：" + details.character.sourceId
                + "\n" + strokeAvailability
                + "\n笔顺包采用中国大陆规范顺序；许可与固定版本详见关于页面。");

        renderReading();

        if (PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getBoolean("auto_speak", false) && !autoSpoken) {
            autoSpoken = true;
            speakCharacter();
        }
    }

    private void select(PronunciationEntity reading) {
        selected = reading;
        renderReading();
    }

    private void renderReading() {
        if (details == null || selected == null) {
            return;
        }
        speakButton.setContentDescription(
                "朗读汉字" + details.character.character + "，读音" + selected.pinyinTone);

        definitions.removeAllViews();
        List<DefinitionEntity> definitionValues = details.definitions.stream()
                .filter(value -> value.pronunciationId == selected.id)
                .sorted(Comparator.comparingInt(value -> value.displayOrder))
                .collect(Collectors.toList());
        int number = 1;
        for (DefinitionEntity definition : definitionValues) {
            TextView item = new TextView(requireContext());
            item.setText((number++) + ". " + definition.text);
            item.setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            item.setPadding(0, 8, 0, 8);
            definitions.addView(item);
        }
        if (definitionValues.isEmpty()) {
            addEmpty(definitions, "该读音的释义尚未收录");
        }

        words.removeAllViews();
        List<WordEntity> wordValues = details.words.stream()
                .filter(value -> value.pronunciationId == selected.id)
                .sorted(Comparator.comparingInt(value -> value.displayOrder))
                .collect(Collectors.toList());
        for (WordEntity word : wordValues) {
            Chip chip = new Chip(requireContext());
            chip.setText(word.word
                    + (word.pinyin.trim().isEmpty() ? "" : "　" + word.pinyin));
            chip.setChipIconResource(R.drawable.ic_volume);
            chip.setChipIconVisible(true);
            chip.setCheckable(false);
            chip.setEnsureMinTouchTargetSize(true);
            chip.setContentDescription("朗读词语" + word.word);
            chip.setOnClickListener(ignored ->
                    TtsManager.getInstance(requireContext()).speakWord(word.word));
            MotionEffects.installPressFeedback(chip);
            words.addView(chip);
        }
        if (wordValues.isEmpty()) {
            Chip chip = new Chip(requireContext());
            chip.setText("暂无组词");
            chip.setEnabled(false);
            words.addView(chip);
        }
    }

    private void speakCharacter() {
        if (details == null) {
            return;
        }
        String pinyin = selected == null ? "" : selected.pinyinTone;
        boolean accepted = TtsManager.getInstance(requireContext())
                .speakPronunciation(details.character.character, pinyin);
        if (!accepted) {
            Toast.makeText(requireContext(), R.string.tts_unavailable, Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private static String joinPinyin(List<PronunciationEntity> values) {
        return values.stream().map(value -> value.pinyinTone)
                .reduce((first, second) -> first + "、" + second)
                .orElse("待补充");
    }

    private static String empty(String value) {
        return value == null || value.trim().isEmpty() ? "待补充" : value;
    }

    private static void addEmpty(LinearLayout container, String text) {
        TextView value = new TextView(container.getContext());
        value.setText(text);
        value.setPadding(0, 8, 0, 8);
        container.addView(value);
    }
}
