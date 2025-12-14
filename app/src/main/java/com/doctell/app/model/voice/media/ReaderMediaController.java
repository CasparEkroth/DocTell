package com.doctell.app.model.voice.media;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;

import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;

import com.doctell.app.R;
import com.doctell.app.ReaderActivity;
import com.doctell.app.model.utils.PermissionHelper;

public class ReaderMediaController {

    public static final String ACTION_PLAY = "com.doctell.app.action.PLAY";
    public static final String ACTION_PAUSE = "com.doctell.app.action.PAUSE";
    public static final String ACTION_NEXT = "com.doctell.app.action.NEXT";
    public static final String ACTION_PREV = "com.doctell.app.action.PREV";

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

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setClass(context, ReaderService.class);

        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent mediaButtonPendingIntent = PendingIntent.getService(
                context,
                0,
                mediaButtonIntent,
                flags);

        mediaSession.setMediaButtonReceiver(mediaButtonPendingIntent);
        mediaSession.setActive(true);

        // This is what the system media player talks to:
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                Log.d(TAG, "onPlay from system controls");
                playbackControl.play();
                isPlaying = true;
                mediaSession.setActive(true);
                updateMediaSession();
                updateNotification();
            }

            @Override
            public void onPause() {
                Log.d(TAG, "onPause from system controls");
                playbackControl.pause();
                isPlaying = false;
                mediaSession.setActive(true);
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

            @Override
            public boolean onMediaButtonEvent(Intent intent) {
                if (intent != null && Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
                    KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                    if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                        int code = event.getKeyCode();
                        if (code == KeyEvent.KEYCODE_HEADSETHOOK ||
                                code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                                code == KeyEvent.KEYCODE_MEDIA_PLAY ||
                                code == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                            if (isPlaying) {
                                onPause();
                            } else {
                                onPlay();
                            }
                            return true;
                        }
                    }
                }
                return super.onMediaButtonEvent(intent);
            }
        });

        mediaSession.setActive(true);
        updateMediaSession();
    }

    public void updateState(boolean playing, int index, String sentence) {
        updateFromReader(playing, index, sentence, coverBitmap);
    }

    public void updateFromReader(boolean playing, int index,
                                 String sentence, Bitmap cover) {
        isPlaying = playing;
        currentIndex = index;
        currentSentence = sentence != null ? sentence : "";
        coverBitmap = cover;
        if (cover == null)
            Log.d(TAG, "cover is null form the beginning");
        updateMediaSession();
        updateNotification();
    }

    public void setCover(Bitmap cover) {
        Log.d(TAG, "MediaController.setCover called, cover = " + cover);
        this.coverBitmap = cover;
        updateMediaSession();
        updateNotification();
    }

    private void updateMediaSession() {
        if (mediaSession == null) return;

        String title = (currentSentence != null && !currentSentence.isEmpty())
                ? currentSentence : "DocTell is reading";

        MediaMetadataCompat.Builder metaBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "DocTell");

        if (coverBitmap != null) {
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, coverBitmap);
        }

        mediaSession.setMetadata(metaBuilder.build());

        int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;

        long actions = PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                PlaybackStateCompat.ACTION_STOP |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;

        PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, currentIndex, isPlaying ? 1.0f : 0f)
                .build();

        mediaSession.setPlaybackState(playbackState);

        if (!mediaSession.isActive()) {
            mediaSession.setActive(true);
        }
    }

    private void updateNotification() {
        try {
            Notification n = buildNotification();
            notificationManager.notify(NOTIFICATION_ID, n);
        } catch (Exception e) {
            Log.e(TAG, "Failed to update notification", e);
        }
    }

    /**
     * Build the notification with proper action ordering.
     */
    Notification buildNotification() {
        Intent openAppIntent = new Intent(context, ReaderActivity.class);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent contentIntent = PendingIntent.getActivity(
                context, 0, openAppIntent, flags);

        PendingIntent prevIntent = buildServicePendingIntent(ACTION_PREV, 1);
        PendingIntent playIntent = buildServicePendingIntent(ACTION_PLAY, 2);
        PendingIntent nextIntent = buildServicePendingIntent(ACTION_NEXT, 3);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ReaderService.CHANNEL_ID)
                .setSmallIcon(R.drawable.doctell_notification)
                .setContentTitle("DocTell")
                .setContentText(currentSentence)
                .setContentIntent(contentIntent)
                .setOngoing(isPlaying)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (coverBitmap != null) {
            builder.setLargeIcon(coverBitmap);
        } else {
            Log.d(TAG, "cover is null");
        }

        if (PermissionHelper.cheekNotificationPermission(context)) {
            // ADD ACTIONS FIRST, THEN SET COMPACT VIEW INDICES
            builder.addAction(
                    new NotificationCompat.Action(
                            android.R.drawable.ic_media_previous, "Previous", prevIntent));

            builder.addAction(
                    new NotificationCompat.Action(
                            isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                            isPlaying ? "Pause" : "Play",
                            isPlaying ? buildServicePendingIntent(ACTION_PAUSE, 2) : playIntent));

            builder.addAction(
                    new NotificationCompat.Action(
                            android.R.drawable.ic_media_next, "Next", nextIntent));

            builder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowActionsInCompactView(0, 1, 2));
        }
        return builder.build();
    }

    /**
     * Build the initial notification (same as buildNotification now)
     */
    public Notification buildInitialNotification() {
        return buildNotification();
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

    public void refreshSession() {
        if (mediaSession != null) {
            mediaSession.setActive(true);
            updateMediaSession();
        }
    }

    public void stop() {
        try {
            notificationManager.cancel(NOTIFICATION_ID);
        } catch (Exception e) {
            Log.e(TAG, "Error canceling notification", e);
        }
        isPlaying = false;
        updateMediaSession();
        mediaSession.setActive(false);
    }

    /** Fully release the session when the service is going away. */
    public void release() {
        mediaSession.setActive(false);
        mediaSession.release();
    }

    public MediaSessionCompat getMediaSession() {
        return mediaSession;
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
}