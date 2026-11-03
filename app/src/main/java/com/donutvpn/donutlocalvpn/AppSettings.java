// Copyright (C) 2026 Donut VPN
// SPDX-License-Identifier: GPL-3.0-or-later
package com.donutvpn.donutlocalvpn;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Local preferences for the app: the two tunnel addresses, auto-connect and the chosen language.
 * Nothing here ever leaves the device.
 */
final class AppSettings {
    static final String PREFS = "donut_settings";
    static final String DEFAULT_DEVICE_IP = "10.7.0.1";
    static final String DEFAULT_TUNNEL_IP = "10.7.0.2";

    private static final String KEY_DEVICE_IP = "device_ip";
    private static final String KEY_TUNNEL_IP = "tunnel_ip";
    private static final String KEY_AUTO_CONNECT = "auto_connect";
    private static final String KEY_LANGUAGE = "language";

    enum Validation {
        OK,
        INVALID_DEVICE,
        INVALID_TUNNEL,
        DIFFERENT_SUBNET,
        SAME_ADDRESS
    }

    private AppSettings() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String deviceIp(Context context) {
        String device = prefs(context).getString(KEY_DEVICE_IP, DEFAULT_DEVICE_IP).trim();
        String tunnel = prefs(context).getString(KEY_TUNNEL_IP, DEFAULT_TUNNEL_IP).trim();
        return validateNetwork(device, tunnel) == Validation.OK ? device : DEFAULT_DEVICE_IP;
    }

    static String tunnelIp(Context context) {
        String device = prefs(context).getString(KEY_DEVICE_IP, DEFAULT_DEVICE_IP).trim();
        String tunnel = prefs(context).getString(KEY_TUNNEL_IP, DEFAULT_TUNNEL_IP).trim();
        return validateNetwork(device, tunnel) == Validation.OK ? tunnel : DEFAULT_TUNNEL_IP;
    }

    static boolean setNetwork(Context context, String device, String tunnel) {
        device = device == null ? "" : device.trim();
        tunnel = tunnel == null ? "" : tunnel.trim();
        if (validateNetwork(device, tunnel) != Validation.OK) return false;
        prefs(context).edit()
                .putString(KEY_DEVICE_IP, device)
                .putString(KEY_TUNNEL_IP, tunnel)
                .apply();
        return true;
    }

    static void resetNetwork(Context context) {
        prefs(context).edit()
                .putString(KEY_DEVICE_IP, DEFAULT_DEVICE_IP)
                .putString(KEY_TUNNEL_IP, DEFAULT_TUNNEL_IP)
                .apply();
    }

    static boolean autoConnect(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_CONNECT, false);
    }

    static void setAutoConnect(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_CONNECT, enabled).apply();
    }

    /** Empty means "follow the system language". */
    static String languageTag(Context context) {
        String tag = prefs(context).getString(KEY_LANGUAGE, "");
        return tag == null ? "" : tag;
    }

    static void setLanguageTag(Context context, String tag) {
        prefs(context).edit().putString(KEY_LANGUAGE, tag == null ? "" : tag).apply();
    }

    /**
     * The TUN needs a usable IPv4 host address; the DNS gateway has to sit in the same /24 so a
     * mistyped address cannot quietly point DNS somewhere outside the local tunnel.
     */
    static Validation validateNetwork(String device, String tunnel) {
        int[] d = parseIpv4(device);
        int[] t = parseIpv4(tunnel);
        if (!isHostAddress(d)) return Validation.INVALID_DEVICE;
        if (!isHostAddress(t)) return Validation.INVALID_TUNNEL;
        if (d[0] != t[0] || d[1] != t[1] || d[2] != t[2]) return Validation.DIFFERENT_SUBNET;
        if (d[3] == t[3]) return Validation.SAME_ADDRESS;
        return Validation.OK;
    }

    private static int[] parseIpv4(String value) {
        if (value == null) return null;
        String[] parts = value.trim().split("\\.", -1);
        if (parts.length != 4) return null;
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) return null;
            for (int j = 0; j < part.length(); j++) {
                char c = part.charAt(j);
                if (c < '0' || c > '9') return null;
            }
            int n;
            try {
                n = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return null;
            }
            if (n > 255) return null;
            out[i] = n;
        }
        return out;
    }

    private static boolean isHostAddress(int[] a) {
        if (a == null) return false;
        // Network 0/8, loopback and multicast are never valid TUN endpoints.
        if (a[0] == 0 || a[0] == 127 || a[0] >= 224) return false;
        // A /24 host address cannot be the network or broadcast address.
        return a[3] != 0 && a[3] != 255;
    }
}
