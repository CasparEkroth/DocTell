package com.doctell.app.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BookStorage {
    private static final String PREFS_NAME = "MyAppPrefs";
    private static final String KEY_BOOK_LIST = "book_list";

    public static List<Book> booksCache = new ArrayList<>();
    public static void saveBooks(Context ctx, List<Book> list) {
        SharedPreferences pref = ctx.getSharedPreferences("books", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putInt("size", list.size());
        for (int i = 0; i < list.size(); i++) {
            Book b = list.get(i);
            editor.putString("uri_" + i, b.getUri().toString());
            editor.putString("title_" + i, b.getTitle());
        }
        editor.apply();
    }

    public static List<Book> loadBooks(Context ctx) {
        List<Book> list = new ArrayList<>();
        SharedPreferences pref = ctx.getSharedPreferences("books", Context.MODE_PRIVATE);
        int size = pref.getInt("size", 0);

        for (int i = 0; i < size; i++) {
            String uriString = pref.getString("uri_" + i, null);
            String title = pref.getString("title_" + i, null);
            if (uriString == null) continue;

            Uri uri = Uri.parse(uriString);
            try {
                // verify permission before adding
                Objects.requireNonNull(ctx.getContentResolver().openInputStream(uri)).close();
                list.add(new Book(uri, title, 1, 1,
                        PdfPreviewHelper.renderFirstPageFromUri(ctx, uri)));
            } catch (SecurityException | FileNotFoundException e) {
                Log.w("BookStorage", "Skipping invalid or revoked URI: " + uri);
            } catch (IOException e) {
                Log.w("BookStorage", "IO error: " + e.getMessage());
            }
        }

        booksCache.clear();
        booksCache.addAll(list);

        return list;
    }

    public static boolean updateBook(Book book, Context ctx){
        List<Book> list = loadBooks(ctx);
        for (Book b : list) {
            if (b.getUri().equals(book.getUri())){
                b.setBitmap(book.getBitmap());
                b.setLastPage(book.getLastPage());
                return true;
            }
        }
        return false;
    }

    public static Book findBookByUri(Context ctx, Uri uri) {
        List<Book> list = loadBooks(ctx);
        for (Book b : list) {
            if (b.getUri().equals(uri)) return b;
        }
        return null;
    }

}
