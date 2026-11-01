// Copyright (C) 2026 Project Gamers
// SPDX-License-Identifier: GPL-3.0-or-later
package com.projectgamers.splitgate;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

/**
 * Arayuzun animasyon yardimcilari. Kullanici kapatmissa ya da telefonun animasyon olcegi 0 ise
 * hicbir sey animasyonlanmaz; her sey son degerine aninda gecer.
 */
final class Motion {
    private Motion() {}

    static final String KEY_ANIM = "animations";

    private static boolean enabled = true;
    private static final ArgbEvaluator ARGB = new ArgbEvaluator();

    /**
     * Tercihi, telefonun animasyon olcegini ve Pil Tasarrufu modunu yeniden okur (acilista ve her onResume'da).
     * Pil Tasarrufu aciksa animasyonlar kendiliginden kapanir.
     */
    static void refresh(Context c, boolean userWants) {
        float scale = 1f;
        try {
            scale = Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        } catch (RuntimeException ignore) {
        }
        boolean saver = false;
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            saver = pm != null && pm.isPowerSaveMode();
        } catch (RuntimeException ignore) {
        }
        enabled = userWants && scale > 0f && !saver;
    }

    static boolean enabled() {
        return enabled;
    }

    /**
     * Surekli animasyonlu gorunumler 30 kare/sn istesin (Android 15+). Ekran 120 Hz ise her kare pencerenin
     * tamamini yeniden cizdirir; bu, pil icin animasyonun en pahali kismidir.
     */
    static void lowFrameRate(View v) {
        if (android.os.Build.VERSION.SDK_INT >= 35) v.setRequestedFrameRate(30f);
    }

    static long ms(long d) {
        return enabled ? d : 0;
    }

    static int mix(int from, int to, float t) {
        return (Integer) ARGB.evaluate(Math.max(0f, Math.min(1f, t)), from, to);
    }

    /**
     * Gorunumdeki animasyonu keser ve yarida kalmis konumu duzeltir. Yalnizca cancel() cagrilirsa, ornegin
     * "enter" yarida kesildiginde gorunum kaymis ya da soluk kalirdi.
     */
    private static void settle(View v) {
        v.animate().cancel();
        v.setTranslationY(0f);
        v.setAlpha(1f);
    }

    /** Gorunumu asagidan yumusakca yukari kaydirarak belirtir. */
    static void enter(final View v, long delay, float risePx) {
        if (!enabled) return;
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(risePx);
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(340)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        v.setAlpha(1f);
                        v.setTranslationY(0f);
                        // ViewPropertyAnimator bu ayarlari saklar: sonraki animate() cagrilari (basma, yazi gecisi) devralmasin
                        v.animate().setStartDelay(0).setInterpolator(null);
                    }
                }).start();
    }

    /** Basinca hafifce kuculur, birakinca yaylanarak doner. Tiklamayi engellemez. */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    static void pressable(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                if (!enabled) return false;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).setInterpolator(new DecelerateInterpolator()).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(2.4f)).start();
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }

    /** Yazi degisince eskisi solar, yenisi belirir; ayni yazi tekrar gelirse dokunmaz. */
    static void swap(final TextView t, final CharSequence text) {
        // Etikette, solma bitince yazilacak bekleyen yazi saklanir: gecis sirasinda eski yazi gelirse yine de dogru yazi kalir.
        boolean pending = t.getTag() instanceof CharSequence;
        if (!pending && TextUtils.equals(t.getText(), text)) return;
        t.animate().cancel();
        t.animate().setStartDelay(0).setInterpolator(null);
        t.setTranslationY(0f); // acilis animasyonu yarida kesildiyse baslik kaymis kalmasin
        if (!enabled || !t.isShown()) {
            t.setTag(null);
            t.setAlpha(1f);
            t.setText(text);
            return;
        }
        t.setTag(text);
        t.animate().alpha(0f).setDuration(100).withEndAction(new Runnable() {
            @Override
            public void run() {
                t.setText(text);
                t.setTag(null);
                t.animate().alpha(1f).setDuration(180).start();
            }
        }).start();
    }

    /** Yazi rengini yumusakca degistirir. */
    static void textColor(final TextView t, int to) {
        int from = t.getCurrentTextColor();
        if (from == to) return;
        if (!enabled || !t.isShown()) {
            t.setTextColor(to);
            return;
        }
        ValueAnimator a = ValueAnimator.ofObject(ARGB, from, to);
        a.setDuration(260);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                t.setTextColor((Integer) va.getAnimatedValue());
            }
        });
        a.start();
    }

    /** Dolgu rengini yumusakca degistirir (secili / secili degil). */
    static void fill(final GradientDrawable g, final View host, int from, int to) {
        if (from == to) return;
        if (!enabled || host == null || !host.isShown()) {
            g.setColor(to);
            return;
        }
        ValueAnimator a = ValueAnimator.ofObject(ARGB, from, to);
        a.setDuration(220);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                g.setColor((Integer) va.getAnimatedValue());
            }
        });
        a.start();
    }

    /** Secim yapilinca kisa bir "pop". */
    static void pop(View v) {
        if (!enabled) return;
        settle(v);
        v.setScaleX(0.94f);
        v.setScaleY(0.94f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new OvershootInterpolator(3f)).start();
    }

    /** Hata / dikkat: yana kisa salinim. */
    static void shake(final View v, float amplitudePx) {
        if (!enabled) return;
        settle(v);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(420);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                float t = (Float) va.getAnimatedValue();
                v.setTranslationX((float) (Math.sin(t * Math.PI * 6) * (1f - t) * amplitudePx));
            }
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                v.setTranslationX(0f);
            }
        });
        a.start();
    }

    /**
     * Sayi gosterir. Bilerek animasyonsuzdur: sayi her saniye degisir, her karede setText yerlesimi yeniden
     * hesaplatir ve pencerenin tamami yeniden cizilir; bu, pili hizla tuketir. Ayni deger tekrar gelirse dokunmaz.
     */
    static final class Counter {
        interface Format {
            String text(long v);
        }

        private final TextView view;
        private final Format format;
        private long shown = Long.MIN_VALUE;

        Counter(TextView view, Format format) {
            this.view = view;
            this.format = format;
        }

        void set(long target) {
            if (target == shown) return;
            shown = target;
            view.setText(format.text(target));
        }
    }

    /** Govdeyi yukseklik animasyonuyla acar / kapatir; kapaliyken GONE olur. */
    static void setOpen(final View body, boolean open) {
        Object old = body.getTag();
        if (old instanceof ValueAnimator) ((ValueAnimator) old).cancel();
        final ViewGroup.LayoutParams lp = body.getLayoutParams();
        if (!enabled || !body.isLaidOut()) {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            body.setLayoutParams(lp);
            body.setAlpha(1f);
            body.setVisibility(open ? View.VISIBLE : View.GONE);
            return;
        }
        final int start;
        final int end;
        if (open) {
            View parent = (View) body.getParent();
            int w = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
            // GONE iken getHeight() eski bir degeri tutabilir; yalnizca gorunuyorsa (yarim kalmis animasyon) oradan basla
            start = body.getVisibility() == View.VISIBLE ? body.getHeight() : 0;
            body.setVisibility(View.VISIBLE);
            body.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            end = body.getMeasuredHeight();
        } else {
            start = body.getHeight();
            end = 0;
        }
        lp.height = start;
        body.setLayoutParams(lp);
        final boolean opening = open;
        ValueAnimator a = ValueAnimator.ofInt(start, end);
        a.setDuration(240);
        a.setInterpolator(new DecelerateInterpolator(1.4f));
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                lp.height = (Integer) va.getAnimatedValue();
                body.setLayoutParams(lp);
                body.setAlpha(opening ? va.getAnimatedFraction() : 1f - va.getAnimatedFraction() * 0.6f);
            }
        });
        a.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                body.setTag(null);
                if (cancelled) return;
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                body.setLayoutParams(lp);
                body.setAlpha(1f);
                body.setVisibility(opening ? View.VISIBLE : View.GONE);
            }
        });
        body.setTag(a);
        a.start();
    }
}
