package com.doctell.app.model.voice.media;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.session.MediaButtonReceiver;

import com.doctell.app.MainActivity;
import com.doctell.app.R;
import com.doctell.app.ReaderActivity;


public class ReaderMediaController {
    public static final String ACTION_PLAY    = "com.doctell.app.action.PLAY";
    public static final String ACTION_PAUSE   = "com.doctell.app.action.PAUSE";
    public static final String ACTION_NEXT    = "com.doctell.app.action.NEXT";
    public static final String ACTION_PREV    = "com.doctell.app.action.PREV";
    private static final String TAG = "ReaderMediaController";
    static final int NOTIFICATION_ID = 1001;
    private final Context context;
    private final PlaybackControl playbackControl;
    private final NotificationManager notificationManager;
    private final MediaSessionCompat mediaSession;

    private boolean isPlaying = false;
    private int currentIndex = 0;
    private String currentSentence = "";
    private Bitmap coverBitmap = null;

    public ReaderMediaController(Context ctx, PlaybackControl playbackControl) {
        this.context = ctx.getApplicationContext();
        this.playbackControl = playbackControl;
        this.notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        createChannelIfNeeded();

        mediaSession = new MediaSessionCompat(context, "DocTellReader");
        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                        | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );

        // This is what the system media player talks to:
        mediaSession.setCallback(new MediaSessionCompat.Callback() {

            @Override
            public void onPlay() {
                Log.d(TAG, "onPlay from system controls");
                playbackControl.play();
                isPlaying = true;
                updateMediaSession();
                updateNotification();
            }

            @Override
            public void onPause() {
                Log.d(TAG, "onPause from system controls");
                playbackControl.pause();
                isPlaying = false;
                updateMediaSession();
                updateNotification();
            }

            @Override
            public void onSkipToNext() {
                Log.d(TAG, "onSkipToNext from system controls");
                playbackControl.next();
            }

            @Override
            public void onSkipToPrevious() {
                Log.d(TAG, "onSkipToPrevious from system controls");
                playbackControl.prev();
            }

            @Override
            public void onStop() {
                Log.d(TAG, "onStop from system controls");
                playbackControl.stop();
                stop();
            }
        });

        mediaSession.setActive(true);
        updateMediaSession();
    }

    public void updateState(boolean playing, int index, String sentence, Bitmap cover) {
        updateFromReader(playing, index, sentence, cover);
    }

    public void updateFromReader(boolean playing, int index,
                                 String sentence, Bitmap cover) {
        isPlaying = playing;
        currentIndex = index;
        currentSentence = sentence != null ? sentence : "";
        coverBitmap = cover;

        updateMediaSession();
        updateNotification();
    }

    private void updateMediaSession() {
        String title = currentSentence.isEmpty()
                ? "DocTell is reading…"
                : currentSentence;

        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "DocTell")
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, coverBitmap)
                .build();
        mediaSession.setMetadata(metadata);

        int state = isPlaying
                ? PlaybackStateCompat.STATE_PLAYING
                : PlaybackStateCompat.STATE_PAUSED;

        PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY
                                | PlaybackStateCompat.ACTION_PAUSE
                                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                                | PlaybackStateCompat.ACTION_STOP
                                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                // we fake "position" with currentIndex; that’s fine
                .setState(state, currentIndex, isPlaying ? 1.0f : 0f)
                .build();

        mediaSession.setPlaybackState(playbackState);
    }

    private void updateNotification() {
        Notification n = buildNotification();
        notificationManager.notify(NOTIFICATION_ID, n);
    }

    private Notification buildNotification() {
        Intent openAppIntent = new Intent(context, ReaderActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ReaderService.CHANNEL_ID)
                .setSmallIcon(R.drawable.doctell_notification)//TODO add DocTell logo
                .setContentTitle("DocTell")
                .setContentText(currentSentence)
                .setContentIntent(contentIntent)
                .setOngoing(isPlaying)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2)
                )
                .setPriority(NotificationCompat.PRIORITY_LOW);

        // Actions can stay if you like, but they’re not needed for the big system card.
        return builder.build();
    }

    private void createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    ReaderService.CHANNEL_ID,
                    "DocTell Reader",
                    NotificationManager.IMPORTANCE_LOW
            );
            notificationManager.createNotificationChannel(channel);
        }
    }

    public Notification buildInitialNotification() {
        // Open ReaderActivity when the user taps the notification
        Intent openIntent = new Intent(context, ReaderActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, 0, openIntent, flags
        );

        PendingIntent prevIntent  = buildServicePendingIntent(ACTION_PREV,  1);
        PendingIntent playIntent  = buildServicePendingIntent(ACTION_PLAY,  2);
        PendingIntent pauseIntent = buildServicePendingIntent(ACTION_PAUSE, 3);
        PendingIntent nextIntent  = buildServicePendingIntent(ACTION_NEXT,  4);

        String subtitle = (currentSentence == null || currentSentence.isEmpty())
                ? "Ready to read"
                : currentSentence;

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, ReaderService.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)        // your app icon
                        .setContentTitle("DocTell")
                        .setContentText(subtitle)
                        .setContentIntent(contentIntent)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                                .setMediaSession(mediaSession.getSessionToken())
                                .setShowActionsInCompactView(0, 1, 2)
                        );

        builder.addAction(
                new NotificationCompat.Action(
                        android.R.drawable.ic_media_previous, "Previous", prevIntent));

        builder.addAction(
                new NotificationCompat.Action(
                        android.R.drawable.ic_media_play, "Play", playIntent));

        builder.addAction(
                new NotificationCompat.Action(
                        android.R.drawable.ic_media_next, "Next", nextIntent));

        return builder.build();
    }

    /**
     * Helper for building a PendingIntent that goes to ReaderService,
     * where you handle ACTION_PLAY / ACTION_PAUSE / ACTION_NEXT / ACTION_PREV
     */
    private PendingIntent buildServicePendingIntent(String action, int requestCode) {
        Intent intent = new Intent(context, ReaderService.class);
        intent.setAction(action);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        return PendingIntent.getService(context, requestCode, intent, flags);
    }

    public void stop() {
        notificationManager.cancel(NOTIFICATION_ID);
        mediaSession.setActive(false);
        mediaSession.release();
    }

    public MediaSessionCompat getMediaSession() {
        return mediaSession;
    }

}
