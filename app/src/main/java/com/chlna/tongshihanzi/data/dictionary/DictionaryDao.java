/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.data.dictionary;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import java.util.List;

@Dao
public interface DictionaryDao {
    String BASE_SELECT = "SELECT c.id AS character_id, c.character_text AS character, " +
            "COALESCE((SELECT GROUP_CONCAT(p.pinyin_tone, ' / ') FROM pronunciations p WHERE p.character_id=c.id ORDER BY p.display_order), '') AS pinyin, " +
            "c.radical AS radical, c.total_strokes AS total_strokes, " +
            "COALESCE((SELECT d.text FROM definitions d WHERE d.character_id=c.id ORDER BY d.display_order LIMIT 1), '') AS definition, " +
            "COALESCE((SELECT w.code FROM wubi_codes w WHERE w.character_id=c.id ORDER BY w.is_primary DESC LIMIT 1), '') AS wubi, " +
            "c.frequency_rank AS frequency_rank FROM characters c ";

    @Query("SELECT COUNT(*) FROM characters") int countCharacters();

    @Query(BASE_SELECT + "WHERE c.character_text = :query OR c.simplified = :query OR c.traditional = :query ORDER BY c.frequency_rank LIMIT :limit")
    List<SearchRow> searchCharacters(String query, int limit);

    @Query(BASE_SELECT + "WHERE c.id IN (SELECT p.character_id FROM pronunciations p WHERE p.pinyin_plain = :query) ORDER BY c.frequency_rank LIMIT :limit")
    List<SearchRow> searchPinyinExact(String query, int limit);

    @Query(BASE_SELECT + "WHERE c.id IN (SELECT p.character_id FROM pronunciations p WHERE p.pinyin_plain LIKE :prefix || '%') ORDER BY c.frequency_rank LIMIT :limit")
    List<SearchRow> searchPinyinPrefix(String prefix, int limit);

    @Query(BASE_SELECT + "WHERE c.id IN (SELECT w.character_id FROM wubi_codes w WHERE UPPER(w.code) = UPPER(:query)) ORDER BY c.frequency_rank LIMIT :limit")
    List<SearchRow> searchWubiExact(String query, int limit);

    @Query(BASE_SELECT + "WHERE c.id IN (SELECT w.character_id FROM wubi_codes w WHERE UPPER(w.code) LIKE UPPER(:prefix) || '%') ORDER BY c.frequency_rank LIMIT :limit")
    List<SearchRow> searchWubiPrefix(String prefix, int limit);

    @Query(BASE_SELECT + "WHERE c.total_strokes BETWEEN :min AND :max ORDER BY c.total_strokes, c.frequency_rank LIMIT :limit")
    List<SearchRow> searchStrokes(int min, int max, int limit);

    @Query(BASE_SELECT + "WHERE c.id IN (SELECT a.character_id FROM search_alias a WHERE a.normalized_text LIKE :prefix || '%') ORDER BY c.frequency_rank LIMIT :limit")
    List<SearchRow> searchAliases(String prefix, int limit);

    @Query(BASE_SELECT + "WHERE c.id IN (:ids) ORDER BY c.frequency_rank")
    List<SearchRow> getRowsByIds(List<Integer> ids);

    @Query(BASE_SELECT + "WHERE c.is_common = 1 ORDER BY c.frequency_rank LIMIT :limit")
    List<SearchRow> commonCharacters(int limit);

    @Transaction
    @Query("SELECT * FROM characters WHERE id = :id")
    CharacterWithDetails getCharacter(int id);

    @Query("SELECT * FROM data_sources WHERE source_id = :sourceId")
    DataSourceEntity getSource(String sourceId);

    @Insert(onConflict = OnConflictStrategy.REPLACE) void insertCharacters(List<CharacterEntity> values);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insertPronunciations(List<PronunciationEntity> values);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insertDefinitions(List<DefinitionEntity> values);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insertWords(List<WordEntity> values);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insertWubi(List<WubiEntity> values);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insertStrokes(List<StrokeEntity> values);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insertAliases(List<SearchAliasEntity> values);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insertSources(List<DataSourceEntity> values);
}
