package com.doctell.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.doctell.app.model.Book;
import com.doctell.app.model.BookStorage;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private List<Book> list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        //init pdfbox
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(getApplicationContext());
        //load books
        list = BookStorage.loadBooks(this);

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
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent,1001);
    }

    public void openSettings(View v){

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();

            // Persist permission so we can read it later
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

            list.add(getPdf(uri));
            /*
            Intent readerI = new Intent(this, ReaderActivity.class);
            readerI.putExtra("uri",uri);
            startActivity(readerI);
             */
        }
    }

    private Book getPdf(Uri uri){
        try(InputStream in = getContentResolver().openInputStream(uri)) {
            PDDocument document = PDDocument.load(in);
            PDDocumentInformation info = document.getDocumentInformation();
            String s = info.getTitle();
            document.close();

            return new Book(uri,s,1,1);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}