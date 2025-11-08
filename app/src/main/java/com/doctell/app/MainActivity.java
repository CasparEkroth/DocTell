package com.doctell.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.GridLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.doctell.app.model.Book;
import com.doctell.app.model.BookStorage;
import com.doctell.app.model.PdfPreviewHelper;
import com.doctell.app.view.ItemView;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_PDF_REQUEST = 1001;
    private GridLayout pdfGrid;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        //init pdfbox
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(getApplicationContext());
        //load books
        BookStorage.booksCache = BookStorage.loadBooks(this);
        pdfGrid = findViewById(R.id.pdfGrid);

        refreshGrid();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {finish();}
        });
    }

    public void addBook(View v){
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);

        startActivityForResult(intent, PICK_PDF_REQUEST);
    }

    public void openSettings(View v){
        Intent intent = new Intent(this, SettingsActivity.class);
        // set values??
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_PDF_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;

            // persist permission
            final int takeFlags = data.getFlags() &
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
            } catch (SecurityException e) {
                Log.w("PDF", "Could not persist URI permission: " + e.getMessage());
            }
            
            exec.execute(() -> {
                try {
                    String title = safeTitleFromPdfOrName(uri);
                    String localPath = PdfPreviewHelper.ensureLocalCopy(this,uri);
                    int screenW = getResources().getDisplayMetrics().widthPixels;
                    int thumbW = Math.min(screenW / 2, 480);

                    String thumbPath = PdfPreviewHelper.ensureThumb(this, uri, thumbW);

                    Book b = new Book(uri, title, 0, 0, thumbPath,localPath);
                    if (BookStorage.findBookByUri(this, uri) == null) {
                        BookStorage.booksCache.add(b);
                        BookStorage.updateBook(b, this);
                    }
                    main.post(this::refreshGrid);

                } catch (Exception e) {
                    Log.e("PDF", "Import failed", e);
                    // show a toast on main
                    main.post(() -> {
                        // findViewById(R.id.importOverlay).setVisibility(View.GONE);
                        Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            });
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        BookStorage.loadBooks(this);
        refreshGrid();
    }

    private String safeTitleFromPdfOrName(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri);
             PDDocument doc = PDDocument.load(in, MemoryUsageSetting.setupTempFileOnly())) {

            PDDocumentInformation info = doc.getDocumentInformation();
            if (info != null) {
                String t = info.getTitle();
                if (t != null && !t.trim().isEmpty()) return t.trim();
            }
        } catch (IOException e) {
            Log.w("PDF", "Could not read title from PDF metadata: " + e.getMessage());
        }

        return "Untitled";
    }

    private void refreshGrid(){
        Log.d("GRID", "Refreshing grid with " + BookStorage.booksCache.size() + " books");
        pdfGrid.removeAllViews();
        for(Book b : BookStorage.booksCache){
            Log.d("GRID", "Adding book: " + b.getTitle() + " | URI=" + b.getUri());
            ItemView item = new ItemView(this, null, b);
            pdfGrid.addView(item);
        }
    }

    @Override
    protected void onDestroy() {
        try {
        } catch (Exception ignored) {}
        super.onDestroy();
    }

}