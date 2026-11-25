package in.appbucket.spendsense;

import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Handles Reply and Mark-as-read from message notifications. */
public class ReplyReceiver extends BroadcastReceiver {
    static final String ACTION_REPLY = "in.appbucket.spendsense.REPLY";
    static final String ACTION_READ = "in.appbucket.spendsense.MARK_READ";

    @Override
    public void onReceive(Context c, Intent intent) {
        long thread = intent.getLongExtra(ConversationActivity.EXTRA_THREAD, 0);
        if (ACTION_REPLY.equals(intent.getAction())) {
            Bundle in = RemoteInput.getResultsFromIntent(intent);
            CharSequence text = in == null ? null : in.getCharSequence(MessageNotifier.KEY_REPLY);
            String address = intent.getStringExtra(ConversationActivity.EXTRA_ADDRESS);
            if (text != null && text.toString().trim().length() > 0) {
                boolean ok = SmsSender.send(c, address, text.toString().trim());
                Toast.makeText(c, ok ? "Sent" : "Couldn't send", Toast.LENGTH_SHORT).show();
            }
        }
        SmsStore.markRead(c, thread);
        MessageNotifier.cancel(c, thread);
    }
}
