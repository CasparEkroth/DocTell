package com.doctell.app.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import com.google.gson.Gson;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BookStorage {
    private static final String PREFS_NAME = "MyAppPrefs";
    private static final String KEY_BOOK_LIST = "book_list";

    public static List<Book> booksCache = new ArrayList<>();
    private static void saveBooks(Context ctx, List<Book> list) {
        SharedPreferences pref = ctx.getSharedPreferences("books", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();

        editor.putInt("size", list.size());
        for (int i = 0; i < list.size(); i++) {
            Book b = list.get(i);
            editor.putString("uri_" + i, b.getUri().toString());
            editor.putString("title_" + i, b.getTitle());
            editor.putInt("lastPage_" + i, b.getLastPage());
            editor.putInt("sentence_" + i, b.getSentence());

            editor.putString("thumb_" + i, b.getThumbnailPath());
            editor.putString("local_" + i, b.getLocalPath());
        }
        editor.apply();
    }


    public static List<Book> loadBooks(Context ctx) {
        SharedPreferences pref = ctx.getSharedPreferences("books", Context.MODE_PRIVATE);
        int size = pref.getInt("size", 0);

        List<Book> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String uriString = pref.getString("uri_" + i, null);
            String title = pref.getString("title_" + i, null);
            if (uriString == null) continue;

            Uri uri = Uri.parse(uriString);

            try {
                Objects.requireNonNull(ctx.getContentResolver().openInputStream(uri)).close();

                int lastPage = pref.getInt("lastPage_" + i, 0);// default 0
                int sentence = pref.getInt("sentence_" + i, 0);
                String thumbPath = pref.getString("thumb_" + i, null);
                String localPath = pref.getString("local_" + i, null);
                Book b = new Book(
                        uri,
                        title,
                        lastPage,
                        sentence,
                        thumbPath,
                        localPath
                );
                list.add(b);
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


    public static boolean updateBook(Book updated, Context ctx) {
        for (int i = 0; i < booksCache.size(); i++) {
            Book b = booksCache.get(i);
            if (b.getUri().equals(updated.getUri())) {
                b.setLastPage(updated.getLastPage());
                b.setSentence(updated.getSentence());
                if (updated.getThumbnailPath() != null) {
                    b.setThumbnailPath(updated.getThumbnailPath());
                }
                if (updated.getLocalPath() != null && !updated.getLocalPath().isEmpty()) {
                    b.setLocalPath(updated.getLocalPath());
                }

                saveBooks(ctx, booksCache);
                return true;
            }
        }
        return false;
    }

    public static boolean delete(Context ctx, Book target) {
        for (int i = 0; i < booksCache.size(); i++) {
            Book b = booksCache.get(i);
            if (b.equals(target)) {
                booksCache.remove(i);
                saveBooks(ctx, booksCache);
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
