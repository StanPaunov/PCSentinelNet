# PC Sentinel Mobile

Android companion for PC Sentinel NET. This app is for checking Windows PC information from Android. It is separate from any future Android-phone monitoring app.

Website: https://stanpaunov.github.io/PCSentinelNet/

Repository: https://github.com/StanPaunov/PCSentinelNet

## PC Monitoring

- One Windows PC in the free edition
- PC agent URL stored on the device
- PC CPU, memory, system disk, and network throughput from a JSON status endpoint
- PC firewall and Microsoft Defender status when reported by the agent
- PC alerts, TCP connections, and listening TCP ports when reported by the agent
- Manual refresh and automatic refresh intervals: 5 seconds, 30 seconds, 1 minute, or Never

Expected PC agent endpoint:

```text
http://PC-IP-ADDRESS:8787/api/status
```

Expected JSON fields are intentionally simple so the Windows app can add the API later:

```json
{
  "pcName": "Office-PC",
  "status": "OK",
  "cpuPercent": 15,
  "memoryPercent": 49,
  "diskPercent": 77,
  "networkBytesPerSecond": 941,
  "firewall": "Domain, Private, Public enabled",
  "defender": "Real-time protection enabled",
  "alerts": ["No active alerts"],
  "tcpConnections": ["chrome.exe -> 93.184.216.34:443"],
  "listeningPorts": ["PCSentinelNet.exe listening on 8787"]
}
```

## Pro Version Path

- Free: monitor one PC
- Pro Personal: planned support for up to 3 PCs
- Pro Business: planned support for 10, 25, or more PCs
- Planned Pro features: saved clients, groups, alert history, report export, and a multi-client dashboard

## What This App Is Not

This app is not the Android-phone monitor. A separate Android app should handle Android phone checks such as battery, GPS, storage, network, and Android security settings.

## Android Limits

Android does not allow a normal app to inspect Windows TCP ports, firewall profiles, Microsoft Defender, or Windows process CPU data directly. PC monitoring requires a Windows PC companion service or API endpoint.

The app requests internet access so it can contact the PC agent over the local network.

## Open In Android Studio

1. Open Android Studio.
2. Choose **Open**.
3. Select the `Sentinel Mobile` folder.
4. Let Android Studio sync Gradle.
5. Run the `app` configuration on an emulator or Android device.

Default refresh interval is `1 minute`; the app also supports `5 seconds`, `30 seconds`, and `Never`.
