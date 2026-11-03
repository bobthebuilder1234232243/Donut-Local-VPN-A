// Copyright (C) 2026 Donut VPN
// SPDX-License-Identifier: GPL-3.0-or-later
package com.donutvpn.donutlocalvpn;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * End-to-end smoke test for the one-button screen: press Start, wait for the local TUN to come
 * up, press Stop, and check that the screen returns to Disconnected.
 *
 * <p>VPN consent is pre-granted with the shell app-op so the system consent dialog cannot make
 * the test flaky. The notification permission is granted the same way on Android 13+. Run on an
 * emulator or a debuggable device with shell access:
 *
 * <pre>./gradlew connectedDebugAndroidTest</pre>
 */
@RunWith(AndroidJUnit4.class)
public class StartStopSmokeTest {
    private static final long CONNECT_TIMEOUT_MS = 20_000;
    private static final long DISCONNECT_TIMEOUT_MS = 10_000;

    private ActivityScenario<MainActivity> scenario;

    @Before
    public void grantPermissionsAndLaunch() throws Exception {
        Context context = targetContext();
        String pkg = context.getPackageName();

        // Keep the test deterministic: no auto-connect and no language override from a previous run.
        context.getSharedPreferences(AppSettings.PREFS, Context.MODE_PRIVATE).edit().clear().commit();

        String out = shell("appops set " + pkg + " ACTIVATE_VPN allow");
        if (Build.VERSION.SDK_INT >= 33) {
            shell("pm grant " + pkg + " android.permission.POST_NOTIFICATIONS");
        }
        assertNull("VPN consent was not granted; appops output: " + out,
                VpnService.prepare(context));

        scenario = ActivityScenario.launch(MainActivity.class);
    }

    @After
    public void stopTunnelAndClose() {
        Context context = targetContext();
        context.stopService(new Intent(context, BypassVpnService.class));
        if (scenario != null) {
            scenario.close();
            scenario = null;
        }
    }

    @Test
    public void startReachesConnectedAndStopReturnsToDisconnected() {
        assertStatus(R.string.status_disconnected);

        clickPower();
        awaitStatus(R.string.status_connected, CONNECT_TIMEOUT_MS);
        assertStatus(R.string.status_connected);

        clickPower();
        awaitStatus(R.string.status_disconnected, DISCONNECT_TIMEOUT_MS);
        assertStatus(R.string.status_disconnected);
    }

    // ------------------------------------------------------------------ helpers

    private void clickPower() {
        scenario.onActivity(activity -> {
            View power = activity.findViewById(R.id.power_button);
            assertNotNull("Start/Stop button is missing", power);
            assertTrue("Start/Stop button did not handle the click", power.performClick());
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private String statusText() {
        AtomicReference<String> text = new AtomicReference<>("");
        scenario.onActivity(activity -> {
            TextView status = activity.findViewById(R.id.status_label);
            assertNotNull("Status label is missing", status);
            text.set(status.getText().toString());
        });
        return text.get();
    }

    private void assertStatus(int stringId) {
        String expected = targetContext().getString(stringId);
        String actual = statusText();
        if (!expected.equals(actual)) {
            fail("Expected status \"" + expected + "\" but was \"" + actual + "\"");
        }
    }

    private void awaitStatus(int stringId, long timeoutMs) {
        String expected = targetContext().getString(stringId);
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        String last = "";
        while (SystemClock.uptimeMillis() < deadline) {
            last = statusText();
            if (expected.equals(last)) return;
            SystemClock.sleep(200);
        }
        fail("Status never became \"" + expected + "\" within " + timeoutMs
                + " ms; last was \"" + last + "\"");
    }

    private static Context targetContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    /** Runs a shell command through UiAutomation and returns its drained output. */
    private static String shell(String command) throws IOException {
        ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(command);
        StringBuilder out = new StringBuilder();
        try (FileInputStream in = new FileInputStream(pfd.getFileDescriptor())) {
            byte[] buf = new byte[512];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
        } finally {
            pfd.close();
        }
        return out.toString().trim();
    }
}
