package com.doctell.app.model.voice.media;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.media.session.MediaButtonReceiver;

import com.doctell.app.R;
import com.doctell.app.model.analytics.DocTellCrashlytics;
import com.doctell.app.model.entity.Book;
import com.doctell.app.model.pdf.PageLifecycleManager;
import com.doctell.app.model.repository.BookStorage;
import com.doctell.app.model.pdf.PdfManager;
import com.doctell.app.model.pdf.PdfPreviewHelper;
import com.doctell.app.model.voice.HighlightListener;
import com.doctell.app.model.voice.ReaderController;
import com.doctell.app.model.voice.TTSBuffer;
import com.doctell.app.model.voice.TtsEngineStrategy;
import com.tom_roush.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReaderService extends Service implements PlaybackControl, HighlightListener, ReaderController.MediaNav{

    public static final String CHANNEL_ID = "doctell_reader_service";
    private final IBinder binder = new LocalBinder();
    private ReaderController readerController;
    private ReaderMediaController mediaController;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean resumeAfterFocusGain = false;
    private boolean autoReading = false;
    private MediaPlayer silentPlayer;
    private PageLifecycleManager pageLifecycleManager;
    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener =
            focusChange -> {
                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_LOSS:
                        Log.d("ReaderService", "AUDIOFOCUS_LOSS → pause/stop");
                        resumeAfterFocusGain = false;   // full loss, don’t auto-resume
                        pause();
                        break;

                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        Log.d("ReaderService", "AUDIOFOCUS_LOSS_TRANSIENT → pause (remember to resume)");
                        // Only remember to resume if we were actually reading
                        if (autoReading) {
                            resumeAfterFocusGain = true;
                            pause();
                        }
                        break;

                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        Log.d("ReaderService", "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                        // For now just treat like transient loss, or implement real ducking if you want
                        if (autoReading) {
                            resumeAfterFocusGain = true;
                            pause();
                        }
                        break;

                    case AudioManager.AUDIOFOCUS_GAIN:
                        Log.d("ReaderService", "AUDIOFOCUS_GAIN, resumeAfterFocusGain=" + resumeAfterFocusGain);
                        if (resumeAfterFocusGain) {
                            resumeAfterFocusGain = false;
                            play();  // calls ReaderController.play() and updates MediaSession
                        }
                        break;
                }
            };


    private final BroadcastReceiver headsetMonitor = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case AudioManager.ACTION_AUDIO_BECOMING_NOISY:
                    Log.d("ReaderService", "Headphones unplugged -> Pausing");
                    pause();
                    break;
                case android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED:
                case android.media.AudioManager.ACTION_HEADSET_PLUG:
                    Log.d("ReaderService", "Headset connected -> Reclaiming Priority");
                    if (mediaController != null) {
                        mediaController.refreshSession();
                    }
                    if (autoReading) {
                        play();
                    }
                    break;
                case android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED:
                    Log.d("ReaderService", "Headset disconnected");
                    pause();
                    break;
            }
        }
    };

    private HighlightListener uiHighlightListener;
    private Book currentBook;
    private ReaderController.MediaNav uiMediaNav;
    private PdfManager pdfManager;
    private ExecutorService executor;
    private Handler mainHandler;
    private Bitmap coverOfBook;


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel(CHANNEL_ID,
                            "DocTell Reading",
                            NotificationManager.IMPORTANCE_LOW);

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    public void registerUiMediaNav(ReaderController.MediaNav nav) {
        uiMediaNav = nav;
    }

    public void unregisterUiMediaNav(ReaderController.MediaNav nav) {
        if (uiMediaNav == nav) {
            uiMediaNav = null;
        }
    }

    @Override
    public void navForward() {
        if (uiMediaNav != null) {
            uiMediaNav.navForward();
        }
    }

    @Override
    public void navBackward() {
        if (uiMediaNav != null) {
            uiMediaNav.navBackward();
        }
    }

    public void setPage(int pageIndex) {
        currentBook.setLastPage(pageIndex);
        //currentBook.setSentence(0);
        onReadingPositionChanged();
    }

    public int getPageCount() throws IOException{
        return pdfManager.getPageCount();
    }

    public class LocalBinder extends Binder {
        public ReaderService getService() {
            return ReaderService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("ReaderService", "onCreate");

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();

        mediaController = new ReaderMediaController(this, this);
        pageLifecycleManager = new PageLifecycleManager();

        Notification notification = mediaController.buildInitialNotification();
        startForeground(ReaderMediaController.NOTIFICATION_ID, notification);

        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        filter.addAction(AudioManager.ACTION_HEADSET_PLUG);
        // Note: This requires BLUETOOTH_CONNECT permission on Android 12+
        filter.addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(headsetMonitor, filter);
    }

    public MediaSessionCompat.Token getMediaSessionToken() {
        if (mediaController != null) {
            return mediaController.getMediaSession().getSessionToken();
        }
        return null;
    }

    private void requestAudioFocus() {
        if (audioManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(playbackAttributes)
                        .setOnAudioFocusChangeListener(audioFocusChangeListener)
                        .build();
            }
            int result = audioManager.requestAudioFocus(audioFocusRequest);
            Log.d("ReaderService", "requestAudioFocus result = " + result);
        } else {
            int result = audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
            );
            Log.d("ReaderService", "requestAudioFocus (legacy) result = " + result);
        }
    }


    private void abandonAudioFocus() {
        if (audioManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            }
        } else {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
        }
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(headsetMonitor);
        }catch (Exception ignored){/*Receiver might not be registered*/}
        abandonAudioFocus();
        stopSilentAudio();
        onReadingPositionChanged();
        super.onDestroy();
        Log.d("ReaderService", "onDestroy");
        if (readerController != null) {
            readerController.stop();
            readerController.shutdown();
        }

        if (pdfManager != null) {
            pdfManager.close();
            pdfManager = null;
        }

        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }

        if (mediaController != null) {
            mediaController.release();
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
    }

    private void onReadingPositionChanged() {
        if (currentBook == null) return;
        BookStorage.updateBook(currentBook, getApplicationContext());
    }

    private void onHighlightChange(int sentenceIndex) {
        // TODO: send highlight updates to ReaderActivity via callback (later step)
    }

    // PUBLIC control API -----------------------------

    @Override
    public void play() {
        safeExecuteAction(()->{
            Log.d("ReaderService","play was entered");
            autoReading = true;
            requestAudioFocus();
            startSilentAudio();
            if (mediaController != null && mediaController.getMediaSession() != null) {
                mediaController.getMediaSession().setActive(true);
            }

            if (readerController != null) {
                readerController.play();
            }
        });
    }
    @Override
    public void pause() {
        safeExecuteAction(()-> {
            Log.d("ReaderService","pause was entered");
            autoReading = false;
            stopSilentAudio();
            if (mediaController != null && mediaController.getMediaSession() != null) {
                mediaController.getMediaSession().setActive(true);
            }
            if (readerController != null){
                readerController.pause();
                Notification n = mediaController.buildNotification();
                startForeground(ReaderMediaController.NOTIFICATION_ID, n);
            }

        });
    }
    @Override
    public void stop() {
        safeExecuteAction(()-> {
            Log.d("ReaderService","stop was entered");
            autoReading = false;
            stopSilentAudio();
            if (readerController != null)
                readerController.stop();
            abandonAudioFocus();
        });
    }
    @Override
    public void next() {
        safeExecuteAction(()->{
            if (currentBook == null || pdfManager == null) return;
            try {
                int pageCount = pdfManager.getPageCount();
                int currentPage = currentBook.getLastPage();
                if (currentPage + 1 >= pageCount) return;
                final int newPage = currentPage + 1;

                currentBook.setLastPage(newPage);
                currentBook.setSentence(0);
                onReadingPositionChanged();
                executor.execute(() -> {
                    try {
                        List<String> chunks = loadCurrentPageSentences();
                        mainHandler.post(() -> {
                            if (readerController != null && !chunks.isEmpty()) {
                                readerController.setChunks(chunks, 0);
                                readerController.startReading();
                            }else if(readerController != null){
                                next();
                                Toast.makeText(this,"illegible text",Toast.LENGTH_SHORT).show();
                            }
                            if (uiMediaNav != null) {
                                uiMediaNav.navForward();
                            }
                        });
                    } catch (IOException e) {
                        Log.e("ReaderService", "next(): failed to load page text", e);
                        DocTellCrashlytics.logPdfError(currentBook, newPage, "render_page", e);
                    }
                });

            } catch (IOException e) {
                Log.e("ReaderService", "next(): getPageCount failed", e);
                DocTellCrashlytics.logPdfError(currentBook, currentBook.getLastPage(), "render_page", e);
            }
        });
    }

    @Override
    public void prev() {
        safeExecuteAction(()-> {
            if (currentBook == null || pdfManager == null) return;

            int currentPage = currentBook.getLastPage();
            if (currentPage <= 0) return;

            final int newPage = currentPage - 1;

            currentBook.setLastPage(newPage);
            currentBook.setSentence(0);
            onReadingPositionChanged();

            executor.execute(() -> {
                try {
                    List<String> chunks = loadCurrentPageSentences();

                    mainHandler.post(() -> {
                        if (readerController != null) {
                            readerController.setChunks(chunks, 0);
                            readerController.startReading();
                        }
                        if (uiMediaNav != null) {
                            uiMediaNav.navBackward();
                        }
                    });
                } catch (IOException e) {
                    Log.e("ReaderService", "prev(): failed to load page text", e);
                    DocTellCrashlytics.logPdfError(currentBook, newPage, "render_page", e);
                }
            });
        });
    }

    public void registerUiHighlightListener(HighlightListener listener) {
        uiHighlightListener = listener;
    }

    public void unregisterUiHighlightListener(HighlightListener listener) {
        if (uiHighlightListener == listener) {
            uiHighlightListener = null;
        }
    }

    // ==== HighlightListener coming from ReaderController ====

    @Override
    public void onChunkStart(int index, String text) {
        currentBook.setSentence(index);
        onReadingPositionChanged();
        if (uiHighlightListener != null) {
            uiHighlightListener.onChunkStart(index, text);
        }
    }

    @Override
    public void onChunkDone(int index, String text) {
        if (uiHighlightListener != null) {
            uiHighlightListener.onChunkDone(index, text);
        }
    }

    @Override
    public void onPageFinished() {
        if (uiHighlightListener != null) {
            uiHighlightListener.onPageFinished();
        }

        if (autoReading) {
            try {
                int pageCount = pdfManager != null ? pdfManager.getPageCount() : 0;
                if (currentBook != null && currentBook.getLastPage() + 1 < pageCount) {
                    next();
                } else {
                    autoReading = false;
                    if (readerController != null) {
                        readerController.pause();
                    }
                }
            } catch (Exception e) {
                Log.e("ReaderService", "Failed to auto advance page", e);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            if (mediaController != null && mediaController.getMediaSession() != null) {
                MediaButtonReceiver.handleIntent(mediaController.getMediaSession(), intent);
            } else {
                // Edge Case: Service started by button, but controller not ready.
                // You might need to re-initialize from storage here if you want
                // "Play" to work from a cold dead state.
                Log.w("ReaderService", "MediaButton received but Controller is null");
            }
        }
        if (intent != null) {
            String action = intent.getAction();

            if (ReaderMediaController.ACTION_PLAY.equals(action)) {
                safeExecuteAction(this::play);
            } else if (ReaderMediaController.ACTION_PAUSE.equals(action)) {
                safeExecuteAction(this::pause);
            } else if (ReaderMediaController.ACTION_NEXT.equals(action)) {
                safeExecuteAction(this::next);
            } else if (ReaderMediaController.ACTION_PREV.equals(action)) {
                safeExecuteAction(this::prev);
            }
        }

        return START_STICKY;
    }

    public PageLifecycleManager getPageLifecycleManager() {
        return pageLifecycleManager;
    }
    private void safeExecuteAction(Runnable action) {
        try {
            if (action != null) {
                action.run();
            }
        } catch (Exception e) {
            Log.e("ReaderService", "Error executing action", e);
            DocTellCrashlytics.logException(e);
        }
    }

    private void startSilentAudio() {
        if (silentPlayer == null) {
            silentPlayer = MediaPlayer.create(this, R.raw.silence_1s); // 1s silent WAV
            silentPlayer.setLooping(true);
            silentPlayer.setVolume(0f, 0f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                silentPlayer.setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                );
            }
        }
        if (!silentPlayer.isPlaying()) {
            silentPlayer.start();
        }
    }

    private void stopSilentAudio() {
        if (silentPlayer != null && silentPlayer.isPlaying()) {
            silentPlayer.pause();
        }
    }
    public void initBook(Book book, PDDocument doc, ParcelFileDescriptor pdf, PdfRenderer renderer) {
        Context appCtx = getApplicationContext();
        if (book != null) {
            currentBook = book;
            this.pdfManager = new PdfManager(appCtx, currentBook.getLocalPath(), doc, pdf, renderer);
            executor.execute(()->{
                try {
                    pdfManager.ensureOpened();

                    Uri uri = currentBook.getUri();
                    String thumbPath = PdfPreviewHelper.ensureThumb(appCtx, uri, 320);
                    coverOfBook = PdfPreviewHelper.loadThumbBitmap(thumbPath);

                    if (coverOfBook == null) {
                    } else {
                        if (mediaController != null) {
                            mainHandler.post(()->{
                                mediaController.setCover(coverOfBook);
                            });
                        }
                    }
                } catch (IOException e) {
                    Log.e("ReaderService", "initBook: failed to generate/load thumbnail", e);
                    DocTellCrashlytics.logPdfError(currentBook, 0, "render_page", e);
                }
            });
        }
    }
    public List<String> loadCurrentPageSentences() throws IOException {
        String pageText = pdfManager.getPageText(currentBook.getLastPage());

        TTSBuffer.getInstance().setPage(pageText);
        return TTSBuffer.getInstance().getAllSentences();
    }
    public Bitmap getPageBitmap(DisplayMetrics dm, int widthPx) throws IOException {
        return pdfManager.renderPageBitmap(currentBook.getLastPage(), dm, widthPx);
    }

    @SuppressLint("ForegroundServiceType")
    public void startReading(Book book,
                             TtsEngineStrategy engine,
                             PDDocument doc,
                             ParcelFileDescriptor pfd,
                             PdfRenderer renderer) {
        requestAudioFocus();
        startSilentAudio();
        autoReading = true;
        currentBook = book;
        Context appCtx = getApplicationContext();

        if (pdfManager == null) {
            pdfManager = new PdfManager(appCtx, currentBook.getLocalPath(), doc, pfd, renderer);
        }

        if (coverOfBook != null)
            mediaController.setCover(coverOfBook);

        executor.execute(() -> {
            try {
                String text = pdfManager.getPageText(currentBook.getLastPage());
                TTSBuffer buffer = TTSBuffer.getInstance();
                buffer.setPage(text);
                List<String> chunks = buffer.getAllSentences();

                if (chunks == null || chunks.isEmpty()) {
                    Log.w("ReaderService", "No text found on page " + currentBook.getLastPage());
                    mainHandler.post(() -> {
                        Toast.makeText(appCtx,
                                "No readable text found on this page",
                                Toast.LENGTH_SHORT).show();
                        autoReading = false;
                        stopSilentAudio();
                    });
                    return;
                }

                int startSentence = currentBook.getSentence();

                mainHandler.post(() -> {
                    // Initialize controller if needed
                    if (readerController == null) {
                        readerController = new ReaderController(
                                engine,
                                chunks,
                                this,
                                appCtx,
                                uiMediaNav
                        );
                        readerController.setMediaController(mediaController);
                    } else {
                        readerController.setChunks(chunks, startSentence);
                    }
                    Log.d("ReaderService", "Starting reading with " + chunks.size() + " chunks");
                    readerController.startReading();

                    Notification n = mediaController.buildInitialNotification();
                    startForeground(1001, n);

                    MediaSessionCompat session = mediaController.getMediaSession();
                    if (session != null) session.setActive(true);
                });

            } catch (IOException e) {
                e.printStackTrace();
                DocTellCrashlytics.logPdfError(currentBook, currentBook.getLastPage(), "render_page", e);
                mainHandler.post(() -> {
                    Toast.makeText(appCtx,
                            "Failed to read page text",
                            Toast.LENGTH_SHORT).show();
                    autoReading = false;
                    stopSilentAudio();
                });
            }
        });
    }



}


