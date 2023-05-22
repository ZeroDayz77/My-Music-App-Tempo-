package com.example.tempo;


import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Timer;
import java.util.TimerTask;


public class BottomToolbarActivity extends AppCompatActivity {

    static MediaPlayer mediaPlayer;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bottom_tool_bar);

        //allows for navigation between activities.

        BottomNavigationView bottomNavigationView=findViewById(R.id.bottomToolBar);

        bottomNavigationView.setSelectedItemId(R.id.songLibraryButton);

        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {

                switch (item.getItemId()) {
                    case R.id.songLibraryButton:
                        return true;
                    case R.id.songPlayingButton:

                        if(mediaPlayer == null)
                        {
                            Context context = getApplicationContext();
                            CharSequence text = "No song currently playing, please choose a song...";
                            int duration = Toast.LENGTH_SHORT;

                            Toast toast = Toast.makeText(context, text, duration);
                            toast.show();
                            return false;
                        }

                        Intent musicPlayerActivity = (new Intent(getApplicationContext(), MusicPlayerActivity.class));
                        musicPlayerActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(musicPlayerActivity);
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
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
}
