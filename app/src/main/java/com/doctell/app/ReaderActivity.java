package com.doctell.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.widget.Toast;

import com.doctell.app.model.ChapterItem;
import com.doctell.app.model.data.Book;
import com.doctell.app.model.data.BookStorage;
import com.doctell.app.model.data.ChapterLoader;
import com.doctell.app.model.data.PdfPreviewHelper;
import com.doctell.app.model.voice.HighlightListener;
import com.doctell.app.model.voice.ReaderController;
import com.doctell.app.model.voice.TTSBuffer;
import com.doctell.app.model.voice.TtsEngineStrategy;
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

public class ReaderActivity extends AppCompatActivity implements HighlightListener {
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
    private static final int REQ_SELECT_CHAPTER = 1001;
    private List<ChapterItem> chapters;
    private ChapterLoader chapterLoader;
    private HighlightOverlayView highlightOverlay;
    private TTSBuffer buffer;
    private PDDocument doc;
    private ReaderController readerController;

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

        ImageScale imageScale = new ImageScale(pdfImage,this);
        pdfImage.setOnTouchListener((view,motionEvent) -> {
            imageScale.onTouch(motionEvent);
            highlightOverlay.setImageMatrix(imageScale.getMatrix());
            return true;
        });

        // Load Book from MainActivity
        String uriString = getIntent().getStringExtra("uri");
        Uri uri = Uri.parse(uriString);
        currentBook = BookStorage.findBookByUri(this, uri);
        assert currentBook != null;
        bookLocalPath = currentBook.getLocalPath();

        //TtsEngineStrategy engine = LocalTtsEngine.getInstance(getApplicationContext());
        TtsEngineStrategy engine = TtsEngineProvider.getEngine(getApplicationContext());
        readerController = new ReaderController(
                engine,
                java.util.Collections.emptyList(),
                this,
                this
        );

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

        showLoading(true);
        openRendererAsync();
    }

    private void openRendererAsync(){
        exec.execute(() ->{
            try {
                File file = new File(bookLocalPath);
                doc = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly());
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                renderer = new PdfRenderer(pfd);
                totalPages = renderer.getPageCount();
                currentPage = currentBook.getLastPage();
                if (currentPage >= totalPages) currentPage = Math.max(0, totalPages - 1);
                Bitmap bmp = PdfPreviewHelper.renderOnePage(
                        renderer,
                        currentPage,
                        getResources().getDisplayMetrics(),
                        pdfImage.getWidth()
                );

                main.post(() -> {
                    pdfImage.setImageBitmap(bmp);
                    showLoading(false);
                    showPage(currentPage);
                });

            } catch (IOException e) {
                main.post(() -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to open PDF", Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void showLoading(boolean on) {
        if (loadingBar != null) loadingBar.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    private void showNextPage() {
        showPage(currentPage + 1);
        buffer.clear();
        highlightOverlay.clearHighlights();
    }

    private void showPrevPage() {
        showPage(currentPage - 1);
        buffer.clear();
        highlightOverlay.clearHighlights();
    }

    private void showPage(int page) {
        if (page < 0 || page >= totalPages) return;
        currentPage = page;
        showLoading(true);

        Bitmap bmp = PdfPreviewHelper.renderOnePage(
                renderer, currentPage,
                pdfImage.getResources().getDisplayMetrics(),
                pdfImage.getWidth());

        pdfImage.setImageBitmap(bmp);
        pageIndicator.setText((page + 1) + " / " + totalPages);

        currentBook.setLastPage(currentPage);
        currentBook.setSentence(0);
        BookStorage.updateBook(currentBook,this);

        showLoading(false);
        if (isSpeaking) speakPage();
    }

    private void toggleTTS() {
        if (!isSpeaking) {
            speakPage();
        } else {
            readerController.stopReading();
            isSpeaking = false;
            btnTTS.setText(getString(R.string.pref_play));
            highlightOverlay.clearHighlights();
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

                        readerController.setChunks(chunks);
                        readerController.startReadingFrom(0);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
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

    private void closeRenderer() {
        try { if (renderer != null) renderer.close(); } catch (Exception ignored) {}
        try { if (pfd != null) pfd.close(); } catch (Exception ignored) {}
        renderer = null;
        pfd = null;
    }

    @Override
    public void onChunkStart(int index, String text) {
        BitmapDrawable drawable = (BitmapDrawable) pdfImage.getDrawable();
        if (drawable == null) return;
        Bitmap bitmap = drawable.getBitmap();
        if (bitmap == null || renderer == null || doc == null) return;

        int bmpW = bitmap.getWidth();
        int bmpH = bitmap.getHeight();
        int pageW = renderer.openPage(currentPage).getWidth();
        int pageH = renderer.openPage(currentPage).getHeight();

        List<RectF> rects = PdfPreviewHelper.getRectsForSentence(
                doc, currentPage, text, bmpW, bmpH, pageW, pageH
        );

        for (RectF r : rects) {
            r.offset(0, -r.height());
        }
        highlightOverlay.setHighlights(rects);
    }

    @Override
    public void onChunkDone(int index, String text) {
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
    protected void onDestroy() {
        buffer.clear();
        readerController.shutdown();
        if (doc != null) {
            try { doc.close(); } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try { closeRenderer(); } catch (Exception ignored) {}
        if (exec != null) exec.shutdownNow();
        if (chapterLoader != null) chapterLoader.shutdown();
        super.onDestroy();
    }

}
