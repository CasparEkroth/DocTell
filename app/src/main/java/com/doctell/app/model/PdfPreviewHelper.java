package com.doctell.app.model;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;
import android.util.Log;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

public class PdfPreviewHelper {

    public static Bitmap renderOnePage(PdfRenderer renderer, int index, DisplayMetrics dm, int targetWidthPx){
        try(PdfRenderer.Page page = renderer.openPage(index)){
            if(targetWidthPx <= 0){
                targetWidthPx = Math.min(dm.widthPixels,1200);
            }
            int w = targetWidthPx;
            int h = (int) (w * (float)page.getHeight() / page.getWidth());

            Bitmap bmp = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            page.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bmp;
        }
    }

    public static String extractOnePageText(PDDocument doc, int index) throws IOException{
        PDFTextStripper stripper = new PDFTextStripper();
        int base = index + 1;
        stripper.setStartPage(base);
        stripper.setEndPage(base);
        stripper.setSortByPosition(true);

        String text = stripper.getText(doc);
        return text != null ? text.trim() : "";
    }

    public static String thumbPathFor(Context ctx, Uri uri) {
        File dir = new File(ctx.getCacheDir(), "thumbs");
        if (!dir.exists()) dir.mkdirs();
        String name = sha1(uri.toString()) + ".png";
        return new File(dir, name).getAbsolutePath();
    }

    public static String ensureThumb(Context ctx, Uri uri, int targetWidthPx) throws IOException {
        String path = thumbPathFor(ctx, uri);
        File f = new File(path);
        if (f.exists() && f.length() > 0) return path;

        try (ParcelFileDescriptor pfd = ctx.getContentResolver().openFileDescriptor(uri, "r");
             PdfRenderer renderer = new PdfRenderer(pfd);
             PdfRenderer.Page page = renderer.openPage(0)) {

            int w = Math.max(160, Math.min(targetWidthPx, 480));
            int h = (int) (w * (float) page.getHeight() / page.getWidth());
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            try (FileOutputStream out = new FileOutputStream(f)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            bmp.recycle();
        }
        return path;
    }

    public static Bitmap loadThumbBitmap(String path) {
        if (path == null) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        opts.inDither = true;
        return BitmapFactory.decodeFile(path, opts);
    }

    private static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] b = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    public static String ensureLocalCopy(Context ctx, Uri uri) throws IOException {
        File dir = new File(ctx.getFilesDir(), "docs");
        if (!dir.exists()) dir.mkdirs();
        String name = sha1(uri.toString()) + ".pdf";
        File out = new File(dir, name);
        if (out.exists() && out.length() > 0) return out.getAbsolutePath();

        try (InputStream in = ctx.getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
        }
        return out.getAbsolutePath();
    }


}
