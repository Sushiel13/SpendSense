package in.appbucket.spendsense;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends Activity {

    private static final int REQ_SMS = 1;
    private static final int REQ_EXPORT = 2;
    private static final long SIX_MONTHS = 183L * 24 * 60 * 60 * 1000;

    private static final int BG = Color.parseColor("#F4F6FB");
    private static final int PRIMARY = Color.parseColor("#3D5AFE");
    private static final int RED = Color.parseColor("#E53935");
    private static final int GREEN = Color.parseColor("#2E7D32");
    private static final int GREY = Color.parseColor("#6B7280");
    private static final int DARK = Color.parseColor("#111827");

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Calendar month = Calendar.getInstance();
    private ExpenseDb db;
    private SharedPreferences prefs;

    private TextView monthLabel;
    private LinearLayout summaryBox;
    private ListView list;
    private final List<Expense> shown = new ArrayList<>();
    private TxAdapter adapter;
    private int filter = 0; // 0 = expenses, 1 = income, 2 = all

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        db = ExpenseDb.get(this);
        prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        getWindow().setStatusBarColor(PRIMARY);
        month.set(Calendar.DAY_OF_MONTH, 1);
        if (!BuildConfig.SMS_AUTO || hasSms() || prefs.getBoolean("skipOnboarding", false)) showMain();
        else showOnboarding();
        handleShare(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (list == null) showMain();
        handleShare(intent);
    }

    /** Bank SMS shared from the Messages app (long-press -> Share -> SpendSense). */
    private void handleShare(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        CharSequence cs = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        intent.setAction(Intent.ACTION_MAIN); // don't re-import on rotation
        if (cs == null || cs.toString().trim().isEmpty()) return;
        if (list == null) showMain();
        importText(cs.toString(), true);
    }

    /** Imports one or more pasted/shared SMS. Separate messages with a blank line. */
    private void importText(String text, boolean openSingle) {
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
        else if (added == 0) msg = "No transaction found. Paste a bank, UPI or card SMS, or tap + Add.";
        else msg = added + " transaction" + (added == 1 ? "" : "s") + " added"
                + (dup > 0 ? ", " + dup + " already there" : "")
                + (notTx > 0 ? ", " + notTx + " skipped" : "");
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        if (openSingle && added == 1 && last != null) editDialog(last);
    }

    private void pasteDialog() {
        EditText box = new EditText(this);
        box.setHint("Paste bank / UPI / card SMS here.\nPut a blank line between messages.");
        box.setMinLines(6);
        box.setGravity(Gravity.TOP);
        android.content.ClipboardManager cm =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null
                && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence clip = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            if (clip != null) box.setText(clip);
        }
        LinearLayout f = vbox();
        f.setPadding(dp(20), dp(8), dp(20), 0);
        f.addView(box);
        TextView tip = text("Quicker: in Messages, long-press a bank SMS → Share → SpendSense.", 12, GREY, false);
        tip.setPadding(0, dp(8), 0, 0);
        f.addView(tip);
        new AlertDialog.Builder(this)
                .setTitle("Add from SMS")
                .setView(f)
                .setPositiveButton("Add", (d, w) -> importText(box.getText().toString(), false))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (list != null) refresh();
    }

    private boolean hasSms() {
        if (!BuildConfig.SMS_AUTO) return false;
        return checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    // ---------- Onboarding ----------

    private void showOnboarding() {
        LinearLayout root = vbox();
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(32), dp(32), dp(32), dp(32));
        root.setBackgroundColor(BG);

        TextView icon = text("₹", 64, PRIMARY, true);
        icon.setGravity(Gravity.CENTER);
        root.addView(icon);
        TextView title = text("SpendSense", 28, DARK, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView body = text("Tracks your spending automatically from bank, UPI and card SMS.\n\n"
                + "• Reads only transaction messages (amount, merchant, account)\n"
                + "• Ignores OTPs and personal chats\n"
                + "• Everything stays on this phone, the app has no internet access", 15, GREY, false);
        body.setPadding(0, dp(16), 0, dp(24));
        root.addView(body);

        Button allow = button("Allow SMS access", PRIMARY);
        allow.setOnClickListener(v -> {
            List<String> perms = new ArrayList<>(Arrays.asList(
                    Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS));
            if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS);
            requestPermissions(perms.toArray(new String[0]), REQ_SMS);
        });
        root.addView(allow, new LinearLayout.LayoutParams(-1, dp(52)));

        Button manual = flatButton("Continue without SMS (manual entry)");
        manual.setOnClickListener(v -> {
            prefs.edit().putBoolean("skipOnboarding", true).apply();
            showMain();
        });
        root.addView(manual);
        setContentView(root);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code != REQ_SMS) return;
        if (hasSms()) {
            showMain();
            scan(true);
        } else {
            Toast.makeText(this, "SMS access is needed for automatic tracking", Toast.LENGTH_LONG).show();
        }
    }

    // ---------- Main screen ----------

    private void showMain() {
        LinearLayout root = vbox();
        root.setBackgroundColor(BG);

        LinearLayout header = vbox();
        header.setBackgroundColor(PRIMARY);
        header.setPadding(dp(16), dp(14), dp(16), dp(12));
        header.addView(text("SpendSense", 22, Color.WHITE, true));

        LinearLayout nav = hbox();
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(0, dp(8), 0, 0);
        TextView prev = text("‹", 30, Color.WHITE, true);
        TextView next = text("›", 30, Color.WHITE, true);
        monthLabel = text("", 17, Color.WHITE, true);
        monthLabel.setGravity(Gravity.CENTER);
        prev.setPadding(dp(8), 0, dp(16), 0);
        next.setPadding(dp(16), 0, dp(8), 0);
        prev.setOnClickListener(v -> { month.add(Calendar.MONTH, -1); refresh(); });
        next.setOnClickListener(v -> { month.add(Calendar.MONTH, 1); refresh(); });
        nav.addView(prev);
        nav.addView(monthLabel, new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(next);
        header.addView(nav);
        root.addView(header);

        list = new ListView(this);
        list.setDivider(null);
        list.setPadding(dp(12), 0, dp(12), dp(8));
        list.setClipToPadding(false);
        summaryBox = vbox();
        list.addHeaderView(summaryBox, null, false);
        adapter = new TxAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            int i = pos - list.getHeaderViewsCount();
            if (i >= 0 && i < shown.size()) editDialog(shown.get(i));
        });
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bar = hbox();
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));
        bar.setBackgroundColor(Color.WHITE);
        Button scan = button(BuildConfig.SMS_AUTO ? "Scan SMS" : "Paste SMS", PRIMARY);
        Button add = button("+ Add", GREEN);
        Button export = button("Export", DARK);
        scan.setOnClickListener(v -> {
            if (!BuildConfig.SMS_AUTO) pasteDialog();
            else if (hasSms()) scan(false);
            else showOnboarding();
        });
        add.setOnClickListener(v -> editDialog(null));
        export.setOnClickListener(v -> exportCsv());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
        lp.setMargins(dp(4), 0, dp(4), 0);
        bar.addView(scan, lp);
        bar.addView(add, new LinearLayout.LayoutParams(lp));
        bar.addView(export, new LinearLayout.LayoutParams(lp));
        root.addView(bar);

        setContentView(root);
        refresh();
        if (hasSms() && prefs.getLong("lastScan", 0) > 0) scan(false, true);
    }

    private void scan(boolean firstTime) {
        scan(firstTime, false);
    }

    private void scan(boolean firstTime, boolean silent) {
        long last = prefs.getLong("lastScan", 0);
        long since = (firstTime || last == 0) ? System.currentTimeMillis() - SIX_MONTHS
                : last - 24L * 60 * 60 * 1000;
        if (!silent) Toast.makeText(this, "Reading transaction SMS…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            int added = SmsImporter.scanInbox(this, since);
            prefs.edit().putLong("lastScan", System.currentTimeMillis()).apply();
            ui.post(() -> {
                refresh();
                if (!silent || added > 0) {
                    Toast.makeText(this, added == 0 ? "No new transactions found"
                            : added + " new transaction" + (added == 1 ? "" : "s") + " added", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private long monthStart() {
        Calendar c = (Calendar) month.clone();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private long monthEnd() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(monthStart());
        c.add(Calendar.MONTH, 1);
        return c.getTimeInMillis();
    }

    private void refresh() {
        if (list == null) return;
        monthLabel.setText(Format.month(monthStart()));
        List<Expense> all = db.between(monthStart(), monthEnd());

        double spent = 0, received = 0;
        for (Expense e : all) {
            if (e.debit) spent += e.amount;
            else received += e.amount;
        }

        shown.clear();
        for (Expense e : all) {
            if (filter == 2 || (filter == 0 && e.debit) || (filter == 1 && !e.debit)) shown.add(e);
        }
        buildSummary(all, spent, received);
        adapter.notifyDataSetChanged();
    }

    private void buildSummary(List<Expense> all, double spent, double received) {
        summaryBox.removeAllViews();

        LinearLayout card = card();
        LinearLayout totals = hbox();
        totals.addView(stat("Spent", Format.money(spent), RED), new LinearLayout.LayoutParams(0, -2, 1));
        totals.addView(stat("Received", Format.money(received), GREEN), new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(totals);
        int count = 0;
        for (Expense e : all) if (e.debit) count++;
        TextView sub = text(count + " expense" + (count == 1 ? "" : "s") + " this month", 13, GREY, false);
        sub.setPadding(0, dp(6), 0, 0);
        card.addView(sub);
        summaryBox.addView(card);

        Map<String, Double> cats = ExpenseDb.byCategory(all);
        if (!cats.isEmpty()) {
            LinearLayout cc = card();
            cc.addView(text("Where it went", 16, DARK, true));
            for (Map.Entry<String, Double> en : cats.entrySet()) {
                LinearLayout row = hbox();
                row.setPadding(0, dp(10), 0, dp(2));
                row.addView(text(en.getKey(), 14, DARK, false), new LinearLayout.LayoutParams(0, -2, 1));
                int pct = spent > 0 ? (int) Math.round(en.getValue() * 100 / spent) : 0;
                row.addView(text(Format.money(en.getValue()) + "  · " + pct + "%", 14, GREY, false));
                cc.addView(row);
                ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                pb.setMax(100);
                pb.setProgress(Math.max(pct, 1));
                pb.setProgressTintList(android.content.res.ColorStateList.valueOf(PRIMARY));
                cc.addView(pb, new LinearLayout.LayoutParams(-1, dp(8)));
            }
            summaryBox.addView(cc);
        }

        LinearLayout chips = hbox();
        chips.setPadding(0, dp(12), 0, dp(4));
        String[] labels = {"Expenses", "Income", "All"};
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView chip = text(labels[i], 14, filter == i ? Color.WHITE : DARK, filter == i);
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));
            chip.setBackground(round(filter == i ? PRIMARY : Color.WHITE, 20));
            chip.setOnClickListener(v -> { filter = idx; refresh(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(0, 0, dp(8), 0);
            chips.addView(chip, lp);
        }
        summaryBox.addView(chips);

        if (shown.isEmpty()) {
            String hint;
            if (!BuildConfig.SMS_AUTO) hint = "No transactions for this month yet.\n\n"
                    + "To add a bank SMS: open Messages, long-press the SMS, tap Share, then pick SpendSense.\n\n"
                    + "Or copy one or more SMS and tap \"Paste SMS\". Use \"+ Add\" for cash spends.";
            else if (hasSms()) hint = "No transactions for this month yet.\nTap \"Scan SMS\" to check your inbox, or \"+ Add\" for cash spends.";
            else hint = "SMS access is off. Tap \"Scan SMS\" to turn on automatic tracking, or \"+ Add\" to enter spends manually.";
            TextView empty = text(hint, 14, GREY, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(32), dp(16), dp(32));
            summaryBox.addView(empty);
        }
    }

    // ---------- Edit / add ----------

    private void editDialog(Expense existing) {
        final boolean isNew = existing == null;
        final Expense e = isNew ? new Expense() : existing;
        if (isNew) {
            e.ts = System.currentTimeMillis();
            e.debit = true;
            e.mode = "Cash";
            e.merchant = "";
            e.category = "Other";
        }

        LinearLayout f = vbox();
        f.setPadding(dp(20), dp(8), dp(20), 0);

        EditText amount = new EditText(this);
        amount.setHint("Amount (₹)");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (!isNew) amount.setText(trim(e.amount));
        f.addView(amount);

        EditText merchant = new EditText(this);
        merchant.setHint("Paid to / received from");
        merchant.setText(e.merchant);
        f.addView(merchant);

        Spinner type = new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Expense", "Income"}));
        type.setSelection(e.debit ? 0 : 1);
        f.addView(type);

        List<String> catList = new ArrayList<>(Arrays.asList(Categorizer.CATEGORIES));
        if (!catList.contains("Income")) catList.add("Income");
        if (!catList.contains(e.category)) catList.add(e.category);
        Spinner cat = new Spinner(this);
        cat.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, catList));
        cat.setSelection(catList.indexOf(e.category));
        f.addView(cat);

        EditText note = new EditText(this);
        note.setHint("Note (optional)");
        note.setText(e.note);
        f.addView(note);

        CheckBox applyAll = new CheckBox(this);
        applyAll.setText("Use this category for all payments to this merchant");
        if (!isNew) f.addView(applyAll);

        if (!isNew) {
            TextView meta = text(Format.date(e.ts) + "  ·  " + e.mode
                    + (e.bank.isEmpty() ? "" : "  ·  " + e.bank)
                    + (e.account.isEmpty() ? "" : " ••" + e.account), 12, GREY, false);
            meta.setPadding(0, dp(8), 0, 0);
            f.addView(meta);
            if (!e.smsBody.isEmpty()) {
                TextView sms = text("SMS: " + e.smsBody, 12, GREY, false);
                sms.setPadding(0, dp(6), 0, 0);
                f.addView(sms);
            }
        }

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(f);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(isNew ? "Add transaction" : "Edit transaction")
                .setView(sv)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null);
        if (!isNew) b.setNeutralButton("Delete", (d, w) -> confirmDelete(e));
        AlertDialog dlg = b.create();
        dlg.setOnShowListener(d -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double amt;
            try {
                amt = Double.parseDouble(amount.getText().toString().replace(",", "").trim());
            } catch (NumberFormatException ex) {
                amount.setError("Enter an amount");
                return;
            }
            if (amt <= 0) {
                amount.setError("Enter an amount");
                return;
            }
            e.amount = amt;
            e.merchant = merchant.getText().toString().trim();
            if (e.merchant.isEmpty()) e.merchant = "Unknown";
            e.debit = type.getSelectedItemPosition() == 0;
            e.category = (String) cat.getSelectedItem();
            if (!e.debit && !"Income".equals(e.category) && isNew) e.category = "Income";
            e.note = note.getText().toString().trim();
            if (isNew) {
                e.smsKey = "manual-" + UUID.randomUUID();
                db.insertIfNew(e);
            } else {
                db.update(e);
                if (e.debit) db.rememberMerchant(e.merchant, e.category, applyAll.isChecked());
            }
            dlg.dismiss();
            refresh();
        }));
        dlg.show();
    }

    private void confirmDelete(Expense e) {
        new AlertDialog.Builder(this)
                .setTitle("Delete this transaction?")
                .setMessage(Format.money(e.amount) + " · " + e.merchant
                        + "\n\nIt won't come back when you scan SMS again.")
                .setPositiveButton("Delete", (d, w) -> { db.hide(e.id); refresh(); })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- Export ----------

    private void exportCsv() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/csv");
        i.putExtra(Intent.EXTRA_TITLE, "spendsense-expenses.csv");
        startActivityForResult(i, REQ_EXPORT);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_EXPORT || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        StringBuilder sb = new StringBuilder("Date,Type,Amount,Merchant,Category,Mode,Bank,Account,Note\n");
        for (Expense e : db.all()) {
            sb.append(Format.csvDate(e.ts)).append(',')
                    .append(e.debit ? "Expense" : "Income").append(',')
                    .append(trim(e.amount)).append(',')
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

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.US, "%.2f", v);
    }

    // ---------- List adapter ----------

    private class TxAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return shown.get(i).id; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Expense e = shown.get(i);
            LinearLayout row = hbox();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackground(round(Color.WHITE, 12));

            TextView badge = text(e.category.isEmpty() ? "?" : e.category.substring(0, 1), 16, PRIMARY, true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(round(Color.parseColor("#E8ECFF"), 20));
            row.addView(badge, new LinearLayout.LayoutParams(dp(40), dp(40)));

            LinearLayout mid = vbox();
            mid.setPadding(dp(12), 0, dp(8), 0);
            TextView name = text(e.merchant, 15, DARK, true);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            mid.addView(name);
            String sub = e.category + " · " + Format.date(e.ts) + (e.mode.isEmpty() ? "" : " · " + e.mode);
            TextView subtitle = text(sub, 12, GREY, false);
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            mid.addView(subtitle);
            row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));

            row.addView(text((e.debit ? "−" : "+") + Format.money(e.amount), 15, e.debit ? RED : GREEN, true));

            LinearLayout wrap = vbox();
            wrap.setPadding(0, dp(4), 0, dp(4));
            wrap.addView(row, new LinearLayout.LayoutParams(-1, -2));
            return wrap;
        }
    }

    // ---------- Tiny view helpers ----------

    private LinearLayout card() {
        LinearLayout c = vbox();
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(round(Color.WHITE, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private View stat(String label, String value, int color) {
        LinearLayout b = vbox();
        b.addView(text(label, 13, GREY, false));
        b.addView(text(value, 22, color, true));
        return b;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout hbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s, int color) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setBackground(round(color, 12));
        return b;
    }

    private Button flatButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(PRIMARY);
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
