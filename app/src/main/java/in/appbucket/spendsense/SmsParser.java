package in.appbucket.spendsense;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java parser for Indian bank / UPI / card transaction SMS.
 * No Android imports so it can be unit-tested on a plain JVM.
 */
public final class SmsParser {

    public static final class Result {
        public double amount;
        public boolean debit;      // true = expense, false = income/credit
        public String merchant;    // may be "Unknown"
        public String account;     // last 4 digits or ""
        public String mode;        // UPI / Card / ATM / Bank
        public String bank;        // e.g. HDFC, or ""
        public String category;
        public long date;          // epoch millis read from the SMS text, or 0 if none found

        @Override public String toString() {
            return String.format(Locale.US, "%s %.2f | %s | %s | %s | %s | a/c %s",
                    debit ? "DEBIT" : "CREDIT", amount, merchant, category, mode, bank, account);
        }
    }

    private static final String NUM = "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)";
    private static final Pattern AMT = Pattern.compile("(?i)(?:rs\\.?|inr|₹)\\s*:?\\s*" + NUM);
    private static final Pattern AMT_FALLBACK = Pattern.compile(
            "(?i)(?:debited by|debited for|credited by|credited with|debited with|amount of|amt of)\\s+(?:rs\\.?|inr|₹)?\\s*" + NUM);

    private static final Pattern IGNORE = Pattern.compile("(?i)(\\botp\\b|one[- ]time password|verification code"
            + "|will be debited|to be debited|would be debited|scheduled|is due|due date|due on|due by|min(?:imum)?\\.? (?:amt|amount) due"
            + "|total (?:amt|amount) due|statement (?:for|is generated)|has requested|collect request|requested money|payment request"
            + "|pre-?approved|loan offer|apply now|recharge now|\\bfailed\\b|\\bdeclined\\b|could not be|unsuccessful"
            + "|reminder|bill generated|e-?mandate (?:registered|created)|is pending)");

    private static final Pattern STRONG_DEBIT = Pattern.compile(
            "(?i)\\b(debited|spent|paid|sent|withdrawn|withdrawal|purchased?|charged|deducted|transferred to)\\b");
    private static final Pattern CREDIT = Pattern.compile(
            "(?i)\\b(credited(?!\\s+to)|received(?!\\s+(?:a\\s+)?request)|deposited|refund(?:ed)?|cashback of)\\b");
    private static final Pattern WEAK_DEBIT = Pattern.compile("(?i)\\b(debit|dr|txn|transaction|used|payment)\\b");

    private static final Pattern ACCOUNT = Pattern.compile(
            "(?i)(?:a/?c|acct|account|card|ac)\\s*(?:no\\.?)?\\s*(?:ending\\s*(?:with|in)?\\s*)?[x*#.]*\\s*(\\d{3,6})\\b");

    private static final String END = "(?=\\s+on\\b|\\s+via\\b|\\s+ref|\\s+txn|\\s+upi|\\s+avl|\\s+avbl|\\s+using|\\s+dated|\\s+date|\\s+at\\s+\\d|\\s+for\\b|\\s+from\\b|\\s+not you|\\s*[.,;:(]|\\s+\\d|$)";
    private static final String NAME = "([A-Za-z][A-Za-z0-9 &'*_\\-]{1,40}?)";

    private static final Pattern[] MERCHANT_PATTERNS = {
            Pattern.compile("(?i)UPI/(?:P2[AM]/)?\\d+/([A-Za-z][A-Za-z0-9 &._]{1,30}?)(?=/|\\s+(?:not|ref|axis)\\b|\\s+\\d|$)"),
            Pattern.compile("(?i)\\b(?:vpa|to|towards)\\s+([\\w.\\-]{2,}@[A-Za-z]{2,})"),
            Pattern.compile("(?i)[;.]\\s*" + NAME + "\\s+credited\\b"),
            Pattern.compile("(?i)\\bat\\s+" + NAME + END),
            Pattern.compile("(?i)\\b(?:trf to|transfer to|paid to|sent to|to)\\s+" + NAME + END),
            Pattern.compile("(?i)\\binfo:?\\s*" + NAME + END),
            Pattern.compile("(?i)\\b(?:from|by)\\s+([\\w.\\-]{2,}@[A-Za-z]{2,})"),
            Pattern.compile("(?i)\\b(?:from|by)\\s+" + NAME + END),
    };

    private static final Pattern BAD_MERCHANT = Pattern.compile(
            "(?i)^(your|you|a/?c|ac|acct|account|card|bank|xx|the|beneficiary|upi|imps|neft|rtgs|mobile|net banking|self|my|transfer|deposit)\\b.*");

    private SmsParser() {}

    /** Quick sender filter: Indian transactional SMS use alphanumeric sender IDs, not phone numbers. */
    public static boolean looksLikeBankSender(String sender) {
        if (sender == null) return false;
        String s = sender.replaceAll("[\\s+\\-]", "");
        return s.matches(".*[A-Za-z].*");
    }

    public static Result parse(String sender, String body) {
        if (body == null) return null;
        String text = body.replaceAll("\\s+", " ").trim();
        if (IGNORE.matcher(text).find()) return null;

        Double amount = findAmount(text);
        if (amount == null || amount <= 0) return null;

        Boolean debit;
        if (STRONG_DEBIT.matcher(text).find()) debit = true;
        else if (CREDIT.matcher(text).find()) debit = false;
        else if (WEAK_DEBIT.matcher(text).find()) debit = true;
        else debit = null;
        if (debit == null) return null;

        Result r = new Result();
        r.amount = amount;
        r.debit = debit;
        r.merchant = findMerchant(text);
        r.account = findAccount(text);
        r.mode = findMode(text);
        r.bank = bankFromSender(sender, text);
        r.category = r.debit ? Categorizer.categorize(r.merchant + " " + text) : "Income";
        r.date = findDate(text, System.currentTimeMillis());
        return r;
    }

    private static final String MONTHS = "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec";
    private static final Pattern DATE_ISO = Pattern.compile("\\b(20\\d{2})-(\\d{1,2})-(\\d{1,2})\\b");
    private static final Pattern DATE_MON = Pattern.compile(
            "(?i)\\b(\\d{1,2})[-/ ]?(" + MONTHS + ")[a-z]*[-/ ,]*(\\d{4}|\\d{2})(?!\\d)");
    private static final Pattern DATE_NUM = Pattern.compile("\\b(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4}|\\d{2})(?!\\d)");

    /** Reads the transaction date written in the SMS (Indian day-first order). Returns 0 if none. */
    public static long findDate(String text, long now) {
        Matcher m = DATE_ISO.matcher(text);
        if (m.find()) {
            long d = makeDate(int_(m.group(1)), int_(m.group(2)), int_(m.group(3)), now);
            if (d > 0) return d;
        }
        m = DATE_MON.matcher(text);
        while (m.find()) {
            int mon = MONTHS.indexOf(m.group(2).toLowerCase(Locale.ROOT)) / 4 + 1;
            long d = makeDate(year(m.group(3)), mon, int_(m.group(1)), now);
            if (d > 0) return d;
        }
        m = DATE_NUM.matcher(text);
        while (m.find()) {
            long d = makeDate(year(m.group(3)), int_(m.group(2)), int_(m.group(1)), now);
            if (d > 0) return d;
        }
        return 0;
    }

    private static int int_(String s) {
        return Integer.parseInt(s);
    }

    private static int year(String y) {
        int v = Integer.parseInt(y);
        return v < 100 ? 2000 + v : v;
    }

    private static long makeDate(int y, int mon, int d, long now) {
        if (y < 2000 || mon < 1 || mon > 12 || d < 1 || d > 31) return 0;
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setLenient(false);
        c.clear();
        try {
            c.set(y, mon - 1, d, 12, 0, 0);
            long t = c.getTimeInMillis();
            if (t > now + 36L * 60 * 60 * 1000) return 0; // not a future date
            return t;
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    private static Double findAmount(String text) {
        Matcher m = AMT.matcher(text);
        while (m.find()) {
            String before = text.substring(Math.max(0, m.start() - 22), m.start()).toLowerCase(Locale.ROOT);
            if (before.contains("bal") || before.contains("limit") || before.contains("lmt")
                    || before.contains("avl") || before.contains("avbl") || before.contains("available")
                    || before.contains("due")) continue;
            Double v = toNumber(m.group(1));
            if (v != null) return v;
        }
        m = AMT_FALLBACK.matcher(text);
        if (m.find()) return toNumber(m.group(1));
        return null;
    }

    private static Double toNumber(String s) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String findMerchant(String text) {
        for (Pattern p : MERCHANT_PATTERNS) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                String c = clean(m.group(1));
                if (c != null) return c;
            }
        }
        return "Unknown";
    }

    private static String clean(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.contains("@")) s = s.substring(0, s.indexOf('@'));
        s = s.replaceAll("[._\\-]+", " ").replaceAll("\\s+", " ").trim();
        s = s.replaceAll("(?i)\\s+(on|via|ref|refno)$", "").trim();
        if (s.length() < 2 || BAD_MERCHANT.matcher(s).matches() || s.matches("[0-9 ]+")) return null;
        if (s.matches("(?i)x+\\d*")) return null;
        return titleCase(s);
    }

    private static String titleCase(String s) {
        StringBuilder b = new StringBuilder();
        for (String w : s.toLowerCase(Locale.ROOT).split(" ")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }

    private static String findAccount(String text) {
        Matcher m = ACCOUNT.matcher(text);
        if (m.find()) {
            String d = m.group(1);
            return d.length() > 4 ? d.substring(d.length() - 4) : d;
        }
        return "";
    }

    private static String findMode(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains("upi") || t.contains("vpa") || t.matches(".*[\\w.\\-]+@[a-z]{2,}.*")) return "UPI";
        if (t.contains("atm") || t.contains("withdrawn") || t.contains("cash withdrawal")) return "ATM";
        if (t.contains("card")) return "Card";
        if (t.contains("neft") || t.contains("imps") || t.contains("rtgs")) return "Transfer";
        return "Bank";
    }

    private static final String[][] BANKS = {
            {"HDFC", "HDFC"}, {"SBI", "SBI"}, {"ICICI", "ICICI"}, {"AXIS", "Axis"}, {"KOTAK", "Kotak"},
            {"KKBK", "Kotak"}, {"PNB", "PNB"}, {"BOB", "Bank of Baroda"}, {"BARODA", "Bank of Baroda"},
            {"CANBNK", "Canara"}, {"CANARA", "Canara"}, {"UNION", "Union Bank"}, {"UBOI", "Union Bank"},
            {"IDFC", "IDFC First"}, {"YES", "Yes Bank"}, {"INDUS", "IndusInd"}, {"FEDBNK", "Federal"},
            {"FEDERAL", "Federal"}, {"PAYTM", "Paytm"}, {"AMEX", "Amex"}, {"AUBANK", "AU Bank"},
            {"IOB", "IOB"}, {"BOI", "Bank of India"}, {"CITI", "Citi"}, {"SCB", "Standard Chartered"},
            {"RBL", "RBL"}, {"SLICE", "Slice"}, {"ONECARD", "OneCard"}, {"JUPITER", "Jupiter"},
    };

    private static String bankFromSender(String sender, String text) {
        String s = sender == null ? "" : sender.toUpperCase(Locale.ROOT);
        for (String[] b : BANKS) if (s.contains(b[0])) return b[1];
        String t = text.toUpperCase(Locale.ROOT);
        for (String[] b : BANKS) if (b[0].length() >= 4 && t.contains(b[0])) return b[1];
        return "";
    }
}
