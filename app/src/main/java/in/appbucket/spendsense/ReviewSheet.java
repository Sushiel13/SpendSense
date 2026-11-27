package in.appbucket.spendsense;

import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;
import java.util.Locale;

/** Messages that look like money but couldn't be read automatically: add or dismiss each. */
final class ReviewSheet {

    private ReviewSheet() {}

    static void show(MainActivity a) {
        List<ExpenseDb.Review> list = a.db.pendingReviews();
        BottomSheetDialog sheet = new BottomSheetDialog(a);
        LinearLayout col = Ui.v(a);
        col.setPadding(Ui.dp(a, 20), Ui.dp(a, 16), Ui.dp(a, 20), Ui.dp(a, 24));
        col.addView(Ui.text(a, "Check these messages", 20, Ui.INK, true));
        TextView sub = Ui.text(a, "They mention money but SpendSense couldn't read them for sure. "
                + "Add the ones that are real spends or income.", 14, Ui.MUTED, false);
        sub.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 6));
        col.addView(sub);

        if (list.size() > 1) {
            TextView all = Ui.text(a, "None of these are transactions", 14, Ui.PRIMARY, true);
            all.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
            all.setOnClickListener(v -> {
                for (ExpenseDb.Review r : list) a.db.finishReview(r.key);
                sheet.dismiss();
                a.refresh();
            });
            col.addView(all);
        }

        int n = Math.min(list.size(), 100);
        for (int i = 0; i < n; i++) col.addView(item(a, sheet, list.get(i)));

        ScrollView sv = new ScrollView(a);
        sv.addView(col);
        sheet.setContentView(sv);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.setOnDismissListener(d -> a.refresh());
        sheet.show();
    }

    private static View item(MainActivity a, BottomSheetDialog sheet, ExpenseDb.Review r) {
        LinearLayout c = Ui.card(a);
        c.setBackground(Ui.round(a, 0xFFF8F9FC, 16));
        LinearLayout top = Ui.h(a);
        top.addView(Ui.oneLine(Ui.text(a, senderName(r.sender), 15, Ui.INK, true)), Ui.weight(1));
        top.addView(Ui.text(a, Format.date(r.ts), 12, Ui.MUTED, false));
        c.addView(top);
        TextView body = Ui.text(a, r.body, 13, Ui.MUTED, false);
        body.setMaxLines(5);
        body.setEllipsize(android.text.TextUtils.TruncateAt.END);
        body.setPadding(0, Ui.dp(a, 4), 0, 0);
        c.addView(body);

        LinearLayout buttons = Ui.h(a);
        LinearLayout.LayoutParams bl = Ui.lp(-1, -2);
        bl.topMargin = Ui.dp(a, 10);
        View skip = Ui.button(a, "Not a transaction", Ui.SURFACE, Ui.MUTED);
        View add = Ui.button(a, r.amount > 0 ? "Add " + Format.money(r.amount) : "Add", Ui.PRIMARY, Color.WHITE);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, Ui.dp(a, 44), 1);
        sl.rightMargin = Ui.dp(a, 8);
        buttons.addView(skip, sl);
        buttons.addView(add, new LinearLayout.LayoutParams(0, Ui.dp(a, 44), 1));
        c.addView(buttons, bl);

        skip.setOnClickListener(v -> {
            a.db.finishReview(r.key);
            c.setVisibility(View.GONE);
        });
        add.setOnClickListener(v -> {
            Expense e = new Expense();
            e.smsKey = r.key;
            e.ts = r.ts;
            e.amount = r.amount;
            String low = r.body.toLowerCase(Locale.ROOT);
            e.debit = !(low.contains("credited") || low.contains("received") || low.contains("refund"));
            e.merchant = "";
            e.category = e.debit ? Categorizer.categorize(r.body) : "Income";
            e.mode = low.contains("upi") ? "UPI" : low.contains("card") ? "Card" : "Bank";
            e.bank = senderName(r.sender);
            e.smsBody = r.body;
            sheet.dismiss();
            EditSheet.show(a, e);
        });
        return c;
    }

    /** "VM-HDFCBK-S" -> "HDFCBK"; phone numbers unchanged. */
    static String senderName(String s) {
        if (s == null || s.isEmpty()) return "Unknown sender";
        if (!s.matches(".*[A-Za-z].*")) return s;
        String[] p = s.toUpperCase(Locale.ROOT).split("-");
        return p.length >= 2 ? p[1] : s.toUpperCase(Locale.ROOT);
    }
}
