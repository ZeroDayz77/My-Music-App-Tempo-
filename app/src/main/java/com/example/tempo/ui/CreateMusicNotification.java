package com.example.tempo.ui;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.media.session.MediaSessionCompat;
import android.Manifest;
import android.content.pm.PackageManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.tempo.Services.NotificationActionService;

public class CreateMusicNotification extends AppCompatActivity {

    public static final String CHANNEL_ID = "channel1";
    public static final String SKIPSONGNEXT = "skip_song_next";
    public static final String SKIPSONGPREV = "skip_song_prev";
    public static final String BUTTONPLAY = "button_play";
    public static Notification notification;

    public static void createNotification(Context context, String currentSong, int playButton, int pos, int size) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManagerCompat managerCompat = NotificationManagerCompat.from(context);
            MediaSessionCompat mediaSessionCompat = new MediaSessionCompat(context, "tag");

            PendingIntent pendingIntentPrevious;
            int drw_prev;
            if (pos == 0) {
                pendingIntentPrevious = null;
                drw_prev = 0;
            } else {
                Intent intentPrevious = new Intent(context, NotificationActionService.class)
                        .setAction(SKIPSONGPREV);
                pendingIntentPrevious = PendingIntent.getBroadcast(
                        context, 0, intentPrevious, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                drw_prev = com.example.tempo.R.drawable.ic_skip_previous_icon;
            }

            Intent intentPlay = new Intent(context, NotificationActionService.class)
                    .setAction(BUTTONPLAY);
            PendingIntent pendingIntentPlay = PendingIntent.getBroadcast(
                    context, 0, intentPlay, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            PendingIntent pendingIntentNext;
            int drw_next;
            if (pos == size) {
                pendingIntentNext = null;
                drw_next = 0;
            } else {
                Intent intentNext = new Intent(context, NotificationActionService.class)
                        .setAction(SKIPSONGNEXT);
                pendingIntentNext = PendingIntent.getBroadcast(
                        context, 0, intentNext, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                drw_next = com.example.tempo.R.drawable.ic_skip_next_icon;
            }

            // Notification creation
            notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle(context.getString(com.example.tempo.R.string.app_name))
                    .setContentText("Currently Playing: " + currentSong)
                    .setSmallIcon(com.example.tempo.R.drawable.ic_music)
                    .setShowWhen(false)
                    .setOnlyAlertOnce(true)
                    .setSilent(true)
                    .setAutoCancel(true)
                    .addAction(drw_prev, "Previous", pendingIntentPrevious)
                    .addAction(playButton, "Play", pendingIntentPlay)
                    .addAction(drw_next, "Next", pendingIntentNext)
                    .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                            .setShowActionsInCompactView(0, 1, 2)
                            .setMediaSession(mediaSessionCompat.getSessionToken()))
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();

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
}
