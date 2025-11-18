package com.doctell.app.view;


import android.content.Context;
import android.graphics.Matrix;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

import com.doctell.app.model.tts.TTSModel;

public class ImageScale {

    private ScaleGestureDetector scaleDetector;
    private final Matrix matrix;
    private ImageView pdfView;
    private float scale;

    public ImageScale(ImageView pdfView, Context cxt){
        this.pdfView = pdfView;
        matrix = new Matrix();
        scale = 1.0f;
        scaleDetector = new ScaleGestureDetector(cxt, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scale *= detector.getScaleFactor();
                scale = Math.max(0.5f, Math.min(scale, 5.0f));
                matrix.setScale(scale, scale, detector.getFocusX(), detector.getFocusY());
                pdfView.setImageMatrix(matrix);
                return true;
            }
        });
    }
    public ScaleGestureDetector getScaleDetector(){return this.scaleDetector;}

}
