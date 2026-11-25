package in.appbucket.spendsense;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * Required for an app to be eligible as the default SMS app. SpendSense doesn't download
 * picture messages (MMS), so it tells the user one arrived.
 */
public class MmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(MessageNotifier.CHANNEL, "Messages",
                    NotificationManager.IMPORTANCE_HIGH));
            b = new Notification.Builder(c, MessageNotifier.CHANNEL);
        } else {
            b = new Notification.Builder(c);
        }
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Picture message received")
                .setStyle(new Notification.BigTextStyle().bigText(
                        "SpendSense can't open picture messages (MMS). To get it, set Google Messages as your SMS app "
                                + "in Settings → Apps → Default apps."))
                .setAutoCancel(true);
        nm.notify(4242, b.build());
    }
}
