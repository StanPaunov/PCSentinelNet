# PC Sentinel .NET

This is a .NET 8 Windows Forms version of PC Sentinel.

## Build

Install the .NET 8 SDK or Visual Studio, then run:

```powershell
dotnet build .\PCSentinelNet.csproj
dotnet run --project .\PCSentinelNet.csproj
```

This environment only has the .NET runtime, not the SDK, so the project could not be built here.

## Notes

The app is a real WinForms desktop app. It uses Windows PowerShell probes for system, networking, firewall, Defender, disk, and inventory data so it does not require NuGet packages.
