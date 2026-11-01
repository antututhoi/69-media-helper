package com.gor69.mediahelper;

import android.util.Log;

import java.lang.reflect.Method;

/**
 * Runs FFmpeg through the bundled ffmpeg-kit library.
 * Called by reflection so a different ffmpeg-kit build can be swapped in without code changes,
 * and so the app still works (without conversions) if the library is missing.
 */
public final class FFmpeg {
    private static final String TAG = "Mod69FFmpeg";
    private static Boolean available;
    static String version = "";
    static String problem = "";
    private static String base = "com.arthenica.ffmpegkit";

    private FFmpeg() {}

    public static synchronized boolean isAvailable() {
        if (available == null) {
            available = false;
            StringBuilder why = new StringBuilder();
            // different FFmpegKit builds use different package names
            for (String pkg : new String[]{"com.arthenica.ffmpegkit", "com.antonkarpenko.ffmpegkit"}) {
                try {
                    Class.forName(pkg + ".FFmpegKit");
                    // also load the native part, so "Ready" really means it can run
                    Object v = Class.forName(pkg + ".FFmpegKitConfig").getMethod("getFFmpegVersion").invoke(null);
                    version = v == null ? "" : v.toString();
                    base = pkg;
                    available = true;
                    break;
                } catch (Throwable t) {
                    Throwable root = t;
                    while (root.getCause() != null) root = root.getCause();
                    // a class missing INSIDE the library (not the library itself) is a real problem: show it
                    boolean libraryItself = t instanceof ClassNotFoundException
                            && String.valueOf(t.getMessage()).contains(pkg + ".FFmpegKit");
                    if (!libraryItself) {
                        Log.w(TAG, "FFmpeg not available in " + pkg, t);
                        why.append(root.getClass().getSimpleName()).append(": ").append(root.getMessage()).append(' ');
                    }
                }
            }
            problem = why.length() > 0 ? why.toString().trim() : "FFmpeg library not found in the app";
        }
        return available;
    }

    /** @return true if FFmpeg ran and exited with code 0. */
    public static boolean run(String... args) {
        return exec(base + ".FFmpegKit", args) != null;
    }

    /**
     * Lets FFmpeg read a content:// file directly (no copy). Returns null if this FFmpegKit
     * build cannot do it.
     */
    public static String safRead(android.content.Context context, android.net.Uri uri) {
        if (!isAvailable()) return null;
        try {
            Class<?> cfg = Class.forName(base + ".FFmpegKitConfig");
            Object r = cfg.getMethod("getSafParameterForRead", android.content.Context.class, android.net.Uri.class)
                    .invoke(null, context, uri);
            return r == null ? null : r.toString();
        } catch (Throwable t) {
            Log.w(TAG, "no SAF support", t);
            return null;
        }
    }

    /** Runs ffprobe and returns its printed output, or null on failure. */
    public static String probe(String... args) {
        return exec(base + ".FFprobeKit", args);
    }

    private static String exec(String kitClass, String[] args) {
        if (!isAvailable()) return null;
        try {
            Class<?> kit = Class.forName(kitClass);
            Method m = kit.getMethod("executeWithArguments", String[].class);
            Object session = m.invoke(null, (Object) args);
            Object rc = session.getClass().getMethod("getReturnCode").invoke(session);
            boolean ok = false;
            if (rc != null) {
                Object v = rc.getClass().getMethod("getValue").invoke(rc);
                ok = v instanceof Integer && (Integer) v == 0;
            }
            String out = "";
            try {
                Object o = session.getClass().getMethod("getOutput").invoke(session);
                if (o != null) out = o.toString();
            } catch (Throwable ignored) {
            }
            if (!ok) {
                Log.w(TAG, "failed: " + String.join(" ", args) + "\n" + out);
                return null;
            }
            return out;
        } catch (Throwable t) {
            Log.w(TAG, "error running " + kitClass, t);
            return null;
        }
    }
}
