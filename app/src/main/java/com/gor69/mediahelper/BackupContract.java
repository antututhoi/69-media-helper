package com.gor69.mediahelper;

/**
 * How 69-Media Helper backs up and restores the other 69 apps. Keep this file identical in every app.
 *
 * Every 69 app has a small service (action ACTION_BACKUP) that only apps with the same signing key may use.
 * Backup:  helper sends MSG_BACKUP -> the app zips its own data and answers MSG_REPLY with KEY_URI (read access granted).
 *          The helper copies the zip, then sends MSG_CLEANUP.
 * Restore: helper sends MSG_RESTORE_PREPARE -> the app answers with an empty KEY_URI (write access granted),
 *          the helper writes the zip into it and sends MSG_RESTORE_APPLY -> the app unpacks it, answers, and closes itself.
 */
public final class BackupContract {
    public static final String ACTION_BACKUP = "com.gor69.mod69.BACKUP";
    public static final String HELPER_PACKAGE = "com.gor69.mediahelper";

    public static final int MSG_BACKUP = 10;
    public static final int MSG_CLEANUP = 11;
    public static final int MSG_RESTORE_PREPARE = 12;
    public static final int MSG_RESTORE_APPLY = 13;
    public static final int MSG_REPLY = 14;

    public static final String KEY_OK = "ok";
    public static final String KEY_URI = "uri";
    public static final String KEY_ERROR = "error";
    public static final String KEY_SIZE = "size";

    /** Name of the info file inside every backup zip (first line: package name). */
    public static final String INFO_ENTRY = "mod69_backup.txt";

    private BackupContract() {}
}
