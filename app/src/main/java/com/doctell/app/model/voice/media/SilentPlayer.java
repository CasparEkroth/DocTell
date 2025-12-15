package com.doctell.app.model.voice.media;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.doctell.app.R;

import java.util.function.Supplier;

public class SilentPlayer {
    private static MediaPlayer silentPlayer;
    private static Handler watchdogHandler = new Handler(Looper.getMainLooper());

    private SilentPlayer(){}

    public static void startSilentAudio(Context ctx) {
        if (silentPlayer == null) {
            silentPlayer = MediaPlayer.create(ctx, R.raw.silence_1s); // 1s silent WAV
            silentPlayer.setLooping(true);
            silentPlayer.setVolume(0f, 0f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                silentPlayer.setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                );
            }
        }
        if (!silentPlayer.isPlaying()) {
            silentPlayer.start();
        }
    }

    public static void stopSilentAudio() {
        if (silentPlayer != null && silentPlayer.isPlaying()) {
            Log.w("SilentPlayer", "Silent player not playing, restarting...");
            silentPlayer.pause();
        }
    }

    public static Runnable makeWatchdog(Supplier<Boolean> isAutoReadingSupplier, Context ctx) {
        Log.d("SilentPlayer", "watchdog tick - autoReading=" + isAutoReadingSupplier.get());
        return new Runnable() {
            @Override
            public void run() {
                if (!isAutoReadingSupplier.get()){
                    stopSilentAudio();
                    watchdogHandler.postDelayed(this, 5000);
                    return;
                }

                if (silentPlayer != null && !silentPlayer.isPlaying()) {
                    stopSilentAudio();
                    startSilentAudio(ctx);
                }

                watchdogHandler.postDelayed(this, 5000);
            }
        };
    }

    public static Handler getWatchdogHandler(){return watchdogHandler;}

}
