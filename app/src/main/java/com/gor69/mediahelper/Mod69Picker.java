package com.gor69.mediahelper;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.List;

/**
 * 69 mod: which app opens when this app asks for a folder or a file:
 * 69-Material Files, MiXplorer or the phone's own picker (SAF). Same in every 69 app.
 * If the chosen app is missing or can't answer this request, the phone's picker is used.
 */
public final class Mod69Picker {
    public static final String SYSTEM = "system";
    public static final String MATERIAL_FILES = "materialfiles";
    public static final String MIXPLORER = "mixplorer";
    private static final String PREFS = "mod69_picker";
    private static final String KEY = "choice";
    private static final String[] MF = {"me.zhanghai.android.files.mod69", "me.zhanghai.android.files"};
    private static final String[] MX = {"com.mixplorer.silver", "com.mixplorer"};
    public static final String[] VALUES = {MATERIAL_FILES, MIXPLORER, SYSTEM};
    public static final String[] NAMES = {"69-Material Files", "MiXplorer", "Phone's own picker (SAF)"};

    private Mod69Picker() {}

    public static String choice(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, MATERIAL_FILES);
    }

    public static void set(Context c, String value) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value).apply();
    }

    public static String choiceName(Context c) {
        String v = choice(c);
        for (int i = 0; i < VALUES.length; i++) if (VALUES[i].equals(v)) return NAMES[i];
        return NAMES[0];
    }

    /** A small dialog to change the setting. */
    public static void showSettingDialog(Context c, Runnable onChanged) {
        String now = choice(c);
        int checked = 0;
        for (int i = 0; i < VALUES.length; i++) if (VALUES[i].equals(now)) checked = i;
        new AlertDialog.Builder(c)
                .setTitle("Folder / file picker")
                .setSingleChoiceItems(NAMES, checked, (d, which) -> {
                    set(c, VALUES[which]);
                    d.dismiss();
                    if (onChanged != null) onChanged.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static boolean isPickerAction(String a) {
        return Intent.ACTION_OPEN_DOCUMENT_TREE.equals(a) || Intent.ACTION_OPEN_DOCUMENT.equals(a)
                || Intent.ACTION_CREATE_DOCUMENT.equals(a) || Intent.ACTION_GET_CONTENT.equals(a);
    }

    private static boolean resolves(Context c, Intent i) {
        try {
            return !c.getPackageManager().queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static Intent withPackage(Context c, Intent intent, String[] packages) {
        for (String p : packages) {
            Intent t = new Intent(intent).setPackage(p);
            if (resolves(c, t)) return t;
        }
        return null;
    }

    private static Intent system(Context c, Intent intent) {
        try {
            List<ResolveInfo> list = c.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo r : list) {
                String p = r.activityInfo.packageName;
                if (p.contains("documentsui")) return new Intent(intent).setPackage(p);
            }
        } catch (Exception ignored) {
        }
        return intent;
    }

    /** Points a picker intent at the chosen app. Other intents are returned unchanged. */
    public static Intent apply(Context c, Intent intent) {
        if (intent == null || !isPickerAction(intent.getAction())) return intent;
        String choice = choice(c);
        Intent out = null;
        if (MATERIAL_FILES.equals(choice)) out = withPackage(c, intent, MF);
        else if (MIXPLORER.equals(choice)) out = withPackage(c, intent, MX);
        return out != null ? out : system(c, intent);
    }
}
