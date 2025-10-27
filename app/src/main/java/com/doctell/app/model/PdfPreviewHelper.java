package com.doctell.app.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import com.tom_roush.pdfbox.text.PDFTextStripper;

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

    public static Bitmap renderPage(int page, PDDocument pdf){
        try {
            PDFRenderer renderer = new PDFRenderer(pdf);

            float scale = 2.0f; // 1.0 = screen resolution, higher = sharper
            return renderer.renderImage(page,scale);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String extractText(Context context, Uri uri, int pageIndex) {
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             PDDocument document = PDDocument.load(in)) {

            if (pageIndex < 0 || pageIndex >= document.getNumberOfPages())
                return "";

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageIndex + 1);
            stripper.setEndPage(pageIndex + 1);

            String text = stripper.getText(document);
            return text != null ? text : "";

        } catch (Exception e) {
            return "";
        }
    }


}
