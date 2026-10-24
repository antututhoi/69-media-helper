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
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            b.putBoolean(HelperContract.KEY_LOW_QUALITY, Mod69Prefs.isLowQuality(prefs));
            b.putInt(HelperContract.KEY_LOW_VIDEO_HEIGHT, Mod69Prefs.lowVideoHeight(prefs));
            b.putBoolean(HelperContract.KEY_GIF_TO_MP4, prefs.getBoolean(Mod69Prefs.GIF_TO_MP4, false));
            pong.setData(b);
            send(reply, pong);
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
            if (inUri == null || outUri == null) throw new IOException("missing file");

            temp = MediaProcessor.newTempFile(this, "in");
            try (InputStream is = getContentResolver().openInputStream(inUri);
                 OutputStream os = new FileOutputStream(temp)) {
                if (is == null) throw new IOException("cannot read input");
                copy(is, os);
            }
            long originalSize = temp.length();

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            MediaProcessor.Result r = MediaProcessor.process(this, prefs, temp, type, ext);
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
            Stats.increment(this);
        } catch (Throwable t) {
            Log.w(TAG, "processing failed", t);
            out.putString(HelperContract.KEY_ERROR, String.valueOf(t.getMessage()));
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
