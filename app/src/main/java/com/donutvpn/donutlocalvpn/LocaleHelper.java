// Copyright (C) 2026 Donut VPN
// SPDX-License-Identifier: GPL-3.0-or-later
package com.donutvpn.donutlocalvpn;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * Applies the in-app language choice to every activity and to the VPN service. An empty tag
 * follows the device language, so the default remains whatever Android is already using.
 */
final class LocaleHelper {
    private LocaleHelper() {
    }

    static Context wrap(Context base) {
        String tag = AppSettings.languageTag(base);
        if (tag.isEmpty()) return base;
        Locale locale = Locale.forLanguageTag(tag);
        Locale.setDefault(locale);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);
        return base.createConfigurationContext(config);
    }

    /** Restarts the task so every screen and the service pick up the new language. */
    static void restart(Activity activity) {
        Intent intent = new Intent(activity, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_SKIP_AUTO_CONNECT, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
