package com.example.tempo.ui;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.util.DisplayMetrics;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.MobileAds;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public abstract class BaseBottomNavActivity extends AppCompatActivity {

    protected BottomNavigationView bottomNavigationView;

    // Central AdView reference for activities that include bottom_tool_bar
    private AdView adView;
    private boolean adLoaded = false;

    /**
     * Return the menu id that should be selected for this activity (eg R.id.songLibraryButton)
     */
    @IdRes
    protected abstract int getNavigationItemId();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize Mobile Ads SDK once for the app. Safe to call multiple times.
        try {
            MobileAds.initialize(this, initializationStatus -> { /* no-op */ });
        } catch (Exception e) {
            Log.w("BaseBottomNavActivity", "MobileAds.initialize failed", e);
        }
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

        // For MusicPlayerActivity, do not show the bottom banner ad.
        // Hide the AdView (if present) and ensure the BottomNavigationView aligns to parent bottom.
        try {
            if (this instanceof MusicPlayerActivity) {
                adView = findViewById(com.example.tempo.R.id.adView);
                if (adView != null) {
                    try { adView.setVisibility(android.view.View.GONE); } catch (Exception ignored) {}
                }
                BottomNavigationView bottom = findViewById(com.example.tempo.R.id.bottomToolBar);
                if (bottom != null) {
                    ViewGroup.LayoutParams lp = bottom.getLayoutParams();
                    if (lp instanceof RelativeLayout.LayoutParams) {
                        RelativeLayout.LayoutParams rlp = (RelativeLayout.LayoutParams) lp;
                        try {
                            // Remove the "above adView" rule and align parent bottom so nav isn't squished
                            rlp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                            // removeRule is API 17+. Attempt to remove if available.
                            try { rlp.removeRule(RelativeLayout.ABOVE); } catch (NoSuchMethodError ignored) {}
                        } catch (Exception ignored) {}
                        bottom.setLayoutParams(rlp);
                    }
                }
                return;
            }
        } catch (Exception e) {
            Log.w("BaseBottomNavActivity", "Skipping banner for MusicPlayerActivity failed", e);
        }

        // Find AdView (if present in this activity's layout) and load/resume it
        try {
            adView = findViewById(com.example.tempo.R.id.adView);
            if (adView != null) {
                // Load ad only once per activity instance
                if (!adLoaded) {
                    // Use adaptive banner sizing so the ad fills the available width
                    try {
                        AdSize adSize = getAdSize();
                        if (adSize != null) adView.setAdSize(adSize);
                    } catch (Exception ignored) {}

                    AdRequest adRequest = new AdRequest.Builder().build();
                    try { adView.loadAd(adRequest); } catch (Exception e) { Log.w("BaseBottomNavActivity", "adView.loadAd failed", e); }
                    adLoaded = true;
                }
                try { adView.resume(); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Log.w("BaseBottomNavActivity", "AdView setup failed", e);
        }
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

        // Pause ad view if exists
        try {
            if (adView != null) {
                adView.pause();
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Destroy ad view
        try {
            if (adView != null) {
                adView.destroy();
                adView = null;
            }
        } catch (Exception ignored) {}
    }

    // Compute adaptive banner AdSize based on current screen width
    private AdSize getAdSize() {
        try {
            DisplayMetrics outMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(outMetrics);
            float widthPixels = outMetrics.widthPixels;
            float density = outMetrics.density;
            int adWidth = (int) (widthPixels / density);
            // Request an adaptive banner of the current orientation that matches the width
            return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth);
        } catch (Exception e) {
            Log.w("BaseBottomNavActivity", "Failed to compute adaptive ad size", e);
            return AdSize.BANNER;
        }
    }
}
