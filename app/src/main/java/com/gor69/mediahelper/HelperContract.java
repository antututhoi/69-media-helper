package com.gor69.mediahelper;

/**
 * How other 69 apps talk to the helper. Keep this file identical in every app that uses it.
 *
 * 1. The app binds to the service (action PROCESS, package PACKAGE) - only apps with the same signing key may.
 * 2. It sends MSG_PROCESS with: input file Uri (read access granted), output file Uri (write access granted),
 *    declared media type (0 image, 1 GIF, 2 video) and the extension from the file name.
 * 3. The helper converts the file using its own settings and writes the result to the output Uri.
 * 4. It answers MSG_RESULT with: ok, final media type, extension and MIME type.
 *    If ok is false the app keeps its original download.
 */
public final class HelperContract {
    public static final String PACKAGE = "com.gor69.mediahelper";
    public static final String ACTION_PROCESS = "com.gor69.mediahelper.PROCESS";
    public static final String ACTION_OPEN_SETTINGS = "com.gor69.mediahelper.OPEN_SETTINGS";

    public static final int MSG_PROCESS = 1;
    public static final int MSG_RESULT = 2;
    public static final int MSG_PING = 3;
    public static final int MSG_PONG = 4;

    public static final String KEY_IN = "in";
    public static final String KEY_OUT = "out";
    public static final String KEY_TYPE = "type";
    public static final String KEY_EXT = "ext";
    public static final String KEY_OK = "ok";
    public static final String KEY_MIME = "mime";
    public static final String KEY_CHANGED = "changed";
    public static final String KEY_ERROR = "error";
    public static final String KEY_VERSION = "version";
    /** Sent back with MSG_PONG so apps can pick smaller downloads up front. */
    public static final String KEY_LOW_QUALITY = "low_quality";
    public static final String KEY_LOW_VIDEO_HEIGHT = "low_video_height";
    public static final String KEY_GIF_TO_MP4 = "gif_to_mp4";

    public static final int VERSION = 1;

    private HelperContract() {}
}
