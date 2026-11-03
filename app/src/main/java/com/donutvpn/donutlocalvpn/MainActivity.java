// Copyright (C) 2026 Donut VPN
// SPDX-License-Identifier: GPL-3.0-or-later
package com.donutvpn.donutlocalvpn;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The whole user interface: one big Start/Stop button, a status label and the credits.
 */
public class MainActivity extends Activity {
    private static final int REQ_VPN = 1;
    private static final int REQ_NOTIFICATIONS = 2;
    private static final String HOME_URL = "https://techperson1234.github.io/Donut-VPN/";

    private static final int BG = 0xFF0B1020;
    private static final int TEXT = 0xFFEAF0FF;
    private static final int MUTED = 0xFF8A95B3;
    private static final int ACCENT = 0xFF2EE6A8;
    private static final int AMBER = 0xFFFFC857;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerButton power;
    private TextView status;
    private boolean ticking;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            if (ticking) handler.postDelayed(this, 500);
        }
    };

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LocalCore.init(); // the local core is ready before the user touches anything
        Motion.refresh(this, true);
        if (Build.VERSION.SDK_INT < 35) {
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
        }
        setContentView(buildScreen());
        askNotificationPermission();
        refresh();
    }

    private View buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(BG);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(dp(24), dp(28), dp(24), dp(22));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo);
        header.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text(getString(R.string.app_name), 24, TEXT, true));
        titles.addView(text(getString(R.string.tagline), 12, MUTED, false));
        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titlesLp.leftMargin = dp(12);
        header.addView(titles, titlesLp);
        column.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        column.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        power = new PowerButton(this);
        power.setId(R.id.power_button);
        power.setContentDescription(getString(R.string.toggle_cd));
        power.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggle();
            }
        });
        LinearLayout.LayoutParams powerLp = new LinearLayout.LayoutParams(dp(220), dp(220));
        powerLp.gravity = Gravity.CENTER_HORIZONTAL;
        column.addView(power, powerLp);

        status = text(getString(R.string.status_disconnected), 24, MUTED, true);
        status.setId(R.id.status_label);
        status.setGravity(Gravity.CENTER);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(20);
        column.addView(status, statusLp);

        column.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        TextView credits = text(getString(R.string.credits), 11, MUTED, false);
        credits.setGravity(Gravity.CENTER);
        credits.setLineSpacing(dp(3), 1f);
        credits.setPadding(0, dp(16), 0, 0);
        credits.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openHomepage();
            }
        });
        column.addView(credits, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int l, t, r, b;
                if (Build.VERSION.SDK_INT >= 30) {
                    Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    l = i.left;
                    t = i.top;
                    r = i.right;
                    b = i.bottom;
                } else {
                    l = insets.getSystemWindowInsetLeft();
                    t = insets.getSystemWindowInsetTop();
                    r = insets.getSystemWindowInsetRight();
                    b = insets.getSystemWindowInsetBottom();
                }
                v.setPadding(l, t, r, b);
                return insets;
            }
        });
        return root;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    // ------------------------------------------------------------------ button

    private void toggle() {
        if (BypassVpnService.isOn()) {
            startService(new Intent(this, BypassVpnService.class).setAction(BypassVpnService.ACTION_STOP));
            handler.postDelayed(tick, 100);
            return;
        }
        Intent prepare = VpnService.prepare(this);
        if (prepare != null) {
            startActivityForResult(prepare, REQ_VPN);
        } else {
            startTunnel();
        }
    }

    private void startTunnel() {
        Intent i = new Intent(this, BypassVpnService.class).setAction(BypassVpnService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        handler.postDelayed(tick, 100);
        refresh();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN && resultCode == RESULT_OK) startTunnel();
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
    }

    private void openHomepage() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(HOME_URL)));
        } catch (RuntimeException ignore) {
            // no browser installed
        }
    }

    private void refresh() {
        if (power == null || status == null) return;
        if (BypassVpnService.starting) {
            status.setText(R.string.status_starting);
            status.setTextColor(AMBER);
            power.setLook(PowerButton.Look.BUSY);
        } else if (BypassVpnService.running) {
            status.setText(R.string.status_connected);
            status.setTextColor(ACCENT);
            power.setLook(PowerButton.Look.WORKING);
        } else {
            status.setText(R.string.status_disconnected);
            status.setTextColor(MUTED);
            power.setLook(PowerButton.Look.OFF);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Motion.refresh(this, true);
        ticking = true;
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        ticking = false;
        handler.removeCallbacks(tick);
        super.onPause();
    }
}
