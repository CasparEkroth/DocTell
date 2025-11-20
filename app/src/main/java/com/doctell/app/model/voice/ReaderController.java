package com.doctell.app.model.voice;

import java.util.List;

public class ReaderController {
    private List<String> chunks;
    private int currentIndex;

    private boolean isPaused;
    private TtsEngineStrategy engine;
    private HighlightListener highlightListener;

    public void setLanguage(String langCode) {
        engine.setLanguageByCode(langCode);
    }

    public void setRate(float rate) {
        engine.setRate(rate);
    }

    void startReading(){

    }

    void pauseReading(){

    }

    void resumeReading(){

    }

    void stopReading(){

    }

}
