package com.doctell.app.model.pdf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;

import com.tom_roush.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class PdfManager {

    private final Context appContext;
    private final String bookLocalPath; // Book.getLocalPath()

    private PDDocument pdDocument;
    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor pdfFd;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public PdfManager(Context ctx,
                      String bookLocalPath,
                      PDDocument doc,
                      ParcelFileDescriptor pdfFd,
                      PdfRenderer renderer) {
        this.appContext = ctx.getApplicationContext();
        this.bookLocalPath = bookLocalPath;
        this.pdDocument = doc;
        this.pdfFd = pdfFd;
        this.pdfRenderer = renderer;
    }

    private synchronized void openIfNeeded() throws IOException {
        if (pdDocument == null) {
            pdDocument = PDDocument.load(new FileInputStream(bookLocalPath));
        }
        if (pdfRenderer == null) {
            pdfFd = ParcelFileDescriptor.open(
                    new File(bookLocalPath),
                    ParcelFileDescriptor.MODE_READ_ONLY
            );
            pdfRenderer = new PdfRenderer(pdfFd);
        }
    }

    public void ensureOpened() throws IOException{
        synchronized(this){
            openIfNeeded();
        }
    }

    public synchronized int getPageCount() throws IOException {
        openIfNeeded();
        return pdfRenderer.getPageCount();
    }

    public synchronized String getPageText(int pageIndex) throws IOException {
        openIfNeeded();
        return PdfPreviewHelper.extractOnePageText(pdDocument, pageIndex);
    }

    public synchronized Bitmap renderPageBitmap(
            int pageIndex,
            DisplayMetrics dm,
            int targetWidthPx
    ) throws IOException {
        openIfNeeded();
        return PdfPreviewHelper.renderOnePage(pdfRenderer, pageIndex, dm, targetWidthPx);
    }

    public synchronized void close() {
        try {
            if (pdfRenderer != null) pdfRenderer.close();
        } catch (Exception ignore) {}
        try {
            if (pdfFd != null) pdfFd.close();
        } catch (IOException ignored) {}
        try {
            if (pdDocument != null) pdDocument.close();
        } catch (IOException ignored) {}
        pdfRenderer = null;
        pdfFd = null;
        pdDocument = null;
    }
}
