package com.example.tempo.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.tempo.data.PlaylistItem;
import com.example.tempo.repo.PlaylistRepository;

import java.util.ArrayList;

import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.animation.AccelerateDecelerateInterpolator;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import android.view.Menu;
import androidx.appcompat.widget.SearchView;

public class PlaylistDetailActivity extends AppCompatActivity {

    private PlaylistRepository repository;
    private int playlistId;
    private ArrayList<PlaylistItem> items;
    private ListView listView;
    private PlaylistItemAdapter playlistItemAdapter;

    MediaBrowserCompat mediaBrowser;
    private boolean skipShowAnimation = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.example.tempo.R.layout.activity_playlist_detail);

        repository = new PlaylistRepository(this);

        playlistId = getIntent().getIntExtra("playlist_id", -1);
        String playlistName = getIntent().getStringExtra("playlist_name");

        Toolbar toolbar = findViewById(com.example.tempo.R.id.tempoToolBar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(playlistName);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        listView = findViewById(com.example.tempo.R.id.listViewPlaylistItems);

        // Bottom toolbar / mini-bar wiring
        BottomNavigationView bottomNavigationView = findViewById(com.example.tempo.R.id.bottomToolBar);
        bottomNavigationView.setSelectedItemId(com.example.tempo.R.id.playlistButton);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == com.example.tempo.R.id.songLibraryButton) {
                startActivity(new Intent(getApplicationContext(), com.example.tempo.ui.MainActivity.class));
                overridePendingTransition(0,0);
                return true;
            } else if (id == com.example.tempo.R.id.songPlayingButton) {
                if (!com.example.tempo.Services.MediaPlaybackService.isActive) {
                    Toast.makeText(getApplicationContext(), "No song currently playing, please choose a song...", Toast.LENGTH_SHORT).show();
                    return false;
                }
                Intent musicPlayerActivity = new Intent(getApplicationContext(), com.example.tempo.ui.MusicPlayerActivity.class);
                musicPlayerActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(musicPlayerActivity);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            } else return id == com.example.tempo.R.id.playlistButton;
        });

        // now-playing mini-bar
        TextView nowPlayingTitle = findViewById(com.example.tempo.R.id.nowPlayingTitle);
        View nowPlayingClickable = findViewById(com.example.tempo.R.id.nowPlayingClickable);
        View nowPlayingInclude = findViewById(com.example.tempo.R.id.nowPlayingInclude);
        skipShowAnimation = com.example.tempo.Services.MediaPlaybackService.isActive;
        if (nowPlayingInclude != null) {
            if (skipShowAnimation && com.example.tempo.Services.MediaPlaybackService.currentTitle != null && !com.example.tempo.Services.MediaPlaybackService.currentTitle.isEmpty()) {
                nowPlayingInclude.setVisibility(View.VISIBLE);
                nowPlayingTitle.setText(com.example.tempo.Services.MediaPlaybackService.currentTitle);
            } else {
                nowPlayingInclude.setVisibility(View.GONE);
            }
        }

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

        // Setup media browser for live updates
        mediaBrowser = new MediaBrowserCompat(this, new ComponentName(this, com.example.tempo.Services.MediaPlaybackService.class),
                new MediaBrowserCompat.ConnectionCallback() {
                    @Override
                    public void onConnected() {
                        try {
                            MediaControllerCompat controller = new MediaControllerCompat(PlaylistDetailActivity.this, mediaBrowser.getSessionToken());
                            MediaControllerCompat.setMediaController(PlaylistDetailActivity.this, controller);
                            MediaMetadataCompat meta = controller.getMetadata();
                            PlaybackStateCompat state = controller.getPlaybackState();
                            runOnUiThread(() -> updateMiniBarFromController(meta, state));
                            controller.registerCallback(controllerCallback);
                        } catch (Exception ignored) {}
                    }
                }, null);
        mediaBrowser.connect();

        loadItems();

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final PlaylistItem item = items.get(position);
                AlertDialog delDlg = new AlertDialog.Builder(PlaylistDetailActivity.this)
                        .setTitle("Remove song")
                        .setMessage("Remove '" + item.getTitle() + "' from playlist?")
                        .setPositiveButton("Remove", (dialog, which) -> {
                            repository.removeSongFromPlaylist(item.getId());
                            loadItems();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                try {
                    delDlg.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.white));
                    delDlg.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(android.R.color.white));
                } catch (Exception ignored) {}
                return true;
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Play song from playlist using MusicPlayerActivity
                PlaylistItem item = items.get(position);
                Intent intent = new Intent(PlaylistDetailActivity.this, com.example.tempo.ui.MusicPlayerActivity.class);
                // pass the uri and title
                intent.putExtra("song_uri", item.getUri());
                intent.putExtra("song_name", item.getTitle());
                startActivity(intent);
            }
        });
    }

    private void loadItems() {
        items = repository.getItemsForPlaylist(playlistId);
        // Use custom adapter to show each item with song_list_names layout
        playlistItemAdapter = new PlaylistItemAdapter(items);
        listView.setAdapter(playlistItemAdapter);
    }

    private class PlaylistItemAdapter extends BaseAdapter {
        private final ArrayList<PlaylistItem> originalData;
        private ArrayList<PlaylistItem> filteredData;

        PlaylistItemAdapter(ArrayList<PlaylistItem> data) {
            this.originalData = data != null ? data : new ArrayList<PlaylistItem>();
            this.filteredData = new ArrayList<>(this.originalData);
        }

        void filter(String query) {
            if (query == null || query.isEmpty()) {
                filteredData = new ArrayList<>(originalData);
            } else {
                String q = query.toLowerCase();
                ArrayList<PlaylistItem> out = new ArrayList<>();
                for (PlaylistItem it : originalData) {
                    if (it.getTitle() != null && it.getTitle().toLowerCase().contains(q)) out.add(it);
                }
                filteredData = out;
            }
            notifyDataSetChanged();
        }

        void updateList(ArrayList<PlaylistItem> newList) {
            this.originalData.clear();
            if (newList != null) this.originalData.addAll(newList);
            filter("");
        }

        ArrayList<PlaylistItem> getFiltered() { return filteredData; }

        @Override
        public int getCount() {
            return filteredData.size();
        }

        @Override
        public Object getItem(int position) {
            return filteredData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return filteredData.get(position).getId();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View itemView = convertView;
            if (itemView == null) {
                itemView = android.view.LayoutInflater.from(PlaylistDetailActivity.this).inflate(com.example.tempo.R.layout.song_list_names, parent, false);
            }

            TextView songText = itemView.findViewById(com.example.tempo.R.id.songname);
            ImageView songImage = itemView.findViewById(com.example.tempo.R.id.songimage);

            PlaylistItem it = filteredData.get(position);
            songText.setText(it.getTitle());
            songText.setSelected(true);
            songImage.setImageResource(com.example.tempo.R.drawable.ic_music);

            return itemView;
        }
    }

    private final MediaControllerCompat.Callback controllerCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            runOnUiThread(() -> updateMiniBarFromController(metadata, MediaControllerCompat.getMediaController(PlaylistDetailActivity.this).getPlaybackState()));
        }

        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            runOnUiThread(() -> updateMiniBarFromController(MediaControllerCompat.getMediaController(PlaylistDetailActivity.this).getMetadata(), state));
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
            MediaControllerCompat controller = MediaControllerCompat.getMediaController(PlaylistDetailActivity.this);
            if (controller != null) controller.unregisterCallback(controllerCallback);
        } catch (Exception ignored) {}
        try { if (mediaBrowser != null && mediaBrowser.isConnected()) { mediaBrowser.disconnect(); mediaBrowser = null; } } catch (Exception ignored) {}
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(com.example.tempo.R.menu.menu, menu);
        MenuItem searchItem = menu.findItem(com.example.tempo.R.id.search_button);
        SearchView searchView = (SearchView) searchItem.getActionView();

        if (searchView == null){
            return false;
        }

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                playlistItemAdapter.filter(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                playlistItemAdapter.filter(newText);
                return false;
            }
        });
        return true;
    }
}
