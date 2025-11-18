package com.doctell.app.model.data;

import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects bounding boxes for each "word" on a page.
 */
public class PositionAwareStripper extends PDFTextStripper {

    public static class WordBox {
        public final String text;
        public final float x, y, w, h;
        public WordBox(String text, float x, float y, float w, float h) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private final List<WordBox> words = new ArrayList<>();

    public PositionAwareStripper() throws IOException {
        super();
        setSortByPosition(true);
    }

    public List<WordBox> getWords() {
        return words;
    }

    @Override
    protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
        if (string == null || string.trim().isEmpty()) {
            return;
        }


        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;

        for (TextPosition tp: textPositions) {
            float x = tp.getXDirAdj();
            float y = tp.getYDirAdj();
            float w = tp.getWidthDirAdj();
            float h = tp.getHeightDir();

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x + w);
            maxY = Math.max(maxY, y + h);
        }

        if (minX <= maxX && minY <= maxY) {
            words.add(new WordBox(string, minX, minY, maxX - minX, maxY - minY));
        }

        super.writeString(string, textPositions);
    }
}
