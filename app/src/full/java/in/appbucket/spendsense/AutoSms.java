package in.appbucket.spendsense;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/** Inbox reading. Only in the automatic ("full") build; the lite build has a no-op version. */
final class AutoSms {

    private AutoSms() {}

    /** Runtime permissions this build asks for to read SMS. */
    static final String[] PERMISSIONS = {
            android.Manifest.permission.READ_SMS, android.Manifest.permission.RECEIVE_SMS};

    static boolean granted(Context ctx) {
        return ctx.checkSelfPermission(android.Manifest.permission.READ_SMS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** Scans the SMS inbox from {@code sinceMillis}. Returns how many new transactions were added. */
    static int scanInbox(Context ctx, long sinceMillis) {
        return scanInbox(ctx, sinceMillis, null);
    }

    static int scanInbox(Context ctx, long sinceMillis, SmsImporter.Progress progress) {
        int added = 0;
        Uri inbox = Uri.parse("content://sms/inbox");
        String[] cols = {"address", "body", "date"};
        android.database.sqlite.SQLiteDatabase db = ExpenseDb.get(ctx).getWritableDatabase();
        try (Cursor c = ctx.getContentResolver().query(inbox, cols, "date>=?",
                new String[]{String.valueOf(sinceMillis)}, "date ASC")) {
            if (c == null) return 0;
            int total = c.getCount(), scanned = 0;
            db.beginTransaction();
            try {
                while (c.moveToNext()) {
                    if (SmsImporter.handle(ctx, c.getString(0), c.getString(1), c.getLong(2)) != null) added++;
                    scanned++;
                    if (scanned % 200 == 0) {
                        db.setTransactionSuccessful();
                        db.endTransaction();
                        db.beginTransaction();
                        if (progress != null) progress.update(scanned, total, added);
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            if (progress != null) progress.update(scanned, total, added);
        } catch (SecurityException ignored) {
            // permission revoked mid-scan
        }
        return added;
    }
}
