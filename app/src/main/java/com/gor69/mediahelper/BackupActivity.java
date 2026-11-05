package com.gor69.mediahelper;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.provider.DocumentsContract;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Backs up (APK + data) and restores every installed 69 app, from one screen. */
public class BackupActivity extends AppCompatActivity {
    private static final int REQ_BACKUP_FOLDER = 1;
    private static final int REQ_RESTORE_FOLDER = 2;
    private static final String PREF_FOLDER = "backup_folder";

    private final Map<String, CheckBox> boxes = new LinkedHashMap<>();
    private TextView folderText;
    private TextView log;
    private Button backupButton;
    private Button restoreButton;
    private final Handler main = new Handler(Looper.getMainLooper());

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.backup_title);
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(16), dp(16), dp(16));
        scroll.addView(col);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(b.left, b.top, b.right, b.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        TextView title = new TextView(this);
        title.setText(R.string.backup_title);
        title.setTextSize(20);
        col.addView(title);

        folderText = new TextView(this);
        folderText.setPadding(0, dp(12), 0, dp(4));
        col.addView(folderText);
        Button choose = new Button(this);
        choose.setText(R.string.backup_choose_folder);
        choose.setOnClickListener(v -> pickFolder(REQ_BACKUP_FOLDER));
        col.addView(choose);

        TextView appsLabel = new TextView(this);
        appsLabel.setText(R.string.backup_apps);
        appsLabel.setPadding(0, dp(16), 0, dp(4));
        col.addView(appsLabel);
        for (Map.Entry<String, String> app : find69Apps().entrySet()) {
            CheckBox cb = new CheckBox(this);
            cb.setText(app.getValue() + "\n" + app.getKey());
            cb.setChecked(true);
            boxes.put(app.getKey(), cb);
            col.addView(cb);
        }
        if (boxes.isEmpty()) {
            TextView none = new TextView(this);
            none.setText(R.string.backup_no_apps);
            col.addView(none);
        }

        backupButton = new Button(this);
        backupButton.setText(R.string.backup_now);
        backupButton.setOnClickListener(v -> startBackup());
        col.addView(backupButton);

        restoreButton = new Button(this);
        restoreButton.setText(R.string.backup_restore);
        restoreButton.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.backup_restore)
                .setMessage(R.string.backup_restore_explain)
                .setPositiveButton(R.string.backup_pick_backup, (d, w) -> pickFolder(REQ_RESTORE_FOLDER))
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        col.addView(restoreButton);

        log = new TextView(this);
        log.setPadding(0, dp(16), 0, 0);
        log.setTextIsSelectable(true);
        col.addView(log);

        setContentView(scroll);
        showFolder();
    }

    /** Installed 69 apps (package -> name), found by their backup service, plus this helper. */
    private Map<String, String> find69Apps() {
        Map<String, String> apps = new LinkedHashMap<>();
        PackageManager pm = getPackageManager();
        List<ResolveInfo> found = pm.queryIntentServices(new Intent(BackupContract.ACTION_BACKUP), 0);
        for (ResolveInfo ri : found) {
            String pkg = ri.serviceInfo.packageName;
            apps.put(pkg, label(pkg));
        }
        apps.put(getPackageName(), label(getPackageName()));
        return apps;
    }

    private String label(String pkg) {
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    private void showFolder() {
        String f = prefs().getString(PREF_FOLDER, null);
        folderText.setText(getString(R.string.backup_folder, f == null ? getString(R.string.backup_folder_none)
                : Uri.decode(DocumentsContract.getTreeDocumentId(Uri.parse(f)))));
    }

    private void pickFolder(int req) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(Mod69Picker.apply(this, i), req);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        if (requestCode == REQ_BACKUP_FOLDER) {
            prefs().edit().putString(PREF_FOLDER, tree.toString()).apply();
            showFolder();
        } else if (requestCode == REQ_RESTORE_FOLDER) {
            startRestore(tree);
        }
    }

    private void say(String line) {
        main.post(() -> log.append(line + "\n"));
    }

    private void busy(boolean b) {
        main.post(() -> {
            backupButton.setEnabled(!b);
            restoreButton.setEnabled(!b);
        });
    }

    // ---------------- backup ----------------

    private void startBackup() {
        String f = prefs().getString(PREF_FOLDER, null);
        if (f == null) {
            pickFolder(REQ_BACKUP_FOLDER);
            return;
        }
        List<String> chosen = new ArrayList<>();
        for (Map.Entry<String, CheckBox> e : boxes.entrySet()) if (e.getValue().isChecked()) chosen.add(e.getKey());
        if (chosen.isEmpty()) return;
        log.setText("");
        busy(true);
        Uri tree = Uri.parse(f);
        new Thread(() -> {
            try {
                Uri root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
                String name = "69-backup " + new SimpleDateFormat("yyyy-MM-dd HHmm", Locale.US).format(new Date());
                Uri dir = DocumentsContract.createDocument(getContentResolver(), root, DocumentsContract.Document.MIME_TYPE_DIR, name);
                if (dir == null) throw new IOException("cannot create the backup folder");
                say(getString(R.string.backup_saving_to, name));
                int ok = 0;
                for (String pkg : chosen) {
                    String label = safe(label(pkg));
                    say("• " + label);
                    try {
                        saveApk(pkg, dir, label);
                        say("   APK saved");
                    } catch (Exception e) {
                        say("   APK failed: " + e.getMessage());
                    }
                    try {
                        long size = saveData(pkg, dir, label);
                        say("   data saved (" + MediaProcessor.size(size) + ")");
                        ok++;
                    } catch (Exception e) {
                        say("   data failed: " + e.getMessage());
                    }
                }
                say(getString(R.string.backup_done, ok, chosen.size()));
            } catch (Exception e) {
                say("Backup failed: " + e.getMessage());
            } finally {
                busy(false);
            }
        }).start();
    }

    private static String safe(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private void saveApk(String pkg, Uri dir, String label) throws Exception {
        ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
        String[] splits = ai.splitSourceDirs;
        if (splits == null || splits.length == 0) {
            Uri out = DocumentsContract.createDocument(getContentResolver(), dir, "application/vnd.android.package-archive", label + ".apk");
            if (out == null) throw new IOException("cannot create file");
            try (InputStream in = new FileInputStream(ai.sourceDir); OutputStream os = getContentResolver().openOutputStream(out)) {
                copy(in, os);
            }
        } else {
            // app made of several parts: one .apks file (install it with SAI)
            Uri out = DocumentsContract.createDocument(getContentResolver(), dir, "application/octet-stream", label + ".apks");
            if (out == null) throw new IOException("cannot create file");
            try (OutputStream os = getContentResolver().openOutputStream(out); ZipOutputStream zos = new ZipOutputStream(os)) {
                addFile(zos, new File(ai.sourceDir), "base.apk");
                for (String s : splits) addFile(zos, new File(s), new File(s).getName());
            }
        }
    }

    private static void addFile(ZipOutputStream zos, File f, String name) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        try (InputStream in = new FileInputStream(f)) {
            copy(in, zos);
        }
        zos.closeEntry();
    }

    private long saveData(String pkg, Uri dir, String label) throws Exception {
        try (Conn c = new Conn(pkg)) {
            Bundle r = c.call(BackupContract.MSG_BACKUP, new Bundle(), 10 * 60);
            Uri zip = r.getParcelable(BackupContract.KEY_URI);
            if (zip == null) throw new IOException("no data");
            Uri out = DocumentsContract.createDocument(getContentResolver(), dir, "application/zip", label + " data.zip");
            if (out == null) throw new IOException("cannot create file");
            long n;
            try (InputStream in = getContentResolver().openInputStream(zip); OutputStream os = getContentResolver().openOutputStream(out)) {
                if (in == null || os == null) throw new IOException("cannot copy");
                n = copy(in, os);
            }
            c.call(BackupContract.MSG_CLEANUP, new Bundle(), 60);
            return n;
        }
    }

    // ---------------- restore ----------------

    private void startRestore(Uri tree) {
        log.setText("");
        busy(true);
        new Thread(() -> {
            try {
                Uri root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
                Map<String, Uri> files = list(tree, root);
                // the helper's own data last: restoring it closes this screen
                List<Map.Entry<String, Uri>> order = new ArrayList<>(files.entrySet());
                String own = safe(label(getPackageName())) + " data.zip";
                order.sort((a, b) -> Boolean.compare(a.getKey().equals(own), b.getKey().equals(own)));
                int done = 0;
                for (Map.Entry<String, Uri> f : order) {
                    if (!f.getKey().endsWith(" data.zip")) continue;
                    String pkg = readPackage(f.getValue());
                    String label = f.getKey().substring(0, f.getKey().length() - " data.zip".length());
                    if (pkg == null) {
                        say("• " + f.getKey() + ": not a 69 backup, skipped");
                        continue;
                    }
                    if (!installed(pkg)) {
                        Uri apk = files.get(label + ".apk");
                        say("• " + label + ": not installed. " + (apk != null ? "Opening its APK - install it, then restore again."
                                : "Install it (the .apks file needs the SAI app), then restore again."));
                        if (apk != null) main.post(() -> openApk(apk));
                        continue;
                    }
                    try {
                        restoreData(pkg, f.getValue());
                        say("• " + label + ": restored. Open the app again.");
                        done++;
                    } catch (Exception e) {
                        say("• " + label + ": failed: " + e.getMessage());
                    }
                }
                say(getString(R.string.backup_restored, done));
            } catch (Exception e) {
                say("Restore failed: " + e.getMessage());
            } finally {
                busy(false);
            }
        }).start();
    }

    private boolean installed(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void openApk(Uri apk) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(apk, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            say("   cannot open the installer: " + e.getMessage());
        }
    }

    /** Name -> document of the files directly inside a picked folder. */
    private Map<String, Uri> list(Uri tree, Uri dir) {
        Map<String, Uri> out = new LinkedHashMap<>();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(dir));
        try (Cursor c = getContentResolver().query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    out.put(c.getString(1), DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)));
                }
            }
        }
        return out;
    }

    @Nullable
    private String readPackage(Uri zip) {
        try (InputStream in = getContentResolver().openInputStream(zip); ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry e = zis.getNextEntry();
            if (e == null || !BackupContract.INFO_ENTRY.equals(e.getName())) return null;
            byte[] buf = new byte[512];
            int n = zis.read(buf);
            if (n <= 0) return null;
            String first = new String(buf, 0, n, StandardCharsets.UTF_8).split("\n")[0].trim();
            return first.isEmpty() ? null : first;
        } catch (Exception e) {
            return null;
        }
    }

    private void restoreData(String pkg, Uri zip) throws Exception {
        try (Conn c = new Conn(pkg)) {
            Bundle r = c.call(BackupContract.MSG_RESTORE_PREPARE, new Bundle(), 60);
            Uri target = r.getParcelable(BackupContract.KEY_URI);
            if (target == null) throw new IOException("the app did not answer");
            try (InputStream in = getContentResolver().openInputStream(zip); OutputStream os = getContentResolver().openOutputStream(target, "wt")) {
                if (in == null || os == null) throw new IOException("cannot copy");
                copy(in, os);
            }
            c.call(BackupContract.MSG_RESTORE_APPLY, new Bundle(), 10 * 60);
        }
    }

    // ---------------- talking to an app ----------------

    /** One connection to a 69 app's backup service. */
    private final class Conn implements AutoCloseable {
        private final CountDownLatch connected = new CountDownLatch(1);
        private final AtomicReference<Messenger> service = new AtomicReference<>();
        private final HandlerThread replies = new HandlerThread("backup-reply");
        private final ServiceConnection sc = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                service.set(new Messenger(binder));
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                service.set(null);
            }
        };

        Conn(String pkg) throws Exception {
            replies.start();
            Intent i = new Intent(BackupContract.ACTION_BACKUP).setPackage(pkg);
            boolean bound = bindService(i, sc, Context.BIND_AUTO_CREATE);
            if (!bound || !connected.await(15, TimeUnit.SECONDS) || service.get() == null) {
                throw new IOException("app not reachable (is it signed with the same key?)");
            }
        }

        Bundle call(int what, Bundle data, int timeoutSeconds) throws Exception {
            CountDownLatch answered = new CountDownLatch(1);
            AtomicReference<Bundle> answer = new AtomicReference<>();
            Message m = Message.obtain(null, what);
            m.setData(data);
            m.replyTo = new Messenger(new Handler(replies.getLooper(), msg -> {
                if (msg.what == BackupContract.MSG_REPLY) {
                    answer.set(msg.getData());
                    answered.countDown();
                }
                return true;
            }));
            Messenger s = service.get();
            if (s == null) throw new IOException("app went away");
            s.send(m);
            if (!answered.await(timeoutSeconds, TimeUnit.SECONDS)) throw new IOException("no answer in time");
            Bundle b = answer.get();
            if (b == null || !b.getBoolean(BackupContract.KEY_OK, false)) {
                throw new IOException(b == null ? "no answer" : String.valueOf(b.getString(BackupContract.KEY_ERROR)));
            }
            return b;
        }

        @Override
        public void close() {
            try {
                unbindService(sc);
            } catch (Exception ignored) {
            }
            replies.quitSafely();
        }
    }

    private static long copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            total += n;
        }
        out.flush();
        return total;
    }
}
