# PC Sentinel NET

PC Sentinel NET is a Windows desktop monitoring app with an Android companion project included in this repository.

The product direction is:

- **PC Sentinel NET**: Windows desktop app that monitors the Windows PC.
- **PC Sentinel Mobile**: Android companion app for viewing Windows PC information from a phone.
- **Sentinel Phone**: separate future Android app for monitoring Android phones themselves.

Website: https://stanpaunov.github.io/PCSentinelNet/

GitHub Releases: https://github.com/StanPaunov/PCSentinelNet/releases

Android project: `Sentinel Mobile`

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

## PC Sentinel Mobile

PC Sentinel Mobile is the Android companion app inside this repository. It is for checking Windows PC information from Android, not for monitoring Android phones. It can monitor one Windows PC in the free version by connecting to a LAN PC agent endpoint, and it includes a visible Pro path for future multi-client monitoring.

Expected PC agent endpoint:

```text
http://PC-IP-ADDRESS:8787/api/status
```

The Windows PC app still needs to expose that endpoint before the Android app can show live PC data.

### Mobile Features

- Dark mode by default
- Refresh interval selector: 5 seconds, 30 seconds, 1 minute, or Never
- Swipe or tap between PC, Network, Security, Clients, and Pro tabs
- Set a PC Agent URL for one-PC LAN monitoring
- PC cards for CPU, memory, system disk, and network throughput when the PC agent reports them
- PC sections for firewall, Microsoft Defender, alerts, TCP connections, and listening TCP ports
- Pro placeholder for future multi-client monitoring

## Separate Android Phone Monitor

The Android-phone monitoring app should be a separate app/project so users are not confused:

- PC Sentinel Mobile: checks Windows PCs from Android.
- Sentinel Phone: checks Android phone battery, GPS, storage, network, and Android security settings.

## Security Notes

Both apps are local visibility tools. They do not replace antivirus, do not block traffic, and do not upload monitoring data.

PC Sentinel Mobile requests internet access so it can contact the PC agent over the local network. The separate Android-phone monitor can request phone-specific permissions only when that app is built.
