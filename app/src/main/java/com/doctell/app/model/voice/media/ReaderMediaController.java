package com.doctell.app.model.voice.media;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.session.MediaButtonReceiver;

import com.doctell.app.R;

public class ReaderMediaController {
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "reader_channel";

    private final Context context;
    private final PlaybackControl playbackControl;
    private final MediaSessionCompat mediaSession;
    private final NotificationManagerCompat notificationManager;

    private boolean isPlaying = false;
    private int currentIndex = 0;
    private String currentSentence = "";
    private Bitmap coverBitmap;

    public ReaderMediaController(Context ctx, PlaybackControl playbackControl) {
        this.context = ctx.getApplicationContext();
        this.playbackControl = playbackControl;

        mediaSession = new MediaSessionCompat(context, "DocTell");
        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                        | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );
        notificationManager = NotificationManagerCompat.from(context);

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                playbackControl.play();
            }

            @Override
            public void onPause() {
                playbackControl.pause();
            }

            @Override
            public void onStop() {
                playbackControl.stop();
            }

            @Override
            public void onSkipToNext(){
                playbackControl.forward();
            }

            @Override
            public void onSkipToPrevious(){
                playbackControl.backward();
            }

        });

        mediaSession.setActive(true);
    }

    public void updateState(boolean playing,
                            int index,
                            String sentence,
                            Bitmap cover) {
        isPlaying = playing;
        currentIndex = index;
        currentSentence = sentence != null ? sentence : "";
        coverBitmap = cover;

        updateMediaSession();
        showNotification();
    }

    private void updateMediaSession() {
        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_STOP
                                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(
                        isPlaying ? PlaybackStateCompat.STATE_PLAYING
                                : PlaybackStateCompat.STATE_PAUSED,
                        currentIndex,
                        1.0f
                )
                .build();

        mediaSession.setPlaybackState(state);
    }

    private void showNotification() {
        PendingIntent playIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
                context, PlaybackStateCompat.ACTION_PLAY);

        PendingIntent pauseIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
                context, PlaybackStateCompat.ACTION_PAUSE);

        PendingIntent stopIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
                context, PlaybackStateCompat.ACTION_STOP);


        NotificationCompat.Action prevAction =
                new NotificationCompat.Action(
                        android.R.drawable.ic_media_previous, "Previous",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                                context, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS));

        NotificationCompat.Action nextAction =
                new NotificationCompat.Action(
                        android.R.drawable.ic_media_next, "Next",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                                context, PlaybackStateCompat.ACTION_SKIP_TO_NEXT));


        NotificationCompat.Action playPauseAction =
                new NotificationCompat.Action(
                        isPlaying ? android.R.drawable.ic_media_pause
                                : android.R.drawable.ic_media_play,
                        isPlaying ? "Pause" : "Play",
                        isPlaying ? pauseIntent : playIntent
                );

        NotificationCompat.Action stopAction =
                new NotificationCompat.Action(
                        android.R.drawable.ic_media_ff,// ic_media_stop
                        "Stop",
                        stopIntent
                );

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("DocTell – Reading")
                .setContentText(currentSentence)
                .setLargeIcon(coverBitmap)
                .setOngoing(isPlaying)
                .addAction(prevAction)       // index 0
                .addAction(playPauseAction)  // index 1
                .addAction(nextAction)       // index 2
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1)
                )
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .build();

        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    public void stop() {
        notificationManager.cancel(NOTIFICATION_ID);
        mediaSession.setActive(false);
        mediaSession.release();
    }
}
