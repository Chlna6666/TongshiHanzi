/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.speech;

import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Conservative classifier for engine-specific Chinese voice names.
 *
 * <p>Android does not expose standardized gender or age fields for a {@link Voice}. Therefore this
 * registry only assigns an adult profile when the engine name or feature metadata contains a known
 * signal. Unknown voices remain neutral instead of being mislabeled as male or female.</p>
 */
final class VoiceProfileRegistry {
    enum Profile {
        ADULT_FEMALE("成年女声"),
        ADULT_MALE("成年男声"),
        CHILD("儿童声"),
        UNKNOWN("未标注音色");

        final String displayName;

        Profile(String displayName) {
            this.displayName = displayName;
        }
    }

    private static final List<String> CHILD_HINTS = Arrays.asList(
            "child", "kid", "girl", "boy",
            "xiaoyi", "xiaoshuang", "xiaoyou", "xiaobei"
    );
    private static final List<String> FEMALE_HINTS = Arrays.asList(
            "female", "woman", "adult_female", "gender=female",
            "xiaoxiao", "xiaomeng", "xiaohan", "xiaomo", "xiaorui",
            "xiaozhen", "xiaoyan", "tingting", "huihui"
    );
    private static final List<String> MALE_HINTS = Arrays.asList(
            "male", "man", "adult_male", "gender=male",
            "yunxi", "yunjian", "yunyang", "yunhao", "yunfeng",
            "kangkang", "danny"
    );

    private VoiceProfileRegistry() {
    }

    @Nullable
    static Voice select(List<Voice> voices, String preference) {
        Profile requested;
        if ("female".equals(preference)) {
            requested = Profile.ADULT_FEMALE;
        } else if ("male".equals(preference)) {
            requested = Profile.ADULT_MALE;
        } else {
            return selectAutomatic(voices);
        }

        List<Voice> matches = new ArrayList<>();
        for (Voice voice : voices) {
            if (classify(voice) == requested && isInstalled(voice)) {
                matches.add(voice);
            }
        }
        matches.sort(VOICE_PRIORITY);
        return matches.isEmpty() ? null : matches.get(0);
    }

    @Nullable
    static Voice selectAutomatic(List<Voice> voices) {
        List<Voice> candidates = new ArrayList<>();
        for (Voice voice : voices) {
            Profile profile = classify(voice);
            if (profile != Profile.CHILD && isInstalled(voice)) {
                candidates.add(voice);
            }
        }
        candidates.sort(VOICE_PRIORITY);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    static boolean hasProfile(List<Voice> voices, String preference) {
        if (!"female".equals(preference) && !"male".equals(preference)) {
            return true;
        }
        return select(voices, preference) != null;
    }

    static String describe(Voice voice) {
        String connection = voice.isNetworkConnectionRequired() ? "联网" : "可离线";
        return voice.getLocale().toLanguageTag()
                + " · " + classify(voice).displayName
                + " · " + connection
                + " · " + voice.getName();
    }

    static Profile classify(Voice voice) {
        String metadata = metadata(voice);
        if (containsAny(metadata, CHILD_HINTS)) {
            return Profile.CHILD;
        }
        if (containsAny(metadata, FEMALE_HINTS)) {
            return Profile.ADULT_FEMALE;
        }
        if (containsAny(metadata, MALE_HINTS)) {
            return Profile.ADULT_MALE;
        }
        return Profile.UNKNOWN;
    }

    private static String metadata(Voice voice) {
        StringBuilder value = new StringBuilder(voice.getName().toLowerCase(Locale.ROOT));
        Set<String> features = voice.getFeatures();
        if (features != null) {
            for (String feature : features) {
                value.append(' ').append(feature.toLowerCase(Locale.ROOT));
            }
        }
        return value.toString();
    }

    private static boolean containsAny(String value, List<String> hints) {
        for (String hint : hints) {
            if (value.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInstalled(Voice voice) {
        Set<String> features = voice.getFeatures();
        return features == null
                || !features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED);
    }

    private static final Comparator<Voice> VOICE_PRIORITY =
            Comparator.comparingInt((Voice voice) -> profileRank(classify(voice)))
                    .thenComparing(Voice::isNetworkConnectionRequired)
                    .thenComparing((Voice voice) -> localeRank(voice.getLocale()))
                    .thenComparing(Comparator.comparingInt(Voice::getQuality).reversed())
                    .thenComparingInt(Voice::getLatency)
                    .thenComparing(Voice::getName);

    private static int profileRank(Profile profile) {
        if (profile == Profile.ADULT_FEMALE || profile == Profile.ADULT_MALE) {
            return 0;
        }
        if (profile == Profile.UNKNOWN) {
            return 1;
        }
        return 2;
    }

    private static int localeRank(Locale locale) {
        if (Locale.SIMPLIFIED_CHINESE.equals(locale)) {
            return 0;
        }
        String country = locale.getCountry();
        if ("CN".equalsIgnoreCase(country) || "SG".equalsIgnoreCase(country)) {
            return 1;
        }
        return 2;
    }
}
