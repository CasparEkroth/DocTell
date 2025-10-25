package com.doctell.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.GridLayout;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IntDef;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.doctell.app.model.Book;
import com.doctell.app.model.BookStorage;
import com.doctell.app.model.PdfPreviewHelper;
import com.doctell.app.view.ItemView;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_PDF_REQUEST = 1001;
    private List<Book> list;
    private GridLayout pdfGrid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        //init pdfbox
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(getApplicationContext());
        //load books
        list = BookStorage.loadBooks(this);
        pdfGrid = findViewById(R.id.pdfGrid);

        refreshGrid();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                BookStorage.saveBooks(MainActivity.this, list);
                Log.d("SAVE", "Books saved on back press");
                finish();
            }
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

            try {
                Book b = getPdf(uri);
                list.add(b);
                BookStorage.saveBooks(this, list);
                refreshGrid();
            } catch (Exception e) {
                Log.e("PDF", "Failed to load selected PDF: " + e.getMessage());
            }
        }
    }



    @Override
    protected void onPause() {
        super.onPause();
        BookStorage.saveBooks(this, list);
    }
    private Book getPdf(Uri uri){
        try(InputStream in = getContentResolver().openInputStream(uri)) {
            PDDocument document = PDDocument.load(in);
            PDDocumentInformation info = document.getDocumentInformation();
            String s = info.getTitle();
            document.close();

            return new Book(uri,s,1,1,
                    PdfPreviewHelper.renderFirstPageFromUri(this,uri));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void refreshGrid(){
        Log.d("GRID", "Refreshing grid with " + list.size() + " books");
        pdfGrid.removeAllViews();
        for(Book b : list){
            Log.d("GRID", "Adding book: " + b.getTitle() + " | URI=" + b.getUri());
            ItemView item = new ItemView(this, null, b);
            pdfGrid.addView(item);
        }
    }

}