package in.appbucket.spendsense;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SmsParserTest {

    private static void check(String sender, String body, Double amt, Boolean debit, String merchant) {
        SmsParser.Result r = SmsParser.parse(sender, body);
        if (amt == null) {
            assertNull("should ignore: " + body, r);
            return;
        }
        assertNotNull("should parse: " + body, r);
        assertEquals(body, amt, r.amount, 0.001);
        assertEquals(body, debit, r.debit);
        if (merchant != null) assertTrue(body + " -> " + r.merchant, r.merchant.equalsIgnoreCase(merchant));
    }

    @Test
    public void indianBankMessages() {
        check("VM-HDFCBK", "Sent Rs.250.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 12/11/26\nRef 432112345678\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808", 250.0, true, "Swiggy");
        check("AD-SBIUPI-S", "Dear UPI user A/C X5678 debited by 1200.0 on date 12Nov26 trf to ZOMATO Refno 431234567890. If not u? call 1800111109. -SBI", 1200.0, true, "Zomato");
        check("JD-ICICIT", "ICICI Bank Acct XX123 debited for Rs 499.00 on 12-Nov-26; NETFLIX credited. UPI:431299887766. Call 18002662 for dispute. SMS BLOCK 123 to 9215676766.", 499.0, true, "Netflix");
        check("VM-HDFCBK", "Rs.1,250.00 spent on HDFC Bank Card x9876 at AMAZON PAY INDIA on 2026-11-12:10:00:00. Avl Lmt: Rs 50,000.00. Not you? Call 18002586161", 1250.0, true, "Amazon Pay India");
        check("AX-AXISBK", "INR 560.00 debited\nA/c no. XX4321\n12-11-26, 10:15:01\nUPI/P2M/431211112222/UBER INDIA\nNot you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank", 560.0, true, "Uber India");
        check("BP-KOTAKB", "Sent Rs.100.00 from Kotak Bank AC X1234 to blinkit.payu@hdfcbank on 12-11-26.UPI Ref 431255554444. Not you, https://kotak.com/KBANKT/Fraud", 100.0, true, "Blinkit Payu");
        check("VM-HDFCBK", "Update! INR 45,000.00 deposited in HDFC Bank A/c XX1234 on 01-NOV-26 for NEFT Cr-ACME CORP SALARY.Avl bal INR 1,20,000.00", 45000.0, false, null);
        check("AD-SBIINB", "Your A/C XXXXX5678 Credited INR 2,000.00 on 10/11/26 -Deposit by transfer from RAHUL S. Avl Bal INR 12,345.00-SBI", 2000.0, false, "Rahul S");
        check("VM-HDFCBK", "Your OTP for transaction of Rs 500 at AMAZON is 123456. Do not share.", null, null, null);
        check("VM-ICICIB", "Rs 3,450.00 is due on your ICICI Credit Card XX1111 by 20-Nov-26. Min amt due Rs 200.", null, null, null);
        check("VM-ATMSBI", "Dear Customer, Rs.2000 withdrawn at SBI ATM S1BW000123 from A/c X5678 on 12Nov26. Avl Bal Rs.10000", 2000.0, true, null);
        check("+919876543210", "Bro send Rs 500 for dinner", null, null, null); // sender filter happens in app, parser still parses
        check("VM-PAYTMB", "Paid Rs.40 to Chai Point from Paytm Payments Bank a/c 918XX123. UPI Ref: 431299999999", 40.0, true, "Chai Point");
        check("VM-IDFCFB", "Your IDFC FIRST Bank Credit Card ending 4455 has been used for INR 2,999.00 at MYNTRA on 12-NOV-2026. Available limit INR 80,000.", 2999.0, true, "Myntra");
        check("VM-HDFCBK", "Money Received - INR 300.00 in your HDFC Bank A/c xx1234 on 12-11-26 by A/c linked to VPA ramesh.k@okaxis (UPI Ref No 431212121212).", 300.0, false, "Ramesh K");
        check("VM-SBIUPI", "Rs 750 will be debited from your a/c on 15-11-26 for AutoPay mandate towards NETFLIX", null, null, null);
    }

    @Test
    public void readsDateFromText() {
        long now = System.currentTimeMillis();
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        String[][] cases = {
                {"debited by 1200.0 on date 12Nov26 trf to ZOMATO", "2026-11-12"},
                {"Rs 499.00 on 12-Nov-26; NETFLIX credited", "2026-11-12"},
                {"spent at AMAZON on 2026-11-12:10:00:00.", "2026-11-12"},
                {"to Swiggy On 12/11/26 Ref 4321", "2026-11-12"},
                {"Call 1800-111-109 no date here", "none"},
        };
        for (String[] c : cases) {
            long d = SmsParser.findDate(c[0], now);
            assertEquals(c[0], c[1], d == 0 ? "none" : f.format(new java.util.Date(d)));
        }
    }
}
