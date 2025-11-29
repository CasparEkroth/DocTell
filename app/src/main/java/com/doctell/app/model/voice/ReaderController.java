package com.doctell.app.model.voice;
import android.app.Notification;
import android.content.Context;

import com.doctell.app.model.voice.media.PlaybackControl;
import com.doctell.app.model.voice.media.ReaderMediaController;

import java.util.List;

public class ReaderController implements TtsEngineListener, PlaybackControl {
    private List<String> chunks;
    private int currentIndex;
    private boolean isPaused;
    private TtsEngineStrategy engine;
    private HighlightListener highlightListener;
    private final ReaderMediaController mediaController;

    public interface MediaNav{
        void navForward();
        void navBackward();
    }

    private final MediaNav mediaNav;
    public ReaderController(TtsEngineStrategy engine,
                            List<String> chunks,
                            HighlightListener highlightListener,
                            Context ctx, MediaNav mediaNav) {
        this.engine = engine;
        this.chunks = chunks;
        this.highlightListener = highlightListener;

        mediaController = new ReaderMediaController(ctx, this);
        this.mediaNav = mediaNav;

        engine.init(ctx);
        engine.setListener(this);
    }

    public Notification buildNotification(boolean isPlaying, int currentIndex, String currentSentence) {
        // exactly what you already do now – build and return Notification
    }

    public void updatePlaybackState(boolean isPlaying, int currentIndex) {
        // your existing PlaybackStateCompat.Builder code
    }


    public void setLanguage(String langCode) {
        engine.setLanguageByCode(langCode);
    }

    public void setRate(float rate) {
        engine.setRate(rate);
    }

    public void setChunks(List<String> chunks) {
        this.chunks = chunks;
        //this.currentIndex = 0;
    }

    public void startReading() {
        isPaused = false;
        if (chunks == null || chunks.isEmpty()) return;
        speakCurrent();

        String sentence = chunks.get(currentIndex);
        mediaController.updateState(true, currentIndex, sentence,null);
    }

    public void startReadingFrom(int index) {
        if (chunks == null || chunks.isEmpty()) return;
        if (index < 0) index = 0;
        if (index >= chunks.size()) return;

        currentIndex = index;
        isPaused = false;
        speakCurrent();
        String sentence = chunks.get(currentIndex);
        mediaController.updateState(true, currentIndex, sentence,null);
    }

    public void pauseReading() {
        isPaused = true;
        engine.pause();
        if (chunks != null && currentIndex >= 0 && currentIndex < chunks.size()) {
            mediaController.updateState(false, currentIndex, chunks.get(currentIndex),null);
        }
    }

    public void resumeReading() {
        if (!isPaused) return;
        isPaused = false;
        engine.resume();
        if (chunks != null && currentIndex >= 0 && currentIndex < chunks.size()) {
            mediaController.updateState(true, currentIndex, chunks.get(currentIndex),null);
        }
    }

    public void stopReading() {
        isPaused = false;
        engine.stop();
        currentIndex = 0;
        mediaController.stop();
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

        String sentence = chunks.get(index);
        mediaController.updateState(!isPaused, index, sentence,null);
    }

    @Override
    public void onEngineChunkDone(String utteranceId) {
        int index = parseIndex(utteranceId);

        // Clear highlight
        if (chunks != null && highlightListener != null &&
                index >= 0 && index < chunks.size()) {
            highlightListener.onChunkDone(index, chunks.get(index));
        }

        if (isPaused || chunks == null) return;

        currentIndex = index + 1;

        if (currentIndex < chunks.size()) {
            speakCurrent();
            String sentence = chunks.get(currentIndex);
            mediaController.updateState(true, currentIndex, sentence,null);
        } else {
            if (highlightListener != null) {
                highlightListener.onPageFinished();
            }
            mediaController.stop();
        }
    }


    @Override
    public void onEngineError(String utteranceId) {
        //TODO add if needed
        //mediaController.stop();
    }

    @Override
    public void setStartSentence(int sentence) {
        this.currentIndex = sentence;
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
        mediaController.stop();
    }

    @Override
    public void play() {
        if (engine == null) return;
        resumeReading();
    }

    @Override
    public void pause() {
        if (engine == null) return;
        pauseReading();
    }

    @Override
    public void stop() {
        if (engine == null) return;
        stopReading();
    }

    @Override
    public void forward() {
        if (mediaNav == null) return;
        if (engine == null) return;
        mediaNav.navForward();
    }

    @Override
    public void backward() {
        if (mediaNav == null) return;
        if (engine == null) return;
        mediaNav.navBackward();
    }
}
