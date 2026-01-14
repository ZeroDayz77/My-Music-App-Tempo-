package com.example.tempo.ui;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.TextViewCompat;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import androidx.appcompat.app.AlertDialog;

import com.example.tempo.repo.LyricsRepository;
import com.example.tempo.lyrics.LrcLibLyricsProvider;
import com.example.tempo.lyrics.LyricsProvider;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.ViewGroup;
import com.example.tempo.util.AdManager;

public class LyricsActivity extends BaseBottomNavActivity {
     TextView lyricsPlainText;
     TextView lyricsStatus;
     ScrollView lyricsScroll;
     LinearLayout lyricsLinesContainer;
     FloatingActionButton fabToggle;
     LyricsRepository repo;

    String songName;
    String songArtist;
    String songUri;

    Handler uiHandler = new Handler();
    LyricsProvider provider = new LrcLibLyricsProvider();
    boolean showingSynced = true; // toggle state
    android.support.v4.media.MediaBrowserCompat mediaBrowser;
    android.support.v4.media.session.MediaControllerCompat mediaController;
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "lyrics_prefs";
    private static final String KEY_SHOW_SYNCED = "show_synced";

    private long songStartSystemTimeMs = 0;
    private long songStartPositionMs = 0;

    // For synced lyrics
    static class LrcLine {
        long timeMs;
        String text;
    }

    List<LrcLine> lrcLines = new ArrayList<>();
    boolean autoScroll = true;
    int currentIndex = 0;
    // Track current song identity so we reliably detect song changes
    private String currentTrackId = null;
    // Record the last time the user performed a seek so we can debounce controller updates
    private long lastUserSeekTimeMs = 0;

    // Increase to make highlighting appear earlier;
    private static final int HIGHLIGHT_LEAD_MS = 250;

    // Treat the string literal "null" or empty/whitespace as no-value
    private boolean isBlankOrNullLiteral(String s) {
        if (s == null) return true;
        String t = s.trim();
        return t.isEmpty() || t.equalsIgnoreCase("null");
    }

    Runnable highlightRunnable = new Runnable() {
        @Override
        public void run() {
            if (!autoScroll || lrcLines.isEmpty()) return;

            long nowMs = getCurrentPlaybackPosition() + HIGHLIGHT_LEAD_MS;

            // binary search for the current index (largest i where timeMs <= nowMs)
            int lo = 0, hi = lrcLines.size() - 1, found = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                long t = lrcLines.get(mid).timeMs;
                if (t <= nowMs) {
                    found = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            currentIndex = Math.max(0, found);
            // update UI: if found == -1, clear highlights and schedule until first timestamp
            if (found < 0) {
                clearAllHighlights();
                long firstTime = lrcLines.get(0).timeMs;
                long diff = firstTime - nowMs;
                long delay = diff > 0 ? Math.min(Math.max(diff, 50), 2000) : 500;
                uiHandler.postDelayed(this, delay);
                return;
            }

            // highlight and scroll
            highlightLine(currentIndex);

            // schedule next update > target the next timestamp
            long delay = 500;
            if (currentIndex + 1 < lrcLines.size()) {
                long nextTime = lrcLines.get(currentIndex + 1).timeMs;
                long diff = nextTime - nowMs;
                if (diff > 0) delay = Math.min(Math.max(diff, 50), 2000);
                else delay = 200;
            }
            uiHandler.postDelayed(this, delay);
        }
    };

    private long getCurrentPlaybackPosition() {
        try {
            android.support.v4.media.session.MediaControllerCompat controller = android.support.v4.media.session.MediaControllerCompat.getMediaController(LyricsActivity.this);
            if (controller == null) {
                // fall back to local baseline if available
                if (songStartSystemTimeMs > 0) {
                    long now = android.os.SystemClock.elapsedRealtime();
                    long delta = now - songStartSystemTimeMs;
                    return songStartPositionMs + delta;
                }
                return 0;
            }
            android.support.v4.media.session.PlaybackStateCompat state = controller.getPlaybackState();
            if (state == null) return 0;
            long pos = state.getPosition();
            long lastUpdate = state.getLastPositionUpdateTime();
            float speed = state.getPlaybackSpeed();
            long now = android.os.SystemClock.elapsedRealtime();
            if (lastUpdate > 0) {
                long delta = now - lastUpdate;
                // advance position by playback speed * elapsed ms
                pos += (long) (delta * speed);
            } else {
                // Some playback implementations may not supply lastUpdate; fall back to our baseline if set
                if (songStartSystemTimeMs > 0) {
                    long delta = now - songStartSystemTimeMs;
                    pos = songStartPositionMs + (long) (delta * (double) Math.max(0.0f, speed));
                }
            }
            return pos;
        } catch (Exception ignored) {}
        return 0;
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.example.tempo.R.layout.activity_lyrics);

        Toolbar toolbar = findViewById(com.example.tempo.R.id.lyricsToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Song");
        }

        lyricsPlainText = findViewById(com.example.tempo.R.id.lyricsPlainText);
        lyricsStatus = findViewById(com.example.tempo.R.id.lyricsStatus);
        lyricsScroll = findViewById(com.example.tempo.R.id.lyricsScroll);
        lyricsLinesContainer = findViewById(com.example.tempo.R.id.lyricsLinesContainer);
        fabToggle = findViewById(com.example.tempo.R.id.fabToggle);

        // Initialize prefs and FAB immediately so it's active even if cached lyrics cause early return
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        showingSynced = prefs.getBoolean(KEY_SHOW_SYNCED, true);
        try {
            android.graphics.drawable.Drawable d = androidx.core.content.ContextCompat.getDrawable(this, android.R.drawable.ic_menu_view);
            if (d != null) {
                fabToggle.setImageDrawable(d);
                fabToggle.setImageTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(com.example.tempo.R.color.teal_700)));
            }
        } catch (Exception ignored) {}
        updateFabIcon();
        fabToggle.setOnClickListener(v -> {
            showingSynced = !showingSynced;
            prefs.edit().putBoolean(KEY_SHOW_SYNCED, showingSynced).apply();
            updateFabIcon();
            if (showingSynced) {
                // stop plain auto-scroll if running
                clearNoLyricsOverlay();
                lyricsPlainText.setVisibility(View.GONE);
                lyricsLinesContainer.setVisibility(lrcLines.isEmpty() ? View.GONE : View.VISIBLE);
                autoScroll = true;
                uiHandler.removeCallbacks(highlightRunnable);
                uiHandler.post(highlightRunnable);
            } else {
                // switch to plain: stop highlight loop
                uiHandler.removeCallbacks(highlightRunnable);
                autoScroll = false; // highlight loop disabled
                lyricsLinesContainer.setVisibility(View.GONE);

                // Populate plain text if empty: prefer persisted DB plain lyrics, else join lrcLines
                String key = songUri != null ? songUri : songName;
                boolean filled = false;
                if (TextUtils.isEmpty(lyricsPlainText.getText())) {
                    if (key != null) {
                        try {
                            String[] existing = repo.load(key);
                            if (existing != null && !TextUtils.isEmpty(existing[0])) {
                                setPlainTextWithFade(existing[0]);
                                filled = true;
                            }
                        } catch (Exception ignored) {}
                    }
                    if (!filled && !lrcLines.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < lrcLines.size(); i++) {
                            if (i > 0) sb.append('\n');
                            sb.append(lrcLines.get(i).text == null ? "" : lrcLines.get(i).text);
                        }
                        if (sb.length() > 0) {
                            setPlainTextWithFade(sb.toString());
                            filled = true;
                        }
                    }
                } else {
                    filled = lyricsPlainText.getText().length() > 0;
                }

                if (!filled) {
                    // nothing to show yet > start a fetch and show retry/status
                    lyricsStatus.setVisibility(View.VISIBLE);
                    lyricsStatus.setText(getString(com.example.tempo.R.string.lyrics_looking_up));
                    fetchAndStoreLyrics();
                } else {
                    // ensure plain text is visible and readable
                    clearNoLyricsOverlay();
                    lyricsPlainText.setTextColor(getResources().getColor(android.R.color.white));
                    lyricsPlainText.setVisibility(View.VISIBLE);
                    // reset scroll to top so content is visible
                    lyricsScroll.post(() -> lyricsScroll.scrollTo(0, 0));
                    // plain view intentionally does NOT auto-scroll; just show the text
                }
            }
        });

        // Ensure scroll view reports clicks for accessibility and linting
        lyricsScroll.setClickable(true);
        lyricsScroll.setOnClickListener(v -> {
            // no-op; ensures performClick is implemented on the view
        });

        // Increase base font size a bit and make plain text match synced-lines appearance
        lyricsPlainText.setTextSize(20);
        TextViewCompat.setTextAppearance(lyricsPlainText, android.R.style.TextAppearance_Material_Body1);
        lyricsPlainText.setPadding(0, dpToPx(12), 0, dpToPx(12));

        repo = new LyricsRepository(this);

        songName = getIntent().getStringExtra("song_name");
        songArtist = getIntent().getStringExtra("song_artist");
        songUri = getIntent().getStringExtra("song_uri");

        String key = songUri != null ? songUri : (songName != null ? songName : null);

        if (key != null) {
            String[] loaded = repo.load(key);
            if (loaded != null) {
                // show cached lyrics if meaningful
                String lPlain = loaded.length > 0 ? loaded[0] : null;
                String lSynced = loaded.length > 1 ? loaded[1] : null;
                if (!isBlankOrNullLiteral(lSynced)) {
                    showSyncedLyrics(lSynced);
                    // ensure overlay is cleared if cached synced lyrics are shown immediately
                    clearNoLyricsOverlay();
                    if (lyricsStatus != null) lyricsStatus.setVisibility(View.GONE);
                    return;
                } else if (!isBlankOrNullLiteral(lPlain)) {
                    setPlainTextWithFade(lPlain);
                    // ensure overlay is cleared if cached plain lyrics are shown immediately
                    clearNoLyricsOverlay();
                    if (lyricsStatus != null) lyricsStatus.setVisibility(View.GONE);
                    return;
                }
                // otherwise fall through to fetch
            }
        }

        if (isOnline()) {
            lyricsStatus.setVisibility(View.VISIBLE);
            lyricsStatus.setText(getString(com.example.tempo.R.string.lyrics_looking_up_retry));
            lyricsStatus.setOnClickListener(v -> {
                // allow retry
                lyricsStatus.setText(getString(com.example.tempo.R.string.lyrics_looking_up));
                fetchAndStoreLyrics();
            });
            fetchAndStoreLyrics();
        } else {
            lyricsStatus.setVisibility(View.VISIBLE);
            lyricsStatus.setText(getString(com.example.tempo.R.string.lyrics_offline));
        }

        // Pause auto-scroll on user touch, resume after 3s of inactivity
        // Attach touch listener to inner container to avoid ScrollView performClick lint
        lyricsLinesContainer.setOnTouchListener(new View.OnTouchListener() {
            boolean userTouch = false;
            final Runnable resumeRunnable = () -> {
                autoScroll = true;
                uiHandler.post(highlightRunnable);
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                    userTouch = true;
                    autoScroll = false;
                    uiHandler.removeCallbacks(resumeRunnable);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    // resume after 3s
                    uiHandler.removeCallbacks(resumeRunnable);
                    uiHandler.postDelayed(resumeRunnable, 3000);
                    // accessibility > report click
                    v.performClick();
                }
                return false;
            }
        });
    }

    private void fetchAndStoreLyrics() {
        // show looking-up state when starting a fetch
        lyricsStatus.setVisibility(View.VISIBLE);
        lyricsStatus.setText(getString(com.example.tempo.R.string.lyrics_looking_up));

        new Thread(() -> {
            final String titleToLookup = songName != null ? songName.trim() : "";
            final String strippedTitle = titleToLookup.replaceAll("(?i)\\.(mp3|wav|flac|m4a)$", "");

            java.util.List<com.example.tempo.lyrics.LrcLibLyricsProvider.Candidate> candidates = null;
            try {
                if (provider instanceof com.example.tempo.lyrics.LrcLibLyricsProvider) {
                    candidates = ((com.example.tempo.lyrics.LrcLibLyricsProvider) provider).searchCandidates(strippedTitle);
                }
            } catch (Exception ignored) {}

            final java.util.List<com.example.tempo.lyrics.LrcLibLyricsProvider.Candidate> finalCandidates = candidates;

            runOnUiThread(() -> {
                // Multiple candidates -> show chooser
                if (finalCandidates != null && finalCandidates.size() > 1) {
                    CharSequence[] items = new CharSequence[finalCandidates.size()];
                    for (int i = 0; i < finalCandidates.size(); i++) {
                        com.example.tempo.lyrics.LrcLibLyricsProvider.Candidate c = finalCandidates.get(i);
                        String artist = c.artistName != null ? (" — " + c.artistName) : "";
                        items[i] = (c.trackName != null ? c.trackName : "Unknown") + artist;
                    }
                    AlertDialog.Builder b = new AlertDialog.Builder(LyricsActivity.this);
                    b.setTitle("Choose lyrics");
                    b.setItems(items, (dialog, which) -> {
                        com.example.tempo.lyrics.LrcLibLyricsProvider.Candidate chosen = finalCandidates.get(which);
                        showCandidateAndSave(chosen);
                    });
                    b.setNegativeButton("Cancel", (d, w) -> d.dismiss());
                    androidx.appcompat.app.AlertDialog dlg = b.create();
                    dlg.show();
                    try {
                        int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                        if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
                            android.widget.Button neg = dlg.getButton(android.content.DialogInterface.BUTTON_NEGATIVE);
                            if (neg != null) neg.setTextColor(getResources().getColor(android.R.color.white));
                        }
                    } catch (Exception ignored) {}
                    return;
                }

                // Single candidate -> use it
                if (finalCandidates != null && finalCandidates.size() == 1) {
                    showCandidateAndSave(finalCandidates.get(0));
                    return;
                }

                // Fallback: provider.lookup
                String[] result = null;
                try {
                    result = provider.lookup(strippedTitle, songArtist);
                } catch (Exception ignored) {}

                if (result != null) {
                    String plain = result.length > 0 ? result[0] : null;
                    String lrc = result.length > 1 ? result[1] : null;
                    if (!isBlankOrNullLiteral(lrc)) {
                        showSyncedLyrics(lrc);
                        // show synced and ensure status overlay removed
                        try { clearNoLyricsOverlay(); if (lyricsStatus != null) lyricsStatus.setVisibility(View.GONE); } catch (Exception ignored) {}
                        if (!showingSynced) {
                            lyricsLinesContainer.setVisibility(View.GONE);
                            lyricsPlainText.setVisibility(View.VISIBLE);
                        }
                        lyricsStatus.setVisibility(View.GONE);
                        String key = songUri != null ? songUri : songName;
                        repo.save(key, plain, lrc);
                    } else if (!isBlankOrNullLiteral(plain)) {
                        setPlainTextWithFade(plain);
                        // ensure overlay is cleared if cached plain lyrics are shown immediately
                        clearNoLyricsOverlay();
                        try { if (lyricsStatus != null) lyricsStatus.setVisibility(View.GONE); } catch (Exception ignored) {}
                        String key = songUri != null ? songUri : songName;
                        repo.save(key, plain, lrc);
                    } else {
                        // No lyrics found -> show centered no-lyrics UI
                        showNoLyricsFound();
                    }
                } else {
                    // no result -> show centered no-lyrics UI
                    showNoLyricsFound();
                }
            });
        }).start();
    }

    private void showCandidateAndSave(com.example.tempo.lyrics.LrcLibLyricsProvider.Candidate c) {
        String plain = c.plainLyrics;
        String lrc = c.syncedLyrics;
        String key = songUri != null ? songUri : songName;
        repo.save(key, plain, lrc);
        if (!TextUtils.isEmpty(lrc)) {
            showSyncedLyrics(lrc);
            // show synced and ensure status overlay removed
            try { clearNoLyricsOverlay(); if (lyricsStatus != null) lyricsStatus.setVisibility(View.GONE); } catch (Exception ignored) {}
            if (!showingSynced) {
                lyricsLinesContainer.setVisibility(View.GONE);
                lyricsPlainText.setVisibility(View.VISIBLE);
            }
        } else {
            clearNoLyricsOverlay();
            setPlainTextWithFade(plain == null ? "" : plain);
        }
        lyricsStatus.setVisibility(View.GONE);
    }

    private void showSyncedLyrics(String lrcText) {
        // Clear any previous "no lyrics" overlay before attempting to render synced lyrics
        clearNoLyricsOverlay();
        if (TextUtils.isEmpty(lrcText)) {
            showNoLyricsFound();
            return;
        }
        lrcLines.clear();
        // parse LRC timestamps like [mm:ss.xx] or [hh:mm:ss.xx]
        Pattern p = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]\\s*(.*)");
        String[] lines = lrcText.split("\\r?\\n");
        for (String ln : lines) {
            Matcher m = p.matcher(ln);
            if (m.find()) {
                try {
                    String g1 = m.group(1);
                    String g2 = m.group(2);
                    String g3 = m.group(3);
                    String g4 = m.group(4);
                    if (g1 == null || g2 == null) continue;
                    int min = Integer.parseInt(g1);
                    int sec = Integer.parseInt(g2);
                    int ms = 0;
                    if (g3 != null) {
                        if (g3.length() == 1) ms = Integer.parseInt(g3) * 100;
                        else if (g3.length() == 2) ms = Integer.parseInt(g3) * 10;
                        else ms = Integer.parseInt(g3);
                    }
                    long timeMs = (long) min * 60L * 1000L + (long) sec * 1000L + (long) ms;
                    String text = g4 != null ? g4 : "";
                    LrcLine ll = new LrcLine();
                    ll.timeMs = timeMs;
                    ll.text = text;
                    lrcLines.add(ll);
                } catch (Exception ignored) {}
            }
        }
        if (lrcLines.isEmpty()) {
            // not true LRC text — fallback to plain (animated). Normalize null->empty so "null" isn't shown.
            setPlainTextWithFade(isBlankOrNullLiteral(lrcText) ? "" : lrcText);
            return;
        }
        // sort by time (compatible with API 21+)
        Collections.sort(lrcLines, (a, b) -> Long.compare(a.timeMs, b.timeMs));

        // populate views with cross-fade, then continue with baseline/setup in the onComplete callback
        populateSyncedLinesWithFade(lrcLines, () -> {
            // Ensure overlay cleared once the synced lines are populated
            clearNoLyricsOverlay();
            // also explicitly hide status to avoid any z-order issues
            try { if (lyricsStatus != null) lyricsStatus.setVisibility(View.GONE); } catch (Exception ignored) {}
            // no-op here; baseline/seek adjustments done below after this call too
        });

        // Set local playback clock baseline: prefer the MediaController playback state if available
        try {
            android.support.v4.media.session.MediaControllerCompat controller = android.support.v4.media.session.MediaControllerCompat.getMediaController(LyricsActivity.this);
            if (controller != null && controller.getPlaybackState() != null) {
                android.support.v4.media.session.PlaybackStateCompat st = controller.getPlaybackState();
                long stPos = st.getPosition();
                long stLast = st.getLastPositionUpdateTime();
                if (stLast > 0) {
                    songStartPositionMs = stPos + (long) ((android.os.SystemClock.elapsedRealtime() - stLast) * st.getPlaybackSpeed());
                    songStartSystemTimeMs = android.os.SystemClock.elapsedRealtime();
                } else {
                    songStartPositionMs = stPos;
                    songStartSystemTimeMs = android.os.SystemClock.elapsedRealtime();
                }
            } else {
                // fallback: assume just-started baseline
                songStartPositionMs = 0;
                songStartSystemTimeMs = android.os.SystemClock.elapsedRealtime();
            }
        } catch (Exception ignored) {
            songStartPositionMs = 0;
            songStartSystemTimeMs = android.os.SystemClock.elapsedRealtime();
        }

        // start auto highlight loop after the layout is ready
        autoScroll = true;
        currentIndex = 0;
        uiHandler.removeCallbacks(highlightRunnable);
        // Post start after a short delay to allow the fade/populate to complete
        lyricsLinesContainer.post(() -> uiHandler.postDelayed(highlightRunnable, 250));
    }

    private void highlightLine(int index) {
        int childCount = lyricsLinesContainer.getChildCount();
        if (childCount == 0) return;
        if (index < 0) {
            // clear all highlights
            for (int i = 0; i < childCount; i++) {
                View child = lyricsLinesContainer.getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setTextColor(getResources().getColor(android.R.color.white));
                 }
             }
             return;
         }
         if (index >= childCount) index = childCount - 1;
         for (int i = 0; i < childCount; i++) {
             View child = lyricsLinesContainer.getChildAt(i);
             if (child instanceof TextView) {
                 TextView tv = (TextView) child;
                 if (i == index) {
                    tv.setTextColor(getResources().getColor(com.example.tempo.R.color.teal_700));
                    // scroll to make it visible after layout
                    final int y = tv.getTop();
                    lyricsScroll.post(() -> lyricsScroll.smoothScrollTo(0, y));
                 } else {
                    tv.setTextColor(getResources().getColor(android.R.color.white));
                 }
              }
          }
      }

     private int dpToPx(int dp) {
         float density = getResources().getDisplayMetrics().density;
         return Math.round(dp * density);
     }

     private void clearAllHighlights() {
          int childCount = lyricsLinesContainer.getChildCount();
          for (int i = 0; i < childCount; i++) {
              View child = lyricsLinesContainer.getChildAt(i);
              if (child instanceof TextView) ((TextView) child).setTextColor(getResources().getColor(android.R.color.white));
          }
      }

    // Animate plain lyrics updates with a short fade-out / fade-in
     private void setPlainTextWithFade(final String text) {
         if (lyricsPlainText == null) return;
         final String safe = text == null ? "" : text;
         uiHandler.post(() -> {
             try {
                 if (safe.trim().isEmpty()) {
                     // nothing useful to show
                     showNoLyricsFound();
                     return;
                 }
                // Clear any previously shown "no lyrics" overlay before showing content
                clearNoLyricsOverlay();
                lyricsPlainText.animate().cancel();
                 // Ensure visible so alpha animation works
                 if (lyricsPlainText.getVisibility() != View.VISIBLE) lyricsPlainText.setVisibility(View.VISIBLE);
                 lyricsPlainText.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                     try {
                         lyricsPlainText.setText(safe);
                         lyricsPlainText.setAlpha(0f);
                         lyricsPlainText.animate().alpha(1f).setDuration(180).start();
                         // Ensure any overlay is cleared and bring plain text above it
                         clearNoLyricsOverlay();
                         try { lyricsPlainText.bringToFront(); lyricsPlainText.requestLayout(); lyricsPlainText.invalidate(); } catch (Exception ignored) {}
                     } catch (Exception ignored) {}
                 }).start();
             } catch (Exception ignored) {
                 try { lyricsPlainText.setText(safe); } catch (Exception ignored2) {}
             }
         });
     }

    // Restore / clear the no-lyrics overlay (lyricsStatus) so it does not sit on top of real content
     private void clearNoLyricsOverlay() {
         uiHandler.post(() -> {
             try {
                 if (lyricsStatus != null) {
                     lyricsStatus.setOnClickListener(null);
                     lyricsStatus.setClickable(false);
                     lyricsStatus.setFocusable(false);
                     try { lyricsStatus.setText(""); } catch (Exception ignored) {}
                     try { lyricsStatus.setVisibility(View.GONE); } catch (Exception ignored) {}
                     try { lyricsStatus.requestLayout(); lyricsStatus.invalidate(); } catch (Exception ignored) {}
                 }
             } catch (Exception ignored) {}
         });
     }

    // Show a centered "No lyrics found..." message. Tapping it retries a lookup if online.
    private void showNoLyricsFound() {
        uiHandler.post(() -> {
            try {
                // Hide other views
                if (lyricsLinesContainer != null) lyricsLinesContainer.setVisibility(View.GONE);
                if (lyricsPlainText != null) lyricsPlainText.setVisibility(View.GONE);

                if (lyricsStatus != null) {
                    // Make status occupy the available area and center text
                    ViewGroup.LayoutParams lp = lyricsStatus.getLayoutParams();
                    if (lp != null) {
                        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                        lyricsStatus.setLayoutParams(lp);
                    }
                    lyricsStatus.setGravity(Gravity.CENTER);
                    lyricsStatus.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                    lyricsStatus.setText(getString(com.example.tempo.R.string.lyrics_not_found));
                    lyricsStatus.setTextSize(18);
                    lyricsStatus.setVisibility(View.VISIBLE);
                    // allow tap to retry
                    lyricsStatus.setOnClickListener(v -> {
                        if (isOnline()) {
                            lyricsStatus.setText(getString(com.example.tempo.R.string.lyrics_looking_up));
                            fetchAndStoreLyrics();
                        }
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    // Populate synced lines into the container with a cross-fade to make replacements smooth.
    // Accepts an optional onComplete Runnable that runs after the population/fade-in finishes.
    private void populateSyncedLinesWithFade(final List<LrcLine> lines, final Runnable onComplete) {
         if (lyricsLinesContainer == null) return;
         uiHandler.post(() -> {
             try {
                 lyricsLinesContainer.animate().cancel();
                 // Fade out current view (if visible) then replace children and fade in
                 lyricsLinesContainer.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                     try {
                         lyricsLinesContainer.removeAllViews();
                         for (LrcLine ll : lines) {
                             TextView tv = new TextView(LyricsActivity.this);
                             tv.setText(ll.text);
                             tv.setTextSize(20);
                             TextViewCompat.setTextAppearance(tv, android.R.style.TextAppearance_Material_Body1);
                             tv.setPadding(0, dpToPx(12), 0, dpToPx(12));
                             tv.setAlpha(0.95f);
                             lyricsLinesContainer.addView(tv);
                         }
                         // Ensure plain text is hidden and container visible before fading in
                         if (lyricsPlainText != null) lyricsPlainText.setVisibility(View.GONE);
                         lyricsLinesContainer.setVisibility(View.VISIBLE);
                         lyricsLinesContainer.setAlpha(0f);
                         lyricsLinesContainer.animate().alpha(1f).setDuration(180).withEndAction(() -> {
                             // Clear overlay and bring synced container to front
                             clearNoLyricsOverlay();
                             try { lyricsLinesContainer.bringToFront(); lyricsLinesContainer.requestLayout(); lyricsLinesContainer.invalidate(); } catch (Exception ignored) {}
                             if (onComplete != null) onComplete.run();
                         }).start();
                     } catch (Exception ignored) {
                         // fallback: immediate populate without animation
                         lyricsLinesContainer.removeAllViews();
                         for (LrcLine ll : lines) {
                             TextView tv = new TextView(LyricsActivity.this);
                             tv.setText(ll.text);
                             tv.setTextSize(20);
                             TextViewCompat.setTextAppearance(tv, android.R.style.TextAppearance_Material_Body1);
                             tv.setPadding(0, dpToPx(12), 0, dpToPx(12));
                             tv.setAlpha(0.95f);
                             lyricsLinesContainer.addView(tv);
                         }
                         if (lyricsPlainText != null) lyricsPlainText.setVisibility(View.GONE);
                         lyricsLinesContainer.setVisibility(View.VISIBLE);
                         if (onComplete != null) onComplete.run();
                     }
                 }).start();
             } catch (Exception ignored) {
                 // immediate fallback
                 lyricsLinesContainer.removeAllViews();
                 for (LrcLine ll : lines) {
                     TextView tv = new TextView(LyricsActivity.this);
                     tv.setText(ll.text);
                     tv.setTextSize(20);
                     TextViewCompat.setTextAppearance(tv, android.R.style.TextAppearance_Material_Body1);
                     tv.setPadding(0, dpToPx(12), 0, dpToPx(12));
                     tv.setAlpha(0.95f);
                     lyricsLinesContainer.addView(tv);
                 }
                 if (lyricsPlainText != null) lyricsPlainText.setVisibility(View.GONE);
                 lyricsLinesContainer.setVisibility(View.VISIBLE);
                 if (onComplete != null) onComplete.run();
             }
         });
     }

    // MediaController callback to detect song changes while the activity is visible
    private final android.support.v4.media.session.MediaControllerCompat.Callback controllerCallback = new android.support.v4.media.session.MediaControllerCompat.Callback() {
        @Override
        public void onMetadataChanged(android.support.v4.media.MediaMetadataCompat metadata) {
            super.onMetadataChanged(metadata);
            try {
                runOnUiThread(() -> handleMetadataChanged(metadata));
            } catch (Exception ignored) {}
        }

        @Override
        public void onPlaybackStateChanged(android.support.v4.media.session.PlaybackStateCompat state) {
            super.onPlaybackStateChanged(state);
            try {
                // Always update our local position baseline so seeks are reflected immediately
                updateBaselineFromState(state);

                // Update UI on main thread: refresh highlights and ensure highlight loop runs when appropriate
                runOnUiThread(() -> {
                    try {
                        if (showingSynced && !lrcLines.isEmpty()) {
                            // Immediately highlight the line matching the new position
                            highlightLine(currentIndex);
                            // Ensure the auto highlight loop is running if allowed
                            if (autoScroll) {
                                uiHandler.removeCallbacks(highlightRunnable);
                                uiHandler.post(highlightRunnable);
                            }
                        }
                    } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}
        }
    };

    // Receiver to handle immediate user seeks from the player so lyrics jump instantly
    private final BroadcastReceiver userSeekReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            try {
                long pos = intent.getLongExtra("seek_position_ms", -1);
                if (pos < 0) {
                    // fallback to int extra
                    pos = intent.getIntExtra("seek_position_ms", -1);
                }
                if (pos < 0) return;

                // Update our baseline to the new position
                songStartPositionMs = pos;
                songStartSystemTimeMs = SystemClock.elapsedRealtime();
                // Record the user seek time so controller updates shortly after don't override our immediate jump
                lastUserSeekTimeMs = SystemClock.elapsedRealtime();

                // Recompute index & refresh UI on main thread
                final long p = pos;
                uiHandler.post(() -> {
                    try {
                        if (!lrcLines.isEmpty()) {
                            int idx = findIndexForTime(p + HIGHLIGHT_LEAD_MS);
                            currentIndex = Math.max(0, idx);
                            if (showingSynced) {
                                highlightLine(currentIndex);
                                if (autoScroll) {
                                    uiHandler.removeCallbacks(highlightRunnable);
                                    uiHandler.post(highlightRunnable);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}
        }
    };

    // Called on metadata updates or when playback starts to check if the active song changed
    private void handleMetadataChanged(android.support.v4.media.MediaMetadataCompat metadata) {
        if (metadata == null) return;
        try {
            // Compute a stable track id using mediaUri > mediaId > title+artist
            String metaTitle = null;
            String metaArtist = null;
            String metaUri = null;
            String metaId = null;
            try { metaTitle = metadata.getString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE); } catch (Exception ignored) {}
            try { metaArtist = metadata.getString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST); } catch (Exception ignored) {}
            try { metaUri = metadata.getString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_URI); } catch (Exception ignored) {}
            try { metaId = metadata.getString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_ID); } catch (Exception ignored) {}

            String idCandidate = null;
            if (!TextUtils.isEmpty(metaUri)) idCandidate = metaUri;
            else if (!TextUtils.isEmpty(metaId)) idCandidate = metaId;
            else if (!TextUtils.isEmpty(metaTitle)) idCandidate = metaTitle + "||" + (metaArtist == null ? "" : metaArtist);

            if (idCandidate != null && idCandidate.equals(currentTrackId)) {
                // same track - nothing to do
                return;
            }

            // New track detected
            currentTrackId = idCandidate;
            // Update local song identity
            songName = metaTitle != null ? metaTitle : songName;
            songArtist = metaArtist != null ? metaArtist : songArtist;
            songUri = metaUri != null ? metaUri : songUri;

            // Reset UI state for new song
            lrcLines.clear();
            lyricsLinesContainer.removeAllViews();
            setPlainTextWithFade("");
            clearAllHighlights();
            uiHandler.removeCallbacks(highlightRunnable);

            // Try to load from DB first, otherwise fetch
            String key = songUri != null ? songUri : (songName != null ? songName : null);
            if (key != null) {
                String[] loaded = repo.load(key);
                if (loaded != null) {
                    // show cached lyrics and return
                    if (!TextUtils.isEmpty(loaded[1])) {
                        showSyncedLyrics(loaded[1]);
                        lyricsStatus.setVisibility(View.GONE);
                        return;
                    } else if (!TextUtils.isEmpty(loaded[0])) {
                        setPlainTextWithFade(loaded[0]);
                        lyricsStatus.setVisibility(View.GONE);
                        return;
                    }
                }
            }

            // If we reach here, no cached lyrics — fetch if online, otherwise show retryable offline status
            if (isOnline()) {
                lyricsStatus.setVisibility(View.VISIBLE);
                lyricsStatus.setText(getString(com.example.tempo.R.string.lyrics_looking_up));
                fetchAndStoreLyrics();
            } else {
                lyricsStatus.setVisibility(View.VISIBLE);
                lyricsStatus.setText(getString(com.example.tempo.R.string.lyrics_offline));
            }
        } catch (Exception ignored) {}
    }

    // Update position baseline from a PlaybackState (used to catch up after seeks)
    private void updateBaselineFromState(android.support.v4.media.session.PlaybackStateCompat state) {
        try {
            if (state == null) return;
            // If the user recently performed a seek, ignore controller updates for a short window
            if (lastUserSeekTimeMs > 0 && SystemClock.elapsedRealtime() - lastUserSeekTimeMs < 600) {
                return;
            }
            long pos = state.getPosition();
            long last = state.getLastPositionUpdateTime();
            float speed = state.getPlaybackSpeed();
            long now = android.os.SystemClock.elapsedRealtime();
            if (last > 0) {
                songStartPositionMs = pos + (long) ((now - last) * speed);
                songStartSystemTimeMs = now;
            } else {
                songStartPositionMs = pos;
                songStartSystemTimeMs = now;
            }

            // Update currentIndex to reflect the new position immediately
            if (!lrcLines.isEmpty()) {
                long nowMs = getCurrentPlaybackPosition() + HIGHLIGHT_LEAD_MS;
                int idx = findIndexForTime(nowMs);
                currentIndex = Math.max(0, idx);
            }
        } catch (Exception ignored) {}
    }

    // Binary-search helper to find the largest index i where lrcLines[i].timeMs <= timeMs
    private int findIndexForTime(long timeMs) {
        int lo = 0, hi = lrcLines.size() - 1, found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long t = lrcLines.get(mid).timeMs;
            if (t <= timeMs) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found;
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Connect to the media browser service so we get a MediaController for metadata/playback updates
        try {
            if (mediaBrowser == null) {
                mediaBrowser = new android.support.v4.media.MediaBrowserCompat(this,
                        new android.content.ComponentName(this, com.example.tempo.Services.MediaPlaybackService.class),
                        new android.support.v4.media.MediaBrowserCompat.ConnectionCallback() {
                            @Override
                            public void onConnected() {
                                try {
                                    android.support.v4.media.session.MediaControllerCompat controller = new android.support.v4.media.session.MediaControllerCompat(LyricsActivity.this, mediaBrowser.getSessionToken());
                                    mediaController = controller;
                                    android.support.v4.media.session.MediaControllerCompat.setMediaController(LyricsActivity.this, controller);
                                    controller.registerCallback(controllerCallback);
                                    // Immediate metadata check
                                    handleMetadataChanged(controller.getMetadata());
                                } catch (Exception ignored) {}
                            }

                            @Override
                            public void onConnectionSuspended() {
                                try { mediaController = null; } catch (Exception ignored) {}
                            }

                            @Override
                            public void onConnectionFailed() {
                                try { mediaController = null; } catch (Exception ignored) {}
                            }
                        }, null);
            }
            mediaBrowser.connect();

            // Register user seek receiver so we can react immediately when the user seeks in the player
            try {
                IntentFilter f = new IntentFilter("com.example.tempo.ACTION_USER_SEEK");
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(userSeekReceiver, f, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    // older APIs don't have the flag overload; use the standard call
                    registerReceiver(userSeekReceiver, f);
                }
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            if (mediaController != null) {
                mediaController.unregisterCallback(controllerCallback);
                mediaController = null;
            } else {
                try {
                    android.support.v4.media.session.MediaControllerCompat mc = android.support.v4.media.session.MediaControllerCompat.getMediaController(LyricsActivity.this);
                    if (mc != null) mc.unregisterCallback(controllerCallback);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        try { unregisterReceiver(userSeekReceiver); } catch (Exception ignored) {}
        try { if (mediaBrowser != null) { mediaBrowser.disconnect(); } } catch (Exception ignored) {}
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(com.example.tempo.R.menu.menu_lyrics, menu);
        try {
            android.view.MenuItem it = menu.findItem(com.example.tempo.R.id.action_refresh_lyrics);
            if (it != null && it.getIcon() != null) {
                it.getIcon().setTint(getResources().getColor(android.R.color.white));
            }
        } catch (Exception ignored) {}
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == com.example.tempo.R.id.action_refresh_lyrics) {
            // Refresh: re-fetch lyrics and if different update UI & DB
            lyricsStatus.setVisibility(View.VISIBLE);
            lyricsStatus.setText(getString(com.example.tempo.R.string.lyrics_looking_up));
            new Thread(() -> {
                final String titleToLookup = songName != null ? songName.trim() : "";
                final String strippedTitle = titleToLookup.replaceAll("(?i)\\.(mp3|wav|flac|m4a)$", "");
                java.util.List<com.example.tempo.lyrics.LrcLibLyricsProvider.Candidate> candidates = null;
                try {
                    if (provider instanceof com.example.tempo.lyrics.LrcLibLyricsProvider) {
                        candidates = ((com.example.tempo.lyrics.LrcLibLyricsProvider) provider).searchCandidates(strippedTitle);
                    }
                } catch (Exception ignored) {}

                final java.util.List<com.example.tempo.lyrics.LrcLibLyricsProvider.Candidate> finalCandidates = candidates;
                runOnUiThread(() -> {
                    if (finalCandidates != null && finalCandidates.size() > 1) {
                        // chooser
                        CharSequence[] items = new CharSequence[finalCandidates.size()];
                        for (int i = 0; i < finalCandidates.size(); i++) {
                            com.example.tempo.lyrics.LrcLibLyricsProvider.Candidate c = finalCandidates.get(i);
                            String artist = c.artistName != null ? (" — " + c.artistName) : "";
                            items[i] = (c.trackName != null ? c.trackName : "Unknown") + artist;
                        }
                        AlertDialog.Builder b = new AlertDialog.Builder(LyricsActivity.this);
                        b.setTitle("Choose lyrics");
                        b.setItems(items, (dialog, which) -> {
                            com.example.tempo.lyrics.LrcLibLyricsProvider.Candidate chosen = finalCandidates.get(which);
                            // save and show
                            String key = songUri != null ? songUri : songName;
                            repo.save(key, chosen.plainLyrics, chosen.syncedLyrics);
                            if (!TextUtils.isEmpty(chosen.syncedLyrics)) showSyncedLyrics(chosen.syncedLyrics);
                            else setPlainTextWithFade(chosen.plainLyrics);
                            lyricsStatus.setVisibility(View.GONE);
                        });
                        b.setNegativeButton("Cancel", (d, w) -> d.dismiss());
                        androidx.appcompat.app.AlertDialog dlg = b.create();
                        dlg.show();
                        try {
                            int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                            if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
                                android.widget.Button neg = dlg.getButton(android.content.DialogInterface.BUTTON_NEGATIVE);
                                if (neg != null) neg.setTextColor(getResources().getColor(android.R.color.white));
                            }
                        } catch (Exception ignored) {}
                        return;
                    }

                    if (finalCandidates != null && finalCandidates.size() == 1) {
                        com.example.tempo.lyrics.LrcLibLyricsProvider.Candidate c = finalCandidates.get(0);
                        String plain = c.plainLyrics;
                        String lrc = c.syncedLyrics;
                        String key = songUri != null ? songUri : songName;
                        String[] existing = repo.load(key);
                        boolean different = true;
                        if (existing != null) {
                            String ePlain = existing.length > 0 ? existing[0] : null;
                            String eLrc = existing.length > 1 ? existing[1] : null;
                            different = !((ePlain == null ? "" : ePlain).equals(plain == null ? "" : plain) && (eLrc == null ? "" : eLrc).equals(lrc == null ? "" : lrc));
                        }
                        if (different) {
                            repo.save(key, plain, lrc);
                            if (!isBlankOrNullLiteral(lrc)) {
                                showSyncedLyrics(lrc);
                                clearNoLyricsOverlay();
                                if (lyricsStatus != null) lyricsStatus.setVisibility(View.GONE);
                            } else {
                                setPlainTextWithFade(plain);
                                clearNoLyricsOverlay();
                                if (lyricsStatus != null) lyricsStatus.setVisibility(View.GONE);
                            }
                            lyricsStatus.setText(getString(com.example.tempo.R.string.lyrics_updated));
                        } else {
                            lyricsStatus.setText(getString(com.example.tempo.R.string.lyrics_up_to_date));
                            uiHandler.postDelayed(() -> { if (lyricsStatus != null) lyricsStatus.setVisibility(View.GONE); }, 1500);
                        }
                        uiHandler.postDelayed(() -> { if (lyricsStatus != null) lyricsStatus.setVisibility(View.GONE); }, 800);
                    }
                });
            }).start();
             return true;
         }
         return super.onOptionsItemSelected(item);
     }

    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.net.Network nw = cm.getActiveNetwork();
                if (nw == null) return false;
                android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(nw);
                return caps != null && (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
            } else {
                NetworkInfo ni = cm.getActiveNetworkInfo();
                return ni != null && ni.isConnected();
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacksAndMessages(null);
    }

    private void updateFabIcon() {
        try {
            if (showingSynced) {
                fabToggle.setImageResource(android.R.drawable.ic_menu_view);
                fabToggle.setContentDescription("Synced lyrics");
            } else {
                fabToggle.setImageResource(android.R.drawable.ic_menu_edit);
                fabToggle.setContentDescription("Plain lyrics");
            }
            fabToggle.setImageTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(com.example.tempo.R.color.teal_700)));
        } catch (Exception ignored) {}
    }

    @Override
    protected int getNavigationItemId() {
        // Lyrics screen isn't directly represented in bottom nav; return 0 to avoid selecting any item
        return 0;
    }

    @Override
    public void onBackPressed() {
        // Suppress a subsequent interstitial when returning to the Music Player
        try { AdManager.suppressNextInterstitial(this); } catch (Exception ignored) {}
        super.onBackPressed();
    }

    @Override
    public boolean onSupportNavigateUp() {
        try { AdManager.suppressNextInterstitial(this); } catch (Exception ignored) {}
        finish();
        return true;
    }
}
