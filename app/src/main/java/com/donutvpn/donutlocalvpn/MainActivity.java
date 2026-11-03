// Copyright (C) 2026 Donut VPN
// SPDX-License-Identifier: GPL-3.0-or-later
package com.donutvpn.donutlocalvpn;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/**
 * The main screen: one big Start/Stop button, the current session addresses and a way into the
 * local settings. Everything the app does stays on the phone.
 */
public class MainActivity extends Activity {
    static final String EXTRA_SKIP_AUTO_CONNECT = "skip_auto_connect";

    private static final int REQ_VPN = 1;
    private static final int REQ_NOTIFICATIONS = 2;
    private static final String HOME_URL = "https://techperson1234.github.io/Donut-VPN/";

    private static final int BG = 0xFF0B1020;
    private static final int CARD = 0xFF141B2D;
    private static final int CARD_STROKE = 0xFF24314D;
    private static final int TEXT = 0xFFEAF0FF;
    private static final int MUTED = 0xFF8A95B3;
    private static final int ACCENT = 0xFF2EE6A8;
    private static final int AMBER = 0xFFFFC857;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerButton power;
    private TextView status;
    private View sessionCard;
    private TextView sessionDevice;
    private TextView sessionTunnel;
    private TextView sessionTime;
    private boolean ticking;
    private boolean autoConnectAfterPermission;

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
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
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
        boolean autoConnect = savedInstanceState == null
                && !getIntent().getBooleanExtra(EXTRA_SKIP_AUTO_CONNECT, false)
                && AppSettings.autoConnect(this)
                && !BypassVpnService.isOn();
        askNotificationPermission(autoConnect);
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
        TextView tagline = text(getString(R.string.tagline), 12, MUTED, false);
        tagline.setMaxLines(2);
        tagline.setLineSpacing(dp(2), 1f);
        titles.addView(tagline);
        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titlesLp.leftMargin = dp(12);
        header.addView(titles, titlesLp);

        ImageButton settings = new ImageButton(this);
        settings.setId(R.id.settings_button);
        settings.setImageResource(R.drawable.ic_settings);
        settings.setBackgroundColor(0x00000000);
        settings.setColorFilter(TEXT);
        settings.setContentDescription(getString(R.string.settings_cd));
        settings.setPadding(dp(10), dp(10), dp(10), dp(10));
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        header.addView(settings, new LinearLayout.LayoutParams(dp(44), dp(44)));

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

        column.addView(buildSessionCard());

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

    private View buildSessionCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(CARD);
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), CARD_STROKE);
        card.setBackground(background);

        TextView title = text(getString(R.string.session_details), 12, MUTED, true);
        card.addView(title);

        LinearLayout addresses = new LinearLayout(this);
        addresses.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams addressesLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addressesLp.topMargin = dp(10);

        LinearLayout deviceCell = detailCell(R.string.device_ip);
        sessionDevice = (TextView) deviceCell.getChildAt(1);
        sessionDevice.setId(R.id.session_device_ip);
        addresses.addView(deviceCell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout tunnelCell = detailCell(R.string.tunnel_ip);
        sessionTunnel = (TextView) tunnelCell.getChildAt(1);
        sessionTunnel.setId(R.id.session_tunnel_ip);
        addresses.addView(tunnelCell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        card.addView(addresses, addressesLp);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams timeRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeRowLp.topMargin = dp(12);
        timeRow.addView(text(getString(R.string.session_time), 12, MUTED, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sessionTime = text(getString(R.string.value_none), 14, TEXT, true);
        sessionTime.setId(R.id.session_time);
        sessionTime.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeRow.addView(sessionTime);
        card.addView(timeRow, timeRowLp);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(18);
        card.setLayoutParams(cardLp);
        sessionCard = card;
        card.setVisibility(View.GONE);
        return card;
    }

    private LinearLayout detailCell(int labelId) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.addView(text(getString(labelId), 11, MUTED, false));
        TextView value = text(getString(R.string.value_none), 16, TEXT, true);
        value.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueLp.topMargin = dp(4);
        cell.addView(value, valueLp);
        return cell;
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
            ConnectionLog.add(getString(R.string.log_permission_requested));
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
        if (requestCode == REQ_VPN) {
            if (resultCode == RESULT_OK) {
                startTunnel();
            } else {
                ConnectionLog.add(getString(R.string.log_permission_denied));
            }
        }
    }

    private void askNotificationPermission(boolean connectAfter) {
        if (Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            if (connectAfter) scheduleAutoConnect();
            return;
        }
        autoConnectAfterPermission = connectAfter;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_NOTIFICATIONS) return;
        boolean connectAfter = autoConnectAfterPermission;
        autoConnectAfterPermission = false;
        if (connectAfter) scheduleAutoConnect();
    }

    private void scheduleAutoConnect() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing() && !BypassVpnService.isOn()) toggle();
            }
        }, 250);
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
        refreshSession();
    }

    private void refreshSession() {
        boolean connected = BypassVpnService.running && BypassVpnService.sessionStartedAt > 0;
        if (sessionCard != null) {
            int visibility = connected ? View.VISIBLE : View.GONE;
            if (sessionCard.getVisibility() != visibility) sessionCard.setVisibility(visibility);
        }
        if (sessionDevice == null || sessionTunnel == null || sessionTime == null) return;
        if (!connected) {
            setIfChanged(sessionDevice, getString(R.string.value_none));
            setIfChanged(sessionTunnel, getString(R.string.value_none));
            setIfChanged(sessionTime, getString(R.string.value_none));
            return;
        }

        String device = BypassVpnService.activeDeviceIp;
        if (device == null) device = AppSettings.deviceIp(this);
        String tunnel = BypassVpnService.activeTunnelIp;
        if (tunnel == null) tunnel = AppSettings.tunnelIp(this);
        setIfChanged(sessionDevice, device);
        setIfChanged(sessionTunnel, tunnel);

        long seconds = Math.max(0,
                (SystemClock.elapsedRealtime() - BypassVpnService.sessionStartedAt) / 1000);
        setIfChanged(sessionTime, formatDuration(seconds));
    }

    private void setIfChanged(TextView view, String value) {
        if (!TextUtils.equals(view.getText(), value)) view.setText(value);
    }

    private String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
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
