package in.appbucket.spendsense;

import android.content.Context;

import java.util.Locale;

/** Turns SMS into stored expenses. Used by both the inbox scan and the live receiver. */
public final class SmsImporter {

    private SmsImporter() {}

    /** Stable key so the same SMS is never added twice (live receiver + later inbox scan). */
    static String keyFor(String sender, String body) {
        String s = sender == null ? "" : sender.toUpperCase(Locale.ROOT);
        // "VM-HDFCBK-S" / "AD-HDFCBK" -> "HDFCBK"
        String[] parts = s.split("-");
        if (parts.length >= 2) s = parts[1];
        String b = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        return s + "|" + b.length() + "|" + b.hashCode();
    }

    /** Returns the stored expense, or null if the SMS isn't a transaction or was already stored. */
    public static Expense handle(Context ctx, String sender, String body, long ts) {
        if (!SmsParser.looksLikeBankSender(sender)) return null;
        SmsParser.Result r = SmsParser.parse(sender, body);
        if (r == null) return null;
        Expense e = new Expense();
        e.smsKey = keyFor(sender, body);
        e.ts = ts;
        e.amount = r.amount;
        e.debit = r.debit;
        e.merchant = r.merchant;
        e.category = r.category;
        e.mode = r.mode;
        e.bank = r.bank;
        e.account = r.account;
        e.smsBody = body;
        long id = ExpenseDb.get(ctx).insertIfNew(e);
        if (id == -1) return null;
        e.id = id;
        return e;
    }

    /**
     * Imports SMS text the user shared or pasted (sender unknown, date read from the text).
     * Returns null if it isn't a transaction, or an Expense with id -1 if it was already added.
     */
    public static Expense handleText(Context ctx, String body) {
        SmsParser.Result r = SmsParser.parse("", body);
        if (r == null) return null;
        Expense e = new Expense();
        e.smsKey = keyFor("", body);
        e.ts = r.date > 0 ? r.date : System.currentTimeMillis();
        e.amount = r.amount;
        e.debit = r.debit;
        e.merchant = r.merchant;
        e.category = r.category;
        e.mode = r.mode;
        e.bank = r.bank;
        e.account = r.account;
        e.smsBody = body;
        long id = ExpenseDb.get(ctx).insertIfNew(e);
        e.id = id;
        return e;
    }

    /** Progress callback for long scans. */
    public interface Progress {
        void update(int scanned, int total, int added);
    }
}
