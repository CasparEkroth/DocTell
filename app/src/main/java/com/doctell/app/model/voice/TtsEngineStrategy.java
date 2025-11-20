package com.doctell.app.model.voice;

import android.content.Context;

public interface TtsEngineStrategy {
    void init(Context context);
    void setListener(TtsEngineListener listener);
    void speakChunk(String text,int index);
    void pause();
    void resume();
    void stop();

    void setLanguageByCode(String langCode);
    String getLanguage();
    void setRate(float rate);
}
