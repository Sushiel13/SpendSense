package in.appbucket.spendsense;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

/** Trends and breakdowns: 6-month chart, key numbers, top merchants, categories. */
final class InsightsScreen {
    private final MainActivity a;

    InsightsScreen(MainActivity a) {
        this.a = a;
    }

    View build() {
        LinearLayout col = Ui.v(a);
        List<Expense> all = a.db.between(a.monthStart(), a.monthEnd());

        col.addView(trendCard());

        double spent = 0;
        int count = 0;
        Expense biggest = null;
        for (Expense e : all) {
            if (!e.debit) continue;
            spent += e.amount;
            count++;
            if (biggest == null || e.amount > biggest.amount) biggest = e;
        }
        int days = a.isCurrentMonth() ? Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                : a.month.getActualMaximum(Calendar.DAY_OF_MONTH);
        Map<String, Double> cats = ExpenseDb.byCategory(all);
        String topCat = cats.isEmpty() ? "–" : cats.keySet().iterator().next();

        LinearLayout grid1 = Ui.h(a);
        LinearLayout grid2 = Ui.h(a);
        grid1.addView(tile("Average per day", Format.money(Math.round(spent / Math.max(1, days))), null), tileLp(true));
        grid1.addView(tile("Spends", String.valueOf(count), null), tileLp(false));
        grid2.addView(tile("Biggest spend", biggest == null ? "–" : Format.money(biggest.amount),
                biggest == null ? null : biggest.merchant), tileLp(true));
        grid2.addView(tile("Top category", cats.isEmpty() ? "–" : Categorizer.emoji(topCat) + " " + topCat, null), tileLp(false));
        col.addView(grid1);
        col.addView(grid2);

        col.addView(merchantsCard(all));
        col.addView(categoriesCard(cats, spent));
        return a.scroller(col);
    }

    private View trendCard() {
        LinearLayout c = Ui.card(a);
        c.addView(Ui.text(a, "Spending, last 6 months", 16, Ui.INK, true));
        String[] labels = new String[6];
        double[] vals = new double[6];
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(a.monthStart());
        cal.add(Calendar.MONTH, -5);
        double sum = 0;
        int months = 0;
        for (int i = 0; i < 6; i++) {
            long s = cal.getTimeInMillis();
            cal.add(Calendar.MONTH, 1);
            long e = cal.getTimeInMillis();
            labels[i] = Format.shortMonth(s);
            for (Expense x : a.db.between(s, e)) if (x.debit) vals[i] += x.amount;
            if (i < 5 && vals[i] > 0) { sum += vals[i]; months++; }
        }
        if (months > 0) {
            TextView avg = Ui.text(a, "Monthly average before this: " + Format.money(Math.round(sum / months)), 13, Ui.MUTED, false);
            avg.setPadding(0, Ui.dp(a, 2), 0, 0);
            c.addView(avg);
        }
        BarChart chart = new BarChart(a);
        chart.setData(labels, vals, 5);
        LinearLayout.LayoutParams lp = Ui.lp(-1, Ui.dp(a, 180));
        lp.topMargin = Ui.dp(a, 12);
        c.addView(chart, lp);
        return c;
    }

    private View tile(String label, String value, String sub) {
        LinearLayout t = Ui.v(a);
        t.setBackground(Ui.round(a, Ui.SURFACE, 18));
        t.setPadding(Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14));
        t.addView(Ui.oneLine(Ui.text(a, label, 12, Ui.MUTED, false)));
        t.addView(Ui.oneLine(Ui.text(a, value, 18, Ui.INK, true)));
        t.addView(Ui.oneLine(Ui.text(a, sub == null ? " " : sub, 12, Ui.MUTED, false)));
        return t;
    }

    private LinearLayout.LayoutParams tileLp(boolean left) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.topMargin = Ui.dp(a, 12);
        if (left) p.rightMargin = Ui.dp(a, 6);
        else p.leftMargin = Ui.dp(a, 6);
        return p;
    }

    private View merchantsCard(List<Expense> all) {
        LinearLayout c = Ui.card(a);
        c.addView(Ui.text(a, "Top merchants", 16, Ui.INK, true));
        List<Object[]> ms = ExpenseDb.byMerchant(all);
        if (ms.isEmpty()) {
            TextView t = Ui.text(a, "No spends this month", 14, Ui.MUTED, false);
            t.setPadding(0, Ui.dp(a, 10), 0, 0);
            c.addView(t);
        }
        int n = Math.min(5, ms.size());
        for (int i = 0; i < n; i++) {
            Object[] m = ms.get(i);
            final String name = (String) m[0];
            LinearLayout row = Ui.h(a);
            row.setPadding(0, Ui.dp(a, 12), 0, 0);
            TextView rank = Ui.text(a, String.valueOf(i + 1), 13, Ui.PRIMARY, true);
            rank.setGravity(android.view.Gravity.CENTER);
            rank.setBackground(Ui.round(a, Ui.PRIMARY_SOFT, 10));
            row.addView(rank, Ui.lp(Ui.dp(a, 28), Ui.dp(a, 28)));
            LinearLayout mid = Ui.v(a);
            mid.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 8), 0);
            mid.addView(Ui.oneLine(Ui.text(a, name, 15, Ui.INK, true)));
            int cnt = (Integer) m[2];
            mid.addView(Ui.text(a, cnt + (cnt == 1 ? " payment" : " payments"), 12, Ui.MUTED, false));
            row.addView(mid, Ui.weight(1));
            row.addView(Ui.text(a, Format.money((Double) m[1]), 15, Ui.INK, true));
            row.setOnClickListener(v -> {
                a.txQuery = name;
                a.txCategory = null;
                a.txType = 1;
                a.go(MainActivity.ID_TX);
            });
            c.addView(row);
        }
        return c;
    }

    private View categoriesCard(Map<String, Double> cats, double spent) {
        LinearLayout c = Ui.card(a);
        c.addView(Ui.text(a, "By category", 16, Ui.INK, true));
        for (Map.Entry<String, Double> en : cats.entrySet()) {
            final String cat = en.getKey();
            int pct = spent > 0 ? (int) Math.round(en.getValue() * 100 / spent) : 0;
            LinearLayout block = Ui.v(a);
            block.setPadding(0, Ui.dp(a, 12), 0, 0);
            LinearLayout row = Ui.h(a);
            row.addView(Ui.badge(a, cat, 34));
            TextView name = Ui.oneLine(Ui.text(a, cat, 14, Ui.INK, true));
            name.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 8), 0);
            row.addView(name, Ui.weight(1));
            row.addView(Ui.text(a, Format.money(en.getValue()) + "  ·  " + pct + "%", 13, Ui.MUTED, false));
            block.addView(row);
            LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
            lp.topMargin = Ui.dp(a, 6);
            lp.leftMargin = Ui.dp(a, 44);
            block.addView(Ui.bar(a, Math.max(pct, 1), Categorizer.color(cat), 0xFFF0F1F6), lp);
            block.setOnClickListener(v -> {
                a.txCategory = cat;
                a.txType = 1;
                a.txQuery = "";
                a.go(MainActivity.ID_TX);
            });
            c.addView(block);
        }
        if (cats.isEmpty()) {
            TextView t = Ui.text(a, "No spends this month", 14, Ui.MUTED, false);
            t.setPadding(0, Ui.dp(a, 10), 0, 0);
            c.addView(t);
        }
        return c;
    }
}
