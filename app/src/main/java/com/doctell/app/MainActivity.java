package com.doctell.app;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.doctell.app.model.entity.Book;
import com.doctell.app.model.repository.BookStorage;
import com.doctell.app.model.pdf.PdfPreviewHelper;
import com.doctell.app.model.voice.LocalTtsEngine;
import com.doctell.app.model.voice.TtsEngineStrategy;
import com.doctell.app.view.ItemView;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation;

import java.io.IOException;
import java.io.InputStream;
import java.text.Collator;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_PDF_REQUEST = 1001;
    private GridLayout pdfGrid;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private ProgressBar loadingBar;
    private TtsEngineStrategy engine;
    private static final String READER_CHANNEL_ID = "reader_channel";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //EdgeToEdge.enable(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        //init pdfbox
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(getApplicationContext());
        //load books
        BookStorage.booksCache = BookStorage.loadBooks(this);
        pdfGrid = findViewById(R.id.pdfGrid);
        loadingBar = findViewById(R.id.loadingMain);
        refreshGrid();

        engine = LocalTtsEngine.getInstance(getApplicationContext());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {finish();}
        });

        View root = findViewById(R.id.main);
        View nav = findViewById(R.id.bottomNav);

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
            ViewGroup.MarginLayoutParams navLp =
                    (ViewGroup.MarginLayoutParams) nav.getLayoutParams();
            navLp.bottomMargin = bottom;
            nav.setLayoutParams(navLp);

            return insets;
        });

        createReaderNotificationChannel();
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

    public void openLibrary(View v){
        showSortDialog();
    }

    private void showSortDialog() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_sort_library, null);

        RadioGroup rgSort = dialogView.findViewById(R.id.rgSortOptions);

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("OK", (dialog, which) -> {
                    int checkedId = rgSort.getCheckedRadioButtonId();
                    if (checkedId == R.id.rbTitleAZ) {
                        sortBooksByTitleAsc();
                    } else if (checkedId == R.id.rbTitleZA) {
                        sortBooksByTitleDesc();
                    } else if (checkedId == R.id.rbDateNewest) {
                        sortBooksByDateNewest();
                    } else if (checkedId == R.id.rbDateOldest) {
                        sortBooksByDateOldest();
                    }
                    // After sorting: rebuild your GridLayout in memory, no extra PDF I/O
                    rebuildGrid();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sortBooksByTitleAsc() {
        if (BookStorage.booksCache == null || BookStorage.booksCache.isEmpty()) {
            return;
        }

        // Swedish-friendly A–Ö sort
        Collator collator = Collator.getInstance(new Locale("sv", "SE"));
        collator.setStrength(Collator.PRIMARY); // ignore case / accents mostly

        Collections.sort(BookStorage.booksCache,
                (b1, b2) -> {
                    String t1 = b1.getTitle() != null ? b1.getTitle() : "";
                    String t2 = b2.getTitle() != null ? b2.getTitle() : "";
                    return collator.compare(t1, t2);
                });

        // Redraw the grid with the new order
        refreshGrid();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_PDF_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            showLoading(true);
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
                    main.post(() ->{
                        this.refreshGrid();
                        showLoading(false);
                    });

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

    private void showLoading(boolean on) {
        if (loadingBar != null) loadingBar.setVisibility(on ? View.VISIBLE : View.GONE);
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

    private void createReaderNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    READER_CHANNEL_ID,
                    "DocTell reading",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Playback controls when DocTell is reading");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    protected void onDestroy() {
        try {
            engine.shutdown();
        } catch (Exception ignored) {}
        super.onDestroy();
    }

}