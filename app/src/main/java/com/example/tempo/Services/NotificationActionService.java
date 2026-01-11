package com.example.tempo.Services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class NotificationActionService extends BroadcastReceiver {

    public static final String ACTION_UNIFIED = "com.example.tempo.ACTION_NOTIFICATION_COMMAND";
    public static final String EXTRA_COMMAND = "command";
    public static final String EXTRA_SEEK_POSITION = "seek_position";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        Intent svc = new Intent(context, MediaPlaybackService.class);

        switch (action) {
            case MediaPlaybackService.BUTTONPLAY:
                svc.setAction(MediaPlaybackService.ACTION_TOGGLE);
                break;
            case MediaPlaybackService.SKIPSONGNEXT:
                svc.setAction(MediaPlaybackService.ACTION_NEXT);
                break;
            case MediaPlaybackService.SKIPSONGPREV:
                svc.setAction(MediaPlaybackService.ACTION_PREV);
                break;
            case "seek_to": {
                long pos = intent.getLongExtra(EXTRA_SEEK_POSITION, -1);
                svc.setAction(MediaPlaybackService.ACTION_SEEK);
                svc.putExtra(MediaPlaybackService.EXTRA_SEEK_POSITION, pos);
                break;
            }
            default:
                // Unknown action: ignore
                return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc);
        } else {
            context.startService(svc);
        }
    }
}
