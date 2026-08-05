/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.speech;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
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

/** Single application-wide TTS engine with explicit character/word playback APIs. */
public final class TtsManager {
    private static final String TAG = "TtsManager";
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
        if (ready && tts != null) {
            main.post(this::applyPreferences);
        }
    }

    /** Speaks exactly one displayed Han character; it never substitutes a sample word. */
    public boolean speakCharacter(String character) {
        return speakInternal(SpeechTextPolicy.character(character), "character");
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

    private boolean speakInternal(String text, String type) {
        if (text.isEmpty()) {
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

    private void applyPreferences() {
        if (tts == null) {
            return;
        }
        android.content.SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        tts.setSpeechRate(preferences.getInt("speech_rate", 90) / 100f);
        tts.setPitch(preferences.getInt("speech_pitch", 100) / 100f);

        List<Voice> voices = getChineseVoices();
        String mode = preferences.getString("voice_mode", "auto");
        String selected = preferences.getString("voice_name", "");
        Voice voice = null;

        if ("manual".equals(mode) && !selected.trim().isEmpty()) {
            for (Voice candidate : voices) {
                if (candidate.getName().equals(selected)) {
                    voice = candidate;
                    break;
                }
            }
        } else if ("male".equals(mode) || "female".equals(mode)) {
            voice = VoiceProfileRegistry.select(voices, mode);
        }

        if (voice == null) {
            voice = VoiceProfileRegistry.selectAutomatic(voices);
        }
        if (voice != null) {
            int status = tts.setVoice(voice);
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "Unable to select TTS voice: " + voice.getName());
            }
        }
    }

    private void notifyReadyListeners() {
        for (Runnable listener : readyListeners) {
            main.post(listener);
        }
    }
}
