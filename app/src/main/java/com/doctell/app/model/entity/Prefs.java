package com.doctell.app.model.entity;

import androidx.annotation.NonNull;

public enum Prefs {

    DOCTELL_PREFS,
    TTS_SPEED,
    LANG,
    ENGINE,
    SORT_INDEX,
    ANALYTICS_ENABLED,
    CRASHLYTICS_ENABLED,
    PERMISSIONS_ON_START,
    STEP_LENGTH;

    @NonNull
    @Override
    public String toString(){
        String s = "";
         switch (this){
             case TTS_SPEED: s = "pref_tts_speed"; break;
             case DOCTELL_PREFS: s = "doctell_prefs"; break;
             case LANG: s = "pref_lang"; break;
             case ENGINE: s = "tts_engine_type"; break;
             case SORT_INDEX: s = "book_sort_index"; break;
             case ANALYTICS_ENABLED: s = "firebase_analytics_enabled"; break;
             case CRASHLYTICS_ENABLED: s = "firebase_crashlytics_enabled"; break;
             case PERMISSIONS_ON_START: s = "permissions_shown"; break;
             case STEP_LENGTH: s = "step_length"; break;
        };
         return s;
    }
}
