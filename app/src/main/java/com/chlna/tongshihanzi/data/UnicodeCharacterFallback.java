/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.data;

import com.chlna.tongshihanzi.data.dictionary.CharacterEntity;
import com.chlna.tongshihanzi.data.dictionary.CharacterWithDetails;
import com.chlna.tongshihanzi.data.dictionary.DefinitionEntity;
import com.chlna.tongshihanzi.data.dictionary.PronunciationEntity;
import java.util.ArrayList;

/** Creates a safe, minimal detail record for CJK characters not yet in the reviewed dictionary. */
public final class UnicodeCharacterFallback {
    private UnicodeCharacterFallback() {}

    public static boolean isSingleCjkCharacter(String value) {
        if (value == null) return false;
        String text = value.trim();
        if (text.isEmpty() || text.codePointCount(0, text.length()) != 1) return false;
        return isCjkCodePoint(text.codePointAt(0));
    }

    public static int syntheticId(String character) {
        return -character.trim().codePointAt(0);
    }

    public static CharacterWithDetails create(String character) {
        String text = character.trim();
        int codePoint = text.codePointAt(0);
        int id = -codePoint;
        String code = String.format("U+%04X", codePoint);

        CharacterEntity entity = new CharacterEntity();
        entity.id = id;
        entity.character = text;
        entity.simplified = text;
        entity.traditional = text;
        entity.unicodeCodepoint = code;
        entity.radical = "待补充";
        entity.totalStrokes = 0;
        entity.structure = "待补充";
        entity.strokeNumber = "";
        entity.frequencyRank = 99999;
        entity.common = false;
        entity.sourceId = "unicode-fallback";

        PronunciationEntity pronunciation = new PronunciationEntity();
        pronunciation.id = id;
        pronunciation.characterId = id;
        pronunciation.pinyinTone = "读音待补充";
        pronunciation.pinyinPlain = "";
        pronunciation.pinyinNumber = "";
        pronunciation.tone = 0;
        pronunciation.primary = true;
        pronunciation.speakWord = text;
        pronunciation.displayOrder = 0;

        DefinitionEntity definition = new DefinitionEntity();
        definition.id = id;
        definition.characterId = id;
        definition.pronunciationId = id;
        definition.kind = "UNICODE_FALLBACK";
        definition.text = "该字尚未收入内置审校词库，当前提供 Unicode 基础展示与系统朗读。编码：" + code + "。";
        definition.displayOrder = 0;
        definition.sourceId = "unicode-fallback";

        CharacterWithDetails details = new CharacterWithDetails();
        details.character = entity;
        details.pronunciations = new ArrayList<>();
        details.pronunciations.add(pronunciation);
        details.definitions = new ArrayList<>();
        details.definitions.add(definition);
        details.words = new ArrayList<>();
        details.wubiCodes = new ArrayList<>();
        details.strokes = new ArrayList<>();
        return details;
    }

    private static boolean isCjkCodePoint(int value) {
        return inRange(value, 0x3400, 0x4DBF)
                || inRange(value, 0x4E00, 0x9FFF)
                || inRange(value, 0xF900, 0xFAFF)
                || inRange(value, 0x20000, 0x2FA1F)
                || inRange(value, 0x30000, 0x323AF);
    }

    private static boolean inRange(int value, int start, int end) {
        return value >= start && value <= end;
    }
}
