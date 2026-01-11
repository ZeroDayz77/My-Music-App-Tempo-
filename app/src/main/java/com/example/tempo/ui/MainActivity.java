package com.example.tempo.ui;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;

import com.example.tempo.Services.OnClearRecentService;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;

import androidx.appcompat.app.AlertDialog;
import android.widget.EditText;

import com.example.tempo.repo.PlaylistRepository;
import com.example.tempo.data.Playlist;

public class MainActivity extends AppCompatActivity implements com.example.tempo.ui.Playable {
    ListView listView;
    String[] items;
    SearchView searchView;
    customAdapter customAdapter;
    ArrayList<File> filterList;
    boolean isSearchActive = false;
    ArrayList<File> mySongs;
    public static MediaPlayer mediaPlayer;
    NotificationManager notificationManager;

    private PlaylistRepository playlistRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        setTheme(com.example.tempo.R.style.Theme_Tempo_NoActionBar);
        setContentView(com.example.tempo.R.layout.activity_main);

        playlistRepository = new PlaylistRepository(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel();
            IntentFilter filter = new IntentFilter("SONG_CURRENTSONG");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(broadcastReceiver, filter);
            }
            startService(new Intent(getBaseContext(), OnClearRecentService.class));
        }

        Toolbar toolbar = findViewById(com.example.tempo.R.id.tempoToolBar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("Tempo");

        searchView = findViewById(com.example.tempo.R.id.search_button);
        listView = findViewById(com.example.tempo.R.id.listViewSong);

        filterList = new ArrayList<>();

        customAdapter = new customAdapter(new ArrayList<>());
        listView.setAdapter(customAdapter);

        isSearchActive = false;
        runtimePermission();

        BottomNavigationView bottomNavigationView = findViewById(com.example.tempo.R.id.bottomToolBar);

        bottomNavigationView.setSelectedItemId(com.example.tempo.R.id.songLibraryButton);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == com.example.tempo.R.id.songLibraryButton) {
                return true;
            } else if (id == com.example.tempo.R.id.songPlayingButton) {
                if (mediaPlayer == null) {
                    Toast.makeText(getApplicationContext(), "No song currently playing, please choose a song...", Toast.LENGTH_SHORT).show();
                    return false;
                }

                Intent musicPlayerActivity = new Intent(getApplicationContext(), com.example.tempo.ui.MusicPlayerActivity.class);
                musicPlayerActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(musicPlayerActivity);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            } else if (id == com.example.tempo.R.id.playlistButton) {
                startActivity(new Intent(getApplicationContext(), com.example.tempo.ui.PlaylistsActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(com.example.tempo.ui.CreateMusicNotification.CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_HIGH);
            notificationManager = getSystemService(NotificationManager.class);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }

            channel.enableVibration(false);
            channel.setSound(null, null);
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(com.example.tempo.R.menu.menu, menu);
        MenuItem searchItem = menu.findItem(com.example.tempo.R.id.search_button);
        SearchView searchView = (SearchView) searchItem.getActionView();

        assert searchView != null;
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterList = filter(newText);

                if (isSearchActive) {
                    customAdapter = new customAdapter(filterList);
                    listView.setAdapter(customAdapter);
                } else {
                    customAdapter.updateList(mySongs);
                    listView.setAdapter(customAdapter);
                }

                return true;
            }
        });

        MenuItem.OnActionExpandListener onActionExpandListener = new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(@NonNull MenuItem menuItem) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(@NonNull MenuItem menuItem) {
                return true;
            }
        };


        menu.findItem(com.example.tempo.R.id.search_button).setOnActionExpandListener(onActionExpandListener);
        searchView = (SearchView) menu.findItem(com.example.tempo.R.id.search_button).getActionView();
        assert searchView != null;
        searchView.setQueryHint("Name of song...");
        searchView.setIconified(true);
        searchView.onActionViewCollapsed();

        return true;

    }

    public void runtimePermission() {
        Dexter.withContext(this).withPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                        displaySongs();
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {

                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest, PermissionToken permissionToken) {
                        permissionToken.continuePermissionRequest();
                    }
                }).check();
    }

    private ArrayList<File> filter(String newText) {
        ArrayList<File> filteredList = new ArrayList<>();

        if (!newText.isEmpty()) {
            isSearchActive = true;
            for (File file : mySongs) {
                if (file.getName().toLowerCase(Locale.getDefault()).contains(newText.toLowerCase(Locale.getDefault()))) {
                    filteredList.add(file);
                }
            }
        } else {
            isSearchActive = false;
        }

        if (customAdapter != null) {
            customAdapter.notifyDataSetChanged();
        } else {
            Log.e("MainActivity", "customAdapter is null when calling notifyDataSetChanged()");
        }


        return filteredList;
    }

    public ArrayList<File> findSong(File file) {
        ArrayList<File> arrayList = new ArrayList<>();
        File[] files = file.listFiles();
        if (files != null) {
            for (File singlefile : files) {
                if (singlefile.isDirectory() && !singlefile.isHidden()) {
                    arrayList.addAll(findSong(singlefile));
                } else {
                    if (singlefile.getName().endsWith(".wav")) {
                        arrayList.add(singlefile);
                    } else if (singlefile.getName().endsWith(".mp3")) {
                        arrayList.add(singlefile);
                    } else if (singlefile.getName().startsWith("AUD") || singlefile.getName().startsWith(".")) {
                        return arrayList;
                    }
                }

            }
        }
        return arrayList;
    }

    void displaySongs() {

        String extFilePath = "/storage/emulated/0/Music";
        File myFiles = new File(extFilePath);
        mySongs = findSong(myFiles);

        Collections.sort(mySongs, (file1, file2) -> file1.getName().compareToIgnoreCase(file2.getName()));

        items = new String[mySongs.size()];
        for (int i = 0; i < mySongs.size(); i++) {
            items[i] = mySongs.get(i).getName().replace(".mp3", "").replace(".wav", "");
        }

        customAdapter.adapterList = mySongs;
        listView.setAdapter(customAdapter);

        listView.setOnItemClickListener((adapterView, view, i, l) -> {
            int position = isSearchActive ? mySongs.indexOf(filterList.get(i)) : i;

            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }

            // Use the actual file name (no path) to avoid passing a full file path to the player activity
            String songName = mySongs.get(position).getName().replace(".mp3", "").replace(".wav", "");
            startActivity(new Intent(getApplicationContext(), com.example.tempo.ui.MusicPlayerActivity.class)
                    .putExtra("songs", mySongs)
                    .putExtra("songname", songName)
                    .putExtra("pos", position));
            overridePendingTransition(0, 0);

            // Immediately show a notification with the selected song title (no playback metadata yet)
            com.example.tempo.ui.CreateMusicNotification.createNotification(MainActivity.this,
                    mySongs.get(position).getName().replace(".mp3", "").replace(".wav", ""), "",
                    com.example.tempo.R.drawable.ic_play_icon, position, mySongs.size(), 0L, 0L, false);
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            final File selectedFile = mySongs.get(position);
            showAddToPlaylistDialog(selectedFile);
            return true;
        });
    }

    private void showAddToPlaylistDialog(File file) {
        final ArrayList<Playlist> playlists = playlistRepository.getAllPlaylists();
        final ArrayList<String> names = new ArrayList<>();
        for (Playlist p : playlists) names.add(p.getName());
        names.add("Create new playlist...");

        CharSequence[] options = names.toArray(new CharSequence[0]);

        new AlertDialog.Builder(this)
                .setTitle("Add to playlist")
                .setItems(options, (dialog, which) -> {
                    if (which == playlists.size()) {
                        final EditText input = new EditText(MainActivity.this);
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Create Playlist")
                                .setView(input)
                                .setPositiveButton("Create", (d, w) -> {
                                    String name = input.getText().toString().trim();
                                    if (!name.isEmpty()) {
                                        long id = playlistRepository.createPlaylist(name);
                                        if (id != -1) {
                                            playlistRepository.addSongToPlaylist((int) id, file.getAbsolutePath(), file.getName().replace(".mp3", "").replace(".wav", ""), file.getAbsolutePath(), 0L);
                                            Toast.makeText(MainActivity.this, "Added to " + name, Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(MainActivity.this, "Unable to create playlist", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    } else {
                        Playlist chosen = playlists.get(which);
                        if (playlistRepository.isSongInPlaylist(chosen.getId(), file.getAbsolutePath())) {
                            Toast.makeText(MainActivity.this, "Song already in playlist", Toast.LENGTH_SHORT).show();
                        } else {
                            playlistRepository.addSongToPlaylist(chosen.getId(), file.getAbsolutePath(), file.getName().replace(".mp3", "").replace(".wav", ""), file.getAbsolutePath(), 0L);
                            Toast.makeText(MainActivity.this, "Added to " + chosen.getName(), Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getExtras() == null) return;
            String action = intent.getExtras().getString("actionname");
            // Placeholder: react to broadcast actions if needed
        }
    };

    public class customAdapter extends BaseAdapter {
        public ArrayList<File> adapterList;
        private final Context ctx;

        public customAdapter(ArrayList<File> list) {
            this.adapterList = list != null ? list : new ArrayList<File>();
            this.ctx = MainActivity.this;
        }

        @Override
        public int getCount() {
            return adapterList != null ? adapterList.size() : 0;
        }

        @Override
        public Object getItem(int position) {
            return adapterList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {

            // Inflate song_list_names layout and populate data
            View itemView = view;
            if (itemView == null) {
                itemView = android.view.LayoutInflater.from(ctx).inflate(com.example.tempo.R.layout.song_list_names, viewGroup, false);
            }

            TextView songText = itemView.findViewById(com.example.tempo.R.id.songname);
            ImageView songImage = itemView.findViewById(com.example.tempo.R.id.songimage);

            File f = adapterList.get(i);
            String displayName = f.getName().replace(".mp3", "").replace(".wav", "");
            songText.setText(displayName);
            songText.setSelected(true);
            songImage.setImageResource(com.example.tempo.R.drawable.ic_music);

            return itemView;
        }

        public void updateList(ArrayList<File> newList) {
            if (newList == null) newList = new ArrayList<>();
            this.adapterList = newList;
            notifyDataSetChanged();
        }
    }

    @Override
    public void onButtonPrevious() {
        com.example.tempo.ui.MusicPlayerActivity.position--;
        com.example.tempo.ui.CreateMusicNotification.createNotification(MainActivity.this, mySongs.get(com.example.tempo.ui.MusicPlayerActivity.position).getName().replace(".mp3", "").replace(".wav", ""),
                com.example.tempo.R.drawable.ic_pause_icon, com.example.tempo.ui.MusicPlayerActivity.position, mySongs.size() - 1);
    }

    @Override
    public void onButtonPlay() {
        com.example.tempo.ui.CreateMusicNotification.createNotification(MainActivity.this, mySongs.get(com.example.tempo.ui.MusicPlayerActivity.position).getName().replace(".mp3", "").replace(".wav", ""),
                com.example.tempo.R.drawable.ic_pause_icon, com.example.tempo.ui.MusicPlayerActivity.position, mySongs.size() - 1);
    }

    @Override
    public void onButtonPause() {
        com.example.tempo.ui.CreateMusicNotification.createNotification(MainActivity.this, mySongs.get(com.example.tempo.ui.MusicPlayerActivity.position).getName().replace(".mp3", "").replace(".wav", ""),
                com.example.tempo.R.drawable.ic_play_icon, com.example.tempo.ui.MusicPlayerActivity.position, mySongs.size() - 1);
    }

    @Override
    public void onButtonNext() {
        com.example.tempo.ui.MusicPlayerActivity.position++;
        com.example.tempo.ui.CreateMusicNotification.createNotification(MainActivity.this, mySongs.get(com.example.tempo.ui.MusicPlayerActivity.position).getName().replace(".mp3", "").replace(".wav", ""),
                com.example.tempo.R.drawable.ic_pause_icon, com.example.tempo.ui.MusicPlayerActivity.position, mySongs.size() - 1);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager != null) notificationManager.cancelAll();
        }

        try {
            unregisterReceiver(broadcastReceiver);
        } catch (IllegalArgumentException ignored) {
        }
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
