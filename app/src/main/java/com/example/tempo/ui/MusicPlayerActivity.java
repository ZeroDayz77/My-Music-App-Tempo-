package com.example.tempo.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.Toolbar;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.example.tempo.Services.MediaPlaybackService;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.MediaBrowserCompat.ConnectionCallback;

public class MusicPlayerActivity extends AppCompatActivity implements com.example.tempo.ui.Playable {
    AppCompatButton buttonPlay, skipSongNext, skipSongPrev, buttonShuffle, buttonRepeat;
    TextView songNameText, songStartTime, songEndTime;
    SeekBar seekbar;
    ImageView songImageView;
    String songName;
    // Activity no longer owns MediaPlayer; controller will talk to the service
    MediaPlayer mediaPlayer; // kept for backward compatibility in some code paths but not created here
    MediaBrowserCompat mediaBrowser;
    MediaControllerCompat mediaController;
    public static int position;
    public static boolean isShuffleToggled;
    public static ArrayList<File> mySongs;
    // seekbar updater uses a Handler + Runnable instead of a Thread
    Handler seekHandler;
    Runnable seekRunnable;
    public static Bundle bundle;

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId()==android.R.id.home)
        {
            onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        setContentView(com.example.tempo.R.layout.activity_music_player);

        Toolbar toolBar = findViewById(com.example.tempo.R.id.tempoToolBar);
        setSupportActionBar(toolBar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("Now Playing");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        buttonPlay = findViewById(com.example.tempo.R.id.buttonplay);
        skipSongNext = findViewById(com.example.tempo.R.id.skipsongnext);
        skipSongPrev = findViewById(com.example.tempo.R.id.skipsongprev);
        buttonRepeat = findViewById(com.example.tempo.R.id.buttonrepeat);
        buttonShuffle = findViewById(com.example.tempo.R.id.buttonshuffle);
        songImageView = findViewById(com.example.tempo.R.id.songimageview);

        songNameText = findViewById(com.example.tempo.R.id.songnametext);
        songStartTime = findViewById(com.example.tempo.R.id.songstarttime);
        songEndTime = findViewById(com.example.tempo.R.id.songendtime);

        seekbar = findViewById(com.example.tempo.R.id.seekbar);

        // Release any previous player instance
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        // Create notification channel on O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(MediaPlaybackService.CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = getSystemService(NotificationManager.class);
            channel.enableVibration(false);
            channel.setSound(null, null);
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        // Safely load songs passed via Intent extras (supports Serializable fallback)
        Intent i = getIntent();
        bundle = i.getExtras();
        String passedSongName = null;
        // Support alternate keys from other activities
        String altSongName = i.getStringExtra("song_name");
        String altSongUri = i.getStringExtra("song_uri");

        if (bundle != null) {
            // prefer explicit song_name (from playlist) over songname
            passedSongName = altSongName != null && !altSongName.isEmpty() ? altSongName : bundle.getString("songname");

            Object songsObj = bundle.get("songs");
            if (songsObj instanceof ArrayList) {
                @SuppressWarnings("unchecked")
                ArrayList<File> tmp = (ArrayList<File>) songsObj;
                mySongs = tmp;
            } else {
                java.io.Serializable serial = bundle.getSerializable("songs");
                if (serial instanceof ArrayList) {
                    @SuppressWarnings("unchecked")
                    ArrayList<File> tmp = (ArrayList<File>) serial;
                    mySongs = tmp;
                } else if (altSongUri != null && !altSongUri.isEmpty()) {
                    // build a single-item playlist from the passed URI string
                    mySongs = new ArrayList<>();
                    mySongs.add(new File(altSongUri));
                } else {
                    mySongs = new ArrayList<>();
                }
            }
            position = bundle.getInt("pos", 0);
        } else if (altSongUri != null) {
            // activity started with only a URI (playlist details)
            mySongs = new ArrayList<>();
            mySongs.add(new File(altSongUri));
            passedSongName = altSongName != null ? altSongName : new File(altSongUri).getName();
            position = 0;
        } else {
            mySongs = new ArrayList<>();
            position = 0;
        }

        songNameText.setSelected(true);

        // If caller provided song name string, show it immediately to avoid placeholder on first tap
        if (passedSongName != null && !passedSongName.isEmpty()) {
            // If the caller passed a file path (e.g. File.toString()), extract the file name only
            String displayName = passedSongName;
            try {
                java.io.File possibleFile = new java.io.File(passedSongName);
                String nameOnly = possibleFile.getName();
                if (!nameOnly.isEmpty()) displayName = nameOnly;
            } catch (Exception ignored) {
            }

            displayName = displayName.replace(".mp3", "").replace(".wav", "");
            songName = displayName;
            songNameText.setText(songName);
        }

        // We don't create a MediaPlayer here. The MediaPlaybackService owns playback.
        if (mySongs.isEmpty()) {
            if (songName == null) songName = "";
        } else {
            if (songName == null || songName.isEmpty()) {
                songName = mySongs.get(position).getName().replace(".mp3", "").replace(".wav", "");
                songNameText.setText(songName);
            }
            // The service will manage playback and the notification. We will connect to it in onStart().
        }

        // Keep UI time labels updated
        final Handler timeHandler = new Handler(Looper.getMainLooper());
        final int timeDelay = 500;
        timeHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mediaController != null) {
                        PlaybackStateCompat state = mediaController.getPlaybackState();
                        MediaMetadataCompat meta = mediaController.getMetadata();
                        if (state != null) {
                            long cur = state.getPosition();
                            songStartTime.setText(createSongTime((int) cur));
                        }
                        if (meta != null) {
                            long dur = meta.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
                            songEndTime.setText(createSongTime((int) dur));
                        }
                    }
                } catch (RuntimeException e) {
                    Log.w("MusicPlayerActivity", "time handler safe-guard: controller unusable", e);
                }
                timeHandler.postDelayed(this, timeDelay);
            }
        }, timeDelay);

        // Play/pause, skip, shuffle, repeat handlers
        buttonPlay.setOnClickListener(view -> {
            MediaControllerCompat controller = MediaControllerCompat.getMediaController(MusicPlayerActivity.this);
            if (controller != null) {
                PlaybackStateCompat state = controller.getPlaybackState();
                if (state != null && state.getState() == PlaybackStateCompat.STATE_PLAYING) {
                    controller.getTransportControls().pause();
                } else {
                    controller.getTransportControls().play();
                }
            }
        });

        skipSongNext.setOnClickListener(view -> {
            MediaControllerCompat controller = MediaControllerCompat.getMediaController(MusicPlayerActivity.this);
            if (controller != null) controller.getTransportControls().skipToNext();
        });

        skipSongPrev.setOnClickListener(view -> {
            MediaControllerCompat controller = MediaControllerCompat.getMediaController(MusicPlayerActivity.this);
            if (controller != null) controller.getTransportControls().skipToPrevious();
        });

        buttonShuffle.setOnClickListener(view -> {
            if (!isShuffleToggled) {
                isShuffleToggled = true;
                buttonShuffle.setBackgroundResource(com.example.tempo.R.drawable.ic_shuffle_selected_icon);
            } else {
                isShuffleToggled = false;
                buttonShuffle.setBackgroundResource(com.example.tempo.R.drawable.ic_shuffle_icon);
            }
        });

        buttonRepeat.setOnClickListener(view -> {
            if (mediaPlayer != null) {
                if (mediaPlayer.isLooping()) {
                    mediaPlayer.setLooping(false);
                    buttonRepeat.setBackgroundResource(com.example.tempo.R.drawable.ic_repeat_icon);
                } else {
                    mediaPlayer.setLooping(true);
                    buttonRepeat.setBackgroundResource(com.example.tempo.R.drawable.ic_repeat_selected_icon);
                }
            }
        });

        BottomNavigationView bottomNavigationView = findViewById(com.example.tempo.R.id.bottomToolBar);
        bottomNavigationView.setSelectedItemId(com.example.tempo.R.id.songPlayingButton);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == com.example.tempo.R.id.songLibraryButton) {
                startActivity(new Intent(getApplicationContext(), com.example.tempo.ui.MainActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == com.example.tempo.R.id.songPlayingButton) {
                return true;
            } else if (id == com.example.tempo.R.id.playlistButton) {
                startActivity(new Intent(getApplicationContext(), com.example.tempo.ui.PlaylistsActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }

    private void startSeekbarUpdateThread() {
        // Use MediaController's playback state/metadata to update seekbar
        // Ensure any previous handler is cleared
        stopSeekbarUpdate();

        seekHandler = new Handler(Looper.getMainLooper());
        seekRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (mediaController != null) {
                        PlaybackStateCompat state = mediaController.getPlaybackState();
                        MediaMetadataCompat meta = mediaController.getMetadata();
                        if (meta != null) {
                            int dur = (int) meta.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
                            seekbar.setMax(dur);
                            songEndTime.setText(createSongTime(dur));
                            // enable seekbar only when we have a valid duration
                            seekbar.setEnabled(dur > 0);
                        }
                        if (state != null) {
                            int pos = (int) state.getPosition();
                            seekbar.setProgress(pos);
                        }
                    }
                } catch (RuntimeException e) {
                    Log.w("MusicPlayerActivity", "seekbar updater safe-guard: controller unusable", e);
                }
                if (seekHandler != null) seekHandler.postDelayed(this, 500);
            }
        };

        // Start periodic updates
        // Ensure the seekbar is interactive right away (even if metadata not available yet)
        seekbar.setEnabled(true);
        // Run one immediate update to initialize UI right away
        seekHandler.post(seekRunnable);

        seekbar.getProgressDrawable().setColorFilter(getResources().getColor(com.example.tempo.R.color.white), PorterDuff.Mode.MULTIPLY);
        seekbar.getThumb().setColorFilter(getResources().getColor(com.example.tempo.R.color.teal_700), PorterDuff.Mode.SRC_IN);

        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
             @Override
             public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

             }

             @Override
             public void onStartTrackingTouch(SeekBar seekBar) {

             }

             @Override
             public void onStopTrackingTouch(SeekBar seekBar) {
                int newPos = seekBar.getProgress();
                // If we have a media controller, use it. Otherwise, forward to the service so the action still works.
                if (mediaController != null) {
                    try {
                        mediaController.getTransportControls().seekTo(newPos);
                    } catch (RuntimeException e) {
                        Log.w("MusicPlayerActivity", "seek safe-guard: controller unusable", e);
                    }
                } else {
                    // Send a service intent to perform the seek. Service should already be running when activity was opened.
                    try {
                        Intent intent = new Intent(getApplicationContext(), com.example.tempo.Services.MediaPlaybackService.class)
                                .setAction(com.example.tempo.Services.MediaPlaybackService.ACTION_SEEK)
                                .putExtra(com.example.tempo.Services.MediaPlaybackService.EXTRA_SEEK_POSITION, (long) newPos);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            androidx.core.content.ContextCompat.startForegroundService(getApplicationContext(), intent);
                        } else {
                            startService(intent);
                        }
                    } catch (Exception e) {
                        Log.w("MusicPlayerActivity", "seek forwarding failed", e);
                    }
                }
                // update UI immediately so it doesn't snap back
                seekbar.setProgress(newPos);
             }
         });
     }

    private void stopSeekbarUpdate() {
        if (seekHandler != null) {
            seekHandler.removeCallbacks(seekRunnable);
            seekHandler = null;
            seekRunnable = null;
        }
    }

    public void startAnimation(View view)
    {
        ObjectAnimator animator = ObjectAnimator.ofFloat(songImageView, "rotation", 0f, 360f);
        animator.setDuration(1000);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(animator);
        animatorSet.start();
    }

    public String createSongTime(int songDuration)
    {
        String time = "";
        int min = songDuration/1000/60;
        int sec = songDuration/1000%60;

        time+=min+":";

        if (sec<10)
        {
            time+="0";
        }
        time+=sec;

        return  time;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Bundle currentSong = bundle;
        outState.putBundle("currentSongData", currentSong);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        bundle = savedInstanceState.getBundle("currentSongData");
    }

    @Override
    public void onButtonPrevious() {
        position--;
        // Ensure shared position/state is in sync
        // MainActivity.mediaPlayer is kept in sync when we create/release the player
    }

    @Override
    public void onButtonPlay() {
    }

    @Override
    public void onButtonPause() {
    }

    @Override
    public void onButtonNext() {
        position++;
    }

    @Override
    public boolean onNavigateUp() {
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        return super.onNavigateUp();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // No legacy broadcast receiver to unregister (service handles notification actions)
    }

    @Override
    protected void onResume() {
        super.onResume();
        // restart seekbar update thread if needed (use MediaController)
        if (mediaController != null) {
            if (seekHandler == null) startSeekbarUpdateThread();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop seekbar updates to avoid background thread accessing released objects
        stopSeekbarUpdate();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Disconnect from the media browser service
        if (mediaBrowser != null) {
            mediaBrowser.disconnect();
            mediaBrowser = null;
        }
        if (mediaController != null) {
            mediaController.unregisterCallback(controllerCallback);
            mediaController = null;
        }
        stopSeekbarUpdate();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Connect to the media browser service
        mediaBrowser = new MediaBrowserCompat(this, new ComponentName(this, com.example.tempo.Services.MediaPlaybackService.class),
                new ConnectionCallback() {
                    @Override
                    public void onConnected() {
                        // We are connected to the media service
                        mediaController = new MediaControllerCompat(MusicPlayerActivity.this, mediaBrowser.getSessionToken());
                        MediaControllerCompat.setMediaController(MusicPlayerActivity.this, mediaController);

                        // Update UI with current metadata
                        MediaMetadataCompat metadata = mediaController.getMetadata();
                        if (metadata != null) {
                            songName = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
                            songNameText.setText(songName);
                        }

                        // Initialize seekbar immediately so it's usable even before playback state changes
                        startSeekbarUpdateThread();

                        // Start the playback if there is a song loaded
                        if (mySongs != null && !mySongs.isEmpty()) {
                            mediaController.getTransportControls().play();
                        }

                        // Register callback to receive updates
                        mediaController.registerCallback(controllerCallback);
                    }

                    @Override
                    public void onConnectionSuspended() {
                        super.onConnectionSuspended();
                        // Connection to the media service was suspended
                        mediaController = null;
                    }

                    @Override
                    public void onConnectionFailed() {
                        super.onConnectionFailed();
                        // Connection to the media service failed
                        mediaController = null;
                    }
                }, null);
        mediaBrowser.connect();
    }

    private final MediaControllerCompat.Callback controllerCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            super.onMetadataChanged(metadata);
            if (metadata != null) {
                songName = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
                songNameText.setText(songName);
            }
        }

        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            super.onPlaybackStateChanged(state);
            if (state != null && state.getState() == PlaybackStateCompat.STATE_PLAYING) {
                buttonPlay.setBackgroundResource(com.example.tempo.R.drawable.ic_pause_icon);
                startAnimation(songImageView);
                startSeekbarUpdateThread();
            } else {
                buttonPlay.setBackgroundResource(com.example.tempo.R.drawable.ic_play_icon);
                // Keep seekbar showing current position while paused; do not stop updates here.
            }
        }
    };
}

