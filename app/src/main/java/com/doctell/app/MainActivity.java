package com.doctell.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
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

import com.doctell.app.model.entity.Prefs;
import com.doctell.app.model.analytics.DocTellAnalytics;
import com.doctell.app.model.analytics.DocTellCrashlytics;
import com.doctell.app.model.entity.Book;
import com.doctell.app.model.repository.BookStorage;
import com.doctell.app.model.pdf.PdfPreviewHelper;
import com.doctell.app.model.utils.BookSorter;
import com.doctell.app.model.utils.PermissionHelper;
import com.doctell.app.model.voice.LocalTtsEngine;
import com.doctell.app.model.voice.TtsEngineStrategy;
import com.doctell.app.view.ItemView;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_PDF_REQUEST = 1001;
    private GridLayout pdfGrid;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private ProgressBar loadingBar;
    private TtsEngineStrategy engine;
    private int selectedSortIndex = 0;
    private static final String READER_CHANNEL_ID = "reader_channel";

    private BookStorage.BookLoadCallback bookLoadCallback = new BookStorage.BookLoadCallback() {
        @Override
        public void onBooksLoaded(List<Book> books) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    BookSorter.sortBooksOnDefault(BookStorage.booksCache);
                    selectedSortIndex = BookSorter.getIndex();
                    refreshGrid();
                }
            });
        }

        @Override
        public void onLoadFailed(Exception e) {
            Log.e("BookStorage", "Load failed: " + e.getMessage());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        //init pdfbox
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(getApplicationContext());
        //load books
        //BookStorage.booksCache = BookStorage.loadBooks(this);
        BookStorage.loadBooksAsync(this,bookLoadCallback);

        pdfGrid = findViewById(R.id.pdfGrid);
        setupGridColumns();
        loadingBar = findViewById(R.id.loadingMain);


        BookSorter.getSavedSort(getApplicationContext());
        engine = LocalTtsEngine.getInstance(getApplicationContext());
        askForPermissions();
        setPremonitionsForAnalytics();

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
        startActivity(intent);
    }

    public void openLibrary(View v){
        showSortDialog();
    }
    private void showSortDialog() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_sort_library,null);

        RadioGroup rgSortOptions = dialogView.findViewById(R.id.rgSortOptions);
        View btnCancel = dialogView.findViewById(R.id.btnCancel);
        View btnOk = dialogView.findViewById(R.id.btnOk);

        // Pre-select current sort choice
        switch (selectedSortIndex) {
            case 0:
                rgSortOptions.check(R.id.rbTitleAZ);
                break;
            case 1:
                rgSortOptions.check(R.id.rbTitleZA);
                break;
            case 2:
                rgSortOptions.check(R.id.rbDateNewest);
                break;
            case 3:
                rgSortOptions.check(R.id.rbDateOldest);
                break;
            default:
                break;
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            );
        }
        // Button actions
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnOk.setOnClickListener(v -> {
            if (BookStorage.booksCache == null) {
                dialog.dismiss();
                return;
            }
            int checkedId = rgSortOptions.getCheckedRadioButtonId();
            int newIndex = selectedSortIndex;
            if (checkedId == R.id.rbTitleAZ) {
                newIndex = 0;
            } else if (checkedId == R.id.rbTitleZA) {
                newIndex = 1;
            } else if (checkedId == R.id.rbDateNewest) {
                newIndex = 2;
            } else if (checkedId == R.id.rbDateOldest) {
                newIndex = 3;
            }
            selectedSortIndex = newIndex;
            BookSorter.sortBooks(selectedSortIndex, BookStorage.booksCache);
            BookSorter.saveSortIndex(getApplicationContext());
            refreshGrid();
            dialog.dismiss();
        });
        dialog.show();
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
                        BookSorter.sortBooksOnDefault(BookStorage.booksCache);
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
    protected void onStart(){
        super.onStart();
        BookSorter.sortBooksOnDefault(BookStorage.booksCache);
        refreshGrid();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //BookStorage.loadBooks(this);
        BookStorage.loadBooksAsync(this,bookLoadCallback);
        BookSorter.sortBooksOnDefault(BookStorage.booksCache);
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
            ItemView item = new ItemView(this, null, b);

            GridLayout.LayoutParams gridParams = new GridLayout.LayoutParams();
            gridParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            gridParams.width = 0; // 0 width + weight = equal distribution
            int margin = (int) (8 * getResources().getDisplayMetrics().density);
            gridParams.setMargins(margin, margin, margin, margin);

            item.setLayoutParams(gridParams);

            pdfGrid.addView(item);
        }
    }


    private void setupGridColumns() {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        float screenWidthDp = displayMetrics.widthPixels / displayMetrics.density;

        float itemWidthDp = 180f;

        int columns = (int) (screenWidthDp / itemWidthDp);
        if (columns < 2) columns = 2; // Minimum 2

        pdfGrid.setColumnCount(columns);
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

    private void setPremonitionsForAnalytics(){
        SharedPreferences prefs = getSharedPreferences(Prefs.DOCTELL_PREFS.toString(), MODE_PRIVATE);
        DocTellAnalytics.setEnable(getApplicationContext(),
                prefs.getBoolean(Prefs.ANALYTICS_ENABLED.toString(), true));
        DocTellCrashlytics.setEnabled(prefs.getBoolean(Prefs.CRASHLYTICS_ENABLED.toString(),true));
    }

    @SuppressLint("CommitPrefEdits")
    private void askForPermissions(){
        SharedPreferences prefs = getSharedPreferences(Prefs.DOCTELL_PREFS.toString(), MODE_PRIVATE);
        boolean shown = prefs.getBoolean(Prefs.PERMISSIONS_ON_START.toString(),false);
        if(shown)return;
        PermissionHelper.ensureNotificationPermission(this, this);
        PermissionHelper.ensureBluetoothPermission(this, this);
        prefs.edit().putBoolean(Prefs.PERMISSIONS_ON_START.toString(), true).apply();
    }

    @Override
    protected void onDestroy() {
        BookSorter.saveSortIndex(getApplicationContext());
        try {
            engine.shutdown();
        } catch (Exception ignored) {}
        super.onDestroy();
    }

}