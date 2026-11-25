package in.appbucket.spendsense;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.text.InputType;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** One conversation: chat bubbles, composer, and SpendSense transaction chips on bank SMS. */
public class ConversationActivity extends AppCompatActivity {

    static final String EXTRA_THREAD = "thread_id";
    static final String EXTRA_ADDRESS = "address";
    private static final int REQ_CONTACT = 7;

    /** Thread currently on screen, so incoming messages for it don't also notify. */
    static volatile long openThread = -1;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Object> items = new ArrayList<>(); // String day header or SmsStore.Msg
    private final LruCache<Long, Bitmap> images = new LruCache<>(12);
    private long threadId;
    private String address = "";
    private TextView titleView, subView;
    private EditText to, input;
    private LinearLayout toRow;
    private ListView list;
    private Adapter adapter;
    private ExpenseDb db;
    private ContentObserver observer;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        db = ExpenseDb.get(this);
        getWindow().setStatusBarColor(Ui.SURFACE);
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        readIntent(getIntent());
        buildUi();
        observer = new ContentObserver(ui) {
            @Override public void onChange(boolean self) { load(); }
        };
        getContentResolver().registerContentObserver(Uri.parse("content://sms"), true, observer);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readIntent(intent);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        openThread = threadId > 0 ? threadId : -1;
        if (threadId > 0) {
            SmsStore.markRead(this, threadId);
            MessageNotifier.cancel(this, threadId);
        }
        load();
    }

    @Override
    protected void onPause() {
        super.onPause();
        openThread = -1;
    }

    @Override
    protected void onDestroy() {
        if (observer != null) getContentResolver().unregisterContentObserver(observer);
        super.onDestroy();
    }

    /** Recipients from sms:/smsto:/mms:/mmsto: URIs (comma or semicolon separated). */
    static List<String> recipients(Uri data) {
        List<String> out = new ArrayList<>();
        if (data == null) return out;
        String part = data.getSchemeSpecificPart();
        if (part == null) return out;
        int q = part.indexOf('?');
        if (q >= 0) part = part.substring(0, q);
        part = part.replace("//", "");
        for (String r : part.split("[,;]")) if (!r.trim().isEmpty()) out.add(r.trim());
        return out;
    }

    private void readIntent(Intent i) {
        threadId = i.getLongExtra(EXTRA_THREAD, 0);
        address = i.getStringExtra(EXTRA_ADDRESS);
        if (address == null) address = "";
        String body = null;
        Uri data = i.getData();
        if (data != null && data.getScheme() != null && data.getScheme().startsWith("sms")
                || data != null && data.getScheme() != null && data.getScheme().startsWith("mms")) {
            List<String> r = recipients(data);
            if (!r.isEmpty()) address = r.get(0);
            String q = data.getSchemeSpecificPart();
            if (q != null && q.contains("body=")) body = Uri.decode(q.substring(q.indexOf("body=") + 5));
        }
        if (i.getStringExtra("sms_body") != null) body = i.getStringExtra("sms_body");
        if (body == null && i.getStringExtra(Intent.EXTRA_TEXT) != null && data != null) body = i.getStringExtra(Intent.EXTRA_TEXT);
        if (threadId == 0 && !address.isEmpty()) threadId = SmsStore.threadFor(this, address);
        if (threadId > 0 && address.isEmpty()) address = SmsStore.addressOfThread(this, threadId);
        pendingBody = body;
    }

    private String pendingBody;

    private void buildUi() {
        LinearLayout root = Ui.v(this);
        root.setBackgroundColor(Ui.BG);

        // Top bar
        LinearLayout bar = Ui.h(this);
        bar.setBackgroundColor(Ui.SURFACE);
        bar.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        TextView back = Ui.text(this, "←", 24, Ui.INK, false);
        back.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        bar.addView(avatar(this, address, 40));
        LinearLayout names = Ui.v(this);
        names.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        titleView = Ui.oneLine(Ui.text(this, "", 17, Ui.INK, true));
        subView = Ui.oneLine(Ui.text(this, "", 12, Ui.MUTED, false));
        names.addView(titleView);
        names.addView(subView);
        bar.addView(names, Ui.weight(1));
        if (!address.isEmpty() && !SmsStore.isBusiness(address)) {
            TextView call = Ui.text(this, "📞", 20, Ui.INK, false);
            call.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
            call.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + address))));
            bar.addView(call);
        }
        root.addView(bar);

        // Recipient row for a new message
        toRow = Ui.h(this);
        toRow.setBackgroundColor(Ui.SURFACE);
        toRow.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 10), Ui.dp(this, 10));
        toRow.addView(Ui.text(this, "To", 15, Ui.MUTED, true));
        to = new EditText(this);
        to.setHint("Name or number");
        to.setInputType(InputType.TYPE_CLASS_PHONE);
        to.setBackground(Ui.round(this, 0xFFF3F4F6, 12));
        to.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        LinearLayout.LayoutParams tl = Ui.weight(1);
        tl.leftMargin = Ui.dp(this, 12);
        toRow.addView(to, tl);
        TextView pick = Ui.text(this, "👤", 20, Ui.INK, false);
        pick.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        pick.setOnClickListener(v -> {
            Intent p = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
            try { startActivityForResult(p, REQ_CONTACT); } catch (Exception ignored) { }
        });
        toRow.addView(pick);
        root.addView(toRow);

        list = new ListView(this);
        list.setDivider(null);
        list.setStackFromBottom(true);
        list.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_NORMAL);
        list.setSelector(android.R.color.transparent);
        list.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        list.setClipToPadding(false);
        adapter = new Adapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        // Composer
        LinearLayout comp = Ui.h(this);
        comp.setBackgroundColor(Ui.SURFACE);
        comp.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        input = new EditText(this);
        input.setHint("Text message");
        input.setMaxLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackground(Ui.round(this, 0xFFF3F4F6, 22));
        input.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        if (pendingBody != null) {
            input.setText(pendingBody);
            pendingBody = null;
        }
        comp.addView(input, Ui.weight(1));
        FloatingActionButton send = new FloatingActionButton(this);
        send.setSize(FloatingActionButton.SIZE_MINI);
        send.setImageResource(R.drawable.ic_send);
        send.setBackgroundTintList(ColorStateList.valueOf(Ui.PRIMARY));
        send.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        send.setContentDescription("Send");
        send.setOnClickListener(v -> sendNow());
        LinearLayout.LayoutParams sl = Ui.lp(-2, -2);
        sl.leftMargin = Ui.dp(this, 8);
        comp.addView(send, sl);
        boolean business = !address.isEmpty() && SmsStore.isBusiness(address);
        if (business) {
            TextView note = Ui.text(this, "This sender can't receive replies", 13, Ui.MUTED, false);
            note.setGravity(Gravity.CENTER);
            note.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
            note.setBackgroundColor(Ui.SURFACE);
            root.addView(note, Ui.lp(-1, -2));
        } else {
            root.addView(comp);
        }

        setContentView(root);
        updateHeader();
    }

    private void updateHeader() {
        boolean isNew = address.isEmpty();
        toRow.setVisibility(isNew ? View.VISIBLE : View.GONE);
        titleView.setText(isNew ? "New message" : SmsStore.displayName(this, address));
        String name = isNew ? "" : SmsStore.displayName(this, address);
        subView.setText(!isNew && !name.equals(address) ? address : (SmsStore.isDefault(this) ? "" : "SMS"));
        subView.setVisibility(subView.getText().length() == 0 ? View.GONE : View.VISIBLE);
    }

    private void load() {
        if (threadId <= 0) {
            items.clear();
            if (adapter != null) adapter.notifyDataSetChanged();
            return;
        }
        final long tid = threadId;
        new Thread(() -> {
            List<SmsStore.Msg> msgs = SmsStore.messages(this, tid);
            List<Object> rows = new ArrayList<>();
            Calendar last = null;
            for (SmsStore.Msg m : msgs) {
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(m.date);
                if (last == null || !Format.sameDay(last, c)) {
                    rows.add(Format.dayHeader(m.date));
                    last = c;
                }
                rows.add(m);
            }
            ui.post(() -> {
                items.clear();
                items.addAll(rows);
                adapter.notifyDataSetChanged();
                list.setSelection(items.size() - 1);
            });
        }).start();
    }

    private void sendNow() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        if (address.isEmpty()) {
            String num = to.getText().toString().trim();
            if (num.isEmpty()) {
                to.setError("Enter a number");
                return;
            }
            address = num;
            threadId = SmsStore.threadFor(this, address);
            openThread = threadId;
            buildUi();
        }
        if (!SmsSender.send(this, address, text)) {
            Toast.makeText(this, "Couldn't send. Check your SIM and signal.", Toast.LENGTH_LONG).show();
            return;
        }
        input.setText("");
        if (threadId <= 0) threadId = SmsStore.threadFor(this, address);
        ui.postDelayed(this::load, 400);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_CONTACT || res != RESULT_OK || data == null || data.getData() == null) return;
        try (Cursor c = getContentResolver().query(data.getData(),
                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null)) {
            if (c != null && c.moveToFirst()) to.setText(c.getString(0));
        } catch (Exception ignored) { }
    }

    // ---------------- Bubbles ----------------

    private void messageMenu(SmsStore.Msg m) {
        List<String> opts = new ArrayList<>();
        opts.add("Copy text");
        SmsParser.Result r = m.type == SmsStore.TYPE_INBOX && !m.mms ? SmsParser.parse(m.address, m.body) : null;
        boolean added = r != null && db.hasKey(SmsImporter.keyFor(m.address, m.body));
        if (r != null && !added) opts.add("Add to SpendSense");
        if (m.type == SmsStore.TYPE_FAILED) opts.add("Try sending again");
        if (SmsStore.isDefault(this)) opts.add("Delete message");
        String[] arr = opts.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setItems(arr, (d, w) -> {
                    String o = arr[w];
                    if (o.startsWith("Copy")) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("message", m.body));
                        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
                    } else if (o.startsWith("Add")) {
                        addExpense(m);
                    } else if (o.startsWith("Try")) {
                        SmsStore.deleteMessage(this, m);
                        SmsSender.send(this, address, m.body);
                        load();
                    } else if (o.startsWith("Delete")) {
                        SmsStore.deleteMessage(this, m);
                        load();
                    }
                })
                .show();
    }

    private void addExpense(SmsStore.Msg m) {
        SmsParser.Result r = SmsParser.parse(m.address, m.body);
        if (r == null) return;
        Expense e = new Expense();
        e.smsKey = SmsImporter.keyFor(m.address, m.body);
        e.ts = m.date;
        e.amount = r.amount;
        e.debit = r.debit;
        e.merchant = r.merchant;
        e.category = r.category;
        e.mode = r.mode;
        e.bank = r.bank;
        e.account = r.account;
        e.smsBody = m.body;
        db.insertIfNew(e);
        Toast.makeText(this, "Added to SpendSense", Toast.LENGTH_SHORT).show();
        adapter.notifyDataSetChanged();
    }

    private View bubble(SmsStore.Msg m) {
        boolean mine = m.type != SmsStore.TYPE_INBOX;
        LinearLayout wrap = Ui.v(this);
        wrap.setGravity(mine ? Gravity.END : Gravity.START);
        wrap.setPadding(mine ? Ui.dp(this, 56) : 0, Ui.dp(this, 3), mine ? 0 : Ui.dp(this, 56), Ui.dp(this, 3));

        LinearLayout b = Ui.v(this);
        b.setPadding(Ui.dp(this, 14), Ui.dp(this, 9), Ui.dp(this, 14), Ui.dp(this, 9));
        b.setBackground(Ui.round(this, mine ? (m.type == SmsStore.TYPE_FAILED ? 0xFFFEE2E2 : Ui.PRIMARY) : Ui.SURFACE, 18));

        if (m.mms && m.imagePartId > 0) {
            ImageView iv = new ImageView(this);
            iv.setAdjustViewBounds(true);
            iv.setMaxHeight(Ui.dp(this, 260));
            loadImage(iv, m.imagePartId);
            b.addView(iv, Ui.lp(Ui.dp(this, 200), -2));
        }
        if (!m.body.isEmpty() || !m.mms) {
            TextView t = Ui.text(this, m.body.isEmpty() ? "(empty message)" : m.body, 15,
                    mine && m.type != SmsStore.TYPE_FAILED ? Color.WHITE : Ui.INK, false);
            b.addView(t);
        }
        wrap.addView(b, Ui.lp(-2, -2));

        String status = Format.time(m.date);
        if (mine) {
            if (m.type == SmsStore.TYPE_OUTBOX || m.type == SmsStore.TYPE_QUEUED) status += " · Sending…";
            else if (m.type == SmsStore.TYPE_FAILED) status += " · Not sent, tap to retry";
            else if (m.status == 0) status += " · Delivered";
            else status += " · Sent";
        }
        TextView st = Ui.text(this, status, 11, m.type == SmsStore.TYPE_FAILED ? Ui.RED : Ui.MUTED, false);
        st.setPadding(Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6), 0);
        wrap.addView(st);

        // SpendSense chip under bank transaction SMS
        if (!mine && !m.mms && SmsStore.isBusiness(m.address)) {
            SmsParser.Result r = SmsParser.parse(m.address, m.body);
            if (r != null) {
                boolean added = db.hasKey(SmsImporter.keyFor(m.address, m.body));
                String label = Categorizer.emoji(r.category) + "  " + (r.debit ? "Spent " : "Received ")
                        + Format.money(r.amount) + " · " + r.category + (added ? "  ✓ in SpendSense" : "  + Add");
                TextView chip = Ui.text(this, label, 12, Ui.PRIMARY, true);
                chip.setBackground(Ui.round(this, Ui.PRIMARY_SOFT, 12));
                chip.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
                if (!added) chip.setOnClickListener(v -> addExpense(m));
                LinearLayout.LayoutParams cl = Ui.lp(-2, -2);
                cl.topMargin = Ui.dp(this, 4);
                wrap.addView(chip, cl);
            }
        }

        b.setOnLongClickListener(v -> { messageMenu(m); return true; });
        b.setOnClickListener(v -> {
            if (m.type == SmsStore.TYPE_FAILED) messageMenu(m);
        });
        return wrap;
    }

    private void loadImage(ImageView iv, long partId) {
        Bitmap cached = images.get(partId);
        if (cached != null) {
            iv.setImageBitmap(cached);
            return;
        }
        iv.setTag(partId);
        new Thread(() -> {
            Bitmap bm = null;
            Uri u = Uri.parse("content://mms/part/" + partId);
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                try (InputStream in = getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, o); }
                int sample = 1;
                while (o.outWidth / sample > 800 || o.outHeight / sample > 800) sample *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = sample;
                try (InputStream in = getContentResolver().openInputStream(u)) { bm = BitmapFactory.decodeStream(in, null, o2); }
            } catch (Exception ignored) { }
            final Bitmap res = bm;
            if (res != null) images.put(partId, res);
            ui.post(() -> {
                if (res != null && Long.valueOf(partId).equals(iv.getTag())) iv.setImageBitmap(res);
            });
        }).start();
    }

    /** Round avatar: initials for people, bank icon for business senders. */
    static TextView avatar(android.content.Context c, String address, int sizeDp) {
        String name = SmsStore.displayName(c, address == null ? "" : address);
        String label;
        if (address == null || address.isEmpty()) label = "+";
        else if (SmsStore.isBusiness(address)) label = "🏦";
        else if (!TextUtils.isEmpty(name) && Character.isLetter(name.charAt(0))) label = name.substring(0, 1).toUpperCase();
        else label = "👤";
        int[] palette = {0xFF6366F1, 0xFF0EA5E9, 0xFF10B981, 0xFFF59E0B, 0xFFEC4899, 0xFF8B5CF6, 0xFF14B8A6, 0xFFEF4444};
        int col = palette[Math.abs((address == null ? "" : address).hashCode()) % palette.length];
        TextView t = Ui.text(c, label, sizeDp * 0.4f, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(SmsStore.isBusiness(address) ? 0xFFEEF0FF : col);
        t.setBackground(g);
        t.setLayoutParams(Ui.lp(Ui.dp(c, sizeDp), Ui.dp(c, sizeDp)));
        return t;
    }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int i) { return items.get(i) instanceof String ? 0 : 1; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Object o = items.get(i);
            if (o instanceof String) {
                TextView t = Ui.text(ConversationActivity.this, (String) o, 12, Ui.MUTED, true);
                t.setGravity(Gravity.CENTER);
                t.setPadding(0, Ui.dp(ConversationActivity.this, 14), 0, Ui.dp(ConversationActivity.this, 6));
                return t;
            }
            return bubble((SmsStore.Msg) o);
        }
    }
}
