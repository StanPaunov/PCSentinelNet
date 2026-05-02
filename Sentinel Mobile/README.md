# Sentinel Mobile

Android companion-style version of the Sentinel monitoring app. It uses the same dark monitoring style, but it reports Android device data using APIs that normal Android apps are allowed to access.

Website: https://stanpaunov.github.io/PCSentinelNet/

Repository: https://github.com/StanPaunov/PCSentinelNet

## What It Monitors

- GPS coordinates, with hold-to-copy support
- Battery level, charging state, health, temperature, voltage, and estimated time remaining where Android exposes enough data
- Memory usage through `ActivityManager`
- Internal storage usage through `StatFs`
- Aggregate upload/download rate through `TrafficStats`
- Active network transport, VPN state, metered state, and interface inventory
- Network settings shortcut
- Security settings shortcut
- Storage settings shortcut
- Device settings shortcut
- Device information such as manufacturer, model, Android version, build, and security patch
- Security posture checks where Android allows access:
  - secure screen lock state
  - USB debugging setting
  - unknown app install permission for this app
  - VPN activity
  - Private DNS mode
  - developer options state

## Android Limits

Android does not allow a normal app to inspect global TCP listening ports, firewall profiles, Microsoft Defender, or all running process CPU data like Windows does. Those features would require root access, device-owner management APIs, or a separate Windows PC companion service.

The app does not request internet access or package-install permission. Location is used for the GPS coordinate card. Copied GPS coordinates are automatically cleared from the clipboard when unchanged.

## Open In Android Studio

1. Open Android Studio.
2. Choose **Open**.
3. Select the `Sentinel Mobile` folder.
4. Let Android Studio sync Gradle.
5. Run the `app` configuration on an emulator or Android device.

Default refresh interval is `1 minute`; the app also supports `5 seconds`, `30 seconds`, and `Never`.
