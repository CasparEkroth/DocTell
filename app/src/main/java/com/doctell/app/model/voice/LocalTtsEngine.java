package com.doctell.app.model.voice;

import static android.os.Looper.getMainLooper;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
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

    public LocalTtsEngine(Context context){
        app = context;
        init(context);
    }

    @Override
    public void init(Context context) {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                // set language etc...
                // Audio attributes (speech)
                tts.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());

                // Apply saved language & rate
                SharedPreferences p = app.getSharedPreferences(Prefs.DOCTELL_PREFS.toString(), Context.MODE_PRIVATE);
                String lang = p.getString(Prefs.LANG.toString(), "eng");


                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {
                        speaking = true;
                        if (engineListener != null) {
                            main.post(() -> engineListener.onEngineChunkStart(id));
                        }
                    }
                    @Override public void onDone(String id) {
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

    @Override
    public void setLanguageByCode(String langCode) {
        app.getSharedPreferences(Prefs.DOCTELL_PREFS.toString(), Context.MODE_PRIVATE)
                .edit().putString(Prefs.LANG.toString(), langCode).apply();
        if (tts == null) return;
        Locale loc;

        switch (langCode) {
            case "swe": loc = new Locale("swe"); break;
            case "spa": loc = new Locale("spa"); break;
            default:   loc = new Locale("eng"); // "eng"
        }
        tts.setLanguage(loc);
        Log.d("LocalTTS", "setLanguage " + loc);
    }

    @Override
    public String getLanguage() {
        Locale lang = tts.getLanguage();
        if(lang == null)lang = new Locale("eng");

        return convertLang(lang);
    }

    private String convertLang(Locale locale) {
        if (locale == null){
            return "eng";
        }
        String lang = locale.getLanguage();
        if (lang == null || lang.isEmpty()){
            return "eng";
        }
        return lang;
    }


    @Override
    public void setRate(float rate) {
        if (tts == null) return;
        float clamped = Math.max(0.5f, Math.min(2.0f, rate));
        tts.setSpeechRate(clamped);
        app.getSharedPreferences(Prefs.DOCTELL_PREFS.toString(), Context.MODE_PRIVATE)
                .edit().putFloat(Prefs.TTS_SPEED.toString(), clamped).apply();
    }

    @Override
    public void shutdown() {//when terminating the app
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
        }
    }
}
