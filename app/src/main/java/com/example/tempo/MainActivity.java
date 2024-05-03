package com.example.tempo;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.shapes.Shape;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.media.MediaPlayer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tempo.Services.OnClearRecentService;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;
import java.io.File;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements Playable {
    ListView listView;
    String[] items;
    SearchView searchView;
    customAdapter customAdapter;
    ArrayList<File> filterList;
    boolean isSearchActive = false;
    ArrayList<File> mySongs;
    static MediaPlayer mediaPlayer;
    private Toolbar toolbar;
    NotificationManager notificationManager;
    RecyclerView recyclerView;
    FloatingActionButton NewPlaylistButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        setTheme(R.style.Theme_Tempo_NoActionBar);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        NewPlaylistButton = findViewById(R.id.NewPlaylistButton);
//        NewPlaylistButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Intent intent = new Intent(MainActivity.this, PlaylistAdd.class);
//                startActivity(intent);
//            }
//        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel();
            registerReceiver(broadcastReceiver, new IntentFilter("SONG_CURRENTSONG"));
            startService(new Intent(getBaseContext(), OnClearRecentService.class));
        }

        toolbar = findViewById(R.id.tempoToolBar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Tempo");

        searchView = findViewById(R.id.search_button);

        listView = findViewById(R.id.listViewSong);

        filterList = new ArrayList<>();

        isSearchActive = false;
        runtimePermission();

        //allows for navigation between activities.

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomToolBar);

        bottomNavigationView.setSelectedItemId(R.id.songLibraryButton);

        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {

                switch (item.getItemId()) {
                    case R.id.songLibraryButton:
                        return true;
                    case R.id.songPlayingButton:

                        if (mediaPlayer == null) {
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
                        startActivity(new Intent(getApplicationContext(), PlaylistsActivity.class));
                        overridePendingTransition(0, 0);
                        return true;
                }

                return false;
            }
        });
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CreateMusicNotification.CHANNEL_ID, "notification", NotificationManager.IMPORTANCE_LOW);
            notificationManager = getSystemService(NotificationManager.class);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }

            channel.enableVibration(false);
            channel.setSound(null, null);
            channel.setShowBadge(false);
        }
    }


    // method for toolbar settings menu as well as cosmetic search feature as I did not solve the issues for the song search function.
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        MenuItem searchItem = menu.findItem(R.id.search_button);
        SearchView searchView = (SearchView) searchItem.getActionView();

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

        // for the search feature
        MenuItem.OnActionExpandListener onActionExpandListener = new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                return true;
            }
        };


        menu.findItem(R.id.search_button).setOnActionExpandListener(onActionExpandListener);
        searchView = (SearchView) menu.findItem(R.id.search_button).getActionView();
        searchView.setQueryHint("Name of song...");
        searchView.setIconified(true);
        searchView.onActionViewCollapsed();

        return true;

    }


    // this method will ask on first runtime for permission to access and read phone's internal or external storage. ( this issues apparently differs per device )
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

        // Update the adapter with the appropriate list
        customAdapter.notifyDataSetChanged();

        return filteredList;
    }

    // this method will find if song files are available to be read ( .wav and .mp3 )
    public ArrayList<File> findSong(File file) {
        ArrayList arrayList = new ArrayList();
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

        // this worked for my phone to get internal storage.
        String extFilePath = "/storage/595E-F616/Music";
        File myFiles = new File(extFilePath);
        mySongs = findSong(myFiles);

        //this one line is what I had used initially as a default to get a general external storage.
//        final ArrayList<File> mySongs = findSong(Environment.getExternalStorageDirectory());

        Collections.sort(mySongs, (file1, file2) -> file1.getName().compareToIgnoreCase(file2.getName()));

        items = new String[mySongs.size()];
        for (int i = 0; i < mySongs.size(); i++) {
            items[i] = mySongs.get(i).getName().toString().replace(".mp3", "").replace(".wav", "");
        }

        customAdapter = new customAdapter(mySongs);
        listView.setAdapter(customAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                int position;
                if (isSearchActive) {
                    position = mySongs.indexOf(filterList.get(i));
                } else {
                    position = i;
                }

                String songName = (listView.getItemAtPosition(i).toString());
                startActivity(new Intent(getApplicationContext(), MusicPlayerActivity.class)
                        .putExtra("songs", mySongs)
                        .putExtra("songname", songName)
                        .putExtra("pos", position));
                overridePendingTransition(0, 0);

                CreateMusicNotification.createNotification(MainActivity.this, mySongs.get(MusicPlayerActivity.position).getName().toString().replace(".mp3", "").replace(".wav", ""), R.drawable.ic_play_icon, MusicPlayerActivity.position, mySongs.size());

//                NotificationCompat.Builder builder = new NotificationCompat.Builder(MainActivity.this, "notification");
//                builder.setContentTitle(getString(R.string.app_name));
//                builder.setContentText("Currently Playing: " + mySongs.get(i).getName().toString().replace( ".mp3", "").replace(".wav", ""));
//                builder.setSmallIcon(R.drawable.ic_music);
//                builder.setSilent(true);
//                builder.setAutoCancel(true);
//
//                NotificationManagerCompat managerCompat = NotificationManagerCompat.from(MainActivity.this);
//                managerCompat.notify(1,builder.build());
            }
        });
    }

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getExtras().getString("actionname");

            switch (action) {
                case CreateMusicNotification.SKIPSONGPREV:
                    onButtonPrevious();
                    break;
                case CreateMusicNotification.BUTTONPLAY:
                    if (mediaPlayer.isPlaying())
                        onButtonPause();
                    else
                        onButtonPlay();
                    break;
                case CreateMusicNotification.SKIPSONGNEXT:
                    onButtonNext();
                    break;
            }
        }
    };

    // custom adapter used to display the songs.
    class customAdapter extends BaseAdapter {

        private ArrayList<File> adapterList;

        public customAdapter(ArrayList<File> list) {
            adapterList = list;
        }

        public void updateList(ArrayList<File> newList) {
            adapterList = newList;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return adapterList.size();
        }

        @Override
        public Object getItem(int i) {
            return adapterList.get(i);
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }


        //this will help to display the song names and allow for the layout to display them as suited in the xml
        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {

            View myView = getLayoutInflater().inflate(R.layout.song_list_names, null);
            TextView songText = myView.findViewById(R.id.songname);
            songText.setSelected(true);
            songText.setEnabled(true);
            songText.setText(adapterList.get(i).getName().replace(".mp3", "").replace(".wav", ""));

            return myView;
        }
    }

    @Override
    public void onButtonPrevious() {
        MusicPlayerActivity.position--;
        CreateMusicNotification.createNotification(MainActivity.this, mySongs.get(MusicPlayerActivity.position).getName().toString().replace(".mp3", "").replace(".wav", ""),
                R.drawable.ic_pause_icon, MusicPlayerActivity.position, mySongs.size() - 1);
    }

    @Override
    public void onButtonPlay() {
        CreateMusicNotification.createNotification(MainActivity.this, mySongs.get(MusicPlayerActivity.position).getName().toString().replace(".mp3", "").replace(".wav", ""),
                R.drawable.ic_pause_icon, MusicPlayerActivity.position, mySongs.size() - 1);
    }

    @Override
    public void onButtonPause() {
        CreateMusicNotification.createNotification(MainActivity.this, mySongs.get(MusicPlayerActivity.position).getName().toString().replace(".mp3", "").replace(".wav", ""),
                R.drawable.ic_play_icon, MusicPlayerActivity.position, mySongs.size() - 1);
    }

    @Override
    public void onButtonNext() {
        MusicPlayerActivity.position++;
        CreateMusicNotification.createNotification(MainActivity.this, mySongs.get(MusicPlayerActivity.position).getName().toString().replace(".mp3", "").replace(".wav", ""),
                R.drawable.ic_pause_icon, MusicPlayerActivity.position, mySongs.size() - 1);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.cancelAll();
        }

        unregisterReceiver(broadcastReceiver);
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
