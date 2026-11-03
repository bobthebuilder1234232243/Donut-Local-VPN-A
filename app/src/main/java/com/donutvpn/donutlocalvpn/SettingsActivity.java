// Copyright (C) 2026 Donut VPN
// SPDX-License-Identifier: GPL-3.0-or-later
package com.donutvpn.donutlocalvpn;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Local settings: auto-connect, the two tunnel addresses, the UI language and the in-memory
 * connection log. Everything is stored in the app's private preferences on the device.
 */
public class SettingsActivity extends Activity {
    private static final int BG = 0xFF0B1020;
    private static final int CARD = 0xFF141B2D;
    private static final int CARD_STROKE = 0xFF24314D;
    private static final int INPUT = 0xFF0A0F1C;
    private static final int TEXT = 0xFFEAF0FF;
    private static final int MUTED = 0xFF8A95B3;
    private static final int ACCENT = 0xFF2EE6A8;
    private static final int ACCENT_DARK = 0xFF062218;
    private static final int SELECTED = 0xFF173B36;
    private static final int WARN = 0xFFFF6B7A;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private EditText deviceInput;
    private EditText tunnelInput;
    private TextView errorView;
    private TextView logView;
    private ScrollView logScroll;
    private String lastLog;
    private boolean logTicking;

    private final Runnable logTick = new Runnable() {
        @Override
        public void run() {
            refreshLog();
            if (logTicking) handler.postDelayed(this, 1500);
        }
    };

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT < 35) {
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
        }
        View root = buildScreen();
        setContentView(root);
        applyInsets(root);
        root.requestApplyInsets();
    }

    @Override
    protected void onStart() {
        super.onStart();
        logTicking = true;
        handler.removeCallbacks(logTick);
        refreshLog();
        handler.postDelayed(logTick, 1000);
    }

    @Override
    protected void onStop() {
        logTicking = false;
        handler.removeCallbacks(logTick);
        super.onStop();
    }

    // ------------------------------------------------------------------ layout

    private View buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(BG);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(20), dp(20), dp(20), dp(28));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setBackgroundColor(0x00000000);
        back.setColorFilter(TEXT);
        back.setContentDescription(getString(R.string.back));
        back.setPadding(dp(10), dp(10), dp(10), dp(10));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = text(getString(R.string.settings), 26, TEXT, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.leftMargin = dp(4);
        header.addView(title, titleLp);

        column.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        column.addView(connectionCard());
        column.addView(networkCard());
        column.addView(languageCard());
        column.addView(logCard());

        scroll.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(CARD, 18, CARD_STROKE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        card.setLayoutParams(lp);
        return card;
    }

    private TextView cardTitle(int stringId) {
        return text(getString(stringId), 16, TEXT, true);
    }

    private void addDescription(LinearLayout card, int stringId) {
        TextView description = text(getString(stringId), 12, MUTED, false);
        description.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        card.addView(description, lp);
    }

    private View connectionCard() {
        LinearLayout card = card();
        card.addView(cardTitle(R.string.connection));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, 0);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(getString(R.string.auto_connect), 15, TEXT, false));
        TextView description = text(getString(R.string.auto_connect_desc), 12, MUTED, false);
        description.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams descriptionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionLp.topMargin = dp(4);
        labels.addView(description, descriptionLp);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(AppSettings.autoConnect(this));
        toggle.setContentDescription(getString(R.string.auto_connect));
        toggle.setThumbTintList(ColorStateList.valueOf(ACCENT));
        toggle.setTrackTintList(ColorStateList.valueOf(0xFF33405E));
        toggle.setOnCheckedChangeListener((buttonView, checked) ->
                AppSettings.setAutoConnect(SettingsActivity.this, checked));
        LinearLayout.LayoutParams toggleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        toggleLp.leftMargin = dp(12);
        row.addView(toggle, toggleLp);

        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private View networkCard() {
        LinearLayout card = card();
        card.addView(cardTitle(R.string.network_config));
        addDescription(card, R.string.network_desc);

        card.addView(fieldLabel(R.string.device_ip));
        deviceInput = ipInput(AppSettings.deviceIp(this), getString(R.string.device_ip));
        card.addView(deviceInput);

        card.addView(fieldLabel(R.string.tunnel_ip));
        tunnelInput = ipInput(AppSettings.tunnelIp(this), getString(R.string.tunnel_ip));
        card.addView(tunnelInput);

        errorView = text("", 12, WARN, false);
        errorView.setVisibility(View.GONE);
        LinearLayout.LayoutParams errorLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        errorLp.topMargin = dp(8);
        card.addView(errorView, errorLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(14);

        TextView save = text(getString(R.string.save_network), 14, ACCENT_DARK, true);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dp(14), dp(12), dp(14), dp(12));
        save.setBackground(rounded(ACCENT, 12, 0));
        save.setClickable(true);
        save.setFocusable(true);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveNetwork();
            }
        });
        actions.addView(save, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView reset = text(getString(R.string.reset_defaults), 14, TEXT, false);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(dp(14), dp(12), dp(14), dp(12));
        reset.setBackground(rounded(CARD, 12, 0xFF33405E));
        reset.setClickable(true);
        reset.setFocusable(true);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetNetwork();
            }
        });
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resetLp.leftMargin = dp(8);
        actions.addView(reset, resetLp);
        card.addView(actions, actionsLp);

        TextView note = text(getString(R.string.network_applies), 11, MUTED, false);
        note.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = dp(10);
        card.addView(note, noteLp);
        return card;
    }

    private TextView fieldLabel(int stringId) {
        TextView label = text(getString(stringId), 12, MUTED, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        label.setLayoutParams(lp);
        return label;
    }

    private EditText ipInput(String value, String hint) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setTextColor(TEXT);
        input.setHintTextColor(0xFF55627F);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setBackground(rounded(INPUT, 12, CARD_STROKE));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        input.setLayoutParams(lp);
        return input;
    }

    private View languageCard() {
        LinearLayout card = card();
        card.addView(cardTitle(R.string.language));
        addDescription(card, R.string.language_desc);

        String[] tags = {"", "en", "es", "it", "pl", "ko", "zh-TW", "fr"};
        int[] names = {
                R.string.system_default,
                R.string.language_en,
                R.string.language_es,
                R.string.language_it,
                R.string.language_pl,
                R.string.language_ko,
                R.string.language_zh_tw,
                R.string.language_fr
        };
        String current = AppSettings.languageTag(this);
        for (int i = 0; i < tags.length; i++) {
            addLanguageRow(card, tags[i], getString(names[i]), current.equals(tags[i]), i == 0);
        }
        return card;
    }

    private void addLanguageRow(LinearLayout card, final String tag, String name, boolean selected, boolean first) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(selected ? rounded(SELECTED, 12, 0) : null);
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(name);

        TextView label = text(name, 15, TEXT, selected);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView check = text("\u2713", 18, ACCENT, true);
        check.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        row.addView(check);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tag.equals(AppSettings.languageTag(SettingsActivity.this))) return;
                AppSettings.setLanguageTag(SettingsActivity.this, tag);
                LocaleHelper.restart(SettingsActivity.this);
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(first ? 10 : 2);
        card.addView(row, lp);
    }

    private View logCard() {
        LinearLayout card = card();

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(cardTitle(R.string.connection_log),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView clear = text(getString(R.string.clear_log), 13, ACCENT, true);
        clear.setPadding(dp(8), dp(4), dp(8), dp(4));
        clear.setClickable(true);
        clear.setFocusable(true);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ConnectionLog.clear();
                lastLog = null;
                refreshLog();
            }
        });
        heading.addView(clear);
        card.addView(heading);
        addDescription(card, R.string.connection_log_desc);

        logScroll = new ScrollView(this);
        logScroll.setNestedScrollingEnabled(true);
        logScroll.setBackground(rounded(INPUT, 12, CARD_STROKE));
        logScroll.setPadding(dp(12), dp(10), dp(12), dp(10));
        logView = text("", 11, 0xFFB9C6E4, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setLineSpacing(dp(2), 1f);
        logScroll.addView(logView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams logLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(190));
        logLp.topMargin = dp(12);
        card.addView(logScroll, logLp);
        return card;
    }

    // ------------------------------------------------------------------ actions

    private void saveNetwork() {
        String device = deviceInput.getText().toString().trim();
        String tunnel = tunnelInput.getText().toString().trim();
        AppSettings.Validation result = AppSettings.validateNetwork(device, tunnel);
        if (result != AppSettings.Validation.OK) {
            errorView.setText(errorFor(result));
            errorView.setVisibility(View.VISIBLE);
            if (result == AppSettings.Validation.INVALID_DEVICE) {
                deviceInput.requestFocus();
            } else if (result == AppSettings.Validation.INVALID_TUNNEL) {
                tunnelInput.requestFocus();
            }
            return;
        }
        AppSettings.setNetwork(this, device, tunnel);
        errorView.setVisibility(View.GONE);
        hideKeyboard();
        Toast.makeText(this, R.string.network_saved, Toast.LENGTH_SHORT).show();
    }

    private void resetNetwork() {
        deviceInput.setText(AppSettings.DEFAULT_DEVICE_IP);
        tunnelInput.setText(AppSettings.DEFAULT_TUNNEL_IP);
        AppSettings.resetNetwork(this);
        errorView.setVisibility(View.GONE);
        hideKeyboard();
        Toast.makeText(this, R.string.network_reset, Toast.LENGTH_SHORT).show();
    }

    private int errorFor(AppSettings.Validation result) {
        switch (result) {
            case INVALID_DEVICE:
                return R.string.error_invalid_device;
            case INVALID_TUNNEL:
                return R.string.error_invalid_tunnel;
            case DIFFERENT_SUBNET:
                return R.string.error_different_subnet;
            case SAME_ADDRESS:
                return R.string.error_same_ip;
            default:
                return R.string.error_invalid_device;
        }
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        focus.clearFocus();
    }

    private void refreshLog() {
        if (logView == null) return;
        String value = ConnectionLog.snapshot();
        if (value.isEmpty()) value = getString(R.string.log_empty);
        if (TextUtils.equals(value, lastLog)) return;
        boolean followLatest = isLogAtBottom();
        lastLog = value;
        logView.setText(value);
        if (followLatest && logScroll != null) {
            logScroll.post(new Runnable() {
                @Override
                public void run() {
                    logScroll.scrollTo(0, maxLogScroll());
                }
            });
        }
    }

    /**
     * Only follow new lines when the log was already at its bottom. If the user has scrolled up
     * to read older entries, a refresh must leave that position alone.
     */
    private boolean isLogAtBottom() {
        if (logScroll == null || logView == null) return true;
        int viewport = logScroll.getHeight() - logScroll.getPaddingTop()
                - logScroll.getPaddingBottom();
        if (viewport <= 0) return true;
        int max = Math.max(0, logView.getHeight() - viewport);
        return logScroll.getScrollY() >= max - dp(24);
    }

    private int maxLogScroll() {
        if (logScroll == null || logView == null) return 0;
        int viewport = logScroll.getHeight() - logScroll.getPaddingTop()
                - logScroll.getPaddingBottom();
        return Math.max(0, logView.getHeight() - viewport);
    }

    // ------------------------------------------------------------------ drawing

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable rounded(int fill, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void applyInsets(final View root) {
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                int left, top, right, bottom;
                if (Build.VERSION.SDK_INT >= 30) {
                    Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                            | WindowInsets.Type.displayCutout());
                    Insets ime = insets.getInsets(WindowInsets.Type.ime());
                    left = bars.left;
                    top = bars.top;
                    right = bars.right;
                    bottom = Math.max(bars.bottom, ime.bottom);
                } else {
                    left = insets.getSystemWindowInsetLeft();
                    top = insets.getSystemWindowInsetTop();
                    right = insets.getSystemWindowInsetRight();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                view.setPadding(left, top, right, bottom);
                return insets;
            }
        });
    }
}
