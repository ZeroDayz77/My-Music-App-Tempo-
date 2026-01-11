package com.example.tempo.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.tempo.data.PlaylistItem;
import com.example.tempo.repo.PlaylistRepository;

import java.util.ArrayList;

public class PlaylistDetailActivity extends AppCompatActivity {

    private PlaylistRepository repository;
    private int playlistId;
    private ArrayList<PlaylistItem> items;
    private ListView listView;

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

        loadItems();

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final PlaylistItem item = items.get(position);
                new AlertDialog.Builder(PlaylistDetailActivity.this)
                        .setTitle("Remove song")
                        .setMessage("Remove '" + item.getTitle() + "' from playlist?")
                        .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                repository.removeSongFromPlaylist(item.getId());
                                loadItems();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
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
        PlaylistItemAdapter adapter = new PlaylistItemAdapter(items);
        listView.setAdapter(adapter);
    }

    // Custom adapter for playlist items using song_list_names.xml
    private class PlaylistItemAdapter extends BaseAdapter {
        private final ArrayList<PlaylistItem> data;

        PlaylistItemAdapter(ArrayList<PlaylistItem> data) {
            this.data = data != null ? data : new ArrayList<PlaylistItem>();
        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Object getItem(int position) {
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return data.get(position).getId();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View itemView = convertView;
            if (itemView == null) {
                itemView = android.view.LayoutInflater.from(PlaylistDetailActivity.this).inflate(com.example.tempo.R.layout.song_list_names, parent, false);
            }

            TextView songText = itemView.findViewById(com.example.tempo.R.id.songname);
            ImageView songImage = itemView.findViewById(com.example.tempo.R.id.songimage);

            PlaylistItem it = data.get(position);
            songText.setText(it.getTitle());
            songText.setSelected(true);
            songImage.setImageResource(com.example.tempo.R.drawable.ic_music);

            return itemView;
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
