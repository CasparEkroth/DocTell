package com.doctell.app.model.voice;

public interface TtsEngineListener {
    void onEngineChunkStart(String utteranceId);
    void onEngineChunkDone(String utteranceId);
    void onEngineError(String utteranceId);
    void setStartSentence(int sentence);

    void onEngineReady();
}
