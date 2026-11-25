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

    static final boolean MESSAGING = false;

    static android.view.View messagesScreen(MainActivity a) {
        return null;
    }

    static void newMessage(MainActivity a) { }

    static boolean isDefaultSmsApp(Context c) {
        return false;
    }

    static void askToBeDefault(MainActivity a) { }

    static void openDefaultAppsSettings(MainActivity a) { }

    static void onContactsResult() { }
}
