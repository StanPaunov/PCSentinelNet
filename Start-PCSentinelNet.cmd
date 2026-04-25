@echo off
set SCRIPT_DIR=%~dp0
dotnet run --project "%SCRIPT_DIR%PCSentinelNet\PCSentinelNet.csproj"
