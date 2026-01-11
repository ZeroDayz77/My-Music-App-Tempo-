package com.example.tempo.data;

public class PlaylistItem {
    private int id;
    private int playlistId;
    private String songId; // a unique identifier for the song, e.g., path or filename
    private String title;
    private String uri;
    private long duration;

    public PlaylistItem() {}

    public PlaylistItem(int id, int playlistId, String songId, String title, String uri, long duration) {
        this.id = id;
        this.playlistId = playlistId;
        this.songId = songId;
        this.title = title;
        this.uri = uri;
        this.duration = duration;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(int playlistId) {
        this.playlistId = playlistId;
    }

    public String getSongId() {
        return songId;
    }

    public void setSongId(String songId) {
        this.songId = songId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }
}

