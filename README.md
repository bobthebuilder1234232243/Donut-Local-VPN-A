<p align="center"><img src="docs/banner.svg" alt="Donut Local VPN: open the sites your ISP blocks. No root. No remote server." width="100%"></p>

# Donut Local VPN

**Donut Local VPN** is a local-only Android VPN app with a single button. It brings up a local
`VpnService` TUN interface, splits the first packet of an HTTPS connection so a DPI filter cannot
read the site name, and resolves DNS over HTTPS. Everything happens on the phone: there is no
remote server, no account, no analytics and no update check.

Project page: **https://techperson1234.github.io/Donut-VPN/**

## Using it

1. Install the APK and open **Donut Local VPN**.
2. Press the big button and approve Android's VPN permission once.
3. The label under the button reads **Connected**. Press the button again to stop; it reads
   **Disconnected**.

There is nothing else to configure. The local core is initialized when the app starts, using the
built-in split method and Quad9 DNS-over-HTTPS (with Cloudflare and Google as fallbacks).

## What it does

- Runs entirely on the device through Android's `VpnService`; no remote server is involved.
- Splits the TLS ClientHello at the start of the SNI, so the site name is not in one packet.
- Blocks QUIC (UDP 443) so browsers fall back to TCP + TLS, where splitting applies.
- Resolves DNS over HTTPS (Quad9 by default).
- Excludes the app from its own tunnel, so there is no loop.
- All other traffic leaves over the phone's own connection.

## What was removed

This fork keeps SplitGate's local packet engine but drops the surrounding product surface:
settings, method/DPI tuning, per-app selection, site lists, logs, the connection test and report
tools, the Quick Settings tile, the language picker, and automatic connection probing. No code
path contacts a remote control plane.

## Building

Needs JDK 17 or newer and the Android SDK (platform 36, build-tools 36). The Gradle wrapper
downloads Gradle by itself.

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Engine self-test (no Android needed):

```bash
./tools/run-selftest.sh
```

Instrumented UI smoke test (needs a running emulator or device):

```bash
./gradlew connectedDebugAndroidTest
```

It pre-grants VPN consent through `appops`, then taps Start and Stop and checks that the status
label reaches `Connected` and returns to `Disconnected`.

## Project structure

```
app/src/main/java/com/donutvpn/donutlocalvpn/
  MainActivity.java        the single screen: one button, one status label, credits
  PowerButton.java         the big round button
  Motion.java              animation helpers
  LocalCore.java           built-in engine defaults, initialized on launch
  BypassVpnService.java    VpnService + TUN I/O + foreground notification + network changes
  AndroidTtl.java          TTL of the app's own socket
  engine/                  pure Java packet engine (no Android dependency)
```

## Privacy

- No analytics, crash reporting, ads or accounts.
- No remote server and no outbound control-plane calls.
- DNS is encrypted (DoH); HTTPS traffic keeps its own TLS.
- Nothing is stored but the app's fixed defaults, and Android backups are off.

## Credits

Donut Local VPN — by Donut VPN. Originally based on SplitGate by gmtr244.

The original project is at https://github.com/gmtr244/splitgate-android.

## License

GNU General Public License v3.0 or later. See [LICENSE](LICENSE), which is unchanged; keep the
original copyright notices when redistributing.
