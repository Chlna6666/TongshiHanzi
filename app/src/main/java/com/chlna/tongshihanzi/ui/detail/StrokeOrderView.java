/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.ui.detail;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import androidx.annotation.Nullable;
import androidx.core.graphics.PathParser;
import com.chlna.tongshihanzi.R;
import com.chlna.tongshihanzi.data.dictionary.StrokeEntity;
import com.google.android.material.color.MaterialColors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.json.JSONArray;

/**
 * Offline vector stroke-order renderer.
 *
 * <p>The complete glyph is drawn as a low-opacity guide. Completed strokes are filled with the
 * Material primary colour, while the current stroke follows its median brush path and fades into
 * the final vector shape. Parsed source paths are cached because SVG parsing is substantially more
 * expensive than drawing transformed Android {@link Path} instances.</p>
 */
public final class StrokeOrderView extends View {
    public interface StepListener {
        void onStepChanged(int index, String name);
    }

    private static final long STROKE_DURATION_MS = 560L;
    private static final long BETWEEN_STROKES_MS = 110L;
    private static final LruCache<String, List<RawStroke>> PATH_CACHE = new LruCache<>(48);

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint completedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint brushPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF glyphBox = new RectF();
    private final List<RawStroke> rawStrokes = new ArrayList<>();
    private final List<DrawStroke> drawStrokes = new ArrayList<>();

    private String character = "";
    private int currentStroke = -1;
    private float strokeProgress;
    private StepListener listener;
    private ValueAnimator animator;
    private boolean geometryDirty = true;
    private boolean vectorReady;

    public StrokeOrderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        setFocusable(true);
        setContentDescription("点击播放笔顺动画");
        setOnClickListener(view -> startAnimation());
    }

    public void setData(String value, List<StrokeEntity> values) {
        stopAnimation();
        character = value == null ? "" : value;
        rawStrokes.clear();

        List<StrokeEntity> sorted = values == null
                ? Collections.emptyList() : new ArrayList<>(values);
        sorted.sort(Comparator.comparingInt(stroke -> stroke.strokeIndex));
        String cacheKey = buildCacheKey(character, sorted);
        List<RawStroke> cached = PATH_CACHE.get(cacheKey);
        if (cached != null) {
            rawStrokes.addAll(cached);
        } else {
            for (StrokeEntity stroke : sorted) rawStrokes.add(parseStroke(stroke));
            PATH_CACHE.put(cacheKey, Collections.unmodifiableList(new ArrayList<>(rawStrokes)));
        }

        vectorReady = !rawStrokes.isEmpty();
        for (RawStroke stroke : rawStrokes) {
            if (stroke.fill == null || stroke.median == null) {
                vectorReady = false;
                break;
            }
        }
        currentStroke = -1;
        strokeProgress = 0f;
        geometryDirty = true;
        updateAccessibilityText();
        notifyStep();
        invalidate();
    }

    public void setStepListener(StepListener value) {
        listener = value;
        notifyStep();
    }

    public void startAnimation() {
        if (!vectorReady || rawStrokes.isEmpty()) {
            currentStroke = -1;
            strokeProgress = 0f;
            notifyStep();
            invalidate();
            return;
        }
        stopAnimatorOnly();
        currentStroke = 0;
        strokeProgress = 0f;
        animateCurrentStroke();
    }

    public void stopAnimation() {
        removeCallbacks(this::advanceToNextStroke);
        stopAnimatorOnly();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        geometryDirty = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        calculateGlyphBox();
        drawGrid(canvas);
        if (geometryDirty) rebuildGeometry();

        if (vectorReady && !drawStrokes.isEmpty()) {
            drawVectorGlyph(canvas);
        } else {
            drawFallbackGlyph(canvas);
        }
    }

    private void animateCurrentStroke() {
        if (currentStroke < 0 || currentStroke >= rawStrokes.size()) return;
        notifyStep();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(STROKE_DURATION_MS);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            strokeProgress = (float) valueAnimator.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override public void onAnimationEnd(Animator animation) {
                if (cancelled) return;
                strokeProgress = 1f;
                invalidate();
                postDelayed(StrokeOrderView.this::advanceToNextStroke, BETWEEN_STROKES_MS);
            }
        });
        animator.start();
    }

    private void advanceToNextStroke() {
        currentStroke++;
        strokeProgress = 0f;
        if (currentStroke >= rawStrokes.size()) {
            currentStroke = rawStrokes.size();
            notifyStep();
            updateAccessibilityText();
            invalidate();
            return;
        }
        animateCurrentStroke();
    }

    private void stopAnimatorOnly() {
        if (animator != null) {
            animator.cancel();
            animator.removeAllListeners();
            animator = null;
        }
    }

    private void calculateGlyphBox() {
        float inset = Math.max(12f, Math.min(getWidth(), getHeight()) * 0.045f);
        float size = Math.max(0f, Math.min(getWidth(), getHeight()) - inset * 2f);
        glyphBox.set(
                (getWidth() - size) / 2f,
                (getHeight() - size) / 2f,
                (getWidth() + size) / 2f,
                (getHeight() + size) / 2f);
    }

    private void drawGrid(Canvas canvas) {
        int outline = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOutline);
        gridPaint.setColor(outline);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(Math.max(1f, glyphBox.width() / 420f));
        gridPaint.setAlpha(72);
        canvas.drawRect(glyphBox, gridPaint);
        canvas.drawLine(glyphBox.centerX(), glyphBox.top,
                glyphBox.centerX(), glyphBox.bottom, gridPaint);
        canvas.drawLine(glyphBox.left, glyphBox.centerY(),
                glyphBox.right, glyphBox.centerY(), gridPaint);
        gridPaint.setAlpha(42);
        canvas.drawLine(glyphBox.left, glyphBox.top,
                glyphBox.right, glyphBox.bottom, gridPaint);
        canvas.drawLine(glyphBox.right, glyphBox.top,
                glyphBox.left, glyphBox.bottom, gridPaint);
    }

    private void drawVectorGlyph(Canvas canvas) {
        int primary = MaterialColors.getColor(this, R.attr.colorPrimary);
        int onSurface = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurface);

        guidePaint.setStyle(Paint.Style.FILL);
        guidePaint.setColor(onSurface);
        guidePaint.setAlpha(38);
        for (DrawStroke stroke : drawStrokes) canvas.drawPath(stroke.fill, guidePaint);

        completedPaint.setStyle(Paint.Style.FILL);
        completedPaint.setColor(primary);
        completedPaint.setAlpha(255);
        int completeCount = Math.min(currentStroke, drawStrokes.size());
        for (int i = 0; i < completeCount; i++) {
            canvas.drawPath(drawStrokes.get(i).fill, completedPaint);
        }

        if (currentStroke >= 0 && currentStroke < drawStrokes.size()) {
            DrawStroke active = drawStrokes.get(currentStroke);
            activeFillPaint.setStyle(Paint.Style.FILL);
            activeFillPaint.setColor(primary);
            activeFillPaint.setAlpha(Math.round(35f + 205f * strokeProgress));
            canvas.drawPath(active.fill, activeFillPaint);

            PathMeasure measure = new PathMeasure(active.median, false);
            Path segment = new Path();
            float remaining = Math.max(0f, Math.min(1f, strokeProgress));
            do {
                float contourLength = measure.getLength();
                float contourFraction = remaining;
                measure.getSegment(0f, contourLength * contourFraction, segment, true);
                if (remaining < 1f) break;
            } while (measure.nextContour());

            brushPaint.setStyle(Paint.Style.STROKE);
            brushPaint.setStrokeCap(Paint.Cap.ROUND);
            brushPaint.setStrokeJoin(Paint.Join.ROUND);
            brushPaint.setStrokeWidth(Math.max(8f, glyphBox.width() * 0.055f));
            brushPaint.setColor(primary);
            brushPaint.setAlpha(255);
            canvas.drawPath(segment, brushPaint);
        }
    }

    private void drawFallbackGlyph(Canvas canvas) {
        int onSurface = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurface);
        fallbackPaint.setColor(onSurface);
        fallbackPaint.setAlpha(54);
        fallbackPaint.setTextAlign(Paint.Align.CENTER);
        fallbackPaint.setTextSize(glyphBox.width() * 0.72f);
        fallbackPaint.setTypeface(android.graphics.Typeface.create(
                "sans-serif", android.graphics.Typeface.NORMAL));
        Paint.FontMetrics metrics = fallbackPaint.getFontMetrics();
        canvas.drawText(character, glyphBox.centerX(),
                glyphBox.centerY() - (metrics.ascent + metrics.descent) / 2f,
                fallbackPaint);
    }

    private void rebuildGeometry() {
        drawStrokes.clear();
        geometryDirty = false;
        if (!vectorReady || glyphBox.isEmpty()) return;

        RectF sourceBounds = new RectF();
        RectF temporary = new RectF();
        boolean first = true;
        for (RawStroke stroke : rawStrokes) {
            stroke.fill.computeBounds(temporary, true);
            if (first) {
                sourceBounds.set(temporary);
                first = false;
            } else {
                sourceBounds.union(temporary);
            }
        }
        if (first || sourceBounds.width() <= 0f || sourceBounds.height() <= 0f) return;

        float contentInset = glyphBox.width() * 0.08f;
        RectF target = new RectF(
                glyphBox.left + contentInset,
                glyphBox.top + contentInset,
                glyphBox.right - contentInset,
                glyphBox.bottom - contentInset);
        float scale = Math.min(
                target.width() / sourceBounds.width(),
                target.height() / sourceBounds.height());
        float offsetX = target.left + (target.width() - sourceBounds.width() * scale) / 2f;
        float offsetY = target.top + (target.height() - sourceBounds.height() * scale) / 2f;
        Matrix transform = new Matrix();
        transform.setValues(new float[] {
                scale, 0f, offsetX - sourceBounds.left * scale,
                0f, -scale, offsetY + sourceBounds.bottom * scale,
                0f, 0f, 1f
        });

        for (RawStroke raw : rawStrokes) {
            Path fill = new Path();
            Path median = new Path();
            raw.fill.transform(transform, fill);
            raw.median.transform(transform, median);
            drawStrokes.add(new DrawStroke(raw.name, fill, median));
        }
    }

    private RawStroke parseStroke(StrokeEntity entity) {
        try {
            Path fill = entity.pathData.trim().isEmpty()
                    ? null : PathParser.createPathFromPathData(entity.pathData);
            Path median = parseMedian(entity.medianData);
            return new RawStroke(entity.name, fill, median);
        } catch (RuntimeException error) {
            return new RawStroke(entity.name, null, null);
        }
    }

    @Nullable
    private static Path parseMedian(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            JSONArray points = new JSONArray(json);
            if (points.length() < 2) return null;
            Path path = new Path();
            for (int i = 0; i < points.length(); i++) {
                JSONArray point = points.getJSONArray(i);
                float x = (float) point.getDouble(0);
                float y = (float) point.getDouble(1);
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            return path;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void notifyStep() {
        if (listener == null) return;
        if (!vectorReady) {
            listener.onStepChanged(-1, "暂无矢量笔顺数据，仅显示字形参考");
        } else if (currentStroke >= rawStrokes.size()) {
            listener.onStepChanged(rawStrokes.size(), "播放完成，点击可重新播放");
        } else if (currentStroke < 0) {
            listener.onStepChanged(-1, "点击开始播放 · 共 " + rawStrokes.size() + " 笔");
        } else {
            listener.onStepChanged(currentStroke, rawStrokes.get(currentStroke).name);
        }
    }

    private void updateAccessibilityText() {
        if (!vectorReady) {
            setContentDescription(character + "，暂无矢量笔顺动画");
        } else if (currentStroke >= rawStrokes.size()) {
            setContentDescription(character + "笔顺播放完成，点击重新播放");
        } else {
            setContentDescription(character + "，点击播放共" + rawStrokes.size() + "笔的笔顺动画");
        }
    }

    private static String buildCacheKey(String character, List<StrokeEntity> strokes) {
        int hash = 1;
        for (StrokeEntity stroke : strokes) {
            hash = 31 * hash + stroke.pathData.hashCode();
            hash = 31 * hash + stroke.medianData.hashCode();
        }
        return character + ':' + hash;
    }

    private static final class RawStroke {
        final String name;
        @Nullable final Path fill;
        @Nullable final Path median;

        RawStroke(String name, @Nullable Path fill, @Nullable Path median) {
            this.name = name == null || name.trim().isEmpty() ? "未命名笔画" : name;
            this.fill = fill;
            this.median = median;
        }
    }

    private static final class DrawStroke {
        final String name;
        final Path fill;
        final Path median;

        DrawStroke(String name, Path fill, Path median) {
            this.name = name;
            this.fill = fill;
            this.median = median;
        }
    }
}
