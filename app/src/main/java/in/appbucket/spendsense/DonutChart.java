package in.appbucket.spendsense;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Ring chart of spending by category, with the total in the middle. */
public class DonutChart extends View {
    private final List<Float> values = new ArrayList<>();
    private final List<Integer> colors = new ArrayList<>();
    private String center = "", caption = "";
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    public DonutChart(Context c) {
        super(c);
        ring.setStyle(Paint.Style.STROKE);
        big.setTextAlign(Paint.Align.CENTER);
        big.setColor(Ui.INK);
        big.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        small.setTextAlign(Paint.Align.CENTER);
        small.setColor(Ui.MUTED);
    }

    public void setData(List<Float> v, List<Integer> c, String center, String caption) {
        values.clear();
        values.addAll(v);
        colors.clear();
        colors.addAll(c);
        this.center = center;
        this.caption = caption;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        float stroke = size * 0.14f;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        box.set(cx - size / 2 + stroke / 2, cy - size / 2 + stroke / 2,
                cx + size / 2 - stroke / 2, cy + size / 2 - stroke / 2);
        ring.setStrokeWidth(stroke);

        float total = 0;
        for (float f : values) total += f;
        if (total <= 0) {
            ring.setColor(Ui.LINE);
            canvas.drawArc(box, 0, 360, false, ring);
        } else {
            float start = -90;
            float gap = values.size() > 1 ? 2f : 0f;
            for (int i = 0; i < values.size(); i++) {
                float sweep = values.get(i) / total * 360f;
                ring.setColor(colors.get(i));
                canvas.drawArc(box, start + gap / 2, Math.max(0.5f, sweep - gap), false, ring);
                start += sweep;
            }
        }
        big.setTextSize(size * 0.13f);
        small.setTextSize(size * 0.075f);
        canvas.drawText(center, cx, cy + size * 0.02f, big);
        canvas.drawText(caption, cx, cy + size * 0.13f, small);
    }
}
