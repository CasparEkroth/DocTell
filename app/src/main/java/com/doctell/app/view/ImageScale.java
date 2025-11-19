package com.doctell.app.view;


import android.content.Context;
import android.graphics.Matrix;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

import com.doctell.app.model.tts.TTSModel;

public class ImageScale {

    private ScaleGestureDetector scaleDetector;
    private final Matrix matrix;
    private ImageView pdfView;
    private float scale;

    private float lastX, lastY;
    private boolean isDragging = false;

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

    public boolean onTouch(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        switch (event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                isDragging = true;
                break;

            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress() && isDragging) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;

                    matrix.postTranslate(dx, dy);
                    pdfView.setImageMatrix(matrix);

                    lastX = event.getX();
                    lastY = event.getY();
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                break;
        }
        return true;
    }
    public ScaleGestureDetector getScaleDetector(){return this.scaleDetector;}
    public Matrix getMatrix(){return matrix;}
}
