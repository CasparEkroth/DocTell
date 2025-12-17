package com.doctell.app.model.voice;

import android.content.Context;
import android.content.Intent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import java.util.Locale;

public class TtsWrapper {
    private TextToSpeech tts;
    private final Context context;
    private final Runnable onInitSuccess;

    public TtsWrapper(Context context, Runnable onInitSuccess) {
        this.context = context;
        this.onInitSuccess = onInitSuccess;
        // Use default constructor -> Respects user's system preference (Samsung/Google/etc)
        this.tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                if (onInitSuccess != null) onInitSuccess.run();
            } else {
                Log.e("TtsWrapper", "Initialization failed!");
            }
        });
    }

    /**
     * Set language using a code (e.g. "sv-SE", "swe", "en").
     * Returns TRUE if language is ready to use.
     * Returns FALSE if data is missing (and triggers installation prompt).
     */
    public boolean setLanguage(String langCode) {
        if (tts == null) return false;

        Locale targetLocale = parseLocale(langCode);
        int availability = tts.isLanguageAvailable(targetLocale);

        if (availability == TextToSpeech.LANG_MISSING_DATA ||
                availability == TextToSpeech.LANG_NOT_SUPPORTED) {

            Log.w("TtsWrapper", "Language missing for: " + tts.getDefaultEngine());
            launchInstallDataIntent();
            return false;
        }

        tts.setLanguage(targetLocale);
        return true;
    }

    /**
     * Smart intent launcher.
     * It specifically targets the SETTINGS of the ACTIVE engine.
     */
    private void launchInstallDataIntent() {
        Intent intent = new Intent();
        intent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);

        // CRITICAL: Force the intent to open settings for the CURRENT engine only
        // This prevents opening Google settings when Samsung is active (and vice versa)
        if (tts.getDefaultEngine() != null) {
            intent.setPackage(tts.getDefaultEngine());
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e("TtsWrapper", "Failed to launch TTS settings", e);
        }
    }

    /**
     * Universal parser to handle "swe", "sv-SE", "en-US", etc.
     */
    public static Locale parseLocale(String code) {
        // 1. Handle legacy 3-letter ISO codes (if you use them)
        if ("swe".equalsIgnoreCase(code)) return new Locale("sv", "SE");
        if ("eng".equalsIgnoreCase(code)) return Locale.US;
        if ("spa".equalsIgnoreCase(code)) return new Locale("es", "ES");

        // 2. Handle standard tags (sv-SE, en-GB)
        return Locale.forLanguageTag(code);
    }


    public static Locale getLocaleFromTag(String tag) {
        if (tag == null) return Locale.getDefault();

        // Manual parsing is often safer for TTS matching than forLanguageTag
        // because TTS engines expect simple (Language, Country) pairs.
        String[] parts = tag.split("-");

        if (parts.length == 1) {
            return new Locale(parts[0]);
        } else if (parts.length == 2) {
            return new Locale(parts[0], parts[1]);
        } else if (parts.length >= 3) {
            return new Locale(parts[0], parts[1], parts[2]);
        }

        return Locale.forLanguageTag(tag); // Fallback
    }


    public TextToSpeech getTts() {
        return tts;
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }
}
