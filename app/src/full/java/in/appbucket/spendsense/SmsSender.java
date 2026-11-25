package in.appbucket.spendsense;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.telephony.SmsManager;

import java.util.ArrayList;

/** Sends SMS and records them in the phone's message store. */
final class SmsSender {

    static final String ACTION_SENT = "in.appbucket.spendsense.SMS_SENT";
    static final String ACTION_DELIVERED = "in.appbucket.spendsense.SMS_DELIVERED";
    private static int requestCode = (int) (System.currentTimeMillis() & 0xFFFFF);

    private SmsSender() {}

    @SuppressWarnings("deprecation")
    static SmsManager manager(Context c) {
        if (Build.VERSION.SDK_INT >= 31) {
            SmsManager m = c.getSystemService(SmsManager.class);
            if (m != null) return m;
        }
        return SmsManager.getDefault();
    }

    /** Returns false if the message couldn't be handed to the phone's SMS system. */
    static boolean send(Context c, String address, String body) {
        if (address == null || address.trim().isEmpty() || body == null || body.trim().isEmpty()) return false;
        address = address.trim();
        long thread = SmsStore.threadFor(c, address);
        // As default SMS app we must store sent messages ourselves; otherwise Android does it.
        Uri row = SmsStore.isDefault(c) ? SmsStore.insertOutbox(c, address, body, thread) : null;
        try {
            SmsManager sm = manager(c);
            ArrayList<String> parts = sm.divideMessage(body);
            ArrayList<PendingIntent> sent = new ArrayList<>();
            ArrayList<PendingIntent> delivered = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                Intent s = new Intent(c, SentReceiver.class).setAction(ACTION_SENT);
                if (row != null) s.setData(row);
                s.putExtra("last", i == parts.size() - 1);
                sent.add(PendingIntent.getBroadcast(c, requestCode++, s,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
                Intent d = new Intent(c, SentReceiver.class).setAction(ACTION_DELIVERED);
                if (row != null) d.setData(row);
                delivered.add(PendingIntent.getBroadcast(c, requestCode++, d,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            }
            sm.sendMultipartTextMessage(address, null, parts, sent, delivered);
            return true;
        } catch (Exception e) {
            SmsStore.setType(c, row, SmsStore.TYPE_FAILED);
            return false;
        }
    }
}
