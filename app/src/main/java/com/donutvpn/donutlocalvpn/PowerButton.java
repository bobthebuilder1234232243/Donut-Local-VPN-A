// Copyright (C) 2026 Donut VPN
// SPDX-License-Identifier: GPL-3.0-or-later
package com.donutvpn.donutlocalvpn;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

/**
 * Yuvarlak guc dugmesi. Hali renk ve harekete cevrilir:
 * OFF gri ve durgun, BUSY donen yay, WORKING nabiz atan yesil halkalar.
 * Basinca hafifce kuculur, birakinca yaylanir.
 *
 * Pil: surekli hareketler (halka, yay, nefes) tek tek goruntulere ayrilmistir ve yalnizca olcek / saydamlik /
 * donme ozelligi animasyonlanir. Bu ozellikler yeniden cizim gerektirmez; ana is parcacigi her karede cizim yapmaz.
 * Cizim yalnizca renk degisirken (yarim saniye) olur.
 */
final class PowerButton extends FrameLayout {
    enum Look { OFF, BUSY, WORKING }

    private static final int OFF = 0xFF4A5675;
    private static final int ON = 0xFF2EE6A8;
    private static final int AMBER = 0xFFFFC857;
    private static final int DISC = 0xFF141B2D;
    private static final int DISC_HI = 0xFF1E2842;

    private final Glow glow;
    private final Ring[] rings = new Ring[2];
    private final Disc disc;
    private final Spinner spinner;
    private Look look = Look.OFF;
    private int color = OFF;
    private float lit;
    private ValueAnimator colorAnim;
    private final ObjectAnimator[] ringAnims = new ObjectAnimator[2];
    private ObjectAnimator spinAnim;
    private boolean windowVisible = true; // uygulama arka plandayken (isShown() true kalir) donguler calismasin
    // Dikkat cekmek icin nabiz / nefes yalnizca bir sure surer, sonra sakin bir hale gecer: ekran acik kalsa da
    // her karede pencere yeniden cizilmesin (pil). Durum degisince ya da uygulama tekrar acilinca yeniden baslar.
    private static final long CALM_AFTER_MS = 6000;
    private static final long BUSY_MAX_MS = 30000;
    private final Runnable calm = new Runnable() {
        @Override
        public void run() {
            stopLoops();
        }
    };

    PowerButton(Context c) {
        super(c);
        setClickable(true);
        setFocusable(true);
        setClipChildren(false);
        glow = new Glow(c);
        disc = new Disc(c);
        spinner = new Spinner(c);
        addView(glow, fill());
        for (int i = 0; i < rings.length; i++) {
            rings[i] = new Ring(c);
            rings[i].setVisibility(View.GONE);
            addView(rings[i], fill());
        }
        addView(disc, fill());
        addView(spinner, fill());
        spinner.setVisibility(View.GONE);
    }

    private static LayoutParams fill() {
        return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Override
    protected void onMeasure(int w, int h) {
        // kare: genislik ve yukseklikten kucugu
        int size = Math.min(MeasureSpec.getSize(w), MeasureSpec.getSize(h));
        super.onMeasure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY));
    }

    Look look() {
        return look;
    }

    void setLook(Look v) {
        if (look == v) return;
        look = v;
        final int from = color;
        final int to = colorOf(v);
        final float litFrom = lit;
        final float litTo = v == Look.OFF ? 0f : 1f;
        if (colorAnim != null) colorAnim.cancel();
        if (Motion.enabled() && isShown()) {
            colorAnim = ValueAnimator.ofFloat(0f, 1f);
            colorAnim.setDuration(380);
            colorAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    float t = (Float) a.getAnimatedValue();
                    paint(Motion.mix(from, to, t), litFrom + (litTo - litFrom) * t);
                }
            });
            colorAnim.start();
        } else {
            paint(to, litTo);
        }
        spinner.setVisibility(v == Look.BUSY ? View.VISIBLE : View.GONE);
        disc.busy = v == Look.BUSY;
        disc.invalidate();
        syncLoop();
    }

    private void paint(int c, float l) {
        color = c;
        lit = l;
        glow.set(c, l);
        disc.set(c, l);
        spinner.set(c);
        for (Ring r : rings) r.set(c);
    }

    private static int colorOf(Look l) {
        switch (l) {
            case WORKING: return ON;
            case BUSY: return AMBER;
            default: return OFF;
        }
    }

    /** Animasyon ayari degisince (ya da ekran gorununce) donguleri yeniden degerlendirir. */
    void refresh() {
        syncLoop();
    }

    private boolean animating() {
        return Motion.enabled() && windowVisible && isShown();
    }

    private void stopLoops() {
        removeCallbacks(calm);
        for (int i = 0; i < ringAnims.length; i++) {
            if (ringAnims[i] != null) {
                ringAnims[i].cancel();
                ringAnims[i] = null;
            }
        }
        if (spinAnim != null) {
            spinAnim.cancel();
            spinAnim = null;
        }
        glow.setAlpha(1f);
        spinner.setRotation(0f);
        for (Ring r : rings) {
            r.setVisibility(View.GONE);
            r.setScaleX(1f);
            r.setScaleY(1f);
            r.setAlpha(0.45f);
        }
    }

    private void syncLoop() {
        stopLoops();
        if (!animating()) return;
        if (look == Look.WORKING) {
            for (int i = 0; i < rings.length; i++) {
                rings[i].setVisibility(View.VISIBLE);
                ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(rings[i],
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.39f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.39f),
                        PropertyValuesHolder.ofFloat(View.ALPHA, 0.45f, 0f));
                a.setDuration(2400);
                a.setRepeatCount(ValueAnimator.INFINITE);
                a.setInterpolator(new LinearInterpolator());
                a.setStartDelay(i * 1200L);
                a.start();
                ringAnims[i] = a;
                Motion.lowFrameRate(rings[i]);
            }
            postDelayed(calm, CALM_AFTER_MS);
        } else if (look == Look.BUSY) {
            spinAnim = ObjectAnimator.ofFloat(spinner, View.ROTATION, 0f, 360f);
            spinAnim.setDuration(1100);
            spinAnim.setRepeatCount(ValueAnimator.INFINITE);
            spinAnim.setInterpolator(new LinearInterpolator());
            spinAnim.start();
            Motion.lowFrameRate(spinner);
            postDelayed(calm, BUSY_MAX_MS);
        }
    }

    // Gorunmezken (uygulama arka planda) donguleri durdur: pil ve islemci harcamasin.
    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        windowVisible = isVisible;
        syncLoop();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        syncLoop();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopLoops();
        if (colorAnim != null) colorAnim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (disc == null) return; // ust sinif kurulurken de cagrilabilir
        float target = isPressed() ? 0.93f : 1f;
        disc.animate().cancel();
        if (!Motion.enabled() || !isShown()) {
            disc.setScaleX(target);
            disc.setScaleY(target);
            return;
        }
        disc.animate().scaleX(target).scaleY(target).setDuration(target < 1f ? 90 : 300)
                .setInterpolator(target < 1f ? new LinearInterpolator() : new OvershootInterpolator(2.6f)).start();
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0x00FFFFFF);
    }

    /** Dis parlama: ic ice soluk daireler (gradyan nesnesi uretmeden). */
    private static final class Glow extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int color = OFF;
        private float lit;

        Glow(Context c) {
            super(c);
            p.setStyle(Paint.Style.FILL);
        }

        void set(int c, float l) {
            color = c;
            lit = l;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (lit <= 0.01f) return;
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(getWidth(), getHeight()) / 2f;
            p.setColor(withAlpha(color, (int) (8 * lit)));
            for (int i = 0; i < 8; i++) c.drawCircle(cx, cy, r * (1f - i * (0.45f / 7f)), p);
        }
    }

    /** Disk, halka ve guc simgesi. Yalnizca renk degisince yeniden cizilir. */
    private static final class Disc extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private int color = OFF;
        private float lit;
        boolean busy;
        private RadialGradient shader;
        private int shaderTop;
        private float shaderR;

        Disc(Context c) {
            super(c);
        }

        void set(int c, float l) {
            color = c;
            lit = l;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(getWidth(), getHeight()) / 2f;
            int top = Motion.mix(DISC_HI, Motion.mix(DISC, color, 0.20f), lit);
            if (shader == null || top != shaderTop || r != shaderR) {
                shader = new RadialGradient(cx, cy - r * 0.2f, r * 0.8f, top, DISC, Shader.TileMode.CLAMP);
                shaderTop = top;
                shaderR = r;
            }
            p.setStyle(Paint.Style.FILL);
            p.setShader(shader);
            c.drawCircle(cx, cy, r * 0.72f, p);
            p.setShader(null);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(r * 0.045f);
            p.setColor(busy ? withAlpha(color, 0x55) : color);
            c.drawCircle(cx, cy, r * 0.72f, p);

            // guc simgesi: ustte bosluklu yay + dikey cizgi
            float ir = r * 0.30f;
            p.setStrokeWidth(r * 0.07f);
            p.setColor(color);
            oval.set(cx - ir, cy - ir, cx + ir, cy + ir);
            c.drawArc(oval, -50f, 280f, false, p);
            c.drawLine(cx, cy - ir * 1.15f, cx, cy - ir * 0.1f, p);
        }
    }

    /** Yayilan halka: durgun bir daire; olcek ve saydamligi disaridan animasyonlanir. */
    private static final class Ring extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int color = ON;

        Ring(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
        }

        void set(int c) {
            color = c;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float r = Math.min(getWidth(), getHeight()) / 2f;
            p.setStrokeWidth(r * 0.03f);
            p.setColor(color);
            c.drawCircle(getWidth() / 2f, getHeight() / 2f, r * 0.72f, p);
        }
    }

    /** Bekleme yayi: halka uzerinde 100 derecelik parlak yay; donmesi disaridan animasyonlanir. */
    private static final class Spinner extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private int color = AMBER;

        Spinner(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        void set(int c) {
            color = c;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(getWidth(), getHeight()) / 2f;
            p.setStrokeWidth(r * 0.07f);
            p.setColor(color);
            float rr = r * 0.72f;
            oval.set(cx - rr, cy - rr, cx + rr, cy + rr);
            c.drawArc(oval, -90f, 100f, false, p);
        }
    }
}
