package in.appbucket.spendsense;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class Format {
    private static final Locale IN = new Locale("en", "IN");

    private Format() {}

    static String money(double v) {
        NumberFormat f = NumberFormat.getCurrencyInstance(IN);
        f.setMaximumFractionDigits(v == Math.rint(v) ? 0 : 2);
        return f.format(v);
    }

    static String date(long ts) {
        return new SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH).format(new Date(ts));
    }

    static String month(long ts) {
        return new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(new Date(ts));
    }

    /** ₹950, ₹12.5k, ₹3.2L, ₹1.1Cr */
    static String shortMoney(double v) {
        if (v < 1000) return "₹" + Math.round(v);
        if (v < 100000) return "₹" + trim1(v / 1000) + "k";
        if (v < 10000000) return "₹" + trim1(v / 100000) + "L";
        return "₹" + trim1(v / 10000000) + "Cr";
    }

    private static String trim1(double v) {
        String s = String.format(Locale.US, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    static String time(long ts) {
        return new SimpleDateFormat("h:mm a", Locale.ENGLISH).format(new Date(ts));
    }

    static String shortMonth(long ts) {
        return new SimpleDateFormat("MMM", Locale.ENGLISH).format(new Date(ts));
    }

    static String monthName(long ts) {
        return new SimpleDateFormat("MMMM", Locale.ENGLISH).format(new Date(ts));
    }

    static String fullDate(long ts) {
        return new SimpleDateFormat("EEE, d MMM yyyy", Locale.ENGLISH).format(new Date(ts));
    }

    /** "Today", "Yesterday" or "Mon, 23 Nov". */
    static String dayHeader(long ts) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        java.util.Calendar d = java.util.Calendar.getInstance();
        d.setTimeInMillis(ts);
        if (sameDay(c, d)) return "Today";
        c.add(java.util.Calendar.DAY_OF_YEAR, -1);
        if (sameDay(c, d)) return "Yesterday";
        return new SimpleDateFormat("EEE, d MMM", Locale.ENGLISH).format(new Date(ts));
    }

    static boolean sameDay(java.util.Calendar a, java.util.Calendar b) {
        return a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR)
                && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR);
    }

    static String csvDate(long ts) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(new Date(ts));
    }
}
