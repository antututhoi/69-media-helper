package com.gor69.mediahelper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Checks and converts a downloaded file before it is saved:
 * real format check, low quality, GIF to MP4, video cover picture, notification thumbnail.
 * An original is only replaced after the new file passes a size and duration check.
 */
public final class MediaProcessor {
    private static final String TAG = "Mod69Media";

    public static final int TYPE_IMAGE = 0;
    public static final int TYPE_GIF = 1;
    public static final int TYPE_VIDEO = 2;

    /** Cover picture width, like the user's ffmpeg_settings.ps1 ($ThumbnailWidth); set per call. */
    private static int COVER_WIDTH = 320;

    public static final class Result {
        public File file;
        public String extension;
        public String mimeType;
        public int mediaType;
        @Nullable public Bitmap thumbnail;
        /** What was done (or why not), shown in the app's notification and in the helper's "Recent jobs". */
        public final List<String> notes = new ArrayList<>();
        /** File name ending for low quality copies: "_lo" (pictures) or e.g. "_480p" (videos). Empty for originals. */
        public String suffix = "";
    }

    static String size(long bytes) {
        if (bytes >= 1024 * 1024) return String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0);
        return String.format(java.util.Locale.US, "%d KB", Math.max(1, bytes / 1024));
    }

    private MediaProcessor() {}

    public static File workDir(Context context) {
        File dir = new File(context.getCacheDir(), "mod69");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    public static File newTempFile(Context context, String extension) {
        return new File(workDir(context), UUID.randomUUID().toString() + "." + extension);
    }

    /** Reads the first bytes of the file to find what it really is. */
    @Nullable
    public static String sniff(File file) {
        byte[] h = new byte[16];
        int n;
        try (InputStream in = new FileInputStream(file)) {
            n = in.read(h);
        } catch (IOException e) {
            return null;
        }
        if (n < 12) return null;
        if (h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8') return "gif";
        if ((h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return "jpg";
        if ((h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return "png";
        if (h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return "webp";
        if (h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p') {
            String brand = new String(h, 8, 4);
            if (brand.startsWith("avif") || brand.startsWith("avis")) return "avif";
            if (brand.startsWith("heic") || brand.startsWith("mif1") || brand.startsWith("heix")) return "heic";
            return "mp4";
        }
        if ((h[0] & 0xFF) == 0x1A && (h[1] & 0xFF) == 0x45 && (h[2] & 0xFF) == 0xDF && (h[3] & 0xFF) == 0xA3) return "webm";
        return null;
    }

    public static String mimeFor(String ext) {
        switch (ext) {
            case "gif": return "image/gif";
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "webp": return "image/webp";
            case "avif": return "image/avif";
            case "heic": return "image/heic";
            case "webm": return "video/webm";
            case "mkv": return "video/x-matroska";
            default: return "video/mp4";
        }
    }

    private static int typeFor(String ext) {
        switch (ext) {
            case "gif": return TYPE_GIF;
            case "mp4": case "webm": case "mkv": return TYPE_VIDEO;
            default: return TYPE_IMAGE;
        }
    }

    /**
     * @param input           the downloaded temp file
     * @param declaredType    what the post said the media is
     * @param declaredExt     the extension from the original file name (may be null)
     */
    public static Result process(Context context, SharedPreferences prefs, File input,
                                 int declaredType, @Nullable String declaredExt) {
        return process(context, prefs, input, declaredType, declaredExt, null);
    }

    public static Result process(Context context, SharedPreferences prefs, File input,
                                 int declaredType, @Nullable String declaredExt, @Nullable String qualityOverride) {
        return process(context, prefs, input, declaredType, declaredExt, qualityOverride, null);
    }

    private static String perf = "normal";

    /**
     * @param qualityOverride "high" or "low" for this one file (the "other quality" download), or null for the setting.
     * @param videoTarget     "h265", "webm" or "keep" for this one video, or null for the setting.
     */
    public static Result process(Context context, SharedPreferences prefs, File input,
                                 int declaredType, @Nullable String declaredExt, @Nullable String qualityOverride,
                                 @Nullable String videoTarget) {
        Result r = new Result();
        String real = sniff(input);
        if (real == null) {
            real = declaredExt != null ? declaredExt.toLowerCase() : (declaredType == TYPE_VIDEO ? "mp4" : declaredType == TYPE_GIF ? "gif" : "jpg");
            if (real.equals("jpeg")) real = "jpg";
        }
        String declared = declaredExt == null ? null : declaredExt.toLowerCase().replace("jpeg", "jpg");
        if (declared != null && !declared.equals(real)) {
            r.notes.add("Real format: " + real.toUpperCase() + " (was named ." + declared + ")");
        }
        r.file = input;
        r.extension = real;
        r.mediaType = typeFor(real);
        boolean low = qualityOverride != null ? "low".equals(qualityOverride) : Mod69Prefs.isLowQuality(prefs);
        boolean cover = prefs.getBoolean(Mod69Prefs.EMBED_VIDEO_COVER, true);
        boolean toWebp = prefs.getBoolean(Mod69Prefs.IMAGE_TO_WEBP, false);
        boolean toWebm = videoTarget == null || videoTarget.isEmpty()
                ? prefs.getBoolean(Mod69Prefs.VIDEO_TO_WEBM, false) : "webm".equals(videoTarget);
        boolean toH265 = "h265".equals(videoTarget);
        perf = prefs.getString(Mod69Prefs.VIDEO_PERFORMANCE, "normal");
        COVER_WIDTH = Mod69Prefs.coverWidth(prefs);
        boolean ffmpeg = FFmpeg.isAvailable();
        r.notes.add("Quality: " + (low ? "low" : "original"));

        try {
            if (r.mediaType == TYPE_GIF) {
                if (prefs.getBoolean(Mod69Prefs.GIF_TO_MP4, false)) {
                    if (!ffmpeg) {
                        r.notes.add("GIF to MP4 skipped: FFmpeg not available");
                    } else {
                        long before = r.file.length();
                        File mp4 = gifToMp4(context, r.file, low ? prefs : null);
                        if (mp4 != null && mp4.length() > 0 && mp4.length() < before) {
                            r.notes.add("GIF to MP4: " + size(before) + " -> " + size(mp4.length()));
                            replace(r, mp4, "mp4");
                        } else if (mp4 != null) {
                            r.notes.add("GIF to MP4 kept GIF (MP4 " + size(mp4.length()) + " is not smaller than " + size(before) + ")");
                            //noinspection ResultOfMethodCallIgnored
                            mp4.delete();
                        } else {
                            r.notes.add("GIF to MP4 failed, kept GIF");
                        }
                    }
                }
                if (r.mediaType == TYPE_GIF && low) {
                    File small = ffmpeg ? downscaleGif(context, prefs, r.file) : null;
                    if (small != null) {
                        r.notes.add("GIF made smaller");
                        replace(r, small, "gif");
                        r.suffix = "_lo";
                    }
                }
            } else if (r.mediaType == TYPE_IMAGE) {
                if (low && !isAnimatedWebp(r.file)) {
                    File small = downscaleImage(context, prefs, r.file, r.extension);
                    if (small != null) {
                        replace(r, small, r.extension);
                        r.suffix = "_lo";
                        int[] wh = imageSize(r.file);
                        r.notes.add("Picture made smaller" + (wh == null ? "" : " (" + wh[0] + "x" + wh[1] + ")"));
                    } else {
                        r.notes.add("Picture already small, kept as is");
                    }
                }
                if (toWebp && !r.extension.equals("webp") && !r.extension.equals("gif") && !isAnimatedWebp(r.file)) {
                    long before = r.file.length();
                    File webp = toWebp(context, r.file);
                    if (webp != null && webp.length() < before) {
                        r.notes.add("WebP: " + size(before) + " -> " + size(webp.length()));
                        replace(r, webp, "webp");
                    } else {
                        if (webp != null) //noinspection ResultOfMethodCallIgnored
                            webp.delete();
                        r.notes.add("WebP not smaller, kept " + r.extension.toUpperCase());
                    }
                }
            }

            if (r.mediaType == TYPE_VIDEO) {
                if (!ffmpeg) {
                    r.notes.add("Video changes skipped: FFmpeg not available");
                } else {
                    if (r.extension.equals("webm") || r.extension.equals("mkv")) {
                        if (!toWebm) {
                            File mp4 = newTempFile(context, "mp4");
                            if (FFmpeg.run("-y", "-i", r.file.getAbsolutePath(), "-map", "0:v:0", "-map", "0:a:0?",
                                    "-c", "copy", "-strict", "experimental", "-movflags", "+faststart",
                                    mp4.getAbsolutePath()) && sameDuration(r.file, mp4)) {
                                replace(r, mp4, "mp4");
                            } else {
                                //noinspection ResultOfMethodCallIgnored
                                mp4.delete();
                            }
                        }
                    }
                    if (low) {
                        File small = downscaleVideo(context, prefs, r.file);
                        if (small != null) {
                            replace(r, small, "mp4");
                            r.suffix = "_" + Mod69Prefs.lowVideoHeight(prefs) + "p";
                            r.notes.add("Video made " + Mod69Prefs.lowVideoHeight(prefs) + "p");
                        } else {
                            r.notes.add("Video already " + Mod69Prefs.lowVideoHeight(prefs) + "p or smaller");
                        }
                    }
                    if (toH265) {
                        long before = r.file.length();
                        File hevc = toH265(context, r.file);
                        if (hevc != null && hevc.length() < before) {
                            r.notes.add("H.265: " + size(before) + " -> " + size(hevc.length()));
                            replace(r, hevc, "mp4");
                        } else {
                            if (hevc != null) //noinspection ResultOfMethodCallIgnored
                                hevc.delete();
                            r.notes.add("H.265 not smaller, kept " + r.extension.toUpperCase());
                        }
                    }
                    if (toWebm && !r.extension.equals("webm")) {
                        long before = r.file.length();
                        File webm = toWebm(context, r.file);
                        if (webm != null && webm.length() < before) {
                            r.notes.add("WebM: " + size(before) + " -> " + size(webm.length()));
                            replace(r, webm, "webm");
                        } else {
                            if (webm != null) //noinspection ResultOfMethodCallIgnored
                                webm.delete();
                            r.notes.add("WebM not smaller, kept " + r.extension.toUpperCase());
                        }
                    }
                    if (cover) {
                        String[] why = new String[1];
                        File withCover = embedCover(context, r.file, why);
                        if (withCover != null) {
                            replace(r, withCover, r.extension);
                            r.notes.add("Cover picture added");
                        } else {
                            r.notes.add("Cover: " + (why[0] == null ? "failed" : why[0]));
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "processing failed, keeping the downloaded file", t);
            r.notes.add("Error: " + t.getClass().getSimpleName());
        }

        r.mimeType = mimeFor(r.extension);
        return r;
    }

    @Nullable
    static int[] imageSize(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        return o.outWidth > 0 ? new int[]{o.outWidth, o.outHeight} : null;
    }

    /** Width x height of a picture, GIF or video, or null. */
    @Nullable
    static int[] dimensions(File f, int mediaType) {
        if (mediaType == TYPE_VIDEO) return videoSize(f);
        if (mediaType == TYPE_GIF) return gifSize(f);
        return imageSize(f);
    }

    // ---------- WebP / WebM ----------

    @Nullable
    private static File toWebp(Context context, File in) {
        Bitmap b = BitmapFactory.decodeFile(in.getAbsolutePath());
        if (b == null) return null;
        File out = newTempFile(context, "webp");
        Bitmap.CompressFormat f = android.os.Build.VERSION.SDK_INT >= 30
                ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
        try (OutputStream os = new FileOutputStream(out)) {
            b.compress(f, 85, os);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            return null;
        } finally {
            b.recycle();
        }
        return out.length() > 0 ? out : null;
    }

    @Nullable
    private static File toWebm(Context context, File in) {
        File out = newTempFile(context, "webm");
        // Low = cool and battery friendly, High = all cores and better compression.
        String threads = "low".equals(perf) ? "2" : "high".equals(perf) ? "0" : "4";
        String cpuUsed = "low".equals(perf) ? "8" : "high".equals(perf) ? "4" : "6";
        String deadline = "high".equals(perf) ? "good" : "realtime";
        boolean ok = FFmpeg.run("-y", "-threads", threads, "-i", in.getAbsolutePath(), "-map", "0:v:0", "-map", "0:a:0?",
                "-c:v", "libvpx-vp9", "-crf", "34", "-b:v", "0", "-row-mt", "1", "-deadline", deadline, "-cpu-used", cpuUsed,
                "-threads", threads, "-c:a", "libopus", "-b:a", "96k", out.getAbsolutePath());
        if (ok && out.length() > 1024 && sameDuration(in, out)) return out;
        //noinspection ResultOfMethodCallIgnored
        out.delete();
        return null;
    }

    /**
     * H.265 MP4: first the phone's video chip (fast, cool), then the x265 software encoder.
     * The chip gets a bitrate about 55% of the original video's.
     */
    @Nullable
    private static File toH265(Context context, File in) {
        long bitrate = 0;
        android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
        try {
            mmr.setDataSource(in.getAbsolutePath());
            String b = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE);
            if (b != null) bitrate = Long.parseLong(b);
        } catch (Exception ignored) {
        } finally {
            try { mmr.release(); } catch (Exception ignored) { }
        }
        long target = Math.max(300_000L, (long) ((bitrate > 0 ? bitrate - 128_000 : 2_000_000) * 0.55));
        String threads = "low".equals(perf) ? "2" : "high".equals(perf) ? "0" : "4";
        String preset = "low".equals(perf) ? "ultrafast" : "high".equals(perf) ? "medium" : "veryfast";
        String[][] encoders = {
                {"-c:v", "hevc_mediacodec", "-b:v", String.valueOf(target)},
                {"-c:v", "libx265", "-preset", preset, "-crf", "28", "-x265-params", "pools=" + ("0".equals(threads) ? "*" : threads)},
        };
        for (String[] enc : encoders) {
            File out = newTempFile(context, "mp4");
            java.util.List<String> a = new java.util.ArrayList<>(java.util.Arrays.asList(
                    "-y", "-threads", threads, "-i", in.getAbsolutePath(), "-map", "0:v:0", "-map", "0:a:0?"));
            a.addAll(java.util.Arrays.asList(enc));
            a.addAll(java.util.Arrays.asList("-tag:v", "hvc1", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", out.getAbsolutePath()));
            boolean ok = FFmpeg.run(a.toArray(new String[0]));
            if (ok && out.length() > 1024 && sameDuration(in, out)) return out;
            //noinspection ResultOfMethodCallIgnored
            out.delete();
        }
        return null;
    }

    private static void replace(Result r, File newFile, String ext) {
        if (!newFile.equals(r.file)) {
            //noinspection ResultOfMethodCallIgnored
            r.file.delete();
        }
        r.file = newFile;
        r.extension = ext;
        r.mediaType = typeFor(ext);
    }

    // ---------- images ----------

    /** @return new width/height, or null if no shrinking is needed. */
    @Nullable
    static int[] targetSize(SharedPreferences prefs, int w, int h) {
        if (w <= 0 || h <= 0) return null;
        String mode = Mod69Prefs.lowImageMode(prefs);
        double scale;
        if ("percent".equals(mode)) {
            scale = Mod69Prefs.lowImagePercent(prefs) / 100.0;
        } else if ("box".equals(mode)) {
            int s = Mod69Prefs.lowImageSize(prefs);
            scale = Math.min((double) s / w, (double) s / h);
        } else {
            scale = (double) Mod69Prefs.lowImageSize(prefs) / w;
        }
        if (scale >= 1.0) return null;
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        return new int[]{nw, nh};
    }

    @Nullable
    private static File downscaleImage(Context context, SharedPreferences prefs, File in, String ext) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(in.getAbsolutePath(), bounds);
        int[] t = targetSize(prefs, bounds.outWidth, bounds.outHeight);
        if (t == null) return null;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= t[0] && bounds.outHeight / (sample * 2) >= t[1]) sample *= 2;
        opts.inSampleSize = sample;
        Bitmap src = BitmapFactory.decodeFile(in.getAbsolutePath(), opts);
        if (src == null) return null;
        Bitmap scaled = Bitmap.createScaledBitmap(src, t[0], t[1], true);
        if (scaled != src) src.recycle();

        Bitmap.CompressFormat format;
        String outExt = ext;
        if (ext.equals("png")) {
            format = Bitmap.CompressFormat.PNG;
        } else if (ext.equals("webp")) {
            format = Bitmap.CompressFormat.WEBP;
        } else {
            format = Bitmap.CompressFormat.JPEG;
            outExt = "jpg";
        }
        File out = newTempFile(context, outExt);
        try (OutputStream os = new FileOutputStream(out)) {
            scaled.compress(format, 92, os);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            return null;
        } finally {
            scaled.recycle();
        }
        if (out.length() == 0) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            return null;
        }
        return out;
    }

    private static boolean isAnimatedWebp(File f) {
        byte[] h = new byte[64];
        try (InputStream in = new FileInputStream(f)) {
            int n = in.read(h);
            if (n < 40) return false;
        } catch (IOException e) {
            return false;
        }
        // VP8X chunk flags: bit 1 = animation
        return h[12] == 'V' && h[13] == 'P' && h[14] == '8' && h[15] == 'X' && (h[20] & 0x02) != 0;
    }

    // ---------- GIF ----------

    @Nullable
    private static File gifToMp4(Context context, File gif, @Nullable SharedPreferences lowPrefs) {
        String scale = "scale=trunc(iw/2)*2:trunc(ih/2)*2";
        if (lowPrefs != null) {
            int[] wh = gifSize(gif);
            int[] t = wh == null ? null : targetSize(lowPrefs, wh[0], wh[1]);
            if (t != null) scale = "scale=" + even(t[0]) + ":" + even(t[1]);
        }
        File out = newTempFile(context, "mp4");
        List<String> base = new ArrayList<>(Arrays.asList("-y", "-i", gif.getAbsolutePath(),
                "-movflags", "+faststart", "-pix_fmt", "yuv420p", "-vf", scale, "-an"));
        if (encodeVideo(base, out)) return out;
        //noinspection ResultOfMethodCallIgnored
        out.delete();
        return null;
    }

    @Nullable
    private static File downscaleGif(Context context, SharedPreferences prefs, File gif) {
        int[] wh = gifSize(gif);
        int[] t = wh == null ? null : targetSize(prefs, wh[0], wh[1]);
        if (t == null) return null;
        File out = newTempFile(context, "gif");
        boolean ok = FFmpeg.run("-y", "-i", gif.getAbsolutePath(), "-vf",
                "scale=" + t[0] + ":" + t[1] + ":flags=lanczos,split[a][b];[a]palettegen[p];[b][p]paletteuse",
                out.getAbsolutePath());
        if (ok && out.length() > 0 && out.length() < gif.length()) return out;
        //noinspection ResultOfMethodCallIgnored
        out.delete();
        return null;
    }

    @Nullable
    private static int[] gifSize(File gif) {
        byte[] h = new byte[10];
        try (InputStream in = new FileInputStream(gif)) {
            if (in.read(h) < 10) return null;
        } catch (IOException e) {
            return null;
        }
        int w = (h[6] & 0xFF) | ((h[7] & 0xFF) << 8);
        int hh = (h[8] & 0xFF) | ((h[9] & 0xFF) << 8);
        return new int[]{w, hh};
    }

    private static int even(int v) {
        return Math.max(2, v - (v % 2));
    }

    // ---------- video ----------

    /** Tries H.264 (libx264), then the phone's H.264 encoder, then MPEG-4 as a last resort. */
    private static boolean encodeVideo(List<String> baseArgs, File out) {
        String[][] encoders = {
                {"-c:v", "libx264", "-preset", "veryfast", "-crf", "23"},
                {"-c:v", "h264_mediacodec", "-b:v", "2500k"},
                {"-c:v", "mpeg4", "-q:v", "4"}
        };
        for (String[] enc : encoders) {
            List<String> args = new ArrayList<>(baseArgs);
            args.addAll(Arrays.asList(enc));
            args.add(out.getAbsolutePath());
            if (FFmpeg.run(args.toArray(new String[0])) && out.length() > 1024) return true;
        }
        return false;
    }

    @Nullable
    private static File downscaleVideo(Context context, SharedPreferences prefs, File in) {
        int[] wh = videoSize(in);
        if (wh == null) return null;
        int target = Mod69Prefs.lowVideoHeight(prefs);
        int shortSide = Math.min(wh[0], wh[1]);
        if (shortSide <= target) return null;
        String scale = wh[0] >= wh[1] ? "scale=-2:" + target : "scale=" + target + ":-2";
        File out = newTempFile(context, "mp4");
        List<String> base = new ArrayList<>(Arrays.asList("-y", "-i", in.getAbsolutePath(),
                "-map", "0:v:0", "-map", "0:a:0?", "-vf", scale, "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart"));
        if (encodeVideo(base, out) && sameDuration(in, out) && out.length() < in.length()) return out;
        //noinspection ResultOfMethodCallIgnored
        out.delete();
        return null;
    }

    @Nullable
    private static File embedCover(Context context, File video, String[] why) {
        boolean webm = video.getName().endsWith(".webm");
        if (!webm) {
            String has = FFmpeg.probe("-v", "error", "-select_streams", "v", "-show_entries",
                    "stream_disposition=attached_pic", "-of", "default=noprint_wrappers=1:nokey=1",
                    video.getAbsolutePath());
            if (has != null && has.contains("1")) {
                why[0] = "already has one";
                return null;
            }
        }
        File thumb = newTempFile(context, "jpg");
        try {
            Bitmap frame = frameAt(video, 0.25);
            if (frame == null) {
                why[0] = "could not read a frame";
                return null;
            }
            int w = Math.min(COVER_WIDTH, frame.getWidth());
            int h = Math.max(1, Math.round(frame.getHeight() * (w / (float) frame.getWidth())));
            Bitmap small = Bitmap.createScaledBitmap(frame, w, h, true);
            try (OutputStream os = new FileOutputStream(thumb)) {
                small.compress(Bitmap.CompressFormat.JPEG, 85, os);
            }
            if (small != frame) small.recycle();
            frame.recycle();

            File out = newTempFile(context, webm ? "webm" : "mp4");
            boolean ok;
            if (webm) {
                ok = FFmpeg.run("-y", "-i", video.getAbsolutePath(), "-map", "0", "-c", "copy",
                        "-attach", thumb.getAbsolutePath(), "-metadata:s:t", "mimetype=image/jpeg",
                        "-metadata:s:t", "filename=cover.jpg", out.getAbsolutePath());
            } else {
                ok = FFmpeg.run("-y", "-i", video.getAbsolutePath(), "-i", thumb.getAbsolutePath(),
                        "-map", "0:v:0", "-map", "0:a:0?", "-map", "1", "-map_metadata", "0",
                        "-c", "copy", "-c:v:1", "mjpeg", "-disposition:v:1", "attached_pic",
                        "-movflags", "+faststart", out.getAbsolutePath());
            }
            // Safety: exit code, size and duration must all look right before the original is replaced.
            if (ok && out.length() >= video.length() * 0.9 && sameDuration(video, out)) return out;
            why[0] = !ok ? "FFmpeg error" : "result failed the safety check";
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            return null;
        } catch (IOException e) {
            why[0] = "file error";
            return null;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            thumb.delete();
        }
    }

    @Nullable
    private static int[] videoSize(File f) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(f.getAbsolutePath());
            int w = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int h = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            String rot = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if ("90".equals(rot) || "270".equals(rot)) return new int[]{h, w};
            return new int[]{w, h};
        } catch (Exception e) {
            return null;
        } finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
    }

    private static long durationMs(File f) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(f.getAbsolutePath());
            String d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return d == null ? -1 : Long.parseLong(d);
        } catch (Exception e) {
            return -1;
        } finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
    }

    /** Duration within 2% (at least 1 second) of the original. */
    private static boolean sameDuration(File original, File converted) {
        long a = durationMs(original);
        long b = durationMs(converted);
        if (b <= 0) return false;
        if (a <= 0) return true;
        return Math.abs(a - b) <= Math.max(1000, a / 50);
    }

    @Nullable
    private static Bitmap frameAt(File video, double fraction) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(video.getAbsolutePath());
            long d = durationMs(video);
            long us = d > 0 ? (long) (d * fraction * 1000) : 0;
            Bitmap b = mmr.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (b == null) b = mmr.getFrameAtTime(0);
            return b;
        } catch (Exception e) {
            return null;
        } finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
    }

    // ---------- notification thumbnail ----------

    @Nullable
    public static Bitmap makeThumbnail(File f, int mediaType) {
        try {
            Bitmap b;
            if (mediaType == TYPE_VIDEO) {
                b = frameAt(f, 0.25);
            } else {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
                int sample = 1;
                while (bounds.outWidth / (sample * 2) >= 512 && bounds.outHeight / (sample * 2) >= 256) sample *= 2;
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                b = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
            }
            if (b == null) return null;
            int max = 768;
            if (b.getWidth() > max) {
                Bitmap s = Bitmap.createScaledBitmap(b, max, Math.max(1, b.getHeight() * max / b.getWidth()), true);
                if (s != b) b.recycle();
                b = s;
            }
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Removes leftover temp files older than a day. */
    public static void cleanOld(Context context) {
        File[] files = workDir(context).listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - 24L * 3600 * 1000;
        for (File f : files) {
            if (f.lastModified() < cutoff) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }
}
