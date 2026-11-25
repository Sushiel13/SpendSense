package in.appbucket.spendsense;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/** Notifications for incoming messages, with Reply and Mark-as-read actions. */
final class MessageNotifier {

    static final String CHANNEL = "messages";
    static final String KEY_REPLY = "reply_text";

    private MessageNotifier() {}

    static void show(Context c, long threadId, String address, String body, Expense expense) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("New text messages");
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(c, CHANNEL);
        } else {
            b = new Notification.Builder(c);
            b.setPriority(Notification.PRIORITY_HIGH);
            b.setDefaults(Notification.DEFAULT_ALL);
        }
        int id = (int) (threadId & 0x7FFFFFFF);
        String name = SmsStore.displayName(c, address);

        Intent open = new Intent(c, ConversationActivity.class)
                .putExtra(ConversationActivity.EXTRA_THREAD, threadId)
                .putExtra(ConversationActivity.EXTRA_ADDRESS, address)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, id, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String text = body;
        if (expense != null) {
            text = (expense.debit ? "Spent " : "Received ") + Format.money(expense.amount)
                    + " · " + expense.category + " (added to SpendSense)\n" + body;
        }
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(name)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true);

        // Reply straight from the notification (not for business senders, which can't receive replies)
        if (!SmsStore.isBusiness(address)) {
            Intent r = new Intent(c, ReplyReceiver.class).setAction(ReplyReceiver.ACTION_REPLY)
                    .putExtra(ConversationActivity.EXTRA_THREAD, threadId)
                    .putExtra(ConversationActivity.EXTRA_ADDRESS, address);
            int mutable = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
            PendingIntent rpi = PendingIntent.getBroadcast(c, id, r, mutable | PendingIntent.FLAG_UPDATE_CURRENT);
            RemoteInput ri = new RemoteInput.Builder(KEY_REPLY).setLabel("Reply").build();
            b.addAction(new Notification.Action.Builder(null, "Reply", rpi).addRemoteInput(ri).build());
        }
        Intent mr = new Intent(c, ReplyReceiver.class).setAction(ReplyReceiver.ACTION_READ)
                .putExtra(ConversationActivity.EXTRA_THREAD, threadId);
        PendingIntent mpi = PendingIntent.getBroadcast(c, id + 1, mr,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        b.addAction(new Notification.Action.Builder(null, "Mark as read", mpi).build());
        nm.notify(id, b.build());
    }

    static void cancel(Context c, long threadId) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel((int) (threadId & 0x7FFFFFFF));
    }
}
