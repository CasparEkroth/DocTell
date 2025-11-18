package com.doctell.app.model.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PdfPreviewHelper {

    private static final PDFTextStripper stripper = createStripper();

    private static PDFTextStripper createStripper() {
        try {
            PDFTextStripper s = new PDFTextStripper();
            s.setSortByPosition(true);
            s.setAddMoreFormatting(true);
            return s;
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public static Bitmap renderOnePage(PdfRenderer renderer, int index, DisplayMetrics dm, int targetWidthPx){
        try(PdfRenderer.Page page = renderer.openPage(index)){
            if(targetWidthPx <= 0){
                targetWidthPx = Math.min(dm.widthPixels,1200);
            }
            int bmpW = targetWidthPx;
            int bmpH = Math.round(bmpW * page.getHeight() / (float) page.getWidth());
            Bitmap bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888);
            bmp.eraseColor(0xFFFFFFFF);

            Matrix m = new Matrix();
            float sx = bmpW / (float) page.getWidth();
            float sy = bmpH / (float) page.getHeight();
            m.setScale(sx, sy);

            Rect dest = new Rect(0, 0, bmpW, bmpH);

            page.render(bmp, dest, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bmp;
        }
    }

    public static String extractOnePageText(PDDocument doc, int index){
        try{
            stripper.setStartPage(index + 1);
            stripper.setEndPage(index + 1);
            String text = stripper.getText(doc);
            return text != null ? text.trim() : "";
        } catch (IOException e) {
            return "";
        }
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

    public static List<RectF> getRectsForSentence(
            PDDocument doc,
            int pageIndex,
            String sentence,
            int bitmapWidth,
            int bitmapHeight,
            int pageWidthPdf,
            int pageHeightPdf
    ) {
        List<RectF> out = new ArrayList<>();
        if (sentence == null) return out;

        String targetNorm = normalizeForFuzzy(sentence);
        if (targetNorm.isEmpty()) return out;

        try {
            PositionAwareStripper stripper = new PositionAwareStripper();
            stripper.setStartPage(pageIndex + 1);
            stripper.setEndPage(pageIndex + 1);
            stripper.getText(doc);

            List<PositionAwareStripper.WordBox> words = stripper.getWords();
            if (words.isEmpty()) return out;

            float scaleX = bitmapWidth / (float) pageWidthPdf;
            float scaleY = bitmapHeight / (float) pageHeightPdf;

            StringBuilder pageBuilder = new StringBuilder();
            List<Integer> wordStart = new ArrayList<>();
            List<Integer> wordEnd = new ArrayList<>();

            for (PositionAwareStripper.WordBox wb : words) {
                String normWord = normalizeForFuzzy(wb.text);
                if (normWord.isEmpty()) continue;

                int startPos = pageBuilder.length();
                pageBuilder.append(normWord).append(' ');
                int endPos = pageBuilder.length();

                wordStart.add(startPos);
                wordEnd.add(endPos);
            }

            String pageNorm = pageBuilder.toString().trim();
            if (pageNorm.isEmpty()) return out;

            int idx = pageNorm.indexOf(targetNorm);
            if (idx != -1) {
                int idxEnd = idx + targetNorm.length();

                int startWord = -1, endWord = -1;
                for (int i = 0; i < wordStart.size(); i++) {
                    int ws = wordStart.get(i);
                    int we = wordEnd.get(i);
                    if (we <= idx) continue;
                    if (ws >= idxEnd) break;
                    if (startWord == -1) startWord = i;
                    endWord = i;
                }

                if (startWord != -1 && endWord != -1) {
                    for (int k = startWord; k <= endWord; k++) {
                        PositionAwareStripper.WordBox wb = words.get(k);

                        float pdfLeft   = wb.x;
                        float pdfRight  = wb.x + wb.w;
                        float pdfTop    = wb.y;
                        float pdfBottom = wb.y + wb.h;

                        float left   = pdfLeft   * scaleX;
                        float right  = pdfRight  * scaleX;
                        float top    = pdfTop    * scaleY;
                        float bottom = pdfBottom * scaleY;
                        out.add(new RectF(left, top, right, bottom));
                    }
                    return out;
                }
            }

            // 3) Fallback: fuzzy Jaccard
            String[] targetTokens = targetNorm.split(" ");
            int targetTokenCount = targetTokens.length;

            int bestStart = -1, bestEnd = -1;
            float bestScore = 0f;

            for (int i = 0; i < words.size(); i++) {
                StringBuilder sb = new StringBuilder();

                for (int j = i; j < words.size(); j++) {
                    sb.append(words.get(j).text).append(' ');
                    String candNorm = normalizeForFuzzy(sb.toString());
                    if (candNorm.isEmpty()) continue;

                    int candTokens = candNorm.split(" ").length;

                    // length guard
                    if (candTokens > targetTokenCount * 1.8f) break;

                    float score = jaccardSimilarity(targetNorm, candNorm);

                    if (score > bestScore ||
                            (Math.abs(score - bestScore) < 0.02f && bestStart != -1 && i < bestStart)) {
                        bestScore = score;
                        bestStart = i;
                        bestEnd = j;
                    }
                }
            }

            final float THRESHOLD = 0.35f;
            if (bestStart == -1 || bestEnd == -1 || bestScore < THRESHOLD) {
                return out; // nothing convincing
            }

            for (int k = bestStart; k <= bestEnd; k++) {
                PositionAwareStripper.WordBox wb = words.get(k);

                float pdfLeft = wb.x;
                float pdfRight = wb.x + wb.w;
                float pdfTop = pageHeightPdf - wb.y;
                float pdfBottom = pdfTop - wb.h;

                float left = pdfLeft * scaleX;
                float right = pdfRight * scaleX;
                float top = pdfTop * scaleY;
                float bottom = pdfBottom * scaleY;

                out.add(new RectF(left, top, right, bottom));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return out;
    }


    private static String normalizeForFuzzy(String s) {
        if (s == null) return "";
        String norm = s.toLowerCase();
        norm = norm.replaceAll("[\"',;:()\\[\\]]", " ");
        norm = norm.replaceAll("\\s+", " ").trim();
        return norm;
    }

    private static float jaccardSimilarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0f;

        String[] aTokens = a.split(" ");
        String[] bTokens = b.split(" ");

        Set<String> setA = new HashSet<>();
        Set<String> setB = new HashSet<>();

        for (String t : aTokens) if (!t.isEmpty()) setA.add(t);
        for (String t : bTokens) if (!t.isEmpty()) setB.add(t);

        if (setA.isEmpty() || setB.isEmpty()) return 0f;

        int inter = 0;
        for (String t : setA) if (setB.contains(t)) inter++;

        int union = setA.size() + setB.size() - inter;
        if (union == 0) return 0f;

        return inter / (float) union;
    }




}
