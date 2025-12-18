package com.doctell.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.doctell.app.model.analytics.DocTellAnalytics;
import com.doctell.app.model.analytics.DocTellCrashlytics;
import com.doctell.app.model.entity.ChapterItem;
import com.doctell.app.model.entity.Book;
import com.doctell.app.model.pdf.PageLifecycleManager;
import com.doctell.app.model.repository.BookStorage;
import com.doctell.app.model.utils.ChapterLoader;
import com.doctell.app.model.pdf.PdfLoader;
import com.doctell.app.model.pdf.PdfPreviewHelper;
import com.doctell.app.model.utils.PermissionHelper;
import com.doctell.app.model.voice.HighlightListener;
import com.doctell.app.model.voice.ReaderController;
import com.doctell.app.model.voice.TtsEngineStrategy;
import com.doctell.app.model.voice.TtsWrapper;
import com.doctell.app.model.voice.media.ReaderService;
import com.doctell.app.model.voice.notPublic.TtsEngineProvider;
import com.doctell.app.view.HighlightOverlayView;
import com.doctell.app.view.ImageScale;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.tom_roush.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReaderActivity extends AppCompatActivity implements HighlightListener, ReaderController.MediaNav {
    private ImageView pdfImage;
    private ProgressBar loadingBar;
    private Button btnNext, btnPrev, btnTTS, btnChapter;
    private TextView pageIndicator;
    private ExecutorService exec;
    private Handler main;
    private int totalPages;
    private Book currentBook;
    private boolean isSpeaking = false;
    private boolean ttsStartedOnPage = false;
    private static final int REQ_SELECT_CHAPTER = 1001;
    private List<ChapterItem> chapters;
    private ChapterLoader chapterLoader;
    private HighlightOverlayView highlightOverlay;
    private ReaderService readerService;
    private boolean isServiceBound = false;
    private PdfRenderer renderer;
    private ParcelFileDescriptor pfd;
    private PDDocument doc;
    private TtsEngineStrategy ttsEngine;
    private MediaControllerCompat mediaController;

    private final MediaControllerCompat.Callback mediaCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state) {
                    boolean playing = state != null
                            && state.getState() == PlaybackStateCompat.STATE_PLAYING;
                    runOnUiThread(() -> syncTtsUiWithPlayback(playing));
                }
            };
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ReaderService.LocalBinder binder = (ReaderService.LocalBinder) service;
            readerService = binder.getService();
            try {
                MediaSessionCompat.Token token = readerService.getMediaSessionToken();
                if (token != null) {
                    mediaController = new MediaControllerCompat(ReaderActivity.this, token);
                    MediaControllerCompat.setMediaController(ReaderActivity.this, mediaController);
                    mediaController.registerCallback(mediaCallback);

                    PlaybackStateCompat state = mediaController.getPlaybackState();
                    if (state != null) {
                        boolean playing = state.getState() == PlaybackStateCompat.STATE_PLAYING;
                        syncTtsUiWithPlayback(playing);
                    }
                    readerService.setTitleInReaderController(currentBook.getTitle());
                }
            } catch (Exception e) {
                Log.e("ReaderActivity", "Failed to attach media controller", e);
            }

            isServiceBound = true;

            readerService.initBook(currentBook, doc, pfd, renderer);

            try {
                totalPages = readerService.getPageCount();
            } catch (IOException e) {
                Log.d("PDF","can't total numbers of pages", e);
                Toast.makeText(ReaderActivity.this,
                        "Could not read this PDF.",
                        Toast.LENGTH_LONG
                ).show();
                finish();
                return;
            }

            readerService.registerUiHighlightListener(ReaderActivity.this);
            readerService.registerUiMediaNav(ReaderActivity.this);

            showPage(currentBook.getLastPage());
            showLoading(false);

            if (readerService.isTtsLoading()) {
                showLoading(true);
            } else {
                showLoading(false);
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (readerService != null) {
                readerService.unregisterUiHighlightListener(ReaderActivity.this);
                readerService.unregisterUiMediaNav(ReaderActivity.this);
            }
            readerService = null;
            isServiceBound = false;

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isFinishing()) {
                    ensureServiceBound();
                }
            }, 500);

            runOnUiThread(() -> {
                isSpeaking = false;
                syncTtsUiWithPlayback(false);
            });
        }
    };

    private final BroadcastReceiver ttsStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ReaderService.ACTION_TTS_LOADING.equals(action)) {
                showLoading(true);
            } else if (ReaderService.ACTION_TTS_READY.equals(action)) {
                showLoading(false);
            } else if (ReaderService.ACTION_TTS_MISSING_DATA.equals(action)) {
                String langCode = intent.getStringExtra("lang");
                String enginePkg = intent.getStringExtra("engine");
                TtsWrapper.showMissingDataDialog(ReaderActivity.this, enginePkg,langCode);
            }
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);
        //PermissionHelper.ensureBluetoothPermission(this, this);
        pdfImage = findViewById(R.id.pdfImage);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        btnTTS = findViewById(R.id.btnTTS);
        pageIndicator = findViewById(R.id.pageIndicator);
        loadingBar = findViewById(R.id.loadingBar);
        btnChapter = findViewById(R.id.chapterBtn);

        highlightOverlay = findViewById(R.id.highlightOverlay);

        exec = Executors.newSingleThreadExecutor();
        main = new Handler(Looper.getMainLooper());
        ensureTtsInit();

        View root = findViewById(R.id.readerRoot);
        View bottomBar = findViewById(R.id.readerBottomBar);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets systemBars =
                    insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int top = systemBars.top;
            int bottom = systemBars.bottom;

            v.setPadding(
                    v.getPaddingLeft(),
                    top,
                    v.getPaddingRight(),
                    0
            );
            ViewGroup.MarginLayoutParams barLp =
                    (ViewGroup.MarginLayoutParams) bottomBar.getLayoutParams();
            barLp.bottomMargin = bottom + dp(16, v);
            bottomBar.setLayoutParams(barLp);

            return insets;
        });
        // Load Book from MainActivity
        Intent mainIntent = getIntent();
        String uriStr = null;
        if (mainIntent != null) {
            uriStr = mainIntent.getStringExtra("uri");
        }
        if (uriStr == null) {
            Log.e("ReaderActivity", "No 'uri' extra in intent");
            finish();
            return;
        }

        Uri uri = Uri.parse(uriStr);
        currentBook = BookStorage.findBookByUri(this, uri);
        assert currentBook != null;
        DocTellAnalytics.bookOpened(this,currentBook);
        DocTellCrashlytics.setCurrentBookContext(currentBook, currentBook.getLastPage());
        currentBook.setLastOpenedAt();

        btnNext.setOnClickListener(v -> showNextPage());
        btnPrev.setOnClickListener(v -> showPrevPage());
        btnTTS.setOnClickListener(v -> toggleTTS());
        btnChapter.setOnClickListener(v -> openChapterActivity());
        btnChapter.setEnabled(false);

        chapterLoader = new ChapterLoader();
        chapters = new ArrayList<>();
        chapterLoader.loadChaptersAsync(currentBook.getLocalPath(), loadedChapters -> {
            chapters.clear();
            chapters.addAll(loadedChapters);
            btnChapter.setEnabled(chapters != null);
            Log.d("ChapterLoader", "Loaded " + chapters.size() + " chapters");
        });

        ImageScale imageScale = new ImageScale(pdfImage, this, new ImageScale.TapNavigator() {
            @Override
            public void onTapLeft() {
                showPrevPage();
            }
            @Override
            public void onTapRight() {
                showNextPage();
            }
        });
        pdfImage.setOnTouchListener((view, motionEvent) -> {
            imageScale.onTouch(motionEvent);
            highlightOverlay.setImageMatrix(imageScale.getMatrix());
            return true;
        });

        loadPdfAsync();
    }

    private void ensureTtsInit() {
        if (ttsEngine == null) {
            ttsEngine = TtsEngineProvider.getEngine(getApplicationContext());
            exec.execute(() -> {
                ttsEngine.init(getApplicationContext());
            });
        }
    }
    private int dp(int value, View v) {
        float density = v.getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }

    private void ensureServiceAlive() {
        if (!isServiceBound || readerService == null) {
            Log.w("ReaderActivity", "Service not alive, forcing reconnect");
            ensureServiceBound();
            showLoading(true);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                showLoading(false);
            }, 2000);
        }
    }


    private void loadPdfAsync() {
        showLoading(true);
        if(readerService != null){
            readerService.pause();
            readerService.setTitleInReaderController(currentBook.getTitle());
        }

        String path = currentBook.getLocalPath();
        PdfLoader loader = PdfLoader.getInstance(getApplicationContext());

        //Fast path: already loaded
        if (loader.isReady(path)) {
            PdfLoader.PdfSession session = loader.getCurrentSession();
            if (session != null) {
                useLoadedSession(session);
                return;
            }
        }
        loader.loadIfNeeded(path, new PdfLoader.Listener() {
            @Override
            public void onLoaded(PdfLoader.PdfSession session) {
                useLoadedSession(session);
            }
            @Override
            public void onError(Throwable error) {
                Log.e("ReaderActivity", "Failed to load PDF", error);
                showLoading(false);

                if (error instanceof OutOfMemoryError) {
                    Toast.makeText(ReaderActivity.this,
                            "This PDF is too large to open on this device.",
                            Toast.LENGTH_LONG
                    ).show();
                } else {
                    Toast.makeText(ReaderActivity.this,
                            "Could not open this PDF.",
                            Toast.LENGTH_LONG
                    ).show();
                }
                finish();
            }
        });
    }

    private void syncTtsUiWithPlayback(boolean playing) {
        isSpeaking = playing;
        if (playing) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            btnTTS.setText(getString(R.string.pref_pause));
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            btnTTS.setText(getString(R.string.pref_play));
        }
    }

    private void useLoadedSession(PdfLoader.PdfSession session) {
        renderer = session.renderer;
        doc = session.doc;
        pfd = session.pfd;
        totalPages = session.pageCount;
        // Start + bind service
        Intent intent = new Intent(ReaderActivity.this, ReaderService.class);
        startService(intent); // idempotent
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);

        showLoading(false);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.e("ReaderActivity", "new keyEvent=" + event.toString() + " keyCode=" + keyCode);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_HEADSETHOOK:
                    toggleTTS();
                    return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void ensureServiceBound() {
        if (isServiceBound && readerService != null) {
            return;
        }
        Intent intent = new Intent(this, ReaderService.class);
        startService(intent);//idempotent
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    private void showLoading(boolean on) {
        if (loadingBar != null) loadingBar.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    @Override
    public void navForward() {
        runOnUiThread(() -> {
            int page = currentBook.getLastPage();
            showPageFromService(page);
        });
    }

    @Override
    public void navBackward() {
        runOnUiThread(() -> {
            int page = currentBook.getLastPage();
            showPageFromService(page);
        });
    }

    private void showNextPage() {
        int fromPage = currentBook.getLastPage();
        currentBook.setSentence(0);
        int toPage = currentBook.incrementPage();
        showPage(toPage);

        highlightOverlay.clearHighlights();
        DocTellAnalytics.pageChanged(this, currentBook, fromPage, toPage);
        DocTellCrashlytics.setCurrentBookContext(currentBook, toPage);
    }

    private void showPrevPage() {
        int fromPage = currentBook.getLastPage();
        currentBook.setSentence(0);
        int toPage = currentBook.decrementPage();
        showPage(toPage);

        highlightOverlay.clearHighlights();
        DocTellAnalytics.pageChanged(this, currentBook, fromPage, toPage);
        DocTellCrashlytics.setCurrentBookContext(currentBook, toPage);
    }


    private void showPage(int page) {
        showPageInternal(page, true);
    }
    private void showPageFromService(int page) {
        showPageInternal(page, false);
    }

    private void showPageInternal(int page, boolean autoSpeak) {
        if (page < 0 || page >= totalPages) return;
        if (!isServiceBound || readerService == null) {
            Log.w("ReaderActivity", "Service not bound, cannot load page");
            return;
        }
        PageLifecycleManager pageManager = readerService.getPageLifecycleManager();
        if (!pageManager.canStartPageLoad(page)) {
            Log.w("ReaderActivity", "Page load blocked: " + pageManager.getStateString());
            return;
        }
        pageManager.startPageLoad(page);
        ttsStartedOnPage = false;
        readerService.setPage(page);
        showLoading(true);
        exec.execute(() -> {
            try {
                Bitmap bmp = readerService.getPageBitmap(
                        pdfImage.getResources().getDisplayMetrics(),
                        pdfImage.getWidth()
                );
                runOnUiThread(() -> {
                    showLoading(false);
                    if (!pageManager.isCurrentPage(page)) {
                        Log.w("ReaderActivity", "Page changed during render, skipping UI update");
                        return;
                    }
                    pdfImage.setImageBitmap(bmp);
                    pageIndicator.setText((page + 1) + " / " + totalPages);
                    pageManager.markPageReady(page);
                    if (autoSpeak && isSpeaking) {
                        DocTellAnalytics.autoPageChanged(getApplicationContext(), currentBook, page);
                        speakPage();
                    }
                });
            } catch (IOException e) {
                Log.e("ReaderActivity", "Failed to render page", e);
                pageManager.resetToIdle();
                runOnUiThread(() -> showLoading(false));
            }
        });
    }

    private void toggleTTS() {
        int pageIndex = currentBook.getLastPage();
        if (!isSpeaking) {
            if (!PermissionHelper.cheekNotificationPermission(this)) {
                Toast.makeText(getApplicationContext(), "Notification permission required for playback", Toast.LENGTH_LONG).show();
                return;
            }
            showLoading(true);
            if (!ttsStartedOnPage) {
                ttsStartedOnPage = true;
                DocTellAnalytics.readingStarted(this, currentBook, pageIndex);
                speakPage();
            } else {
                ensureServiceAlive();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (isServiceBound && readerService != null) {
                        DocTellAnalytics.readingResumed(this, currentBook, pageIndex);
                        readerService.play();
                    } else {
                        Toast.makeText(this,
                                "Claude not connect to TTS(try again)",
                                Toast.LENGTH_SHORT).show();
                    }
                    showLoading(false);
                }, 500);
            }
        } else {
            if (isServiceBound && readerService != null) {
                DocTellAnalytics.readingPaused(this, currentBook, pageIndex);
                readerService.pause();
            }
        }
    }


    private void speakPage() {
        ensureServiceBound();
        if (!isServiceBound || readerService == null) {
            Toast.makeText(this, "TTS service not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!PermissionHelper.cheekNotificationPermission(this)) {
            return;
        }
        TtsEngineStrategy engine = TtsEngineProvider.getEngine(getApplicationContext());
        showLoading(true);
        readerService.startReading(
                currentBook,
                engine,
                doc,
                pfd,
                renderer
        );
        showLoading(false);
    }


    private void openChapterActivity() {
        if(chapters == null){
            Toast.makeText(this, "No chapters found.", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<Integer> pages = new ArrayList<>();
        ArrayList<Integer> levels = new ArrayList<>();

        for (ChapterItem c : chapters) {
            titles.add(c.getTitle());
            pages.add(c.getPageIndex());
            levels.add(c.getLevel());
        }
        Intent intent = new Intent(this, ChapterActivity.class);
        intent.putStringArrayListExtra("chapterTitles", titles);
        intent.putIntegerArrayListExtra("chapterPages", pages);
        intent.putIntegerArrayListExtra("chapterLevels", levels);
        //intent.putExtra("currentPage", currentPageIndex); // highlight

        startActivityForResult(intent, REQ_SELECT_CHAPTER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SELECT_CHAPTER && resultCode == RESULT_OK && data != null) {
            int page = data.getIntExtra("selectedPage", -1);
            if (page >= 0){
                currentBook.setLastPage(page);
                currentBook.setSentence(0);
                showPage(page);
            }
        }
    }

    // ---- HighlightListener callbacks ----
    @Override
    public void onChunkStart(int index, String text) {
        if (!isServiceBound || readerService == null) {
            return;
        }
        exec.execute(() -> {
            PageLifecycleManager pageManager = readerService.getPageLifecycleManager();
            int currentPage = currentBook.getLastPage();
            if (!pageManager.canProcessChunkStart(currentPage, index)) {
                Log.d("ReaderActivity", "Orphaned chunk dropped: page=" + currentPage +
                        ", chunk=" + index + ". " + pageManager.getStateString());
                return;
            }
            pageManager.startSpeakingChunk(currentPage, index);
            BitmapDrawable drawable = (BitmapDrawable) pdfImage.getDrawable();
            if (drawable == null) return;
            Bitmap bitmap = drawable.getBitmap();
            if (bitmap == null || renderer == null || doc == null) return;

            int bmpW = bitmap.getWidth();
            int bmpH = bitmap.getHeight();
            int pageW, pageH;
            PdfRenderer.Page page = null;

            try {
                synchronized(renderer) {
                    page = renderer.openPage(currentBook.getLastPage());
                }
                pageW = page.getWidth();
                pageH = page.getHeight();
            } finally {
                if (page != null) {
                    page.close();
                }
            }
            List<RectF> rects = PdfPreviewHelper.getRectsForSentence(
                    doc, currentBook.getLastPage(), text, bmpW, bmpH, pageW, pageH
            );
            for (RectF r : rects) {
                r.offset(0, -r.height());
            }
            main.post(()->{
                currentBook.setSentence(index);
                highlightOverlay.setHighlights(rects);
            });
        });
    }

    @Override
    public void onChunkDone(int index, String text) {
        if (isServiceBound && readerService != null) {
            PageLifecycleManager pageManager = readerService.getPageLifecycleManager();
            pageManager.finishSpeakingChunk(currentBook.getLastPage(), index);
        }
        currentBook.setSentence(index);
        BookStorage.updateBook(currentBook, this);
        highlightOverlay.clearHighlights();
    }

    @Override
    public void onPageFinished() {
        if (isServiceBound && readerService != null) {
            PageLifecycleManager pageManager = readerService.getPageLifecycleManager();
            pageManager.finishPage(currentBook.getLastPage());
        }
        // Auto page advance + auto read is now handled in ReaderService.
    }

    @Override
    protected void onResume(){
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ReaderService.ACTION_TTS_LOADING);
        filter.addAction(ReaderService.ACTION_TTS_READY);
        filter.addAction(ReaderService.ACTION_TTS_MISSING_DATA);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(ttsStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        }

        if (readerService != null && readerService.getReaderController() != null) {
            readerService.getReaderController().checkHealth();
            readerService.getReaderController().reattachListener();
        }
    }

    @Override
    protected void onPause() {
        try {
            unregisterReceiver(ttsStateReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered, ignore
        }
        if (currentBook != null) {
            BookStorage.updateBook(currentBook, this);
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (currentBook != null) {
            BookStorage.updateBook(currentBook, this);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (currentBook != null) {
            BookStorage.updateBook(currentBook, this);
        }
        DocTellCrashlytics.clearBookContext();
        if (mediaController != null) {
            mediaController.unregisterCallback(mediaCallback);
            mediaController = null;
        }
        if (isServiceBound && readerService != null) {
            readerService.unregisterUiHighlightListener(this);
            readerService.unregisterUiMediaNav(this);
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        if (exec != null) exec.shutdownNow();
        if (chapterLoader != null) chapterLoader.shutdown();

        super.onDestroy();
    }
}
