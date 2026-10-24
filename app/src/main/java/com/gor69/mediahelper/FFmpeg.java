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

    private FFmpeg() {}

    public static synchronized boolean isAvailable() {
        if (available == null) {
            try {
                Class.forName("com.arthenica.ffmpegkit.FFmpegKit");
                available = true;
            } catch (Throwable t) {
                available = false;
            }
        }
        return available;
    }

    /** @return true if FFmpeg ran and exited with code 0. */
    public static boolean run(String... args) {
        return exec("com.arthenica.ffmpegkit.FFmpegKit", args) != null;
    }

    /** Runs ffprobe and returns its printed output, or null on failure. */
    public static String probe(String... args) {
        return exec("com.arthenica.ffmpegkit.FFprobeKit", args);
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
