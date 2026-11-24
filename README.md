# SpendSense

Android app that reads bank, UPI and card SMS and turns them into an expense tracker automatically.

## What it does
- **Scans your SMS inbox** (last 6 months on first run) and adds every debit/credit it finds
- **Adds new transactions live** as bank SMS arrive, with a notification ("Spent ₹250 at Swiggy · Food")
- Pulls out **amount, debit/credit, merchant, account last 4 digits, bank, and mode** (UPI / Card / ATM / Transfer)
- **Auto-categorises** spends (Food, Groceries, Shopping, Travel, Fuel, Bills, Rent & EMI, Entertainment, Health, …)
- **Learns your corrections**: change a merchant's category once and tick "use for all", and future ones follow
- Monthly view with **Spent vs Received** totals and a **category breakdown**
- **Manual add** for cash spends, edit/delete any entry, **CSV export**
- Ignores OTPs, payment requests, due-date reminders, upcoming autopays, failed/declined transactions and personal chats

## Privacy
The app has **no INTERNET permission**. Everything stays in a local SQLite database on the phone.

## Supported formats
Tested against message formats from HDFC, SBI, ICICI, Axis, Kotak, Paytm Payments Bank and IDFC First
(UPI, debit/credit card, ATM, NEFT/IMPS credits). See `app/src/test/.../SmsParserTest.java`.
Other banks generally work too, since the parser looks for common patterns (`Rs/INR/₹`, `debited/spent/sent`, `at/to/VPA`).

## Build
Push to `main` → GitHub Actions runs the parser tests and builds `SpendSense.apk` (download from the run's artifacts).
Push a tag like `v1.0.0` → the APK is also attached to a GitHub Release.

For consistent signing (so updates install over the old version), add these repo secrets:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

```bash
keytool -genkeypair -v -keystore release.jks -alias spendsense -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks   # paste output into KEYSTORE_BASE64
```

Local build: open in Android Studio, or `gradle assembleRelease` with Gradle 8.7 + JDK 17 + Android SDK 34.

## Google Play note
Play only allows `READ_SMS` for apps that qualify under its SMS permission policy (SMS-based money management is one
of the listed exceptions) and requires a Permissions Declaration Form. Sideloading / your own store has no such restriction.
