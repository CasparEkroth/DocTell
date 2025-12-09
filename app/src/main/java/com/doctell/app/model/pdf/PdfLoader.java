package com.doctell.app.model.pdf;

import android.content.Context;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfLoader {
    private static final String TAG = "PdfLoader";

    public static final class PdfSession {
        public final String path;
        public final PDDocument doc;
        public final ParcelFileDescriptor pfd;
        public final PdfRenderer renderer;
        public final int pageCount;

        PdfSession(String path,
                   PDDocument doc,
                   ParcelFileDescriptor pfd,
                   PdfRenderer renderer,
                   int pageCount) {
            this.path = path;
            this.doc = doc;
            this.pfd = pfd;
            this.renderer = renderer;
            this.pageCount = pageCount;
        }
    }

    public interface Listener {
        void onLoaded(PdfSession session);
        void onError(Throwable error);
    }

    private static PdfLoader instance;

    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private PdfSession currentSession;
    private boolean loading;
    private final List<Listener> waitingListeners = new ArrayList<>();

    private PdfLoader(Context ctx) {
        this.appContext = ctx.getApplicationContext();
    }

    public static PdfLoader getInstance(Context ctx) {
        if (instance == null) {
            synchronized (PdfLoader.class) {
                if (instance == null) {
                    instance = new PdfLoader(ctx);
                }
            }
        }
        return instance;
    }

    /** True if we already have a loaded session for this exact path. */
    public synchronized boolean isReady(String path) {
        return currentSession != null && path.equals(currentSession.path);
    }

    /** Returns current session if ready, otherwise null. **/
    public synchronized PdfSession getCurrentSession() {
        return currentSession;
    }

    public void loadIfNeeded(String path, Listener listener) {
        synchronized (this) {
            // Same book already loaded
            if (currentSession != null && path.equals(currentSession.path)) {
                PdfSession session = currentSession;
                mainHandler.post(() -> listener.onLoaded(session));
                return;
            }

            // New book, close old one if any
            if (currentSession != null && !path.equals(currentSession.path)) {
                closeCurrentSessionLocked();
            }

            waitingListeners.add(listener);

            if (loading) {
                // Already loading this path; just wait.
                return;
            }

            loading = true;
        }

        executor.execute(() -> {
            Log.d(TAG, "Loading PDF on background thread: " + path);
            PdfSession newSession = null;
            Throwable error = null;

            try {
                File file = new File(path);
                ParcelFileDescriptor pfd = ParcelFileDescriptor.open(
                        file, ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer renderer = new PdfRenderer(pfd);
                PDDocument doc = PDDocument.load(
                        file, MemoryUsageSetting.setupTempFileOnly());
                int pageCount = renderer.getPageCount();

                newSession = new PdfSession(path, doc, pfd, renderer, pageCount);

            } catch (OutOfMemoryError oom) {
                Log.e(TAG, "Out of memory while loading PDF", oom);
                error = oom;
            } catch (IOException io) {
                Log.e(TAG, "IO error while loading PDF", io);
                error = io;
            } catch (Throwable t) {
                Log.e(TAG, "Unexpected error while loading PDF", t);
                error = t;
            }

            List<Listener> toNotify;
            synchronized (PdfLoader.this) {
                loading = false;
                toNotify = new ArrayList<>(waitingListeners);
                waitingListeners.clear();

                if (error == null && newSession != null) {
                    currentSession = newSession;
                }
            }

            if (error == null && newSession != null) {
                for (Listener l : toNotify) {
                    PdfSession finalSession = newSession;
                    mainHandler.post(() -> l.onLoaded(finalSession));
                }
            } else {
                for (Listener l : toNotify) {
                    Throwable finalError = error;
                    mainHandler.post(() -> l.onError(finalError));
                }
            }
        });
    }

    /** Close and clear current session (e.g. when user closes the book). **/
    public synchronized void closeCurrent() {
        closeCurrentSessionLocked();
    }

    private void closeCurrentSessionLocked() {
        if (currentSession == null) return;

        try {
            if (currentSession.renderer != null) {
                currentSession.renderer.close();
            }
        } catch (Exception ignore) {}

        try {
            if (currentSession.pfd != null) {
                currentSession.pfd.close();
            }
        } catch (Exception ignore) {}

        try {
            if (currentSession.doc != null) {
                currentSession.doc.close();
            }
        } catch (Exception ignore) {}

        currentSession = null;
    }

}
