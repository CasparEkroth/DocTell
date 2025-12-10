package com.doctell.app.model.analytics;

import android.content.Context;
import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.doctell.app.model.entity.Book;

public class DocTellAnalytics {

    private DocTellAnalytics(){}
    private static FirebaseAnalytics get(Context ctx) {
        return FirebaseAnalytics.getInstance(ctx.getApplicationContext());
    }

    // Helper to get a stable id for a book
    private static String bookId(Book book) {
        if (book == null || book.getUri() == null) {
            return "unknown";
        }
        String raw = book.getUri().toString();
        return "book_" + sha256Short(raw);
    }

    static String sha256Short(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // first 8 bytes into hex enough for analytics id
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < hash.length; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            // fallback
            return "hash_error";
        }
    }

    // --- Events ---
    /**
     * Enable or disable Analytics collection (for settings / consent).
     */
    public static void setEnable(Context ctx,boolean enable){
        get(ctx).setAnalyticsCollectionEnabled(enable);
    }

    public static void bookOpened(Context ctx, Book book) {
        Bundle b = new Bundle();
        b.putString("book_id", bookId(book));
        get(ctx).logEvent("book_opened", b);
    }

    public static void readingStarted(Context ctx, Book book, int pageIndex) {
        Bundle b = new Bundle();
        b.putString("book_id", bookId(book));
        b.putInt("page_index", pageIndex);
        get(ctx).logEvent("reading_started", b);
    }

    public static void readingPaused(Context ctx, Book book, int pageIndex) {
        Bundle b = new Bundle();
        b.putString("book_id", bookId(book));
        b.putInt("page_index", pageIndex);
        get(ctx).logEvent("reading_paused", b);
    }

    public static void readingResumed(Context ctx, Book book, int pageIndex) {
        Bundle b = new Bundle();
        b.putString("book_id", bookId(book));
        b.putInt("page_index", pageIndex);
        get(ctx).logEvent("reading_resumed", b);
    }

    public static void pageChanged(Context ctx, Book book, int fromPage, int toPage) {
        Bundle b = new Bundle();
        b.putString("book_id", bookId(book));
        b.putInt("from_page", fromPage);
        b.putInt("to_page", toPage);
        get(ctx).logEvent("page_changed", b);
    }

    public static void autoPageChanged(Context ctx, Book book, int toPage){
        Bundle b = new Bundle();
        b.putString("book_id", bookId(book));
        b.putInt("to_page", toPage);
        get(ctx).logEvent("auto_page_changed", b);
    }
}
