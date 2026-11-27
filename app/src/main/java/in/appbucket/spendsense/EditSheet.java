package in.appbucket.spendsense;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.InputType;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

/** Bottom sheet to add a transaction or edit/delete one. */
final class EditSheet {

    private EditSheet() {}

    static void show(MainActivity a, Expense existing) {
        final boolean isNew = existing == null || existing.id == 0;
        final Expense e = existing == null ? new Expense() : existing;
        if (existing == null) {
            e.ts = System.currentTimeMillis();
            e.debit = true;
            e.mode = "Cash";
            e.merchant = "";
            e.category = "Food";
        }
        final long[] ts = {e.ts};
        final boolean[] debit = {e.debit};
        final String[] category = {e.category};

        BottomSheetDialog sheet = new BottomSheetDialog(a);
        LinearLayout col = Ui.v(a);
        col.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), Ui.dp(a, 24));

        // Handle bar
        View handle = new View(a);
        handle.setBackground(Ui.round(a, Ui.LINE, 3));
        LinearLayout.LayoutParams hl = Ui.lp(Ui.dp(a, 40), Ui.dp(a, 5));
        hl.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        hl.bottomMargin = Ui.dp(a, 12);
        col.addView(handle, hl);

        col.addView(Ui.text(a, isNew ? "Add transaction" : "Transaction", 20, Ui.INK, true));

        // Type toggle
        LinearLayout typeRow = Ui.h(a);
        typeRow.setPadding(0, Ui.dp(a, 12), 0, 0);
        Chip exp = typeChip(a, "Expense");
        Chip inc = typeChip(a, "Income");
        exp.setChecked(debit[0]);
        inc.setChecked(!debit[0]);
        exp.setOnClickListener(v -> { debit[0] = true; exp.setChecked(true); inc.setChecked(false); });
        inc.setOnClickListener(v -> { debit[0] = false; inc.setChecked(true); exp.setChecked(false); });
        typeRow.addView(exp);
        typeRow.addView(inc);
        col.addView(typeRow);

        // Amount
        EditText amount = field(a, "0");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setTextSize(30);
        amount.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        if (!isNew || e.amount > 0) amount.setText(MainActivity.plain(e.amount));
        col.addView(label(a, "Amount (₹)"));
        col.addView(amount, Ui.lp(-1, -2));

        EditText merchant = field(a, "e.g. Swiggy, Rahul, Petrol pump");
        merchant.setText(e.merchant);
        col.addView(label(a, "Paid to / received from"));
        col.addView(merchant, Ui.lp(-1, -2));

        // Category chips
        col.addView(label(a, "Category"));
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        ChipGroup cg = new ChipGroup(a);
        cg.setSingleLine(true);
        cg.setSingleSelection(true);
        List<String> cats = new ArrayList<>(Arrays.asList(Categorizer.CATEGORIES));
        if (!cats.contains("Income")) cats.add("Income");
        if (!cats.contains(category[0])) cats.add(category[0]);
        for (String c : cats) {
            Chip ch = new Chip(a);
            ch.setText(Categorizer.emoji(c) + "  " + c);
            ch.setCheckable(true);
            ch.setCheckedIconVisible(false);
            int[][] states = {{android.R.attr.state_checked}, {}};
            ch.setChipBackgroundColor(new ColorStateList(states, new int[]{Ui.PRIMARY_SOFT, 0xFFF3F4F6}));
            ch.setChipStrokeColor(new ColorStateList(states, new int[]{Ui.PRIMARY, 0x00000000}));
            ch.setChipStrokeWidth(Ui.dp(a, 1.5f));
            ch.setTextColor(Ui.INK);
            ch.setChecked(c.equals(category[0]));
            ch.setOnClickListener(v -> category[0] = c);
            cg.addView(ch);
        }
        hs.addView(cg);
        col.addView(hs);

        // Date
        col.addView(label(a, "Date"));
        TextView date = Ui.text(a, "📅  " + Format.fullDate(ts[0]), 15, Ui.INK, false);
        date.setBackground(Ui.round(a, 0xFFF3F4F6, 12));
        date.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
        date.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(ts[0]);
            new DatePickerDialog(a, (dp, y, m, d) -> {
                Calendar n = Calendar.getInstance();
                n.setTimeInMillis(ts[0]);
                n.set(y, m, d);
                ts[0] = n.getTimeInMillis();
                date.setText("📅  " + Format.fullDate(ts[0]));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        col.addView(date, Ui.lp(-1, -2));

        EditText note = field(a, "Add a note (optional)");
        note.setText(e.note);
        col.addView(label(a, "Note"));
        col.addView(note, Ui.lp(-1, -2));

        CheckBox applyAll = new CheckBox(a);
        applyAll.setText("Always use this category for " + (e.merchant.isEmpty() ? "this merchant" : e.merchant));
        applyAll.setTextColor(Ui.INK);
        applyAll.setButtonTintList(ColorStateList.valueOf(Ui.PRIMARY));
        if (!isNew && !e.merchant.isEmpty() && !"Unknown".equals(e.merchant)) {
            applyAll.setPadding(0, Ui.dp(a, 4), 0, 0);
            col.addView(applyAll);
        }

        if (!isNew || !e.smsBody.isEmpty()) {
            LinearLayout info = Ui.v(a);
            info.setBackground(Ui.round(a, 0xFFF8F9FC, 12));
            info.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
            StringBuilder meta = new StringBuilder();
            if (!e.mode.isEmpty()) meta.append(e.mode);
            if (!e.bank.isEmpty()) meta.append(meta.length() > 0 ? " · " : "").append(e.bank);
            if (!e.account.isEmpty()) meta.append(" ••").append(e.account);
            if (meta.length() > 0) info.addView(Ui.text(a, meta, 13, Ui.INK, true));
            if (!e.smsBody.isEmpty()) {
                TextView sms = Ui.text(a, e.smsBody, 12, Ui.MUTED, false);
                sms.setPadding(0, Ui.dp(a, 4), 0, 0);
                info.addView(sms);
            }
            if (info.getChildCount() > 0) {
                LinearLayout.LayoutParams il = Ui.lp(-1, -2);
                il.topMargin = Ui.dp(a, 14);
                col.addView(info, il);
            }
        }

        LinearLayout buttons = Ui.h(a);
        LinearLayout.LayoutParams bl = Ui.lp(-1, -2);
        bl.topMargin = Ui.dp(a, 20);
        if (!isNew) {
            View del = Ui.button(a, "Delete", 0xFFFEE2E2, Ui.RED);
            del.setOnClickListener(v -> new MaterialAlertDialogBuilder(a)
                    .setTitle("Delete this transaction?")
                    .setMessage(Format.money(e.amount) + " · " + e.merchant + "\n\nIt won't come back when SMS are checked again.")
                    .setPositiveButton("Delete", (d, w) -> {
                        a.db.hide(e.id);
                        sheet.dismiss();
                        a.refresh();
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0, Ui.dp(a, 52), 1);
            dl.rightMargin = Ui.dp(a, 10);
            buttons.addView(del, dl);
        }
        View save = Ui.button(a, "Save", Ui.PRIMARY, Color.WHITE);
        save.setOnClickListener(v -> {
            double amt;
            try {
                amt = Double.parseDouble(amount.getText().toString().replace(",", "").trim());
            } catch (NumberFormatException ex) {
                amount.setError("Enter an amount");
                return;
            }
            if (amt <= 0) {
                amount.setError("Enter an amount");
                return;
            }
            e.amount = amt;
            e.merchant = merchant.getText().toString().trim();
            if (e.merchant.isEmpty()) e.merchant = "Unknown";
            e.debit = debit[0];
            e.category = (!e.debit && isNew && !"Income".equals(category[0]) && "Food".equals(category[0]))
                    ? "Income" : category[0];
            e.note = note.getText().toString().trim();
            e.ts = ts[0];
            if (isNew) {
                if (e.smsKey == null || e.smsKey.isEmpty()) e.smsKey = "manual-" + UUID.randomUUID();
                a.db.insertIfNew(e);
                a.db.finishReview(e.smsKey);
            } else {
                a.db.update(e);
                if (e.debit) a.db.rememberMerchant(e.merchant, e.category, applyAll.isChecked());
            }
            sheet.dismiss();
            a.month.setTimeInMillis(e.ts);
            a.month.set(Calendar.DAY_OF_MONTH, 1);
            a.refresh();
            Toast.makeText(a, "Saved", Toast.LENGTH_SHORT).show();
        });
        buttons.addView(save, new LinearLayout.LayoutParams(0, Ui.dp(a, 52), 1));
        col.addView(buttons, bl);

        ScrollView sv = new ScrollView(a);
        sv.addView(col);
        sheet.setContentView(sv);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.show();
    }

    private static Chip typeChip(MainActivity a, String text) {
        Chip c = new Chip(a);
        c.setText(text);
        c.setCheckable(true);
        c.setCheckedIconVisible(false);
        int[][] states = {{android.R.attr.state_checked}, {}};
        c.setChipBackgroundColor(new ColorStateList(states, new int[]{Ui.PRIMARY, 0xFFF3F4F6}));
        c.setTextColor(new ColorStateList(states, new int[]{0xFFFFFFFF, Ui.INK}));
        c.setChipStrokeWidth(0);
        LinearLayout.LayoutParams lp = Ui.lp(-2, -2);
        lp.rightMargin = Ui.dp(a, 8);
        c.setLayoutParams(lp);
        return c;
    }

    private static TextView label(MainActivity a, String s) {
        TextView t = Ui.text(a, s, 13, Ui.MUTED, true);
        t.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 6));
        return t;
    }

    private static EditText field(MainActivity a, String hint) {
        EditText t = new EditText(a);
        t.setHint(hint);
        t.setTextSize(16);
        t.setTextColor(Ui.INK);
        t.setBackground(Ui.round(a, 0xFFF3F4F6, 12));
        t.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
        return t;
    }
}
