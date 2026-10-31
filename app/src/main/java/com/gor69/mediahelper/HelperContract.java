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
    /** Cut a part of a video, or turn it into MP3 (sent by 69-Next Player). Answer: MSG_CUT_RESULT. */
    public static final int MSG_CUT = 5;
    public static final int MSG_CUT_RESULT = 6;
    public static final String KEY_START_MS = "start_ms";
    public static final String KEY_END_MS = "end_ms";        // -1 = until the end
    public static final String KEY_MODE = "mode";            // "copy", "exact" or "mp3"
    public static final String KEY_OUT_EXT = "out_ext";

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

    /** Sent with MSG_PROCESS: "high" or "low" for this one file, overriding the setting (optional). */
    public static final String KEY_QUALITY = "quality";
    /** Sent back with MSG_RESULT: */
    public static final String KEY_INFO = "info";          // what was done, one line per step
    public static final String KEY_SUFFIX = "suffix";      // "_lo", "_480p" or ""
    public static final String KEY_ORIGINAL_SIZE = "original_size";
    public static final String KEY_FINAL_SIZE = "final_size";
    public static final String KEY_WIDTH = "width";
    public static final String KEY_HEIGHT = "height";
    public static final String KEY_FFMPEG = "ffmpeg";      // also sent with MSG_PONG

    public static final int VERSION = 3;

    private HelperContract() {}
}
