package com.doctell.app.model.voice;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

public class TTSBuffer {

    private static TTSBuffer INSTANCE;
    private String page;
    private Queue<String> queueOfSentences;

    private TTSBuffer() {
        queueOfSentences = new ArrayDeque<>();
    }

    public static TTSBuffer getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new TTSBuffer();
        }
        return INSTANCE;
    }

    public void setPage(String pageText) {
        this.page = pageText != null ? pageText : "";
        clear();
        setQueueOfSentences();
    }

    public String getSentence() {
        return queueOfSentences.poll();
    }

    public boolean isEmpty() {
        return queueOfSentences.isEmpty();
    }

    public void clear() {
        queueOfSentences.clear();
    }

    public java.util.List<String> getAllSentences() {
        String[] sentences = page.split("(?<=[.!?])\\s+");
        return java.util.Arrays.asList(sentences);
    }

    private void setQueueOfSentences() {
        String[] sentences = page.split("(?<=[.!?])\\s+");
        queueOfSentences.addAll(Arrays.asList(sentences));
    }
}

