package in.appbucket.spendsense;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import java.util.LinkedHashMap;
import java.util.Map;

/** Adds new transactions the moment a bank SMS arrives. */
public class SmsReceiver extends BroadcastReceiver {

    static final String CHANNEL = "new_expense";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        if (SmsStore.isDefault(ctx)) return; // SmsDeliverReceiver stores, tracks and notifies
        SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (msgs == null || msgs.length == 0) return;

        // Long SMS arrive in parts: join them per sender.
        Map<String, StringBuilder> bodies = new LinkedHashMap<>();
        for (SmsMessage m : msgs) {
            if (m == null) continue;
            String from = m.getDisplayOriginatingAddress();
            if (from == null) from = "";
            StringBuilder sb = bodies.get(from);
            if (sb == null) {
                sb = new StringBuilder();
                bodies.put(from, sb);
            }
            sb.append(m.getMessageBody());
        }

        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                for (Map.Entry<String, StringBuilder> en : bodies.entrySet()) {
                    Expense e = SmsImporter.handle(ctx, en.getKey(), en.getValue().toString(),
                            System.currentTimeMillis());
                    if (e != null) notifyAdded(ctx, e);
                }
            } finally {
                pending.finish();
            }
        }).start();
    }

    private static void notifyAdded(Context ctx, Expense e) {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "New transactions",
                    NotificationManager.IMPORTANCE_DEFAULT));
            b = new Notification.Builder(ctx, CHANNEL);
        } else {
            b = new Notification.Builder(ctx);
        }
        Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE);
        String title = (e.debit ? "Spent " : "Received ") + Format.money(e.amount);
        String text = (e.debit ? "at " : "from ") + e.merchant + " · " + e.category;
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true);
        nm.notify((int) e.id, b.build());
    }
}
