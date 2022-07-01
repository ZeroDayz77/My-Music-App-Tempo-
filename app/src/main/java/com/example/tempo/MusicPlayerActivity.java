package com.example.tempo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;

public class MusicPlayerActivity extends AppCompatActivity {
    AppCompatButton buttonplay, skipsongnext, skipsongprev, buttonshuffle, buttonrepeat;
    TextView songnametext, songstarttime, songendtime;
    SeekBar seekbar;

    String sname;

    public static final String EXTRA_NAME = "song_name";
    static MediaPlayer mediaPlayer;
    int position;
    ArrayList<File> mySongs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_player);

        buttonplay = findViewById(R.id.buttonplay);
        skipsongnext = findViewById(R.id.skipsongnext);
        skipsongprev = findViewById(R.id.skipsongprev);
        buttonrepeat = findViewById(R.id.buttonrepeat);
        buttonshuffle = findViewById(R.id.buttonshuffle);

        songnametext = findViewById(R.id.songnametext);
        songstarttime = findViewById(R.id.songstarttime);
        songendtime = findViewById(R.id.songendtime);

        seekbar = findViewById(R.id.seekbar);

        if (mediaPlayer != null)
        {
            mediaPlayer.stop();
            mediaPlayer.release();
        }

        Intent i = getIntent();
        Bundle bundle = i.getExtras();

        mySongs = (ArrayList) bundle.getParcelableArrayList("songs");
        String songName = i.getStringExtra("songname");
        position = bundle.getInt("position", 0);

        songnametext.setSelected(true);
        Uri uri = Uri.parse(mySongs.get(position).toString());
        sname = mySongs.get(position).getName();

        songnametext.setText(sname);

        mediaPlayer = MediaPlayer.create(getApplicationContext(), uri);
        mediaPlayer.start();

        buttonplay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mediaPlayer.isPlaying())
                {
                    buttonplay.setBackgroundResource(R.drawable.ic_play_icon);
                    mediaPlayer.pause();
                }
                else
                {
                    buttonplay.setBackgroundResource(R.drawable.ic_pause_icon);
                    mediaPlayer.start();
                }
            }
        });
    }
}