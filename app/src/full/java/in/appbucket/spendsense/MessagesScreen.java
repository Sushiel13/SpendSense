package in.appbucket.spendsense;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.provider.Settings;
import android.provider.Telephony;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** The Messages tab: all SMS conversations, like a normal messaging app. */
final class MessagesScreen {
    static final int REQ_DEFAULT_SMS = 4;
    static final int REQ_CONTACTS = 5;

    private static List<SmsStore.Conv> cache; // shown instantly while reloading

    private final MainActivity a;
    private final List<SmsStore.Conv> shown = new ArrayList<>();
    private List<SmsStore.Conv> all = new ArrayList<>();
    private String query = "";
    private Adapter adapter;
    private TextView empty;

    MessagesScreen(MainActivity a) {
        this.a = a;
    }

    View build() {
        LinearLayout col = Ui.v(a);
        col.setPadding(Ui.dp(a, 16), Ui.dp(a, 6), Ui.dp(a, 16), 0);

        if (!AutoSms.granted(a)) {
            col.addView(permissionCard());
            return col;
        }
        if (!SmsStore.isDefault(a)) col.addView(defaultCard());

        EditText search = new EditText(a);
        search.setHint("Search conversations");
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setBackground(Ui.round(a, Ui.SURFACE, 14));
        search.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 12));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                filter();
            }
        });
        LinearLayout.LayoutParams sl = Ui.lp(-1, -2);
        sl.topMargin = Ui.dp(a, 8);
        sl.bottomMargin = Ui.dp(a, 6);
        col.addView(search, sl);

        empty = Ui.text(a, "Loading messages…", 14, Ui.MUTED, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, Ui.dp(a, 40), 0, 0);
        col.addView(empty, Ui.lp(-1, -2));

        ListView list = new ListView(a);
        list.setDivider(null);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, Ui.dp(a, 96));
        list.setSelector(android.R.color.transparent);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> open(shown.get(pos)));
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            convMenu(shown.get(pos));
            return true;
        });
        col.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        if (!SmsStore.canReadContacts(a) && !a.prefs.getBoolean("askedContacts", false)) {
            a.prefs.edit().putBoolean("askedContacts", true).apply();
            a.requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
        }

        if (cache != null) {
            all = cache;
            filter();
        }
        new Thread(() -> {
            List<SmsStore.Conv> fresh = SmsStore.conversations(a);
            a.ui.post(() -> {
                cache = fresh;
                all = fresh;
                filter();
            });
        }).start();
        return col;
    }

    private void filter() {
        shown.clear();
        for (SmsStore.Conv c : all) {
            if (query.isEmpty() || c.name.toLowerCase(Locale.ROOT).contains(query)
                    || c.address.toLowerCase(Locale.ROOT).contains(query)
                    || c.snippet.toLowerCase(Locale.ROOT).contains(query)) shown.add(c);
        }
        if (empty != null) {
            empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
            empty.setText(all.isEmpty() ? "No messages yet. Tap the button below to start one." : "No conversations match");
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void open(SmsStore.Conv c) {
        a.startActivity(new Intent(a, ConversationActivity.class)
                .putExtra(ConversationActivity.EXTRA_THREAD, c.id)
                .putExtra(ConversationActivity.EXTRA_ADDRESS, c.address));
    }

    private void convMenu(SmsStore.Conv c) {
        boolean def = SmsStore.isDefault(a);
        List<String> opts = new ArrayList<>();
        opts.add("Open");
        if (def && c.unread) opts.add("Mark as read");
        if (def) opts.add("Delete conversation");
        String[] arr = opts.toArray(new String[0]);
        new MaterialAlertDialogBuilder(a)
                .setTitle(c.name)
                .setItems(arr, (d, w) -> {
                    if (arr[w].equals("Open")) open(c);
                    else if (arr[w].equals("Mark as read")) {
                        SmsStore.markRead(a, c.id);
                        a.refresh();
                    } else {
                        new MaterialAlertDialogBuilder(a)
                                .setTitle("Delete this conversation?")
                                .setMessage("All messages with " + c.name + " will be deleted from your phone.")
                                .setPositiveButton("Delete", (d2, w2) -> {
                                    SmsStore.deleteConversation(a, c.id);
                                    cache = null;
                                    a.refresh();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                })
                .show();
    }

    private View permissionCard() {
        LinearLayout c = Ui.card(a);
        c.addView(Ui.text(a, "Allow SMS access", 16, Ui.INK, true));
        TextView t = Ui.text(a, "SpendSense needs SMS access to show your messages here.", 14, Ui.MUTED, false);
        t.setPadding(0, Ui.dp(a, 4), 0, 0);
        c.addView(t);
        View b = Ui.button(a, "Allow", Ui.PRIMARY, Color.WHITE);
        b.setOnClickListener(v -> a.requestPermissions(AutoSms.PERMISSIONS, 1));
        LinearLayout.LayoutParams lp = Ui.lp(-1, Ui.dp(a, 48));
        lp.topMargin = Ui.dp(a, 14);
        c.addView(b, lp);
        return c;
    }

    private View defaultCard() {
        LinearLayout c = Ui.v(a);
        c.setBackground(Ui.gradient(a, Ui.PRIMARY, Ui.PRIMARY_2, 18));
        c.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 14));
        LinearLayout.LayoutParams cl = Ui.lp(-1, -2);
        cl.topMargin = Ui.dp(a, 8);
        c.setLayoutParams(cl);
        c.addView(Ui.text(a, "Use SpendSense for your messages", 16, Color.WHITE, true));
        TextView t = Ui.text(a, "Send and reply here, get notifications, and every bank SMS becomes a transaction "
                + "the moment it arrives.", 13, 0xE6FFFFFF, false);
        t.setPadding(0, Ui.dp(a, 4), 0, 0);
        c.addView(t);
        View b = Ui.button(a, "Set as default SMS app", Color.WHITE, Ui.PRIMARY);
        b.setOnClickListener(v -> confirmDefault(a));
        LinearLayout.LayoutParams lp = Ui.lp(-1, Ui.dp(a, 46));
        lp.topMargin = Ui.dp(a, 12);
        c.addView(b, lp);
        return c;
    }

    /** Explains the trade-offs, then asks Android to make SpendSense the default SMS app. */
    static void confirmDefault(MainActivity a) {
        new MaterialAlertDialogBuilder(a)
                .setTitle("Make SpendSense your SMS app?")
                .setMessage("SpendSense will send and receive your text messages.\n\n"
                        + "• Chat features of Google Messages (RCS: typing dots, read receipts, Wi-Fi chats) "
                        + "stop while SpendSense is your SMS app. Those chats go as normal SMS.\n"
                        + "• Picture messages (MMS) can't be received in SpendSense.\n\n"
                        + "You can switch back any time in Settings → Apps → Default apps → SMS app.")
                .setPositiveButton("Continue", (d, w) -> requestDefault(a))
                .setNegativeButton("Not now", null)
                .show();
    }

    @SuppressWarnings("deprecation")
    static void requestDefault(MainActivity a) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager rm = a.getSystemService(RoleManager.class);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS) && !rm.isRoleHeld(RoleManager.ROLE_SMS)) {
                    a.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_SMS), REQ_DEFAULT_SMS);
                    return;
                }
            }
            Intent i = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                    .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, a.getPackageName());
            a.startActivityForResult(i, REQ_DEFAULT_SMS);
        } catch (Exception e) {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
        }
    }

    static void openDefaultAppsSettings(MainActivity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
        } catch (Exception e) {
            a.startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return shown.get(i).id; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            SmsStore.Conv c = shown.get(i);
            LinearLayout row = Ui.h(a);
            row.setPadding(Ui.dp(a, 4), Ui.dp(a, 10), Ui.dp(a, 4), Ui.dp(a, 10));
            row.addView(ConversationActivity.avatar(a, c.address, 48));

            LinearLayout mid = Ui.v(a);
            mid.setPadding(Ui.dp(a, 14), 0, Ui.dp(a, 8), 0);
            mid.addView(Ui.oneLine(Ui.text(a, c.name, 16, Ui.INK, c.unread)));
            TextView sn = Ui.text(a, c.snippet.replace('\n', ' '), 14, c.unread ? Ui.INK : Ui.MUTED, false);
            sn.setMaxLines(2);
            sn.setEllipsize(android.text.TextUtils.TruncateAt.END);
            mid.addView(sn);
            row.addView(mid, Ui.weight(1));

            LinearLayout right = Ui.v(a);
            right.setGravity(Gravity.END);
            right.addView(Ui.text(a, when(c.date), 12, c.unread ? Ui.PRIMARY : Ui.MUTED, c.unread));
            if (c.unread) {
                View dot = new View(a);
                dot.setBackground(Ui.round(a, Ui.PRIMARY, 5));
                LinearLayout.LayoutParams dl = Ui.lp(Ui.dp(a, 10), Ui.dp(a, 10));
                dl.topMargin = Ui.dp(a, 8);
                dl.gravity = Gravity.END;
                right.addView(dot, dl);
            }
            row.addView(right);
            return row;
        }
    }

    /** "4:05 PM" today, "Mon" this week, "12 Nov" this year, else "12/11/25". */
    private static String when(long ts) {
        Calendar now = Calendar.getInstance();
        Calendar d = Calendar.getInstance();
        d.setTimeInMillis(ts);
        if (Format.sameDay(now, d)) return Format.time(ts);
        long days = (now.getTimeInMillis() - ts) / (24L * 60 * 60 * 1000);
        java.util.Date date = new java.util.Date(ts);
        if (days < 6) return new java.text.SimpleDateFormat("EEE", Locale.ENGLISH).format(date);
        if (now.get(Calendar.YEAR) == d.get(Calendar.YEAR))
            return new java.text.SimpleDateFormat("d MMM", Locale.ENGLISH).format(date);
        return new java.text.SimpleDateFormat("d/M/yy", Locale.ENGLISH).format(date);
    }
}
