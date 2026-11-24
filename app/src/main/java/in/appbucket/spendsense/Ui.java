package in.appbucket.spendsense;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/** Small helpers so screens can be built in code with a consistent look. */
final class Ui {
    static final int BG = 0xFFF4F5FB;
    static final int SURFACE = 0xFFFFFFFF;
    static final int PRIMARY = 0xFF4F46E5;
    static final int PRIMARY_2 = 0xFF7C3AED;
    static final int PRIMARY_SOFT = 0xFFEEF0FF;
    static final int INK = 0xFF111827;
    static final int MUTED = 0xFF6B7280;
    static final int LINE = 0xFFE7E9F2;
    static final int RED = 0xFFDC2626;
    static final int GREEN = 0xFF059669;

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    static TextView oneLine(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static GradientDrawable gradient(Context c, int from, int to, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{from, to});
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static LinearLayout v(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout h(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
    }

    /** White rounded card with standard padding and a top margin. */
    static LinearLayout card(Context c) {
        LinearLayout l = v(c);
        l.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        l.setBackground(round(c, SURFACE, 18));
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.topMargin = dp(c, 14);
        l.setLayoutParams(p);
        return l;
    }

    /** Card title row with optional action text on the right. */
    static LinearLayout cardHeader(Context c, String title, String action, View.OnClickListener onAction) {
        LinearLayout row = h(c);
        row.addView(text(c, title, 16, INK, true), weight(1));
        if (action != null) {
            TextView a = text(c, action, 14, PRIMARY, true);
            a.setPadding(dp(c, 8), dp(c, 4), 0, dp(c, 4));
            a.setOnClickListener(onAction);
            row.addView(a);
        }
        return row;
    }

    /** Coloured circle with the category emoji. */
    static TextView badge(Context c, String category, int sizeDp) {
        TextView t = new TextView(c);
        t.setText(Categorizer.emoji(category));
        t.setTextSize(sizeDp * 0.42f);
        t.setGravity(Gravity.CENTER);
        int col = Categorizer.color(category);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.argb(38, Color.red(col), Color.green(col), Color.blue(col)));
        t.setBackground(g);
        t.setLayoutParams(lp(dp(c, sizeDp), dp(c, sizeDp)));
        return t;
    }

    /** One transaction line: badge, merchant + details, signed amount. */
    static View txRow(Context c, Expense e, boolean showDate) {
        LinearLayout row = h(c);
        row.setPadding(0, dp(c, 10), 0, dp(c, 10));
        row.addView(badge(c, e.category, 42));

        LinearLayout mid = v(c);
        mid.setPadding(dp(c, 12), 0, dp(c, 8), 0);
        mid.addView(oneLine(text(c, e.merchant.isEmpty() ? "Unknown" : e.merchant, 15, INK, true)));
        StringBuilder sub = new StringBuilder(e.category);
        sub.append(" · ").append(showDate ? Format.date(e.ts) : Format.time(e.ts));
        if (!e.mode.isEmpty()) sub.append(" · ").append(e.mode);
        if (!e.account.isEmpty()) sub.append(" ••").append(e.account);
        mid.addView(oneLine(text(c, sub, 12, MUTED, false)));
        row.addView(mid, weight(1));

        TextView amt = text(c, (e.debit ? "−" : "+") + Format.money(e.amount), 15, e.debit ? INK : GREEN, true);
        row.addView(amt);
        return row;
    }

    static View divider(Context c) {
        View d = new View(c);
        d.setBackgroundColor(LINE);
        LinearLayout.LayoutParams p = lp(-1, Math.max(1, dp(c, 0.7f)));
        p.leftMargin = dp(c, 54);
        d.setLayoutParams(p);
        return d;
    }

    static MaterialButton button(Context c, String label, int bg, int fg) {
        MaterialButton b = new MaterialButton(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(fg);
        b.setBackgroundTintList(ColorStateList.valueOf(bg));
        b.setCornerRadius(dp(c, 14));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setMinHeight(dp(c, 50));
        return b;
    }

    static LinearProgressIndicator bar(Context c, int percent, int color, int track) {
        LinearProgressIndicator p = new LinearProgressIndicator(c);
        p.setMax(100);
        p.setProgressCompat(Math.max(0, Math.min(100, percent)), false);
        p.setIndicatorColor(color);
        p.setTrackColor(track);
        p.setTrackThickness(dp(c, 8));
        p.setTrackCornerRadius(dp(c, 4));
        return p;
    }

    /** Small label/value pair used in stat rows. */
    static LinearLayout stat(Context c, String label, String value, int labelColor, int valueColor) {
        LinearLayout b = v(c);
        b.addView(oneLine(text(c, label, 12, labelColor, false)));
        b.addView(oneLine(text(c, value, 16, valueColor, true)));
        return b;
    }
}
