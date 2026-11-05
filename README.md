<p align="center"><img src="docs/banner.png" alt="Donut Local VPN: a private local tunnel with one button." width="100%"></p>

# Donut Local VPN

**Donut Local VPN** is a local-only Android VPN app with a single Start/Stop button. It brings up a
local `VpnService` TUN interface, splits the first packet of an HTTPS connection so a DPI filter
cannot read the site name, and resolves DNS over HTTPS. Everything happens on the phone: there is
no remote server, no account, no analytics and no update check.

Project page: **https://techperson1234.github.io/Donut-VPN/**

## Using it

1. Install the APK and open **Donut Local VPN**.
2. Press the big button and approve Android's VPN permission once.
3. The label under the button reads **Connected**. Press the button again to stop; it reads
   **Disconnected**.
4. While connected, the session card shows the device IP, the tunnel IP, the DPI status and how
   long the current session has been running. It disappears when you disconnect. Tap the gear to
   open settings.

## Settings

- **Auto-connect on launch** starts the local tunnel when the app opens.
- **Deep Packet Inspection (DPI)** sets the DPI status shown in the connected session card.
- **Network configuration** lets you change the device IP (the TUN address) and the tunnel IP
  (the local DNS gateway). Both must be valid IPv4 host addresses in the same `/24` subnet, and
  they must differ. The defaults are `10.7.0.1` and `10.7.0.2`. Reconnect the tunnel after
  changing them.
- **Language** switches the interface between English, Spanish, Italian, Polish, Korean,
  Traditional Chinese and French. The system language is used until a choice is made.
- **Connection log** shows recent local activity with timestamps. It is capped in memory for the
  current app process and never written to disk or uploaded. You can clear it at any time.

All settings stay in the app's private preferences on the device.

## What it does

- Runs entirely on the device through Android's `VpnService`; no remote server is involved.
- Splits the TLS ClientHello at the start of the SNI, so the site name is not in one packet.
- Blocks QUIC (UDP 443) so browsers fall back to TCP + TLS, where splitting applies.
- Resolves DNS over HTTPS (Quad9 by default).
- Excludes the app from its own tunnel, so there is no loop.
- All other traffic leaves over the phone's own connection.

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
  MainActivity.java        Start/Stop button, session details, settings entry
  SettingsActivity.java    auto-connect, DPI, addresses, languages and connection log
  AppSettings.java         local preferences and IPv4 validation
  ConnectionLog.java       bounded in-memory log shared with the engine
  LocaleHelper.java        in-app language selection
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
- Preferences and the optional connection log stay on the device. The log is memory-only, and
  Android backups are off.

## Credits

Donut Local VPN — by Donut VPN.

## License

GNU General Public License v3.0 or later. See [LICENSE](LICENSE); keep the original copyright
notices when redistributing.
