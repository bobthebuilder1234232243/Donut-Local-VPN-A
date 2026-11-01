// Copyright (C) 2026 Project Gamers
// SPDX-License-Identifier: GPL-3.0-or-later
package com.projectgamers.splitgate;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Uygulama ici kisa bildirim: alttan kayarak gelir, kendiliginden gider. Metin her zaman duz yazi olarak gosterilir. */
final class Toaster {
    private final TextView view;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float density;
    private boolean up;

    private final Runnable hide = new Runnable() {
        @Override
        public void run() {
            leave();
        }
    };

    Toaster(Context c, FrameLayout host) {
        density = c.getResources().getDisplayMetrics().density;
        view = new TextView(c);
        view.setTextColor(0xFFEAF0FF);
        view.setTextSize(13);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(18), dp(12), dp(18), dp(12));
        view.setMaxWidth((int) (c.getResources().getDisplayMetrics().widthPixels * 0.9f));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xF01B2440);
        g.setCornerRadius(dp(16));
        g.setStroke(dp(1), 0xFF2EE6A8);
        view.setBackground(g);
        view.setElevation(dp(8));
        view.setVisibility(View.GONE);
        view.setClickable(false);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        host.addView(view, lp);
    }

    private int dp(int v) {
        return (int) (v * density + 0.5f);
    }

    void setBottomInset(int px) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        lp.bottomMargin = px + dp(24);
        view.setLayoutParams(lp);
    }

    void show(CharSequence text, boolean longer) {
        handler.removeCallbacks(hide);
        view.animate().cancel();
        view.setText(text);
        view.setVisibility(View.VISIBLE);
        view.announceForAccessibility(text);
        if (Motion.enabled()) {
            view.setAlpha(up ? view.getAlpha() : 0f);
            view.setTranslationY(up ? view.getTranslationY() : dp(28));
            view.animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(new DecelerateInterpolator(1.6f)).start();
        } else {
            view.setAlpha(1f);
            view.setTranslationY(0f);
        }
        up = true;
        handler.postDelayed(hide, longer ? 3800 : 2200);
    }

    private void leave() {
        up = false;
        if (!Motion.enabled()) {
            view.setVisibility(View.GONE);
            return;
        }
        view.animate().alpha(0f).translationY(dp(20)).setDuration(220).setInterpolator(new AccelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (!up) view.setVisibility(View.GONE);
                    }
                }).start();
    }

    /** Etkinlik kapanirken bekleyen zamanlayicilari temizler. */
    void dispose() {
        handler.removeCallbacks(hide);
        view.animate().cancel();
    }
}
