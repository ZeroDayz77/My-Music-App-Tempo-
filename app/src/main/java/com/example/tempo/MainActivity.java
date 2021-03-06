package com.example.tempo;

import android.Manifest;
import android.content.Intent;
import android.graphics.drawable.shapes.Shape;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
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

import com.google.android.material.bottomnavigation.BottomNavigationView;
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
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    ListView listView;
    String[] items;
    TextView songduration;
    SearchView searchView;
    customAdapter customAdapter;

    static MediaPlayer mediaPlayer;

    private Toolbar toolbar;


    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar=findViewById(R.id.tempoToolBar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Tempo");

        searchView = findViewById(R.id.search_button);
        songduration = findViewById(R.id.songduration);

        listView = findViewById(R.id.listViewSong);
        runtimePermission();
        //searchSongs();

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
                        Intent musicPlayerActivity = (new Intent(getApplicationContext(),MusicPlayerActivity.class));
                        musicPlayerActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(musicPlayerActivity);
                        overridePendingTransition(0,0);
                        return true;
                    case R.id.playlistButton:
                        startActivity(new Intent(getApplicationContext(),PlaylistsActivity.class));
                        overridePendingTransition(0,0);
                        return true;
                }

                return false;
            }
        });

        // unsued code to have the duration of each song displayed on the song_list_names layout. Did not work as intended and ran out of time to troubleshoot.

//        mediaPlayer = new MediaPlayer();
//
//        String endTime = createSongTime(mediaPlayer.getDuration());
//        songduration.setText(endTime);
    }


    // method for toolbar settings menu as well as cosmetic search feature as I did not solve the issues for the song search function.
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);


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
    public void runtimePermission()
    {
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


    // unused function that didn't work as intended and caused crashes possibly due to not having a thread to handle this separate task.
    private void searchSongs ()
    {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText)
            {
                filter(newText);
                customAdapter = new customAdapter();
                listView.setAdapter(customAdapter);

                return true;
            }
        });
    }


    // filter function for the search above.
    private void filter(String newText) {
        ArrayList<File> mySongs = findSong(Environment.getExternalStorageDirectory());
        for (File file: mySongs)
        {
            if (mySongs.contains(newText.toLowerCase()))
            {
                mySongs.add(file);
            }
        }
    }

    // this method will find if song files are available to be read ( .wav and .mp3 )
    public ArrayList<File> findSong(File file) {
        ArrayList arrayList = new ArrayList();
        File[] files = file.listFiles();
        if(files != null) {
            for (File singlefile : files) {
                if (singlefile.isDirectory() && !singlefile.isHidden()) {
                    arrayList.addAll(findSong(singlefile));
                } else {
                    if (singlefile.getName().endsWith(".wav")) {
                        arrayList.add(singlefile);
                    } else if (singlefile.getName().endsWith(".mp3")) {
                        arrayList.add(singlefile);
                    }
                    else if (singlefile.getName().startsWith("AUD") || singlefile.getName().startsWith("."))
                    {
                        return arrayList;
                    }
                }

            }
        }
        return arrayList;
    }

    // this method will display only mp3 and wav songs and will display the song name using the customAdapter object below.
    void displaySongs ()
    {
        // this worked for my phone to get internal storage.
        String extFilePath = "/storage/595E-F616/Music";
        File myFiles = new File(extFilePath);
        final ArrayList<File> mySongs = findSong(myFiles);

        //this one line is what I had used initially as a default to get a general external storage.
//        final ArrayList<File> mySongs = findSong(Environment.getExternalStorageDirectory());

        items = new String[mySongs.size()];
        for (int i = 0; i < mySongs.size();i++)
        {
            items[i] = mySongs.get(i).getName().toString().replace( ".mp3", "").replace(".wav", "");
        }

        // this adapter was used initially to display the names of the songs without any styling.

        /*ArrayAdapter<String> myAdapter = new ArrayAdapter<String>( this, android.R.layout.simple_list_item_1, items);
        listView.setAdapter(myAdapter);*/


        customAdapter = new customAdapter();
        listView.setAdapter(customAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String songName = (String) listView.getItemAtPosition(i);
                startActivity(new Intent(getApplicationContext(), MusicPlayerActivity.class)
                        .putExtra("songs", mySongs)
                        .putExtra("songname", songName)
                        .putExtra("pos", i));
            }
        });
    }

    // custom adapter used to display the songs.
    class customAdapter extends BaseAdapter
    {

        @Override
        public int getCount() {
            return items.length;
        }

        @Override
        public Object getItem(int i) {
            return null;
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
            songText.setText(items[i]);

            return myView;
        }
    }


    // unused code for the display of time duration of the songs.

//    public String createSongTime(int songDuration)
//    {
//        String time = "";
//        int min = songDuration/1000/60;
//        int sec = songDuration/1000%60;
//
//        time+=min+":";
//
//        if (sec<10)
//        {
//            time+="0";
//        }
//        time+=sec;
//
//        return  time;
//    }
}
