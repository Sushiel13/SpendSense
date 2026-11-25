package in.appbucket.spendsense;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Receives SMS when SpendSense is the default SMS app. Android then stops saving messages
 * itself, so this stores each one, adds bank SMS as transactions, and shows a notification.
 */
public class SmsDeliverReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(intent.getAction())) return;
        SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (msgs == null || msgs.length == 0) return;

        Map<String, StringBuilder> bodies = new LinkedHashMap<>();
        Map<String, Long> sentAt = new LinkedHashMap<>();
        for (SmsMessage m : msgs) {
            if (m == null) continue;
            String from = m.getDisplayOriginatingAddress();
            if (from == null) from = "";
            StringBuilder sb = bodies.get(from);
            if (sb == null) {
                sb = new StringBuilder();
                bodies.put(from, sb);
                sentAt.put(from, m.getTimestampMillis());
            }
            sb.append(m.getDisplayMessageBody());
        }

        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                long now = System.currentTimeMillis();
                for (Map.Entry<String, StringBuilder> en : bodies.entrySet()) {
                    String from = en.getKey();
                    String body = en.getValue().toString();
                    Long sent = sentAt.get(from);
                    Uri row = SmsStore.insertInbox(ctx, from, body, now, sent == null ? now : sent);
                    Expense e = SmsImporter.handle(ctx, from, body, now);
                    long thread = SmsStore.threadFor(ctx, from);
                    if (thread != 0 && thread == ConversationActivity.openThread) continue; // already on screen
                    MessageNotifier.show(ctx, thread, from, body, e);
                }
            } finally {
                pending.finish();
            }
        }).start();
    }
}
