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
    private ParcelFileDescriptor pfd;
    private int currentPage = 0, totalPages;
    private Book currentBook;
    private PdfRenderer renderer;
    private String bookLocalPath;
    private boolean isSpeaking = false;
    private boolean ttsStartedOnPage = false;
    private static final int REQ_SELECT_CHAPTER = 1001;
    private static final int REQ_POST_NOTIFICATIONS = 42;
    private List<ChapterItem> chapters;
    private ChapterLoader chapterLoader;
    private HighlightOverlayView highlightOverlay;
    private TTSBuffer buffer;
    private PDDocument doc;
    private ReaderService readerService;
    private boolean isServiceBound = false;
    private boolean firstPage = false;

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
        buffer = TTSBuffer.getInstance();

        exec = Executors.newSingleThreadExecutor();
        main = new Handler(Looper.getMainLooper());

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
        String uriString = getIntent().getStringExtra("uri");
        Uri uri = Uri.parse(uriString);
        currentBook = BookStorage.findBookByUri(this, uri);
        assert currentBook != null;
        bookLocalPath = currentBook.getLocalPath();

        // Bind till ReaderService (foreground TTS + media controls)
        Intent serviceIntent = new Intent(this, ReaderService.class);
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);

        btnNext.setOnClickListener(v -> showNextPage());
        btnPrev.setOnClickListener(v -> showPrevPage());
        btnTTS.setOnClickListener(v -> toggleTTS());
        btnChapter.setOnClickListener(v -> openChapterActivity());
        btnChapter.setEnabled(false);

        chapterLoader = new ChapterLoader();
        chapters = new ArrayList<>();
        chapterLoader.loadChaptersAsync(bookLocalPath, loadedChapters -> {
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

        loadPdfAsync(bookLocalPath, currentBook.getLastPage());
    }

    private int dp(int value, View v) {
        float density = v.getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ReaderService.LocalBinder binder = (ReaderService.LocalBinder) service;
            readerService = binder.getService();
            isServiceBound = true;

            readerService.registerUiHighlightListener(ReaderActivity.this);
            readerService.registerUiMediaNav(ReaderActivity.this);
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
    private void loadPdfAsync(String path, int pageToShow) {
        showLoading(true);
        exec.execute(() -> {
            try {
                File file = new File(path);
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                renderer = new PdfRenderer(pfd);

                doc = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly());

                totalPages = renderer.getPageCount();
                main.post(() -> {
                    showLoading(false);
                    showPage(pageToShow);
                });
            } catch (Exception e) {
                e.printStackTrace();
                main.post(() -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to open PDF", Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
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
        showPage(currentPage + 1);
        buffer.clear();
        highlightOverlay.clearHighlights();
    }

    private void showPrevPage() {
        currentBook.setSentence(0);
        showPage(currentPage - 1);
        buffer.clear();
        highlightOverlay.clearHighlights();
    }

    private void showPage(int page) {
        if (page < 0 || page >= totalPages) return;

        currentPage = page;
        ttsStartedOnPage = false;
        showLoading(true);

        Bitmap bmp = PdfPreviewHelper.renderOnePage(
                renderer, currentPage,
                pdfImage.getResources().getDisplayMetrics(),
                pdfImage.getWidth());

        pdfImage.setImageBitmap(bmp);
        pageIndicator.setText((page + 1) + " / " + totalPages);

        currentBook.setLastPage(currentPage);
        BookStorage.updateBook(currentBook,this);

        showLoading(false);
        if (isSpeaking) speakPage();
    }

    private void toggleTTS() {
        if (!isSpeaking) {
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
            isSpeaking = false;
            btnTTS.setText(getString(R.string.pref_play));
            if (isServiceBound && readerService != null) {
                readerService.pause();
            }
        }
    }

    private void speakPage() {
        showLoading(true);
        exec.execute(() -> {
            try {
                String text = PdfPreviewHelper.extractOnePageText(doc, currentPage);
                main.post(() -> {
                    showLoading(false);
                    if (text == null || text.trim().isEmpty()) {
                        Toast.makeText(this, "No searchable text on this page (scan?).", Toast.LENGTH_SHORT).show();
                    } else {
                        isSpeaking = true;
                        btnTTS.setText(getString(R.string.pref_pause));

                        buffer.setPage(text);
                        List<String> chunks = buffer.getAllSentences();
                        if (!firstPage)
                            firstPage = true;
                        else
                            currentBook.setSentence(0);

                        if (ensureNotificationPermission()) {
                            TtsEngineStrategy engine = TtsEngineProvider.getEngine(getApplicationContext());
                            if (isServiceBound && readerService != null) {
                                readerService.startReading(ReaderActivity.this, chunks, engine);
                            } else {
                                Toast.makeText(this, "TTS service not connected", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
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
            if (page >= 0)
                showPage(page);
        }
    }

    private void closeRenderer() {
        try { if (renderer != null) renderer.close(); } catch (Exception ignored) {}
        try { if (pfd != null) pfd.close(); } catch (Exception ignored) {}
        renderer = null;
        pfd = null;
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
            page = renderer.openPage(currentPage);
            pageW = page.getWidth();
            pageH = page.getHeight();
        } finally {
            if (page != null) {
                page.close();
            }
        }
        List<RectF> rects = PdfPreviewHelper.getRectsForSentence(
                doc, currentPage, text, bmpW, bmpH, pageW, pageH
        );

        for (RectF r : rects) {
            r.offset(0, -r.height());
        }
        //Log.d("sentence", "nr " + index);
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
        if (currentPage + 1 < totalPages) {
            showNextPage();
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
        buffer.clear();

        if (isServiceBound && readerService != null) {
            readerService.unregisterUiHighlightListener(this);
            readerService.unregisterUiMediaNav(this);
            unbindService(serviceConnection);
            isServiceBound = false;
        }

        if (doc != null) {
            try {doc.close();}
            catch (IOException e) {throw new RuntimeException(e);}
        }
        try {closeRenderer();}
        catch (Exception ignored) {}

        if (exec != null) exec.shutdownNow();
        if (chapterLoader != null) chapterLoader.shutdown();

        if (isServiceBound) {
            try {unbindService(serviceConnection);
            } catch (Exception ignored) {}
            isServiceBound = false;
        }
        super.onDestroy();
    }
}
