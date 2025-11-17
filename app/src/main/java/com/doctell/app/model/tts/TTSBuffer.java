package com.doctell.app.model.tts;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

public class TTSBuffer {

    private static TTSBuffer INSTANCE;
    private String page;
    private Queue<String> queueOfSentences;

    private TTSBuffer(){
        queueOfSentences = new ArrayDeque<>();
    }
    public static TTSBuffer getInstance(){
        if (INSTANCE == null) {
            synchronized (TTSModel.class) {
                if (INSTANCE == null) INSTANCE = new TTSBuffer();
            }
        }
        return INSTANCE;
    }

    public String getPage() {
        return page;
    }

    public void setPage(String page) {
        this.page = page;
        setQueueOfSentences();
    }

    public String getSenates(){
        return queueOfSentences.poll();
    }

    public boolean isEmpty(){return queueOfSentences.isEmpty();}

    public void clear(){
        queueOfSentences.clear();
    }
    private void setQueueOfSentences(){
        String[] sentences = page.split("(?<=[.!?])\\s+");
        queueOfSentences.addAll(Arrays.asList(sentences));
    }
}
