package com.doctell.app.model.voice;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.util.Log;
import android.widget.Toast;

import com.doctell.app.model.utils.PermissionHelper;
import com.doctell.app.model.voice.media.PlaybackControl;
import com.doctell.app.model.voice.media.ReaderMediaController;

import java.util.List;

public class ReaderController implements TtsEngineListener, PlaybackControl {
    private List<String> chunks;
    private String title;
    private int currentIndex;
    private boolean isPaused;
    private TtsEngineStrategy engine;
    private HighlightListener highlightListener;
    private Context ctx;
    private ReaderMediaController mediaController;

    private float normalVolume = 1.0f;
    private float duckVolume = 0.3f;

    public interface MediaNav {
        void navForward();
        void navBackward();
    }

    private final MediaNav mediaNav;

    public ReaderController(TtsEngineStrategy engine,
                            List<String> chunks,
                            String title,
                            HighlightListener highlightListener,
                            Context ctx,
                            MediaNav mediaNav) {
        this.engine = engine;
        this.chunks = chunks;
        this.title = title;
        this.highlightListener = highlightListener;
        this.mediaNav = mediaNav;
        this.ctx = ctx;
        engine.init(ctx);
        engine.setListener(this);
    }

    public void setMediaController(ReaderMediaController mediaController){this.mediaController = mediaController;}

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

    public void setChunks(List<String> chunks, int startSentence) {
        this.chunks = chunks;
        this.currentIndex = startSentence;
    }

    public void  setTitle(String title){
        this.title = title;
    }

    public void startReading() {
        isPaused = false;
        if (chunks == null || chunks.isEmpty()) return;

        if (currentIndex < 0 || currentIndex >= chunks.size()) {
            Log.e("ReaderController", "Invalid index " + currentIndex + " for chunks size " + chunks.size());
            currentIndex = 0; // Reset to safe default
        }

        speakCurrent();

        //String sentence = chunks.get(currentIndex);
        mediaController.updateState(true, currentIndex, title);
    }

    public void startReadingFrom(int index) {
        if (chunks == null || chunks.isEmpty()) return;
        if (index < 0) index = 0;
        if (index >= chunks.size()) return;

        currentIndex = index;
        isPaused = false;
        speakCurrent();
        //String sentence = chunks.get(currentIndex);
        mediaController.updateState(true, currentIndex, title);
    }

    public void pauseReading() {
        isPaused = true;
        engine.pause();
        if (chunks != null && currentIndex >= 0 && currentIndex < chunks.size()) {
            mediaController.updateState(false, currentIndex, chunks.get(currentIndex));
        }
    }

    public void resumeReading() {
        if (!isPaused) return;
        isPaused = false;
        engine.resume();
        if (chunks != null && currentIndex >= 0 && currentIndex < chunks.size()) {
            mediaController.updateState(true, currentIndex, chunks.get(currentIndex));
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

        //String sentence = chunks.get(index);
        mediaController.updateState(!isPaused, index, title);
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
            //String sentence = chunks.get(currentIndex);
            mediaController.updateState(true, currentIndex, title);
        } else {
            if (highlightListener != null) {
                highlightListener.onPageFinished();
            }
            // We reached the end → consider this a stop
            mediaController.updateState(false, index, "");
        }
    }

    @Override
    public void onEngineError(String utteranceId) {
        Log.e("ReaderController", "Engine error for " + utteranceId);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            pauseReading();
            Toast.makeText(ctx, "TTS connection lost. Please press play to retry.", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void setStartSentence(int sentence) {
        if (chunks != null && !chunks.isEmpty()) {
            if (sentence >= 0 && sentence < chunks.size()) {
                this.currentIndex = sentence;
            } else {
                Log.w("ReaderController", "Ignored invalid start sentence: " + sentence);
                this.currentIndex = 0; // Fallback
            }
        } else {
            this.currentIndex = sentence;
        }
    }

    private int parseIndex(String utteranceId) {
        try {
            if (utteranceId != null && utteranceId.startsWith("CHUNK_")) {
                return Integer.parseInt(utteranceId.substring("CHUNK_".length()));
            }
        } catch (NumberFormatException ignored) {}
        return -1;
    }
    public void shutdown() {
        if (engine != null) {
            engine.stop();
            engine.shutdown();
        }
        mediaController.stop();
    }

    // PlaybackControl implementation -------------------------

    @Override
    public void play() {
        Log.d("ReaderController", "play - isPaused=" + isPaused);
        if (!PermissionHelper.cheekNotificationPermission(ctx)) {
            return;
        }
        if (chunks == null || chunks.isEmpty()) {
            Log.w("ReaderController", "play() called but no chunks loaded");
            Toast.makeText(ctx,
                    "Please wait, loading page text...",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (isPaused) {
            resumeReading();
        } else if (chunks != null && !chunks.isEmpty()) {
            startReadingFrom(currentIndex);
        } else {
            Log.w("ReaderController", "play called but no chunks loaded");
        }
    }

    @Override
    public void pause() {
        pauseReading();
    }

    @Override
    public void stop() {
        stopReading();
    }

    @Override
    public void next() {
        Log.d("ReaderController", "next() from media controls");
        if (mediaNav != null) {
            mediaNav.navForward();
        }
    }

    @Override
    public void prev() {
        //stopReading();
        if (mediaNav != null) {
            mediaNav.navBackward();
        }
    }
    // ----
    public ReaderMediaController getMediaController() {
        return mediaController;
    }

    /**
     * Reduce volume for audio focus ducking (e.g., during incoming calls)
     */
    public void duckAudio(boolean duck) {
        if (engine == null) return;

        float targetVolume = duck ? duckVolume : normalVolume;
        Log.d("ReaderController", "Duck audio: " + (duck ? "ON" : "OFF"));

        //this is a placeholder for TTS implementation
        try {
            engine.setVolume(targetVolume);
        } catch (Exception e) {
            Log.e("ReaderController", "Error ducking audio", e);
        }
    }
}
