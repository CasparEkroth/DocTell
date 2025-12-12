package com.doctell.app.model.analytics;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.doctell.app.model.entity.Book;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class DocTellCrashlytics {

    private DocTellCrashlytics() {
        // no instances
    }

    private static FirebaseCrashlytics get() {
        return FirebaseCrashlytics.getInstance();
    }
    // Hash the URI
    private static String safeBookId(Book book) {
        if (book == null || book.getUri() == null) {
            return "unknown";
        }
        String raw = book.getUri().toString();
        return "book_" + DocTellAnalytics.sha256Short(raw);
    }

    //  Public API
    /**
     * Enable or disable Crashlytics collection (for settings / consent).
     */
    public static void setEnabled(boolean enabled) {
        get().setCrashlyticsCollectionEnabled(enabled);
    }

    /**
     * Attach current book + page context to future crashes.
     */
    public static void setCurrentBookContext(Book book, int pageIndex) {
        get().setCustomKey("book_id", safeBookId(book));
        get().setCustomKey("page_index", pageIndex);
    }

    /**
     * Clear book context (e.g. when leaving reader).
     */
    public static void clearBookContext() {
        get().setCustomKey("book_id", "none");
        get().setCustomKey("page_index", -1);
    }

    /**
     * Generic non-fatal error with type tag.
     * Example type: "pdf_render", "pdf_load", "tts_error"
     */
    public static void logNonFatal(String type, String message, Throwable t) {
        get().setCustomKey("error_type", type);
        if (message != null && !message.isEmpty()) {
            get().log(message);
        }
        if (t != null) {
            get().recordException(t);
        }
    }

    /**
     * Convenience for PDF-related errors.
     */
    public static void logPdfError(Book book, int pageIndex, String stage, Throwable t) {
        get().setCustomKey("error_type", "pdf_error");
        get().setCustomKey("pdf_stage", stage); // e.g. "open", "render", "extract_text"
        get().setCustomKey("book_id", safeBookId(book));
        get().setCustomKey("page_index", pageIndex);

        get().recordException(t);
    }

    /**
     * Convenience for TTS-related errors.
     */
    public static void logTtsError(Book book, int pageIndex, String stage, Throwable t) {
        get().setCustomKey("error_type", "tts_error");
        get().setCustomKey("tts_stage", stage); // e.g. "init", "speak_chunk"
        get().setCustomKey("book_id", safeBookId(book));
        get().setCustomKey("page_index", pageIndex);

        get().recordException(t);
    }

    /**
     * Generic exception logging for Bluetooth/media control errors.
     * Use this for non-fatal exceptions that don't fit other categories.
     */
    public static void logException(Exception e) {
        logNonFatal("mediacontrol", e);
    }

    /**
     * Helper for logging non-fatal exceptions with an error type.
     */
    private static void logNonFatal(String errorType, Exception e) {
        if (e == null) {
            return;
        }

        get().setCustomKey("errorType", errorType);

        String message = e.getMessage();
        if (message != null && !message.isEmpty()) {
            get().log(message);
        }

        get().recordException(e);
    }

}

