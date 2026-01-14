package com.example.tempo.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

public class MusicPlayerActivity extends AppCompatActivity implements com.example.tempo.ui.Playable {
    AppCompatButton buttonPlay, skipSongNext, skipSongPrev;
    AppCompatImageButton buttonShuffle, buttonRepeat;
    TextView songNameText, songStartTime, songEndTime;
    SeekBar seekbar;
    ImageView songImageView;
    String songName;
    MediaPlayer mediaPlayer;
    MediaBrowserCompat mediaBrowser;
    MediaControllerCompat mediaController;
    public static int position;
    public static boolean isShuffleToggled;
    public static ArrayList<File> mySongs;
    Handler seekHandler;
    Runnable seekRunnable;
    public static Bundle bundle;
    public static volatile boolean shouldAnimateMiniBarOnReturn = false;

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        int lyricsId = com.example.tempo.R.id.menu_lyrics;
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == lyricsId) {
            // Open LyricsActivity and pass song info
            Intent intent = new Intent(this, com.example.tempo.ui.LyricsActivity.class);
            // prefer metadata if available
            String artist = null;
            try {
                if (mediaController != null && mediaController.getMetadata() != null) {
                    artist = mediaController.getMetadata().getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
                }
            } catch (Exception ignored) {}
            intent.putExtra("song_name", songName != null ? songName : "");
            intent.putExtra("song_artist", artist != null ? artist : "");
            // pass file uri if available
            if (mySongs != null && mySongs.size() > position && position >= 0) {
                intent.putExtra("song_uri", mySongs.get(position).getAbsolutePath());
            }
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(com.example.tempo.R.menu.menu_music_player, menu);
        return true;
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

        // Hide shared mini now-playing bar when we're on the full player screen
        View nowPlayingInclude = findViewById(com.example.tempo.R.id.nowPlayingInclude);
        if (nowPlayingInclude != null) nowPlayingInclude.setVisibility(View.GONE);

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

        // Initialize shuffle/repeat icons and tints, then reflect service state
        try {
            int colorActive = ContextCompat.getColor(this, com.example.tempo.R.color.teal_700);
            int colorInactive = ContextCompat.getColor(this, com.example.tempo.R.color.white);

            // default unselected (white tint)
            buttonShuffle.setImageResource(com.example.tempo.R.drawable.ic_shuffle_icon);
            buttonShuffle.setImageTintList(ColorStateList.valueOf(colorInactive));
            buttonRepeat.setImageResource(com.example.tempo.R.drawable.ic_repeat_icon);
            buttonRepeat.setImageTintList(ColorStateList.valueOf(colorInactive));
            isShuffleToggled = false;

            // reflect service flags if active
            if (com.example.tempo.Services.MediaPlaybackService.shuffleEnabled.get()) {
                isShuffleToggled = true;
                buttonShuffle.setImageResource(com.example.tempo.R.drawable.ic_shuffle_selected_icon);
                buttonShuffle.setImageTintList(ColorStateList.valueOf(colorActive));
            }
            if (com.example.tempo.Services.MediaPlaybackService.repeatEnabled.get()) {
                buttonRepeat.setImageResource(com.example.tempo.R.drawable.ic_repeat_selected_icon);
                buttonRepeat.setImageTintList(ColorStateList.valueOf(colorActive));
            }
        } catch (Exception ignored) {}

        // Toggle shuffle via service intent so behavior is centralized
        buttonShuffle.setOnClickListener(view -> {
            Intent intent = new Intent(getApplicationContext(), com.example.tempo.Services.MediaPlaybackService.class).setAction(com.example.tempo.Services.MediaPlaybackService.ACTION_TOGGLE_SHUFFLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                androidx.core.content.ContextCompat.startForegroundService(getApplicationContext(), intent);
            } else {
                startService(intent);
            }

            isShuffleToggled = !isShuffleToggled;
            int colorActive = ContextCompat.getColor(this, com.example.tempo.R.color.teal_700);
            int colorInactive = ContextCompat.getColor(this, com.example.tempo.R.color.white);
            if (isShuffleToggled) {
                buttonShuffle.setImageResource(com.example.tempo.R.drawable.ic_shuffle_selected_icon);
                buttonShuffle.setImageTintList(ColorStateList.valueOf(colorActive));
            } else {
                buttonShuffle.setImageResource(com.example.tempo.R.drawable.ic_shuffle_icon);
                buttonShuffle.setImageTintList(ColorStateList.valueOf(colorInactive));
            }
            // small press animation to make change obvious
            buttonShuffle.setScaleX(0.95f);
            buttonShuffle.setScaleY(0.95f);
            buttonShuffle.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            buttonShuffle.invalidate();
            buttonShuffle.requestLayout();
        });

        buttonRepeat.setOnClickListener(view -> {
            // Toggle repeat via service intent
            Intent intent = new Intent(getApplicationContext(), com.example.tempo.Services.MediaPlaybackService.class).setAction(com.example.tempo.Services.MediaPlaybackService.ACTION_TOGGLE_REPEAT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                androidx.core.content.ContextCompat.startForegroundService(getApplicationContext(), intent);
            } else {
                startService(intent);
            }
            // Toggle UI immediately; service will apply looping and is authoritative
            boolean newRepeat = !(com.example.tempo.Services.MediaPlaybackService.repeatEnabled.get());
            int colorActive = ContextCompat.getColor(this, com.example.tempo.R.color.teal_700);
            int colorInactive = ContextCompat.getColor(this, com.example.tempo.R.color.white);
            if (newRepeat) {
                buttonRepeat.setImageResource(com.example.tempo.R.drawable.ic_repeat_selected_icon);
                buttonRepeat.setImageTintList(ColorStateList.valueOf(colorActive));
            } else {
                buttonRepeat.setImageResource(com.example.tempo.R.drawable.ic_repeat_icon);
                buttonRepeat.setImageTintList(ColorStateList.valueOf(colorInactive));
            }
            // small press animation
            buttonRepeat.setScaleX(0.95f);
            buttonRepeat.setScaleY(0.95f);
            buttonRepeat.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            buttonRepeat.invalidate();
            buttonRepeat.requestLayout();
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
        // Notify other activities to animate the mini bar when they become visible again
        shouldAnimateMiniBarOnReturn = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unregister receiver for playback options
        try { unregisterReceiver(playbackOptionsReceiver); } catch (Exception ignored) {}
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
        // Register receiver for playback options (shuffle/repeat changes)
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(com.example.tempo.Services.MediaPlaybackService.ACTION_SHUFFLE_CHANGED);
            filter.addAction(com.example.tempo.Services.MediaPlaybackService.ACTION_REPEAT_CHANGED);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(playbackOptionsReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(playbackOptionsReceiver, filter);
            }
        } catch (Exception ignored) {}
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
            runOnUiThread(() -> {
                if (state != null && state.getState() == PlaybackStateCompat.STATE_PLAYING) {
                    buttonPlay.setBackgroundResource(com.example.tempo.R.drawable.ic_pause_icon);
                    startAnimation(songImageView);
                    startSeekbarUpdateThread();
                } else {
                    buttonPlay.setBackgroundResource(com.example.tempo.R.drawable.ic_play_icon);
                    // Keep seekbar showing current position while paused; do not stop updates here.
                }

                // Also refresh shuffle/repeat visuals from authoritative service state
                try {
                    boolean shuffleOn = com.example.tempo.Services.MediaPlaybackService.shuffleEnabled.get();
                    boolean repeatOn = com.example.tempo.Services.MediaPlaybackService.repeatEnabled.get();
                    int colorActive = ContextCompat.getColor(MusicPlayerActivity.this, com.example.tempo.R.color.teal_700);
                    int colorInactive = ContextCompat.getColor(MusicPlayerActivity.this, com.example.tempo.R.color.white);
                    buttonShuffle.setImageResource(shuffleOn ? com.example.tempo.R.drawable.ic_shuffle_selected_icon : com.example.tempo.R.drawable.ic_shuffle_icon);
                    buttonShuffle.setImageTintList(ColorStateList.valueOf(shuffleOn ? colorActive : colorInactive));
                    buttonRepeat.setImageResource(repeatOn ? com.example.tempo.R.drawable.ic_repeat_selected_icon : com.example.tempo.R.drawable.ic_repeat_icon);
                    buttonRepeat.setImageTintList(ColorStateList.valueOf(repeatOn ? colorActive : colorInactive));
                } catch (Exception ignored) {}
            });
         }
     };

    // Register/unregister receiver for shuffle/repeat changes
    private final BroadcastReceiver playbackOptionsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                String action = intent.getAction();
                if (com.example.tempo.Services.MediaPlaybackService.ACTION_SHUFFLE_CHANGED.equals(action)) {
                    // Update shuffle button state
                    boolean shuffleEnabled = com.example.tempo.Services.MediaPlaybackService.shuffleEnabled.get();
                    buttonShuffle.setImageResource(shuffleEnabled ? com.example.tempo.R.drawable.ic_shuffle_selected_icon : com.example.tempo.R.drawable.ic_shuffle_icon);
                    int colorActive = ContextCompat.getColor(context, com.example.tempo.R.color.teal_700);
                    int colorInactive = ContextCompat.getColor(context, com.example.tempo.R.color.white);
                    buttonShuffle.setImageTintList(ColorStateList.valueOf(shuffleEnabled ? colorActive : colorInactive));
                } else if (com.example.tempo.Services.MediaPlaybackService.ACTION_REPEAT_CHANGED.equals(action)) {
                    // Update repeat button state
                    boolean repeatEnabled = com.example.tempo.Services.MediaPlaybackService.repeatEnabled.get();
                    buttonRepeat.setImageResource(repeatEnabled ? com.example.tempo.R.drawable.ic_repeat_selected_icon : com.example.tempo.R.drawable.ic_repeat_icon);
                    int colorActive = ContextCompat.getColor(context, com.example.tempo.R.color.teal_700);
                    int colorInactive = ContextCompat.getColor(context, com.example.tempo.R.color.white);
                    buttonRepeat.setImageTintList(ColorStateList.valueOf(repeatEnabled ? colorActive : colorInactive));
                }
            }
        }
    };
}
