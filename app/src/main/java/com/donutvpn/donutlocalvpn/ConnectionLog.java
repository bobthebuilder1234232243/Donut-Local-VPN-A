// Copyright (C) 2026 Donut VPN
// SPDX-License-Identifier: GPL-3.0-or-later
package com.donutvpn.donutlocalvpn;

import com.donutvpn.donutlocalvpn.engine.EngineLog;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * A small in-memory log for the settings screen. It is deliberately not persisted: restarting
 * the app starts a fresh connection log, and nothing is uploaded anywhere.
 */
final class ConnectionLog {
    private static final int MAX_LINES = 400;
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();

    private static final EngineLog ENGINE = new EngineLog() {
        @Override
        public void log(String msg) {
            add(msg);
        }
    };

    private ConnectionLog() {
    }

    static void add(String message) {
        if (message == null || message.trim().isEmpty()) return;
        synchronized (ConnectionLog.class) {
            LINES.addLast(stamp() + "  " + message);
            while (LINES.size() > MAX_LINES) LINES.removeFirst();
        }
    }

    private static String stamp() {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    static void clear() {
        synchronized (ConnectionLog.class) {
            LINES.clear();
        }
    }

    static String snapshot() {
        synchronized (ConnectionLog.class) {
            StringBuilder out = new StringBuilder();
            for (String line : LINES) {
                if (out.length() > 0) out.append('\n');
                out.append(line);
            }
            return out.toString();
        }
    }

    static EngineLog engine() {
        return ENGINE;
    }
}
