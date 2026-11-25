package in.appbucket.spendsense;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    static final int ID_HOME = 1, ID_TX = 2, ID_INSIGHTS = 3, ID_SETTINGS = 4, ID_MESSAGES = 5;
    private static final int REQ_SMS = 1;
    private static final int REQ_EXPORT = 2;

    final Handler ui = new Handler(Looper.getMainLooper());
    final Calendar month = Calendar.getInstance();
    ExpenseDb db;
    SharedPreferences prefs;

    // Transactions screen filters (kept while switching tabs)
    int txType = 1;          // 0 all, 1 expenses, 2 income
    String txCategory = null;
    String txQuery = "";

    // Inbox scan state (full build)
    volatile boolean scanning;
    volatile int scanDone, scanTotal, scanAdded;

    private FrameLayout content;
    private FloatingActionButton fab;
    private BottomNavigationView nav;
    private View monthBar;
    private TextView monthLabel, title;
    private int current = ID_HOME;
    private boolean shellShown;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        db = ExpenseDb.get(this);
        prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        month.set(Calendar.DAY_OF_MONTH, 1);
        getWindow().setStatusBarColor(Ui.BG);
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        if (BuildConfig.SMS_AUTO && !hasSms() && !prefs.getBoolean("skipOnboarding", false)) showOnboarding();
        else showShell();
        handleShare(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!shellShown) return;
        if (hasSms() && !scanning) {
            long last = prefs.getLong("lastScan", 0);
            // First time: read the whole inbox. Later: catch up on anything missed.
            startScan(last == 0 ? 0 : last - 24L * 60 * 60 * 1000, last == 0);
        }
        refresh();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShare(intent);
    }

    boolean hasSms() {
        return BuildConfig.SMS_AUTO && AutoSms.granted(this);
    }

    // ---------------- Onboarding (automatic build) ----------------

    private void showOnboarding() {
        shellShown = false;
        getWindow().setStatusBarColor(Ui.PRIMARY);
        if (Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(0);

        LinearLayout root = Ui.v(this);
        root.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{Ui.PRIMARY, Ui.PRIMARY_2}));
        root.setPadding(Ui.dp(this, 28), Ui.dp(this, 48), Ui.dp(this, 28), Ui.dp(this, 28));

        TextView logo = Ui.text(this, "₹", 40, Ui.PRIMARY, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(Ui.round(this, Color.WHITE, 24));
        root.addView(logo, Ui.lp(Ui.dp(this, 76), Ui.dp(this, 76)));

        TextView t = Ui.text(this, "Your spending,\ntracked automatically", 28, Color.WHITE, true);
        t.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 8));
        root.addView(t);
        root.addView(Ui.text(this, "SpendSense reads the transaction SMS your bank, UPI apps and cards send you, "
                + "and turns them into a clear monthly picture.", 15, 0xDDFFFFFF, false));

        String[][] points = {
                {"⚡", "Every transaction, automatically", "Reads your whole SMS history, then adds new spends the moment they arrive"},
                {"📊", "See where it goes", "Categories, monthly trends, top merchants and a budget"},
                {"🔒", "Stays on your phone", "No internet permission. OTPs and personal chats are ignored"},
        };
        LinearLayout list = Ui.v(this);
        list.setPadding(0, Ui.dp(this, 24), 0, 0);
        for (String[] p : points) {
            LinearLayout row = Ui.h(this);
            row.setGravity(Gravity.TOP);
            row.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            TextView ic = Ui.text(this, p[0], 20, Color.WHITE, false);
            ic.setGravity(Gravity.CENTER);
            ic.setBackground(Ui.round(this, 0x33FFFFFF, 12));
            row.addView(ic, Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40)));
            LinearLayout txt = Ui.v(this);
            txt.setPadding(Ui.dp(this, 14), 0, 0, 0);
            txt.addView(Ui.text(this, p[1], 16, Color.WHITE, true));
            txt.addView(Ui.text(this, p[2], 13, 0xCCFFFFFF, false));
            row.addView(txt, Ui.weight(1));
            list.addView(row);
        }
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        View allow = Ui.button(this, "Allow SMS access", Color.WHITE, Ui.PRIMARY);
        allow.setOnClickListener(v -> {
            List<String> perms = new ArrayList<>(java.util.Arrays.asList(AutoSms.PERMISSIONS));
            if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS);
            requestPermissions(perms.toArray(new String[0]), REQ_SMS);
        });
        root.addView(allow, Ui.lp(-1, Ui.dp(this, 54)));
        TextView later = Ui.text(this, "Not now, I'll add spends myself", 14, Color.WHITE, false);
        later.setGravity(Gravity.CENTER);
        later.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 4));
        later.setOnClickListener(v -> {
            prefs.edit().putBoolean("skipOnboarding", true).apply();
            showShell();
        });
        root.addView(later, Ui.lp(-1, -2));
        setContentView(root);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != REQ_SMS) {
            AutoSms.onContactsResult();
            refresh();
            return;
        }
        if (!hasSms()) {
            Toast.makeText(this, "Without SMS access you can still add spends by hand", Toast.LENGTH_LONG).show();
            prefs.edit().putBoolean("skipOnboarding", true).apply();
        }
        showShell(); // onResume then starts the full inbox scan
    }

    // ---------------- Shell: top bar, content, bottom nav ----------------

    private void showShell() {
        shellShown = true;
        getWindow().setStatusBarColor(Ui.BG);
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        LinearLayout root = Ui.v(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout top = Ui.h(this);
        top.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 6));
        title = Ui.text(this, "SpendSense", 22, Ui.INK, true);
        top.addView(title, Ui.weight(1));

        LinearLayout mb = Ui.h(this);
        mb.setBackground(Ui.round(this, Ui.SURFACE, 20));
        mb.setPadding(Ui.dp(this, 4), Ui.dp(this, 2), Ui.dp(this, 4), Ui.dp(this, 2));
        TextView prev = Ui.text(this, "‹", 22, Ui.PRIMARY, true);
        TextView next = Ui.text(this, "›", 22, Ui.PRIMARY, true);
        prev.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 6), 0);
        next.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 10), 0);
        monthLabel = Ui.text(this, "", 14, Ui.INK, true);
        prev.setOnClickListener(v -> { month.add(Calendar.MONTH, -1); refresh(); });
        next.setOnClickListener(v -> {
            if (monthEnd() <= System.currentTimeMillis()) { month.add(Calendar.MONTH, 1); refresh(); }
        });
        mb.addView(prev);
        mb.addView(monthLabel);
        mb.addView(next);
        monthBar = mb;
        top.addView(mb);
        root.addView(top);

        FrameLayout body = new FrameLayout(this);
        content = new FrameLayout(this);
        body.addView(content, new FrameLayout.LayoutParams(-1, -1));

        FloatingActionButton fab = new FloatingActionButton(this);
        fab.setImageResource(R.drawable.ic_add);
        fab.setBackgroundTintList(ColorStateList.valueOf(Ui.PRIMARY));
        fab.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        fab.setContentDescription("Add transaction");
        fab.setOnClickListener(v -> {
            if (current == ID_MESSAGES) AutoSms.newMessage(this);
            else addMenu();
        });
        this.fab = fab;
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        fp.setMargins(0, 0, Ui.dp(this, 20), Ui.dp(this, 20));
        body.addView(fab, fp);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        nav = new BottomNavigationView(this);
        nav.setBackgroundColor(Ui.SURFACE);
        nav.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        nav.getMenu().add(0, ID_HOME, 0, "Home").setIcon(R.drawable.ic_home);
        if (AutoSms.MESSAGING) nav.getMenu().add(0, ID_MESSAGES, 1, "Messages").setIcon(R.drawable.ic_message);
        nav.getMenu().add(0, ID_TX, 2, "Spends").setIcon(R.drawable.ic_list);
        nav.getMenu().add(0, ID_INSIGHTS, 3, "Insights").setIcon(R.drawable.ic_chart);
        nav.getMenu().add(0, ID_SETTINGS, 4, "Settings").setIcon(R.drawable.ic_settings);
        nav.setOnItemSelectedListener(item -> {
            current = item.getItemId();
            refresh();
            return true;
        });
        root.addView(nav, Ui.lp(-1, -2));
        setContentView(root);
        nav.setSelectedItemId(current);
        refresh();
    }

    void go(int screen) {
        if (nav != null) nav.setSelectedItemId(screen);
    }

    /** Rebuilds the current screen with fresh data. */
    void refresh() {
        if (!shellShown || content == null) return;
        monthLabel.setText(Format.shortMonth(monthStart()) + " " + month.get(Calendar.YEAR));
        monthBar.setVisibility(current == ID_SETTINGS || current == ID_MESSAGES ? View.GONE : View.VISIBLE);
        if (fab != null) {
            fab.setImageResource(current == ID_MESSAGES ? R.drawable.ic_message : R.drawable.ic_add);
            fab.setContentDescription(current == ID_MESSAGES ? "New message" : "Add transaction");
        }
        String[] titles = {"", "SpendSense", "Transactions", "Insights", "Settings", "Messages"};
        title.setText(titles[current]);
        View v;
        if (current == ID_MESSAGES && AutoSms.MESSAGING) v = AutoSms.messagesScreen(this);
        else if (current == ID_TX) v = new TransactionsScreen(this).build();
        else if (current == ID_INSIGHTS) v = new InsightsScreen(this).build();
        else if (current == ID_SETTINGS) v = new SettingsScreen(this).build();
        else v = new HomeScreen(this).build();
        content.removeAllViews();
        content.addView(v, new FrameLayout.LayoutParams(-1, -1));
    }

    long monthStart() {
        Calendar c = (Calendar) month.clone();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    long monthEnd() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(monthStart());
        c.add(Calendar.MONTH, 1);
        return c.getTimeInMillis();
    }

    boolean isCurrentMonth() {
        long now = System.currentTimeMillis();
        return now >= monthStart() && now < monthEnd();
    }

    // ---------------- Automatic inbox scan ----------------

    void startScan(long since, boolean announce) {
        if (!hasSms() || scanning) return;
        scanning = true;
        scanDone = 0;
        scanTotal = 0;
        scanAdded = 0;
        if (announce) refresh();
        new Thread(() -> {
            int added = AutoSms.scanInbox(this, since, (done, total, add) -> {
                scanDone = done;
                scanTotal = total;
                scanAdded = add;
                if (announce) ui.post(this::refresh);
            });
            prefs.edit().putLong("lastScan", System.currentTimeMillis()).apply();
            ui.post(() -> {
                scanning = false;
                refresh();
                if (announce || added > 0) {
                    Toast.makeText(this, added == 0 ? "You're up to date"
                            : added + " transaction" + (added == 1 ? "" : "s") + " added from SMS", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    // ---------------- Adding: manual, share, paste ----------------

    private void addMenu() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout col = Ui.v(this);
        col.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 24));
        col.addView(Ui.text(this, "Add a transaction", 18, Ui.INK, true));
        col.addView(sheetOption("✍️", "Enter it yourself", "Cash spends or anything without an SMS", v -> {
            sheet.dismiss();
            EditSheet.show(this, null);
        }));
        col.addView(sheetOption("📋", "Paste bank SMS", "Paste one or more messages you copied", v -> {
            sheet.dismiss();
            pasteDialog();
        }));
        if (hasSms()) {
            col.addView(sheetOption("🔄", "Check SMS again", "Look through your whole inbox for missed transactions", v -> {
                sheet.dismiss();
                startScan(0, true);
            }));
        }
        sheet.setContentView(col);
        sheet.show();
    }

    private View sheetOption(String icon, String title, String sub, View.OnClickListener l) {
        LinearLayout row = Ui.h(this);
        row.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 6));
        TextView ic = Ui.text(this, icon, 20, Ui.INK, false);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(Ui.round(this, Ui.PRIMARY_SOFT, 14));
        row.addView(ic, Ui.lp(Ui.dp(this, 46), Ui.dp(this, 46)));
        LinearLayout t = Ui.v(this);
        t.setPadding(Ui.dp(this, 14), 0, 0, 0);
        t.addView(Ui.text(this, title, 16, Ui.INK, true));
        t.addView(Ui.text(this, sub, 13, Ui.MUTED, false));
        row.addView(t, Ui.weight(1));
        row.setOnClickListener(l);
        return row;
    }

    /** Bank SMS shared from the Messages app (long-press -> Share -> SpendSense). */
    private void handleShare(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        CharSequence cs = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        intent.setAction(Intent.ACTION_MAIN); // don't re-import on rotation
        if (cs == null || cs.toString().trim().isEmpty()) return;
        if (!shellShown) showShell();
        importText(cs.toString(), true);
    }

    /** Imports one or more pasted/shared SMS. Separate messages with a blank line. */
    void importText(String text, boolean openSingle) {
        String[] parts = text.trim().split("\\n\\s*\\n");
        int added = 0, dup = 0, notTx = 0;
        Expense last = null;
        for (String part : parts) {
            if (part.trim().isEmpty()) continue;
            Expense e = SmsImporter.handleText(this, part.trim());
            if (e == null) notTx++;
            else if (e.id == -1) dup++;
            else { added++; last = e; }
        }
        if (last != null) {
            month.setTimeInMillis(last.ts);
            month.set(Calendar.DAY_OF_MONTH, 1);
        }
        refresh();
        String msg;
        if (added == 0 && dup > 0) msg = "Already added";
        else if (added == 0) msg = "No transaction found in that message";
        else msg = added + " transaction" + (added == 1 ? "" : "s") + " added"
                + (dup > 0 ? ", " + dup + " already there" : "")
                + (notTx > 0 ? ", " + notTx + " skipped" : "");
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        if (openSingle && added == 1) EditSheet.show(this, last);
    }

    void pasteDialog() {
        EditText box = new EditText(this);
        box.setHint("Paste bank / UPI / card SMS here.\nPut a blank line between messages.");
        box.setMinLines(6);
        box.setGravity(Gravity.TOP);
        box.setTextSize(14);
        android.content.ClipboardManager cm =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null
                && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence clip = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            if (clip != null) box.setText(clip);
        }
        LinearLayout f = Ui.v(this);
        f.setPadding(Ui.dp(this, 22), Ui.dp(this, 4), Ui.dp(this, 22), 0);
        f.addView(box);
        if (!hasSms()) {
            TextView tip = Ui.text(this, "Quicker: in Messages, long-press a bank SMS → Share → SpendSense.", 12, Ui.MUTED, false);
            tip.setPadding(0, Ui.dp(this, 8), 0, 0);
            f.addView(tip);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Paste bank SMS")
                .setView(f)
                .setPositiveButton("Add", (d, w) -> importText(box.getText().toString(), false))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------------- Export ----------------

    void exportCsv() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/csv");
        i.putExtra(Intent.EXTRA_TITLE, "spendsense-expenses.csv");
        startActivityForResult(i, REQ_EXPORT);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 4) { // default SMS app request
            refresh();
            Toast.makeText(this, AutoSms.isDefaultSmsApp(this) ? "SpendSense is now your SMS app"
                    : "SMS app not changed", Toast.LENGTH_SHORT).show();
            return;
        }
        if (req != REQ_EXPORT || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        StringBuilder sb = new StringBuilder("Date,Type,Amount,Merchant,Category,Mode,Bank,Account,Note\n");
        for (Expense e : db.all()) {
            sb.append(Format.csvDate(e.ts)).append(',')
                    .append(e.debit ? "Expense" : "Income").append(',')
                    .append(plain(e.amount)).append(',')
                    .append(csv(e.merchant)).append(',')
                    .append(csv(e.category)).append(',')
                    .append(csv(e.mode)).append(',')
                    .append(csv(e.bank)).append(',')
                    .append(csv(e.account)).append(',')
                    .append(csv(e.note)).append('\n');
        }
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os != null) os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "Exported", Toast.LENGTH_SHORT).show();
        } catch (Exception ex) {
            Toast.makeText(this, "Export failed: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    static String plain(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.US, "%.2f", v);
    }

    /** Wraps a screen's column in a scroll view with room for the add button. */
    ScrollView scroller(LinearLayout col) {
        ScrollView sv = new ScrollView(this);
        col.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 96));
        sv.addView(col);
        return sv;
    }
}
