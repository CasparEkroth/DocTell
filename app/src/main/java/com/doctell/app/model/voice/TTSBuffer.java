package com.doctell.app.model.voice;

import android.util.Log;

import com.doctell.app.model.entity.Noise;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
        String[] rawSentences = page.split("(?<=[.!?])\\s+");
        //Log.d("TTSBuffer","page content: " + page);
        //Log.d("TTSBuffer","the split meanings:");
        List<String> cleaned = new ArrayList<>();
        for (String s : rawSentences) {
            s = s.trim();
            if (s.isEmpty()) continue;
            s = s.replaceAll("\\s+", " ");
            if(Noise.isNoise(s))continue;
            //Log.d("TTSBuffer","sentence: " + s);
            cleaned.add(s);
        }
        return cleaned;
    }

    private void setQueueOfSentences() {
        String[] sentences = page.split("(?<=[.!?])\\s+");
        queueOfSentences.addAll(Arrays.asList(sentences));
    }
}

