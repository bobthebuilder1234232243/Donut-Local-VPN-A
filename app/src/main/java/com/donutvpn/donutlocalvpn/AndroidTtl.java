// Copyright (C) 2026 Donut VPN
// SPDX-License-Identifier: GPL-3.0-or-later
package com.donutvpn.donutlocalvpn;

import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import com.donutvpn.donutlocalvpn.engine.SocketTtl;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.Socket;

/** Root gerektirmez: kendi soketimizin TTL'ini setsockopt ile degistirir. */
final class AndroidTtl implements SocketTtl {
    static final AndroidTtl INSTANCE = new AndroidTtl();

    private AndroidTtl() {}

    @Override
    public boolean set(Socket s, int ttl) {
        // Os.setsockoptInt Android 8'den (API 26) once yok: eski surumde NoSuchMethodError olurdu; TTL ayarlanamadi sayilir.
        if (Build.VERSION.SDK_INT < 26) return false;
        ParcelFileDescriptor pfd = null;
        try {
            // fromSocket bir kopya (dup) verir; ayni soketi gosterir, kapatmak soketi kapatmaz.
            pfd = ParcelFileDescriptor.fromSocket(s);
            boolean v4ok, v6ok;
            try {
                Os.setsockoptInt(pfd.getFileDescriptor(), OsConstants.IPPROTO_IP, OsConstants.IP_TTL, ttl);
                v4ok = true;
            } catch (ErrnoException e) {
                v4ok = false;
            }
            try {
                Os.setsockoptInt(pfd.getFileDescriptor(), OsConstants.IPPROTO_IPV6, OsConstants.IPV6_UNICAST_HOPS, ttl);
                v6ok = true;
            } catch (ErrnoException e) {
                v6ok = false;
            }
            // IPv4 hedefte (IPv4-mapped dahil) yalnizca IP_TTL gecerlidir; hop limiti sadece gercek IPv6 hedefi etkiler.
            return s.getInetAddress() instanceof Inet6Address ? v6ok : v4ok;
        } catch (RuntimeException e) {
            return false;
        } finally {
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (IOException ignore) {
                }
            }
        }
    }
}
