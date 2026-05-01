# PC Sentinel .NET

PC Sentinel .NET is a Windows Forms desktop app for monitoring local PC health, network activity, listening TCP ports, disk information, and basic Windows security status.

## Requirements

- Windows 10 or Windows 11
- .NET 8 

## Run From Source

From the repository root:

```powershell
dotnet run --project .\PCSentinelNet\PCSentinelNet.csproj
```

Or double-click:

```text
Start-PCSentinelNet.cmd
```

For the most complete process and network ownership details, run it from an elevated terminal.

## Features

- Dark mode by default, with a Light Mode button
- Refresh interval selector: 5 seconds, 30 seconds, 1 minute, or Never
- CPU, memory, system disk, and network throughput summary cards
- Top processes table with internet search by hover, double-click, or right-click
- Network & Firewall tab with active interfaces, listening TCP ports, and TCP connections
- Listening TCP ports show owning process, PID, risk level, reason, and a danger summary
- TCP connection search by process, remote address, remote port, and connection state
- Security tab with Defender/firewall checks and an Open Security Settings button
- Firewall profile details in the Security tab
- Disk & Info tab with volume, physical disk, hardware, OS, and installed software information
- Buttons for Disk Management and Storage Settings

## Security Notes

This is a local visibility tool. It does not block traffic, replace antivirus, capture packet payloads, or send data outside your machine.

Listening TCP risk levels are guidance, not proof of compromise. Review any unexpected high or medium risk listener, especially services bound to all network interfaces.
