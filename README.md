# PC Sentinel NET

PC Sentinel is a Windows desktop monitoring app for local PC health, network traffic, TCP connections, and basic security posture.

## Run

Double-click `Start-PCSentinel.cmd`

 

Use the `Refresh every` selector in the app header to scan every 5 seconds, 30 seconds, 1 minute, or never. The default is 1 minute.
The app starts in dark mode by default. Use the `Light Mode` button to switch themes.

## What it monitors

- CPU, memory, system disk usage, and aggregate network throughput
- Top processes by CPU time
- Hover over a top process for the internet search hint, then double-click or right-click to search it online
- Active network interfaces with upload/download rates
- Listening TCP ports in the Network & Firewall tab
- Hover over a listening TCP port for the internet search hint, then double-click or right-click to search it online
- Basic firewall profile information in the Security tab
- TCP connections and listening ports
- Hover over a TCP connection for the internet search hint, then double-click or right-click to search it online
- Windows Firewall profile status
- Microsoft Defender real-time protection and signature timestamp when available
- Button to open Windows Security settings from the Security tab
- Disk and volume inventory with links to Windows Disk Management and Storage Settings
- Basic hardware, OS, BIOS, hotfix, and installed software information where Windows allows access
- Alerts for high resource usage, disabled firewall profiles, sensitive listening ports, and outbound connection attempts

## Notes

This is a local visibility tool. It does not block traffic, replace antivirus, capture packet payloads, or send data outside your machine.
