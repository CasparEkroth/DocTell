package com.doctell.app.model.voice;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;

import java.util.Locale;

public class CloudTtsEngine extends BaseTtsEngine {
    private static CloudTtsEngine instance;

    public static CloudTtsEngine getInstance(Context context) {
        if (instance == null) {
            instance = new CloudTtsEngine(context.getApplicationContext());
        }
        return instance;
    }

    private CloudTtsEngine(Context app) {
        super(app);
        this.app = app;
        // maybe separate prefs or same as local
    }

    @Override
    protected boolean acceptVoice(Voice v, Locale lang) {
        return v.getQuality() == Voice.QUALITY_HIGH
                && v.isNetworkConnectionRequired()
                && v.getLocale().equals(lang);
    }

    @Override
    protected void onErrorInternal(String utteranceId, int errorCode) {
        if (errorCode == TextToSpeech.ERROR_NETWORK
                || errorCode == TextToSpeech.ERROR_NETWORK_TIMEOUT) {

            Log.w("CloudTtsEngine", "Network error, trying offline fallback…");

            boolean switched = trySwitchToOfflineVoice();
            if (switched && lastText != null && lastIndex >= 0) {


                if (engineListener != null) {

                    //main.post(() -> engineListener.onEngineNetworkLost());
                    // show to main?
                }

                String newId = "CHUNK_" + lastIndex;
                tts.speak(lastText, TextToSpeech.QUEUE_FLUSH, null, newId);
                speaking = true;
                return;
            }
        }

        super.onErrorInternal(utteranceId, errorCode);
    }

    private boolean trySwitchToOfflineVoice() {
        if (tts == null) return false;

        Locale lang = tts.getLanguage();
        try {
            for (Voice v : tts.getVoices()) {
                if (!v.isNetworkConnectionRequired()
                        && v.getLocale().equals(lang)) {
                    Log.i("CloudTtsEngine", "Switching to offline voice: " + v.getName());
                    tts.setVoice(v);
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e("CloudTtsEngine", "Error selecting offline voice", e);
        }
        return false;
    }

}
