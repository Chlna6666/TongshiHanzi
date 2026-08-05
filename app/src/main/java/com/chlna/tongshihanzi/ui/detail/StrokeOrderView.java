/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.ui.detail;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.R;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

public final class StrokeOrderView extends View {
    public interface StepListener {
        void onStepChanged(int index, String name);
    }

    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progress = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> strokes = new ArrayList<>();

    private String character = "";
    private int step = -1;
    private StepListener listener;

    private final Runnable advance = new Runnable() {
        @Override
        public void run() {
            if (strokes.isEmpty()) {
                return;
            }
            step++;
            if (step >= strokes.size()) {
                step = 0;
            }
            notifyStep();
            invalidate();
            handler.postDelayed(this, 650L);
        }
    };

    public StrokeOrderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        setContentDescription("点击播放笔顺");
        setOnClickListener(view -> startAnimation());
    }

    public void setData(String value, List<String> names) {
        stopAnimation();
        character = value == null ? "" : value;
        strokes.clear();
        if (names != null) {
            strokes.addAll(names);
        }
        step = strokes.isEmpty() ? -1 : 0;
        notifyStep();
        invalidate();
    }

    public void setStepListener(StepListener value) {
        listener = value;
        notifyStep();
    }

    public void startAnimation() {
        handler.removeCallbacks(advance);
        step = -1;
        handler.post(advance);
    }

    public void stopAnimation() {
        handler.removeCallbacks(advance);
    }

    private void notifyStep() {
        if (listener != null) {
            String name = step >= 0 && step < strokes.size()
                    ? strokes.get(step)
                    : "暂无笔顺数据";
            listener.onStepChanged(step, name);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int primary = MaterialColors.getColor(this, R.attr.colorPrimary);
        int outline = MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorOutline
        );
        int onSurface = MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorOnSurface
        );

        float width = getWidth();
        float height = getHeight();
        float size = Math.min(width, height) - 16f;
        float left = (width - size) / 2f;
        float top = (height - size) / 2f;
        RectF box = new RectF(left, top, left + size, top + size);

        grid.setColor(outline);
        grid.setAlpha(100);
        grid.setStrokeWidth(2f);
        grid.setStyle(Paint.Style.STROKE);
        canvas.drawRect(box, grid);
        canvas.drawLine(box.centerX(), box.top, box.centerX(), box.bottom, grid);
        canvas.drawLine(box.left, box.centerY(), box.right, box.centerY(), grid);
        grid.setStrokeWidth(1f);
        canvas.drawLine(box.left, box.top, box.right, box.bottom, grid);
        canvas.drawLine(box.right, box.top, box.left, box.bottom, grid);

        glyph.setColor(onSurface);
        glyph.setTextAlign(Paint.Align.CENTER);
        glyph.setTextSize(size * .68f);
        glyph.setTypeface(android.graphics.Typeface.create(
                "sans-serif",
                android.graphics.Typeface.NORMAL
        ));
        glyph.setAlpha(step < 0 ? 90 : 230);
        Paint.FontMetrics metrics = glyph.getFontMetrics();
        canvas.drawText(
                character,
                box.centerX(),
                box.centerY() - (metrics.ascent + metrics.descent) / 2f,
                glyph
        );

        if (!strokes.isEmpty()) {
            progress.setColor(primary);
            progress.setStyle(Paint.Style.FILL);
            float fraction = step < 0 ? 0f : (step + 1f) / strokes.size();
            canvas.drawRoundRect(
                    new RectF(
                            box.left,
                            box.bottom - 7f,
                            box.left + box.width() * fraction,
                            box.bottom
                    ),
                    4f,
                    4f,
                    progress
            );
        }
    }
}
