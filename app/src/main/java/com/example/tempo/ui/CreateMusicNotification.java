package com.example.tempo.ui;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.example.tempo.Services.NotificationActionService;

public class CreateMusicNotification extends AppCompatActivity {

    public static final String CHANNEL_ID = "channel1";
    public static final String SKIPSONGNEXT = "skip_song_next";
    public static final String SKIPSONGPREV = "skip_song_prev";
    public static final String BUTTONPLAY = "button_play";
    public static Notification notification;

    // Keep original signature for compatibility (no progress metadata)
    public static void createNotification(Context context, String currentSong, int playButton, int pos, int size) {
        // Delegate to the richer API with defaults (no progress)
        createNotification(context, currentSong, "", playButton, pos, size, 0L, 0L, false);
    }

    public static void createNotification(Context context,
                                          String title,
                                          String artist,
                                          int playButton,
                                          int pos,
                                          int size,
                                          long durationMs,
                                          long currentPositionMs,
                                          boolean isPlaying) {

            NotificationManagerCompat managerCompat = NotificationManagerCompat.from(context);

            // Create a media session so lockscreen and MediaStyle can show controls/metadata
            MediaSessionCompat mediaSessionCompat = new MediaSessionCompat(context, "TempoMediaSession");

            // Fill metadata
            MediaMetadataCompat.Builder metaBuilder = new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title != null ? title : "")
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist != null ? artist : "");
            if (durationMs > 0) {
                metaBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs);
            }
            mediaSessionCompat.setMetadata(metaBuilder.build());

            // Setup playback state with position so notification can show a progress/seekbar
            long actions = PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE |
                    PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SEEK_TO;

            PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                    .setActions(actions)
                    .setState(isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                            currentPositionMs, 1.0f)
                    .setActiveQueueItemId(0)
                    .build();
            mediaSessionCompat.setPlaybackState(state);
            mediaSessionCompat.setActive(true);

            // Prepare PendingIntents for actions (only if applicable) - use unique request codes
            PendingIntent pendingIntentPrevious = null;
            int drw_prev = 0;
            if (pos > 0) {
                Intent intentPrevious = new Intent(context, NotificationActionService.class)
                        .setAction(SKIPSONGPREV);
                pendingIntentPrevious = PendingIntent.getBroadcast(
                        context, 101, intentPrevious, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                drw_prev = com.example.tempo.R.drawable.ic_skip_previous_icon;
            }

            Intent intentPlay = new Intent(context, NotificationActionService.class)
                    .setAction(BUTTONPLAY);
            PendingIntent pendingIntentPlay = PendingIntent.getBroadcast(
                    context, 102, intentPlay, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            PendingIntent pendingIntentNext = null;
            int drw_next = 0;
            if (pos < size - 1) {
                Intent intentNext = new Intent(context, NotificationActionService.class)
                        .setAction(SKIPSONGNEXT);
                pendingIntentNext = PendingIntent.getBroadcast(
                        context, 103, intentNext, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                drw_next = com.example.tempo.R.drawable.ic_skip_next_icon;
            }

            // Content intent: tapping the notification opens/reorders the MusicPlayerActivity
            Intent openPlayer = new Intent(context, com.example.tempo.ui.MusicPlayerActivity.class);
            openPlayer.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent contentIntent = PendingIntent.getActivity(context, 201, openPlayer, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

            // Build notification - set category and high priority so it appears on lockscreen and status bar
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle(title != null && !title.isEmpty() ? title : context.getString(com.example.tempo.R.string.app_name))
                    // Collapsed content text (shown in status bar / quick area) should be a short song title
                    .setContentText(title != null && !title.isEmpty() ? title : "Currently Playing")
                    // Use subText for artist in expanded/compact views
                    .setSubText((artist != null && !artist.isEmpty()) ? artist : "")
                     .setSmallIcon(com.example.tempo.R.drawable.ic_music)
                     .setContentIntent(contentIntent)
                     .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // show on lockscreen
                     .setShowWhen(false)
                     .setOnlyAlertOnce(true)
                     .setSilent(true)
                     .setOngoing(true) // keep as ongoing playback notification
                     .setAutoCancel(false)
                     .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                     .setTicker(title != null ? title : "");

            // Add actions in order and keep track of their indexes
            java.util.ArrayList<Integer> compact = new java.util.ArrayList<>();
            int index = 0;
            if (pendingIntentPrevious != null) {
                builder.addAction(drw_prev, "Previous", pendingIntentPrevious);
                compact.add(index);
                index++;
            }

            // play/pause action always present
            builder.addAction(playButton, isPlaying ? "Pause" : "Play", pendingIntentPlay);
            compact.add(index);
            index++;

            if (pendingIntentNext != null) {
                builder.addAction(drw_next, "Next", pendingIntentNext);
                compact.add(index);
            }

            int[] compactArray = new int[compact.size()];
            for (int i = 0; i < compact.size(); i++) compactArray[i] = compact.get(i);

            builder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(compactArray)
                    .setMediaSession(mediaSessionCompat.getSessionToken()));

            // If we have valid duration we can show a progress bar that represents seek position
            if (durationMs > 0) {
                // Android notification progress requires ints
                int dur = (int) Math.min(Integer.MAX_VALUE, durationMs);
                int posInt = (int) Math.max(0, Math.min(dur, currentPositionMs));
                builder.setProgress(dur, posInt, false);
            }

            notification = builder.setPriority(NotificationCompat.PRIORITY_HIGH).build();

            // Ensure we have POST_NOTIFICATIONS permission on Android 13+ before notifying
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    managerCompat.notify(1, notification);
                }
            } else {
                managerCompat.notify(1, notification);
            }
    }
}
