package com.doctell.app.model.voice;

import static android.os.Looper.getMainLooper;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;

import com.doctell.app.model.Prefs;

import java.util.Locale;
import java.util.Set;

public abstract class BaseTtsEngine implements TtsEngineStrategy {

    protected TextToSpeech tts;
    protected TtsEngineListener engineListener;
    protected final Handler main = new Handler(getMainLooper());
    protected boolean speaking;
    protected Context app;

    protected String lastText;
    protected int lastIndex = -1;

    protected String currentLangCode;
    protected float currentRate;
    private static final int ERROR_CODE_GENERIC = 0;

    protected abstract boolean acceptVoice(Voice v, Locale engineLanguage);

    protected BaseTtsEngine(Context context) {
        this.app = context.getApplicationContext();

        SharedPreferences prefs = this.app.getSharedPreferences(
                Prefs.DOCTELL_PREFS.toString(), Context.MODE_PRIVATE
        );

        currentLangCode = prefs.getString(
                Prefs.LANG.toString(),
                Locale.getDefault().toLanguageTag()
        );
        currentRate = prefs.getFloat(Prefs.TTS_SPEED.toString(), 1.0f);
    }

    @Override
    public synchronized void init(Context context) {
        if (tts != null) return;

        tts = new TextToSpeech(app, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());

                applyLanguage();
                applyRate();

                //voice selection acceptVoice() from subclass
                try {
                    Set<Voice> voices = tts.getVoices();
                    if (voices != null) {
                        Locale lang = tts.getLanguage();
                        for (Voice v : voices) {
                            if (acceptVoice(v, lang)) {
                                tts.setVoice(v);
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w("BaseTtsEngine", "Failed selecting voice", e);
                }

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {
                        speaking = true;
                        if (engineListener != null) {
                            main.post(() -> engineListener.onEngineChunkStart(id));
                        }
                    }

                    @Override public void onDone(String id) {
                        Log.d("BaseTtsEngine", "onDone id=" + id);
                        speaking = false;
                        if (engineListener != null) {
                            main.post(() -> engineListener.onEngineChunkDone(id));
                        }
                    }

                    @Override
                    public void onError(String id) {
                        onErrorInternal(id, ERROR_CODE_GENERIC);
                    }

                    @Override
                    public void onError(String id, int errorCode) {
                        onErrorInternal(id, errorCode);
                    }
                });
            } else {
                Log.e("BaseTtsEngine", "TextToSpeech init failed: " + status);
            }
        });
    }

    protected void onErrorInternal(String utteranceId, int errorCode) {
        speaking = false;

        if (engineListener != null) {
            // If you later want to pass errorCode through, you can extend TtsEngineListener.
            main.post(() -> engineListener.onEngineError(utteranceId));
        }
    }

    protected void applyLanguage() {
        if (tts == null) return;
        Locale locale = Locale.forLanguageTag(currentLangCode);
        tts.setLanguage(locale);
    }

    protected void applyRate() {
        if (tts == null) return;
        float clamped = Math.max(0.5f, Math.min(2.0f, currentRate));
        tts.setSpeechRate(clamped);
    }

    protected void persistSettings() {
        SharedPreferences prefs = app.getSharedPreferences(
                Prefs.DOCTELL_PREFS.toString(), Context.MODE_PRIVATE);
        prefs.edit()
                .putString(Prefs.LANG.toString(), currentLangCode)
                .putFloat(Prefs.TTS_SPEED.toString(), currentRate)
                .apply();
    }

    public void setLanguageByCode(String langCode) {
        currentLangCode = langCode;
        applyLanguage();
        persistSettings();
    }

    public String getCurrentLanguageCode() {
        return currentLangCode;
    }

    public void setRate(float rate) {
        currentRate = rate;
        applyRate();
        persistSettings();
    }

    @Override
    public void setListener(TtsEngineListener listener) {
        this.engineListener = listener;
    }

    @Override
    public String getLanguage() {
        return currentLangCode;
    }

    public float getCurrentRate() {
        return currentRate;
    }

    @Override
    public void speakChunk(String text, int index) {
        if (tts == null) return;

        lastText = text;
        lastIndex = index;

        String utteranceId = "CHUNK_" + index;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
    }

    @Override
    public void pause() {
        if (tts != null) {
            tts.stop();
        }
    }

    @Override
    public void resume() {
        if (tts == null) return;
        if (lastText == null || lastIndex < 0) return;

        String utteranceId = "CHUNK_" + lastIndex;
        tts.speak(lastText, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
    }

    @Override
    public void stop() {
        if (tts != null) {
            tts.stop();
            speaking = false;
        }
    }

    @Override
    public void shutdown() {
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
        }
    }
}
