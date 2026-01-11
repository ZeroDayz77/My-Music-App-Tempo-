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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.annotation.SuppressLint;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;

import com.example.tempo.Services.NotificationActionService;

public class MusicPlayerActivity extends AppCompatActivity implements com.example.tempo.ui.Playable {
    AppCompatButton buttonPlay, skipSongNext, skipSongPrev, buttonShuffle, buttonRepeat;
    TextView songNameText, songStartTime, songEndTime;
    SeekBar seekbar;
    ImageView songImageView;
    String songName;
    MediaPlayer mediaPlayer;
    public static int position;
    public static boolean isShuffleToggled;
    public static ArrayList<File> mySongs;
    // seekbar updater uses a Handler + Runnable instead of a Thread
    Handler seekHandler;
    Runnable seekRunnable;
    public static Bundle bundle;

    // BroadcastReceiver will be registered in onCreate
    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            // Always refresh local reference to the shared player (NotificationActionService may have changed it)
            mediaPlayer = com.example.tempo.ui.MainActivity.mediaPlayer;

            // Handle unified actions from NotificationActionService
            if (NotificationActionService.ACTION_UNIFIED.equals(intent.getAction())) {
                String cmd = intent.getStringExtra(NotificationActionService.EXTRA_COMMAND);
                if (cmd == null) return;

                switch (cmd) {
                    case "previous":
                        onButtonPrevious();
                        break;
                    case "play_pause":
                        if (mediaPlayer != null) {
                            if (mediaPlayer.isPlaying()) {
                                mediaPlayer.pause();
                                buttonPlay.setBackgroundResource(com.example.tempo.R.drawable.ic_play_icon);
                                onButtonPause();
                            } else {
                                mediaPlayer.start();
                                buttonPlay.setBackgroundResource(com.example.tempo.R.drawable.ic_pause_icon);
                                onButtonPlay();
                            }
                            // Update notification with current state
                            com.example.tempo.ui.CreateMusicNotification.createNotification(MusicPlayerActivity.this,
                                    songName, "", mediaPlayer.isPlaying() ? com.example.tempo.R.drawable.ic_pause_icon : com.example.tempo.R.drawable.ic_play_icon,
                                    position, mySongs.size(), mediaPlayer.getDuration(), mediaPlayer.getCurrentPosition(), mediaPlayer.isPlaying());
                        }
                        break;
                    case "next":
                        onButtonNext();
                        break;
                    case "seek_to":
                        long seekPos = intent.getLongExtra(NotificationActionService.EXTRA_SEEK_POSITION, -1);
                        if (seekPos >= 0 && mediaPlayer != null) {
                            mediaPlayer.seekTo((int) seekPos);
                            // update notification progress
                            com.example.tempo.ui.CreateMusicNotification.createNotification(MusicPlayerActivity.this,
                                    songName, "", mediaPlayer.isPlaying() ? com.example.tempo.R.drawable.ic_pause_icon : com.example.tempo.R.drawable.ic_play_icon,
                                    position, mySongs.size(), mediaPlayer.getDuration(), mediaPlayer.getCurrentPosition(), mediaPlayer.isPlaying());
                        }
                        break;
                }
                return;
            }

            // Legacy behavior fallback (if some other component uses it)
            Bundle extras = intent.getExtras();
            if (extras == null) return;
            String action = extras.getString("actionname");
            if (action == null) return;

            switch (Objects.requireNonNull(action)) {
                case com.example.tempo.ui.CreateMusicNotification.SKIPSONGPREV:
                    onButtonPrevious();
                    break;
                case com.example.tempo.ui.CreateMusicNotification.BUTTONPLAY:
                    if (mediaPlayer != null && mediaPlayer.isPlaying())
                        onButtonPause();
                    else
                        onButtonPlay();
                    break;
                case com.example.tempo.ui.CreateMusicNotification.SKIPSONGNEXT:
                    onButtonNext();
                    break;
            }
        }
    };

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
            NotificationChannel channel = new NotificationChannel(com.example.tempo.ui.CreateMusicNotification.CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_HIGH);
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
                if (nameOnly != null && !nameOnly.isEmpty()) displayName = nameOnly;
            } catch (Exception ignored) {
            }

            displayName = displayName.replace(".mp3", "").replace(".wav", "");
            songName = displayName;
            songNameText.setText(songName);
        }

        if (mySongs.isEmpty()) {
            if (songName == null) songName = "";
        } else {
            Uri uri = Uri.parse(mySongs.get(position).toString());
            // Only overwrite songName if we didn't already set it from the Intent string
            if (songName == null || songName.isEmpty()) {
                songName = mySongs.get(position).getName().replace(".mp3", "").replace(".wav", "");
                songNameText.setText(songName);
            }

            // Initialize and start playback
            mediaPlayer = MediaPlayer.create(getApplicationContext(), uri);
            if (mediaPlayer != null) {
                mediaPlayer.start();
                // keep shared reference for background control
                com.example.tempo.ui.MainActivity.mediaPlayer = mediaPlayer;

                // Create initial notification with metadata
                com.example.tempo.ui.CreateMusicNotification.createNotification(MusicPlayerActivity.this,
                        songName, "", com.example.tempo.R.drawable.ic_pause_icon, position, mySongs.size(), mediaPlayer.getDuration(), mediaPlayer.getCurrentPosition(), true);

                // start seekbar updater
                startSeekbarUpdateThread();

                String endTime = createSongTime(mediaPlayer.getDuration());
                songEndTime.setText(endTime);
            }
        }

        // Keep UI time labels updated
        final Handler timeHandler = new Handler();
        final int timeDelay = 500;
        timeHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    MediaPlayer mp = com.example.tempo.ui.MainActivity.mediaPlayer;
                    if (mp != null) {
                        String currentTime = createSongTime(mp.getCurrentPosition());
                        songStartTime.setText(currentTime);

                        String endTime = createSongTime(mp.getDuration());
                        songEndTime.setText(endTime);
                    }
                } catch (RuntimeException e) {
                    Log.w("MusicPlayerActivity", "time handler safe-guard: media player unusable", e);
                }
                timeHandler.postDelayed(this, timeDelay);
            }
        }, timeDelay);

        // Register receiver for notification actions (use RECEIVER_NOT_EXPORTED on Android 13+)
        int registerFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? Context.RECEIVER_NOT_EXPORTED : 0;
        IntentFilter _filter = new IntentFilter(NotificationActionService.ACTION_UNIFIED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // registerReceiver with flags is available on API 26+
            registerReceiver(broadcastReceiver, _filter, registerFlags);
        } else {
            registerReceiver(broadcastReceiver, _filter);
        }

        // Play/pause, skip, shuffle, repeat handlers
        buttonPlay.setOnClickListener(view -> {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    buttonPlay.setBackgroundResource(com.example.tempo.R.drawable.ic_play_icon);
                    mediaPlayer.pause();
                    onButtonPause();
                    com.example.tempo.ui.CreateMusicNotification.createNotification(MusicPlayerActivity.this, songName, "", com.example.tempo.R.drawable.ic_play_icon, position, mySongs.size(), mediaPlayer.getDuration(), mediaPlayer.getCurrentPosition(), false);
                } else {
                    buttonPlay.setBackgroundResource(com.example.tempo.R.drawable.ic_pause_icon);
                    mediaPlayer.start();
                    onButtonPlay();
                    com.example.tempo.ui.CreateMusicNotification.createNotification(MusicPlayerActivity.this, songName, "", com.example.tempo.R.drawable.ic_pause_icon, position, mySongs.size(), mediaPlayer.getDuration(), mediaPlayer.getCurrentPosition(), true);
                }
            }
        });

        skipSongNext.setOnClickListener(view -> {
            if (mediaPlayer != null && !mySongs.isEmpty()) {
                mediaPlayer.stop();
                mediaPlayer.release();
                com.example.tempo.ui.MainActivity.mediaPlayer = null;
                stopSeekbarUpdate();
                position = (position + 1) % mySongs.size();
                Uri u = Uri.parse(mySongs.get(position).toString());
                mediaPlayer = MediaPlayer.create(getApplicationContext(), u);
                com.example.tempo.ui.MainActivity.mediaPlayer = mediaPlayer;
                songName = mySongs.get(position).getName().replace(".mp3", "").replace(".wav", "");
                songNameText.setText(songName);
                if (mediaPlayer != null) {
                    mediaPlayer.start();
                    buttonPlay.setBackgroundResource(com.example.tempo.R.drawable.ic_pause_icon);
                    startAnimation(songImageView);
                    startSeekbarUpdateThread();
                    onButtonNext();
                    com.example.tempo.ui.CreateMusicNotification.createNotification(MusicPlayerActivity.this, songName, "", com.example.tempo.R.drawable.ic_pause_icon, position, mySongs.size(), mediaPlayer.getDuration(), mediaPlayer.getCurrentPosition(), true);
                }
            }
        });

        skipSongPrev.setOnClickListener(view -> {
            if (mediaPlayer != null && !mySongs.isEmpty()) {
                mediaPlayer.stop();
                mediaPlayer.release();
                com.example.tempo.ui.MainActivity.mediaPlayer = null;
                stopSeekbarUpdate();
                position = (position - 1) < 0 ? (mySongs.size() - 1) : (position - 1);
                Uri u = Uri.parse(mySongs.get(position).toString());
                mediaPlayer = MediaPlayer.create(getApplicationContext(), u);
                com.example.tempo.ui.MainActivity.mediaPlayer = mediaPlayer;
                songName = mySongs.get(position).getName().replace(".mp3", "").replace(".wav", "");
                songNameText.setText(songName);
                if (mediaPlayer != null) {
                    mediaPlayer.start();
                    buttonPlay.setBackgroundResource(com.example.tempo.R.drawable.ic_pause_icon);
                    startAnimation(songImageView);
                    startSeekbarUpdateThread();
                    onButtonPrevious();
                    com.example.tempo.ui.CreateMusicNotification.createNotification(MusicPlayerActivity.this, songName, "", com.example.tempo.R.drawable.ic_pause_icon, position, mySongs.size(), mediaPlayer.getDuration(), mediaPlayer.getCurrentPosition(), true);
                }
            }
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

        mediaPlayer.setOnCompletionListener(mp -> {
            if (isShuffleToggled) {
                mp.stop();
                mp.release();
                Random random = new Random();
                int upperbound = mySongs.size();
                position = (position + random.nextInt(upperbound));
                Uri u = Uri.parse(mySongs.get(position).toString());
                mediaPlayer = MediaPlayer.create(getApplicationContext(), u);
                songName = mySongs.get(position).getName().replace(".mp3", "").replace(".wav", "");
                songNameText.setText(songName);
                if (mediaPlayer != null) {
                    mediaPlayer.start();
                    buttonPlay.setBackgroundResource(com.example.tempo.R.drawable.ic_pause_icon);
                    startAnimation(songImageView);
                    startSeekbarUpdateThread();
                }
            } else {
                skipSongNext.performClick();
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
        // If no media player available, don't start the updater
        if (com.example.tempo.ui.MainActivity.mediaPlayer == null) return;

        // Ensure any previous handler is cleared
        stopSeekbarUpdate();

        seekHandler = new Handler(Looper.getMainLooper());
        seekRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    MediaPlayer mp = com.example.tempo.ui.MainActivity.mediaPlayer;
                    if (mp != null) {
                        int totalSongDuration = mp.getDuration();
                        int currentSongPosition = mp.getCurrentPosition();
                        seekbar.setMax(totalSongDuration);
                        seekbar.setProgress(currentSongPosition);
                        // update notification progress in lockscreen / notification shade
                        com.example.tempo.ui.CreateMusicNotification.createNotification(MusicPlayerActivity.this,
                                songName, "", mp.isPlaying() ? com.example.tempo.R.drawable.ic_pause_icon : com.example.tempo.R.drawable.ic_play_icon,
                                position, mySongs != null ? mySongs.size() : 0, mp.getDuration(), mp.getCurrentPosition(), mp.isPlaying());
                    }
                } catch (RuntimeException e) {
                    Log.w("MusicPlayerActivity", "seekbar updater safe-guard: media player unusable", e);
                }
                // Re-post after delay
                if (seekHandler != null) seekHandler.postDelayed(this, 500);
            }
        };

        // Set initial max safely
        MediaPlayer initial = com.example.tempo.ui.MainActivity.mediaPlayer;
        if (initial != null) {
            seekbar.setMax(initial.getDuration());
            // also refresh local ref
            mediaPlayer = initial;
        }

        // Start periodic updates
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
                MediaPlayer mp = com.example.tempo.ui.MainActivity.mediaPlayer;
                if (mp != null) {
                    try {
                        mp.seekTo(seekBar.getProgress());
                    } catch (RuntimeException e) {
                        Log.w("MusicPlayerActivity", "seek safe-guard: media player unusable", e);
                    }
                }
            }
        });
    }

    private void stopSeekbarUpdate() {
        if (seekHandler != null) {
            seekHandler.removeCallbacks(seekRunnable);
            seekHandler = null;
            seekRunnable = null;
            seekbar.setProgress(0);
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
        com.example.tempo.ui.CreateMusicNotification.createNotification(MusicPlayerActivity.this, songName,
                com.example.tempo.R.drawable.ic_pause_icon, position, mySongs.size() - 1);
        // Ensure shared position/state is in sync
        // MainActivity.mediaPlayer is kept in sync when we create/release the player
    }

    @Override
    public void onButtonPlay() {
        com.example.tempo.ui.CreateMusicNotification.createNotification(MusicPlayerActivity.this, songName,
                com.example.tempo.R.drawable.ic_pause_icon, position, mySongs.size() - 1);
    }

    @Override
    public void onButtonPause() {
        com.example.tempo.ui.CreateMusicNotification.createNotification(MusicPlayerActivity.this, songName,
                com.example.tempo.R.drawable.ic_play_icon, position, mySongs.size() - 1);
    }

    @Override
    public void onButtonNext() {
        position++;
        com.example.tempo.ui.CreateMusicNotification.createNotification(MusicPlayerActivity.this, songName,
                com.example.tempo.R.drawable.ic_pause_icon, position, mySongs.size() - 1);
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
        // Unregister the broadcast receiver when activity is destroyed
        try {
            unregisterReceiver(broadcastReceiver);
        } catch (IllegalArgumentException ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        // restart seekbar update thread if needed
        mediaPlayer = com.example.tempo.ui.MainActivity.mediaPlayer;
        if (mediaPlayer != null) {
            if (seekHandler == null) {
                startSeekbarUpdateThread();
            }
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
        stopSeekbarUpdate();
    }
}
