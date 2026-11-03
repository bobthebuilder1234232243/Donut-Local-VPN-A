// Copyright (C) 2026 Donut VPN
// SPDX-License-Identifier: GPL-3.0-or-later
package com.donutvpn.donutlocalvpn;

import com.donutvpn.donutlocalvpn.engine.DohResolver;
import com.donutvpn.donutlocalvpn.engine.Presets;
import com.donutvpn.donutlocalvpn.engine.Strategy;

/**
 * Built-in local core settings. Everything runs in this process: the TUN reader, the packet
 * splitter and DNS-over-HTTPS. There is no remote server and nothing to configure.
 */
final class LocalCore {
    /** Built-in split method: cut the TLS ClientHello at byte 1 and at the start of the SNI. */
    private static final String DEFAULT_METHOD = "sni";
    /** Built-in resolver: Quad9 DoH (Cloudflare and Google stay as fallbacks). */
    private static final String DEFAULT_DNS = "quad9";

    private static volatile Strategy strategy;

    private LocalCore() {
    }

    /** Called when the app starts, so the core is ready before the button is pressed. */
    static void init() {
        strategy();
    }

    static Strategy strategy() {
        Strategy s = strategy;
        if (s == null) {
            synchronized (LocalCore.class) {
                s = strategy;
                if (s == null) {
                    s = new Strategy();
                    Presets.apply(s, Presets.byId(DEFAULT_METHOD));
                    s.blockQuic = true;
                    strategy = s;
                }
            }
        }
        return s;
    }

    static DohResolver dns() {
        return new DohResolver(DohResolver.endpointsFor(DEFAULT_DNS));
    }
}
