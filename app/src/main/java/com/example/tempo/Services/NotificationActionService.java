package com.example.tempo.Services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import com.example.tempo.ui.CreateMusicNotification;
import com.example.tempo.ui.MusicPlayerActivity;
import com.example.tempo.ui.MainActivity;

public class NotificationActionService extends BroadcastReceiver {

    private static final String TAG = "NotifActionSvc"; // <=23 chars

    public static final String ACTION_UNIFIED = "com.example.tempo.ACTION_NOTIFICATION_COMMAND";
    public static final String EXTRA_COMMAND = "command";
    public static final String EXTRA_SEEK_POSITION = "seek_position";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        if (action == null) return;

        // Try to handle actions directly if player state is available (app may be backgrounded)
        boolean handledDirectly = false;

        // Use static references where possible
        MediaPlayer sharedPlayer = MainActivity.mediaPlayer;

        try {
            switch (action) {
                case CreateMusicNotification.BUTTONPLAY: {
                    if (sharedPlayer != null) {
                        if (sharedPlayer.isPlaying()) {
                            sharedPlayer.pause();
                            Log.d(TAG, "Paused playback via notification");
                        } else {
                            sharedPlayer.start();
                            Log.d(TAG, "Resumed playback via notification");
                        }

                        // Update notification using available metadata
                        String currentTitle = "";
                        int pos = 0;
                        int size = 0;
                        if (MusicPlayerActivity.mySongs != null && MusicPlayerActivity.position >= 0 && MusicPlayerActivity.position < MusicPlayerActivity.mySongs.size()) {
                            currentTitle = MusicPlayerActivity.mySongs.get(MusicPlayerActivity.position).getName().replace(".mp3", "").replace(".wav", "");
                            pos = MusicPlayerActivity.position;
                            size = MusicPlayerActivity.mySongs.size();
                        }

                        CreateMusicNotification.createNotification(context, currentTitle, "",
                                sharedPlayer.isPlaying() ? com.example.tempo.R.drawable.ic_pause_icon : com.example.tempo.R.drawable.ic_play_icon,
                                pos, size, sharedPlayer.getDuration(), sharedPlayer.getCurrentPosition(), sharedPlayer.isPlaying());
                        handledDirectly = true;
                    }
                    break;
                }
                case CreateMusicNotification.SKIPSONGNEXT: {
                    if (MusicPlayerActivity.mySongs != null && !MusicPlayerActivity.mySongs.isEmpty()) {
                        int size = MusicPlayerActivity.mySongs.size();
                        MusicPlayerActivity.position = (MusicPlayerActivity.position + 1) % size;

                        if (sharedPlayer != null) {
                            sharedPlayer.stop();
                            sharedPlayer.release();
                        }

                        Uri uri = Uri.parse(MusicPlayerActivity.mySongs.get(MusicPlayerActivity.position).toString());
                        MediaPlayer newPlayer = MediaPlayer.create(context, uri);
                        if (newPlayer != null) {
                            newPlayer.start();
                            MainActivity.mediaPlayer = newPlayer; // update shared reference

                            String newTitle = MusicPlayerActivity.mySongs.get(MusicPlayerActivity.position).getName().replace(".mp3", "").replace(".wav", "");
                            CreateMusicNotification.createNotification(context, newTitle, "", com.example.tempo.R.drawable.ic_pause_icon,
                                    MusicPlayerActivity.position, size, newPlayer.getDuration(), newPlayer.getCurrentPosition(), true);
                            handledDirectly = true;
                        } else {
                            Log.w(TAG, "Could not create MediaPlayer for next track");
                        }
                    }
                    break;
                }
                case CreateMusicNotification.SKIPSONGPREV: {
                    if (MusicPlayerActivity.mySongs != null && !MusicPlayerActivity.mySongs.isEmpty()) {
                        int size = MusicPlayerActivity.mySongs.size();
                        MusicPlayerActivity.position = (MusicPlayerActivity.position - 1) < 0 ? (size - 1) : (MusicPlayerActivity.position - 1);

                        if (sharedPlayer != null) {
                            sharedPlayer.stop();
                            sharedPlayer.release();
                        }

                        Uri uriPrev = Uri.parse(MusicPlayerActivity.mySongs.get(MusicPlayerActivity.position).toString());
                        MediaPlayer prevPlayer = MediaPlayer.create(context, uriPrev);
                        if (prevPlayer != null) {
                            prevPlayer.start();
                            MainActivity.mediaPlayer = prevPlayer;

                            String newTitlePrev = MusicPlayerActivity.mySongs.get(MusicPlayerActivity.position).getName().replace(".mp3", "").replace(".wav", "");
                            CreateMusicNotification.createNotification(context, newTitlePrev, "", com.example.tempo.R.drawable.ic_pause_icon,
                                    MusicPlayerActivity.position, size, prevPlayer.getDuration(), prevPlayer.getCurrentPosition(), true);
                            handledDirectly = true;
                        } else {
                            Log.w(TAG, "Could not create MediaPlayer for previous track");
                        }
                    }
                    break;
                }
                case "seek_to": {
                    long pos = intent.getLongExtra(EXTRA_SEEK_POSITION, -1);
                    if (pos >= 0 && sharedPlayer != null) {
                        sharedPlayer.seekTo((int) pos);
                        String title = "";
                        int size = 0;
                        if (MusicPlayerActivity.mySongs != null && MusicPlayerActivity.position >= 0 && MusicPlayerActivity.position < MusicPlayerActivity.mySongs.size()) {
                            title = MusicPlayerActivity.mySongs.get(MusicPlayerActivity.position).getName().replace(".mp3", "").replace(".wav", "");
                            size = MusicPlayerActivity.mySongs.size();
                        }
                        CreateMusicNotification.createNotification(context, title, "", sharedPlayer.isPlaying() ? com.example.tempo.R.drawable.ic_pause_icon : com.example.tempo.R.drawable.ic_play_icon,
                                MusicPlayerActivity.position, size, sharedPlayer.getDuration(), sharedPlayer.getCurrentPosition(), sharedPlayer.isPlaying());
                        handledDirectly = true;
                    }
                    break;
                }
                default:
                    // nothing handled directly for this action
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling notification action", e);
        }

        // If we couldn't handle the command directly, re-broadcast so UI components can pick it up
        if (!handledDirectly) {
            Intent broadcast = new Intent(ACTION_UNIFIED);
            switch (action) {
                case CreateMusicNotification.BUTTONPLAY:
                    broadcast.putExtra(EXTRA_COMMAND, "play_pause");
                    break;
                case CreateMusicNotification.SKIPSONGNEXT:
                    broadcast.putExtra(EXTRA_COMMAND, "next");
                    break;
                case CreateMusicNotification.SKIPSONGPREV:
                    broadcast.putExtra(EXTRA_COMMAND, "previous");
                    break;
                case "seek_to": {
                    broadcast.putExtra(EXTRA_COMMAND, "seek_to");
                    long p = intent.getLongExtra(EXTRA_SEEK_POSITION, -1);
                    broadcast.putExtra(EXTRA_SEEK_POSITION, p);
                    break;
                }
                default:
                    broadcast.putExtra(EXTRA_COMMAND, action);
                    break;
            }
            context.sendBroadcast(broadcast);
        }
    }
}
