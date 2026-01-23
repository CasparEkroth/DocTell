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
    private String loadingPath = null;
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

    public void loadIfNeeded(final String path, final Listener listener) {
        synchronized (this) {
            if (currentSession != null && path.equals(currentSession.path)) {
                mainHandler.post(() -> listener.onLoaded(currentSession));
                return;
            }
            if (loading && path.equals(loadingPath)) {
                waitingListeners.add(listener);
                return;
            }
            waitingListeners.clear();

            if (currentSession != null && !path.equals(currentSession.path)) {
                closeCurrentSessionLocked();
            }
            waitingListeners.add(listener);

            loading = true;
            loadingPath = path;

            executor.execute(() -> {
                final String targetPath = path;

                Log.d(TAG, "Loading PDF on background thread: " + targetPath);
                PdfSession newSession = null;
                Throwable error = null;

                try {
                    File file = new File(targetPath);
                    ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                    PdfRenderer renderer = new PdfRenderer(pfd);
                    PDDocument doc = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly());
                    int pageCount = renderer.getPageCount();
                    newSession = new PdfSession(targetPath, doc, pfd, renderer, pageCount);
                } catch (Throwable t) {
                    error = t;
                }

                final PdfSession finalSession = newSession;
                final Throwable finalError = error;

                mainHandler.post(() -> {
                    synchronized (PdfLoader.this) {
                        if (!targetPath.equals(loadingPath)) {
                            Log.d(TAG, "Ignoring result for " + targetPath + " as loader is now interested in " + loadingPath);
                            if (finalSession != null) {
                                try { finalSession.renderer.close(); } catch (Exception e) {}
                                try { finalSession.pfd.close(); } catch (Exception e) {}
                                try { finalSession.doc.close(); } catch (Exception e) {}
                                //System.gc();
                            }
                            return;
                        }
                        loading = false;
                        loadingPath = null;

                        if (finalError == null && finalSession != null) {
                            currentSession = finalSession;
                            for (Listener l : waitingListeners) {
                                l.onLoaded(finalSession);
                            }
                        } else {
                            for (Listener l : waitingListeners) {
                                l.onError(finalError);
                            }
                        }
                        waitingListeners.clear();
                    }
                });
            });
        }
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
        } catch (Exception ignored) {}

        try {
            if (currentSession.pfd != null) {
                currentSession.pfd.close();
            }
        } catch (Exception ignored) {}

        try {
            if (currentSession.doc != null) {
                currentSession.doc.close();
            }
        } catch (Exception ignored) {}

        currentSession = null;
    }

}
