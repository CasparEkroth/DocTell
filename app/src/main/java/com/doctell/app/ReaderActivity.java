package com.doctell.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import android.speech.tts.UtteranceProgressListener;

import com.doctell.app.model.Book;
import com.doctell.app.model.BookStorage;
import com.doctell.app.model.PdfPreviewHelper;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.InputStream;
import java.util.Locale;

public class ReaderActivity extends AppCompatActivity {

    private ImageView pdfImage;
    private ProgressBar loadingBar;
    private Button btnNext, btnPrev, btnTTS;
    private TextView pageIndicator;

    private PDDocument pdf;
    private int currentPage = 0;
    private int totalPages;
    private TextToSpeech tts;
    private boolean isSpeaking = false;
    private Book currentBook;
    private GestureDetector gestureDetector;

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

        // Load Book from MainActivity
        String uriString = getIntent().getStringExtra("uri");
        Uri uri = Uri.parse(uriString);
        currentBook = BookStorage.findBookByUri(this, uri);

        loadPdf(uri);

        btnNext.setOnClickListener(v -> showNextPage());
        btnPrev.setOnClickListener(v -> showPrevPage());

        btnTTS.setOnClickListener(v -> toggleTTS());

        gestureDetector = new GestureDetector(this, new SwipeGestureListener());
        pdfImage.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
    }

    private void loadPdf(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            pdf = PDDocument.load(is);
            totalPages = pdf.getNumberOfPages();

            currentPage = currentBook.getLastPage();
            showPage(currentPage);

            setupTTS();
        } catch (Exception e) {
            Log.e("PDF", "Failed to open", e);
        }
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

        Bitmap bmp = PdfPreviewHelper.renderPage(currentPage, pdf);
        pdfImage.setImageBitmap(bmp);

        pageIndicator.setText((page + 1) + " / " + totalPages);
        currentBook.setLastPage(currentPage);
        BookStorage.updateBook(currentBook,this);
        BookStorage.saveBooks(this, BookStorage.booksCache);

        loadingBar.setVisibility(ProgressBar.GONE);

        if (isSpeaking) speakPage();
    }

    private void setupTTS() {
        tts = new TextToSpeech(this, status -> {
            if(status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
            }
        });
    }

    private void toggleTTS() {
        if (!isSpeaking) {
            speakPage();
        } else {
            tts.stop();
            isSpeaking = false;
            btnTTS.setText("Play");
        }
    }

    private void speakPage() {
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(currentPage + 1);
            stripper.setEndPage(currentPage + 1);

            String text = stripper.getText(pdf);
            isSpeaking = true;
            btnTTS.setText("Pause");

            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TTS_PAGE");

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    // TTS started
                }

                @Override
                public void onDone(String utteranceId) {
                    runOnUiThread(() -> {
                        // Auto-next page when finished
                        if (isSpeaking) {
                            showNextPage();
                        }
                    });
                }

                @Override
                public void onError(String utteranceId) {
                    Log.e("TTS", "Error speaking!");
                }
            });


        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        try { pdf.close(); } catch (Exception ignored) {}
        super.onDestroy();
    }

    private class SwipeGestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 100;
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
            float diffX = e2.getX() - e1.getX();
            if (Math.abs(diffX) > SWIPE_THRESHOLD) {
                if (diffX < 0) showNextPage();
                else showPrevPage();
                return true;
            }
            return false;
        }
    }
}
