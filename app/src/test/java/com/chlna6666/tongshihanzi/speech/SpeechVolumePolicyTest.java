/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.speech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SpeechVolumePolicyTest {
    @Test
    public void clampsTheSupportedRange() {
        assertEquals(50, SpeechVolumePolicy.clampPercent(10));
        assertEquals(100, SpeechVolumePolicy.clampPercent(100));
        assertEquals(200, SpeechVolumePolicy.clampPercent(300));
    }

    @Test
    public void directTtsVolumeNeverExceedsOne() {
        assertEquals(0.5f, SpeechVolumePolicy.directVolume(50), 0.0001f);
        assertEquals(1.0f, SpeechVolumePolicy.directVolume(100), 0.0001f);
        assertEquals(1.0f, SpeechVolumePolicy.directVolume(200), 0.0001f);
    }

    @Test
    public void onlyValuesAboveOneHundredRequireSoftwareBoost() {
        assertFalse(SpeechVolumePolicy.requiresSoftwareBoost(100));
        assertTrue(SpeechVolumePolicy.requiresSoftwareBoost(101));
        assertTrue(SpeechVolumePolicy.requiresSoftwareBoost(200));
    }

    @Test
    public void twoHundredPercentMapsToApproximatelySixDecibels() {
        assertEquals(0, SpeechVolumePolicy.boostMillibels(100));
        assertEquals(352, SpeechVolumePolicy.boostMillibels(150));
        assertEquals(602, SpeechVolumePolicy.boostMillibels(200));
    }
}
