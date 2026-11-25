package in.appbucket.spendsense;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;

/** "Reply with message" when declining a call. Required for the default SMS app role. */
public class HeadlessSmsSendService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "android.intent.action.RESPOND_VIA_MESSAGE".equals(intent.getAction())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            Uri data = intent.getData();
            if (text != null && data != null) {
                for (String to : ConversationActivity.recipients(data)) SmsSender.send(this, to, text);
            }
        }
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
