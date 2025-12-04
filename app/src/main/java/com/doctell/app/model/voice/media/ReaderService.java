package com.doctell.app.model.voice.media;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;

import androidx.annotation.Nullable;

import com.doctell.app.model.data.PdfManager;
import com.doctell.app.model.voice.HighlightListener;
import com.doctell.app.model.voice.ReaderController;
import com.doctell.app.model.voice.TTSBuffer;
import com.doctell.app.model.voice.TtsEngineStrategy;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReaderService extends Service implements PlaybackControl, HighlightListener, ReaderController.MediaNav{

    public static final String CHANNEL_ID = "doctell_reader_service";
    private final IBinder binder = new LocalBinder();
    private ReaderController readerController;
    private ReaderMediaController mediaController;
    private HighlightListener uiHighlightListener;
    private List<String> currentPage;
    private ReaderController.MediaNav uiMediaNav;
    private PdfManager pdfManager;
    private String bookLocalPath;
    private int currentPageIndex;
    private ExecutorService executor;
    private Handler mainHandler;

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

    public List<String> getCurrentPage() {
        return currentPage;
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
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    @SuppressLint("ForegroundServiceType")
    public void startReading(Context ctx, List<String> page, TtsEngineStrategy engine) {
        this.currentPage = page;
        mediaController = new ReaderMediaController(ctx, this);

        readerController = new ReaderController(engine, page, this, ctx,this);

        // Hook media into controller
        readerController.setMediaController(mediaController);

        readerController.startReading();

        //Notification n = mediaController.buildInitialNotification();
        //startForeground(1001, n);
    }

    private void onHighlightChange(int sentenceIndex) {
        // TODO: send highlight updates to ReaderActivity via callback (later step)
    }

    // PUBLIC control API -----------------------------

    @Override
    public void play()   { if (readerController != null) readerController.play(); }
    @Override
    public void pause()  { if (readerController != null) readerController.pause(); }
    @Override
    public void stop()   { if (readerController != null) readerController.stop(); }
    @Override
    public void next()   { if (readerController != null) readerController.next(); }
    @Override
    public void prev()   { if (readerController != null) readerController.prev(); }

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
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mediaController != null) {
            Notification n = mediaController.buildInitialNotification();
            startForeground(1001, n);
        }
        return START_STICKY;
    }
    public void initBook(Context ctx, String localPath, int startPage) {
        this.bookLocalPath = localPath;
        this.currentPageIndex = startPage;
        this.pdfManager = new PdfManager(ctx, localPath);
    }
    public List<String> loadCurrentPageSentences() throws IOException {
        String pageText = pdfManager.getPageText(currentPageIndex);

        TTSBuffer.getInstance().setPage(pageText);
        return TTSBuffer.getInstance().getAllSentences();
    }
    public Bitmap getPageBitmap(DisplayMetrics dm, int widthPx) throws IOException {
        return pdfManager.renderPageBitmap(currentPageIndex, dm, widthPx);
    }

    @SuppressLint("ForegroundServiceType")
    public void startReading(Context ctx,
                             String bookPath,
                             int pageIndex,
                             TtsEngineStrategy engine) {

        this.bookLocalPath = bookPath;
        this.currentPageIndex = pageIndex;

        if (pdfManager == null) {
            pdfManager = new PdfManager(ctx, bookLocalPath);
        }

        if (mediaController == null) {
            mediaController = new ReaderMediaController(ctx, this);
        }

        executor.execute(() -> {
            try {
                String text = pdfManager.getPageText(currentPageIndex);

                TTSBuffer buffer = TTSBuffer.getInstance();
                buffer.setPage(text);
                List<String> chunks = buffer.getAllSentences();

                mainHandler.post(() -> {
                    if (readerController == null) {
                        readerController = new ReaderController(
                                engine,
                                chunks,
                                this,
                                ctx,
                                uiMediaNav
                        );
                        readerController.setMediaController(mediaController);
                    } else {
                        readerController.setChunks(chunks);
                    }

                    readerController.startReading();
                });

            } catch (IOException e) {
                e.printStackTrace();
                // TODO: maybe notify UI or show a Toast via a callback
            }
        });
    }


}


