/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.speech;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SpeechTextPolicyTest {
    @Test
    public void characterPlaybackKeepsOnlyTheDisplayedCharacter() {
        assertEquals("一", SpeechTextPolicy.character("一"));
        assertEquals("人", SpeechTextPolicy.character("人民"));
        assertEquals("龘", SpeechTextPolicy.character("  龘  "));
    }

    @Test
    public void selectedPronunciationKeepsToneMarkedPinyin() {
        assertEquals("dài", SpeechTextPolicy.pronunciation(" dài "));
        assertEquals("háng", SpeechTextPolicy.pronunciation("háng"));
        assertEquals("", SpeechTextPolicy.pronunciation(null));
    }

    @Test
    public void wordPlaybackKeepsTheCompleteWord() {
        assertEquals("第一", SpeechTextPolicy.word("第一"));
        assertEquals("人民", SpeechTextPolicy.word(" 人民 "));
    }
}
