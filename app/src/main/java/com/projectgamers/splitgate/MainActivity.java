// Copyright (C) 2026 Project Gamers
// SPDX-License-Identifier: GPL-3.0-or-later
package com.projectgamers.splitgate;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.TextUtils;
import android.animation.LayoutTransition;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.ViewGroup;
import android.view.View;
import android.view.WindowInsets;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.projectgamers.splitgate.engine.AppHints;
import com.projectgamers.splitgate.engine.DohResolver;
import com.projectgamers.splitgate.engine.DomainFilter;
import com.projectgamers.splitgate.engine.Presets;
import com.projectgamers.splitgate.engine.Probe;
import com.projectgamers.splitgate.engine.TunEngine;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_VPN = 1;
    private static final int REQ_NOTIF = 2;

    // Renkler
    private static final int BG = 0xFF0B1020;
    private static final int CARD = 0xFF141B2D;
    private static final int CARD_HI = 0xFF18213A;
    private static final int STROKE = 0xFF222C45;
    private static final int CHIP_OFF = 0xFF1B2440;
    private static final int TEXT = 0xFFEAF0FF;
    private static final int MUTED = 0xFF8A95B3;
    private static final int ACCENT = 0xFF2EE6A8;
    private static final int ON_ACCENT = 0xFF06241B;
    private static final int AMBER = 0xFFFFC857;
    private static final int WARN = 0xFFFF6B6B;

    private static final String[] DNS_IDS = {"quad9", "cloudflare", "google"};
    private static final String[] DNS_NAMES = {"Quad9", "Cloudflare", "Google"};
    private static final String KEY_PROBE_DOMAINS = BypassVpnService.KEY_PROBE_DOMAINS;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private static final String KEY_WELCOME_DONE = "welcome_done";

    private PowerButton power;
    private TextView statusTitle, statusSub, hintBtn, rateLegend;
    private TextView downVal, downSub, upVal, upSub, connVal, splitVal, dnsVal;
    private Motion.Counter downCount, upCount, connCount, splitCount, dnsCount;
    private Sparkline spark;
    private ScrollView scrollView;
    private Toaster toaster;
    private Section testSection;
    private LinearLayout testRows;
    private BarView testBusy;
    private Switch animSwitch;
    private long pendingUntil;
    private boolean pendingTarget;
    private final LinearLayout[] presetRows = new LinearLayout[Presets.ALL.length];
    private final GradientDrawable[] presetBg = new GradientDrawable[Presets.ALL.length];
    private final boolean[] presetOn = new boolean[Presets.ALL.length];
    private final TextView[] presetTitles = new TextView[Presets.ALL.length];
    private final TextView[] presetDescs = new TextView[Presets.ALL.length];
    private final TextView[] dnsChips = new TextView[DNS_IDS.length];
    private TextView savedNets;
    private String shownPreset;
    private TextView testBtn, testStatus, testResults, applyBtn;
    private EditText testDomains;
    private volatile boolean testing;
    private String bestId;
    private TextView logView, methodSummary, appsSummary, pickAppsBtn;
    private final TextView[] appModeChips = new TextView[2];
    private final TextView[] siteModeChips = new TextView[3];
    private EditText siteList;
    private boolean logOpen;
    private static final String REPO_URL = "https://github.com/gmtr244/splitgate-android";
    private static final String LINUX_URL = "https://github.com/gmtr244/splitgate-linux";
    private int shownLogVersion = -1;

    // hiz hesabi
    private TunEngine lastEngine;
    private long lastDown, lastUp, lastTick, downRate, upRate;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void snack(int res, boolean longer) {
        toaster.show(getString(res), longer);
    }

    private void snack(String text, boolean longer) {
        toaster.show(text, longer);
    }

    // ------------------------------------------------------------------ kurulum

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(BypassVpnService.PREFS, MODE_PRIVATE);
        Motion.refresh(this, prefs.getBoolean(Motion.KEY_ANIM, true));
        if (Build.VERSION.SDK_INT < 35) {
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
        }

        ScrollView scroll = new ScrollView(this);
        scrollView = scroll;
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(28));
        scroll.addView(root);

        buildHeader(root);
        buildPower(root);
        buildWelcome(root);
        buildStats(root);
        buildSettings(root);
        buildApps(root);
        buildTest(root);
        buildInfo(root);
        buildLog(root);
        buildAbout(root);
        TextView star = tv(getString(R.string.star_banner), 13, ACCENT, true);
        star.setGravity(Gravity.CENTER);
        star.setPadding(dp(8), dp(12), dp(8), dp(12));
        star.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUrl(REPO_URL);
            }
        });
        root.addView(star, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, dp(4), 0, 0));

        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(BG);
        scroll.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int l, t, r, b;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    int ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
                    l = i.left;
                    t = i.top;
                    r = i.right;
                    b = Math.max(i.bottom, ime);
                    final View focused = v.findFocus();
                    if (ime > 0 && focused != null) {
                        final ScrollView sv = (ScrollView) v;
                        final int bottomPad = b;
                        v.post(new Runnable() {
                            @Override
                            public void run() {
                                android.graphics.Rect r = new android.graphics.Rect(0, 0, focused.getWidth(), focused.getHeight());
                                sv.offsetDescendantRectToMyCoords(focused, r);
                                int visibleBottom = sv.getScrollY() + sv.getHeight() - bottomPad;
                                int delta = r.bottom + dp(24) - visibleBottom;
                                if (delta > 0) sv.smoothScrollBy(0, delta);
                            }
                        });
                    }
                } else {
                    l = insets.getSystemWindowInsetLeft();
                    t = insets.getSystemWindowInsetTop();
                    r = insets.getSystemWindowInsetRight();
                    b = insets.getSystemWindowInsetBottom();
                }
                v.setPadding(l, t, r, b);
                if (toaster != null) toaster.setBottomInset(b);
                return insets;
            }
        });
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        frame.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        toaster = new Toaster(this, frame);
        setContentView(frame);
        // acilista kartlar sirayla asagidan yukari belirir
        for (int i = 0; i < root.getChildCount(); i++) {
            Motion.enter(root.getChildAt(i), Math.min(i, 9) * 55L, dp(16));
        }
        String forNet = NetworkId.savedPreset(prefs, NetworkId.current(this));
        highlightPreset(forNet != null ? forNet : prefs.getString(BypassVpnService.KEY_PRESET, "sni"));
        selectDns(prefs.getString(BypassVpnService.KEY_DNS, "quad9"), false);
        if (LocaleHelper.saved(this) == null) showLanguageDialog(true);
    }

    private void showLanguageDialog(final boolean firstRun) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_language)
                .setCancelable(!firstRun)
                .setItems(LocaleHelper.NAMES, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        LocaleHelper.save(MainActivity.this, LocaleHelper.CODES[which]);
                        recreate();
                    }
                })
                .show();
    }

    private String presetLabel(Presets.Preset p) {
        switch (p.id) {
            case "none": return getString(R.string.preset_none);
            case "hafif": return getString(R.string.preset_hafif);
            case "sni": return getString(R.string.preset_sni);
            case "agresif": return getString(R.string.preset_agresif);
            case "oob": return getString(R.string.preset_oob);
            case "tlskayit": return getString(R.string.preset_tlskayit);
            case "disorder": return getString(R.string.preset_disorder);
            case "disordersni": return getString(R.string.preset_disordersni);
            case "tlsdisorder": return getString(R.string.preset_tlsdisorder);
            default: return p.label;
        }
    }

    private String presetDesc(Presets.Preset p) {
        switch (p.id) {
            case "none": return getString(R.string.preset_none_desc);
            case "hafif": return getString(R.string.preset_hafif_desc);
            case "sni": return getString(R.string.preset_sni_desc);
            case "agresif": return getString(R.string.preset_agresif_desc);
            case "oob": return getString(R.string.preset_oob_desc);
            case "tlskayit": return getString(R.string.preset_tlskayit_desc);
            case "disorder": return getString(R.string.preset_disorder_desc);
            case "disordersni": return getString(R.string.preset_disordersni_desc);
            case "tlsdisorder": return getString(R.string.preset_tlsdisorder_desc);
            default: return p.desc;
        }
    }

    private String errText(String err) {
        if (err == null) return "";
        if (err.equals(Probe.ERR_TIMEOUT)) return getString(R.string.err_timeout);
        if (err.equals(Probe.ERR_CERTIFICATE)) return getString(R.string.err_certificate);
        if (err.equals(Probe.ERR_HOSTNAME)) return getString(R.string.err_hostname);
        if (err.equals(Probe.ERR_CONNECT_TIMEOUT)) return getString(R.string.err_connect_timeout);
        if (err.equals(Probe.ERR_RESET)) return getString(R.string.err_reset);
        if (err.equals(Probe.ERR_REFUSED)) return getString(R.string.err_refused);
        if (err.equals(Probe.ERR_UNREACHABLE)) return getString(R.string.err_unreachable);
        if (err.equals(Probe.ERR_CLOSED)) return getString(R.string.err_closed);
        if (err.startsWith(Probe.ERR_DNS_PREFIX)) return "DNS: " + err.substring(Probe.ERR_DNS_PREFIX.length());
        return err;
    }

    private String verdictText(Probe.Verdict v) {
        if (v == null) return "";
        switch (v) {
            case WORKS: return getString(R.string.verdict_works);
            case NOT_BLOCKED: return getString(R.string.verdict_not_blocked);
            case DNS_FAILED: return getString(R.string.verdict_dns);
            case NO_CONNECTION: return getString(R.string.verdict_no_connection);
            case IP_BLOCK: return getString(R.string.verdict_ip_block);
            case INTERCEPTED: return getString(R.string.verdict_intercepted);
            default: return getString(R.string.verdict_dpi);
        }
    }

    private void buildHeader(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo);
        row.addView(logo, lp(dp(40), dp(40), 0, 0, 0, 0, 0));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(tv(getString(R.string.app_name), 22, TEXT, true));
        col.addView(tv(getString(R.string.tagline), 12, MUTED, false));
        row.addView(col, lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f, dp(12), 0, 0, 0));
        row.addView(smallButton("🌐 " + LocaleHelper.nameOf(LocaleHelper.code(this)), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLanguageDialog(false);
            }
        }));

        root.addView(row, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0, dp(8)));
    }

    private void buildPower(LinearLayout root) {
        power = new PowerButton(this);
        power.setContentDescription(getString(R.string.toggle_cd));
        power.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggle();
            }
        });
        LinearLayout.LayoutParams plp = lp(dp(210), dp(210), 0, 0, dp(10), 0, dp(6));
        plp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(power, plp);

        statusTitle = tv(getString(R.string.status_off), 24, MUTED, true);
        statusTitle.setGravity(Gravity.CENTER);
        statusTitle.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(statusTitle, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0, 0));

        statusSub = tv(getString(R.string.status_hint), 13, MUTED, false);
        statusSub.setGravity(Gravity.CENTER);
        root.addView(statusSub, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0, dp(18)));

        hintBtn = smallButton(getString(R.string.hint_find_method), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTest();
            }
        });
        hintBtn.setPadding(dp(22), dp(11), dp(22), dp(11));
        hintBtn.setVisibility(View.GONE);
        LinearLayout.LayoutParams hlp = lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, dp(-8), 0, dp(18));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(hintBtn, hlp);
    }

    /** Ilk acilista "3 adimda baglan" karti; kapatilinca bir daha cikmaz. */
    private void buildWelcome(LinearLayout root) {
        if (prefs.getBoolean(KEY_WELCOME_DONE, false)) return;
        final LinearLayout c = card();
        c.addView(tv(getString(R.string.welcome_title), 14, TEXT, true));
        TextView t = tv(getString(R.string.welcome_text), 12, MUTED, false);
        t.setPadding(0, dp(6), 0, dp(10));
        c.addView(t);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(smallButton(getString(R.string.welcome_open), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTest();
            }
        }), lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f, 0, 0, dp(4), 0));
        row.addView(smallButton(getString(R.string.welcome_dismiss), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().putBoolean(KEY_WELCOME_DONE, true).apply();
                Motion.setOpen(c, false);
            }
        }), lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f, dp(4), 0, 0, 0));
        c.addView(row);
        root.addView(c, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0, dp(14)));
    }

    /** Test kartini acar ve ekrana kaydirir. */
    private void openTest() {
        testSection.set(true);
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.smoothScrollTo(0, Math.max(0, testSection.card.getTop() - dp(8)));
            }
        });
    }

    private void buildStats(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        downVal = tv("0 B", 22, TEXT, true);
        downSub = tv("↓ —", 12, ACCENT, false);
        upVal = tv("0 B", 22, TEXT, true);
        upSub = tv("↑ —", 12, Sparkline.UP, false);

        row.addView(statCard(getString(R.string.stat_downloaded), downVal, downSub), lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f, 0, 0, dp(6), 0));
        row.addView(statCard(getString(R.string.stat_uploaded), upVal, upSub), lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f, dp(6), 0, 0, 0));
        root.addView(row, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0, dp(12)));

        LinearLayout mini = card();
        LinearLayout mrow = new LinearLayout(this);
        mrow.setOrientation(LinearLayout.HORIZONTAL);
        connVal = tv("0", 18, TEXT, true);
        splitVal = tv("0", 18, TEXT, true);
        dnsVal = tv("0", 18, TEXT, true);
        mrow.addView(miniStat(getString(R.string.stat_active), connVal), lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f, 0, 0, 0, 0));
        mrow.addView(miniStat(getString(R.string.stat_split), splitVal), lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f, 0, 0, 0, 0));
        mrow.addView(miniStat(getString(R.string.stat_dns), dnsVal), lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f, 0, 0, 0, 0));
        mini.addView(mrow);
        root.addView(mini, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0, dp(12)));

        downCount = new Motion.Counter(downVal, new Motion.Counter.Format() {
            @Override
            public String text(long v) {
                return Fmt.bytes(v);
            }
        });
        upCount = new Motion.Counter(upVal, new Motion.Counter.Format() {
            @Override
            public String text(long v) {
                return Fmt.bytes(v);
            }
        });
        Motion.Counter.Format plain = new Motion.Counter.Format() {
            @Override
            public String text(long v) {
                return String.valueOf(v);
            }
        };
        connCount = new Motion.Counter(connVal, plain);
        splitCount = new Motion.Counter(splitVal, plain);
        dnsCount = new Motion.Counter(dnsVal, plain);

        LinearLayout live = card();
        LinearLayout lh = new LinearLayout(this);
        lh.setOrientation(LinearLayout.HORIZONTAL);
        lh.setGravity(Gravity.CENTER_VERTICAL);
        lh.addView(tv(getString(R.string.live_traffic), 11, MUTED, true), lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f, 0, 0, 0, 0));
        rateLegend = tv("", 11, MUTED, false);
        lh.addView(rateLegend);
        live.addView(lh);
        spark = new Sparkline(this);
        live.addView(spark, lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(84), 0, 0, dp(8), 0, 0));
        root.addView(live, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0, dp(12)));
    }

    private void buildSettings(LinearLayout root) {
        Section sec = collapsible(root, getString(R.string.section_settings), false, null);
        methodSummary = sec.summary;
        LinearLayout c = sec.body;

        for (int i = 0; i < Presets.ALL.length; i++) {
            final Presets.Preset pr = Presets.ALL[i];
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            TextView title = tv(presetLabel(pr), 14, MUTED, true);
            TextView desc = tv(presetDesc(pr), 11, MUTED, false);
            row.addView(title);
            row.addView(desc);
            GradientDrawable rbg = new GradientDrawable();
            rbg.setCornerRadius(dp(12));
            rbg.setColor(CHIP_OFF);
            row.setBackground(ripple(rbg));
            presetBg[i] = rbg;
            Motion.pressable(row);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    choosePreset(pr.id);
                }
            });
            presetRows[i] = row;
            presetTitles[i] = title;
            presetDescs[i] = desc;
            c.addView(row, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, i == 0 ? dp(10) : dp(6), 0, 0));
        }

        Switch auto = new Switch(this);
        auto.setChecked(prefs.getBoolean(BypassVpnService.KEY_AUTO, true));
        auto.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                prefs.edit().putBoolean(BypassVpnService.KEY_AUTO, checked).apply();
            }
        });
        c.addView(switchRow(getString(R.string.auto_title), getString(R.string.auto_desc), auto),
                lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, dp(16), 0, 0));

        savedNets = tv("", 12, MUTED, false);
        c.addView(savedNets, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, dp(10), 0, 0));
        c.addView(smallButton(getString(R.string.nets_forget), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NetworkId.forgetAll(prefs);
                renderSavedNets();
                snack(R.string.nets_forgotten, false);
            }
        }), lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, dp(8), 0, 0));
        renderSavedNets();

        c.addView(tv(getString(R.string.section_dns), 11, MUTED, true), lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, dp(16), 0, 0));
        LinearLayout dnsRow = new LinearLayout(this);
        dnsRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < DNS_IDS.length; i++) {
            final String id = DNS_IDS[i];
            TextView chip = tv(DNS_NAMES[i], 13, MUTED, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(6), dp(11), dp(6), dp(11));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectDns(id, true);
                }
            });
            dnsChips[i] = chip;
            dnsRow.addView(chip, lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f, i == 0 ? 0 : dp(4), 0, i == DNS_IDS.length - 1 ? 0 : dp(4), 0));
        }
        c.addView(dnsRow, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, dp(8), 0, 0));

        Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean(BypassVpnService.KEY_QUIC, true));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                prefs.edit().putBoolean(BypassVpnService.KEY_QUIC, checked).apply();
                BypassVpnService.applyQuicLive(checked);
            }
        });
        c.addView(switchRow(getString(R.string.quic_title), getString(R.string.quic_desc), sw), lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, dp(16), 0, 0));

    }

    private void tint(Switch s) {
        int[][] st = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(st, new int[]{ACCENT, 0xFFB7C0DA}));
        s.setTrackTintList(new ColorStateList(st, new int[]{0x662EE6A8, 0xFF2A3556}));
    }

    private LinearLayout switchRow(String title, String desc, Switch sw) {
        tint(sw);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.addView(tv(title, 15, TEXT, true));
        txt.addView(tv(desc, 12, MUTED, false));
        row.addView(txt, lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f, 0, 0, 0, 0));
        row.addView(sw);
        return row;
    }

    private void renderSavedNets() {
        List<String[]> nets = NetworkId.savedList(prefs);
        if (nets.isEmpty()) {
            savedNets.setText(getString(R.string.nets_none));
            return;
        }
        StringBuilder sb = new StringBuilder(getString(R.string.nets_title));
        for (String[] n : nets) {
            sb.append("\n• ").append(n[0]).append(" → ").append(presetLabel(Presets.byId(n[1])))
                    .append(n[2].equals("manual") ? getString(R.string.nets_manual) : getString(R.string.nets_auto));
        }
        savedNets.setText(sb.toString());
    }

    // ------------------------------------------------------------------ uygulamalar ve siteler

    private static final String[] SITE_MODES = {"all", "include", "exclude"};

    private TextView hint(String text) {
        TextView t = tv(text, 12, MUTED, false);
        t.setPadding(0, dp(4), 0, dp(8));
        return t;
    }

    private TextView chip(String text, View.OnClickListener l) {
        TextView t = tv(text, 13, MUTED, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(10), dp(8), dp(10));
        t.setOnClickListener(l);
        Motion.pressable(t);
        return t;
    }

    /** Secili / degil rengi yumusakca degistirir; arka plan ilk cagrida kurulur ve etikette saklanir. */
    private void styleChip(TextView chip, boolean on) {
        GradientDrawable g;
        if (chip.getTag() instanceof GradientDrawable) {
            g = (GradientDrawable) chip.getTag();
        } else {
            g = new GradientDrawable();
            g.setCornerRadius(dp(12));
            g.setColor(CHIP_OFF);
            chip.setBackground(ripple(g));
            chip.setTag(g);
        }
        int to = on ? ACCENT : CHIP_OFF;
        ColorStateList cur = g.getColor();
        Motion.fill(g, chip, cur != null ? cur.getDefaultColor() : CHIP_OFF, to);
        Motion.textColor(chip, on ? ON_ACCENT : MUTED);
    }

    private String appMode() {
        return "only".equals(prefs.getString(BypassVpnService.KEY_APP_MODE, "all")) ? "only" : "all";
    }

    private String appsKey() {
        return appMode().equals("only") ? BypassVpnService.KEY_APPS_ONLY : BypassVpnService.KEY_APPS_EXCLUDE;
    }

    private int appCount() {
        return prefs.getStringSet(appsKey(), Collections.<String>emptySet()).size();
    }

    private String siteMode() {
        String m = prefs.getString(BypassVpnService.KEY_SITE_MODE, "all");
        return "include".equals(m) || "exclude".equals(m) ? m : "all";
    }

    private void updateAppsSummary() {
        if (appsSummary == null) return;
        int n = appCount();
        String a = appMode().equals("only") ? getString(R.string.apps_summary_only, n) : getString(R.string.apps_summary_all, n);
        String sm = siteMode();
        String sites = "";
        if (!sm.equals("all")) {
            int count;
            try {
                count = DomainFilter.parse(prefs.getString(BypassVpnService.KEY_SITES, "")).length;
            } catch (IllegalArgumentException e) {
                count = 0;
            }
            sites = " \u00b7 " + getString(R.string.sites_summary, count);
        }
        showSummary(appsSummary, a + sites);
        if (pickAppsBtn != null) pickAppsBtn.setText(getString(R.string.apps_pick, n));
        for (int i = 0; i < appModeChips.length; i++) styleChip(appModeChips[i], (i == 1) == appMode().equals("only"));
        for (int i = 0; i < siteModeChips.length; i++) styleChip(siteModeChips[i], SITE_MODES[i].equals(sm));
    }

    private void buildApps(LinearLayout root) {
        Section sec = collapsible(root, getString(R.string.section_apps), false, null);
        appsSummary = sec.summary;
        LinearLayout c = sec.body;

        c.addView(tv(getString(R.string.apps_title), 15, TEXT, true));
        c.addView(hint(getString(R.string.apps_intro)));
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        appModeChips[0] = chip(getString(R.string.apps_mode_all), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setAppMode("all");
            }
        });
        appModeChips[1] = chip(getString(R.string.apps_mode_only), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setAppMode("only");
            }
        });
        modes.addView(appModeChips[0], lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f, 0, 0, dp(4), 0));
        modes.addView(appModeChips[1], lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f, dp(4), 0, 0, 0));
        c.addView(modes, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0, dp(8)));
        pickAppsBtn = smallButton("", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickApps();
            }
        });
        c.addView(pickAppsBtn);

        c.addView(tv(getString(R.string.sites_title), 15, TEXT, true), lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, dp(18), 0, 0));
        c.addView(hint(getString(R.string.sites_intro)));
        LinearLayout sm = new LinearLayout(this);
        sm.setOrientation(LinearLayout.VERTICAL);
        int[] labels = {R.string.sites_mode_all, R.string.sites_mode_only, R.string.sites_mode_except};
        for (int i = 0; i < 3; i++) {
            final String mode = SITE_MODES[i];
            siteModeChips[i] = chip(getString(labels[i]), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.edit().putString(BypassVpnService.KEY_SITE_MODE, mode).apply();
                    BypassVpnService.applySiteFilterLive(prefs);
                    updateAppsSummary();
                }
            });
            sm.addView(siteModeChips[i], lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, i == 0 ? 0 : dp(6), 0, 0));
        }
        c.addView(sm);
        siteList = new EditText(this);
        siteList.setMinLines(3);
        siteList.setGravity(Gravity.TOP | Gravity.START);
        siteList.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_URI);
        siteList.setHint(R.string.sites_hint);
        siteList.setHintTextColor(MUTED);
        siteList.setTextColor(TEXT);
        siteList.setTextSize(13);
        siteList.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable box = new GradientDrawable();
        box.setColor(CHIP_OFF);
        box.setCornerRadius(dp(12));
        siteList.setBackground(box);
        siteList.setText(prefs.getString(BypassVpnService.KEY_SITES, ""));
        c.addView(siteList, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, dp(8), 0, dp(8)));
        c.addView(smallButton(getString(R.string.sites_save), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSites();
            }
        }));

        c.addView(tv(getString(R.string.battery_title), 15, TEXT, true), lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, dp(18), 0, 0));
        c.addView(hint(getString(R.string.battery_text)));
        c.addView(smallButton(getString(R.string.battery_open), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                } catch (RuntimeException e) {
                    try {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    } catch (RuntimeException ignore) {
                    }
                }
            }
        }));
        updateAppsSummary();
    }

    private void setAppMode(String mode) {
        prefs.edit().putString(BypassVpnService.KEY_APP_MODE, mode).apply();
        updateAppsSummary();
        notifyRestartNeeded();
    }

    private void saveSites() {
        String text = siteList.getText().toString();
        String[] parsed;
        try {
            parsed = DomainFilter.parse(text);
        } catch (IllegalArgumentException e) {
            String m = e.getMessage();
            snack(m != null && m.startsWith("more than")
                    ? getString(R.string.sites_too_many, DomainFilter.MAX_DOMAINS)
                    : getString(R.string.test_bad_domain, m), true);
            return;
        }
        String joined = android.text.TextUtils.join("\n", parsed);
        prefs.edit().putString(BypassVpnService.KEY_SITES, joined).apply();
        siteList.setText(joined);
        BypassVpnService.applySiteFilterLive(prefs);
        updateAppsSummary();
        snack(parsed.length == 0 && !siteMode().equals("all") ? R.string.sites_empty_note : R.string.sites_saved, true);
    }

    private static final class AppItem {
        String pkg;
        String label;
        boolean suggested;
    }

    /** Baslatilabilir uygulamalari (kendimiz haric) yukler; ucuncu parti degil, yalniz yerel PackageManager. */
    private boolean pickingApps;

    private void pickApps() {
        if (pickingApps) return; // cift dokunusta iki pencere acilmasin
        pickingApps = true;
        snack(R.string.apps_loading, false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                PackageManager pm = getPackageManager();
                Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                final List<AppItem> items = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
                    String pkg = ri.activityInfo.packageName;
                    if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                    AppItem it = new AppItem();
                    it.pkg = pkg;
                    it.label = String.valueOf(ri.loadLabel(pm));
                    it.suggested = AppHints.looksLikeBankOrGov(pkg, it.label);
                    items.add(it);
                }
                final Collator col = Collator.getInstance(Locale.forLanguageTag("tr-TR"));
                Collections.sort(items, new Comparator<AppItem>() {
                    @Override
                    public int compare(AppItem a, AppItem b) {
                        if (a.suggested != b.suggested) return a.suggested ? -1 : 1;
                        return col.compare(a.label, b.label);
                    }
                });
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        pickingApps = false;
                        if (!isFinishing() && !isDestroyed()) showAppDialog(items);
                    }
                });
            }
        }, "dpi-apps").start();
    }

    private void showAppDialog(final List<AppItem> items) {
        final Set<String> saved = new HashSet<>(prefs.getStringSet(appsKey(), Collections.<String>emptySet()));
        final String[] names = new String[items.size()];
        final boolean[] checked = new boolean[items.size()];
        final Set<String> listed = new HashSet<>();
        for (int i = 0; i < names.length; i++) {
            AppItem it = items.get(i);
            names[i] = it.suggested ? it.label + "   (" + getString(R.string.apps_suggest_tag) + ")" : it.label;
            checked[i] = saved.contains(it.pkg);
            listed.add(it.pkg);
        }
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(appMode().equals("only") ? R.string.apps_dialog_only : R.string.apps_dialog_all)
                .setMultiChoiceItems(names, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.apps_suggest_btn, null)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Set<String> out = new HashSet<>();
                        for (String p : saved) if (!listed.contains(p)) out.add(p); // listede gorunmeyenleri koru
                        for (int i = 0; i < checked.length; i++) if (checked[i]) out.add(items.get(i).pkg);
                        prefs.edit().putStringSet(appsKey(), out).apply();
                        updateAppsSummary();
                        notifyRestartNeeded();
                    }
                })
                .create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i).suggested) {
                        checked[i] = true;
                        dlg.getListView().setItemChecked(i, true);
                    }
                }
            }
        });
    }

    private void buildTest(LinearLayout root) {
        testSection = collapsible(root, getString(R.string.section_test), false, null);
        LinearLayout c = testSection.body;
        // durum / sonuc satirlari belirip kaybolurken alttakiler yumusakca kayar
        LayoutTransition lt = new LayoutTransition();
        lt.enableTransitionType(LayoutTransition.CHANGING);
        c.setLayoutTransition(lt);
        TextView info = tv(getString(R.string.test_info), 12, MUTED, false);
        info.setPadding(0, dp(6), 0, dp(10));
        c.addView(info);

        testDomains = new EditText(this);
        testDomains.setSingleLine(true);
        testDomains.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        testDomains.setHint(R.string.test_domains_hint);
        testDomains.setHintTextColor(MUTED);
        testDomains.setTextColor(TEXT);
        testDomains.setTextSize(13);
        testDomains.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable box = new GradientDrawable();
        box.setColor(CHIP_OFF);
        box.setCornerRadius(dp(12));
        testDomains.setBackground(box);
        testDomains.setText(prefs.getString(KEY_PROBE_DOMAINS, TextUtils.join(", ", Probe.DEFAULT_DOMAINS)));
        c.addView(testDomains, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0, dp(10)));

        testBtn = smallButton(getString(R.string.test_run), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runTest();
            }
        });
        c.addView(testBtn);

        testBusy = new BarView(this);
        testBusy.setVisibility(View.GONE);
        c.addView(testBusy, lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(5), 0, 0, dp(10), 0, 0));

        testStatus = tv("", 12, ACCENT, false);
        testStatus.setPadding(0, dp(10), 0, 0);
        testStatus.setVisibility(View.GONE);
        c.addView(testStatus);

        testRows = new LinearLayout(this);
        testRows.setOrientation(LinearLayout.VERTICAL);
        testRows.setPadding(0, dp(8), 0, 0);
        c.addView(testRows);

        testResults = tv("", 12, TEXT, false);
        testResults.setTypeface(Typeface.MONOSPACE);
        testResults.setPadding(0, dp(8), 0, 0);
        testResults.setVisibility(View.GONE);
        c.addView(testResults);

        applyBtn = smallButton(getString(R.string.test_apply), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bestId != null) choosePreset(bestId);
            }
        });
        applyBtn.setVisibility(View.GONE);
        c.addView(applyBtn, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, dp(10), 0, 0));

        TextView rinfo = tv(getString(R.string.report_info), 12, MUTED, false);
        rinfo.setPadding(0, dp(14), 0, dp(8));
        c.addView(rinfo);
        LinearLayout rrow = new LinearLayout(this);
        rrow.setOrientation(LinearLayout.HORIZONTAL);
        rrow.addView(smallButton(getString(R.string.report_send), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askReport(false);
            }
        }), lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f, 0, 0, dp(4), 0));
        rrow.addView(smallButton(getString(R.string.report_share), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askReport(true);
            }
        }), lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f, dp(4), 0, 0, 0));
        c.addView(rrow);

    }

    private void askReport(final boolean share) {
        final EditText note = new EditText(this);
        note.setHint(R.string.report_note_hint);
        note.setMinLines(3);
        note.setGravity(Gravity.TOP | Gravity.START);
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(note, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0, 0));
        new AlertDialog.Builder(this)
                .setTitle(R.string.report_note_title)
                .setView(box)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(share ? R.string.report_share : R.string.report_send, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String body = Report.build(MainActivity.this, prefs, note.getText().toString());
                        if (share) {
                            Report.share(MainActivity.this, body);
                        } else if (!Report.email(MainActivity.this, body)) {
                            snack(R.string.report_no_mail, true);
                            Report.share(MainActivity.this, body);
                        }
                    }
                })
                .show();
    }

    private void runTest() {
        if (testing) return;
        final List<String> domains;
        try {
            domains = Probe.parseDomains(testDomains.getText().toString());
        } catch (IllegalArgumentException e) {
            snack(getString(R.string.test_bad_domain, e.getMessage()), true);
            return;
        }
        String joined = TextUtils.join(", ", domains);
        prefs.edit().putString(KEY_PROBE_DOMAINS, joined).apply();
        testDomains.setText(joined);
        testing = true;
        bestId = null;
        testBtn.setText(getString(R.string.test_running));
        testStatus.setVisibility(View.VISIBLE);
        testStatus.setText(getString(R.string.test_starting));
        testRows.removeAllViews();
        testResults.setVisibility(View.GONE);
        applyBtn.setVisibility(View.GONE);
        testBusy.setVisibility(View.VISIBLE);
        testBusy.setIndeterminate(true);
        final String dnsId = prefs.getString(BypassVpnService.KEY_DNS, "quad9");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String text;
                String best = null;
                List<Probe.Result> results = null;
                DohResolver resolver = new DohResolver(DohResolver.endpointsFor(dnsId));
                try {
                    List<Probe.Result> res = Probe.runAll(resolver, domains,
                            new Probe.Progress() {
                                @Override
                                public void step(final Presets.Preset preset) {
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            Motion.swap(testStatus, getString(R.string.test_progress, presetLabel(preset)));
                                        }
                                    });
                                }
                            }, AndroidTtl.INSTANCE);
                    results = res;
                    best = Probe.best(res);
                    text = render(res);
                    BypassVpnService.lastResults = res;
                    NetworkId tn = NetworkId.current(MainActivity.this);
                    BypassVpnService.lastResultsNet = tn != null ? tn.label : null;
                    if (best == null) text = verdictText(Probe.diagnose(res)) + "\n\n" + text;
                } catch (Exception e) {
                    text = getString(R.string.test_error, e.getClass().getSimpleName()
                            + (e.getMessage() != null ? ": " + e.getMessage() : ""));
                } finally {
                    resolver.close();
                }
                final String fText = text;
                final String fBest = best;
                final List<Probe.Result> fRes = results;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        testing = false;
                        testBtn.setText(getString(R.string.test_run));
                        bestId = fBest;
                        testBusy.setIndeterminate(false);
                        testBusy.setVisibility(View.GONE);
                        if (fRes != null) showRows(fRes, fBest);
                        testResults.setText(fText);
                        testResults.setVisibility(fText.isEmpty() ? View.GONE : View.VISIBLE);
                        if (fBest != null) {
                            Motion.swap(testStatus, getString(R.string.test_recommended, presetLabel(Presets.byId(fBest))));
                            applyBtn.setVisibility(View.VISIBLE);
                        } else {
                            Motion.swap(testStatus, getString(R.string.test_none));
                            Motion.shake(testStatus, dp(6));
                        }
                    }
                });
            }
        }, "dpi-probe").start();
    }

    private String render(List<Probe.Result> res) {
        StringBuilder sb = new StringBuilder();
        for (Probe.Result r : res) {
            if (r.preset != Presets.NONE) continue;
            for (Map.Entry<String, Probe.DomainResult> e : r.domains.entrySet()) {
                if (!e.getValue().ok) sb.append("\n").append(getString(R.string.test_baseline_err, e.getKey(), errText(e.getValue().err)));
            }
        }
        return sb.toString().trim();
    }

    /** Her yontem icin bir satir: ad, acilan site sayisi, sure ve dolan bir cubuk. En iyisi vurgulanir. */
    private void showRows(List<Probe.Result> res, String best) {
        testRows.removeAllViews();
        int i = 0;
        for (Probe.Result r : res) {
            boolean isBest = best != null && r.preset.id.equals(best);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.addView(tv((isBest ? "\u2605 " : "") + presetLabel(r.preset), 13, isBest ? ACCENT : TEXT, true),
                    lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f, 0, 0, 0, 0));
            String right = r.ok + "/" + r.total + (r.avgMs >= 0 ? "  \u00b7  " + r.avgMs + " ms" : "");
            line.addView(tv(right, 12, MUTED, false));
            row.addView(line);
            BarView bar = new BarView(this);
            bar.setColor(isBest ? ACCENT : (r.ok == r.total && r.total > 0 ? 0xFF5B8CFF : (r.ok > 0 ? AMBER : STROKE)));
            row.addView(bar, lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(6), 0, 0, dp(5), 0, 0));
            row.setContentDescription(getString(R.string.test_row_cd, presetLabel(r.preset), r.ok, r.total));
            testRows.addView(row);
            bar.fillTo(r.total > 0 ? r.ok / (float) r.total : 0f, 80L * i);
            Motion.enter(row, 80L * i, dp(10));
            i++;
        }
    }

    private void buildInfo(LinearLayout root) {
        LinearLayout c = collapsible(root, getString(R.string.section_privacy), false, null).body;
        TextView t = tv(getString(R.string.privacy_text), 12, MUTED, false);
        t.setPadding(0, dp(6), 0, 0);
        c.addView(t);
    }

    private void buildLog(LinearLayout root) {
        Section sec = collapsible(root, getString(R.string.section_log), false, new Toggled() {
            @Override
            public void run(boolean open) {
                logOpen = open;
                shownLogVersion = -1;
                refresh();
            }
        });
        LinearLayout body = sec.body;

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.addView(smallButton(getString(R.string.log_copy), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("dpi-log", LogBuffer.dump()));
                snack(R.string.log_copied, false);
            }
        }), lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f, 0, 0, dp(4), 0));
        btns.addView(smallButton(getString(R.string.log_clear), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LogBuffer.clear();
                refresh();
            }
        }), lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f, dp(4), 0, 0, 0));
        body.addView(btns, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0, dp(8)));

        logView = tv("", 11, MUTED, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        body.addView(logView);
    }

    private void buildAbout(LinearLayout root) {
        LinearLayout c = collapsible(root, getString(R.string.section_about), false, null).body;
        String ver = "?";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignore) {
        }
        c.addView(tv(getString(R.string.app_name) + " " + ver, 16, TEXT, true));
        TextView about = tv(getString(R.string.about_text), 12, MUTED, false);
        about.setPadding(0, dp(6), 0, dp(10));
        c.addView(about);
        TextView note = tv(getString(R.string.about_star_note), 12, TEXT, false);
        note.setPadding(0, 0, 0, dp(10));
        c.addView(note);
        c.addView(linkButton(getString(R.string.about_star), REPO_URL), lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0, dp(8)));
        c.addView(linkButton(getString(R.string.about_issues), REPO_URL + "/issues"), lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0, dp(8)));
        c.addView(linkButton(getString(R.string.about_linux), LINUX_URL), lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0, dp(10)));
        c.addView(tv(getString(R.string.section_interface), 11, MUTED, true), lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, dp(6), 0, dp(8)));
        animSwitch = new Switch(this);
        animSwitch.setChecked(prefs.getBoolean(Motion.KEY_ANIM, true));
        animSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                prefs.edit().putBoolean(Motion.KEY_ANIM, checked).apply();
                Motion.refresh(MainActivity.this, checked);
                power.refresh();
            }
        });
        c.addView(switchRow(getString(R.string.anim_title), getString(R.string.anim_desc), animSwitch),
                lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0, dp(14)));
        c.addView(tv(getString(R.string.copyright), 11, MUTED, false));
    }

    private TextView linkButton(String text, final String url) {
        return smallButton(text, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUrl(url);
            }
        });
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException e) {
            snack(getString(R.string.link_failed, url), true);
        }
    }

    private static final class Section {
        LinearLayout card;
        LinearLayout body;
        TextView summary;
        TextView arrow;
        Toggled onToggle;
        boolean open;

        /** Karti yukseklik animasyonuyla acar / kapatir, oku doner. */
        void set(boolean now) {
            if (now == open) return;
            open = now;
            Motion.setOpen(body, now);
            arrow.animate().rotation(now ? 90f : 0f).setDuration(Motion.ms(220)).start();
            if (onToggle != null) onToggle.run(now);
        }
    }

    private interface Toggled {
        void run(boolean open);
    }

    /** Basligina dokununca acilip kapanan kart; govde (body) basta kapalidir. */
    private Section collapsible(LinearLayout root, String title, boolean open, final Toggled onToggle) {
        final Section s = new Section();
        LinearLayout c = card();
        s.card = c;
        s.open = open;
        s.onToggle = onToggle;
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setClickable(true);
        head.setBackground(ripple(new GradientDrawable()));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(tv(title, 13, TEXT, true));
        s.summary = tv("", 12, MUTED, false);
        s.summary.setSingleLine(true);
        s.summary.setEllipsize(TextUtils.TruncateAt.END);
        s.summary.setVisibility(View.GONE); // ozet yazilmayan kartlarda bos satir yer kaplamasin
        titles.addView(s.summary);
        head.addView(titles, lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f, 0, 0, 0, 0));
        s.arrow = tv("\u25B8", 16, MUTED, true);
        s.arrow.setRotation(open ? 90f : 0f);
        head.addView(s.arrow);
        s.body = new LinearLayout(this);
        s.body.setOrientation(LinearLayout.VERTICAL);
        s.body.setPadding(0, dp(12), 0, 0);
        s.body.setVisibility(open ? View.VISIBLE : View.GONE);
        head.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                s.set(!s.open);
            }
        });
        c.addView(head);
        c.addView(s.body);
        root.addView(c, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0, dp(12)));
        return s;
    }

    private TextView tv(String text, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{CARD_HI, CARD});
        g.setCornerRadius(dp(18));
        g.setStroke(dp(1), STROKE);
        c.setBackground(g);
        return c;
    }

    private LinearLayout statCard(String label, TextView value, TextView sub) {
        LinearLayout c = card();
        c.addView(tv(label, 11, MUTED, true));
        value.setPadding(0, dp(6), 0, 0);
        c.addView(value);
        c.addView(sub);
        return c;
    }

    private LinearLayout miniStat(String label, TextView value) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        value.setGravity(Gravity.CENTER);
        c.addView(value);
        TextView l = tv(label, 11, MUTED, false);
        l.setGravity(Gravity.CENTER);
        c.addView(l);
        return c;
    }

    private TextView smallButton(String text, View.OnClickListener l) {
        TextView t = tv(text, 13, TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(10), dp(8), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(CHIP_OFF);
        g.setCornerRadius(dp(12));
        t.setBackground(ripple(g));
        t.setOnClickListener(l);
        Motion.pressable(t);
        return t;
    }

    /** Dokunma geri bildirimi: verilen arka planin ustune soluk beyaz dalga. */
    private android.graphics.drawable.Drawable ripple(android.graphics.drawable.Drawable bg) {
        return new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x30FFFFFF), bg, null);
    }

    private LinearLayout.LayoutParams lp(int w, int h, float weight, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = weight > 0
                ? new LinearLayout.LayoutParams(w, h, weight)
                : new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    private void choosePreset(String id) {
        String sel = Presets.byId(id).id;
        NetworkId.save(prefs, NetworkId.current(this), sel, true);
        BypassVpnService.applyLive(sel, "chosen by hand");
        if (BypassVpnService.running) {
            startService(new Intent(this, BypassVpnService.class).setAction(BypassVpnService.ACTION_VERIFY));
        }
        highlightPreset(sel);
        renderSavedNets();
    }

    private void showSummary(TextView t, String text) {
        t.setText(text);
        t.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void updateMethodSummary(String presetId) {
        if (methodSummary == null) return;
        String net = BypassVpnService.running ? BypassVpnService.networkLabel : null;
        showSummary(methodSummary, presetLabel(Presets.byId(presetId)) + (net != null ? " \u00b7 " + net : ""));
    }

    private void highlightPreset(String id) {
        String sel = Presets.byId(id).id;
        shownPreset = sel;
        updateMethodSummary(sel);
        for (int i = 0; i < Presets.ALL.length; i++) {
            boolean on = Presets.ALL[i].id.equals(sel);
            ColorStateList cur = presetBg[i].getColor();
            Motion.fill(presetBg[i], presetRows[i], cur != null ? cur.getDefaultColor() : CHIP_OFF, on ? ACCENT : CHIP_OFF);
            Motion.textColor(presetTitles[i], on ? ON_ACCENT : TEXT);
            Motion.textColor(presetDescs[i], on ? ON_ACCENT : MUTED);
            if (on && !presetOn[i] && presetRows[i].isShown()) Motion.pop(presetRows[i]);
            presetOn[i] = on;
        }
    }

    private void selectDns(String id, boolean fromUser) {
        String sel = DNS_IDS[0];
        for (String d : DNS_IDS) if (d.equals(id)) sel = d;
        if (fromUser) {
            prefs.edit().putString(BypassVpnService.KEY_DNS, sel).apply();
            notifyRestartNeeded();
        }
        for (int i = 0; i < DNS_IDS.length; i++) {
            boolean on = DNS_IDS[i].equals(sel);
            styleChip(dnsChips[i], on);
            if (on && fromUser) Motion.pop(dnsChips[i]);
        }
    }

    private void notifyRestartNeeded() {
        if (BypassVpnService.running) {
            snack(R.string.restart_needed, false);
        }
    }

    // ------------------------------------------------------------------ yasam dongusu

    @Override
    protected void onResume() {
        super.onResume();
        Motion.refresh(this, prefs.getBoolean(Motion.KEY_ANIM, true));
        power.refresh();
        handler.post(ticker);
    }

    @Override
    protected void onDestroy() {
        if (toaster != null) toaster.dispose();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    // ------------------------------------------------------------------ baslat / durdur

    private void onToggle() {
        power.performHapticFeedback(Build.VERSION.SDK_INT >= 30
                ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
        if (BypassVpnService.running) {
            pendingTarget = false;
            pendingUntil = SystemClock.elapsedRealtime() + 4000;
            refresh();
            startService(new Intent(this, BypassVpnService.class).setAction(BypassVpnService.ACTION_STOP));
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    refresh();
                }
            }, 400);
            return;
        }
        // Android 13+: durum bildirimi icin izin iste (reddedilse de VPN calisir)
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            return;
        }
        prepareAndStart();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_NOTIF) return;
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            snack(R.string.notif_denied, true);
        }
        prepareAndStart();
    }

    private void prepareAndStart() {
        Intent prep = VpnService.prepare(this);
        if (prep != null) {
            startActivityForResult(prep, REQ_VPN);
        } else {
            startVpn();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_VPN) return;
        if (resultCode == RESULT_OK) startVpn();
        else LogBuffer.add("VPN permission was not granted");
    }

    private void startVpn() {
        pendingTarget = true;
        pendingUntil = SystemClock.elapsedRealtime() + 8000;
        refresh();
        startService(new Intent(this, BypassVpnService.class).setAction(BypassVpnService.ACTION_START));
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        }, 500);
    }

    // ------------------------------------------------------------------ ekran guncelleme

    private void refresh() {
        boolean on = BypassVpnService.running;
        TunEngine e = BypassVpnService.current;
        long nowMs = SystemClock.elapsedRealtime();
        // baslatiliyor / durduruluyor: servis durumu degisene kadar (en cok birkac saniye) bekleme animasyonu
        boolean pending = pendingUntil != 0 && nowMs < pendingUntil && on != pendingTarget;
        if (pendingUntil != 0 && !pending) pendingUntil = 0;
        BypassVpnService.Check ck = BypassVpnService.check;

        PowerButton.Look look;
        if (pending) {
            look = PowerButton.Look.BUSY;
        } else if (!on) {
            look = PowerButton.Look.OFF;
        } else if (ck == BypassVpnService.Check.CHECKING) {
            look = PowerButton.Look.BUSY;
        } else if (ck == BypassVpnService.Check.IDLE) {
            look = PowerButton.Look.WARN;
        } else if (ck == BypassVpnService.Check.FAILED) {
            look = PowerButton.Look.FAILED;
        } else {
            look = PowerButton.Look.WORKING;
        }
        power.setLook(look);
        boolean showHint = on && !pending && ck == BypassVpnService.Check.FAILED;
        if ((hintBtn.getVisibility() == View.VISIBLE) != showHint) {
            hintBtn.setVisibility(showHint ? View.VISIBLE : View.GONE);
            if (showHint) Motion.enter(hintBtn, 0, dp(8));
        }

        if (on && e != null) {
            long now = nowMs;
            long down = e.bytesDown(), up = e.bytesUp();
            if (e != lastEngine) {
                lastEngine = e;
                lastDown = down;
                lastUp = up;
                lastTick = now;
                downRate = 0;
                upRate = 0;
                spark.clear();
            } else if (now - lastTick >= 900) {
                long dt = now - lastTick;
                downRate = (down - lastDown) * 1000 / dt;
                upRate = (up - lastUp) * 1000 / dt;
                lastDown = down;
                lastUp = up;
                lastTick = now;
                spark.push(downRate, upRate);
                updateLegend();
            }
            String activeId = BypassVpnService.activePreset;
            Presets.Preset active = Presets.byId(activeId != null ? activeId : prefs.getString(BypassVpnService.KEY_PRESET, "sni"));
            updateMethodSummary(active.id);
            if (!active.id.equals(shownPreset)) {
                highlightPreset(active.id);
                renderSavedNets();
            }
            Motion.swap(statusTitle, pending ? getString(R.string.status_stopping) : getString(BypassVpnService.checkTitle(ck)));
            Motion.textColor(statusTitle, pending ? AMBER : ck == BypassVpnService.Check.FAILED ? WARN
                    : (ck == BypassVpnService.Check.CHECKING || ck == BypassVpnService.Check.IDLE) ? AMBER : ACCENT);
            StringBuilder sub = new StringBuilder(Fmt.duration(now - BypassVpnService.startedAt));
            String net = BypassVpnService.networkLabel;
            if (net != null) sub.append(" · ").append(net);
            sub.append(" · ").append(presetLabel(active));
            String detail = BypassVpnService.checkDetail;
            if (detail != null && ck == BypassVpnService.Check.WORKING) sub.append(" · ").append(getString(R.string.check_sites, detail));
            if (ck == BypassVpnService.Check.FAILED) sub.append("\n").append(verdictText(BypassVpnService.verdict));
            statusSub.setText(sub.toString());
            downCount.set(down);
            downSub.setText("↓ " + Fmt.rate(downRate, getString(R.string.per_second)));
            upCount.set(up);
            upSub.setText("↑ " + Fmt.rate(upRate, getString(R.string.per_second)));
            connCount.set(e.activeTcp());
            splitCount.set(e.splitCount());
            dnsCount.set(e.dnsCount());
        } else {
            if (lastEngine != null) {
                spark.clear();
                rateLegend.setText("");
                spark.setContentDescription(null);
            }
            lastEngine = null;
            Motion.swap(statusTitle, pending && pendingTarget ? getString(R.string.status_starting) : getString(R.string.status_off));
            Motion.textColor(statusTitle, pending && pendingTarget ? AMBER : MUTED);
            statusSub.setText(pending && pendingTarget ? "" : getString(R.string.status_hint));
            downSub.setText("↓ —");
            upSub.setText("↑ —");
            connCount.set(0);
        }

        int v = LogBuffer.version();
        if (logOpen && v != shownLogVersion) {
            shownLogVersion = v;
            logView.setText(LogBuffer.dump(80));
        }
    }

    /** Grafigin ustundeki renkli nokta + anlik hiz yazisi; ekran okuyucu icin de ozet verir. */
    private void updateLegend() {
        String per = getString(R.string.per_second);
        String d = Fmt.rate(downRate, per), u = Fmt.rate(upRate, per);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int a = sb.length();
        sb.append("● ");
        sb.setSpan(new ForegroundColorSpan(Sparkline.DOWN), a, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("↓ ").append(d).append("   ");
        a = sb.length();
        sb.append("● ");
        sb.setSpan(new ForegroundColorSpan(Sparkline.UP), a, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("↑ ").append(u);
        rateLegend.setText(sb);
        spark.setContentDescription(getString(R.string.traffic_cd, d, u));
    }
}
