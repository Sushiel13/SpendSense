package in.appbucket.spendsense;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExpenseDb extends SQLiteOpenHelper {

    private static ExpenseDb instance;

    public static synchronized ExpenseDb get(Context c) {
        if (instance == null) instance = new ExpenseDb(c.getApplicationContext());
        return instance;
    }

    private ExpenseDb(Context c) {
        super(c, "spendsense.db", null, 1);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.enableWriteAheadLogging(); // lets the UI read while a scan is writing
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE expenses (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "sms_key TEXT UNIQUE," +
                "ts INTEGER NOT NULL," +
                "amount REAL NOT NULL," +
                "debit INTEGER NOT NULL," +
                "merchant TEXT," +
                "category TEXT," +
                "mode TEXT," +
                "bank TEXT," +
                "account TEXT," +
                "note TEXT," +
                "sms_body TEXT," +
                "hidden INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_ts ON expenses(ts)");
        db.execSQL("CREATE TABLE merchant_rules (merchant TEXT PRIMARY KEY, category TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) { }

    /** Inserts if the SMS was not seen before. Returns new row id or -1 if duplicate. */
    public long insertIfNew(Expense e) {
        String learned = learnedCategory(e.merchant);
        if (learned != null && e.debit) e.category = learned;
        ContentValues v = toValues(e);
        v.put("sms_key", e.smsKey);
        return getWritableDatabase().insertWithOnConflict("expenses", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void update(Expense e) {
        getWritableDatabase().update("expenses", toValues(e), "id=?", new String[]{String.valueOf(e.id)});
    }

    /** Hide rather than delete so a rescan of the inbox doesn't bring it back. */
    public void hide(long id) {
        ContentValues v = new ContentValues();
        v.put("hidden", 1);
        getWritableDatabase().update("expenses", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void rememberMerchant(String merchant, String category, boolean applyToAll) {
        if (merchant == null || merchant.isEmpty() || "Unknown".equals(merchant)) return;
        ContentValues v = new ContentValues();
        v.put("merchant", merchant);
        v.put("category", category);
        SQLiteDatabase db = getWritableDatabase();
        db.insertWithOnConflict("merchant_rules", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        if (applyToAll) {
            ContentValues u = new ContentValues();
            u.put("category", category);
            db.update("expenses", u, "merchant=? AND debit=1", new String[]{merchant});
        }
    }

    private String learnedCategory(String merchant) {
        if (merchant == null) return null;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT category FROM merchant_rules WHERE merchant=?", new String[]{merchant})) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    public List<Expense> between(long from, long to) {
        List<Expense> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,sms_key,ts,amount,debit,merchant,category,mode,bank,account,note,sms_body " +
                        "FROM expenses WHERE hidden=0 AND ts>=? AND ts<? ORDER BY ts DESC",
                new String[]{String.valueOf(from), String.valueOf(to)})) {
            while (c.moveToNext()) {
                Expense e = new Expense();
                e.id = c.getLong(0);
                e.smsKey = c.getString(1);
                e.ts = c.getLong(2);
                e.amount = c.getDouble(3);
                e.debit = c.getInt(4) == 1;
                e.merchant = nz(c.getString(5));
                e.category = nz(c.getString(6));
                e.mode = nz(c.getString(7));
                e.bank = nz(c.getString(8));
                e.account = nz(c.getString(9));
                e.note = nz(c.getString(10));
                e.smsBody = nz(c.getString(11));
                out.add(e);
            }
        }
        return out;
    }

    public List<Expense> all() {
        return between(0, Long.MAX_VALUE);
    }

    /** Category -> total spent, highest first. */
    public static Map<String, Double> byCategory(List<Expense> list) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (Expense e : list) {
            if (!e.debit) continue;
            Double cur = m.get(e.category);
            m.put(e.category, (cur == null ? 0 : cur) + e.amount);
        }
        List<Map.Entry<String, Double>> entries = new ArrayList<>(m.entrySet());
        java.util.Collections.sort(entries, (a, b) -> Double.compare(b.getValue(), a.getValue()));
        Map<String, Double> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, Double> en : entries) sorted.put(en.getKey(), en.getValue());
        return sorted;
    }

    /** Merchant -> [total, count] of spends, highest total first. */
    public static List<Object[]> byMerchant(List<Expense> list) {
        Map<String, double[]> m = new LinkedHashMap<>();
        for (Expense e : list) {
            if (!e.debit) continue;
            double[] cur = m.get(e.merchant);
            if (cur == null) { cur = new double[2]; m.put(e.merchant, cur); }
            cur[0] += e.amount;
            cur[1] += 1;
        }
        List<Object[]> out = new ArrayList<>();
        for (Map.Entry<String, double[]> en : m.entrySet()) out.add(new Object[]{en.getKey(), en.getValue()[0], (int) en.getValue()[1]});
        java.util.Collections.sort(out, (a, b) -> Double.compare((Double) b[1], (Double) a[1]));
        return out;
    }

    /** True if this SMS was already turned into a transaction (or deleted by the user). */
    public boolean hasKey(String smsKey) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM expenses WHERE sms_key=? AND hidden=0", new String[]{smsKey})) {
            return c.moveToFirst();
        }
    }

    public int count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM expenses WHERE hidden=0", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    private static ContentValues toValues(Expense e) {
        ContentValues v = new ContentValues();
        v.put("ts", e.ts);
        v.put("amount", e.amount);
        v.put("debit", e.debit ? 1 : 0);
        v.put("merchant", e.merchant);
        v.put("category", e.category);
        v.put("mode", e.mode);
        v.put("bank", e.bank);
        v.put("account", e.account);
        v.put("note", e.note);
        v.put("sms_body", e.smsBody);
        return v;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
