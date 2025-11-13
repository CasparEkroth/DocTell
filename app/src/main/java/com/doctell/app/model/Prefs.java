package com.doctell.app.model;

public enum Prefs {

    DOCTELL_PREFS,TTS_SPEED,LANG;

    @Override
    public String toString(){
        String s = "";
         switch (this){
             case TTS_SPEED: s = "pref_tts_speed"; break;
             case DOCTELL_PREFS: s = "doctell_prefs"; break;
             case LANG: s = "pref_lang"; break;
        };
         return s;
    }
}
