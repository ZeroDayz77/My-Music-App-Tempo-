package com.example.tempo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

public class PlaylistsActivity extends AppCompatActivity {

    static MediaPlayer mediaPlayer;
    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        setContentView(R.layout.activity_playlists);

        toolbar=findViewById(R.id.tempoToolBar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Playlists");
//        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //allows for navigation between activities. But crashes the app on pressed, does not reopen the activity on the saved state as I expected.

        BottomNavigationView bottomNavigationView=findViewById(R.id.bottomToolBar);

        bottomNavigationView.setSelectedItemId(R.id.playlistButton);

        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {

                switch (item.getItemId()) {
                    case R.id.songLibraryButton:
                        startActivity(new Intent(getApplicationContext(),MainActivity.class));
                        overridePendingTransition(0,0);
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

                        Intent musicPlayerActivity = (new Intent(getApplicationContext(),MusicPlayerActivity.class));
                        musicPlayerActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(musicPlayerActivity);
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                        return true;
                    case R.id.playlistButton:
                        return true;
                }

                return false;
            }
        });

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