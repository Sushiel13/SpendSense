package in.appbucket.spendsense;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Updates a sent message's status (sent / failed / delivered). */
public class SentReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Uri row = intent.getData();
        if (row == null) return;
        if (SmsSender.ACTION_SENT.equals(intent.getAction())) {
            if (getResultCode() == Activity.RESULT_OK) {
                if (intent.getBooleanExtra("last", true)) SmsStore.setType(c, row, SmsStore.TYPE_SENT);
            } else {
                SmsStore.setType(c, row, SmsStore.TYPE_FAILED);
            }
        } else if (SmsSender.ACTION_DELIVERED.equals(intent.getAction())) {
            ContentValues v = new ContentValues();
            v.put("status", 0); // delivered
            try { c.getContentResolver().update(row, v, null, null); } catch (Exception ignored) { }
        }
    }
}
