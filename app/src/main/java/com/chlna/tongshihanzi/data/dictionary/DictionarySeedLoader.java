/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.data.dictionary;

import android.content.Context;

import com.chlna.tongshihanzi.domain.search.PinyinNormalizer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

final class DictionarySeedLoader {
    private static final String SEED_ASSET = "dictionary/dictionary_seed.json";
    private static final String FULL_MANIFEST_ASSET =
            "dictionary/full_dictionary_manifest.json";
    private static final String FULL_DATA_ASSET =
            "dictionary/full_dictionary.ndjson.bin";
    private static final String STROKE_ASSET = "dictionary/stroke_vectors.json";
    private static final int BATCH_CHARACTER_COUNT = 256;

    private DictionarySeedLoader() {
    }

    static void load(Context context, DictionaryDatabase database) throws Exception {
        JSONObject vectorCharacters = readOptionalStrokeCharacters(context);
        if (assetExists(context, FULL_DATA_ASSET)
                && assetExists(context, FULL_MANIFEST_ASSET)) {
            loadFullDictionary(context, database, vectorCharacters);
        } else {
            loadCuratedSeed(context, database, vectorCharacters);
        }
    }

    private static void loadFullDictionary(
            Context context,
            DictionaryDatabase database,
            JSONObject vectorCharacters
    ) throws Exception {
        JSONObject manifest = readJson(context, FULL_MANIFEST_ASSET);
        insertSources(database, parseSources(manifest.getJSONArray("sources")));

        Batch batch = new Batch();
        IdCounters counters = new IdCounters();
        try (InputStream raw = context.getAssets().open(FULL_DATA_ASSET);
             GZIPInputStream compressed = new GZIPInputStream(raw, 64 * 1024);
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     compressed, StandardCharsets.UTF_8), 64 * 1024)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                parseCharacter(new JSONObject(line), vectorCharacters, batch, counters);
                if (batch.characters.size() >= BATCH_CHARACTER_COUNT) {
                    flush(database, batch);
                }
            }
        }
        flush(database, batch);
    }

    private static void loadCuratedSeed(
            Context context,
            DictionaryDatabase database,
            JSONObject vectorCharacters
    ) throws Exception {
        JSONObject root = readJson(context, SEED_ASSET);
        insertSources(database, parseSources(root.getJSONArray("sources")));

        Batch batch = new Batch();
        IdCounters counters = new IdCounters();
        JSONArray entries = root.getJSONArray("characters");
        for (int index = 0; index < entries.length(); index++) {
            parseCharacter(entries.getJSONObject(index), vectorCharacters, batch, counters);
        }
        flush(database, batch);
    }

    private static void parseCharacter(
            JSONObject value,
            JSONObject vectorCharacters,
            Batch batch,
            IdCounters counters
    ) throws Exception {
        CharacterEntity character = new CharacterEntity();
        character.id = value.getInt("id");
        character.character = value.getString("character");
        character.simplified = value.optString("simplified", character.character);
        character.traditional = value.optString("traditional", character.character);
        character.unicodeCodepoint = value.optString(
                "unicode", String.format("U+%04X", character.character.codePointAt(0)));
        character.radical = value.optString("radical", "—");
        character.totalStrokes = value.optInt("strokes", 0);
        character.structure = value.optString("structure", "未分类结构");
        character.strokeNumber = value.optString("strokeNumber", "");
        character.frequencyRank = value.optInt("frequencyRank", 99999);
        character.common = value.optBoolean("common", false);
        character.sourceId = value.optString("sourceId", "project");
        batch.characters.add(character);

        JSONArray readings = value.optJSONArray("pronunciations");
        if (readings == null || readings.length() == 0) {
            readings = new JSONArray().put(new JSONObject()
                    .put("pinyin", "未收录")
                    .put("tone", 0)
                    .put("speakWord", character.character)
                    .put("definitions", new JSONArray().put("读音和释义仍待审校。")));
        }
        for (int readingIndex = 0; readingIndex < readings.length(); readingIndex++) {
            JSONObject reading = readings.getJSONObject(readingIndex);
            PronunciationEntity pronunciation = new PronunciationEntity();
            pronunciation.id = counters.pronunciationId++;
            pronunciation.characterId = character.id;
            pronunciation.pinyinTone = reading.optString("pinyin", "未收录");
            pronunciation.pinyinPlain = PinyinNormalizer.normalize(pronunciation.pinyinTone);
            pronunciation.tone = reading.optInt(
                    "tone", PinyinNormalizer.extractTone(pronunciation.pinyinTone));
            pronunciation.pinyinNumber = pronunciation.pinyinPlain
                    + (pronunciation.tone == 0 ? "" : pronunciation.tone);
            pronunciation.primary = readingIndex == 0;
            pronunciation.speakWord = reading.optString(
                    "speakWord", character.character);
            pronunciation.displayOrder = readingIndex;
            batch.pronunciations.add(pronunciation);

            JSONArray definitionValues = reading.optJSONArray("definitions");
            if (definitionValues != null) {
                for (int definitionIndex = 0;
                     definitionIndex < definitionValues.length();
                     definitionIndex++) {
                    Object rawDefinition = definitionValues.get(definitionIndex);
                    String text;
                    String sourceId = character.sourceId;
                    if (rawDefinition instanceof JSONObject) {
                        JSONObject definition = (JSONObject) rawDefinition;
                        text = definition.optString("text", "");
                        sourceId = definition.optString("sourceId", sourceId);
                    } else {
                        text = String.valueOf(rawDefinition);
                    }
                    if (text.trim().isEmpty()) {
                        continue;
                    }
                    DefinitionEntity entity = new DefinitionEntity();
                    entity.id = counters.definitionId++;
                    entity.characterId = character.id;
                    entity.pronunciationId = pronunciation.id;
                    entity.kind = definitionIndex == 0 ? "CHILD_SHORT" : "BASIC";
                    entity.text = text;
                    entity.displayOrder = definitionIndex;
                    entity.sourceId = sourceId;
                    batch.definitions.add(entity);
                }
            }

            JSONArray wordValues = reading.optJSONArray("words");
            if (wordValues != null) {
                for (int wordIndex = 0; wordIndex < wordValues.length(); wordIndex++) {
                    JSONObject item = wordValues.getJSONObject(wordIndex);
                    String word = item.optString("word", "").trim();
                    if (word.isEmpty()) {
                        continue;
                    }
                    WordEntity entity = new WordEntity();
                    entity.id = counters.wordId++;
                    entity.characterId = character.id;
                    entity.pronunciationId = pronunciation.id;
                    entity.word = word;
                    entity.pinyin = item.optString("pinyin", "");
                    entity.definition = item.optString("definition", "");
                    entity.displayOrder = wordIndex;
                    batch.words.add(entity);
                }
            }
            if (!pronunciation.pinyinPlain.isEmpty()) {
                addAlias(batch.aliases, character.id, "PINYIN",
                        pronunciation.pinyinPlain, 90);
            }
            if (!pronunciation.pinyinNumber.isEmpty()) {
                addAlias(batch.aliases, character.id, "PINYIN_NUMBER",
                        pronunciation.pinyinNumber, 95);
            }
        }

        JSONArray codes = value.optJSONArray("wubi86");
        if (codes != null) {
            for (int codeIndex = 0; codeIndex < codes.length(); codeIndex++) {
                String code = codes.optString(codeIndex, "").trim();
                if (code.isEmpty()) {
                    continue;
                }
                WubiEntity entity = new WubiEntity();
                entity.id = counters.wubiId++;
                entity.characterId = character.id;
                entity.code = code.toUpperCase();
                entity.primary = codeIndex == 0;
                batch.wubi.add(entity);
                addAlias(batch.aliases, character.id, "WUBI86",
                        entity.code.toLowerCase(), 80);
            }
        }

        JSONObject vector = vectorCharacters.optJSONObject(character.character);
        JSONArray vectorPaths = vector == null ? null : vector.optJSONArray("strokes");
        JSONArray vectorMedians = vector == null ? null : vector.optJSONArray("medians");
        JSONArray strokeNames = value.optJSONArray("strokeNames");
        int strokeCount = Math.max(
                strokeNames == null ? 0 : strokeNames.length(),
                vectorPaths == null ? 0 : vectorPaths.length());
        for (int strokeIndex = 0; strokeIndex < strokeCount; strokeIndex++) {
            StrokeEntity entity = new StrokeEntity();
            entity.id = counters.strokeId++;
            entity.characterId = character.id;
            entity.strokeIndex = strokeIndex;
            entity.name = strokeNames != null && strokeIndex < strokeNames.length()
                    ? strokeNames.optString(strokeIndex, "第 " + (strokeIndex + 1) + " 笔")
                    : "第 " + (strokeIndex + 1) + " 笔";
            entity.pathData = vectorPaths != null && strokeIndex < vectorPaths.length()
                    ? vectorPaths.optString(strokeIndex, "") : "";
            JSONArray median = vectorMedians != null && strokeIndex < vectorMedians.length()
                    ? vectorMedians.optJSONArray(strokeIndex) : null;
            entity.medianData = median == null ? "" : median.toString();
            batch.strokes.add(entity);
        }

        addAlias(batch.aliases, character.id, "CHARACTER", character.character, 100);
        if (!character.traditional.equals(character.character)) {
            character.traditional.codePoints().forEach(codePoint -> addAlias(
                    batch.aliases,
                    character.id,
                    "TRADITIONAL",
                    new String(Character.toChars(codePoint)),
                    100));
        }
    }

    private static void insertSources(
            DictionaryDatabase database,
            List<DataSourceEntity> sources
    ) {
        database.runInTransaction(() -> database.dictionaryDao().insertSources(sources));
    }

    private static void flush(DictionaryDatabase database, Batch batch) {
        if (batch.characters.isEmpty()) {
            return;
        }
        database.runInTransaction(() -> {
            DictionaryDao dao = database.dictionaryDao();
            dao.insertCharacters(batch.characters);
            dao.insertPronunciations(batch.pronunciations);
            dao.insertDefinitions(batch.definitions);
            dao.insertWords(batch.words);
            dao.insertWubi(batch.wubi);
            dao.insertStrokes(batch.strokes);
            dao.insertAliases(batch.aliases);
        });
        batch.clear();
    }

    private static JSONObject readJson(Context context, String asset) throws Exception {
        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(asset), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
        }
        return new JSONObject(json.toString());
    }

    private static JSONObject readOptionalStrokeCharacters(Context context) throws Exception {
        try {
            JSONObject root = readJson(context, STROKE_ASSET);
            JSONObject characters = root.optJSONObject("characters");
            return characters == null ? new JSONObject() : characters;
        } catch (FileNotFoundException ignored) {
            return new JSONObject();
        }
    }

    private static boolean assetExists(Context context, String asset) {
        try (InputStream ignored = context.getAssets().open(asset)) {
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void addAlias(
            List<SearchAliasEntity> output,
            int characterId,
            String type,
            String value,
            int weight
    ) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        SearchAliasEntity alias = new SearchAliasEntity();
        alias.characterId = characterId;
        alias.aliasType = type;
        alias.normalizedText = value;
        alias.weight = weight;
        output.add(alias);
    }

    private static List<DataSourceEntity> parseSources(JSONArray values) throws Exception {
        List<DataSourceEntity> result = new ArrayList<>();
        for (int index = 0; index < values.length(); index++) {
            JSONObject value = values.getJSONObject(index);
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

    private static final class IdCounters {
        int pronunciationId = 1;
        int definitionId = 1;
        int wordId = 1;
        int wubiId = 1;
        int strokeId = 1;
    }

    private static final class Batch {
        final List<CharacterEntity> characters = new ArrayList<>();
        final List<PronunciationEntity> pronunciations = new ArrayList<>();
        final List<DefinitionEntity> definitions = new ArrayList<>();
        final List<WordEntity> words = new ArrayList<>();
        final List<WubiEntity> wubi = new ArrayList<>();
        final List<StrokeEntity> strokes = new ArrayList<>();
        final List<SearchAliasEntity> aliases = new ArrayList<>();

        void clear() {
            characters.clear();
            pronunciations.clear();
            definitions.clear();
            words.clear();
            wubi.clear();
            strokes.clear();
            aliases.clear();
        }
    }
}
