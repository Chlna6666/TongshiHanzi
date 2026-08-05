/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.speech;

import android.speech.tts.Voice;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
final class VoiceProfileRegistry {
    private static final List<String> FEMALE_HINTS=Arrays.asList("female","woman","xiaoxiao","xiaoyi","xiaomeng","tingting","huihui");
    private static final List<String> MALE_HINTS=Arrays.asList("male","man","yunxi","yunjian","kangkang","danny");
    private VoiceProfileRegistry(){}
    static Voice select(List<Voice> voices,String preference){List<String> hints="male".equals(preference)?MALE_HINTS:FEMALE_HINTS;for(Voice voice:voices){String name=voice.getName().toLowerCase(Locale.ROOT);for(String hint:hints)if(name.contains(hint))return voice;}return voices.isEmpty()?null:voices.get(0);}
}
