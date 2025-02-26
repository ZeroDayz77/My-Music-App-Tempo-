package com.example.tempo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.Toolbar;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;

public class MusicPlayerActivity extends AppCompatActivity implements Playable {
    AppCompatButton buttonPlay, skipSongNext, skipSongPrev, buttonShuffle, buttonRepeat;
    TextView songNameText, songStartTime, songEndTime;
    SeekBar seekbar;
    ImageView songImageView;
    String songName;
    NotificationManager notificationManager;
    MediaPlayer mediaPlayer;
    public static int position;
    public static boolean isShuffleToggled;
    public static boolean isLoopToggled;
    public static ArrayList<File> mySongs;
    Thread seekbarUpdate;
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
        setContentView(R.layout.activity_music_player);

        Toolbar toolBar = findViewById(R.id.tempoToolBar);
        setSupportActionBar(toolBar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("Now Playing");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
//        getSupportActionBar().setDisplayShowHomeEnabled(true);

        buttonPlay = findViewById(R.id.buttonplay);
        skipSongNext = findViewById(R.id.skipsongnext);
        skipSongPrev = findViewById(R.id.skipsongprev);
        buttonRepeat = findViewById(R.id.buttonrepeat);
        buttonShuffle = findViewById(R.id.buttonshuffle);
        songImageView = findViewById(R.id.songimageview);

        songNameText = findViewById(R.id.songnametext);
        songStartTime = findViewById(R.id.songstarttime);
        songEndTime = findViewById(R.id.songendtime);

        seekbar = findViewById(R.id.seekbar);

        // just a check before hand on the media player

        if (mediaPlayer != null)
        {
            mediaPlayer.stop();
            mediaPlayer.release();
        }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotificationChannel channel = new NotificationChannel("notification","notification", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            channel.enableVibration(false);
            channel.setSound(null, null);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }

        //to link to the main activity and parse the song information.
        Intent i = getIntent();
        bundle = i.getExtras();

        mySongs = (ArrayList) bundle.getParcelableArrayList("songs");
        position = bundle.getInt("pos", 0);

        songNameText.setSelected(true);
        Uri uri = Uri.parse(mySongs.get(position).toString());
        songName = mySongs.get(position).getName().replace( ".mp3", "").replace(".wav", "");

        songNameText.setText(songName);

        mediaPlayer = MediaPlayer.create(getApplicationContext(), uri);
        mediaPlayer.start();

        // Initialize seekbarUpdate thread
        startSeekbarUpdateThread();
        // displays the song duration and current song time on the music player activity.

        String endTime = createSongTime(mediaPlayer.getDuration());
        songEndTime.setText(endTime);

        final Handler handler = new Handler();
        final int delay = 500;

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String currentTime = createSongTime(mediaPlayer.getCurrentPosition());
                songStartTime.setText(currentTime);

                String endTime = createSongTime(mediaPlayer.getDuration());
                songEndTime.setText(endTime);

                handler.postDelayed(this, delay);
            }
        },delay);


        // when play button is pressed

        buttonPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mediaPlayer.isPlaying())
                {
                    buttonPlay.setBackgroundResource(R.drawable.ic_play_icon);
                    CreateMusicNotification.createNotification(MusicPlayerActivity.this, songName,R.drawable.ic_play_icon, position, mySongs.size());
                    mediaPlayer.pause();
                    onButtonPause();
                } else {
                    buttonPlay.setBackgroundResource(R.drawable.ic_pause_icon);
                    CreateMusicNotification.createNotification(MusicPlayerActivity.this, songName,R.drawable.ic_play_icon, position, mySongs.size());
                    mediaPlayer.start();
                    onButtonPlay();
                }
            }
        });

        // this will play the next song in the current song list view

        // when skip next is pressed

        skipSongNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mediaPlayer.stop();
                mediaPlayer.release();
                stopSeekbarUpdate();
                position = ((position+1)%mySongs.size());
                Uri u = Uri.parse(mySongs.get(position).toString());
                mediaPlayer = MediaPlayer.create(getApplicationContext(), u);
                songName = mySongs.get(position).getName().replace( ".mp3", "").replace(".wav", "");
                songNameText.setText(songName);
                mediaPlayer.start();
                buttonPlay.setBackgroundResource(R.drawable.ic_pause_icon);
                startAnimation(songImageView);

                startSeekbarUpdateThread();
                onButtonNext();

                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mediaPlayer) {
                        skipSongNext.performClick();
                    }
                });

//                NotificationCompat.Builder builder = new NotificationCompat.Builder(MusicPlayerActivity.this, "notification");
//                builder.setContentTitle(getString(R.string.app_name));
//                builder.setContentText("Currently Playing: " + sname);
//                builder.setSmallIcon(R.drawable.ic_music);
//                builder.setSilent(true);
//                builder.setAutoCancel(true);
//
//                NotificationManagerCompat managerCompat = NotificationManagerCompat.from(MusicPlayerActivity.this);
//                managerCompat.notify(1,builder.build());

            }
        });

        // when skip previous is pressed

        skipSongPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mediaPlayer.stop();
                mediaPlayer.release();
                stopSeekbarUpdate();
                position = ((position-1)<0)?(mySongs.size()-1):(position-1);
                Uri u = Uri.parse(mySongs.get(position).toString());
                mediaPlayer = MediaPlayer.create(getApplicationContext(), u);
                songName = mySongs.get(position).getName().replace( ".mp3", "").replace(".wav", "");
                songNameText.setText(songName);
                mediaPlayer.start();
                buttonPlay.setBackgroundResource(R.drawable.ic_pause_icon);
                startAnimation(songImageView);

                startSeekbarUpdateThread();
                onButtonPrevious();
//
//                NotificationCompat.Builder builder = new NotificationCompat.Builder(MusicPlayerActivity.this, "notification");
//                builder.setContentTitle(getString(R.string.app_name));
//                builder.setContentText("Currently Playing: " + sname);
//                builder.setSmallIcon(R.drawable.ic_music);
//                builder.setSilent(true);
//                builder.setAutoCancel(true);
//
//                NotificationManagerCompat managerCompat = NotificationManagerCompat.from(MusicPlayerActivity.this);
//                managerCompat.notify(1,builder.build());
            }
        });

        // when shuffle is pressed, causes crashes to the program, not fully sure as to why.

        buttonShuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(!isShuffleToggled) {
                    isShuffleToggled = true;
                    buttonShuffle.setBackgroundResource(R.drawable.ic_shuffle_selected_icon);
                }
                else {
                    isShuffleToggled = false;
                    buttonShuffle.setBackgroundResource(R.drawable.ic_shuffle_icon);
                }
//                NotificationCompat.Builder builder = new NotificationCompat.Builder(MusicPlayerActivity.this, "notification");
//                builder.setContentTitle(getString(R.string.app_name));
//                builder.setContentText("Currently Playing: " + sname);
//                builder.setSmallIcon(R.drawable.ic_music);
//                builder.setSilent(true);
//                builder.setAutoCancel(true);
//
//                NotificationManagerCompat managerCompat = NotificationManagerCompat.from(MusicPlayerActivity.this);
//                managerCompat.notify(1,builder.build());

            }
        });

        buttonRepeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (mediaPlayer!=null)
                {
                    if (mediaPlayer.isLooping())
                    {
                        mediaPlayer.setLooping(false);
                        buttonRepeat.setBackgroundResource(R.drawable.ic_repeat_icon);
                    }
                    else
                    {
                        mediaPlayer.setLooping(true);
                        buttonRepeat.setBackgroundResource(R.drawable.ic_repeat_selected_icon);
                    }
                }
//                NotificationCompat.Builder builder = new NotificationCompat.Builder(MusicPlayerActivity.this, "notification");
//                builder.setContentTitle(getString(R.string.app_name));
//                builder.setContentText("Currently Playing: " + sname);
//                builder.setSmallIcon(R.drawable.ic_music);
//                builder.setSilent(true);
//                builder.setAutoCancel(true);
//
//                NotificationManagerCompat managerCompat = NotificationManagerCompat.from(MusicPlayerActivity.this);
//                managerCompat.notify(1,builder.build());
            }
        });


        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                if(isShuffleToggled) {
                    mediaPlayer.stop();
                    mediaPlayer.release();
                    Random random = new Random();
                    int upperbound = mySongs.size();
                    position = (position + random.nextInt(upperbound));
                    Uri u = Uri.parse(mySongs.get(position).toString());
                    mediaPlayer = MediaPlayer.create(getApplicationContext(), u);
                    songName = mySongs.get(position).getName().replace(".mp3", "").replace(".wav", "");
                    songNameText.setText(songName);
                    mediaPlayer.start();
                    buttonPlay.setBackgroundResource(R.drawable.ic_pause_icon);
                    startAnimation(songImageView);

                    startSeekbarUpdateThread();

                }
                else {
                    skipSongNext.performClick();
                }
            }
        });

        //allows for navigation between activities.

        BottomNavigationView bottomNavigationView=findViewById(R.id.bottomToolBar);

        bottomNavigationView.setSelectedItemId(R.id.songPlayingButton);

        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @SuppressLint("NonConstantResourceId")
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {

                switch (item.getItemId()) {
                    case R.id.songLibraryButton:
                        startActivity(new Intent(getApplicationContext(),MainActivity.class));
                        overridePendingTransition(0,0);
                        return true;
                    case R.id.songPlayingButton:
                        return true;
                    case R.id.playlistButton:
                        startActivity(new Intent(getApplicationContext(),PlaylistsActivity.class));
                        overridePendingTransition(0,0);
                        return true;
                }
                return false;
            }
        });
    }

    private void startSeekbarUpdateThread() {
        seekbarUpdate = new Thread() {
            @Override
            public void run() {
                while (true) { // Loop continuously until interrupted
                    try {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (mediaPlayer != null) {
                                    int totalSongDuration = mediaPlayer.getDuration();
                                    int currentSongPosition = mediaPlayer.getCurrentPosition();
                                    seekbar.setMax(totalSongDuration);
                                    seekbar.setProgress(currentSongPosition);
                                }
                            }
                        });
                        Thread.sleep(500); // Update every 500 milliseconds
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break; // Break the loop when interrupted
                    }
                }
            }
        };

        //custom code for the seekbar to dynamically change it per song, though it does not fully work as intended.
        seekbar.setMax(mediaPlayer.getDuration());

        seekbarUpdate.start();

        seekbar.getProgressDrawable().setColorFilter(getResources().getColor(R.color.white), PorterDuff.Mode.MULTIPLY);
        seekbar.getThumb().setColorFilter(getResources().getColor(R.color.teal_700), PorterDuff.Mode.SRC_IN);

        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mediaPlayer.seekTo(seekBar.getProgress());
            }
        });
    }

    // Method to stop the seekbarUpdate thread
    private void stopSeekbarUpdate() {
        if (seekbarUpdate != null && seekbarUpdate.isAlive()) {
            seekbarUpdate.interrupt();
            seekbar.setProgress(0);
        }
    }

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = Objects.requireNonNull(intent.getExtras()).getString("actionname");

            switch (Objects.requireNonNull(action)) {
                case CreateMusicNotification.SKIPSONGPREV:
                    onButtonPrevious();
                    break;
                case CreateMusicNotification.BUTTONPLAY:
                    if (mediaPlayer.isPlaying())
                        onButtonPause();
                    else
                        onButtonPlay();
                    break;
                case CreateMusicNotification.SKIPSONGNEXT:
                    onButtonNext();
                    break;
            }
        }
    };

    // animation method for song image
    public void startAnimation(View view)
    {
        ObjectAnimator animator = ObjectAnimator.ofFloat(songImageView, "rotation", 0f, 360f);
        animator.setDuration(1000);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(animator);
        animatorSet.start();
    }

    // the calculation to display the time duration in minutes and seconds.
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
        CreateMusicNotification.createNotification(MusicPlayerActivity.this, songName,
                R.drawable.ic_pause_icon, position, mySongs.size() - 1);
    }

    @Override
    public void onButtonPlay() {
        CreateMusicNotification.createNotification(MusicPlayerActivity.this, songName,
                R.drawable.ic_pause_icon, position, mySongs.size() - 1);
    }

    @Override
    public void onButtonPause() {
        CreateMusicNotification.createNotification(MusicPlayerActivity.this, songName,
                R.drawable.ic_play_icon, position, mySongs.size() - 1);
    }

    @Override
    public void onButtonNext() {
        position++;
        CreateMusicNotification.createNotification(MusicPlayerActivity.this, songName,
                R.drawable.ic_pause_icon, position, mySongs.size() - 1);
    }

    @Override
    public boolean onNavigateUp() {
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        return super.onNavigateUp();
    }

    @Override
    public void onBackPressed() {
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        super.onBackPressed();
    }
}