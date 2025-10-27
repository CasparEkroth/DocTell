package com.doctell.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.doctell.app.model.PdfPreviewHelper;

import java.io.IOException;

public class ReaderActivity extends AppCompatActivity {

    private ImageView pdfImage;
    private ProgressBar loadingBar;
    private Button btnNext, btnPrev;
    private TextView pageIndicator;
    private PdfRenderer pdfRenderer;
    private PdfRenderer.Page currentPage;
    private ParcelFileDescriptor fileDescriptor;
    private int pageIndex = 0;
    private int pageCount = 1;
    private Uri uri;
    private TextToSpeech tts;
    private Button btnTTS;
    private String currentText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);

        pdfImage = findViewById(R.id.pdfImage);
        loadingBar = findViewById(R.id.loadingBar);
        btnNext = findViewById(R.id.btnNext);
        btnPrev = findViewById(R.id.btnPrev);
        pageIndicator = findViewById(R.id.pageIndicator);
        btnTTS = findViewById(R.id.btnTTS);

        String uriString = getIntent().getStringExtra("uri");
        if (uriString == null) finish();

        try {
            uri = Uri.parse(uriString);
            openRenderer(uri);
            showPage(pageIndex);
        } catch (IOException e) {
            e.printStackTrace();
            finish();
        }

        btnNext.setOnClickListener(v -> {
            if (pageIndex + 1 < pageCount) showPage(++pageIndex);
        });

        btnPrev.setOnClickListener(v -> {
            if (pageIndex > 0) showPage(--pageIndex);
        });

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(java.util.Locale.ENGLISH);
            }
        });

        btnTTS.setOnClickListener(v -> {
            currentText = PdfPreviewHelper.extractText(this,uri,pageIndex);
            if (!currentText.isEmpty()) {
                tts.speak(currentText, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });
    }

    private void openRenderer(Uri uri) throws IOException {
        fileDescriptor = getContentResolver().openFileDescriptor(uri, "r");
        pdfRenderer = new PdfRenderer(fileDescriptor);
        pageCount = pdfRenderer.getPageCount();
    }

    @SuppressLint("SetTextI18n")
    private void showPage(int index) {
        loadingBar.setVisibility(ProgressBar.VISIBLE);

        if (currentPage != null)
            currentPage.close();

        currentPage = pdfRenderer.openPage(index);

        Bitmap bitmap = Bitmap.createBitmap(
                currentPage.getWidth(),
                currentPage.getHeight(),
                Bitmap.Config.ARGB_8888);

        currentPage.render(bitmap, null, null,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

        pdfImage.setImageBitmap(bitmap);
        pageIndicator.setText((index + 1) + " / " + pageCount);
        loadingBar.setVisibility(ProgressBar.GONE);
    }

    @Override
    protected void onDestroy() {
        try {
            if (currentPage != null) currentPage.close();
            if (pdfRenderer != null) pdfRenderer.close();
            if (fileDescriptor != null) fileDescriptor.close();
        } catch (IOException ignored) {}
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
