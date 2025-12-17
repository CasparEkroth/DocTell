package com.doctell.app.model.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.Toast;

import java.util.Locale;

public class TtsWrapper {
    private TextToSpeech tts;
    private final Context context;
    private final Runnable onInitSuccess;
    private UtteranceProgressListener checkListener;


    public TtsWrapper(Context context, Runnable onInitSuccess) {
        this.context = context;
        this.onInitSuccess = onInitSuccess;
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
        Locale targetLocale = getLocaleFromTag(langCode);
        int availability = tts.isLanguageAvailable(targetLocale);

        if (availability == TextToSpeech.LANG_MISSING_DATA ||
                availability == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w("TtsWrapper", "Language data missing for: " + langCode);
            return false;
        }

        tts.setLanguage(targetLocale);
        return true;
    }

    /**
     * Smart intent launcher.
     * It specifically targets the SETTINGS of the ACTIVE engine.
     */
    public static void launchInstallDataIntent(Context context,String defaultEngine) {
        Intent intent = new Intent();
        intent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);

        intent.setPackage(defaultEngine);
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

    public static void showMissingDataDialog(Context ctx, String defaultEngine, String langCode) {
        String langName = langCode;
        try {
            java.util.Locale loc = java.util.Locale.forLanguageTag(langCode);
            langName = loc.getDisplayName();
        } catch (Exception ignored) {}

        new androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setTitle("Voice Data Missing")
                .setMessage("The voice data for " + langName + " is missing. You need to download it to hear the audio.")
                .setCancelable(false)
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                })
                .setPositiveButton("Download", (dialog, which) -> {
                    try {
                        TtsWrapper.launchInstallDataIntent(ctx, defaultEngine);
                    } catch (Exception e) {
                        Toast.makeText(ctx, "Could not open TTS settings", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    public void checkVoiceData(String langCode, Runnable onSuccess, Runnable onError) {
        if (tts == null) {
            onError.run();
            return;
        }

        Locale locale = getLocaleFromTag(langCode);
        int result = tts.setLanguage(locale); // First rough check

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            onError.run();
            return;
        }

        //attach a temporary listener to catch the -4 error.
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {}

            @Override
            public void onDone(String utteranceId) {
                if ("CHECK_VOICE".equals(utteranceId)) {
                    new Handler(Looper.getMainLooper()).post(onSuccess);
                }
            }
            @Override
            public void onError(String utteranceId) {
                // Generic error
                new Handler(Looper.getMainLooper()).post(onError);
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
                // This captures the -4 (ERROR_NOT_INSTALLED_YET)
                Log.e("TtsWrapper", "Check failed with error: " + errorCode);
                new Handler(Looper.getMainLooper()).post(onError);
            }
        });
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0); // Silent
        tts.speak(" ", TextToSpeech.QUEUE_FLUSH, params, "CHECK_VOICE");
    }


    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }
}
