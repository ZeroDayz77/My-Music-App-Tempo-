package com.example.tempo.lyrics;

public interface LyricsProvider {
    String[] lookup(String title, String artist);
}

