package in.appbucket.spendsense;

import android.content.Context;

/** Lite build: contains no SMS-reading code at all. Transactions come from Share or Paste. */
final class AutoSms {

    private AutoSms() {}

    static final String[] PERMISSIONS = {};

    static boolean granted(Context ctx) {
        return false;
    }

    static int scanInbox(Context ctx, long sinceMillis, SmsImporter.Progress progress) {
        return 0;
    }
}
