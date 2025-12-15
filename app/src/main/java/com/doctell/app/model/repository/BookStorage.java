package com.doctell.app.model.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import com.doctell.app.model.analytics.DocTellAnalytics;
import com.doctell.app.model.entity.Book;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;

public class BookStorage {

    public static List<Book> booksCache = new CopyOnWriteArrayList<>();

    // Single-threaded executor for serialized loading
    private static final Executor BOOK_LOADER_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BookLoaderThread");
        t.setPriority(Thread.NORM_PRIORITY - 1); // Slightly lower priority
        return t;
    });

    // Callback interface for async operations
    public interface BookLoadCallback {
        void onBooksLoaded(List<Book> books);
        void onLoadFailed(Exception e);
    }

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
            editor.putLong("lastOpened_" + i, b.getLastOpenedAt());
            editor.putString("thumb_" + i, b.getThumbnailPath());
            editor.putString("local_" + i, b.getLocalPath());
        }
        editor.apply();
    }

    public static void loadBooksAsync(Context ctx, BookLoadCallback callback) {
        BOOK_LOADER_EXECUTOR.execute(() -> {
            try {
                List<Book> books = loadBooksInternal(ctx);
                callback.onBooksLoaded(books);
            } catch (Exception e) {
                Log.e("BookStorage", "Failed to load books", e);
                callback.onLoadFailed(e);
            }
        });
    }

    private static List<Book> loadBooksInternal(Context ctx) {
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

                int lastPage = pref.getInt("lastPage_" + i, 0);
                int sentence = pref.getInt("sentence_" + i, 0);
                String thumbPath = pref.getString("thumb_" + i, null);
                String localPath = pref.getString("local_" + i, null);
                long lastOpened = pref.getLong("lastOpened_" + i, System.currentTimeMillis());
                Book b = new Book(
                        uri,
                        title,
                        lastPage,
                        sentence,
                        thumbPath,
                        localPath,
                        lastOpened
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
        DocTellAnalytics.loadLibrary(ctx, list);
        return list;
    }

    /**
     * DEPRECATED: Synchronous version (kept for compatibility, DO NOT CALL FROM MAIN THREAD)
     * Only use from background threads!
     *
     * @deprecated Use {@link #loadBooksAsync(Context, BookLoadCallback)} instead
     */
    @Deprecated
    public static List<Book> loadBooks(Context ctx) {
        return loadBooksInternal(ctx);
    }

    public static boolean updateBook(Book updated, Context ctx) {
        if (updated == null) {
            Log.w("BookStorage", "updateBook called with null Book");
            return false;
        }

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
                DocTellAnalytics.updatedBooks(ctx, updated);
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