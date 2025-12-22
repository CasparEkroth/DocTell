package com.doctell.app.model.voice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.doctell.app.model.utils.PermissionHelper;
import com.doctell.app.model.voice.media.PlaybackControl;
import com.doctell.app.model.voice.media.ReaderMediaController;
import com.doctell.app.model.voice.media.ReaderService;

import java.util.List;
import java.util.Locale;

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
    private boolean pendingResume = false;
    private boolean isEngineLoading = false;

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

    public List<String> getChunks() {
        return chunks;
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
            mediaController.updateState(false, currentIndex, title);
        }
    }

    public void resumeReading() {
        if (!isPaused) return;
        isPaused = false;
        engine.resume();
        if (chunks != null && currentIndex >= 0 && currentIndex < chunks.size()) {
            mediaController.updateState(true, currentIndex, title);
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
        this.isEngineLoading = false;
        sendLoadingBroadcast(false);
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

    @Override
    public void onEngineReady() {
        Log.d("ReaderController", "onEngineReady: pendingResume=" + pendingResume);
        this.isEngineLoading = false;
        sendLoadingBroadcast(false);
        if (pendingResume) {
            pendingResume = false;
            new Handler(Looper.getMainLooper()).post(() -> {
                speakCurrent();
                if (mediaController != null) {

                    mediaController.updateState(true, currentIndex, title);
                }
            });
        }
    }

    @Override
    public void onEngineMissingData(String langCode, String enginePackage) {
        Log.d("ReaderController", "Missing TTS data for " + langCode + " -> requesting UI dialog");
        new Handler(Looper.getMainLooper()).post(() -> {
            pauseReading();
            Intent intent = new Intent(ReaderService.ACTION_TTS_MISSING_DATA);
            intent.putExtra("lang", langCode);
            intent.putExtra("engine", enginePackage);
            intent.setPackage(ctx.getPackageName());
            ctx.sendBroadcast(intent);
        });
    }

    private int parseIndex(String utteranceId) {
        try {
            if (utteranceId != null && utteranceId.startsWith("CHUNK_")) {
                return Integer.parseInt(utteranceId.substring("CHUNK_".length()));
            }
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    public void checkHealth() {
        if (engine instanceof BaseTtsEngine) {
            ((BaseTtsEngine) engine).checkEngineHealth(
                    () -> android.util.Log.d("ReaderController", "TTS is healthy"),
                    () -> {
                        android.util.Log.w("ReaderController", "TTS was dead. Recreating...");
                        ((BaseTtsEngine) engine).recreateTts();
                    }
            );
        }
    }

    public void reattachListener() {
        if (engine != null) {
            engine.setListener(this);
        }
    }

    public boolean isEngineLoading() {
        return this.isEngineLoading;
    }

    private void sendLoadingBroadcast(boolean isLoading) {
        Intent intent = new Intent(isLoading ? ReaderService.ACTION_TTS_LOADING : ReaderService.ACTION_TTS_READY);
        intent.setPackage(ctx.getPackageName());
        ctx.sendBroadcast(intent);
    }

    public void switchEngine(TtsEngineStrategy newEngine, boolean wasPlaying) {
        Log.d("ReaderController", "switchEngine: pendingResume=" + wasPlaying);
        sendLoadingBroadcast(true);
        if (this.engine != null) {
            this.engine.stop();
            this.engine.shutdown();
        }
        this.pendingResume = wasPlaying;
        this.isPaused = !wasPlaying;

        this.engine = newEngine;
        this.engine.setListener(this);
        this.engine.init(ctx);
        this.engine.setVolume(normalVolume);
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
