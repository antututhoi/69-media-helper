package com.gor69.mediahelper;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Receives files from the other 69 apps, converts them with FFmpeg, and sends them back. */
public class MediaHelperService extends Service {
    private static final String TAG = "69MediaHelper";
    private HandlerThread thread;
    private Messenger messenger;

    @Override
    public void onCreate() {
        super.onCreate();
        thread = new HandlerThread("69-media-helper");
        thread.start();
        messenger = new Messenger(new Handler(thread.getLooper(), this::handle));
        MediaProcessor.cleanOld(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    @Override
    public void onDestroy() {
        thread.quitSafely();
        super.onDestroy();
    }

    private boolean handle(Message msg) {
        Messenger reply = msg.replyTo;
        if (msg.what == HelperContract.MSG_PING) {
            Message pong = Message.obtain(null, HelperContract.MSG_PONG);
            Bundle b = new Bundle();
            b.putInt(HelperContract.KEY_VERSION, HelperContract.VERSION);
            b.putBoolean(HelperContract.KEY_OK, FFmpeg.isAvailable());
            b.putBoolean(HelperContract.KEY_FFMPEG, FFmpeg.isAvailable());
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            b.putBoolean(HelperContract.KEY_LOW_QUALITY, Mod69Prefs.isLowQuality(prefs));
            b.putInt(HelperContract.KEY_LOW_VIDEO_HEIGHT, Mod69Prefs.lowVideoHeight(prefs));
            b.putBoolean(HelperContract.KEY_GIF_TO_MP4, prefs.getBoolean(Mod69Prefs.GIF_TO_MP4, false));
            pong.setData(b);
            send(reply, pong);
            return true;
        }
        if (msg.what == HelperContract.MSG_CUT) {
            handleCut(msg.getData(), reply);
            return true;
        }
        if (msg.what != HelperContract.MSG_PROCESS) return false;

        Bundle in = msg.getData();
        Bundle out = new Bundle();
        out.putBoolean(HelperContract.KEY_OK, false);
        File temp = null;
        File result = null;
        try {
            Uri inUri = in.getParcelable(HelperContract.KEY_IN);
            Uri outUri = in.getParcelable(HelperContract.KEY_OUT);
            int type = in.getInt(HelperContract.KEY_TYPE, MediaProcessor.TYPE_IMAGE);
            String ext = in.getString(HelperContract.KEY_EXT);
            String quality = in.getString(HelperContract.KEY_QUALITY);
            if (inUri == null || outUri == null) throw new IOException("missing file");

            temp = MediaProcessor.newTempFile(this, "in");
            try (InputStream is = getContentResolver().openInputStream(inUri);
                 OutputStream os = new FileOutputStream(temp)) {
                if (is == null) throw new IOException("cannot read input");
                copy(is, os);
            }
            long originalSize = temp.length();

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            MediaProcessor.Result r = MediaProcessor.process(this, prefs, temp, type, ext, quality);
            result = r.file;
            boolean changed = !result.equals(temp) || result.length() != originalSize;

            if (changed) {
                try (InputStream is = new FileInputStream(result);
                     OutputStream os = getContentResolver().openOutputStream(outUri, "wt")) {
                    if (os == null) throw new IOException("cannot write output");
                    copy(is, os);
                }
            }
            out.putBoolean(HelperContract.KEY_OK, true);
            out.putBoolean(HelperContract.KEY_CHANGED, changed);
            out.putInt(HelperContract.KEY_TYPE, r.mediaType);
            out.putString(HelperContract.KEY_EXT, r.extension);
            out.putString(HelperContract.KEY_MIME, r.mimeType);
            out.putString(HelperContract.KEY_SUFFIX, r.suffix);
            out.putLong(HelperContract.KEY_ORIGINAL_SIZE, originalSize);
            out.putLong(HelperContract.KEY_FINAL_SIZE, result.length());
            int[] wh = MediaProcessor.dimensions(result, r.mediaType);
            if (wh != null) {
                out.putInt(HelperContract.KEY_WIDTH, wh[0]);
                out.putInt(HelperContract.KEY_HEIGHT, wh[1]);
            }
            String info = String.join("\n", r.notes);
            out.putString(HelperContract.KEY_INFO, info);
            out.putBoolean(HelperContract.KEY_FFMPEG, FFmpeg.isAvailable());
            Stats.increment(this);
            Stats.addJob(this, r.extension.toUpperCase() + " " + MediaProcessor.size(result.length())
                    + (wh == null ? "" : " " + wh[0] + "x" + wh[1]) + " | " + info.replace("\n", "; "));
        } catch (Throwable t) {
            Log.w(TAG, "processing failed", t);
            out.putString(HelperContract.KEY_ERROR, String.valueOf(t.getMessage()));
            Stats.addJob(this, "FAILED: " + t.getClass().getSimpleName() + " " + t.getMessage());
        } finally {
            if (temp != null) //noinspection ResultOfMethodCallIgnored
                temp.delete();
            if (result != null) //noinspection ResultOfMethodCallIgnored
                result.delete();
        }
        Message m = Message.obtain(null, HelperContract.MSG_RESULT);
        m.setData(out);
        send(reply, m);
        return true;
    }

    /** 69-Next Player: cut a part (fast copy or exact re-encode) or save the sound as MP3. */
    private void handleCut(Bundle in, Messenger reply) {
        Bundle out = new Bundle();
        out.putBoolean(HelperContract.KEY_OK, false);
        File copied = null;
        File result = null;
        try {
            if (!FFmpeg.isAvailable()) throw new IOException("FFmpeg not available in the helper");
            Uri inUri = in.getParcelable(HelperContract.KEY_IN);
            Uri outUri = in.getParcelable(HelperContract.KEY_OUT);
            if (inUri == null || outUri == null) throw new IOException("missing file");
            long start = Math.max(0, in.getLong(HelperContract.KEY_START_MS, 0));
            long end = in.getLong(HelperContract.KEY_END_MS, -1);
            String mode = in.getString(HelperContract.KEY_MODE, "copy");
            String ext = in.getString(HelperContract.KEY_OUT_EXT, "mp4");

            String input = FFmpeg.safRead(this, inUri);
            if (input == null) {
                copied = MediaProcessor.newTempFile(this, "cutin");
                try (InputStream is = getContentResolver().openInputStream(inUri);
                     OutputStream os = new FileOutputStream(copied)) {
                    if (is == null) throw new IOException("cannot read input");
                    copy(is, os);
                }
                input = copied.getAbsolutePath();
            }
            result = new File(getCacheDir(), "cut_" + System.currentTimeMillis() + "." + ext);

            java.util.List<String> a = new java.util.ArrayList<>();
            a.add("-y");
            a.add("-ss"); a.add(String.format(java.util.Locale.US, "%.3f", start / 1000.0));
            a.add("-i"); a.add(input);
            if (end > start) {
                a.add("-t"); a.add(String.format(java.util.Locale.US, "%.3f", (end - start) / 1000.0));
            }
            switch (mode) {
                case "mp3":
                    a.add("-vn"); a.add("-map"); a.add("0:a:0");
                    a.add("-c:a"); a.add("libmp3lame"); a.add("-q:a"); a.add("2");
                    break;
                case "exact":
                    a.add("-map"); a.add("0:v:0?"); a.add("-map"); a.add("0:a?");
                    a.add("-c:v"); a.add("libx264"); a.add("-preset"); a.add("veryfast"); a.add("-crf"); a.add("20");
                    a.add("-pix_fmt"); a.add("yuv420p");
                    a.add("-c:a"); a.add("aac"); a.add("-b:a"); a.add("192k");
                    a.add("-movflags"); a.add("+faststart");
                    break;
                default:
                    a.add("-map"); a.add("0:v?"); a.add("-map"); a.add("0:a?");
                    if (ext.equals("mkv")) { a.add("-map"); a.add("0:s?"); }
                    a.add("-c"); a.add("copy");
                    a.add("-avoid_negative_ts"); a.add("make_zero");
                    break;
            }
            a.add(result.getAbsolutePath());
            boolean ok = FFmpeg.run(a.toArray(new String[0]));
            if (!ok && mode.equals("mp3")) {
                // some builds have no LAME: fall back to AAC in an .m4a-compatible stream
                throw new IOException("MP3 encoder failed");
            }
            if (!ok || !result.exists() || result.length() == 0) throw new IOException("FFmpeg failed");
            try (InputStream is = new FileInputStream(result);
                 OutputStream os = getContentResolver().openOutputStream(outUri, "wt")) {
                if (os == null) throw new IOException("cannot write output");
                copy(is, os);
            }
            out.putBoolean(HelperContract.KEY_OK, true);
            out.putLong(HelperContract.KEY_FINAL_SIZE, result.length());
            Stats.increment(this);
            Stats.addJob(this, "Next Player " + mode + " " + MediaProcessor.size(result.length()));
        } catch (Throwable t) {
            Log.w(TAG, "cut failed", t);
            out.putString(HelperContract.KEY_ERROR, String.valueOf(t.getMessage()));
            Stats.addJob(this, "FAILED cut: " + t.getMessage());
        } finally {
            if (copied != null) //noinspection ResultOfMethodCallIgnored
                copied.delete();
            if (result != null) //noinspection ResultOfMethodCallIgnored
                result.delete();
        }
        Message m = Message.obtain(null, HelperContract.MSG_CUT_RESULT);
        m.setData(out);
        send(reply, m);
    }

    private static void send(Messenger to, Message m) {
        if (to == null) return;
        try {
            to.send(m);
        } catch (RemoteException e) {
            Log.w(TAG, "app went away before the answer", e);
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.flush();
    }
}
