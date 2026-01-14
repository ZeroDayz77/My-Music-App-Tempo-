package com.example.tempo.ui;

import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
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

import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

public class PlaylistsActivity extends BaseBottomNavActivity {
    private Toolbar toolbar;

    private PlaylistRepository repository;

    ListView listView;
    FloatingActionButton addNewPlaylistButton;

    private ArrayList<Playlist> playlists;
    private PlaylistAdapter playlistAdapter;

    MediaBrowserCompat mediaBrowser;
    private boolean skipShowAnimation = false;

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

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            final Playlist selected = playlists.get(position);
            CharSequence[] options = new CharSequence[]{"Rename", "Delete", "Cancel"};
            AlertDialog dlg = new AlertDialog.Builder(PlaylistsActivity.this)
                    .setTitle(selected.getName())
                    .setItems(options, (d, which) -> {
                        if (which == 0) {
                            // Rename
                            final EditText input = new EditText(PlaylistsActivity.this);
                            input.setText(selected.getName());
                            AlertDialog renameDlg = new AlertDialog.Builder(PlaylistsActivity.this)
                                    .setTitle("Rename playlist")
                                    .setView(input)
                                    .setPositiveButton("Rename", (rd, rw) -> {
                                        String newName = input.getText().toString().trim();
                                        if (!newName.isEmpty()) {
                                            repository.renamePlaylist(selected.getId(), newName);
                                            loadPlaylists();
                                        }
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                            // make buttons visible (white)
                            try {
                                renameDlg.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.white));
                                renameDlg.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(android.R.color.white));
                            } catch (Exception ignored) {}
                        } else if (which == 1) {
                            // Delete
                            AlertDialog del = new AlertDialog.Builder(PlaylistsActivity.this)
                                    .setTitle("Delete playlist")
                                    .setMessage("Delete playlist '" + selected.getName() + "'? This will remove it and its items.")
                                    .setPositiveButton("Delete", (dd, dw) -> {
                                        repository.deletePlaylist(selected.getId());
                                        loadPlaylists();
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                            try {
                                del.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.white));
                                del.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(android.R.color.white));
                            } catch (Exception ignored) {}
                        }
                    })
                    .show();
            // ensure the top-level list dialog has white buttons if any
            try {
                dlg.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.white));
                dlg.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(android.R.color.white));
            } catch (Exception ignored) {}
            return true;
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
        View nowPlayingInclude = findViewById(com.example.tempo.R.id.nowPlayingInclude);
        // Decide whether to animate based on whether we are returning from the full player
        boolean returningFromPlayer = com.example.tempo.ui.MusicPlayerActivity.shouldAnimateMiniBarOnReturn;
        skipShowAnimation = !returningFromPlayer && com.example.tempo.Services.MediaPlaybackService.isActive;
        if (nowPlayingInclude != null) {
            if (skipShowAnimation && com.example.tempo.Services.MediaPlaybackService.currentTitle != null && !com.example.tempo.Services.MediaPlaybackService.currentTitle.isEmpty()) {
                nowPlayingInclude.setVisibility(View.VISIBLE);
                nowPlayingTitle.setText(com.example.tempo.Services.MediaPlaybackService.currentTitle);
            } else {
                nowPlayingInclude.setVisibility(View.GONE);
            }
        }
        if (returningFromPlayer) com.example.tempo.ui.MusicPlayerActivity.shouldAnimateMiniBarOnReturn = false;

        // Setup media browser for live updates
        mediaBrowser = new MediaBrowserCompat(this, new ComponentName(this, com.example.tempo.Services.MediaPlaybackService.class),
                new MediaBrowserCompat.ConnectionCallback() {
                    @Override
                    public void onConnected() {
                        try {
                            MediaControllerCompat controller = new MediaControllerCompat(PlaylistsActivity.this, mediaBrowser.getSessionToken());
                            MediaControllerCompat.setMediaController(PlaylistsActivity.this, controller);
                            MediaMetadataCompat meta = controller.getMetadata();
                            PlaybackStateCompat state = controller.getPlaybackState();
                            runOnUiThread(() -> updateMiniBarFromController(meta, state));
                            controller.registerCallback(controllerCallback);
                        } catch (Exception ignored) {}
                    }
                }, null);
        mediaBrowser.connect();

        nowPlayingClickable.setOnClickListener(v -> {
            if (com.example.tempo.Services.MediaPlaybackService.isActive) {
                Intent musicPlayerActivity = new Intent(getApplicationContext(), com.example.tempo.ui.MusicPlayerActivity.class);
                musicPlayerActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(musicPlayerActivity);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });

        ImageButton nowPrev = findViewById(com.example.tempo.R.id.nowPrev);
        ImageButton nowPlayPause = findViewById(com.example.tempo.R.id.nowPlayPause);
        ImageButton nowNext = findViewById(com.example.tempo.R.id.nowNext);

        if (com.example.tempo.Services.MediaPlaybackService.isPlaying) {
            nowPlayPause.setImageResource(com.example.tempo.R.drawable.ic_pause_icon);
        } else {
            nowPlayPause.setImageResource(com.example.tempo.R.drawable.ic_play_icon);
        }

        nowPrev.setOnClickListener(v -> startService(new Intent(getApplicationContext(), com.example.tempo.Services.MediaPlaybackService.class).setAction(com.example.tempo.Services.MediaPlaybackService.ACTION_PREV)));
        nowNext.setOnClickListener(v -> startService(new Intent(getApplicationContext(), com.example.tempo.Services.MediaPlaybackService.class).setAction(com.example.tempo.Services.MediaPlaybackService.ACTION_NEXT)));
        nowPlayPause.setOnClickListener(v -> startService(new Intent(getApplicationContext(), com.example.tempo.Services.MediaPlaybackService.class).setAction(com.example.tempo.Services.MediaPlaybackService.ACTION_TOGGLE)));
    }

    private void loadPlaylists() {
        playlists = repository.getAllPlaylists();
        playlistAdapter = new PlaylistAdapter(this, playlists);
        listView.setAdapter(playlistAdapter);
    }

    private void showCreatePlaylistDialog() {
        final EditText input = new EditText(this);
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Create Playlist")
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        long id = repository.createPlaylist(name);
                        if (id == -1) {
                            Toast.makeText(PlaylistsActivity.this, "Unable to create playlist (name may already exist)", Toast.LENGTH_SHORT).show();
                        }
                        loadPlaylists();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        try {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.white));
            dlg.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(android.R.color.white));
        } catch (Exception ignored) {}
    }

    private final MediaControllerCompat.Callback controllerCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            runOnUiThread(() -> updateMiniBarFromController(metadata, MediaControllerCompat.getMediaController(PlaylistsActivity.this).getPlaybackState()));
        }

        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            runOnUiThread(() -> updateMiniBarFromController(MediaControllerCompat.getMediaController(PlaylistsActivity.this).getMetadata(), state));
        }
    };

    private void updateMiniBarFromController(MediaMetadataCompat meta, PlaybackStateCompat state) {
        View include = findViewById(com.example.tempo.R.id.nowPlayingInclude);
        TextView title = findViewById(com.example.tempo.R.id.nowPlayingTitle);
        ImageButton playBtn = findViewById(com.example.tempo.R.id.nowPlayPause);
        if (meta == null || state == null || include == null || title == null || playBtn == null) {
            if (include != null) include.setVisibility(View.GONE);
            return;
        }

        String t = meta.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
        boolean playing = state.getState() == PlaybackStateCompat.STATE_PLAYING;

        title.setText(t != null ? t : "");
        playBtn.setImageResource(playing ? com.example.tempo.R.drawable.ic_pause_icon : com.example.tempo.R.drawable.ic_play_icon);

        if (include.getVisibility() != View.VISIBLE) {
            if (skipShowAnimation) {
                include.setVisibility(View.VISIBLE);
                skipShowAnimation = false;
            } else {
                include.setVisibility(View.VISIBLE);
                include.setTranslationY(include.getHeight());
                include.setAlpha(0f);
                include.animate().translationY(0).alpha(1f).setDuration(250).setInterpolator(new AccelerateDecelerateInterpolator()).start();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            MediaControllerCompat controller = MediaControllerCompat.getMediaController(PlaylistsActivity.this);
            if (controller != null) controller.unregisterCallback(controllerCallback);
        } catch (Exception ignored) {}
        try { if (mediaBrowser != null && mediaBrowser.isConnected()) { mediaBrowser.disconnect(); mediaBrowser = null; } } catch (Exception ignored) {}
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

    @Override
    protected int getNavigationItemId() {
        return com.example.tempo.R.id.playlistButton;
    }
}
