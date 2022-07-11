package com.example.tempo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.Toolbar;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.PersistableBundle;
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

public class MusicPlayerActivity extends AppCompatActivity {
    AppCompatButton buttonplay, skipsongnext, skipsongprev, buttonshuffle, buttonrepeat;
    TextView songnametext, songstarttime, songendtime;
    SeekBar seekbar;
    ImageView songimageview;


    String sname;

    public static final String EXTRA_NAME = "song_name";
    static MediaPlayer mediaPlayer;
    int position;
    ArrayList<File> mySongs;
    Thread seekbarUpdate;

//    private Toolbar toolbar;

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
        setContentView(R.layout.activity_music_player);

//        toolbar=findViewById(R.id.tempoToolBar);
//        setSupportActionBar(toolbar);
//        getActionBar().setTitle("Now Playing");
//        getActionBar().setDisplayHomeAsUpEnabled(true);
//        getActionBar().setDisplayShowHomeEnabled(true);

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

        // just a check before hand on hte media player

        if (mediaPlayer != null)
        {
            mediaPlayer.stop();
            mediaPlayer.release();
        }


        //to link to the main activity and parse the song information.
        Intent i = getIntent();
        Bundle bundle = i.getExtras();

        mySongs = (ArrayList) bundle.getParcelableArrayList("songs");
        String songName = i.getStringExtra("songname");
        position = bundle.getInt("pos", 0);

        songnametext.setSelected(true);
        Uri uri = Uri.parse(mySongs.get(position).toString());
        sname = mySongs.get(position).getName();

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
                    mediaPlayer.pause();
                } else {
                    buttonplay.setBackgroundResource(R.drawable.ic_pause_icon);
                    mediaPlayer.start();
                }
            }
        });

        // this will play the next song in the current song list view

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                    skipsongnext.performClick();
            }
        });

        // when skip next is pressed

        skipsongnext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mediaPlayer.stop();
                mediaPlayer.release();
                position = ((position+1)%mySongs.size());
                Uri u = Uri.parse(mySongs.get(position).toString());
                mediaPlayer = MediaPlayer.create(getApplicationContext(), u);
                sname = mySongs.get(position).getName();
                songnametext.setText(sname);
                mediaPlayer.start();
                buttonplay.setBackgroundResource(R.drawable.ic_pause_icon);
                startAnimation(songimageview);
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
                sname = mySongs.get(position).getName();
                songnametext.setText(sname);
                mediaPlayer.start();
                buttonplay.setBackgroundResource(R.drawable.ic_pause_icon);
                startAnimation(songimageview);
            }
        });

        // when shuffle is pressed, causes crashes to the program, not fully sure as to why.

        buttonshuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mediaPlayer.stop();
                mediaPlayer.release();
                Random random = new Random();
                int upperbound = mySongs.size();
                position = (position+random.nextInt(upperbound));
                Uri u = Uri.parse(mySongs.get(position).toString());
                mediaPlayer = MediaPlayer.create(getApplicationContext(), u);
                sname = mySongs.get(position).getName();
                songnametext.setText(sname);
                mediaPlayer.start();
                buttonplay.setBackgroundResource(R.drawable.ic_pause_icon);
                buttonshuffle.setBackgroundResource(R.drawable.ic_shuffle_selected_icon);
                startAnimation(songimageview);

            }
        });

        // when repeat is pressed, does not work as intended as it doesn't repeat the current song and is not a toggle, does not crash the program.

        buttonrepeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mediaPlayer.stop();
                mediaPlayer.release();
                Uri u = Uri.parse(mySongs.get(position).toString());
                mediaPlayer = MediaPlayer.create(getApplicationContext(), u);
                sname = mySongs.get(position).getName();
                songnametext.setText(sname);
                mediaPlayer.start();
                buttonrepeat.setBackgroundResource(R.drawable.ic_repeat_selected_icon);
                startAnimation(songimageview);
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
}