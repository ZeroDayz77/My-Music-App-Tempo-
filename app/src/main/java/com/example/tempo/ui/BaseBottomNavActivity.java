package com.example.tempo.ui;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public abstract class BaseBottomNavActivity extends AppCompatActivity {

    protected BottomNavigationView bottomNavigationView;

    /**
     * Return the menu id that should be selected for this activity (eg R.id.songLibraryButton)
     */
    @IdRes
    protected abstract int getNavigationItemId();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // child activities will call setContentView and may have the bottom nav in their layout
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            bottomNavigationView = findViewById(com.example.tempo.R.id.bottomToolBar);
            if (bottomNavigationView != null) {
                int id = getNavigationItemId();
                if (id != 0) {
                    // Post selection to avoid interfering with touch input during activity resume transitions
                    bottomNavigationView.post(() -> {
                        try { bottomNavigationView.setSelectedItemId(id); } catch (Exception ignored) {}
                    });
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Clear selection so other activities don't show our item selected
        try {
            if (bottomNavigationView == null) bottomNavigationView = findViewById(com.example.tempo.R.id.bottomToolBar);
            if (bottomNavigationView != null) {
                // Post clearing slightly later so the upcoming activity can process its resume and touch events first
                bottomNavigationView.postDelayed(() -> {
                    try {
                        Menu menu = bottomNavigationView.getMenu();
                        for (int i = 0; i < menu.size(); i++) {
                            MenuItem mi = menu.getItem(i);
                            mi.setChecked(false);
                        }
                    } catch (Exception ignored) {}
                }, 60);
            }
        } catch (Exception ignored) {}
    }
}
