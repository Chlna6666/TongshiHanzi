/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.data.dictionary;

import android.content.Context;
import com.chlna.tongshihanzi.domain.search.PinyinNormalizer;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

final class DictionarySeedLoader {
    private static final String SEED_ASSET = "dictionary/dictionary_seed.json";
    private static final String STROKE_ASSET = "dictionary/stroke_vectors.json";

    private DictionarySeedLoader() {}

    static void load(Context context, DictionaryDatabase db) throws Exception {
        JSONObject root = readJson(context, SEED_ASSET);
        JSONObject vectorCharacters = readOptionalStrokeCharacters(context);
        List<DataSourceEntity> sources = parseSources(root.getJSONArray("sources"));
        List<CharacterEntity> characters = new ArrayList<>();
        List<PronunciationEntity> pronunciations = new ArrayList<>();
        List<DefinitionEntity> definitions = new ArrayList<>();
        List<WordEntity> words = new ArrayList<>();
        List<WubiEntity> wubi = new ArrayList<>();
        List<StrokeEntity> strokes = new ArrayList<>();
        List<SearchAliasEntity> aliases = new ArrayList<>();

        int pronunciationId = 1;
        int definitionId = 1;
        int wordId = 1;
        int wubiId = 1;
        int strokeId = 1;
        JSONArray entries = root.getJSONArray("characters");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject value = entries.getJSONObject(i);
            CharacterEntity character = new CharacterEntity();
            character.id = value.getInt("id");
            character.character = value.getString("character");
            character.simplified = value.optString("simplified", character.character);
            character.traditional = value.optString("traditional", character.character);
            character.unicodeCodepoint = value.getString("unicode");
            character.radical = value.getString("radical");
            character.totalStrokes = value.getInt("strokes");
            character.structure = value.optString("structure", "独体结构");
            character.strokeNumber = value.optString("strokeNumber", "");
            character.frequencyRank = value.optInt("frequencyRank", 9999);
            character.common = value.optBoolean("common", true);
            character.sourceId = value.optString("sourceId", "project");
            characters.add(character);

            JSONArray readings = value.getJSONArray("pronunciations");
            for (int r = 0; r < readings.length(); r++) {
                JSONObject reading = readings.getJSONObject(r);
                PronunciationEntity pronunciation = new PronunciationEntity();
                pronunciation.id = pronunciationId++;
                pronunciation.characterId = character.id;
                pronunciation.pinyinTone = reading.getString("pinyin");
                pronunciation.pinyinPlain = PinyinNormalizer.normalize(pronunciation.pinyinTone);
                pronunciation.tone = reading.optInt(
                        "tone", PinyinNormalizer.extractTone(pronunciation.pinyinTone));
                pronunciation.pinyinNumber = pronunciation.pinyinPlain
                        + (pronunciation.tone == 0 ? "" : pronunciation.tone);
                pronunciation.primary = r == 0;
                pronunciation.speakWord = reading.optString("speakWord", character.character);
                pronunciation.displayOrder = r;
                pronunciations.add(pronunciation);

                JSONArray definitionValues = reading.optJSONArray("definitions");
                if (definitionValues != null) {
                    for (int d = 0; d < definitionValues.length(); d++) {
                        DefinitionEntity entity = new DefinitionEntity();
                        entity.id = definitionId++;
                        entity.characterId = character.id;
                        entity.pronunciationId = pronunciation.id;
                        entity.kind = d == 0 ? "CHILD_SHORT" : "BASIC";
                        entity.text = definitionValues.getString(d);
                        entity.displayOrder = d;
                        entity.sourceId = character.sourceId;
                        definitions.add(entity);
                    }
                }

                JSONArray wordValues = reading.optJSONArray("words");
                if (wordValues != null) {
                    for (int d = 0; d < wordValues.length(); d++) {
                        JSONObject item = wordValues.getJSONObject(d);
                        WordEntity entity = new WordEntity();
                        entity.id = wordId++;
                        entity.characterId = character.id;
                        entity.pronunciationId = pronunciation.id;
                        entity.word = item.getString("word");
                        entity.pinyin = item.optString("pinyin", "");
                        entity.definition = item.optString("definition", "");
                        entity.displayOrder = d;
                        words.add(entity);
                    }
                }
                addAlias(aliases, character.id, "PINYIN", pronunciation.pinyinPlain, 90);
                addAlias(aliases, character.id, "PINYIN_NUMBER", pronunciation.pinyinNumber, 95);
            }

            JSONArray codes = value.optJSONArray("wubi86");
            if (codes != null) {
                for (int c = 0; c < codes.length(); c++) {
                    WubiEntity entity = new WubiEntity();
                    entity.id = wubiId++;
                    entity.characterId = character.id;
                    entity.code = codes.getString(c).toUpperCase();
                    entity.primary = c == 0;
                    wubi.add(entity);
                    addAlias(aliases, character.id, "WUBI86", entity.code.toLowerCase(), 80);
                }
            }

            JSONObject vector = vectorCharacters.optJSONObject(character.character);
            JSONArray vectorPaths = vector == null ? null : vector.optJSONArray("strokes");
            JSONArray vectorMedians = vector == null ? null : vector.optJSONArray("medians");
            JSONArray strokeNames = value.optJSONArray("strokeNames");
            int strokeCount = Math.max(
                    strokeNames == null ? 0 : strokeNames.length(),
                    vectorPaths == null ? 0 : vectorPaths.length());
            for (int s = 0; s < strokeCount; s++) {
                StrokeEntity entity = new StrokeEntity();
                entity.id = strokeId++;
                entity.characterId = character.id;
                entity.strokeIndex = s;
                entity.name = strokeNames != null && s < strokeNames.length()
                        ? strokeNames.optString(s, "第 " + (s + 1) + " 笔")
                        : "第 " + (s + 1) + " 笔";
                entity.pathData = vectorPaths != null && s < vectorPaths.length()
                        ? vectorPaths.optString(s, "") : "";
                JSONArray median = vectorMedians != null && s < vectorMedians.length()
                        ? vectorMedians.optJSONArray(s) : null;
                entity.medianData = median == null ? "" : median.toString();
                strokes.add(entity);
            }

            addAlias(aliases, character.id, "CHARACTER", character.character, 100);
            if (!character.traditional.equals(character.character)) {
                addAlias(aliases, character.id, "TRADITIONAL", character.traditional, 100);
            }
        }

        db.runInTransaction(() -> {
            DictionaryDao dao = db.dictionaryDao();
            dao.insertSources(sources);
            dao.insertCharacters(characters);
            dao.insertPronunciations(pronunciations);
            dao.insertDefinitions(definitions);
            dao.insertWords(words);
            dao.insertWubi(wubi);
            dao.insertStrokes(strokes);
            dao.insertAliases(aliases);
        });
    }

    private static JSONObject readJson(Context context, String asset) throws Exception {
        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(asset), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
        }
        return new JSONObject(json.toString());
    }

    private static JSONObject readOptionalStrokeCharacters(Context context) throws Exception {
        try {
            return readJson(context, STROKE_ASSET).optJSONObject("characters") == null
                    ? new JSONObject()
                    : readJson(context, STROKE_ASSET).getJSONObject("characters");
        } catch (FileNotFoundException ignored) {
            return new JSONObject();
        }
    }

    private static void addAlias(
            List<SearchAliasEntity> out, int characterId, String type, String value, int weight) {
        SearchAliasEntity alias = new SearchAliasEntity();
        alias.characterId = characterId;
        alias.aliasType = type;
        alias.normalizedText = value;
        alias.weight = weight;
        out.add(alias);
    }

    private static List<DataSourceEntity> parseSources(JSONArray values) throws Exception {
        List<DataSourceEntity> result = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.getJSONObject(i);
            DataSourceEntity source = new DataSourceEntity();
            source.sourceId = value.getString("sourceId");
            source.name = value.getString("name");
            source.version = value.optString("version", "");
            source.licenseId = value.getString("licenseId");
            source.attribution = value.optString("attribution", "");
            source.modificationNote = value.optString("modificationNote", "");
            result.add(source);
        }
        return result;
    }
}
