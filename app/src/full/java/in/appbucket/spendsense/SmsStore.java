package in.appbucket.spendsense;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.Telephony;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads and writes the phone's SMS/MMS store (Telephony provider). */
final class SmsStore {

    static final int TYPE_INBOX = 1, TYPE_SENT = 2, TYPE_OUTBOX = 4, TYPE_FAILED = 5, TYPE_QUEUED = 6;

    static final class Conv {
        long id;
        String address = "";
        String name = "";
        String snippet = "";
        long date;
        boolean unread;
    }

    static final class Msg {
        long id;
        String address = "";
        String body = "";
        long date;
        int type;          // TYPE_* (MMS mapped to inbox/sent)
        int status = -1;   // SMS delivery status, 0 = delivered
        boolean mms;
        long imagePartId;  // MMS image part, 0 if none
    }

    private static final Map<String, String> nameCache = new HashMap<>();

    private SmsStore() {}

    static boolean isDefault(Context c) {
        String p = Telephony.Sms.getDefaultSmsPackage(c);
        return p != null && p.equals(c.getPackageName());
    }

    // ---------------- Conversations ----------------

    static List<Conv> conversations(Context c) {
        List<Conv> out = new ArrayList<>();
        ContentResolver cr = c.getContentResolver();
        Map<Long, String> canon = new HashMap<>();
        try (Cursor a = cr.query(Uri.parse("content://mms-sms/canonical-addresses"),
                new String[]{"_id", "address"}, null, null, null)) {
            if (a != null) while (a.moveToNext()) canon.put(a.getLong(0), a.getString(1));
        } catch (Exception ignored) { }

        try (Cursor t = cr.query(Uri.parse("content://mms-sms/conversations?simple=true"),
                new String[]{"_id", "date", "recipient_ids", "snippet", "read"}, null, null, "date DESC")) {
            if (t != null && !canon.isEmpty()) {
                while (t.moveToNext()) {
                    Conv v = new Conv();
                    v.id = t.getLong(0);
                    v.date = t.getLong(1);
                    String ids = t.getString(2);
                    List<String> addrs = new ArrayList<>();
                    if (ids != null) {
                        for (String id : ids.trim().split(" ")) {
                            try {
                                String ad = canon.get(Long.parseLong(id));
                                if (ad != null) addrs.add(ad);
                            } catch (NumberFormatException ignored) { }
                        }
                    }
                    if (addrs.isEmpty()) continue;
                    v.address = addrs.get(0);
                    List<String> names = new ArrayList<>();
                    for (String ad : addrs) names.add(displayName(c, ad));
                    v.name = android.text.TextUtils.join(", ", names);
                    v.snippet = t.getString(3) == null ? "" : t.getString(3);
                    v.unread = t.getInt(4) == 0;
                    out.add(v);
                }
                return out;
            }
        } catch (Exception ignored) {
            // Some phones don't support the threads query: fall back below.
        }

        // Fallback: group SMS by thread
        Map<Long, Conv> byThread = new LinkedHashMap<>();
        try (Cursor s = cr.query(Telephony.Sms.CONTENT_URI,
                new String[]{"thread_id", "address", "body", "date", "read"}, null, null, "date DESC")) {
            if (s != null) {
                int n = 0;
                while (s.moveToNext() && n++ < 20000) {
                    long tid = s.getLong(0);
                    Conv v = byThread.get(tid);
                    if (v == null) {
                        v = new Conv();
                        v.id = tid;
                        v.address = s.getString(1) == null ? "" : s.getString(1);
                        v.name = displayName(c, v.address);
                        v.snippet = s.getString(2) == null ? "" : s.getString(2);
                        v.date = s.getLong(3);
                        byThread.put(tid, v);
                    }
                    if (s.getInt(4) == 0) v.unread = true;
                }
            }
        } catch (Exception ignored) { }
        out.addAll(byThread.values());
        return out;
    }

    // ---------------- Messages in a conversation ----------------

    static List<Msg> messages(Context c, long threadId) {
        List<Msg> out = new ArrayList<>();
        ContentResolver cr = c.getContentResolver();
        String[] args = {String.valueOf(threadId)};
        String[] proj = {"_id", "address", "body", "date", "type", "status"};
        Cursor s = null;
        try {
            s = cr.query(Telephony.Sms.CONTENT_URI, proj, "thread_id=?", args, "date DESC LIMIT 600");
        } catch (Exception e) {
            try { s = cr.query(Telephony.Sms.CONTENT_URI, proj, "thread_id=?", args, "date DESC"); } catch (Exception ignored) { }
        }
        if (s != null) {
            try {
                int n = 0;
                while (s.moveToNext() && n++ < 600) {
                    Msg m = new Msg();
                    m.id = s.getLong(0);
                    m.address = s.getString(1) == null ? "" : s.getString(1);
                    m.body = s.getString(2) == null ? "" : s.getString(2);
                    m.date = s.getLong(3);
                    m.type = s.getInt(4);
                    m.status = s.isNull(5) ? -1 : s.getInt(5);
                    out.add(m);
                }
            } finally {
                s.close();
            }
        }

        // Picture/group messages (MMS): show text and first image, read-only
        try (Cursor mm = cr.query(Uri.parse("content://mms"), new String[]{"_id", "date", "msg_box"},
                "thread_id=?", args, "date DESC")) {
            if (mm != null) {
                int n = 0;
                while (mm.moveToNext() && n++ < 200) {
                    Msg m = new Msg();
                    m.mms = true;
                    m.id = mm.getLong(0);
                    m.date = mm.getLong(1) * 1000L;
                    m.type = mm.getInt(2) == 2 ? TYPE_SENT : TYPE_INBOX;
                    try (Cursor p = cr.query(Uri.parse("content://mms/part"), new String[]{"_id", "ct", "text"},
                            "mid=?", new String[]{String.valueOf(m.id)}, null)) {
                        StringBuilder text = new StringBuilder();
                        if (p != null) while (p.moveToNext()) {
                            String ct = p.getString(1);
                            if ("text/plain".equals(ct) && p.getString(2) != null) text.append(p.getString(2));
                            else if (ct != null && ct.startsWith("image/") && m.imagePartId == 0) m.imagePartId = p.getLong(0);
                        }
                        m.body = text.toString();
                    } catch (Exception ignored) { }
                    out.add(m);
                }
            }
        } catch (Exception ignored) { }

        Collections.sort(out, (a, b) -> Long.compare(a.date, b.date));
        return out;
    }

    static String addressOfThread(Context c, long threadId) {
        try (Cursor s = c.getContentResolver().query(Telephony.Sms.CONTENT_URI, new String[]{"address"},
                "thread_id=? AND address IS NOT NULL", new String[]{String.valueOf(threadId)}, "date DESC")) {
            if (s != null && s.moveToFirst()) return s.getString(0);
        } catch (Exception ignored) { }
        return "";
    }

    static long threadFor(Context c, String address) {
        try {
            return Telephony.Threads.getOrCreateThreadId(c, address);
        } catch (Exception e) {
            return 0;
        }
    }

    // ---------------- Writing (works when SpendSense is the default SMS app) ----------------

    static Uri insertInbox(Context c, String address, String body, long date, long dateSent) {
        ContentValues v = new ContentValues();
        v.put("address", address);
        v.put("body", body);
        v.put("date", date);
        v.put("date_sent", dateSent);
        v.put("read", 0);
        v.put("seen", 0);
        v.put("type", TYPE_INBOX);
        try {
            return c.getContentResolver().insert(Telephony.Sms.Inbox.CONTENT_URI, v);
        } catch (Exception e) {
            return null;
        }
    }

    static Uri insertOutbox(Context c, String address, String body, long threadId) {
        ContentValues v = new ContentValues();
        v.put("address", address);
        v.put("body", body);
        v.put("date", System.currentTimeMillis());
        v.put("read", 1);
        v.put("seen", 1);
        v.put("type", TYPE_OUTBOX);
        if (threadId > 0) v.put("thread_id", threadId);
        try {
            return c.getContentResolver().insert(Telephony.Sms.CONTENT_URI, v);
        } catch (Exception e) {
            return null;
        }
    }

    static void setType(Context c, Uri msg, int type) {
        if (msg == null) return;
        ContentValues v = new ContentValues();
        v.put("type", type);
        try { c.getContentResolver().update(msg, v, null, null); } catch (Exception ignored) { }
    }

    static void markRead(Context c, long threadId) {
        ContentValues v = new ContentValues();
        v.put("read", 1);
        v.put("seen", 1);
        try {
            c.getContentResolver().update(Telephony.Sms.CONTENT_URI, v, "thread_id=? AND read=0",
                    new String[]{String.valueOf(threadId)});
        } catch (Exception ignored) { }
    }

    static boolean deleteMessage(Context c, Msg m) {
        Uri u = Uri.parse((m.mms ? "content://mms/" : "content://sms/") + m.id);
        try { return c.getContentResolver().delete(u, null, null) > 0; } catch (Exception e) { return false; }
    }

    static boolean deleteConversation(Context c, long threadId) {
        try {
            return c.getContentResolver().delete(
                    Uri.parse("content://mms-sms/conversations/" + threadId), null, null) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------- Names ----------------

    static boolean canReadContacts(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    /** Contact name, a cleaned-up business sender ID (VM-HDFCBK-S -> HDFCBK), or the number. */
    static String displayName(Context c, String address) {
        if (address == null || address.isEmpty()) return "Unknown";
        String cached = nameCache.get(address);
        if (cached != null) return cached;
        String name = null;
        if (isBusiness(address)) {
            String[] p = address.toUpperCase(Locale.ROOT).split("-");
            name = p.length >= 2 ? p[1] : address.toUpperCase(Locale.ROOT);
        } else if (canReadContacts(c)) {
            try (Cursor k = c.getContentResolver().query(
                    Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address)),
                    new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
                if (k != null && k.moveToFirst()) name = k.getString(0);
            } catch (Exception ignored) { }
        }
        if (name == null) name = address;
        nameCache.put(address, name);
        return name;
    }

    static boolean isBusiness(String address) {
        return address != null && address.matches(".*[A-Za-z].*");
    }

    static void clearNameCache() {
        nameCache.clear();
    }
}
