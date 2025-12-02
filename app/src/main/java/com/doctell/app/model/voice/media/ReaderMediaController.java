package com.doctell.app.model.voice.media;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.session.MediaButtonReceiver;

import com.doctell.app.MainActivity;

public class ReaderMediaController {

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "doctell_reader_channel";
    public static final String EXTRA_FROM_MEDIA = "EXTRA_FROM_MEDIA";

    private final Context context;
    private final PlaybackControl playbackControl;
    private final MediaSessionCompat mediaSession;
    private final NotificationManagerCompat notificationManager;

    // State used when building the notification / MediaSession
    private boolean isPlaying = false;
    private int currentIndex = 0;
    private String currentSentence = "";
    private Bitmap coverBitmap = null;

    public ReaderMediaController(Context context, PlaybackControl playbackControl) {
        this.context = context.getApplicationContext();
        this.playbackControl = playbackControl;

        notificationManager = NotificationManagerCompat.from(this.context);

        mediaSession = new MediaSessionCompat(this.context, "DocTellReaderSession");
        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                        | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                playbackControl.play();
                updateState(true, currentIndex, currentSentence, coverBitmap);
            }

            @Override
            public void onPause() {
                playbackControl.pause();
                updateState(false, currentIndex, currentSentence, coverBitmap);
            }

            @Override
            public void onStop() {
                playbackControl.stop();
                stop();
            }

            @Override
            public void onSkipToNext() {
                playbackControl.next();
            }

            @Override
            public void onSkipToPrevious() {
                playbackControl.prev();
            }
        });

        mediaSession.setActive(true);

        createNotificationChannelIfNeeded();

        updateState(false, 0, "", null);
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
    public Notification buildInitialNotification() {
        updateMediaSession();
        return createNotificationInternal();
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
                                | PlaybackStateCompat.ACTION_STOP
                                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                // use index as a fake "position"
                .setState(state, currentIndex, isPlaying ? 1.0f : 0f)
                .build();

        mediaSession.setPlaybackState(playbackState);
    }

    private void createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "DocTell reader",
                    android.app.NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Lockscreen media controls for DocTell");
            android.app.NotificationManager manager =
                    (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * For regular state updates – update the existing notification.
     */
    private void showNotification() {
        // Android 13+ notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                // No permission → just skip showing the notification
                return;
            }
        }

        Notification notification = createNotificationInternal();
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    /**
     * Actually builds the Notification object based on current state.
     * Used both by showNotification() and buildNotification().
     */
    private Notification createNotificationInternal() {
        // Intent to open the app (MainActivity) and let it redirect to last book
        Intent activityIntent = new Intent(context, MainActivity.class);
        activityIntent.putExtra(EXTRA_FROM_MEDIA, true);
        activityIntent.setFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                0,
                activityIntent,
                piFlags
        );

        // Connect session with the activity to open
        mediaSession.setSessionActivity(contentIntent);

        // Media button PendingIntents
        PendingIntent playIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
                context, PlaybackStateCompat.ACTION_PLAY);

        PendingIntent pauseIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
                context, PlaybackStateCompat.ACTION_PAUSE);

        PendingIntent stopIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
                context, PlaybackStateCompat.ACTION_STOP);

        PendingIntent nextIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
                context, PlaybackStateCompat.ACTION_SKIP_TO_NEXT);

        PendingIntent prevIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
                context, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);

        NotificationCompat.Action playPauseAction = isPlaying
                ? new NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                pauseIntent
        )
                : new NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                playIntent
        );

        NotificationCompat.Action nextAction = new NotificationCompat.Action(
                android.R.drawable.ic_media_next,
                "Next",
                nextIntent
        );

        NotificationCompat.Action prevAction = new NotificationCompat.Action(
                android.R.drawable.ic_media_previous,
                "Previous",
                prevIntent
        );

        String text = currentSentence.isEmpty()
                ? "DocTell is reading…"
                : currentSentence;

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play) // TODO: replace app icon
                .setContentTitle("DocTell")
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setLargeIcon(coverBitmap)
                .setStyle(
                        new androidx.media.app.NotificationCompat.MediaStyle()
                                .setMediaSession(mediaSession.getSessionToken())
                                .setShowActionsInCompactView(0, 1, 2)
                )
                .addAction(prevAction)
                .addAction(playPauseAction)
                .addAction(nextAction)
                // (Optional) stop button
                // .addAction(new NotificationCompat.Action(
                //         android.R.drawable.ic_menu_close_clear_cancel,
                //         "Stop",
                //         stopIntent
                // ))
                .setOngoing(isPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
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
