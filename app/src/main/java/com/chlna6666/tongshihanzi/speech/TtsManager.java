/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.speech;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TtsSpan;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Single application-wide TTS engine with explicit character, pronunciation and word APIs. */
public final class TtsManager {
    private static final String TAG = "TtsManager";
    private static final int STANDARD_PITCH = 100;
    private static final int MIN_SAFE_PITCH = 80;
    private static final int MAX_SAFE_PITCH = 120;
    private static volatile TtsManager instance;

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Runnable> readyListeners = new CopyOnWriteArrayList<>();
    private TextToSpeech tts;
    private volatile boolean ready;
    private volatile boolean initializing;

    private TtsManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static TtsManager getInstance(Context context) {
        if (instance == null) {
            synchronized (TtsManager.class) {
                if (instance == null) {
                    instance = new TtsManager(context);
                }
            }
        }
        return instance;
    }

    public synchronized void initialize() {
        if (ready || initializing) {
            return;
        }
        initializing = true;
        main.post(() -> tts = new TextToSpeech(context, status -> {
            initializing = false;
            if (status != TextToSpeech.SUCCESS || tts == null) {
                Log.e(TAG, "TextToSpeech initialization failed: " + status);
                ready = false;
                notifyReadyListeners();
                return;
            }
            tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            int language = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
            ready = language != TextToSpeech.LANG_MISSING_DATA
                    && language != TextToSpeech.LANG_NOT_SUPPORTED;
            if (ready) {
                applyPreferences();
            }
            notifyReadyListeners();
        }));
    }

    public boolean isReady() {
        return ready;
    }

    public void addReadyListener(Runnable listener) {
        readyListeners.add(listener);
        if (ready || (!initializing && tts != null)) {
            main.post(listener);
        } else {
            initialize();
        }
    }

    public void removeReadyListener(Runnable listener) {
        readyListeners.remove(listener);
    }

    public List<Voice> getChineseVoices() {
        if (!ready || tts == null) {
            return Collections.emptyList();
        }
        Set<Voice> all = tts.getVoices();
        if (all == null) {
            return Collections.emptyList();
        }
        List<Voice> result = new ArrayList<>();
        for (Voice voice : all) {
            Locale locale = voice.getLocale();
            if (locale != null && "zh".equalsIgnoreCase(locale.getLanguage())) {
                result.add(voice);
            }
        }
        result.sort(Comparator.comparing((Voice voice) -> voice.getLocale().toLanguageTag())
                .thenComparing(Voice::getName));
        return result;
    }

    public String describeVoice(Voice voice) {
        return VoiceProfileRegistry.describe(voice);
    }

    public boolean hasRecognizedProfile(String mode) {
        return VoiceProfileRegistry.hasProfile(getChineseVoices(), mode);
    }

    public void refreshPreferences() {
        reconfigure(null);
    }

    /**
     * Persists and applies a voice profile immediately. Extreme legacy pitch values are reset so a
     * requested adult voice cannot remain child-like because an old pitch value was 140% or higher.
     */
    public void setVoiceMode(String requestedMode, @Nullable Runnable afterApply) {
        String mode = normalizeMode(requestedMode);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit()
                .putString("voice_mode", mode);
        if (!"manual".equals(mode)) {
            editor.remove("voice_name");
        }
        if ("male".equals(mode) || "female".equals(mode)) {
            editor.putInt("speech_pitch", STANDARD_PITCH);
        }
        editor.apply();
        reconfigure(afterApply);
    }

    /** Applies an explicitly selected installed voice and leaves gender/age inference disabled. */
    public void setManualVoice(String voiceName, @Nullable Runnable afterApply) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("voice_mode", "manual")
                .putString("voice_name", voiceName == null ? "" : voiceName)
                .apply();
        reconfigure(afterApply);
    }

    /** Speaks exactly one displayed Han character using the engine's normal reading. */
    public boolean speakCharacter(String character) {
        return speakInternal(SpeechTextPolicy.character(character), "character");
    }

    /**
     * Speaks the currently selected reading of a polyphonic character.
     *
     * <p>The visible character remains the utterance text, while a {@link TtsSpan} supplies the
     * selected tone-marked pinyin as the text to synthesize. This avoids silently falling back to
     * the character's first/default reading and also avoids replacing the button with a whole
     * sample word.</p>
     */
    public boolean speakPronunciation(String character, String pinyinTone) {
        String glyph = SpeechTextPolicy.character(character);
        String reading = SpeechTextPolicy.pronunciation(pinyinTone);
        if (glyph.isEmpty()) {
            return false;
        }
        if (reading.isEmpty()) {
            return speakInternal(glyph, "character");
        }
        SpannableString utterance = new SpannableString(glyph);
        utterance.setSpan(
                new TtsSpan.TextBuilder(reading).build(),
                0,
                glyph.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return speakInternal(utterance, "pronunciation");
    }

    /** Speaks the complete word selected by the user. */
    public boolean speakWord(String word) {
        return speakInternal(SpeechTextPolicy.word(word), "word");
    }

    /** Generic compatibility entry point for sentences and settings previews. */
    public boolean speak(String text) {
        return speakInternal(SpeechTextPolicy.word(text), "text");
    }

    public void stop() {
        if (tts != null) {
            tts.stop();
        }
    }

    public synchronized void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ready = false;
        initializing = false;
    }

    @Nullable
    public String currentVoiceName() {
        return tts != null && tts.getVoice() != null ? tts.getVoice().getName() : null;
    }

    @Nullable
    public String currentVoiceDescription() {
        Voice voice = tts == null ? null : tts.getVoice();
        return voice == null ? null : VoiceProfileRegistry.describe(voice);
    }

    private boolean speakInternal(CharSequence text, String type) {
        if (text == null || text.length() == 0) {
            return false;
        }
        if (!ready || tts == null) {
            initialize();
            return false;
        }
        applyPreferences();
        tts.stop();
        String utteranceId = "tongshi-" + type + '-' + System.nanoTime();
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                == TextToSpeech.SUCCESS;
    }

    private void reconfigure(@Nullable Runnable afterApply) {
        if (!ready || tts == null) {
            Runnable once = new Runnable() {
                @Override
                public void run() {
                    removeReadyListener(this);
                    if (ready) {
                        reconfigure(afterApply);
                    } else if (afterApply != null) {
                        main.post(afterApply);
                    }
                }
            };
            addReadyListener(once);
            return;
        }
        main.post(() -> {
            if (tts != null && ready) {
                tts.stop();
                applyPreferences();
            }
            if (afterApply != null) {
                afterApply.run();
            }
        });
    }

    private void applyPreferences() {
        if (tts == null) {
            return;
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String mode = normalizeMode(preferences.getString("voice_mode", "auto"));
        int rawPitch = preferences.getInt("speech_pitch", STANDARD_PITCH);
        int safePitch = rawPitch < MIN_SAFE_PITCH || rawPitch > MAX_SAFE_PITCH
                ? STANDARD_PITCH : rawPitch;
        if (safePitch != rawPitch) {
            preferences.edit().putInt("speech_pitch", safePitch).apply();
        }

        tts.setSpeechRate(clamp(preferences.getInt("speech_rate", 90) / 100f, 0.5f, 1.5f));
        // Reload the Chinese locale before selecting a concrete voice. Several OEM engines retain
        // their previous child voice unless the locale/voice pair is reapplied after a mode change.
        tts.setLanguage(Locale.SIMPLIFIED_CHINESE);

        List<Voice> voices = getChineseVoices();
        String selected = preferences.getString("voice_name", "");
        Voice voice = null;

        if ("manual".equals(mode) && selected != null && !selected.trim().isEmpty()) {
            for (Voice candidate : voices) {
                if (candidate.getName().equals(selected)) {
                    voice = candidate;
                    break;
                }
            }
        } else if ("male".equals(mode) || "female".equals(mode)) {
            voice = VoiceProfileRegistry.select(voices, mode);
            if (voice == null) {
                voice = VoiceProfileRegistry.selectAdultFallback(voices);
            }
        } else {
            voice = VoiceProfileRegistry.selectAutomatic(voices);
        }

        if (voice != null) {
            int status = tts.setVoice(voice);
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "Unable to select TTS voice: " + voice.getName());
                tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
            }
        }

        // The slider is a small adult-voice adjustment, not a raw 50%-150% multiplier. This keeps
        // both profiles in a normal young-adult range even on engines exposing only one base voice.
        float adjustment = (safePitch - STANDARD_PITCH) / 500f;
        float basePitch = "male".equals(mode) ? 0.86f
                : "female".equals(mode) ? 1.00f : 0.98f;
        tts.setPitch(clamp(basePitch + adjustment, 0.78f, 1.08f));
    }

    private static String normalizeMode(String value) {
        if ("male".equals(value) || "female".equals(value)
                || "manual".equals(value) || "auto".equals(value)) {
            return value;
        }
        return "auto";
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void notifyReadyListeners() {
        for (Runnable listener : readyListeners) {
            main.post(listener);
        }
    }
}
