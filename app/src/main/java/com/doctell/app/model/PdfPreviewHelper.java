package com.doctell.app.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.PDFRenderer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.Condition;

public class PdfPreviewHelper {

    public static Bitmap renderFirstPageFromUri(Context ctx, Uri uri) {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri);
             PDDocument doc = PDDocument.load(in)) {

            PDFRenderer renderer = new PDFRenderer(doc);
            return renderer.renderImageWithDPI(0, 72);
        } catch (Exception e) {
            Log.e("PdfPreviewHelper", "Failed to render preview for " + uri + ": " + e.getMessage());
            return null; // never crash here
        }
    }

}
