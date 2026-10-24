package com.gor69.mediahelper;

import android.content.SharedPreferences;

/** The helper's settings (shared by all 69 apps). */
public final class Mod69Prefs {
    public static final String DOWNLOAD_QUALITY = "mod69_download_quality"; // "high" or "low"
    public static final String LOW_IMAGE_MODE = "mod69_low_image_mode";     // "width", "box", "percent"
    public static final String LOW_IMAGE_SIZE = "mod69_low_image_size";     // px, default 1280
    public static final String LOW_IMAGE_PERCENT = "mod69_low_image_percent"; // default 50
    public static final String LOW_VIDEO_HEIGHT = "mod69_low_video_height"; // default 480
    public static final String GIF_TO_MP4 = "mod69_gif_to_mp4";
    public static final String EMBED_VIDEO_COVER = "mod69_embed_video_cover";
    public static final String NOTIFICATION_THUMBNAIL = "mod69_notification_thumbnail";
    public static final String COVER_WIDTH = "mod69_cover_width";
    public static final String AUTO_UPVOTE_ON_DOWNLOAD = "mod69_auto_upvote_on_download";
    public static final String AUTO_DOWNVOTE_ON_HIDE = "mod69_auto_downvote_on_hide";
    public static final String SHOW_ACTION_BUTTONS = "mod69_show_action_buttons";
    public static final String SHOW_DOWNLOADED_MARK = "mod69_show_downloaded_mark";
    public static final String SAVE_FEED_POSITION = "mod69_save_feed_position";

    private Mod69Prefs() {}

    public static boolean isLowQuality(SharedPreferences p) {
        return "low".equals(p.getString(DOWNLOAD_QUALITY, "high"));
    }

    public static int intPref(SharedPreferences p, String key, int def) {
        try {
            String v = p.getString(key, null);
            if (v == null || v.trim().isEmpty()) return def;
            int n = Integer.parseInt(v.trim());
            return n > 0 ? n : def;
        } catch (Exception e) {
            return def;
        }
    }

    public static String lowImageMode(SharedPreferences p) {
        return p.getString(LOW_IMAGE_MODE, "width");
    }

    public static int lowImageSize(SharedPreferences p) {
        return intPref(p, LOW_IMAGE_SIZE, 1280);
    }

    public static int lowImagePercent(SharedPreferences p) {
        return Math.min(100, intPref(p, LOW_IMAGE_PERCENT, 50));
    }

    public static int coverWidth(SharedPreferences p) {
        return intPref(p, COVER_WIDTH, 320);
    }

    public static int lowVideoHeight(SharedPreferences p) {
        return intPref(p, LOW_VIDEO_HEIGHT, 480);
    }
}
