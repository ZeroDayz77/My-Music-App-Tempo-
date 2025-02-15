package com.example.tempo;

import android.content.DialogInterface;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;

import java.util.ArrayList;

public class PlaylistsActivity extends AppCompatActivity {

    static MediaPlayer mediaPlayer;
    private Toolbar toolbar;

    private static int playlistCount = 0;

    private ArrayList<Playlist> playlists;

    ListView listView;
    FloatingActionButton addNewPlaylistButton;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        setContentView(R.layout.activity_playlists);

        toolbar = findViewById(R.id.tempoToolBar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Playlists");
//        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //allows for navigation between activities. But crashes the app on pressed, does not reopen the activity on the saved state as I expected.

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomToolBar);

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

        addNewPlaylistButton = findViewById(R.id.NewPlaylistButton);

        addNewPlaylistButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // get name from user input and pass it int
                String playlistName = getUserPlaylistName();
                createPlaylist(playlistName);
            }
        });

    }

    public String getUserPlaylistName(){
        final String[] playlistName = {""}; // Store the name entered by the user

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Playlist Name");


        final EditText input = new EditText(this);
        builder.setView(input);

        // Set up the dialog buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                playlistName[0] = input.getText().toString();
                createPlaylist(playlistName[0]);
            }
        });
        builder.setNegativeButton("Cancel", null);

        builder.show();

        return playlistName[0];
    }
    public void createPlaylist(String name){
        this.playlists.get(playlistCount).setName(name);
        playlistCount++;
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