package com.doctell.app.model.voice.media;

import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.doctell.app.model.analytics.DocTellCrashlytics;
import com.doctell.app.model.entity.Book;
import com.doctell.app.model.entity.StepLength;
import com.doctell.app.model.pdf.PdfManager;
import com.doctell.app.model.repository.StepPrefs;
import com.doctell.app.model.voice.ReaderController;

import java.io.IOException;
import java.util.List;

public final class StepNavigator {

    private static final String TAG = "StepNavigator";

    private StepNavigator() {}

    static void handleNextSentenceOrPage(
            ReaderService service,
            Book currentBook,
            PdfManager pdfManager,
            ReaderController readerController,
            ReaderController.MediaNav uiMediaNav,
            Handler mainHandler
    ) {
        if (currentBook == null || pdfManager == null) return;

        Log.d(TAG, "entered handleNextSentenceOrPage");

        int currentPage = currentBook.getLastPage();
        int currentSentence = currentBook.getSentence();

        // Prefer already-loaded chunks from ReaderController
        List<String> chunks = readerController != null ? readerController.getChunks() : null;

        if (chunks == null || chunks.isEmpty()) {
            // Fallback: load from PDF once for this page
            try {
                chunks = service.loadCurrentPageSentences();
                if (readerController != null && chunks != null && !chunks.isEmpty()) {
                    // Keep controller in sync with freshly loaded chunks
                    readerController.setChunks(chunks, currentSentence);
                }
            } catch (IOException e) {
                DocTellCrashlytics.logPdfError(currentBook, currentPage, "get_sentences", e);
                Log.e(TAG, "next(): loadCurrentPageSentences failed", e);
                return;
            }
        }

        StepLength stepLength = StepPrefs.getStepLength(service);
        boolean stepByPage =
                (stepLength == StepLength.PAGE) ||
                        chunks == null || chunks.isEmpty() ||
                        currentSentence >= chunks.size() - 1;

        if (stepByPage) {
            Log.d(TAG, "handleNextSentenceOrPage by PAGE");

            try {
                int pageCount = pdfManager.getPageCount();
                if (currentPage + 1 >= pageCount) return;

                final int newPage = currentPage + 1;

                currentBook.setLastPage(newPage);
                currentBook.setSentence(0);
                service.onReadingPositionChanged();

                service.executor.execute(() -> {
                    try {
                        List<String> newChunks = service.loadCurrentPageSentences();
                        mainHandler.post(() -> {
                            if (readerController != null && newChunks != null && !newChunks.isEmpty()) {
                                readerController.setChunks(newChunks, 0);
                                readerController.startReading();
                            } else if (readerController != null) {
                                Toast.makeText(service, "illegible text", Toast.LENGTH_SHORT).show();
                                service.next();
                            }

                            if (uiMediaNav != null) uiMediaNav.navForward();
                        });
                    } catch (IOException e) {
                        Log.e(TAG, "next(): failed to load page text", e);
                        DocTellCrashlytics.logPdfError(currentBook, newPage, "render_page", e);
                    }
                });
            } catch (IOException e) {
                Log.e(TAG, "next(): getPageCount failed", e);
                DocTellCrashlytics.logPdfError(currentBook, currentBook.getLastPage(), "render_page", e);
            }
        } else {
            Log.d(TAG, "handleNextSentenceOrPage by SENTENCE");

            int newSentence = currentSentence + 1;
            if (newSentence >= chunks.size()) {
                // Safety: if we somehow went past last sentence, do nothing
                return;
            }
            currentBook.setSentence(newSentence);
            service.onReadingPositionChanged();

            final int finalSentence = newSentence;
            final List<String> finalChunks = chunks;

            service.executor.execute(() ->
                    mainHandler.post(() -> {
                        if (readerController != null && finalChunks != null && !finalChunks.isEmpty()) {
                            readerController.setChunks(finalChunks, finalSentence);
                            readerController.startReading();
                        } else if (readerController != null) {
                            Toast.makeText(service, "illegible text", Toast.LENGTH_SHORT).show();
                            service.next();
                        }
                        if (uiMediaNav != null) uiMediaNav.navForward();
                    })
            );
        }
    }

    static void handlePrevSentenceOrPage(
            ReaderService service,
            Book currentBook,
            PdfManager pdfManager,
            ReaderController readerController,
            ReaderController.MediaNav uiMediaNav,
            Handler mainHandler
    ) {
        Log.d(TAG, "entered handlePrevSentenceOrPage");

        if (currentBook == null || pdfManager == null) return;

        int currentPage = currentBook.getLastPage();
        int currentSentence = currentBook.getSentence();

        StepLength stepLength = StepPrefs.getStepLength(service);

        // If page-step mode or we are at/before the first sentence, go by page
        if (stepLength == StepLength.PAGE || currentSentence <= 0) {
            Log.d(TAG, "handlePrevSentenceOrPage by PAGE");

            if (currentPage <= 0) return;

            final int newPage = currentPage - 1;

            currentBook.setLastPage(newPage);
            currentBook.setSentence(0);
            service.onReadingPositionChanged();

            service.executor.execute(() -> {
                try {
                    List<String> chunks = service.loadCurrentPageSentences();
                    mainHandler.post(() -> {
                        if (readerController != null && chunks != null && !chunks.isEmpty()) {
                            readerController.setChunks(chunks, 0);
                            readerController.startReading();
                        }
                        if (uiMediaNav != null) uiMediaNav.navBackward();
                    });
                } catch (IOException e) {
                    Log.e(TAG, "prev(): failed to load page text", e);
                    DocTellCrashlytics.logPdfError(currentBook, newPage, "render_page", e);
                }
            });
        } else {
            Log.d(TAG, "handlePrevSentenceOrPage by SENTENCE");

            int newSentence = currentSentence - 1;
            if (newSentence < 0) {
                // Should have been caught by the page-branch above, but guard anyway
                return;
            }

            currentBook.setSentence(newSentence);
            service.onReadingPositionChanged();

            // Prefer existing chunks in controller
            List<String> chunks = readerController != null ? readerController.getChunks() : null;
            if (chunks == null || chunks.isEmpty()) {
                try {
                    chunks = service.loadCurrentPageSentences();
                    if (readerController != null && chunks != null && !chunks.isEmpty()) {
                        readerController.setChunks(chunks, newSentence);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "prev(): failed to load sentence text", e);
                    DocTellCrashlytics.logPdfError(currentBook, currentPage, "render_page", e);
                    return;
                }
            }

            final int finalSentence = newSentence;
            final List<String> finalChunks = chunks;

            service.executor.execute(() ->
                    mainHandler.post(() -> {
                        if (readerController != null && finalChunks != null && !finalChunks.isEmpty()) {
                            readerController.setChunks(finalChunks, finalSentence);
                            readerController.startReading();
                        }
                        if (uiMediaNav != null) uiMediaNav.navBackward();
                    })
            );
        }
    }
}
