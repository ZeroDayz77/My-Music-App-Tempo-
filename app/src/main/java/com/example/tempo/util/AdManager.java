package com.example.tempo.util;

import android.content.Context;
import android.content.SharedPreferences;

public class AdManager {
    private static final String PREFS = "ad_prefs";
    private static final String KEY_LAST_INTERSTITIAL = "last_interstitial_shown_ms";
    private static final String KEY_SUPPRESS_NEXT = "suppress_next_interstitial";
    // Cooldown between interstitials (5 minutes default)
    private static final long DEFAULT_COOLDOWN_MS = 5 * 60 * 1000L;

    // Allow override for tests or future configuration
    public static boolean shouldShowInterstitial(Context ctx) {
        return shouldShowInterstitial(ctx, DEFAULT_COOLDOWN_MS);
    }

    public static synchronized boolean shouldShowInterstitial(Context ctx, long cooldownMs) {
        try {
            // If a one-time suppression flag is set, do not show and clear it
            if (consumeSuppressFlag(ctx)) return false;

            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long last = sp.getLong(KEY_LAST_INTERSTITIAL, 0L);
            long now = System.currentTimeMillis();
            return now - last >= cooldownMs;
        } catch (Exception e) {
            // If anything goes wrong, be conservative and allow showing
            return true;
        }
    }

    public static synchronized void markInterstitialShown(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            sp.edit().putLong(KEY_LAST_INTERSTITIAL, System.currentTimeMillis()).apply();
        } catch (Exception ignored) {}
    }

    // Request that the next interstitial be suppressed once. Use this when launching an activity
    // that will immediately return to the MusicPlayer (e.g. LyricsActivity) so the ad doesn't reappear.
    public static synchronized void suppressNextInterstitial(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            sp.edit().putBoolean(KEY_SUPPRESS_NEXT, true).apply();
        } catch (Exception ignored) {}
    }

    // Consume the suppression flag (returns true if suppression was set and clears it).
    public static synchronized boolean consumeSuppressFlag(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            boolean v = sp.getBoolean(KEY_SUPPRESS_NEXT, false);
            if (v) sp.edit().remove(KEY_SUPPRESS_NEXT).apply();
            return v;
        } catch (Exception ignored) {}
        return false;
    }
}
