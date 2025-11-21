package com.doctell.app.model.voice;

import android.content.Context;

import java.util.List;

public class ReaderController implements TtsEngineListener {
    private List<String> chunks;
    private int currentIndex;
    private boolean isPaused;
    private TtsEngineStrategy engine;
    private HighlightListener highlightListener;


    public ReaderController(TtsEngineStrategy engine,
                            List<String> chunks,
                            HighlightListener highlightListener,
                            Context ctx) {
        this.engine = engine;
        this.chunks = chunks;
        this.highlightListener = highlightListener;

        engine.init(ctx);
        engine.setListener(this);
    }


    public void setLanguage(String langCode) {
        engine.setLanguageByCode(langCode);
    }

    public void setRate(float rate) {
        engine.setRate(rate);
    }

    public void setChunks(List<String> chunks) {
        this.chunks = chunks;
        this.currentIndex = 0;
    }

    public void startReading() {
        isPaused = false;
        if (chunks == null || chunks.isEmpty()) return;
        speakCurrent();
    }

    public void startReadingFrom(int index) {
        if (chunks == null || chunks.isEmpty()) return;
        if (index < 0) index = 0;
        if (index >= chunks.size()) return;

        currentIndex = index;
        isPaused = false;
        speakCurrent();
    }

    public void pauseReading() {
        isPaused = true;
        engine.pause();
    }

    public void resumeReading() {
        if (!isPaused) return;
        isPaused = false;
        speakCurrent();
    }

    public void stopReading() {
        isPaused = false;
        engine.stop();
        currentIndex = 0;
    }

    private void speakCurrent() {
        if (chunks == null) return;
        if (currentIndex < 0 || currentIndex >= chunks.size()) return;
        String text = chunks.get(currentIndex);
        engine.speakChunk(text, currentIndex);
    }

    @Override
    public void onEngineChunkStart(String utteranceId) {
        int index = parseIndex(utteranceId);
        if (chunks == null || highlightListener == null) return;
        if (index < 0 || index >= chunks.size()) return;
        highlightListener.onChunkStart(index, chunks.get(index));
    }

    @Override
    public void onEngineChunkDone(String utteranceId) {
        int index = parseIndex(utteranceId);
        if (chunks != null && highlightListener != null &&
                index >= 0 && index < chunks.size()) {
            highlightListener.onChunkDone(index, chunks.get(index));
        }
        if (!isPaused) {
            currentIndex = index + 1;
            if (chunks != null && currentIndex < chunks.size()) {
                speakCurrent();
            }
        }
    }

    @Override
    public void onEngineError(String utteranceId) {
        //TODO add if needed
    }

    private int parseIndex(String utteranceId) {
        try {
            if (utteranceId != null && utteranceId.startsWith("CHUNK_")) {
                return Integer.parseInt(utteranceId.substring("CHUNK_".length()));
            }
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    public void shutdown(){
        if(engine != null){
            engine.stop();
            engine.shutdown();
        }
    }
}
