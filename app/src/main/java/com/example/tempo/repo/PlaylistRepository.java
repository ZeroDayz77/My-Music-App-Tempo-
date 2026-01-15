package com.example.tempo.repo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.util.ArrayList;

import com.example.tempo.data.Playlist;
import com.example.tempo.data.PlaylistItem;
import com.example.tempo.db.TempoSongPlaylistDatabase;

public class PlaylistRepository {
    private final TempoSongPlaylistDatabase dbHelper;
    private final Context context;

    public PlaylistRepository(Context context) {
        dbHelper = new TempoSongPlaylistDatabase(context);
        this.context = context;
    }

    public long createPlaylist(String name) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(TempoSongPlaylistDatabase.COLUMN_PLAYLIST_NAME, name);
        values.put(TempoSongPlaylistDatabase.COLUMN_PLAYLIST_CREATED_AT, System.currentTimeMillis());
        long id = db.insert(TempoSongPlaylistDatabase.TABLE_PLAYLISTS, null, values);
        db.close();
        return id;
    }

    public boolean deletePlaylist(int playlistId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rows = db.delete(TempoSongPlaylistDatabase.TABLE_PLAYLISTS, TempoSongPlaylistDatabase.COLUMN_PLAYLIST_ID + "=?", new String[]{String.valueOf(playlistId)});
        db.close();
        return rows > 0;
    }

    public ArrayList<Playlist> getAllPlaylists() {
        ArrayList<Playlist> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(TempoSongPlaylistDatabase.TABLE_PLAYLISTS, null, null, null, null, null, TempoSongPlaylistDatabase.COLUMN_PLAYLIST_NAME + " COLLATE NOCASE ASC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                Playlist p = new Playlist();
                p.setId(cursor.getInt(cursor.getColumnIndexOrThrow(TempoSongPlaylistDatabase.COLUMN_PLAYLIST_ID)));
                p.setName(cursor.getString(cursor.getColumnIndexOrThrow(TempoSongPlaylistDatabase.COLUMN_PLAYLIST_NAME)));
                p.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(TempoSongPlaylistDatabase.COLUMN_PLAYLIST_CREATED_AT)));
                list.add(p);
            }
            cursor.close();
        }
        db.close();
        return list;
    }

    public long addSongToPlaylist(int playlistId, String songId, String title, String uri, long duration) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(TempoSongPlaylistDatabase.COLUMN_ITEM_PLAYLIST_ID, playlistId);
        values.put(TempoSongPlaylistDatabase.COLUMN_ITEM_SONG_ID, songId);
        values.put(TempoSongPlaylistDatabase.COLUMN_ITEM_TITLE, title);
        values.put(TempoSongPlaylistDatabase.COLUMN_ITEM_URI, uri);
        values.put(TempoSongPlaylistDatabase.COLUMN_ITEM_DURATION, duration);
        long id = db.insert(TempoSongPlaylistDatabase.TABLE_PLAYLIST_ITEMS, null, values);
        db.close();
        return id;
    }

    public boolean removeSongFromPlaylist(int playlistItemId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rows = db.delete(TempoSongPlaylistDatabase.TABLE_PLAYLIST_ITEMS, TempoSongPlaylistDatabase.COLUMN_ITEM_ID + "=?", new String[]{String.valueOf(playlistItemId)});
        db.close();
        return rows > 0;
    }

    public ArrayList<PlaylistItem> getItemsForPlaylist(int playlistId) {
        ArrayList<PlaylistItem> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor cursor = db.query(TempoSongPlaylistDatabase.TABLE_PLAYLIST_ITEMS, null, TempoSongPlaylistDatabase.COLUMN_ITEM_PLAYLIST_ID + "=?", new String[]{String.valueOf(playlistId)}, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                int itemId = cursor.getInt(cursor.getColumnIndexOrThrow(TempoSongPlaylistDatabase.COLUMN_ITEM_ID));
                String songId = cursor.getString(cursor.getColumnIndexOrThrow(TempoSongPlaylistDatabase.COLUMN_ITEM_SONG_ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(TempoSongPlaylistDatabase.COLUMN_ITEM_TITLE));
                String uri = cursor.getString(cursor.getColumnIndexOrThrow(TempoSongPlaylistDatabase.COLUMN_ITEM_URI));
                long duration = cursor.getLong(cursor.getColumnIndexOrThrow(TempoSongPlaylistDatabase.COLUMN_ITEM_DURATION));

                boolean exists = false;
                try {
                    String toCheck = (uri != null && !uri.isEmpty()) ? uri : songId;
                    if (toCheck != null && !toCheck.isEmpty()) {
                        if (toCheck.startsWith("content://")) {
                            try {
                                Uri u = Uri.parse(toCheck);
                                DocumentFile df = DocumentFile.fromSingleUri(context, u);
                                if (df != null && df.exists() && df.isFile()) exists = true;
                            } catch (Exception ignored) {}
                        } else {
                            try {
                                File f = new File(toCheck);
                                if (f.exists()) exists = true;
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}

                if (!exists) {
                    // prune missing item silently
                    try {
                        db.delete(TempoSongPlaylistDatabase.TABLE_PLAYLIST_ITEMS, TempoSongPlaylistDatabase.COLUMN_ITEM_ID + "=?", new String[]{String.valueOf(itemId)});
                    } catch (Exception ignored) {}
                    continue; // skip adding to returned list
                }

                PlaylistItem item = new PlaylistItem();
                item.setId(itemId);
                item.setPlaylistId(cursor.getInt(cursor.getColumnIndexOrThrow(TempoSongPlaylistDatabase.COLUMN_ITEM_PLAYLIST_ID)));
                item.setSongId(songId);
                item.setTitle(title);
                item.setUri(uri);
                item.setDuration(duration);
                list.add(item);
            }
            cursor.close();
        }
        db.close();
        return list;
    }

    public boolean isSongInPlaylist(int playlistId, String songId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(TempoSongPlaylistDatabase.TABLE_PLAYLIST_ITEMS, null, TempoSongPlaylistDatabase.COLUMN_ITEM_PLAYLIST_ID + "=? AND " + TempoSongPlaylistDatabase.COLUMN_ITEM_SONG_ID + "=?", new String[]{String.valueOf(playlistId), songId}, null, null, null);
        boolean found = false;
        if (cursor != null) {
            found = cursor.getCount() > 0;
            cursor.close();
        }
        db.close();
        return found;
    }

    public boolean renamePlaylist(int playlistId, String newName) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(TempoSongPlaylistDatabase.COLUMN_PLAYLIST_NAME, newName);
        int rows = db.update(TempoSongPlaylistDatabase.TABLE_PLAYLISTS, values, TempoSongPlaylistDatabase.COLUMN_PLAYLIST_ID + "=?", new String[]{String.valueOf(playlistId)});
        db.close();
        return rows > 0;
    }
}
