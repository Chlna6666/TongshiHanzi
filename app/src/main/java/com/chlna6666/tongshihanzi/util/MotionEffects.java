/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.util;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.preference.PreferenceManager;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Centralized, interruptible motion for the View-based UI.
 *
 * <p>Every transition is driven by a damped spring and can be retargeted while it is running.
 * There are deliberately no idle, floating, breathing or infinite animations.</p>
 */
public final class MotionEffects {
    public static final String PREFERENCE_KEY = "motion_mode";

    private static final float FULL_STIFFNESS = 720f;
    private static final float FULL_DAMPING = 0.76f;
    private static final float REDUCED_STIFFNESS = 900f;
    private static final float REDUCED_DAMPING = 0.92f;

    private static final WeakHashMap<View, MotionState> STATES = new WeakHashMap<>();
    private static final WeakHashMap<View, Long> ITEM_KEYS = new WeakHashMap<>();
    private static final Set<View> PRESS_INSTALLED =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<View> LIFECYCLE_INSTALLED =
            Collections.newSetFromMap(new WeakHashMap<>());

    private MotionEffects() {
    }

    /** Animates a newly created page once. Removing the page cancels all active springs. */
    public static void enterPage(View root) {
        MotionLevel level = level(root.getContext());
        if (level == MotionLevel.OFF) {
            reset(root);
            return;
        }

        root.setAlpha(level == MotionLevel.REDUCED ? 0.9f : 0.82f);
        root.setTranslationY(level == MotionLevel.REDUCED ? 0f : dp(root, 16f));
        root.setScaleX(level == MotionLevel.REDUCED ? 1f : 0.985f);
        root.setScaleY(level == MotionLevel.REDUCED ? 1f : 0.985f);
        root.postOnAnimation(() -> {
            if (!root.isAttachedToWindow()) {
                return;
            }
            springTo(root, DynamicAnimation.ALPHA, 1f, level);
            springTo(root, DynamicAnimation.TRANSLATION_Y, 0f, level);
            springTo(root, DynamicAnimation.SCALE_X, 1f, level);
            springTo(root, DynamicAnimation.SCALE_Y, 1f, level);
        });
    }

    /** Animates a result card only when a holder is rebound to a different stable item. */
    public static void enterListItem(View item, long stableKey, int adapterPosition) {
        Long previous = ITEM_KEYS.put(item, stableKey);
        if (previous != null && previous.longValue() == stableKey) {
            return;
        }

        MotionLevel level = level(item.getContext());
        if (level == MotionLevel.OFF) {
            reset(item);
            return;
        }

        item.setAlpha(level == MotionLevel.REDUCED ? 0.92f : 0.74f);
        item.setTranslationY(level == MotionLevel.REDUCED
                ? 0f : dp(item, Math.min(18f, 8f + Math.max(0, adapterPosition) * 1.5f)));
        item.setScaleX(level == MotionLevel.REDUCED ? 1f : 0.98f);
        item.setScaleY(level == MotionLevel.REDUCED ? 1f : 0.98f);
        item.postOnAnimation(() -> {
            if (!item.isAttachedToWindow()) {
                return;
            }
            springTo(item, DynamicAnimation.ALPHA, 1f, level);
            springTo(item, DynamicAnimation.TRANSLATION_Y, 0f, level);
            springTo(item, DynamicAnimation.SCALE_X, 1f, level);
            springTo(item, DynamicAnimation.SCALE_Y, 1f, level);
        });
    }

    /** Adds touch compression and spring release without consuming the original click event. */
    public static void installPressFeedback(View view) {
        if (!PRESS_INSTALLED.add(view)) {
            return;
        }
        state(view);
        view.setOnTouchListener((target, event) -> {
            if (!target.isEnabled()) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    setPressed(target, true);
                    break;
                case MotionEvent.ACTION_MOVE:
                    boolean inside = event.getX() >= 0f && event.getX() <= target.getWidth()
                            && event.getY() >= 0f && event.getY() <= target.getHeight();
                    setPressed(target, inside);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    setPressed(target, false);
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    public static void installPressFeedbackForChildren(ViewGroup group) {
        for (int index = 0; index < group.getChildCount(); index++) {
            installPressFeedback(group.getChildAt(index));
        }
    }

    /**
     * Shows or hides the bottom navigation without changing the page layout geometry.
     * A new destination can reverse the running spring immediately.
     */
    public static void setBottomBarVisible(View bar, boolean visible, int hiddenOffset) {
        MotionState motion = state(bar);
        motion.bottomTargetVisible = visible;
        MotionLevel level = level(bar.getContext());

        boolean wasHidden = bar.getVisibility() != View.VISIBLE;
        if (visible && wasHidden) {
            bar.setVisibility(View.VISIBLE);
            bar.setAlpha(0f);
        }

        if (bar.getHeight() == 0) {
            if (!visible) {
                cancel(motion);
                bar.setAlpha(0f);
                bar.setVisibility(View.GONE);
                return;
            }
            bar.post(() -> {
                if (bar.isAttachedToWindow()) {
                    setBottomBarVisible(bar, true, hiddenOffset);
                }
            });
            return;
        }

        motion.bottomHiddenOffset = Math.max(hiddenOffset, bar.getHeight());
        if (level == MotionLevel.OFF) {
            cancel(motion);
            bar.setTranslationY(visible ? 0f : motion.bottomHiddenOffset);
            bar.setAlpha(visible ? 1f : 0f);
            bar.setVisibility(visible ? View.VISIBLE : View.GONE);
            return;
        }

        if (visible) {
            if (wasHidden || bar.getAlpha() <= 0.01f) {
                bar.setTranslationY(motion.bottomHiddenOffset);
            }
            springTo(bar, DynamicAnimation.TRANSLATION_Y, 0f, level);
            springTo(bar, DynamicAnimation.ALPHA, 1f, level);
            return;
        }

        if (bar.getVisibility() != View.VISIBLE) {
            return;
        }
        SpringAnimation translation = springTo(
                bar, DynamicAnimation.TRANSLATION_Y, motion.bottomHiddenOffset, level);
        springTo(bar, DynamicAnimation.ALPHA, 0f, level);
        if (!motion.bottomEndListenerInstalled) {
            motion.bottomEndListenerInstalled = true;
            translation.addEndListener((animation, canceled, value, velocity) -> {
                MotionState current = STATES.get(bar);
                if (!canceled && current != null && !current.bottomTargetVisible
                        && Math.abs(bar.getTranslationY() - current.bottomHiddenOffset) < 1.5f) {
                    bar.setVisibility(View.GONE);
                }
            });
        }
    }

    private static void setPressed(View view, boolean pressed) {
        MotionLevel level = level(view.getContext());
        if (level == MotionLevel.OFF) {
            view.setScaleX(1f);
            view.setScaleY(1f);
            return;
        }
        float target = pressed
                ? (level == MotionLevel.REDUCED ? 0.985f : 0.962f)
                : 1f;
        springTo(view, DynamicAnimation.SCALE_X, target, level);
        springTo(view, DynamicAnimation.SCALE_Y, target, level);
    }

    private static SpringAnimation springTo(
            View view,
            FloatPropertyCompat<View> property,
            float finalPosition,
            MotionLevel level
    ) {
        MotionState motion = state(view);
        SpringAnimation animation = motion.animation(view, property);
        float stiffness = level == MotionLevel.REDUCED ? REDUCED_STIFFNESS : FULL_STIFFNESS;
        float damping = level == MotionLevel.REDUCED ? REDUCED_DAMPING : FULL_DAMPING;
        SpringForce spring = animation.getSpring();
        if (spring == null) {
            spring = new SpringForce(finalPosition);
            animation.setSpring(spring);
        }
        spring.setStiffness(stiffness);
        spring.setDampingRatio(damping);
        animation.animateToFinalPosition(finalPosition);
        return animation;
    }

    private static MotionState state(View view) {
        MotionState existing = STATES.get(view);
        if (existing != null) {
            return existing;
        }
        MotionState created = new MotionState();
        STATES.put(view, created);
        if (LIFECYCLE_INSTALLED.add(view)) {
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View attached) {
                }

                @Override
                public void onViewDetachedFromWindow(View detached) {
                    MotionState detachedState = STATES.remove(detached);
                    if (detachedState != null) {
                        cancel(detachedState);
                    }
                    detached.setAlpha(1f);
                    detached.setTranslationY(0f);
                    detached.setScaleX(1f);
                    detached.setScaleY(1f);
                }
            });
        }
        return created;
    }

    private static void cancel(MotionState state) {
        state.cancel();
    }

    private static void reset(View view) {
        MotionState state = STATES.remove(view);
        if (state != null) {
            cancel(state);
        }
        view.setAlpha(1f);
        view.setTranslationY(0f);
        view.setScaleX(1f);
        view.setScaleY(1f);
    }

    private static MotionLevel level(Context context) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            return MotionLevel.OFF;
        }
        String value = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREFERENCE_KEY, "full");
        if ("off".equals(value)) {
            return MotionLevel.OFF;
        }
        if ("reduced".equals(value)) {
            return MotionLevel.REDUCED;
        }
        return MotionLevel.FULL;
    }

    private static float dp(View view, float value) {
        return value * view.getResources().getDisplayMetrics().density;
    }

    private enum MotionLevel {
        FULL,
        REDUCED,
        OFF
    }

    private static final class MotionState {
        private SpringAnimation alpha;
        private SpringAnimation translationY;
        private SpringAnimation scaleX;
        private SpringAnimation scaleY;
        private boolean bottomTargetVisible = true;
        private boolean bottomEndListenerInstalled;
        private float bottomHiddenOffset;

        SpringAnimation animation(View view, FloatPropertyCompat<View> property) {
            if (property == DynamicAnimation.ALPHA) {
                if (alpha == null) {
                    alpha = create(view, property, 0.003f);
                }
                return alpha;
            }
            if (property == DynamicAnimation.TRANSLATION_Y) {
                if (translationY == null) {
                    translationY = create(view, property, 0.5f);
                }
                return translationY;
            }
            if (property == DynamicAnimation.SCALE_X) {
                if (scaleX == null) {
                    scaleX = create(view, property, 0.001f);
                }
                return scaleX;
            }
            if (scaleY == null) {
                scaleY = create(view, property, 0.001f);
            }
            return scaleY;
        }

        void cancel() {
            cancel(alpha);
            cancel(translationY);
            cancel(scaleX);
            cancel(scaleY);
        }

        private static SpringAnimation create(
                View view,
                FloatPropertyCompat<View> property,
                float minimumVisibleChange
        ) {
            SpringAnimation animation = new SpringAnimation(view, property);
            animation.setMinimumVisibleChange(minimumVisibleChange);
            return animation;
        }

        private static void cancel(SpringAnimation animation) {
            if (animation != null && animation.isRunning()) {
                animation.cancel();
            }
        }
    }
}
