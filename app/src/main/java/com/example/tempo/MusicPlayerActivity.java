package com.example.tempo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
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
import android.os.PersistableBundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;

public class MusicPlayerActivity extends AppCompatActivity implements Playable {
    AppCompatButton buttonplay, skipsongnext, skipsongprev, buttonshuffle, buttonrepeat;
    TextView songnametext, songstarttime, songendtime;
    SeekBar seekbar;
    ImageView songimageview;

    String sname;
    NotificationManager notificationManager;

    public static final String EXTRA_NAME = "song_name";
    static MediaPlayer mediaPlayer;
    public static int position;
    public static boolean isShuffleToggled;
    public static boolean isLoopToggled;
    public static ArrayList<File> mySongs;
    Thread seekbarUpdate;

    public static Bundle bundle;

    private Toolbar toolbar;

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

        toolbar=findViewById(R.id.tempoToolBar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Now Playing");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
//        getSupportActionBar().setDisplayShowHomeEnabled(true);

        buttonplay = findViewById(R.id.buttonplay);
        skipsongnext = findViewById(R.id.skipsongnext);
        skipsongprev = findViewById(R.id.skipsongprev);
        buttonrepeat = findViewById(R.id.buttonrepeat);
        buttonshuffle = findViewById(R.id.buttonshuffle);
        songimageview = findViewById(R.id.songimageview);

        songnametext = findViewById(R.id.songnametext);
        songstarttime = findViewById(R.id.songstarttime);
        songendtime = findViewById(R.id.songendtime);

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
        String songName = i.getStringExtra("songname");
        position = bundle.getInt("pos", 0);

        songnametext.setSelected(true);
        Uri uri = Uri.parse(mySongs.get(position).toString());
        sname = mySongs.get(position).getName().toString().replace( ".mp3", "").replace(".wav", "");

        songnametext.setText(sname);

        mediaPlayer = MediaPlayer.create(getApplicationContext(), uri);
        mediaPlayer.start();

        seekbarUpdate = new Thread()
        {
            @Override
            public void run() {
                int totalSongDuration = mediaPlayer.getDuration();
                int currentSongPosition = 0;

                while (currentSongPosition < totalSongDuration)
                {
                    //time in milliseconds for the while loop to checks before updating seekbar
                    try {
                        sleep(500);
                        currentSongPosition = mediaPlayer.getCurrentPosition();
                        seekbar.setProgress(currentSongPosition);
                    }
                    catch (InterruptedException | IllegalStateException e)
                    {
                       e.printStackTrace();
                    }
                }
            }
        };

        //custom code for the seekbar to dynamically change it per song, though it does not fully work as intended.

        seekbarUpdate.start();
        seekbar.setMax(mediaPlayer.getDuration());
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

        // displays the song duration and current song time on the music player activity.

        String endTime = createSongTime(mediaPlayer.getDuration());
        songendtime.setText(endTime);

        final Handler handler = new Handler();
        final int delay = 500;

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String currentTime = createSongTime(mediaPlayer.getCurrentPosition());
                songstarttime.setText(currentTime);

                String endTime = createSongTime(mediaPlayer.getDuration());
                songendtime.setText(endTime);

                handler.postDelayed(this, delay);
            }
        },delay);


        // when play button is pressed

        buttonplay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mediaPlayer.isPlaying())
                {
                    buttonplay.setBackgroundResource(R.drawable.ic_play_icon);
                    CreateMusicNotification.createNotification(MusicPlayerActivity.this, sname,R.drawable.ic_play_icon, position, mySongs.size());
                    mediaPlayer.pause();
                    onButtonPause();
                } else {
                    buttonplay.setBackgroundResource(R.drawable.ic_pause_icon);
                    CreateMusicNotification.createNotification(MusicPlayerActivity.this, sname,R.drawable.ic_play_icon, position, mySongs.size());
                    mediaPlayer.start();
                    onButtonPlay();
                }
            }
        });

        // this will play the next song in the current song list view

        // when skip next is pressed

        skipsongnext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mediaPlayer.stop();
                mediaPlayer.release();
                position = ((position+1)%mySongs.size());
                Uri u = Uri.parse(mySongs.get(position).toString());
                mediaPlayer = MediaPlayer.create(getApplicationContext(), u);
                sname = mySongs.get(position).getName().toString().replace( ".mp3", "").replace(".wav", "");
                songnametext.setText(sname);
                mediaPlayer.start();
                buttonplay.setBackgroundResource(R.drawable.ic_pause_icon);
                startAnimation(songimageview);

                onButtonNext();

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

        skipsongprev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mediaPlayer.stop();
                mediaPlayer.release();
                position = ((position-1)<0)?(mySongs.size()-1):(position-1);
                Uri u = Uri.parse(mySongs.get(position).toString());
                mediaPlayer = MediaPlayer.create(getApplicationContext(), u);
                sname = mySongs.get(position).getName().toString().replace( ".mp3", "").replace(".wav", "");
                songnametext.setText(sname);
                mediaPlayer.start();
                buttonplay.setBackgroundResource(R.drawable.ic_pause_icon);
                startAnimation(songimageview);

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

        buttonshuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(!isShuffleToggled) {
                    isShuffleToggled = true;
                    buttonshuffle.setBackgroundResource(R.drawable.ic_shuffle_selected_icon);
                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mediaPlayer) {
                            mediaPlayer.stop();
                            mediaPlayer.release();
                            Random random = new Random();
                            int upperbound = mySongs.size();
                            position = (position + random.nextInt(upperbound));
                            Uri u = Uri.parse(mySongs.get(position).toString());
                            mediaPlayer = MediaPlayer.create(getApplicationContext(), u);
                            sname = mySongs.get(position).getName().toString().replace(".mp3", "").replace(".wav", "");
                            songnametext.setText(sname);
                            mediaPlayer.start();
                            buttonplay.setBackgroundResource(R.drawable.ic_pause_icon);
                            startAnimation(songimageview);

                            CreateMusicNotification.createNotification(MusicPlayerActivity.this, sname, R.drawable.ic_play_icon, position, mySongs.size());
                        }
                    });
                }
                else {
                    isShuffleToggled = false;
                    buttonshuffle.setBackgroundResource(R.drawable.ic_shuffle_icon);
                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mediaPlayer) {
                            skipsongnext.performClick();
                        }
                    });
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

        // when repeat is pressed, does not work as intended as it doesn't repeat the current song and is not a toggle, does not crash the program.

        buttonrepeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(!isLoopToggled) {
                    isLoopToggled = true;
                    buttonrepeat.setBackgroundResource(R.drawable.ic_repeat_selected_icon);
                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mediaPlayer) {
                            mediaPlayer.stop();
                            mediaPlayer.release();
                            Uri u = Uri.parse(mySongs.get(position).toString());
                            mediaPlayer = MediaPlayer.create(getApplicationContext(), u);
                            sname = mySongs.get(position).getName().toString().replace(".mp3", "").replace(".wav", "");
                            songnametext.setText(sname);
                            mediaPlayer.start();
                            buttonplay.setBackgroundResource(R.drawable.ic_pause_icon);
                            startAnimation(songimageview);

                            CreateMusicNotification.createNotification(MusicPlayerActivity.this, sname, R.drawable.ic_play_icon, position, mySongs.size());
                        }
                    });

                }
                else{
                    isLoopToggled = false;
                    buttonrepeat.setBackgroundResource(R.drawable.ic_repeat_icon);
                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mediaPlayer) {
                            skipsongnext.performClick();
                        }
                    });
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
                skipsongnext.performClick();
            }
        });

        //allows for navigation between activities.

        BottomNavigationView bottomNavigationView=findViewById(R.id.bottomToolBar);

        bottomNavigationView.setSelectedItemId(R.id.songPlayingButton);

        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
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

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getExtras().getString("actionname");

            switch (action) {
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
        ObjectAnimator animator = ObjectAnimator.ofFloat(songimageview, "rotation", 0f, 360f);
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
        Bundle restoredData = savedInstanceState.getBundle("currentSongData");
        bundle = restoredData;
    }

    @Override
    public void onButtonPrevious() {
        position--;
        CreateMusicNotification.createNotification(MusicPlayerActivity.this, sname,
                R.drawable.ic_pause_icon, position, mySongs.size() - 1);
    }

    @Override
    public void onButtonPlay() {
        CreateMusicNotification.createNotification(MusicPlayerActivity.this, sname,
                R.drawable.ic_pause_icon, position, mySongs.size() - 1);
    }

    @Override
    public void onButtonPause() {
        CreateMusicNotification.createNotification(MusicPlayerActivity.this, sname,
                R.drawable.ic_play_icon, position, mySongs.size() - 1);
    }

    @Override
    public void onButtonNext() {
        position++;
        CreateMusicNotification.createNotification(MusicPlayerActivity.this, sname,
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

//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
//            notificationManager.cancelAll();
//        }
//
//        unregisterReceiver(broadcastReceiver);
//    }
}