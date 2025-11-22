package com.doctell.app.model.voice;

public interface HighlightListener {
    void onChunkStart(int index, String text);
    void onChunkDone(int index, String text);

    void onPageFinished();
}
