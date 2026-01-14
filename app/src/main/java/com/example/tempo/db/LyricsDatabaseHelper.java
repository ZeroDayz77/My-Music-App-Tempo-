package com.example.tempo.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

public class LyricsDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "lyrics.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_LYRICS = "lyrics";
    public static final String COL_ID = "id";
    public static final String COL_KEY = "song_key"; // either URI or title
    public static final String COL_PLAIN = "plain_text";
    public static final String COL_LRC = "lrc_text"; // synced lyrics

    public LyricsDatabaseHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + TABLE_LYRICS + " (" + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_KEY + " TEXT UNIQUE, " + COL_PLAIN + " TEXT, " + COL_LRC + " TEXT" + ");";
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LYRICS);
        onCreate(db);
    }
}
