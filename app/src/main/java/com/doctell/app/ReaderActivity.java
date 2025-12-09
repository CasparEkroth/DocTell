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

import com.doctell.app.model.ChapterItem;
import com.doctell.app.model.data.Book;
import com.doctell.app.model.data.BookStorage;
import com.doctell.app.model.data.ChapterLoader;
import com.doctell.app.model.data.PdfLoader;
import com.doctell.app.model.data.PdfPreviewHelper;
import com.doctell.app.model.voice.HighlightListener;
import com.doctell.app.model.voice.ReaderController;
import com.doctell.app.model.voice.TTSBuffer;
import com.doctell.app.model.voice.TtsEngineStrategy;
import com.doctell.app.model.voice.media.ReaderService;
import com.doctell.app.model.voice.notPublic.TtsEngineProvider;
import com.doctell.app.view.HighlightOverlayView;
import com.doctell.app.view.ImageScale;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.FileNotFoundException;
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
    private List<ChapterItem> chapters;
    private ChapterLoader chapterLoader;
    private HighlightOverlayView highlightOverlay;
    private ReaderService readerService;
    private boolean isServiceBound = false;
    private PdfRenderer renderer;
    private ParcelFileDescriptor pfd;
    private PDDocument doc;
    private TtsEngineStrategy ttsEngine;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ReaderService.LocalBinder binder = (ReaderService.LocalBinder) service;
            readerService = binder.getService();
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

        String path = currentBook.getLocalPath();
        PdfLoader loader = PdfLoader.getInstance(getApplicationContext());

        // 1) Fast path: already loaded
        if (loader.isReady(path)) {
            PdfLoader.PdfSession session = loader.getCurrentSession();
            if (session != null) {
                useLoadedSession(session);
                return;
            }
            // if session is somehow null, just fall through and load again
        }
        // 2) Slow path: not loaded yet, or no session => load on background thread
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
        runOnUiThread(this::showNextPage);
    }

    @Override
    public void navBackward() {
        runOnUiThread(this::showPrevPage);
    }

    private void showNextPage() {
        currentBook.setSentence(0);
        showPage(currentBook.incrementPage());
        highlightOverlay.clearHighlights();
    }

    private void showPrevPage() {
        currentBook.setSentence(0);
        showPage(currentBook.decrementPage());
        highlightOverlay.clearHighlights();
    }

    private void showPage(int page) {
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
                    if (isSpeaking) speakPage();
                });
            } catch (IOException e) { /* handle */ }
        });
    }


    private void toggleTTS() {
        if (!isSpeaking) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            isSpeaking = true;
            btnTTS.setText(getString(R.string.pref_pause));
            if (!ttsStartedOnPage) {
                ttsStartedOnPage = true;
                speakPage();
            } else {
                if (isServiceBound && readerService != null) {
                    readerService.play();
                }
            }
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            isSpeaking = false;
            btnTTS.setText(getString(R.string.pref_play));
            if (isServiceBound && readerService != null) {
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
        isSpeaking = true;
        btnTTS.setText(getString(R.string.pref_pause));
        showLoading(true);
        // let the SERVICE do all heavy work (PDF + TTSBuffer + chunks)
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
    public void onPageFinished() {
        int nextPage = currentBook.getLastPage() + 1;
        if (nextPage < totalPages) {
            currentBook.setLastPage(nextPage);
            currentBook.setSentence(0);
            showPage(nextPage);
        } else {
            isSpeaking = false;
            runOnUiThread(() -> btnTTS.setText(getString(R.string.pref_play)));
        }
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
