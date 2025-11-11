package com.doctell.app.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;
import java.util.UUID;

public class TTSModel {
    private static volatile TTSModel INSTANCE;

    public static TTSModel get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (TTSModel.class) {
                if (INSTANCE == null) INSTANCE = new TTSModel(ctx.getApplicationContext());
            }
        }
        return INSTANCE;
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ready = false;
    private boolean speaking = false;

    private UtteranceProgressListener externalListener;
    private TTSModel(Context appCtx) {
        this.app = appCtx;
        initTts();
    }

    private void initTts() {
        tts = new TextToSpeech(app, status -> {
            ready = (status == TextToSpeech.SUCCESS);
            if (!ready) {
                Log.e("TTSModel", "TTS init failed: " + status);
                return;
            }

            // Audio attributes (speech)
            tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());

            // Apply saved language & rate
            SharedPreferences p = app.getSharedPreferences("doctell_prefs", Context.MODE_PRIVATE);
            String lang = p.getString("pref_lang", "en");
            float rate = p.getFloat("pref_tts_speed", 1.0f);
            setLanguageByCode(lang);
            setRate(rate);

            // Internal progress listener
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {
                    speaking = true;
                    if (externalListener != null) externalListener.onStart(id);
                }
                @Override public void onDone(String id) {
                    speaking = false;
                    if (externalListener != null) {
                        // dispatch on main so UI can react safely
                        main.post(() -> externalListener.onDone(id));
                    }
                }
                @Override public void onError(String id) {
                    speaking = false;
                    if (externalListener != null) externalListener.onError(id);
                }
            });
        });
    }

    public boolean isReady() { return ready; }
    public boolean isSpeaking() { return speaking; }

    public void setExternalListener(UtteranceProgressListener l) {
        this.externalListener = l;
    }

    public void setLanguageByCode(String code) {
        if (tts == null) return;
        Locale loc;
        switch (code) {
            case "sv": loc = new Locale("sv"); break;
            case "es": loc = new Locale("es"); break;
            default:   loc = Locale.US; // "en"
        }
        int r = tts.setLanguage(loc);
        Log.d("TTSModel", "setLanguage " + loc + " => " + r);

        // persist
        app.getSharedPreferences("doctell_prefs", Context.MODE_PRIVATE)
                .edit().putString("pref_lang", code).apply();
    }

    public void setRate(float rate) {
        if (tts == null) return;
        float clamped = Math.max(0.5f, Math.min(2.0f, rate));
        tts.setSpeechRate(clamped);
        app.getSharedPreferences("doctell_prefs", Context.MODE_PRIVATE)
                .edit().putFloat("pref_tts_speed", clamped).apply();
    }

    public String speak(String text, boolean flush) {
        if (!ready || tts == null || text == null || text.trim().isEmpty()) return null;
        String id = UUID.randomUUID().toString();
        tts.speak(text, flush ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null, id);
        return id;
    }

    public void stop() {
        if (tts != null) {
            tts.stop();
            speaking = false;
        }
    }

    public void shutdown() {//when terminating the app
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
            ready = false;
        }
    }
}