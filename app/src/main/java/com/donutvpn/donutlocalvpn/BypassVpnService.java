// Copyright (C) 2026 Donut VPN
// SPDX-License-Identifier: GPL-3.0-or-later
package com.donutvpn.donutlocalvpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;

import com.donutvpn.donutlocalvpn.engine.DirectConnector;
import com.donutvpn.donutlocalvpn.engine.DohResolver;
import com.donutvpn.donutlocalvpn.engine.PacketIo;
import com.donutvpn.donutlocalvpn.engine.TunEngine;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * The only service in the app: brings up the local TUN interface and runs the packet splitting
 * engine in this process. No remote endpoint is contacted. DNS lookups use the built-in
 * DNS-over-HTTPS resolver; all other traffic leaves over the phone's own network.
 */
public class BypassVpnService extends VpnService {
    static final String ACTION_START = "com.donutvpn.donutlocalvpn.action.START";
    static final String ACTION_STOP = "com.donutvpn.donutlocalvpn.action.STOP";

    private static final String CHANNEL_ID = "vpn";
    private static final int NOTIF_ID = 1;

    /** Tunnel state, read by the single screen. */
    static volatile boolean running;
    static volatile boolean starting;
    /** The addresses actually applied to the running tunnel, or null when it is not up. */
    static volatile String activeDeviceIp;
    static volatile String activeTunnelIp;
    static volatile long sessionStartedAt;

    static boolean isOn() {
        return running || starting;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    private ParcelFileDescriptor tun;
    private FdIo tunIo;
    private volatile TunEngine engine;
    private DohResolver doh;
    private Thread engineThread;
    private ConnectivityManager.NetworkCallback netCallback;
    private Network lastNetwork;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopVpn();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) {
            startVpn();
            return START_NOT_STICKY;
        }
        stopSelf();
        return START_NOT_STICKY;
    }

    @Override
    public void onRevoke() {
        stopVpn();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ tunnel

    private synchronized void startVpn() {
        if (engineThread != null) return;
        starting = true;
        running = false;
        activeDeviceIp = null;
        activeTunnelIp = null;
        sessionStartedAt = 0;
        String deviceIp = AppSettings.deviceIp(this);
        String tunnelIp = AppSettings.tunnelIp(this);
        ConnectionLog.add(getString(R.string.log_start_requested));
        startInForeground(buildNotification(false));

        Builder b = new Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(1500)
                .addAddress(deviceIp, 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(tunnelIp);
        try {
            // Our own traffic never enters the tunnel, so there is no loop.
            b.addDisallowedApplication(getPackageName());
        } catch (PackageManager.NameNotFoundException ignore) {
        }

        ParcelFileDescriptor pfd;
        try {
            pfd = b.establish();
        } catch (RuntimeException e) {
            pfd = null;
        }
        if (pfd == null) {
            // The VPN permission was not granted or the system refused the interface.
            ConnectionLog.add(getString(R.string.log_vpn_failed));
            activeDeviceIp = null;
            activeTunnelIp = null;
            sessionStartedAt = 0;
            starting = false;
            closeForeground();
            stopSelf();
            return;
        }

        tun = pfd;
        FdIo io = new FdIo(pfd.getFileDescriptor());
        tunIo = io;
        doh = LocalCore.dns();
        final TunEngine e = new TunEngine(io, LocalCore.strategy(), doh, new DirectConnector(), ConnectionLog.engine());
        e.setSocketTtl(AndroidTtl.INSTANCE);
        engine = e;
        engineThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    e.run();
                } finally {
                    onEngineEnded();
                }
            }
        }, "donut-engine");
        activeDeviceIp = deviceIp;
        activeTunnelIp = tunnelIp;
        sessionStartedAt = SystemClock.elapsedRealtime();
        engineThread.start();
        ConnectionLog.add(getString(R.string.log_tun_up, deviceIp, tunnelIp));
        watchNetwork();
        starting = false;
        running = true;
        updateNotification(true);
    }

    private synchronized void stopVpn() {
        boolean wasOn = isOn();
        if (wasOn) ConnectionLog.add(getString(R.string.log_stop_requested));
        TunEngine e = engine;
        if (e != null) {
            e.stop(); // the engine thread ends and onEngineEnded() cleans up
        } else {
            activeDeviceIp = null;
            activeTunnelIp = null;
            sessionStartedAt = 0;
            starting = false;
            running = false;
            if (wasOn) ConnectionLog.add(getString(R.string.log_stopped));
            closeForeground();
            stopSelf();
        }
    }

    private synchronized void onEngineEnded() {
        if (netCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            try {
                if (cm != null) cm.unregisterNetworkCallback(netCallback);
            } catch (RuntimeException ignore) {
            }
            netCallback = null;
        }
        lastNetwork = null;
        if (tun != null) {
            try {
                tun.close();
            } catch (IOException ignore) {
            }
            tun = null;
        }
        if (doh != null) {
            doh.close();
            doh = null;
        }
        if (tunIo != null) {
            tunIo.release();
            tunIo = null;
        }
        engine = null;
        engineThread = null;
        activeDeviceIp = null;
        activeTunnelIp = null;
        sessionStartedAt = 0;
        running = false;
        starting = false;
        ConnectionLog.add(getString(R.string.log_stopped));
        closeForeground();
        stopSelf();
    }

    /**
     * Keeps the real network underneath the tunnel up to date. When the phone switches between
     * Wi-Fi and mobile data, stale connections are reset so apps reconnect at once.
     */
    private void watchNetwork() {
        final ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return;
        ConnectivityManager.NetworkCallback cb = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network n) {
                onNetwork(n, cm.getNetworkCapabilities(n));
            }

            @Override
            public void onCapabilitiesChanged(Network n, NetworkCapabilities nc) {
                onNetwork(n, nc);
            }

            @Override
            public void onLost(Network n) {
                synchronized (BypassVpnService.this) {
                    if (!n.equals(lastNetwork)) return;
                    lastNetwork = null;
                    try {
                        setUnderlyingNetworks(null);
                    } catch (RuntimeException ignore) {
                    }
                }
            }
        };
        try {
            // The app is outside its own VPN, so this is the physical default network.
            cm.registerDefaultNetworkCallback(cb);
            netCallback = cb;
        } catch (RuntimeException ignore) {
        }
    }

    private void onNetwork(Network n, NetworkCapabilities nc) {
        // On recent Android versions this callback briefly reports our own VPN network too.
        if (nc == null || nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                || !nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) return;
        final TunEngine e;
        final DohResolver d;
        synchronized (this) {
            if (n.equals(lastNetwork)) return;
            lastNetwork = n;
            try {
                setUnderlyingNetworks(new Network[]{n});
            } catch (RuntimeException ignore) {
            }
            e = engine;
            d = doh;
        }
        if (e == null) return;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                e.resetConnections("network changed");
                if (d != null) d.flush();
            }
        }, "donut-net");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ notification

    private void startInForeground(Notification n) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (RuntimeException ignore) {
            // The VPN still works if the notification is refused (for example no permission).
        }
    }

    private void updateNotification(boolean connected) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, buildNotification(connected));
        } catch (RuntimeException ignore) {
        }
    }

    private void closeForeground() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (RuntimeException ignore) {
        }
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTIF_ID);
        } catch (RuntimeException ignore) {
        }
    }

    private Notification buildNotification(boolean connected) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel),
                        NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), flags);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, BypassVpnService.class).setAction(ACTION_STOP), flags);

        Notification.Builder nb = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        nb.setSmallIcon(R.drawable.ic_stat_vpn)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(connected ? R.string.status_connected : R.string.status_starting))
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setColor(0xFF2EE6A8)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(0, getString(R.string.notif_stop), stop);
        if (Build.VERSION.SDK_INT < 26) nb.setPriority(Notification.PRIORITY_LOW);
        return nb.build();
    }

    // ------------------------------------------------------------------ TUN I/O

    /**
     * TUN file descriptor I/O. The descriptor is non-blocking; reads wait in poll(), so
     * stopping the tunnel wakes the reader immediately.
     */
    private static final class FdIo implements PacketIo {
        private final FileDescriptor fd;
        private volatile boolean closed;
        private FileDescriptor wakeR, wakeW; // close() wakes the reader through this pipe

        FdIo(FileDescriptor fd) {
            this.fd = fd;
            try {
                // Pipe ends could leak into child processes; the app starts none.
                FileDescriptor[] pipe = Os.pipe();
                wakeR = pipe[0];
                wakeW = pipe[1];
            } catch (ErrnoException e) {
                wakeR = null;
                wakeW = null;
            }
        }

        @Override
        public int read(byte[] buf) throws IOException {
            final FileDescriptor wake = wakeR;
            while (!closed) {
                try {
                    StructPollfd p = new StructPollfd();
                    p.fd = fd;
                    p.events = (short) OsConstants.POLLIN;
                    StructPollfd[] set;
                    if (wake != null) {
                        StructPollfd w = new StructPollfd();
                        w.fd = wake;
                        w.events = (short) OsConstants.POLLIN;
                        set = new StructPollfd[]{p, w};
                    } else {
                        set = new StructPollfd[]{p};
                    }
                    // 30 s upper bound in case the pipe ever fails, so "stop" still finishes.
                    int r = Os.poll(set, wake != null ? 30_000 : 250);
                    if (r <= 0) continue;
                    if (wake != null && (set[1].revents & OsConstants.POLLIN) != 0) return -1;
                    if ((p.revents & (OsConstants.POLLERR | OsConstants.POLLHUP | OsConstants.POLLNVAL)) != 0) return -1;
                    int n = Os.read(fd, buf, 0, buf.length);
                    if (n > 0) return n;
                    if (n < 0) return -1;
                } catch (ErrnoException e) {
                    if (e.errno == OsConstants.EAGAIN || e.errno == OsConstants.EINTR) continue;
                    throw new IOException(e);
                }
            }
            return -1;
        }

        @Override
        public void write(byte[] buf, int off, int len) throws IOException {
            for (int tries = 0; tries < 50 && !closed; tries++) {
                try {
                    Os.write(fd, buf, off, len);
                    return;
                } catch (ErrnoException e) {
                    if (e.errno == OsConstants.EAGAIN || e.errno == OsConstants.EINTR) {
                        waitWritable();
                        continue;
                    }
                    throw new IOException(e);
                }
            }
            if (!closed) throw new IOException("TUN write timed out");
        }

        private void waitWritable() {
            try {
                StructPollfd p = new StructPollfd();
                p.fd = fd;
                p.events = (short) OsConstants.POLLOUT;
                Os.poll(new StructPollfd[]{p}, 100);
            } catch (ErrnoException ignore) {
            }
        }

        @Override
        public synchronized void close() {
            closed = true;
            if (wakeW != null) {
                try {
                    Os.write(wakeW, new byte[]{1}, 0, 1);
                } catch (ErrnoException | java.io.InterruptedIOException ignore) {
                }
            }
        }

        /** Closes the pipe ends after the reader has finished (same lock, so no reused fd is written). */
        synchronized void release() {
            closed = true;
            for (FileDescriptor f : new FileDescriptor[]{wakeR, wakeW}) {
                if (f == null) continue;
                try {
                    Os.close(f);
                } catch (ErrnoException ignore) {
                }
            }
            wakeR = null;
            wakeW = null;
        }
    }
}
