package com.gor69.mediahelper;

import android.content.Context;

/** Counts processed files, shown on the settings screen. */
final class Stats {
    private Stats() {}

    static void increment(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences("stats", Context.MODE_PRIVATE);
        p.edit().putInt("processed", p.getInt("processed", 0) + 1).apply();
    }

    static int processed(Context c) {
        return c.getSharedPreferences("stats", Context.MODE_PRIVATE).getInt("processed", 0);
    }
}
