package com.doctell.app.model;

import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;

public class TTSModel {
    private TextToSpeech tts;// init
    private boolean isSpeaking = false;
    private LANG selectedLang;// init
    private UtteranceProgressListener autoTts;// init
    private static Object obj;

    public static TTSModel create(){
        if(obj == null) obj = new TTSModel();
        return (TTSModel) obj;
    }
    private TTSModel(){
        autoTts = new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                // TTS started
            }

            @Override
            public void onDone(String utteranceId) {
                // solve this
                runOnUiThread(() -> {
                    // Auto-next page when finished
                    if (isSpeaking) {
                        //showNextPage();
                    }
                });
            }
            @Override
            public void onError(String utteranceId) {
                Log.e("TTS", "Error speaking!");
            }
        };
    }

    public void setSpeaking(boolean speaking) {
        isSpeaking = speaking;
    }

    private void updateLang(){
        tts.setLanguage(new Locale(selectedLang.toString()));
    }

    public void setLang(String lang){
        switch (lang){
            case "sv": selectedLang = LANG.SV; break;
            case "en": selectedLang = LANG.EN; break;
            case "es": selectedLang = LANG.ES; break;
        }
    }
}

/*
        getSharedPreferences("doctell_prefs", MODE_PRIVATE)
                .edit()
                .putString("pref_lang", langCode)
                .apply();
 */