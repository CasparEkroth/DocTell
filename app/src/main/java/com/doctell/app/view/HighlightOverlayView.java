package com.doctell.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class HighlightOverlayView extends View {

    private final List<RectF> baseRects = new ArrayList<>();
    private final RectF tmpRect = new RectF();
    private final Matrix imageMatrix = new Matrix();
    private final Paint paint;

    public HighlightOverlayView(Context context, Paint paint) {
        super(context, null);
        this.paint = paint;
    }

    public HighlightOverlayView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HighlightOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        // semi-transparent yellow
        paint.setColor(0x55FFFF00);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (RectF r : baseRects) {
            tmpRect.set(r);
            imageMatrix.mapRect(tmpRect);
            canvas.drawRect(tmpRect, paint);
        }
    }

    public void setHighlights(List<RectF> rects) {
        baseRects.clear();
        if (rects != null) {
            for (RectF r : rects) {
                baseRects.add(new RectF(r));
            }
        }
        invalidate();
    }

    public void clearHighlights() {
        baseRects.clear();
        invalidate();
    }

    public void setImageMatrix(Matrix matrix) {
        if (matrix != null) {
            this.imageMatrix.set(matrix);
            invalidate();
        }
    }

}
