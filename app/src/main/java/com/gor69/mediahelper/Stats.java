package com.gor69.mediahelper;

import android.content.Context;

/** Counts processed files, shown on the settings screen. */
final class Stats {
    private Stats() {}

    static void increment(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences("stats", Context.MODE_PRIVATE);
        p.edit().putInt("processed", p.getInt("processed", 0) + 1).apply();
    }

    /** Keeps the last 15 jobs (newest first) for the "Recent jobs" list. */
    static synchronized void addJob(Context c, String line) {
        android.content.SharedPreferences p = c.getSharedPreferences("stats", Context.MODE_PRIVATE);
        String time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(new java.util.Date());
        String old = p.getString("jobs", "");
        String[] lines = old.isEmpty() ? new String[0] : old.split("\n");
        StringBuilder sb = new StringBuilder(time).append("  ").append(line);
        for (int i = 0; i < Math.min(14, lines.length); i++) sb.append('\n').append(lines[i]);
        p.edit().putString("jobs", sb.toString()).apply();
    }

    static String jobs(Context c) {
        return c.getSharedPreferences("stats", Context.MODE_PRIVATE).getString("jobs", "");
    }

    static int processed(Context c) {
        return c.getSharedPreferences("stats", Context.MODE_PRIVATE).getInt("processed", 0);
    }
}
