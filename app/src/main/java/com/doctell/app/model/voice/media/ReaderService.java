package com.doctell.app.model.voice.media;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;

import com.doctell.app.model.voice.ReaderController;
import com.doctell.app.model.voice.TtsEngineStrategy;
import com.doctell.app.model.voice.notPublic.TtsEngineProvider;

import java.util.ArrayList;

public class ReaderService extends Service implements PlaybackControl {

    public static final String ACTION_START = "START_READING";
    public static final String ACTION_PAUSE = "PAUSE_READING";
    public static final String ACTION_RESUME = "RESUME_READING";
    public static final String ACTION_STOP = "STOP_READING";
    public static final String ACTION_NEXT = "NEXT_READING";
    public static final String ACTION_PREV = "PREV_READING";

    private ReaderController readerController;
    private ReaderMediaController mediaController;

    @Override
    public void onCreate() {
        super.onCreate();
        // Will be fully initialized in onStartCommand().
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null || intent.getAction() == null)
            return START_STICKY;

        switch (intent.getAction()) {

            case ACTION_START: {
                // 1. Receive chunks from activity
                ArrayList<String> chunks = intent.getStringArrayListExtra("chunks");
                if (chunks == null) chunks = new ArrayList<>();

                // 2. Create controller objects ONCE
                if (readerController == null) {
                    TtsEngineStrategy engine = TtsEngineProvider.getEngine(this)
                    readerController = new ReaderController(engine, chunks, null, this);

                    mediaController = new ReaderMediaController(this, this);
                }

                // 3. Start reading
                play();
                break;
            }

            case ACTION_PAUSE:
                pause();
                break;

            case ACTION_RESUME:
                play();
                break;

            case ACTION_STOP:
                stop();
                break;

            case ACTION_NEXT:
                forward();
                break;

            case ACTION_PREV:
                backward();
                break;
        }

        return START_STICKY;
    }

    // ========= PlaybackControl implementation ==========

    @Override
    public void play() {
        if (readerController == null) return;
        readerController.play();
        updateForegroundNotification(true);
    }

    @Override
    public void pause() {
        if (readerController == null) return;
        readerController.pause();
        updateForegroundNotification(false);
    }

    @Override
    public void stop() {
        if (readerController == null) return;
        readerController.stop();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void forward() {
        if (readerController == null) return;
        readerController.forward();
        updateForegroundNotification(true);
    }

    @Override
    public void backward() {
        if (readerController == null) return;
        readerController.backward();
        updateForegroundNotification(true);
    }

    private void updateForegroundNotification(boolean isPlaying) {
        if (mediaController == null || readerController == null)
            return;

        String sentence = readerController.getCurrentSentence();
        int index = readerController.getCurrentIndex();

        startForeground(
                ReaderMediaController.NOTIFICATION_ID,
                mediaController.buildNotification(
                        isPlaying,
                        index,
                        sentence == null ? "" : sentence
                )
        );
        mediaController.updatePlaybackState(isPlaying, index);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // No binding needed
    }

    @Override
    public void onDestroy() {
        if (readerController != null) readerController.stop();
        if (mediaController != null) mediaController.release();
        super.onDestroy();
    }
}
