package in.appbucket.spendsense;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Simple month-by-month bar chart. The highlighted bar is drawn in the brand colour. */
public class BarChart extends View {
    private String[] labels = new String[0];
    private double[] values = new double[0];
    private int highlight = -1;
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public BarChart(Context c) {
        super(c);
        label.setTextAlign(Paint.Align.CENTER);
        label.setColor(Ui.MUTED);
        value.setTextAlign(Paint.Align.CENTER);
        value.setColor(Ui.INK);
    }

    public void setData(String[] labels, double[] values, int highlight) {
        this.labels = labels;
        this.values = values;
        this.highlight = highlight;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = values.length;
        if (n == 0) return;
        float d = getResources().getDisplayMetrics().density;
        label.setTextSize(12 * d);
        value.setTextSize(11 * d);
        float top = 20 * d, bottom = getHeight() - 22 * d;
        float slot = getWidth() / (float) n;
        float bw = Math.min(slot * 0.55f, 36 * d);
        double max = 0;
        for (double v : values) max = Math.max(max, v);
        for (int i = 0; i < n; i++) {
            float cx = slot * i + slot / 2;
            float h = max <= 0 ? 0 : (float) (values[i] / max) * (bottom - top);
            r.set(cx - bw / 2, bottom - Math.max(h, 3 * d), cx + bw / 2, bottom);
            bar.setColor(i == highlight ? Ui.PRIMARY : 0xFFC7CBF5);
            canvas.drawRoundRect(r, 8 * d, 8 * d, bar);
            canvas.drawText(labels[i], cx, getHeight() - 5 * d, label);
            if (values[i] > 0) canvas.drawText(Format.shortMoney(values[i]), cx, r.top - 5 * d, value);
        }
    }
}
