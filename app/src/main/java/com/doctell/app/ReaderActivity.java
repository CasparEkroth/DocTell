package com.doctell.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
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
import com.doctell.app.model.repository.BookStorage;
import com.doctell.app.model.utils.ChapterLoader;
import com.doctell.app.model.pdf.PdfLoader;
import com.doctell.app.model.pdf.PdfPreviewHelper;
import com.doctell.app.model.voice.HighlightListener;
import com.doctell.app.model.voice.ReaderController;
import com.doctell.app.model.voice.TtsEngineStrategy;
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
    private static final int REQ_POST_NOTIFICATIONS = 42;
    private static final int REQ_BLUETOOTH_CONNECT = 43;
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
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (readerService != null) {
                readerService.unregisterUiHighlightListener(ReaderActivity.this);
                readerService.unregisterUiMediaNav(ReaderActivity.this);
            }
            readerService = null;
            isServiceBound = false;
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);
        ensureBluetoothPermission();
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

    private void loadPdfAsync() {
        showLoading(true);
        if(readerService != null)readerService.pause();

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

    private boolean ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return true;

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }

        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQ_POST_NOTIFICATIONS
        );
        return false;
    }

    /**
     * Request BLUETOOTH_CONNECT permission on API 31+ (Android 12) if not already granted.
     * This permission is required to connect to Bluetooth headsets for voice communication.
     */
    private void ensureBluetoothPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
        ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    REQ_BLUETOOTH_CONNECT
            );
        }
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
        ttsStartedOnPage = false;
        readerService.setPage(page);

        showLoading(true);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Bitmap bmp = readerService.getPageBitmap(
                        pdfImage.getResources().getDisplayMetrics(),
                        pdfImage.getWidth()
                );
                runOnUiThread(() -> {
                    showLoading(false);
                    pdfImage.setImageBitmap(bmp);
                    pageIndicator.setText((page + 1) + " / " + totalPages);
                    if (autoSpeak && isSpeaking) {
                        DocTellAnalytics.autoPageChanged(getApplicationContext(),currentBook,page);
                        speakPage();
                    }
                });
            } catch (IOException e) { /* handle */ }
        });
    }

    private void toggleTTS() {
        int pageIndex = currentBook.getLastPage();
        if (!isSpeaking) {
            if (!ttsStartedOnPage) {
                ttsStartedOnPage = true;
                DocTellAnalytics.readingStarted(this, currentBook, pageIndex);
                speakPage();
            } else {
                if (isServiceBound && readerService != null) {
                    DocTellAnalytics.readingResumed(this, currentBook, pageIndex);
                    readerService.play();
                }
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
        if (!ensureNotificationPermission()) {
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
        BitmapDrawable drawable = (BitmapDrawable) pdfImage.getDrawable();
        if (drawable == null) return;
        Bitmap bitmap = drawable.getBitmap();
        if (bitmap == null || renderer == null || doc == null) return;

        int bmpW = bitmap.getWidth();
        int bmpH = bitmap.getHeight();

        int pageW, pageH;
        PdfRenderer.Page page = null;
        try {
            page = renderer.openPage(currentBook.getLastPage());
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

        currentBook.setSentence(index);
        highlightOverlay.setHighlights(rects);
    }


    @Override
    public void onChunkDone(int index, String text) {
        currentBook.setSentence(index);
        BookStorage.updateBook(currentBook, this);
        highlightOverlay.clearHighlights();
    }

    @Override
    public void onPageFinished() {// not needed
        // Auto page advance + auto read is now handled in ReaderService.
    }

    @Override
    protected void onPause() {
        BookStorage.updateBook(currentBook, this);
        super.onPause();
    }

    @Override
    protected void onStop() {
        BookStorage.updateBook(currentBook, this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        BookStorage.updateBook(currentBook, this);
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
