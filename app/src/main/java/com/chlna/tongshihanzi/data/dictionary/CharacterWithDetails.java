/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.data.dictionary;

import androidx.room.Embedded;
import androidx.room.Relation;
import java.util.List;

public class CharacterWithDetails {
    @Embedded public CharacterEntity character;
    @Relation(parentColumn = "id", entityColumn = "character_id") public List<PronunciationEntity> pronunciations;
    @Relation(parentColumn = "id", entityColumn = "character_id") public List<DefinitionEntity> definitions;
    @Relation(parentColumn = "id", entityColumn = "character_id") public List<WordEntity> words;
    @Relation(parentColumn = "id", entityColumn = "character_id") public List<WubiEntity> wubiCodes;
    @Relation(parentColumn = "id", entityColumn = "character_id") public List<StrokeEntity> strokes;
}
