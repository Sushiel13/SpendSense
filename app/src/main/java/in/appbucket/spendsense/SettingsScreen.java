package in.appbucket.spendsense;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Automatic tracking status, budget, export and about. */
final class SettingsScreen {
    private final MainActivity a;

    SettingsScreen(MainActivity a) {
        this.a = a;
    }

    View build() {
        LinearLayout col = Ui.v(a);
        col.addView(trackingCard());
        col.addView(budgetCard());

        LinearLayout data = Ui.card(a);
        data.addView(Ui.text(a, "Your data", 16, Ui.INK, true));
        data.addView(row("📤", "Export to CSV", "Open in Excel or Google Sheets", v -> a.exportCsv()));
        data.addView(row("📋", "Paste bank SMS", "Add transactions from copied messages", v -> a.pasteDialog()));
        TextView count = Ui.text(a, a.db.count() + " transactions saved on this phone", 13, Ui.MUTED, false);
        count.setPadding(0, Ui.dp(a, 10), 0, 0);
        data.addView(count);
        col.addView(data);

        LinearLayout about = Ui.card(a);
        about.addView(Ui.text(a, "Privacy", 16, Ui.INK, true));
        TextView p = Ui.text(a, "SpendSense has no internet permission, so your messages and spends never leave this phone. "
                + "It ignores OTPs, payment requests and messages from people.", 14, Ui.MUTED, false);
        p.setPadding(0, Ui.dp(a, 6), 0, 0);
        about.addView(p);
        TextView ver = Ui.text(a, "Version " + BuildConfig.VERSION_NAME
                + (BuildConfig.SMS_AUTO ? " · Automatic" : " · Share & paste"), 12, Ui.MUTED, false);
        ver.setPadding(0, Ui.dp(a, 10), 0, 0);
        about.addView(ver);
        col.addView(about);
        return a.scroller(col);
    }

    private View trackingCard() {
        LinearLayout c = Ui.card(a);
        c.addView(Ui.text(a, "Automatic tracking", 16, Ui.INK, true));

        if (a.hasSms()) {
            c.addView(status("● On", Ui.GREEN, "Every bank, UPI and card SMS is added automatically, including new ones as they arrive."));
            View b = Ui.button(a, a.scanning ? "Checking your SMS…" : "Check all SMS again", Ui.PRIMARY_SOFT, Ui.PRIMARY);
            b.setEnabled(!a.scanning);
            b.setOnClickListener(v -> a.startScan(0, true));
            c.addView(b, btnLp());
        } else if (BuildConfig.SMS_AUTO) {
            c.addView(status("● Off", Ui.RED, "Allow SMS access so SpendSense can read your transaction messages."));
            View b = Ui.button(a, "Turn on", Ui.PRIMARY, Color.WHITE);
            b.setOnClickListener(v -> {
                if (a.shouldShowRequestPermissionRationale(android.Manifest.permission.READ_SMS)
                        || !a.prefs.getBoolean("askedOnce", false)) {
                    a.prefs.edit().putBoolean("askedOnce", true).apply();
                    a.requestPermissions(new String[]{android.Manifest.permission.READ_SMS,
                            android.Manifest.permission.RECEIVE_SMS}, 1);
                } else {
                    // Permanently denied: open the app's settings page
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", a.getPackageName(), null));
                    a.startActivity(i);
                }
            });
            c.addView(b, btnLp());
        } else {
            c.addView(status("● Share & paste", Ui.PRIMARY,
                    "This version can't read your inbox by itself. Android blocks SMS reading in apps that aren't installed "
                            + "from Google Play. Add spends in seconds instead:"));
            String[] steps = {"1.  Open Messages and long-press a bank SMS",
                    "2.  Tap Share, then SpendSense",
                    "3.  It's added with amount, merchant and date"};
            for (String s : steps) {
                TextView t = Ui.text(a, s, 14, Ui.INK, false);
                t.setPadding(0, Ui.dp(a, 6), 0, 0);
                c.addView(t);
            }
            TextView more = Ui.text(a, "For fully automatic tracking, install SpendSense from Google Play when it's available.",
                    13, Ui.MUTED, false);
            more.setPadding(0, Ui.dp(a, 10), 0, 0);
            c.addView(more);
        }
        return c;
    }

    private View budgetCard() {
        LinearLayout c = Ui.card(a);
        int budget = a.prefs.getInt("budget", 0);
        c.addView(Ui.text(a, "Monthly budget", 16, Ui.INK, true));
        TextView t = Ui.text(a, budget > 0 ? Format.money(budget) + " a month" : "Not set. Set one to see how much is left each month.",
                14, Ui.MUTED, false);
        t.setPadding(0, Ui.dp(a, 4), 0, 0);
        c.addView(t);
        View b = Ui.button(a, budget > 0 ? "Change budget" : "Set budget", Ui.PRIMARY_SOFT, Ui.PRIMARY);
        b.setOnClickListener(v -> {
            EditText in = new EditText(a);
            in.setInputType(InputType.TYPE_CLASS_NUMBER);
            in.setHint("Amount in ₹");
            if (budget > 0) in.setText(String.valueOf(budget));
            LinearLayout f = Ui.v(a);
            f.setPadding(Ui.dp(a, 22), Ui.dp(a, 4), Ui.dp(a, 22), 0);
            f.addView(in);
            new MaterialAlertDialogBuilder(a)
                    .setTitle("Monthly budget")
                    .setView(f)
                    .setPositiveButton("Save", (d, w) -> {
                        int val = 0;
                        try { val = Integer.parseInt(in.getText().toString().trim()); } catch (NumberFormatException ignored) { }
                        a.prefs.edit().putInt("budget", Math.max(0, val)).apply();
                        a.refresh();
                    })
                    .setNeutralButton("Remove", (d, w) -> {
                        a.prefs.edit().putInt("budget", 0).apply();
                        a.refresh();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        c.addView(b, btnLp());
        return c;
    }

    private View status(String label, int color, String text) {
        LinearLayout l = Ui.v(a);
        l.setPadding(0, Ui.dp(a, 6), 0, 0);
        l.addView(Ui.text(a, label, 14, color, true));
        TextView t = Ui.text(a, text, 14, Ui.MUTED, false);
        t.setPadding(0, Ui.dp(a, 4), 0, 0);
        l.addView(t);
        return l;
    }

    private View row(String icon, String title, String sub, View.OnClickListener l) {
        LinearLayout row = Ui.h(a);
        row.setPadding(0, Ui.dp(a, 14), 0, 0);
        TextView ic = Ui.text(a, icon, 18, Ui.INK, false);
        ic.setGravity(android.view.Gravity.CENTER);
        ic.setBackground(Ui.round(a, Ui.PRIMARY_SOFT, 12));
        row.addView(ic, Ui.lp(Ui.dp(a, 40), Ui.dp(a, 40)));
        LinearLayout t = Ui.v(a);
        t.setPadding(Ui.dp(a, 12), 0, 0, 0);
        t.addView(Ui.text(a, title, 15, Ui.INK, true));
        t.addView(Ui.text(a, sub, 12, Ui.MUTED, false));
        row.addView(t, Ui.weight(1));
        row.addView(Ui.text(a, "›", 20, Ui.MUTED, false));
        row.setOnClickListener(l);
        return row;
    }

    private LinearLayout.LayoutParams btnLp() {
        LinearLayout.LayoutParams lp = Ui.lp(-1, Ui.dp(a, 48));
        lp.topMargin = Ui.dp(a, 14);
        return lp;
    }
}
