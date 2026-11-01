// Copyright (C) 2026 Project Gamers
// SPDX-License-Identifier: GPL-3.0-or-later
package com.projectgamers.splitgate;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/** Ince yatay cubuk: ya dolu oran (sonuc cubugu) ya da suresi belli olmayan is icin kayan parca. */
final class BarView extends View {
    private static final int TRACK = 0xFF1B2440;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private int color = 0xFF2EE6A8;
    private float fraction;
    private float phase;
    private boolean indeterminate;
    private ValueAnimator anim;

    BarView(Context c) {
        super(c);
    }

    void setColor(int c) {
        color = c;
        invalidate();
    }

    /** Orani (0..1) animasyonla doldurur. */
    void fillTo(float target, long delay) {
        indeterminate = false;
        stopAnim();
        final float to = Math.max(0f, Math.min(1f, target));
        if (!Motion.enabled()) {
            fraction = to;
            invalidate();
            return;
        }
        fraction = 0f;
        anim = ValueAnimator.ofFloat(0f, to);
        anim.setStartDelay(delay);
        anim.setDuration(600);
        anim.setInterpolator(new DecelerateInterpolator(1.8f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                fraction = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
    }

    void setIndeterminate(boolean on) {
        indeterminate = on;
        stopAnim();
        if (on && Motion.enabled() && isShown()) startSlide();
        invalidate();
    }

    private void startSlide() {
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1300);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                phase = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
    }

    private void stopAnim() {
        if (anim != null) {
            anim.cancel();
            anim = null;
        }
    }

    // Gizliyken (arka plan, kapali kart) kayan animasyon calismasin.
    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (!indeterminate) return;
        stopAnim();
        if (isVisible && Motion.enabled()) startSlide();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnim();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), rad = h / 2f;
        p.setColor(TRACK);
        r.set(0, 0, w, h);
        c.drawRoundRect(r, rad, rad, p);
        p.setColor(color);
        if (indeterminate) {
            float chunk = w * 0.32f;
            float x = -chunk + (w + chunk) * phase;
            c.save();
            c.clipRect(0, 0, w, h);
            r.set(x, 0, x + chunk, h);
            c.drawRoundRect(r, rad, rad, p);
            c.restore();
        } else if (fraction > 0f) {
            r.set(0, 0, Math.max(h, w * fraction), h);
            c.drawRoundRect(r, rad, rad, p);
        }
    }
}
