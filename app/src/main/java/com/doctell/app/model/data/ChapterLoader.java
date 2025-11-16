package com.doctell.app.model.data;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.doctell.app.model.ChapterItem;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDDocumentCatalog;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChapterLoader {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onChaptersLoaded(List<ChapterItem> chapters);
    }

    public void loadChaptersAsync(String bookLocalPath, Callback callback) {
        executor.submit(() -> {
            List<ChapterItem> result = null;
            try {
                result = loadChaptersInternal(bookLocalPath);
            } catch (IOException e) {
                Log.e("ChapterLoader", "Error loading chapters", e);
            }

            final List<ChapterItem> safeResult =
                    (result != null) ? result : new ArrayList<>();
            mainHandler.post(() -> callback.onChaptersLoaded(safeResult));
        });
    }

    private List<ChapterItem> loadChaptersInternal(String bookLocalPath) throws IOException {
        PDDocument doc = null;
        try {
            doc = PDDocument.load(new File(bookLocalPath));
            PDDocumentCatalog catalog = doc.getDocumentCatalog();
            List<ChapterItem> chapters = new ArrayList<>();

            if (catalog == null) {
                Log.w("ChapterLoader", "No document catalog found");
                return null;
            }

            PDDocumentOutline outline = catalog.getDocumentOutline();

            if (outline == null || !outline.hasChildren()) {
                Log.w("ChapterLoader", "No outline structure found");
                return null;
            }

            collectOutline(doc, outline, chapters, 0);
            return chapters;

        } finally {
            if (doc != null) {
                doc.close();
            }
        }
    }

    private static void collectOutline(PDDocument doc,
                                       PDOutlineNode node,
                                       List<ChapterItem> out,
                                       int level) throws IOException {

        PDOutlineItem current = node.getFirstChild();

        while (current != null) {

            int pageIndex = resolvePageIndex(doc, current);
            if (pageIndex >= 0) {
                out.add(new ChapterItem(current.getTitle(), pageIndex, level));
            }
            //recursive for sub chapters
            if (current.hasChildren()) {
                collectOutline(doc,current, out,level + 1);
            }

            current = current.getNextSibling();
        }
    }

    private static int resolvePageIndex(PDDocument doc, PDOutlineItem item) throws IOException {
        PDDestination dest = item.getDestination();

        if(dest == null && item.getAction() instanceof PDActionGoTo){
            dest = ((PDDestination) ((PDActionGoTo) item.getAction()).getDestination());
        }

        if(dest instanceof PDPageDestination){
            PDPage page = ((PDPageDestination) dest).getPage();
            return doc.getPages().indexOf(page);
        }

        if(dest instanceof PDNamedDestination){
            dest = doc.getDocumentCatalog().findNamedDestinationPage((PDNamedDestination) dest);
            if(dest instanceof PDPageDestination){
                PDPage page = ((PDPageDestination) dest).getPage();
                return doc.getPages().indexOf(page);
            }
        }
        return -1;
    }

    public void shutdown() {
        executor.shutdown();
    }

}
