package com.example.tempo.Services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NotificationActionService extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        context.sendBroadcast(new Intent("SONG_CURRENTSONG")
                .putExtra("actionname", intent.getAction()));
    }
}
