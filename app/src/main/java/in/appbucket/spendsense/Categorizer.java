package in.appbucket.spendsense;

import java.util.Locale;

/** Keyword-based category guess. Users can change any category in the app. */
public final class Categorizer {

    public static final String[] CATEGORIES = {
            "Food", "Groceries", "Shopping", "Travel", "Fuel", "Bills & Utilities", "Rent & EMI",
            "Entertainment", "Health", "Education", "Investments", "Transfers", "Cash", "Other"
    };

    private static final String[][] RULES = {
            {"Food", "swiggy", "zomato", "eatsure", "dominos", "domino", "pizza", "mcdonald", "kfc", "burger", "starbucks",
                    "cafe", "restaurant", "hotel", "chaayos", "haldiram", "barbeque", "dhaba", "bakery", "food"},
            {"Groceries", "bigbasket", "blinkit", "zepto", "instamart", "dmart", "d mart", "jiomart", "grofers", "more retail",
                    "reliance fresh", "star bazaar", "nature s basket", "kirana", "supermarket", "grocery", "milkbasket", "country delight"},
            {"Shopping", "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tata cliq", "croma", "reliance digital",
                    "decathlon", "ikea", "lifestyle", "westside", "pantaloons", "shoppers stop", "lenskart", "firstcry", "mall"},
            {"Travel", "uber", "ola", "rapido", "irctc", "makemytrip", "goibibo", "cleartrip", "yatra", "indigo", "air india",
                    "vistara", "akasa", "spicejet", "redbus", "metro", "fastag", "toll", "parking", "airport", "railway", "oyo"},
            {"Fuel", "petrol", "diesel", "fuel", "hpcl", "bpcl", "indian oil", "iocl", "shell", "nayara", "filling station"},
            {"Bills & Utilities", "electricity", "msedcl", "mahadiscom", "bescom", "tata power", "adani electricity", "water bill",
                    "gas", "mahanagar gas", "indane", "bharat gas", "airtel", "jio", "vodafone", "vi ", "bsnl", "act fibernet",
                    "broadband", "recharge", "dth", "tata play", "bill", "postpaid", "maintenance", "society"},
            {"Rent & EMI", "rent", "emi", "loan", "nobroker", "housing", "bajaj finance", "home loan", "nach"},
            {"Entertainment", "netflix", "hotstar", "prime video", "spotify", "youtube", "bookmyshow", "pvr", "inox",
                    "sonyliv", "zee5", "jiocinema", "steam", "playstation", "gaming", "apple com bill", "google play"},
            {"Health", "pharmacy", "pharmeasy", "netmeds", "1mg", "apollo", "medplus", "hospital", "clinic", "diagnostic",
                    "lab", "practo", "cult fit", "cultfit", "gym", "medical", "chemist"},
            {"Education", "udemy", "coursera", "byju", "unacademy", "school", "college", "university", "tuition", "fees", "books"},
            {"Investments", "zerodha", "groww", "upstox", "angel one", "kuvera", "coin", "mutual fund", "sip", "nps", "ppf", "lic"},
            {"Cash", "atm", "cash withdrawal", "withdrawn"},
            {"Transfers", "neft", "imps", "rtgs", "p2a", "self transfer"},
    };

    private Categorizer() {}

    public static String categorize(String text) {
        String t = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ") + " ";
        for (String[] rule : RULES) {
            for (int i = 1; i < rule.length; i++) {
                if (t.contains(" " + rule[i])) return rule[0];
            }
        }
        return "Other";
    }
}
