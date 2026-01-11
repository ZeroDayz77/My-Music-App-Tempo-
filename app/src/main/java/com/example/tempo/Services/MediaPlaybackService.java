package com.example.tempo.Services;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.media.MediaBrowserServiceCompat;

import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.MediaBrowserCompat;

import com.example.tempo.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import android.Manifest;
import android.content.pm.PackageManager;

public class MediaPlaybackService extends MediaBrowserServiceCompat {
    private static final String TAG = "MediaPlaybackSvc";
    public static final String ACTION_PLAY = "com.example.tempo.action.PLAY";
    public static final String ACTION_PAUSE = "com.example.tempo.action.PAUSE";
    public static final String ACTION_TOGGLE = "com.example.tempo.action.TOGGLE";
    public static final String ACTION_NEXT = "com.example.tempo.action.NEXT";
    public static final String ACTION_PREV = "com.example.tempo.action.PREV";
    public static final String ACTION_SEEK = "com.example.tempo.action.SEEK";

    public static final String EXTRA_PLAYLIST = "extra_playlist"; // ArrayList<File> (Serializable)
    public static final String EXTRA_POSITION = "extra_position";
    public static final String EXTRA_SEEK_POSITION = "extra_seek_position";

    public static final String SKIPSONGNEXT = "skip_song_next";
    public static final String SKIPSONGPREV = "skip_song_prev";
    public static final String BUTTONPLAY = "button_play";

    public static final String CHANNEL_ID = "channel1";

    private MediaSessionCompat mediaSession;
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private ArrayList<File> queue = new ArrayList<>();
    private int queueIndex = 0;
    private final int NOTIF_ID = 1;

    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                // Pause playback when headphones disconnected
                if (mediaSession != null) mediaSession.getController().getTransportControls().pause();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        initMediaSession();
        Log.d(TAG, "Service created");
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, "TempoMediaSession");
        mediaSession.setCallback(mediaSessionCallback);
        setSessionToken(mediaSession.getSessionToken());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        if (action == null) return START_STICKY;

        try {
            switch (action) {
                case ACTION_PLAY: {
                    // load playlist and position
                    ArrayList<File> list = null;
                    Bundle extras = intent.getExtras();
                    if (extras != null) {
                        Object obj = extras.getSerializable(EXTRA_PLAYLIST);
                        if (obj instanceof ArrayList) {
                            //noinspection unchecked
                            list = (ArrayList<File>) obj;
                        }
                        queueIndex = extras.getInt(EXTRA_POSITION, 0);
                    }
                    if (list != null && !list.isEmpty()) queue = list;
                    playCurrent();
                    break;
                }
                case ACTION_PAUSE: {
                    if (mediaSession != null) mediaSession.getController().getTransportControls().pause();
                    break;
                }
                case ACTION_TOGGLE: {
                    PlaybackStateCompat state = mediaSession.getController().getPlaybackState();
                    if (state != null && state.getState() == PlaybackStateCompat.STATE_PLAYING) {
                        mediaSession.getController().getTransportControls().pause();
                    } else {
                        mediaSession.getController().getTransportControls().play();
                    }
                    break;
                }
                case ACTION_NEXT: {
                    skipToNext();
                    break;
                }
                case ACTION_PREV: {
                    skipToPrevious();
                    break;
                }
                case ACTION_SEEK: {
                    long pos = intent.getLongExtra(EXTRA_SEEK_POSITION, -1);
                    if (pos >= 0 && mediaPlayer != null) mediaPlayer.seekTo((int) pos);
                    updatePlaybackState(mediaPlayer != null && mediaPlayer.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                    updateNotification();
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "onStartCommand error", e);
        }

        return START_STICKY;
    }

    private final MediaSessionCompat.Callback mediaSessionCallback = new MediaSessionCompat.Callback() {
        @Override
        public void onPlay() {
            // If we already have a mediaPlayer (paused), resume instead of recreating
            try {
                if (mediaPlayer != null) {
                    if (!mediaPlayer.isPlaying()) {
                        mediaPlayer.start();
                        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
                        updateNotification();
                        // ensure service is foreground while playing
                        startForegroundNotification();
                    }
                } else {
                    playCurrent();
                }
            } catch (Exception e) {
                Log.e(TAG, "onPlay error", e);
                // fallback to starting fresh
                playCurrent();
            }
        }

        @Override
        public void onPause() {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);
                updateNotification();
            }
        }

        @Override
        public void onSkipToNext() {
            skipToNext();
        }

        @Override
        public void onSkipToPrevious() {
            skipToPrevious();
        }

        @Override
        public void onSeekTo(long pos) {
            if (mediaPlayer != null) {
                mediaPlayer.seekTo((int) pos);
                updatePlaybackState(mediaPlayer.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                updateNotification();
            }
        }

        @Override
        public void onStop() {
            stopAndCleanup();
        }
    };

    private void playCurrent() {
        if (queue == null || queue.isEmpty()) return;
        if (queueIndex < 0) queueIndex = 0;
        if (queueIndex >= queue.size()) queueIndex = queue.size() - 1;

        File f = queue.get(queueIndex);
        if (f == null) return;

        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }

            Uri uri = Uri.parse(f.toString());
            mediaPlayer = MediaPlayer.create(getApplicationContext(), uri);
            if (mediaPlayer == null) return;
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            mediaPlayer.setOnCompletionListener(mp -> skipToNext());
            mediaPlayer.start();

            // request audio focus
            int result = audioManager.requestAudioFocus(focusRequest);
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w(TAG, "Audio focus not granted");
                // proceed anyway, but audio may be muted by the system
            }
            // register noisy receiver
            registerReceiver(noisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

            // update metadata and playback state
            String title = f.getName().replace(".mp3", "").replace(".wav", "");
            MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer.getDuration())
                    .build();
            mediaSession.setMetadata(metadata);

            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
            startForegroundNotification();

        } catch (Exception e) {
            Log.e(TAG, "playCurrent error", e);
        }
    }

    private void skipToNext() {
        if (queue == null || queue.isEmpty()) return;
        queueIndex = (queueIndex + 1) % queue.size();
        playCurrent();
        mediaSession.setActive(true);
    }

    private void skipToPrevious() {
        if (queue == null || queue.isEmpty()) return;
        queueIndex = (queueIndex - 1) < 0 ? queue.size() - 1 : queueIndex - 1;
        playCurrent();
        mediaSession.setActive(true);
    }

    private void updatePlaybackState(int state) {
        long pos = mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
        long actions = PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SEEK_TO;

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, pos, 1.0f);
        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private void updateNotification() {
        // Build notification from current metadata/state
        MediaMetadataCompat meta = mediaSession.getController().getMetadata();
        PlaybackStateCompat state = mediaSession.getController().getPlaybackState();
        Notification notification = buildNotification(meta, state);
        safeNotify(notification);
    }

    private void startForegroundNotification() {
        MediaMetadataCompat meta = mediaSession.getController().getMetadata();
        PlaybackStateCompat state = mediaSession.getController().getPlaybackState();
        Notification notification = buildNotification(meta, state);
        startForeground(NOTIF_ID, notification);
    }

    private Notification buildNotification(MediaMetadataCompat meta, PlaybackStateCompat state) {
        String title = "";
        long duration = 0;
        long position = 0;
        boolean isPlaying = false;
        if (meta != null) {
            title = meta.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
            duration = meta.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
        }
        if (state != null) {
            position = state.getPosition();
            isPlaying = state.getState() == PlaybackStateCompat.STATE_PLAYING;
        }

        // Intents for actions -> point to this service
        PendingIntent prevIntent = PendingIntent.getService(this, 201, new Intent(this, MediaPlaybackService.class).setAction(ACTION_PREV), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent playIntent = PendingIntent.getService(this, 202, new Intent(this, MediaPlaybackService.class).setAction(ACTION_TOGGLE), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent nextIntent = PendingIntent.getService(this, 203, new Intent(this, MediaPlaybackService.class).setAction(ACTION_NEXT), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title != null && !title.isEmpty() ? title : getString(R.string.app_name))
                .setSubText("Now Playing:")
                .setContentText("")
                .setSmallIcon(R.drawable.ic_music)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(isPlaying)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setContentIntent(PendingIntent.getActivity(this, 301, new Intent(this, com.example.tempo.ui.MusicPlayerActivity.class).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        // Actions
        if (queue != null && queue.size() > 1) builder.addAction(R.drawable.ic_skip_previous_icon, "Previous", prevIntent);
        builder.addAction(isPlaying ? R.drawable.ic_pause_icon : R.drawable.ic_play_icon, isPlaying ? "Pause" : "Play", playIntent);
        if (queue != null && queue.size() > 1) builder.addAction(R.drawable.ic_skip_next_icon, "Next", nextIntent);

        androidx.media.app.NotificationCompat.MediaStyle style = new androidx.media.app.NotificationCompat.MediaStyle();
        style.setShowActionsInCompactView(0,1,2);
        if (mediaSession != null) style.setMediaSession(mediaSession.getSessionToken());
        builder.setStyle(style);

        if (duration > 0) {
            int dur = (int) Math.min(Integer.MAX_VALUE, duration);
            int posInt = (int) Math.max(0, Math.min(dur, position));
            builder.setProgress(dur, posInt, false);
        }

        return builder.setPriority(NotificationCompat.PRIORITY_HIGH).build();
    }

    private void safeNotify(Notification notification) {
        if (notification == null) return;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                NotificationManagerCompat.from(this).notify(NOTIF_ID, notification);
            }
        } else {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, notification);
        }
    }

    private void stopAndCleanup() {
        try {
            stopForeground(true);
        } catch (Exception ignored) {}
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        try { unregisterReceiver(noisyReceiver); } catch (Exception ignored) {}
        try { audioManager.abandonAudioFocusRequest(focusRequest); } catch (Exception ignored) {}
        stopSelf();
    }

    // Audio focus handling
    private final AudioManager.OnAudioFocusChangeListener afChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            if (mediaSession == null) return;
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS:
                    // lost focus for an unbounded amount of time: stop playback and release media
                    mediaSession.getController().getTransportControls().pause();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    // lost focus for a short time, pause
                    mediaSession.getController().getTransportControls().pause();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    // Lower the volume
                    if (mediaPlayer != null) mediaPlayer.setVolume(0.2f, 0.2f);
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    // resume or raise volume
                    if (mediaPlayer != null) {
                        mediaPlayer.setVolume(1.0f, 1.0f);
                        if (!mediaPlayer.isPlaying()) mediaSession.getController().getTransportControls().play();
                    }
                    break;
            }
        }
    };

    private final AudioFocusRequest focusRequest =
            new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(afChangeListener)
                    .build();

    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, Bundle rootHints) {
        return new BrowserRoot("root", null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        // Not exposing a browsable library
        result.sendResult(new ArrayList<>());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAndCleanup();
    }
}
