package com.example.tempo.repo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.tempo.db.LyricsDatabaseHelper;

public class LyricsRepository {
    private final LyricsDatabaseHelper helper;

    public LyricsRepository(Context ctx) {
        this.helper = new LyricsDatabaseHelper(ctx.getApplicationContext());
    }

    public void save(String key, String plain, String lrc) {
        if (key == null) return;
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(LyricsDatabaseHelper.COL_KEY, key);
        cv.put(LyricsDatabaseHelper.COL_PLAIN, plain);
        cv.put(LyricsDatabaseHelper.COL_LRC, lrc);
        db.insertWithOnConflict(LyricsDatabaseHelper.TABLE_LYRICS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String[] load(String key) {
        if (key == null) return null;
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query(LyricsDatabaseHelper.TABLE_LYRICS, new String[]{LyricsDatabaseHelper.COL_PLAIN, LyricsDatabaseHelper.COL_LRC}, LyricsDatabaseHelper.COL_KEY + "=?", new String[]{key}, null, null, null);
        String plain = null, lrc = null;
        if (c != null) {
            if (c.moveToFirst()) {
                plain = c.getString(0);
                lrc = c.getString(1);
            }
            c.close();
        }
        if (plain == null && lrc == null) return null;
        return new String[]{plain, lrc};
    }
}
