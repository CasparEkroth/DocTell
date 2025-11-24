package com.doctell.app.view;

import android.content.Context;
import android.graphics.Matrix;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

public class ImageScale {

    private static final float MIN_SCALE = 1.0f;
    private static final float MAX_SCALE = 5.0f;

    private final ImageView pdfView;
    private final Matrix matrix;
    private final ScaleGestureDetector scaleDetector;

    private float scale = 1.0f;

    private float lastX, lastY;
    private boolean isDragging = false;

    private final float[] mValues = new float[9];


    public interface TapNavigator {
        void onTapLeft();
        void onTapRight();
    }
    private final TapNavigator tapNavigator;
    private float tapDownX;
    private float tapDownY;
    private long tapDownTime;

    private static final int TAP_TIMEOUT_MS = 200;
    private static final float TAP_SLOP_PX = 20f;

    public ImageScale(ImageView pdfView, Context ctx, TapNavigator tapNavigator) {
        this.pdfView = pdfView;
        this.tapNavigator = tapNavigator;
        this.matrix = new Matrix();

        this.pdfView.setScaleType(ImageView.ScaleType.MATRIX);
        this.pdfView.setImageMatrix(matrix);

        this.scaleDetector = new ScaleGestureDetector(ctx,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float factor = detector.getScaleFactor();

                        float prevScale = scale;
                        scale *= factor;
                        scale = Math.max(MIN_SCALE, Math.min(scale, MAX_SCALE));

                        // Adjust factor
                        factor = scale / prevScale;

                        // Apply incremental scale around fingers
                        matrix.postScale(
                                factor,
                                factor,
                                detector.getFocusX(),
                                detector.getFocusY()
                        );

                        fixTranslation();
                        applyMatrix();
                        return true;
                    }
                });
    }

    private void applyMatrix() {
        pdfView.setImageMatrix(matrix);
    }

    private void fixTranslation() {
        if (pdfView.getDrawable() == null) return;

        matrix.getValues(mValues);
        float transX = mValues[Matrix.MTRANS_X];
        float transY = mValues[Matrix.MTRANS_Y];
        float scaleX = mValues[Matrix.MSCALE_X];
        float scaleY = mValues[Matrix.MSCALE_Y];

        float drawableWidth = pdfView.getDrawable().getIntrinsicWidth() * scaleX;
        float drawableHeight = pdfView.getDrawable().getIntrinsicHeight() * scaleY;

        float viewWidth = pdfView.getWidth();
        float viewHeight = pdfView.getHeight();

        // Allowed translation range so the image doesn't disappear
        float minX = Math.min(0f, viewWidth - drawableWidth);
        float maxX = 0f;
        float minY = Math.min(0f, viewHeight - drawableHeight);
        float maxY = 0f;

        float clampedX = Math.max(minX, Math.min(transX, maxX));
        float clampedY = Math.max(minY, Math.min(transY, maxY));

        float dx = clampedX - transX;
        float dy = clampedY - transY;

        matrix.postTranslate(dx, dy);
    }

    public boolean onTouch(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                isDragging = true;

                tapDownX = event.getX();
                tapDownY = event.getY();
                tapDownTime = System.currentTimeMillis();
                break;

            case MotionEvent.ACTION_MOVE:
                // Only pan when not scaling and zoomed in
                if (!scaleDetector.isInProgress() && isDragging && scale > MIN_SCALE) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;

                    matrix.postTranslate(dx, dy);
                    fixTranslation();
                    applyMatrix();

                    lastX = event.getX();
                    lastY = event.getY();
                }
                break;

            case MotionEvent.ACTION_UP:
                isDragging = false;
                long dt = System.currentTimeMillis() - tapDownTime;
                float dxUp = Math.abs(event.getX() - tapDownX);
                float dyUp = Math.abs(event.getY() - tapDownY);

                boolean isTap = dt < TAP_TIMEOUT_MS && dxUp < TAP_SLOP_PX && dyUp < TAP_SLOP_PX;

                if (isTap && scale == MIN_SCALE && tapNavigator != null) {
                    // Only page-tap when not zoomed in
                    float midX = pdfView.getWidth() / 2f;
                    if (event.getX() < midX) {
                        tapNavigator.onTapLeft();
                    } else {
                        tapNavigator.onTapRight();
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                break;
        }

        return true;
    }

    public void reset() {
        scale = 1.0f;
        matrix.reset();
        applyMatrix();
    }

    public ScaleGestureDetector getScaleDetector() {
        return this.scaleDetector;
    }

    public Matrix getMatrix() {
        return matrix;
    }
}
