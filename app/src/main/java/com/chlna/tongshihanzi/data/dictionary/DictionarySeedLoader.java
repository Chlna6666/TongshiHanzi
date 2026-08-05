/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.data.dictionary;

import android.content.Context;
import com.chlna.tongshihanzi.domain.search.PinyinNormalizer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

final class DictionarySeedLoader {
    private DictionarySeedLoader() {}

    static void load(Context context, DictionaryDatabase db) throws Exception {
        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("dictionary/dictionary_seed.json"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
        }
        JSONObject root = new JSONObject(json.toString());
        List<DataSourceEntity> sources = parseSources(root.getJSONArray("sources"));
        List<CharacterEntity> characters = new ArrayList<>();
        List<PronunciationEntity> pronunciations = new ArrayList<>();
        List<DefinitionEntity> definitions = new ArrayList<>();
        List<WordEntity> words = new ArrayList<>();
        List<WubiEntity> wubi = new ArrayList<>();
        List<StrokeEntity> strokes = new ArrayList<>();
        List<SearchAliasEntity> aliases = new ArrayList<>();

        int pronunciationId = 1, definitionId = 1, wordId = 1, wubiId = 1, strokeId = 1;
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
                PronunciationEntity p = new PronunciationEntity();
                p.id = pronunciationId++;
                p.characterId = character.id;
                p.pinyinTone = reading.getString("pinyin");
                p.pinyinPlain = PinyinNormalizer.normalize(p.pinyinTone);
                p.tone = reading.optInt("tone", PinyinNormalizer.extractTone(p.pinyinTone));
                p.pinyinNumber = p.pinyinPlain + (p.tone == 0 ? "" : p.tone);
                p.primary = r == 0;
                p.speakWord = reading.optString("speakWord", character.character);
                p.displayOrder = r;
                pronunciations.add(p);

                JSONArray defs = reading.optJSONArray("definitions");
                if (defs != null) for (int d = 0; d < defs.length(); d++) {
                    DefinitionEntity entity = new DefinitionEntity();
                    entity.id = definitionId++;
                    entity.characterId = character.id;
                    entity.pronunciationId = p.id;
                    entity.kind = d == 0 ? "CHILD_SHORT" : "BASIC";
                    entity.text = defs.getString(d);
                    entity.displayOrder = d;
                    entity.sourceId = character.sourceId;
                    definitions.add(entity);
                }
                JSONArray wordValues = reading.optJSONArray("words");
                if (wordValues != null) for (int d = 0; d < wordValues.length(); d++) {
                    JSONObject item = wordValues.getJSONObject(d);
                    WordEntity entity = new WordEntity();
                    entity.id = wordId++;
                    entity.characterId = character.id;
                    entity.pronunciationId = p.id;
                    entity.word = item.getString("word");
                    entity.pinyin = item.optString("pinyin", "");
                    entity.definition = item.optString("definition", "");
                    entity.displayOrder = d;
                    words.add(entity);
                }
                addAlias(aliases, character.id, "PINYIN", p.pinyinPlain, 90);
                addAlias(aliases, character.id, "PINYIN_NUMBER", p.pinyinNumber, 95);
            }

            JSONArray codes = value.optJSONArray("wubi86");
            if (codes != null) for (int c = 0; c < codes.length(); c++) {
                WubiEntity entity = new WubiEntity();
                entity.id = wubiId++;
                entity.characterId = character.id;
                entity.code = codes.getString(c).toUpperCase();
                entity.primary = c == 0;
                wubi.add(entity);
                addAlias(aliases, character.id, "WUBI86", entity.code.toLowerCase(), 80);
            }
            JSONArray strokeNames = value.optJSONArray("strokeNames");
            if (strokeNames != null) for (int s = 0; s < strokeNames.length(); s++) {
                StrokeEntity entity = new StrokeEntity();
                entity.id = strokeId++;
                entity.characterId = character.id;
                entity.strokeIndex = s;
                entity.name = strokeNames.getString(s);
                strokes.add(entity);
            }
            addAlias(aliases, character.id, "CHARACTER", character.character, 100);
            if (!character.traditional.equals(character.character)) addAlias(aliases, character.id, "TRADITIONAL", character.traditional, 100);
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

    private static void addAlias(List<SearchAliasEntity> out, int characterId, String type, String value, int weight) {
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
