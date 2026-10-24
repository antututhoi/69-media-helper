package com.gor69.mediahelper;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 69 mod: lets 69-Media Helper back up and restore this app's own data (settings, database, logins).
 * Only apps signed with the same key can use it (the helper's signature permission).
 */
public class Mod69BackupService extends Service {
    private static final String TAG = "Mod69Backup";
    /** Folders in the app's data folder that are not worth saving (temporary or rebuilt by Android). */
    private static final Set<String> SKIP = new HashSet<>(Arrays.asList("cache", "code_cache", "lib", "app_webview", "no_backup"));

    private HandlerThread thread;
    private Messenger messenger;

    private File work() {
        File d = new File(getCacheDir(), "mod69");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private Uri share(File f) {
        return FileProvider.getUriForFile(this, getPackageName() + ".provider", f);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        thread = new HandlerThread("mod69-backup");
        thread.start();
        messenger = new Messenger(new Handler(thread.getLooper(), this::handle));
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

    private File dataDir() {
        return Build.VERSION.SDK_INT >= 24 ? getDataDir() : getFilesDir().getParentFile();
    }

    private boolean handle(Message msg) {
        Bundle out = new Bundle();
        boolean exitAfter = false;
        try {
            switch (msg.what) {
                case BackupContract.MSG_BACKUP: {
                    File zip = new File(work(), "backup.zip");
                    zipData(zip);
                    Uri u = share(zip);
                    grantUriPermission(BackupContract.HELPER_PACKAGE, u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    out.putParcelable(BackupContract.KEY_URI, u);
                    out.putLong(BackupContract.KEY_SIZE, zip.length());
                    out.putBoolean(BackupContract.KEY_OK, true);
                    break;
                }
                case BackupContract.MSG_CLEANUP: {
                    //noinspection ResultOfMethodCallIgnored
                    new File(work(), "backup.zip").delete();
                    //noinspection ResultOfMethodCallIgnored
                    new File(work(), "restore.zip").delete();
                    out.putBoolean(BackupContract.KEY_OK, true);
                    break;
                }
                case BackupContract.MSG_RESTORE_PREPARE: {
                    File zip = new File(work(), "restore.zip");
                    //noinspection ResultOfMethodCallIgnored
                    zip.delete();
                    //noinspection ResultOfMethodCallIgnored
                    zip.createNewFile();
                    Uri u = share(zip);
                    grantUriPermission(BackupContract.HELPER_PACKAGE, u,
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    out.putParcelable(BackupContract.KEY_URI, u);
                    out.putBoolean(BackupContract.KEY_OK, true);
                    break;
                }
                case BackupContract.MSG_RESTORE_APPLY: {
                    File zip = new File(work(), "restore.zip");
                    restore(zip);
                    //noinspection ResultOfMethodCallIgnored
                    zip.delete();
                    out.putBoolean(BackupContract.KEY_OK, true);
                    exitAfter = true;
                    break;
                }
                default:
                    return false;
            }
        } catch (Throwable t) {
            Log.w(TAG, "backup step failed", t);
            out.putBoolean(BackupContract.KEY_OK, false);
            out.putString(BackupContract.KEY_ERROR, t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        Message reply = Message.obtain(null, BackupContract.MSG_REPLY);
        reply.setData(out);
        try {
            if (msg.replyTo != null) msg.replyTo.send(reply);
        } catch (RemoteException ignored) {
        }
        if (exitAfter) {
            // close the app so it starts fresh with the restored data next time
            new Handler(getMainLooper()).postDelayed(() -> {
                android.os.Process.killProcess(android.os.Process.myPid());
            }, 800);
        }
        return true;
    }

    private void zipData(File zip) throws IOException {
        File root = dataDir();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry(BackupContract.INFO_ENTRY));
            String info = getPackageName() + "\n" + System.currentTimeMillis() + "\n";
            zos.write(info.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            File[] top = root.listFiles();
            if (top != null) {
                for (File f : top) {
                    if (SKIP.contains(f.getName())) continue;
                    add(zos, f, f.getName());
                }
            }
        }
    }

    private void add(ZipOutputStream zos, File f, String name) throws IOException {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids == null) return;
            for (File k : kids) add(zos, k, name + "/" + k.getName());
            return;
        }
        if (!f.canRead()) return;
        zos.putNextEntry(new ZipEntry(name));
        try (InputStream in = new FileInputStream(f)) {
            copy(in, zos);
        }
        zos.closeEntry();
    }

    private void restore(File zip) throws IOException {
        // check it is a backup of this app
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry e = zis.getNextEntry();
            if (e == null || !BackupContract.INFO_ENTRY.equals(e.getName())) throw new IOException("not a 69 backup");
            String pkg = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8)).readLine();
            if (pkg == null || !pkg.trim().equals(getPackageName())) {
                throw new IOException("this backup is for " + pkg);
            }
        }
        File root = dataDir();
        String rootPath = root.getCanonicalPath() + File.separator;
        // remove the current data first, so old and restored files don't mix
        File[] top = root.listFiles();
        if (top != null) {
            for (File f : top) {
                if (SKIP.contains(f.getName())) continue;
                deleteTree(f);
            }
        }
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory() || BackupContract.INFO_ENTRY.equals(e.getName())) continue;
                File target = new File(root, e.getName());
                if (!target.getCanonicalPath().startsWith(rootPath)) continue; // never write outside the data folder
                File parent = target.getParentFile();
                //noinspection ResultOfMethodCallIgnored
                if (parent != null) parent.mkdirs();
                try (OutputStream os = new FileOutputStream(target)) {
                    copy(zis, os);
                }
            }
        }
    }

    private static void deleteTree(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteTree(k);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }
}
