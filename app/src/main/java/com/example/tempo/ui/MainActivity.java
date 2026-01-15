package com.example.tempo.ui;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import com.example.tempo.Services.OnClearRecentService;
import com.example.tempo.Services.MediaPlaybackService;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.material.bottomnavigation.BottomNavigationView;

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
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.content.res.Configuration;

import com.example.tempo.repo.PlaylistRepository;
import com.example.tempo.data.Playlist;

public class MainActivity extends BaseBottomNavActivity implements com.example.tempo.ui.Playable {
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

    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 9001;
    private static final int REQUEST_CODE_PICK_FOLDER = 1001;
    private static final String PREFS_MUSIC_FOLDER = "prefs_music_folder";

    MediaBrowserCompat mediaBrowser; // for mini-bar live updates
    private boolean skipShowAnimation = false;

    // AdView reference
    private AdView adView;

    // Sort state for main song list
    private boolean songsSortAscending = true;
    private androidx.appcompat.app.AlertDialog folderChoiceDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        setTheme(com.example.tempo.R.style.Theme_Tempo_NoActionBar);
        setContentView(com.example.tempo.R.layout.activity_main);

        playlistRepository = new PlaylistRepository(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel();
            // clear-recent service (keeps app from restarting unexpectedly)
            startService(new Intent(getBaseContext(), OnClearRecentService.class));
        }

        MobileAds.initialize(this, initializationStatus -> { /* no-op */ });

        // Find AdView and load an ad
        try {
            adView = findViewById(com.example.tempo.R.id.adView);
            if (adView != null) {

                AdRequest adRequest = new AdRequest.Builder().build();
                adView.loadAd(adRequest);
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to load AdView", e);
        }

        Toolbar toolbar = findViewById(com.example.tempo.R.id.tempoToolBar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("Tempo");

        // Ensure overflow menu text/icons are white in dark theme
        try {
            int uiMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (uiMode == Configuration.UI_MODE_NIGHT_YES) {
                toolbar.setPopupTheme(androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dark);
            }
        } catch (Exception ignored) {}

        searchView = findViewById(com.example.tempo.R.id.search_button);
        listView = findViewById(com.example.tempo.R.id.listViewSong);

        filterList = new ArrayList<>();

        customAdapter = new customAdapter(new ArrayList<>());
        listView.setAdapter(customAdapter);

        isSearchActive = false;
        runtimePermission();

        // On first run show explanatory dialog offering default or choose folder.
        checkAndShowFolderDialog();

        BottomNavigationView bottomNavigationView = findViewById(com.example.tempo.R.id.bottomToolBar);

        // Now-playing mini bar
        TextView nowPlayingTitle = findViewById(com.example.tempo.R.id.nowPlayingTitle);
        View nowPlayingClickable = findViewById(com.example.tempo.R.id.nowPlayingClickable);
        // initial visibility — toggle the whole include so it's fully hidden/shown
        View nowPlayingInclude = findViewById(com.example.tempo.R.id.nowPlayingInclude);
        // Decide whether to skip animation or animate based on whether we are returning from the full player
        boolean returningFromPlayer = com.example.tempo.ui.MusicPlayerActivity.shouldAnimateMiniBarOnReturn;
        // If returning from player, we want to animate the mini bar in; otherwise show immediately if service active
        skipShowAnimation = !returningFromPlayer && com.example.tempo.Services.MediaPlaybackService.isActive;
        if (nowPlayingInclude != null) {
            if (skipShowAnimation && com.example.tempo.Services.MediaPlaybackService.currentTitle != null && !com.example.tempo.Services.MediaPlaybackService.currentTitle.isEmpty()) {
                nowPlayingInclude.setVisibility(View.VISIBLE);
                nowPlayingTitle.setText(com.example.tempo.Services.MediaPlaybackService.currentTitle);
            } else {
                nowPlayingInclude.setVisibility(View.GONE);
            }
        }
        // Clear the return flag so we don't animate on subsequent activity switches
        if (returningFromPlayer) com.example.tempo.ui.MusicPlayerActivity.shouldAnimateMiniBarOnReturn = false;

        nowPlayingClickable.setOnClickListener(v -> {
            if (!com.example.tempo.Services.MediaPlaybackService.isActive) return;
            if (nowPlayingInclude != null && nowPlayingInclude.getVisibility() == View.VISIBLE) {
                // animate out then open player
                nowPlayingInclude.animate().translationY(nowPlayingInclude.getHeight()).alpha(0f).setDuration(180).withEndAction(() -> {
                    nowPlayingInclude.setVisibility(View.GONE);
                    Intent musicPlayerActivity = new Intent(getApplicationContext(), com.example.tempo.ui.MusicPlayerActivity.class);
                    musicPlayerActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(musicPlayerActivity);
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                }).start();
            } else {
                Intent musicPlayerActivity = new Intent(getApplicationContext(), com.example.tempo.ui.MusicPlayerActivity.class);
                musicPlayerActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(musicPlayerActivity);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });

        // now-playing controls (safe: nowPlayingInclude may be null if not inflated)
        ImageButton nowPrev = findViewById(com.example.tempo.R.id.nowPrev);
        ImageButton nowPlayPause = findViewById(com.example.tempo.R.id.nowPlayPause);
        ImageButton nowNext = findViewById(com.example.tempo.R.id.nowNext);

        // initialize icons based on service state
        if (com.example.tempo.Services.MediaPlaybackService.isPlaying) {
            nowPlayPause.setImageResource(com.example.tempo.R.drawable.ic_pause_icon);
        } else {
            nowPlayPause.setImageResource(com.example.tempo.R.drawable.ic_play_icon);
        }

        nowPrev.setOnClickListener(v -> startService(new Intent(getApplicationContext(), com.example.tempo.Services.MediaPlaybackService.class).setAction(com.example.tempo.Services.MediaPlaybackService.ACTION_PREV)));
        nowNext.setOnClickListener(v -> startService(new Intent(getApplicationContext(), com.example.tempo.Services.MediaPlaybackService.class).setAction(com.example.tempo.Services.MediaPlaybackService.ACTION_NEXT)));
        nowPlayPause.setOnClickListener(v -> startService(new Intent(getApplicationContext(), com.example.tempo.Services.MediaPlaybackService.class).setAction(com.example.tempo.Services.MediaPlaybackService.ACTION_TOGGLE)));

        // Setup MediaBrowser to receive live updates for now-playing mini bar
        mediaBrowser = new MediaBrowserCompat(this, new ComponentName(this, com.example.tempo.Services.MediaPlaybackService.class),
                new MediaBrowserCompat.ConnectionCallback() {
                    @Override
                    public void onConnected() {
                        try {
                            MediaControllerCompat controller = new MediaControllerCompat(MainActivity.this, mediaBrowser.getSessionToken());
                            MediaControllerCompat.setMediaController(MainActivity.this, controller);
                            // initialize bar from current metadata/state
                            MediaMetadataCompat meta = controller.getMetadata();
                            PlaybackStateCompat state = controller.getPlaybackState();
                            runOnUiThread(() -> updateMiniBarFromController(meta, state));
                            controller.registerCallback(controllerCallback);
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }, null);
        mediaBrowser.connect();

        bottomNavigationView.setSelectedItemId(com.example.tempo.R.id.songLibraryButton);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == com.example.tempo.R.id.songLibraryButton) {
                return true;
            } else if (id == com.example.tempo.R.id.songPlayingButton) {
                // Show player if the playback service is active (playlist loaded/playing or paused)
                if (!com.example.tempo.Services.MediaPlaybackService.isActive) {
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
            NotificationChannel channel = new NotificationChannel(MediaPlaybackService.CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_HIGH);
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

        // Sort button wiring
        MenuItem sortItem = menu.findItem(com.example.tempo.R.id.sort_button);
        if (sortItem != null) {
            sortItem.setTitle(songsSortAscending ? "Sort A→Z" : "Sort Z→A");
            sortItem.setOnMenuItemClickListener(item -> {
                songsSortAscending = !songsSortAscending;
                sortItem.setTitle(songsSortAscending ? "Sort A→Z" : "Sort Z→A");
                // re-display songs with new sort order
                if (mySongs != null) {
                    try {
                        java.util.Collections.sort(mySongs, new java.util.Comparator<java.io.File>() {
                            @Override
                            public int compare(java.io.File f1, java.io.File f2) {
                                return songsSortAscending ? f1.getName().compareToIgnoreCase(f2.getName()) : f2.getName().compareToIgnoreCase(f1.getName());
                            }
                        });
                    } catch (Exception ignored) {}
                    customAdapter.updateList(mySongs);
                    listView.setAdapter(customAdapter);
                }
                return true;
            });
        }

        return true;

    }

    public void runtimePermission() {
        Dexter.withContext(this).withPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                        displaySongs();
                        // Request notification permission on Android 13+
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE_POST_NOTIFICATIONS);
                            }
                        }
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "POST_NOTIFICATIONS permission granted");
            } else {
                Log.d("MainActivity", "POST_NOTIFICATIONS permission denied");
            }
        }
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

        // Determine which folder to scan: use stored preference if available
        SharedPreferences prefs = getSharedPreferences("tempo_prefs", MODE_PRIVATE);
        String stored = prefs.getString(PREFS_MUSIC_FOLDER, null);

        mySongs = new ArrayList<>();
        if (stored == null || stored.isEmpty()) {
            // default to common Music folder
            String extFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Music";
            File myFiles = new File(extFilePath);
            mySongs = findSong(myFiles);
        } else {
            try {
                if (stored.startsWith("/")) {
                    File myFiles = new File(stored);
                    mySongs = findSong(myFiles);
                } else if (stored.startsWith("content://")) {
                    // persisted tree Uri string — traverse using DocumentFile
                    Uri tree = Uri.parse(stored);
                    DocumentFile doc = DocumentFile.fromTreeUri(this, tree);
                    if (doc != null && doc.exists() && doc.isDirectory()) {
                        traverseDocumentFile(doc, mySongs);
                    }
                } else {
                    // fallback: try as path
                    File myFiles = new File(stored);
                    mySongs = findSong(myFiles);
                }
            } catch (Exception e) {
                // fallback to default
                String extFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Music";
                File myFiles = new File(extFilePath);
                mySongs = findSong(myFiles);
            }
        }

        Collections.sort(mySongs, new java.util.Comparator<java.io.File>() {
            @Override
            public int compare(java.io.File file1, java.io.File file2) {
                return file1.getName().compareToIgnoreCase(file2.getName());
            }
        });

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

            // Start playback via the MediaPlaybackService (service owns MediaPlayer & notification)
            ArrayList<File> playList = mySongs;
            Intent serviceIntent = new Intent(getApplicationContext(), com.example.tempo.Services.MediaPlaybackService.class)
                    .setAction(com.example.tempo.Services.MediaPlaybackService.ACTION_PLAY)
                    .putExtra(com.example.tempo.Services.MediaPlaybackService.EXTRA_PLAYLIST, playList)
                    .putExtra(com.example.tempo.Services.MediaPlaybackService.EXTRA_POSITION, position);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                androidx.core.content.ContextCompat.startForegroundService(getApplicationContext(), serviceIntent);
            } else {
                startService(serviceIntent);
            }

            // Open player UI (activity will connect to the service)
            String songName = mySongs.get(position).getName().replace(".mp3", "").replace(".wav", "");
            Intent playerIntent = new Intent(getApplicationContext(), com.example.tempo.ui.MusicPlayerActivity.class)
                    .putExtra("songname", songName)
                    .putExtra("pos", position)
                    .putExtra("songs", playList);
            startActivity(playerIntent);
             overridePendingTransition(0, 0);
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            final File selectedFile = mySongs.get(position);
            showAddToPlaylistDialog(selectedFile);
            return true;
        });
    }

    // Recursively traverse DocumentFile tree and add mp3/wav files to outList
    private void traverseDocumentFile(DocumentFile dir, ArrayList<File> outList) {
        if (dir == null || !dir.isDirectory()) return;
        for (DocumentFile f : dir.listFiles()) {
            if (f.isDirectory()) {
                traverseDocumentFile(f, outList);
            } else if (f.isFile()) {
                String name = f.getName();
                if (name == null) continue;
                if (name.endsWith(".mp3") || name.endsWith(".wav")) {
                    try {
                        // We cannot convert DocumentFile to java.io.File; store a placeholder file with URI string
                        // Use File with path equal to content URI string so existing code can reference it as a unique id
                        outList.add(new File(f.getUri().toString()));
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private void showAddToPlaylistDialog(File file) {
        final ArrayList<Playlist> playlists = playlistRepository.getAllPlaylists();
        final ArrayList<String> names = new ArrayList<>();
        for (Playlist p : playlists) names.add(p.getName());
        names.add("Create new playlist...");

        CharSequence[] options = names.toArray(new CharSequence[0]);

        AlertDialog parentDlg = new AlertDialog.Builder(this)
                .setTitle("Add to playlist")
                .setItems(options, (dialog, which) -> {
                    if (which == playlists.size()) {
                        final EditText input = new EditText(MainActivity.this);
                        AlertDialog createDlg = new AlertDialog.Builder(MainActivity.this)
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
                        try {
                            createDlg.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.white));
                            createDlg.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(android.R.color.white));
                        } catch (Exception ignored) {}
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
        try {
            parentDlg.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.white));
            parentDlg.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(android.R.color.white));
        } catch (Exception ignored) {}

        // Set dialog title color to white in night mode
        if ((getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            SpannableString title = new SpannableString("Add to playlist");
            title.setSpan(new ForegroundColorSpan(getResources().getColor(android.R.color.white)), 0, title.length(), 0);
            parentDlg.setTitle(title);
        }
    }

    public class customAdapter extends BaseAdapter {
        public ArrayList<File> adapterList;
        private final Context ctx;

        public customAdapter(ArrayList<File> list) {
            this.adapterList = list != null ? list : new ArrayList<>();
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
        Intent intent = new Intent(getApplicationContext(), com.example.tempo.Services.MediaPlaybackService.class).setAction(com.example.tempo.Services.MediaPlaybackService.ACTION_PREV);
        startService(intent);
    }

    @Override
    public void onButtonPlay() {
        Intent intent = new Intent(getApplicationContext(), com.example.tempo.Services.MediaPlaybackService.class).setAction(com.example.tempo.Services.MediaPlaybackService.ACTION_TOGGLE);
        startService(intent);
    }

    @Override
    public void onButtonPause() {
        Intent intent = new Intent(getApplicationContext(), com.example.tempo.Services.MediaPlaybackService.class).setAction(com.example.tempo.Services.MediaPlaybackService.ACTION_PAUSE);
        startService(intent);
    }

    @Override
    public void onButtonNext() {
        Intent intent = new Intent(getApplicationContext(), com.example.tempo.Services.MediaPlaybackService.class).setAction(com.example.tempo.Services.MediaPlaybackService.ACTION_NEXT);
        startService(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager != null) notificationManager.cancelAll();
        }
        try {
            MediaControllerCompat controller = MediaControllerCompat.getMediaController(MainActivity.this);
            if (controller != null) controller.unregisterCallback(controllerCallback);
        } catch (Exception ignored) {}
        try {
            if (mediaBrowser != null && mediaBrowser.isConnected()) {
                mediaBrowser.disconnect();
                mediaBrowser = null;
            }
        } catch (Exception ignored) {}

        // Destroy ad view
        if (adView != null) {
            try {
                adView.destroy();
            } catch (Exception ignored) {}
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

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check folder choice on resume (dialog should appear until user picks an option)
        checkAndShowFolderDialog();

        // update now-playing bar
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

        // Resume ad view
        if (adView != null) {
            adView.resume();
        }
    }

    @Override
    protected void onPause() {
        // Pause ad view
        if (adView != null) {
            adView.pause();
        }
        super.onPause();
    }

    @Override
    protected int getNavigationItemId() {
        return com.example.tempo.R.id.songLibraryButton;
    }

    private final MediaControllerCompat.Callback controllerCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            runOnUiThread(() -> updateMiniBarFromController(metadata, MediaControllerCompat.getMediaController(MainActivity.this).getPlaybackState()));
        }

        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            runOnUiThread(() -> updateMiniBarFromController(MediaControllerCompat.getMediaController(MainActivity.this).getMetadata(), state));
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

        // update icon
        playBtn.setImageResource(playing ? com.example.tempo.R.drawable.ic_pause_icon : com.example.tempo.R.drawable.ic_play_icon);

        // show/hide with animation, but skip animation on initial startup when requested
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
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == com.example.tempo.R.id.settings) {
            // open folder picker to change music folder
            promptForMusicFolder();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void promptForMusicFolder() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_CODE_PICK_FOLDER);
        } catch (Exception e) {
            Log.w("MainActivity", "Folder picker not available", e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_FOLDER) {
            if (data == null) return;
            Uri treeUri = data.getData();
            if (treeUri == null) return;
            try {
                // Request read permission persistently for the picked tree
                getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}

            // Try to resolve to a filesystem path for primary storage
            String resolved = resolveTreeUriToPath(treeUri);
            if (resolved != null) {
                SharedPreferences prefs = getSharedPreferences("tempo_prefs", MODE_PRIVATE);
                prefs.edit().putString(PREFS_MUSIC_FOLDER, resolved).apply();
                // refresh display
                displaySongs();
                if (folderChoiceDialog != null && folderChoiceDialog.isShowing()) folderChoiceDialog.dismiss();
            } else {
                // Could not resolve; notify user and still store the uri string as fallback
                SharedPreferences prefs = getSharedPreferences("tempo_prefs", MODE_PRIVATE);
                prefs.edit().putString(PREFS_MUSIC_FOLDER, treeUri.toString()).apply();
                displaySongs();
                if (folderChoiceDialog != null && folderChoiceDialog.isShowing()) folderChoiceDialog.dismiss();
            }
        }
    }

    // Attempt to resolve a tree Uri to a real filesystem path for primary storage trees
    private String resolveTreeUriToPath(Uri treeUri) {
        try {
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            String[] parts = docId.split(":");
            String type = parts.length > 0 ? parts[0] : null;
            String relPath = parts.length > 1 ? parts[1] : "";
            if (type != null && (type.equalsIgnoreCase("primary") || type.equalsIgnoreCase("0"))) {
                String base = Environment.getExternalStorageDirectory().getAbsolutePath();
                if (relPath != null && !relPath.isEmpty()) return base + "/" + relPath;
                return base;
            } else if (type != null) {
                // try /storage/<type>/<relPath>
                String candidate = "/storage/" + type + (relPath != null && !relPath.isEmpty() ? "/" + relPath : "");
                java.io.File f = new java.io.File(candidate);
                if (f.exists()) return candidate;
            }
        } catch (Exception ignored) {}
        return null;
    }

    // Check preferences and show the explanatory first-run dialog if no folder chosen yet
    private void checkAndShowFolderDialog() {
        try {
            SharedPreferences prefs = getSharedPreferences("tempo_prefs", MODE_PRIVATE);
            String stored = prefs.getString(PREFS_MUSIC_FOLDER, null);
            if (stored == null || stored.isEmpty()) {
                showFolderChoiceDialog();
            } else {
                // nothing to do
                if (folderChoiceDialog != null && folderChoiceDialog.isShowing()) {
                    folderChoiceDialog.dismiss();
                }
            }
        } catch (Exception ignored) {}
    }

    private void showFolderChoiceDialog() {
        if (folderChoiceDialog != null && folderChoiceDialog.isShowing()) return;
        try {
            androidx.appcompat.app.AlertDialog.Builder b = new androidx.appcompat.app.AlertDialog.Builder(this);
            b.setTitle("Choose music folder");
            b.setMessage("Tempo needs to know where your music is stored. You can use the default Music folder or pick a custom folder.");
            b.setCancelable(false);
            b.setPositiveButton("Use default", (dialog, which) -> {
                // store default Music folder path
                String defaultPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Music";
                SharedPreferences prefs = getSharedPreferences("tempo_prefs", MODE_PRIVATE);
                prefs.edit().putString(PREFS_MUSIC_FOLDER, defaultPath).apply();
                // refresh songs
                displaySongs();
                try { dialog.dismiss(); } catch (Exception ignored) {}
            });
            b.setNegativeButton("Choose folder", (dialog, which) -> {
                try { dialog.dismiss(); } catch (Exception ignored) {}
                // open the system folder picker
                promptForMusicFolder();
            });
            folderChoiceDialog = b.create();
            folderChoiceDialog.setCanceledOnTouchOutside(false);
            folderChoiceDialog.show();
        } catch (Exception e) {
            Log.w("MainActivity", "Unable to show folder choice dialog", e);
            // fallback to direct picker
            promptForMusicFolder();
        }
    }
}
