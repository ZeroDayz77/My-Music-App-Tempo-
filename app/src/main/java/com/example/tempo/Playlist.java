package com.example.tempo;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

public class Playlist extends AppCompatActivity {

    private String name;
    private int songCounter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void setName(String name){
        this.name = name;
    }
}