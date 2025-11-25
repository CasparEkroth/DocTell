package com.doctell.app.model.voice;

import android.content.Context;
import android.speech.tts.Voice;

import java.util.Locale;

public class LocalTtsEngine extends BaseTtsEngine {

    private static LocalTtsEngine instance;

    public static LocalTtsEngine getInstance(Context context){
        if (instance == null) {
            instance = new LocalTtsEngine(context.getApplicationContext());
        }
        return instance;
    }

    private LocalTtsEngine(Context context){
        super(context);
        init(context);
    }

    @Override
    protected boolean acceptVoice(Voice v, Locale engineLanguage) {
        return v.getQuality() == Voice.QUALITY_HIGH
                && !v.isNetworkConnectionRequired()
                && v.getLocale().equals(engineLanguage);
    }
}
