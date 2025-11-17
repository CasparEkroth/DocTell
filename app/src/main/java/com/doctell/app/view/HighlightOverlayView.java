package com.doctell.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class HighlightOverlayView extends View {

    private final Paint paint;
    private final List<RectF> rects = new ArrayList<>();

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
        for (RectF r : rects) {
            canvas.drawRect(r, paint);
        }
    }

    public void setHighlights(List<RectF> newRects) {
        rects.clear();
        if (newRects != null) rects.addAll(newRects);
        invalidate();
    }

    public void setHighlight(RectF rect) {
        rects.clear();
        if (rect != null) rects.add(rect);
        invalidate();
    }

    public void clearHighlights() {
        rects.clear();
        invalidate();
    }
}
