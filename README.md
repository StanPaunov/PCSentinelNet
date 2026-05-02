# PC Sentinel NET

PC Sentinel NET is a Windows desktop monitoring app with a matching Android companion project, Sentinel Mobile.

Website: https://stanpaunov.github.io/PCSentinelNet/

GitHub Releases: https://github.com/StanPaunov/PCSentinelNet/releases

## Desktop App

The Windows desktop app monitors local PC health, network activity, listening TCP ports, disk information, and basic Windows security status.

### Requirements

- Windows 10 or Windows 11
- .NET 8 Desktop Runtime to run the packaged app
- .NET 8 SDK to build from source

### Desktop Features

- Dark mode by default, with a Light Mode button
- Refresh interval selector: 5 seconds, 30 seconds, 1 minute, or Never
- CPU, memory, system disk, and network throughput summary cards
- Top processes table with internet search by hover, double-click, or right-click
- Network & Firewall tab with active interfaces, listening TCP ports, and TCP connections
- Listening TCP ports show owning process, PID, risk level, reason, and a danger summary
- Security tab with Defender/firewall checks and an Open Security Settings button
- Disk & Info tab with volume, physical disk, hardware, OS, and installed software information
- Buttons for Disk Management and Windows Storage Settings

## Sentinel Mobile

Sentinel Mobile is the Android version with the same dark monitoring style. Android limits what normal apps can inspect, so the mobile app reports device, battery, GPS, storage, network, and security checks that Android allows.

Android project folder: `Sentinel Mobile`

### Mobile Features

- Dark mode by default
- Refresh interval selector: 5 seconds, 30 seconds, 1 minute, or Never
- Swipe or tap between Overview, Network, Security, Storage, and Device tabs
- GPS coordinates with hold-to-copy support
- Battery information and estimated time remaining when Android exposes enough data
- Memory, storage, and aggregate network throughput cards
- Network status, interface inventory, and button to Android Network Settings
- Security checks and button to Android Security Settings
- Storage summary and button to Android Storage Settings
- Device information and button to Android Device Settings

## Security Notes

Both apps are local visibility tools. They do not replace antivirus, do not block traffic, and do not upload monitoring data.

The Android app does not request internet access or package-install permission. Location is used only for the GPS coordinate card, and copied GPS coordinates are automatically cleared from the clipboard when unchanged.
