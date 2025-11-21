package com.doctell.app.model.voice.notPublic;

import static android.telecom.DisconnectCause.LOCAL;

import android.content.Context;
import android.content.SharedPreferences;

import com.doctell.app.model.voice.LocalTtsEngine;
import com.doctell.app.model.voice.TtsEngineStrategy;

public class TtsEngineProvider {
    public static TtsEngineStrategy getEngine(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(
                "doctell_prefs",
                Context.MODE_PRIVATE
        );
        String typeStr = prefs.getString("tts_engine_type", "LOCAL");
        TtsEngineType type = TtsEngineType.valueOf(typeStr);

        switch (type) {
            case API:
                //return ApiTtsEngine.getInstance(ctx);
            case LOCAL:
            default:
                return LocalTtsEngine.getInstance(ctx);
        }
    }
}
