package com.example.tempo.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.tempo.data.Playlist;

import java.util.ArrayList;

public class PlaylistAdapter extends BaseAdapter {
    private final Context context;
    private final ArrayList<Playlist> playlists;

    public PlaylistAdapter(Context context, ArrayList<Playlist> playlists) {
        this.context = context;
        this.playlists = playlists;
    }

    @Override
    public int getCount() {
        return playlists.size();
    }

    @Override
    public Object getItem(int position) {
        return playlists.get(position);
    }

    @Override
    public long getItemId(int position) {
        return playlists.get(position).getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(com.example.tempo.R.layout.playlist_list_item, parent, false);
        }

        ImageView icon = convertView.findViewById(com.example.tempo.R.id.playlistIcon);
        TextView name = convertView.findViewById(com.example.tempo.R.id.playlistName);

        Playlist p = playlists.get(position);
        name.setText(p.getName());

        icon.setImageResource(com.example.tempo.R.drawable.ic_folder_icon);

        return convertView;
    }
}
