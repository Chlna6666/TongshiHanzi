/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.chlna6666.tongshihanzi.data.dictionary.CharacterWithDetails;
import org.junit.Test;

public final class UnicodeCharacterFallbackTest {
    @Test
    public void recognizesBmpAndSupplementaryCjkCharacters() {
        assertTrue(UnicodeCharacterFallback.isSingleCjkCharacter("龘"));
        assertTrue(UnicodeCharacterFallback.isSingleCjkCharacter("𠀀"));
        assertFalse(UnicodeCharacterFallback.isSingleCjkCharacter("人民"));
        assertFalse(UnicodeCharacterFallback.isSingleCjkCharacter("A"));
    }

    @Test
    public void createsNonNullMinimalDetails() {
        CharacterWithDetails details = UnicodeCharacterFallback.create("龘");
        assertEquals("龘", details.character.character);
        assertEquals("U+9F98", details.character.unicodeCodepoint);
        assertEquals(1, details.pronunciations.size());
        assertEquals(1, details.definitions.size());
        assertTrue(details.words.isEmpty());
        assertTrue(details.strokes.isEmpty());
    }
}
