# PC Sentinel NET

PC Sentinel NET is a Windows desktop monitoring app for local PC health, network activity, disk information, and basic Windows security status.

Website: https://stanpaunov.github.io/PCSentinelNet/

GitHub Releases: https://github.com/StanPaunov/PCSentinelNet/releases

## Desktop App

The Windows desktop app monitors only the current Windows PC. It does not include Android monitoring, does not expose a mobile API, and does not run a phone companion service.

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

## Security Notes

PC Sentinel NET is a local visibility tool. It does not replace antivirus, does not block traffic, does not sniff packet payloads, and does not upload monitoring data.

The app reads Windows information locally. Some details may require administrator rights or may be limited by Windows security policy.
