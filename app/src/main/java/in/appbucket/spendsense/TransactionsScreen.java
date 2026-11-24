package in.appbucket.spendsense;

import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Searchable, filterable list of the month's transactions, grouped by day. */
final class TransactionsScreen {
    private final MainActivity a;
    private final List<Object> items = new ArrayList<>(); // String day header, or Expense
    private List<Expense> monthList;
    private TextView summary;
    private Adapter adapter;

    TransactionsScreen(MainActivity a) {
        this.a = a;
    }

    View build() {
        monthList = a.db.between(a.monthStart(), a.monthEnd());

        LinearLayout col = Ui.v(a);
        col.setPadding(Ui.dp(a, 16), Ui.dp(a, 6), Ui.dp(a, 16), 0);

        EditText search = new EditText(a);
        search.setHint("Search merchant, category, bank or note");
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setText(a.txQuery);
        search.setBackground(Ui.round(a, Ui.SURFACE, 14));
        search.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 12));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                a.txQuery = s.toString();
                apply();
            }
        });
        col.addView(search, Ui.lp(-1, -2));

        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.h(a);
        chips.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 4));
        String[] types = {"All", "Expenses", "Income"};
        List<Chip> typeChips = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            final int idx = i;
            Chip c = chip(types[i], a.txType == i);
            c.setOnClickListener(v -> {
                a.txType = idx;
                for (int k = 0; k < typeChips.size(); k++) typeChips.get(k).setChecked(k == idx);
                apply();
            });
            typeChips.add(c);
            chips.addView(c);
        }
        Chip cat = chip(a.txCategory == null ? "All categories ▾" : a.txCategory + "  ✕", a.txCategory != null);
        cat.setOnClickListener(v -> {
            if (a.txCategory != null) {
                a.txCategory = null;
                a.refresh();
                return;
            }
            String[] cats = Categorizer.CATEGORIES;
            new MaterialAlertDialogBuilder(a)
                    .setTitle("Show category")
                    .setItems(cats, (d, w) -> {
                        a.txCategory = cats[w];
                        a.refresh();
                    })
                    .show();
        });
        chips.addView(cat);
        hs.addView(chips);
        col.addView(hs);

        summary = Ui.text(a, "", 13, Ui.MUTED, false);
        summary.setPadding(Ui.dp(a, 4), Ui.dp(a, 2), 0, Ui.dp(a, 6));
        col.addView(summary);

        ListView list = new ListView(a);
        list.setDivider(null);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, Ui.dp(a, 96));
        list.setSelector(android.R.color.transparent);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            Object o = items.get(pos);
            if (o instanceof Expense) EditSheet.show(a, (Expense) o);
        });
        col.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        apply();
        return col;
    }

    private Chip chip(String text, boolean checked) {
        Chip c = new Chip(a);
        c.setText(text);
        c.setCheckable(true);
        c.setCheckedIconVisible(false);
        c.setChecked(checked);
        int[][] states = {{android.R.attr.state_checked}, {}};
        c.setChipBackgroundColor(new ColorStateList(states, new int[]{Ui.PRIMARY, Ui.SURFACE}));
        c.setTextColor(new ColorStateList(states, new int[]{0xFFFFFFFF, Ui.INK}));
        c.setChipStrokeWidth(0);
        LinearLayout.LayoutParams lp = Ui.lp(-2, -2);
        lp.rightMargin = Ui.dp(a, 8);
        c.setLayoutParams(lp);
        return c;
    }

    private void apply() {
        String q = a.txQuery.trim().toLowerCase(Locale.ROOT);
        items.clear();
        double spent = 0, received = 0;
        int n = 0;
        Calendar last = null;
        Calendar cur = Calendar.getInstance();
        for (Expense e : monthList) {
            if (a.txType == 1 && !e.debit) continue;
            if (a.txType == 2 && e.debit) continue;
            if (a.txCategory != null && !a.txCategory.equals(e.category)) continue;
            if (!q.isEmpty()) {
                String hay = (e.merchant + " " + e.category + " " + e.bank + " " + e.note + " " + e.mode)
                        .toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) continue;
            }
            cur = Calendar.getInstance();
            cur.setTimeInMillis(e.ts);
            if (last == null || !Format.sameDay(last, cur)) {
                items.add(Format.dayHeader(e.ts));
                last = cur;
            }
            items.add(e);
            n++;
            if (e.debit) spent += e.amount;
            else received += e.amount;
        }
        StringBuilder s = new StringBuilder();
        s.append(n).append(n == 1 ? " transaction" : " transactions");
        if (spent > 0) s.append(" · ").append(Format.money(spent)).append(" spent");
        if (received > 0) s.append(" · ").append(Format.money(received)).append(" received");
        summary.setText(s);
        if (n == 0) items.add(monthList.isEmpty() ? "EMPTY:No transactions this month" : "EMPTY:Nothing matches your filters");
        adapter.notifyDataSetChanged();
    }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int i) { return items.get(i) instanceof Expense ? 1 : 0; }
        @Override public boolean isEnabled(int i) { return items.get(i) instanceof Expense; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Object o = items.get(i);
            if (o instanceof String) {
                String s = (String) o;
                if (s.startsWith("EMPTY:")) {
                    TextView t = Ui.text(a, s.substring(6), 14, Ui.MUTED, false);
                    t.setGravity(Gravity.CENTER);
                    t.setPadding(0, Ui.dp(a, 48), 0, Ui.dp(a, 48));
                    return t;
                }
                TextView t = Ui.text(a, s, 13, Ui.MUTED, true);
                t.setPadding(Ui.dp(a, 4), Ui.dp(a, 14), 0, Ui.dp(a, 6));
                return t;
            }
            Expense e = (Expense) o;
            LinearLayout wrap = Ui.v(a);
            wrap.setPadding(0, 0, 0, Ui.dp(a, 6));
            View row = Ui.txRow(a, e, false);
            row.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 10));
            row.setBackground(Ui.round(a, Ui.SURFACE, 16));
            wrap.addView(row, Ui.lp(-1, -2));
            return wrap;
        }
    }
}
