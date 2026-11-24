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

    static String csvDate(long ts) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(new Date(ts));
    }
}
