package com.doctell.app.model;

import androidx.annotation.NonNull;

public enum Prefs {

    DOCTELL_PREFS,TTS_SPEED,LANG,ENGINE,SORT_INDEX;

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
        };
         return s;
    }
}
