package in.appbucket.spendsense;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/** Dashboard: month total, budget, comparison, category ring and recent transactions. */
final class HomeScreen {
    private final MainActivity a;

    HomeScreen(MainActivity a) {
        this.a = a;
    }

    View build() {
        LinearLayout col = Ui.v(a);
        List<Expense> all = a.db.between(a.monthStart(), a.monthEnd());

        if (a.scanning) col.addView(scanBanner());

        double spent = 0, received = 0;
        int count = 0;
        for (Expense e : all) {
            if (e.debit) { spent += e.amount; count++; }
            else received += e.amount;
        }

        col.addView(hero(spent, received, count));

        if (all.isEmpty() && !a.scanning) {
            col.addView(emptyState());
            return a.scroller(col);
        }

        col.addView(categoryCard(all, spent));
        col.addView(recentCard(all));
        return a.scroller(col);
    }

    private View scanBanner() {
        LinearLayout c = Ui.card(a);
        c.setBackground(Ui.round(a, Ui.PRIMARY_SOFT, 18));
        String msg = a.scanTotal == 0 ? "Reading your SMS…"
                : "Reading your SMS… " + a.scanDone + " of " + a.scanTotal;
        c.addView(Ui.text(a, msg, 15, Ui.PRIMARY, true));
        c.addView(Ui.text(a, a.scanAdded + " transactions found so far", 13, Ui.MUTED, false));
        LinearProgressIndicator p = new LinearProgressIndicator(a);
        p.setIndicatorColor(Ui.PRIMARY);
        p.setTrackColor(0xFFD9DCFB);
        p.setTrackCornerRadius(Ui.dp(a, 3));
        if (a.scanTotal > 0) {
            p.setMax(100);
            p.setProgressCompat((int) (a.scanDone * 100L / a.scanTotal), false);
        } else {
            p.setIndeterminate(true);
        }
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(a, 10);
        c.addView(p, lp);
        return c;
    }

    private View hero(double spent, double received, int count) {
        LinearLayout c = Ui.v(a);
        c.setBackground(Ui.gradient(a, Ui.PRIMARY, Ui.PRIMARY_2, 24));
        c.setPadding(Ui.dp(a, 20), Ui.dp(a, 20), Ui.dp(a, 20), Ui.dp(a, 20));
        LinearLayout.LayoutParams clp = Ui.lp(-1, -2);
        clp.topMargin = Ui.dp(a, 10);
        c.setLayoutParams(clp);

        c.addView(Ui.text(a, "Spent in " + Format.monthName(a.monthStart()), 14, 0xCCFFFFFF, false));
        c.addView(Ui.text(a, Format.money(spent), 34, Color.WHITE, true));

        // Compare with last month up to the same day
        Calendar pc = Calendar.getInstance();
        pc.setTimeInMillis(a.monthStart());
        pc.add(Calendar.MONTH, -1);
        long prevStart = pc.getTimeInMillis();
        long prevEnd = a.isCurrentMonth()
                ? prevStart + (System.currentTimeMillis() - a.monthStart())
                : a.monthStart();
        double prev = 0;
        for (Expense e : a.db.between(prevStart, Math.min(prevEnd, a.monthStart()))) if (e.debit) prev += e.amount;
        if (prev > 0) {
            double diff = (spent - prev) / prev * 100;
            String arrow = diff >= 0 ? "▲ " : "▼ ";
            String when = a.isCurrentMonth() ? " vs same time last month" : " vs last month";
            TextView cmp = Ui.text(a, arrow + Math.round(Math.abs(diff)) + "%" + when, 13, Color.WHITE, false);
            cmp.setBackground(Ui.round(a, 0x26FFFFFF, 10));
            cmp.setPadding(Ui.dp(a, 10), Ui.dp(a, 4), Ui.dp(a, 10), Ui.dp(a, 4));
            LinearLayout.LayoutParams lp = Ui.lp(-2, -2);
            lp.topMargin = Ui.dp(a, 6);
            c.addView(cmp, lp);
        }

        int budget = a.prefs.getInt("budget", 0);
        if (budget > 0) {
            int pct = (int) Math.round(spent * 100 / budget);
            LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
            lp.topMargin = Ui.dp(a, 16);
            c.addView(Ui.bar(a, pct, spent > budget ? 0xFFFCA5A5 : Color.WHITE, 0x40FFFFFF), lp);
            String left = spent <= budget
                    ? Format.money(budget - spent) + " left of " + Format.money(budget) + " budget"
                    : Format.money(spent - budget) + " over your " + Format.money(budget) + " budget";
            TextView bt = Ui.text(a, left, 13, 0xE6FFFFFF, false);
            bt.setPadding(0, Ui.dp(a, 6), 0, 0);
            c.addView(bt);
        }

        LinearLayout stats = Ui.h(a);
        stats.setPadding(0, Ui.dp(a, 16), 0, 0);
        int days = a.isCurrentMonth() ? Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                : a.month.getActualMaximum(Calendar.DAY_OF_MONTH);
        stats.addView(Ui.stat(a, "Received", Format.money(received), 0xB3FFFFFF, Color.WHITE), Ui.weight(1));
        stats.addView(Ui.stat(a, "Spends", String.valueOf(count), 0xB3FFFFFF, Color.WHITE), Ui.weight(1));
        stats.addView(Ui.stat(a, "Per day", Format.money(Math.round(spent / Math.max(1, days))), 0xB3FFFFFF, Color.WHITE), Ui.weight(1));
        c.addView(stats);
        return c;
    }

    private View categoryCard(List<Expense> all, double spent) {
        LinearLayout c = Ui.card(a);
        c.addView(Ui.cardHeader(a, "Where your money went", "Details", v -> a.go(MainActivity.ID_INSIGHTS)));

        Map<String, Double> cats = ExpenseDb.byCategory(all);
        List<Float> vals = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        List<String> names = new ArrayList<>();
        double other = 0;
        int i = 0;
        for (Map.Entry<String, Double> en : cats.entrySet()) {
            if (i < 5) {
                vals.add(en.getValue().floatValue());
                cols.add(Categorizer.color(en.getKey()));
                names.add(en.getKey());
            } else {
                other += en.getValue();
            }
            i++;
        }
        if (other > 0) {
            vals.add((float) other);
            cols.add(0xFFCBD5E1);
            names.add("More");
        }

        LinearLayout row = Ui.h(a);
        row.setPadding(0, Ui.dp(a, 14), 0, 0);
        DonutChart donut = new DonutChart(a);
        donut.setData(vals, cols, Format.shortMoney(spent), "spent");
        row.addView(donut, Ui.lp(Ui.dp(a, 132), Ui.dp(a, 132)));

        LinearLayout legend = Ui.v(a);
        legend.setPadding(Ui.dp(a, 18), 0, 0, 0);
        if (names.isEmpty()) legend.addView(Ui.text(a, "No spends yet", 14, Ui.MUTED, false));
        for (int k = 0; k < names.size(); k++) {
            LinearLayout l = Ui.h(a);
            l.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
            View dot = new View(a);
            dot.setBackground(Ui.round(a, cols.get(k), 5));
            l.addView(dot, Ui.lp(Ui.dp(a, 10), Ui.dp(a, 10)));
            TextView n = Ui.oneLine(Ui.text(a, names.get(k), 13, Ui.INK, false));
            n.setPadding(Ui.dp(a, 8), 0, Ui.dp(a, 4), 0);
            l.addView(n, Ui.weight(1));
            int pct = spent > 0 ? (int) Math.round(vals.get(k) * 100 / spent) : 0;
            l.addView(Ui.text(a, pct + "%", 13, Ui.MUTED, true));
            final String cat = names.get(k);
            if (!"More".equals(cat)) {
                l.setOnClickListener(v -> {
                    a.txCategory = cat;
                    a.txType = 1;
                    a.go(MainActivity.ID_TX);
                });
            }
            legend.addView(l);
        }
        row.addView(legend, Ui.weight(1));
        c.addView(row);
        return c;
    }

    private View recentCard(List<Expense> all) {
        LinearLayout c = Ui.card(a);
        c.addView(Ui.cardHeader(a, "Recent transactions", "See all", v -> {
            a.txType = 0;
            a.txCategory = null;
            a.go(MainActivity.ID_TX);
        }));
        int n = Math.min(6, all.size());
        for (int i = 0; i < n; i++) {
            final Expense e = all.get(i);
            View row = Ui.txRow(a, e, true);
            row.setOnClickListener(v -> EditSheet.show(a, e));
            c.addView(row);
            if (i < n - 1) c.addView(Ui.divider(a));
        }
        return c;
    }

    private View emptyState() {
        LinearLayout c = Ui.card(a);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(Ui.dp(a, 20), Ui.dp(a, 28), Ui.dp(a, 20), Ui.dp(a, 24));
        TextView ic = Ui.text(a, "🧾", 40, Ui.INK, false);
        ic.setGravity(Gravity.CENTER);
        c.addView(ic);
        TextView t = Ui.text(a, "No transactions this month", 17, Ui.INK, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 6));
        c.addView(t);

        String msg;
        if (a.hasSms()) msg = "New bank, UPI and card SMS are added automatically. Use ‹ › above to see earlier months.";
        else if (BuildConfig.SMS_AUTO) msg = "Turn on SMS access in Settings to track spends automatically, or tap + to add one.";
        else msg = "In Messages, long-press a bank SMS, tap Share and pick SpendSense. Or tap + to paste SMS or add a cash spend.";
        TextView m = Ui.text(a, msg, 14, Ui.MUTED, false);
        m.setGravity(Gravity.CENTER);
        c.addView(m);

        View btn;
        if (a.hasSms()) {
            btn = Ui.button(a, "Check SMS again", Ui.PRIMARY, Color.WHITE);
            btn.setOnClickListener(v -> a.startScan(0, true));
        } else {
            btn = Ui.button(a, "Paste bank SMS", Ui.PRIMARY, Color.WHITE);
            btn.setOnClickListener(v -> a.pasteDialog());
        }
        LinearLayout.LayoutParams lp = Ui.lp(-1, Ui.dp(a, 50));
        lp.topMargin = Ui.dp(a, 18);
        c.addView(btn, lp);
        return c;
    }
}
