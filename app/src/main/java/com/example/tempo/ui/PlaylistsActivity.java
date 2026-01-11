package com.example.tempo.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.tempo.data.Playlist;
import com.example.tempo.repo.PlaylistRepository;
import com.example.tempo.ui.adapters.PlaylistAdapter;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;

import java.util.ArrayList;
import java.util.Objects;

public class PlaylistsActivity extends AppCompatActivity {
    private Toolbar toolbar;

    private PlaylistRepository repository;

    ListView listView;
    FloatingActionButton addNewPlaylistButton;

    private ArrayList<Playlist> playlists;
    private PlaylistAdapter playlistAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        setContentView(com.example.tempo.R.layout.activity_playlists);

        repository = new PlaylistRepository(this);

        toolbar = findViewById(com.example.tempo.R.id.tempoToolBar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("Playlists");

        listView = findViewById(com.example.tempo.R.id.listView);
        addNewPlaylistButton = findViewById(com.example.tempo.R.id.NewPlaylistButton);

        // load playlists and show
        loadPlaylists();

        addNewPlaylistButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showCreatePlaylistDialog();
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Playlist selected = playlists.get(position);
                Intent intent = new Intent(PlaylistsActivity.this, com.example.tempo.ui.PlaylistDetailActivity.class);
                intent.putExtra("playlist_id", selected.getId());
                intent.putExtra("playlist_name", selected.getName());
                startActivity(intent);
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final Playlist selected = playlists.get(position);
                new AlertDialog.Builder(PlaylistsActivity.this)
                        .setTitle("Delete playlist")
                        .setMessage("Delete playlist '" + selected.getName() + "'? This will remove it and its items.")
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                repository.deletePlaylist(selected.getId());
                                loadPlaylists();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            }
        });

        //allows for navigation between activities
        BottomNavigationView bottomNavigationView = findViewById(com.example.tempo.R.id.bottomToolBar);

        bottomNavigationView.setSelectedItemId(com.example.tempo.R.id.playlistButton);

        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {

                switch (item.getItemId()) {
                    case com.example.tempo.R.id.songLibraryButton:
                        startActivity(new Intent(getApplicationContext(), com.example.tempo.ui.MainActivity.class));
                        overridePendingTransition(0,0);
                        return true;
                    case com.example.tempo.R.id.songPlayingButton:
                        if (!com.example.tempo.Services.MediaPlaybackService.isActive) {
                            Toast.makeText(getApplicationContext(), "No song currently playing, please choose a song...", Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        Intent musicPlayerActivity = new Intent(getApplicationContext(), com.example.tempo.ui.MusicPlayerActivity.class);
                        musicPlayerActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(musicPlayerActivity);
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                        return true;
                    case com.example.tempo.R.id.playlistButton:
                        return true;
                }

                return false;
            }
        });

        // now-playing mini bar (shared via bottom toolbar include)
        TextView nowPlayingTitle = findViewById(com.example.tempo.R.id.nowPlayingTitle);
        View nowPlayingClickable = findViewById(com.example.tempo.R.id.nowPlayingClickable);
        if (com.example.tempo.Services.MediaPlaybackService.isActive) {
            nowPlayingTitle.setText(com.example.tempo.Services.MediaPlaybackService.currentTitle);
            nowPlayingTitle.setVisibility(View.VISIBLE);
            nowPlayingClickable.setVisibility(View.VISIBLE);
        } else {
            nowPlayingTitle.setVisibility(View.GONE);
            nowPlayingClickable.setVisibility(View.GONE);
        }

        nowPlayingClickable.setOnClickListener(v -> {
            if (com.example.tempo.Services.MediaPlaybackService.isActive) {
                Intent musicPlayerActivity = new Intent(getApplicationContext(), com.example.tempo.ui.MusicPlayerActivity.class);
                musicPlayerActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(musicPlayerActivity);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });
    }

    private void loadPlaylists() {
        playlists = repository.getAllPlaylists();
        playlistAdapter = new PlaylistAdapter(this, playlists);
        listView.setAdapter(playlistAdapter);
    }

    private void showCreatePlaylistDialog() {
        final EditText input = new EditText(this);
        new AlertDialog.Builder(this)
                .setTitle("Create Playlist")
                .setView(input)
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (!name.isEmpty()) {
                            long id = repository.createPlaylist(name);
                            if (id == -1) {
                                Toast.makeText(PlaylistsActivity.this, "Unable to create playlist (name may already exist)", Toast.LENGTH_SHORT).show();
                            }
                            loadPlaylists();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
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
