package com.doctell.app.model.voice.media;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
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

import androidx.annotation.Nullable;
import androidx.media.session.MediaButtonReceiver;

import com.doctell.app.model.analytics.DocTellCrashlytics;
import com.doctell.app.model.entity.Book;
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
    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener =
            focusChange -> {
                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_LOSS:
                        Log.d("ReaderService", "AUDIOFOCUS_LOSS → pause/stop");
                        pause();
                        break;

                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        Log.d("ReaderService", "AUDIOFOCUS_LOSS_TRANSIENT → pause");
                        pause();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        if (readerController != null) {
                            // need to implement setVolume in ReaderController/TtsEngine
                            // Or just pause if TTS doesn't support volume scaling easily.
                            // Ideally: readerController.setVolume(0.3f);
                            Log.d("ReaderService", "Ducking volume (Not implemented yet, ensure TTS supports it)");
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN:
                        Log.d("ReaderService", "AUDIOFOCUS_GAIN");
                        // readerController.setVolume(1.0f);
                        play();
                        break;
                }
            };

    private final android.content.BroadcastReceiver noisyReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                pause(); // Pause immediately when headphones are unplugged
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
    private boolean autoReading = false;

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

        Notification notification = mediaController.buildInitialNotification();
        startForeground(ReaderMediaController.NOTIFICATION_ID, notification);
        registerReceiver(noisyReceiver, new android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY));

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
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build())
                        .setAcceptsDelayedFocusGain(false)
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
            unregisterReceiver(noisyReceiver);
        }catch (Exception ignored){/*Receiver might not be registered*/}
        abandonAudioFocus();
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
        if (readerController != null && mediaController != null) {
            boolean isPlaying = mediaController.getMediaSession().getController().getPlaybackState().getState()
                    == android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;

            if (!isPlaying) {
                stopForeground(true);
                stopSelf();
            }
        }
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
            autoReading = true;
            requestAudioFocus();
            if (readerController != null) {
                readerController.play();
            }
        });
    }
    @Override
    public void pause() {
        safeExecuteAction(()-> {
            autoReading = false;
            if (readerController != null)
                readerController.pause();
        });
    }
    @Override
    public void stop() {
        safeExecuteAction(()-> {
            autoReading = false;
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
                            if (readerController != null) {
                                readerController.setChunks(chunks, 0);
                                readerController.startReading();
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
        if (intent != null) {
            try {
                if (mediaController != null && mediaController.getMediaSession() != null) {
                    MediaButtonReceiver.handleIntent(
                            mediaController.getMediaSession(),
                            intent
                    );
                }
            } catch (Exception ex) {
                Log.w("ReaderService", "Failed to handle media button intent", ex);
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
        autoReading = true;
        currentBook = book;
        Context appCtx = getApplicationContext();
        if (pdfManager == null) {
            pdfManager = new PdfManager(appCtx, currentBook.getLocalPath(),doc, pfd, renderer);
        }
        if(coverOfBook != null)
            mediaController.setCover(coverOfBook);

        executor.execute(() -> {
            try {
                String text = pdfManager.getPageText(currentBook.getLastPage());

                TTSBuffer buffer = TTSBuffer.getInstance();
                buffer.setPage(text);
                List<String> chunks = buffer.getAllSentences();
                int startSentence = currentBook.getSentence();

                mainHandler.post(() -> {
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
                    readerController.setChunks(chunks, startSentence);
                    readerController.startReading();

                    Notification n = mediaController.buildInitialNotification();
                    startForeground(1001, n);
                });

            } catch (IOException e) {
                e.printStackTrace();
                DocTellCrashlytics.logPdfError(currentBook, currentBook.getLastPage(), "render_page", e);
                // TODO: maybe notify UI or show a Toast via a callback
            }
        });
    }


}


