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

public class LocalTtsEngine implements TtsEngineStrategy{

    private TextToSpeech tts;
    private TtsEngineListener engineListener;
    private final Handler main = new android.os.Handler(getMainLooper());
    private boolean speaking;
    private Context app;

    private String lastText;
    private int lastIndex = -1;

    private String currentLangCode;
    private float currentRate;

    private static LocalTtsEngine instance;
    public static LocalTtsEngine getInstance(Context context){
        if(instance == null)
            instance = new LocalTtsEngine(context);
        return instance;
    }

    private LocalTtsEngine(Context context){
        app = context;

        SharedPreferences prefs = this.app.getSharedPreferences(
                Prefs.DOCTELL_PREFS.toString(), Context.MODE_PRIVATE);

        currentLangCode = prefs.getString(
                Prefs.LANG.toString(),
                Locale.getDefault().toLanguageTag()
        );
        currentRate = prefs.getFloat(Prefs.TTS_SPEED.toString(), 1.0f);

        init(context);
    }

    private void applyLanguage() {
        if (tts == null) return;
        Locale locale = Locale.forLanguageTag(currentLangCode);
        tts.setLanguage(locale);
    }

    private void applyRate() {
        if (tts == null) return;
        float clamped = Math.max(0.5f, Math.min(2.0f, currentRate));
        tts.setSpeechRate(clamped);
    }

    private void persistSettings() {
        SharedPreferences prefs = app.getSharedPreferences(
                Prefs.DOCTELL_PREFS.toString(), Context.MODE_PRIVATE);
        prefs.edit()
                .putString(Prefs.LANG.toString(), currentLangCode)
                .putFloat(Prefs.TTS_SPEED.toString(), currentRate)
                .apply();
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

                for (Voice v : tts.getVoices()) {
                    if (v.getQuality() == Voice.QUALITY_HIGH &&
                            !v.isNetworkConnectionRequired() &&
                            v.getLocale().equals(tts.getLanguage())) {
                        tts.setVoice(v);
                        break;
                    }
                }

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {
                        speaking = true;
                        if (engineListener != null) {
                            main.post(() -> engineListener.onEngineChunkStart(id));
                        }
                    }
                    @Override public void onDone(String id) {
                        Log.d("LocalTtsEngine", "onDone id=" + id);
                        speaking = false;
                        if (engineListener != null) {
                            main.post(() -> engineListener.onEngineChunkDone(id));
                        }
                    }
                    @Override public void onError(String id) {
                        speaking = false;
                        if (engineListener != null) {
                            main.post(() -> engineListener.onEngineError(id));
                        }
                    }
                });
            }
        });
    }


    @Override
    public void setListener(TtsEngineListener listener) {
        this.engineListener = listener;
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

    public void setLanguageByCode(String langCode) {
        currentLangCode = langCode;
        applyLanguage();
        persistSettings();
    }

    @Override
    public String getLanguage() {
        return currentLangCode;
    }

    @Override
    public void setRate(float rate) {
        currentRate = rate;
        applyRate();
        persistSettings();
    }



    @Override
    public void shutdown() {//when terminating the app
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
        }
    }
}
