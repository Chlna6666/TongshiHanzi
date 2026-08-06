/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.speech;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TtsSpan;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Single application-wide TTS engine with explicit character, pronunciation and word APIs. */
public final class TtsManager {
    private static final String TAG = "TtsManager";
    private static final float MALE_PROFILE_PITCH = 0.90f;
    private static final float FEMALE_PROFILE_PITCH = 1.00f;
    private static final float DEFAULT_PROFILE_PITCH = 1.00f;
    private static volatile TtsManager instance;

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Runnable> readyListeners = new CopyOnWriteArrayList<>();
    private final Map<String, PendingBoostedPlayback> pendingBoosted =
            new ConcurrentHashMap<>();

    private TextToSpeech tts;
    private MediaPlayer activePlayer;
    private LoudnessEnhancer activeEnhancer;
    private File activeFile;
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
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                }

                @Override
                public void onDone(String utteranceId) {
                    PendingBoostedPlayback pending = pendingBoosted.remove(utteranceId);
                    if (pending != null) {
                        main.post(() -> playBoostedFile(pending));
                    }
                }

                @Override
                public void onError(String utteranceId) {
                    discardPending(utteranceId);
                }

                @Override
                public void onError(String utteranceId, int errorCode) {
                    discardPending(utteranceId);
                }

                @Override
                public void onStop(String utteranceId, boolean interrupted) {
                    discardPending(utteranceId);
                }
            });
            configureAudioRoute();
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

    /** Persists and immediately applies a voice profile, including its internal adult pitch. */
    public void setVoiceMode(String requestedMode, @Nullable Runnable afterApply) {
        String mode = normalizeMode(requestedMode);
        SharedPreferences.Editor editor = PreferenceManager
                .getDefaultSharedPreferences(context)
                .edit()
                .putString("voice_mode", mode)
                .remove("speech_pitch");
        if (!"manual".equals(mode)) {
            editor.remove("voice_name");
        }
        editor.apply();
        reconfigure(afterApply);
    }

    /** Applies an explicitly selected installed voice without additional gender inference. */
    public void setManualVoice(String voiceName, @Nullable Runnable afterApply) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("voice_mode", "manual")
                .putString("voice_name", voiceName == null ? "" : voiceName)
                .remove("speech_pitch")
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
     * <p>Android TTS engines are allowed to ignore pronunciation metadata attached to a Han
     * character. Passing the Han character as the underlying utterance therefore still causes many
     * engines to use the dictionary's first reading. The selected tone-marked pinyin is now the
     * underlying utterance, so switching chips cannot fall back to the first Han reading.</p>
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
        SpannableString utterance = new SpannableString(reading);
        utterance.setSpan(
                new TtsSpan.VerbatimBuilder(reading).build(),
                0,
                reading.length(),
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

    public synchronized void stop() {
        if (tts != null) {
            tts.stop();
        }
        for (PendingBoostedPlayback pending : pendingBoosted.values()) {
            deleteQuietly(pending.file);
        }
        pendingBoosted.clear();
        releaseActivePlayback();
    }

    public synchronized void shutdown() {
        stop();
        if (tts != null) {
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
        stop();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        int volumePercent = SpeechVolumePolicy.clampPercent(preferences.getInt(
                "speech_volume", SpeechVolumePolicy.DEFAULT_PERCENT));
        if (SpeechVolumePolicy.requiresSoftwareBoost(volumePercent)) {
            return synthesizeBoosted(text, type, volumePercent);
        }
        return speakDirect(text, type, SpeechVolumePolicy.directVolume(volumePercent));
    }

    private boolean speakDirect(CharSequence text, String type, float volume) {
        if (tts == null) {
            return false;
        }
        Bundle parameters = new Bundle();
        parameters.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume);
        parameters.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
        String utteranceId = "tongshi-" + type + '-' + System.nanoTime();
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, parameters, utteranceId)
                == TextToSpeech.SUCCESS;
    }

    private boolean synthesizeBoosted(CharSequence text, String type, int volumePercent) {
        if (tts == null) {
            return false;
        }
        File file;
        try {
            file = File.createTempFile("tongshi-tts-", ".wav", context.getCacheDir());
        } catch (IOException exception) {
            Log.w(TAG, "Unable to create boosted TTS cache file", exception);
            return speakDirect(text, type, 1f);
        }

        String utteranceId = "tongshi-boost-" + type + '-' + System.nanoTime();
        PendingBoostedPlayback pending = new PendingBoostedPlayback(
                utteranceId, file, text, type, volumePercent);
        pendingBoosted.put(utteranceId, pending);

        Bundle parameters = new Bundle();
        parameters.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f);
        parameters.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
        int status = tts.synthesizeToFile(text, parameters, file, utteranceId);
        if (status != TextToSpeech.SUCCESS) {
            pendingBoosted.remove(utteranceId);
            deleteQuietly(file);
            return speakDirect(text, type, 1f);
        }
        return true;
    }

    private void playBoostedFile(PendingBoostedPlayback pending) {
        releaseActivePlayback();
        MediaPlayer player = new MediaPlayer();
        try {
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            player.setDataSource(pending.file.getAbsolutePath());
            player.setOnPreparedListener(prepared -> {
                if (activePlayer != prepared) {
                    return;
                }
                try {
                    int gain = SpeechVolumePolicy.boostMillibels(pending.volumePercent);
                    activeEnhancer = new LoudnessEnhancer(prepared.getAudioSessionId());
                    activeEnhancer.setTargetGain(gain);
                    activeEnhancer.setEnabled(gain > 0);
                    prepared.setVolume(1f, 1f);
                    prepared.start();
                } catch (RuntimeException exception) {
                    Log.w(TAG, "Unable to enable boosted TTS playback", exception);
                    releaseActivePlayback();
                    speakDirect(pending.text, pending.type, 1f);
                }
            });
            player.setOnCompletionListener(completed -> releaseIfActive(completed));
            player.setOnErrorListener((failed, what, extra) -> {
                Log.w(TAG, "Boosted TTS playback failed: " + what + '/' + extra);
                releaseIfActive(failed);
                speakDirect(pending.text, pending.type, 1f);
                return true;
            });
            activePlayer = player;
            activeFile = pending.file;
            player.prepareAsync();
        } catch (IOException | RuntimeException exception) {
            Log.w(TAG, "Unable to prepare boosted TTS playback", exception);
            if (activePlayer == player) {
                activePlayer = null;
                activeFile = null;
            }
            player.release();
            deleteQuietly(pending.file);
            speakDirect(pending.text, pending.type, 1f);
        }
    }

    private void discardPending(String utteranceId) {
        PendingBoostedPlayback pending = pendingBoosted.remove(utteranceId);
        if (pending != null) {
            deleteQuietly(pending.file);
        }
    }

    private synchronized void releaseIfActive(MediaPlayer player) {
        if (activePlayer == player) {
            releaseActivePlayback();
        }
    }

    private synchronized void releaseActivePlayback() {
        if (activeEnhancer != null) {
            try {
                activeEnhancer.release();
            } catch (RuntimeException ignored) {
            }
            activeEnhancer = null;
        }
        if (activePlayer != null) {
            try {
                activePlayer.stop();
            } catch (IllegalStateException ignored) {
            }
            activePlayer.release();
            activePlayer = null;
        }
        deleteQuietly(activeFile);
        activeFile = null;
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
                stop();
                applyPreferences();
            }
            if (afterApply != null) {
                afterApply.run();
            }
        });
    }

    private void configureAudioRoute() {
        if (tts == null) {
            return;
        }
        tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
    }

    private void applyPreferences() {
        if (tts == null) {
            return;
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String mode = normalizeMode(preferences.getString("voice_mode", "auto"));
        SharedPreferences.Editor migration = null;
        if (preferences.contains("speech_pitch")) {
            migration = preferences.edit().remove("speech_pitch");
        }
        int rawVolume = preferences.getInt("speech_volume", SpeechVolumePolicy.DEFAULT_PERCENT);
        int safeVolume = SpeechVolumePolicy.clampPercent(rawVolume);
        if (safeVolume != rawVolume) {
            if (migration == null) {
                migration = preferences.edit();
            }
            migration.putInt("speech_volume", safeVolume);
        }
        if (migration != null) {
            migration.apply();
        }

        configureAudioRoute();
        tts.setSpeechRate(clamp(preferences.getInt("speech_rate", 90) / 100f, 0.5f, 1.5f));
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

        float profilePitch = "male".equals(mode) ? MALE_PROFILE_PITCH
                : "female".equals(mode) ? FEMALE_PROFILE_PITCH
                : DEFAULT_PROFILE_PITCH;
        tts.setPitch(profilePitch);
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

    private static void deleteQuietly(@Nullable File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    private void notifyReadyListeners() {
        for (Runnable listener : readyListeners) {
            main.post(listener);
        }
    }

    private static final class PendingBoostedPlayback {
        final String utteranceId;
        final File file;
        final CharSequence text;
        final String type;
        final int volumePercent;

        PendingBoostedPlayback(
                String utteranceId,
                File file,
                CharSequence text,
                String type,
                int volumePercent
        ) {
            this.utteranceId = utteranceId;
            this.file = file;
            this.text = text;
            this.type = type;
            this.volumePercent = volumePercent;
        }
    }
}
