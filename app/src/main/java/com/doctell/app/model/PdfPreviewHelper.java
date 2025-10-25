package com.doctell.app.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.locks.Condition;

public class PdfPreviewHelper {

    public static Bitmap renderFirstPageFromUri(Context context, Uri uri) throws IOException {
        ParcelFileDescriptor fileDescriptor = context.getContentResolver().openFileDescriptor(uri, "r");

        if(fileDescriptor == null){
            throw new IOException("Cannot open file descriptor for URI");
        }
        PdfRenderer renderer = new PdfRenderer(fileDescriptor);
        PdfRenderer.Page page = renderer.openPage(0);

        int width = (int)(page.getWidth() * 0.5);
        int height = (int)(page.getHeight() * 0.5);

        Bitmap bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        page.render(bitmap, null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

        page.close();
        renderer.close();
        fileDescriptor.close();

        return bitmap;
    }
}
