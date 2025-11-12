package com.doctell.app;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import android.speech.tts.UtteranceProgressListener;
import android.widget.Toast;

import com.doctell.app.model.Book;
import com.doctell.app.model.BookStorage;
import com.doctell.app.model.PdfPreviewHelper;
import com.doctell.app.model.TTSModel;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReaderActivity extends AppCompatActivity {

    private ImageView pdfImage;
    private ProgressBar loadingBar;
    private Button btnNext, btnPrev, btnTTS;
    private TextView pageIndicator;

    private ExecutorService exec;
    private Handler main;
    private ParcelFileDescriptor pfd;
    private int currentPage = 0;
    private int totalPages;
    private Book currentBook;
    private PdfRenderer renderer;
    private String bookLocalPath;
    private com.doctell.app.model.TTSModel ttsM;
    private boolean isSpeaking = false;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);

        pdfImage = findViewById(R.id.pdfImage);
        loadingBar = findViewById(R.id.loadingBar);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        btnTTS = findViewById(R.id.btnTTS);
        pageIndicator = findViewById(R.id.pageIndicator);
        loadingBar   = findViewById(R.id.loadingBar);


        exec = Executors.newSingleThreadExecutor();
        main = new Handler(Looper.getMainLooper());

        // Load Book from MainActivity
        String uriString = getIntent().getStringExtra("uri");
        Uri uri = Uri.parse(uriString);
        currentBook = BookStorage.findBookByUri(this, uri);
        assert currentBook != null;
        bookLocalPath = currentBook.getLocalPath();
        try {
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri,"r");
            assert pfd != null;
            renderer = new PdfRenderer(pfd);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ttsM = TTSModel.get(getApplicationContext());
        ttsM.setExternalListener(new UtteranceProgressListener(){
            @Override
            public void onDone(String utteranceId) {}
            @Override
            public void onError(String utteranceId) {
                runOnUiThread(()-> {if(isSpeaking)showNextPage();});
            }
            @Override
            public void onStart(String utteranceId) {}
        });

        btnNext.setOnClickListener(v -> showNextPage());
        btnPrev.setOnClickListener(v -> showPrevPage());

        btnTTS.setOnClickListener(v -> toggleTTS());

        showLoading(true);
        openRendererAsync();

    }

    private void openRendererAsync(){
        exec.execute(() ->{
            try {
                File file = new File(bookLocalPath);
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

    private void showLoading(boolean on) {// fix xml
        if (loadingBar != null) loadingBar.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    private void showNextPage() {
        showPage(currentPage + 1);
    }

    private void showPrevPage() {
        showPage(currentPage - 1);
    }

    private void showPage(int page) {
        if (page < 0 || page >= totalPages) return;
        currentPage = page;

        loadingBar.setVisibility(ProgressBar.VISIBLE);

        Bitmap bmp = PdfPreviewHelper.renderOnePage(
                renderer,
                currentPage,
                pdfImage.getResources().getDisplayMetrics(),
                pdfImage.getWidth());

        pdfImage.setImageBitmap(bmp);

        pageIndicator.setText((page + 1) + " / " + totalPages);

        currentBook.setLastPage(currentPage);
        currentBook.setSentence(0);
        BookStorage.updateBook(currentBook,this);


        loadingBar.setVisibility(ProgressBar.GONE);

        if (isSpeaking) speakPage();
    }

    private void toggleTTS() {
        if (!isSpeaking) {
            speakPage();
        } else {
            ttsM.stop();
            isSpeaking = false;
            btnTTS.setText(getString(R.string.pref_play));
        }
    }

    private void speakPage() {
        showLoading(true);
        exec.execute(() -> {
            try(PDDocument doc = PDDocument.load(new File(bookLocalPath), MemoryUsageSetting.setupTempFileOnly())) {

                String text = PdfPreviewHelper.extractOnePageText(doc,currentPage);
                main.post(() -> {
                    showLoading(false);
                    if (text == null || text.trim().isEmpty()) {
                        Toast.makeText(this, "No searchable text on this page (scan?).", Toast.LENGTH_SHORT).show();
                    } else {
                        isSpeaking = true;
                        btnTTS.setText(getString(R.string.pref_pause));
                        String id = ttsM.speak(text, true);
                        if (id == null) {
                            Toast.makeText(this, "TTS engine not ready", Toast.LENGTH_SHORT).show();
                            isSpeaking = false;
                            btnTTS.setText(getString(R.string.pref_play));
                        } else {
                            Toast.makeText(this, "Text extracted (" + Math.min(text.length(), 60) + " chars…)", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            } catch (Exception e) { e.printStackTrace(); }
        });

    }


    private void closeRenderer() {
        try { if (renderer != null) renderer.close(); } catch (Exception ignored) {}
        try { if (pfd != null) pfd.close(); } catch (Exception ignored) {}
        renderer = null;
        pfd = null;
    }

    @Override
    protected void onDestroy() {
        if (ttsM != null) {
            ttsM.stop();
        }
        try {
           closeRenderer();
        } catch (Exception ignored) {}
        if (exec != null) exec.shutdownNow();
        super.onDestroy();
    }

}
