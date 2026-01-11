package com.example.tempo.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

public class TempoSongPlaylistDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "UserPlaylists.db";
    private static final int DATABASE_VERSION = 2;

    // playlists table
    public static final String TABLE_PLAYLISTS = "playlists";
    public static final String COLUMN_PLAYLIST_ID = "id";
    public static final String COLUMN_PLAYLIST_NAME = "name";
    public static final String COLUMN_PLAYLIST_CREATED_AT = "created_at";

    // playlist items table
    public static final String TABLE_PLAYLIST_ITEMS = "playlist_items";
    public static final String COLUMN_ITEM_ID = "id";
    public static final String COLUMN_ITEM_PLAYLIST_ID = "playlist_id";
    public static final String COLUMN_ITEM_SONG_ID = "song_id";
    public static final String COLUMN_ITEM_TITLE = "song_title";
    public static final String COLUMN_ITEM_URI = "song_uri";
    public static final String COLUMN_ITEM_DURATION = "song_duration";

    public TempoSongPlaylistDatabase(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createPlaylists =
                "CREATE TABLE " + TABLE_PLAYLISTS +
                        " (" + COLUMN_PLAYLIST_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COLUMN_PLAYLIST_NAME + " TEXT UNIQUE NOT NULL, " +
                        COLUMN_PLAYLIST_CREATED_AT + " INTEGER" +
                        ");";

        String createPlaylistItems =
                "CREATE TABLE " + TABLE_PLAYLIST_ITEMS +
                        " (" + COLUMN_ITEM_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COLUMN_ITEM_PLAYLIST_ID + " INTEGER NOT NULL, " +
                        COLUMN_ITEM_SONG_ID + " TEXT NOT NULL, " +
                        COLUMN_ITEM_TITLE + " TEXT, " +
                        COLUMN_ITEM_URI + " TEXT, " +
                        COLUMN_ITEM_DURATION + " INTEGER, " +
                        "UNIQUE(" + COLUMN_ITEM_PLAYLIST_ID + "," + COLUMN_ITEM_SONG_ID + "), " +
                        "FOREIGN KEY(" + COLUMN_ITEM_PLAYLIST_ID + ") REFERENCES " + TABLE_PLAYLISTS + "(" + COLUMN_PLAYLIST_ID + ") ON DELETE CASCADE" +
                        ");";

        db.execSQL(createPlaylists);
        db.execSQL(createPlaylistItems);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PLAYLIST_ITEMS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PLAYLISTS);
        onCreate(db);
    }
}

