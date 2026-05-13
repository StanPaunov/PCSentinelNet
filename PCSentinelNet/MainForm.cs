using System.Data;
using System.Diagnostics;
using System.Drawing;
using System.Net;
using System.Text;
using System.Text.Json;
using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace PCSentinelNet;

public sealed class MainForm : Form
{
    private static readonly SemaphoreSlim ProbeConcurrency = new(3);

    private readonly System.Windows.Forms.Timer refreshTimer = new();
    private readonly Label lastScan = new();
    private readonly ComboBox refreshInterval = new();
    private readonly Button themeButton = new();
    private readonly Button refreshButton = new();

    private readonly Label cpuValue = new();
    private readonly Label cpuSub = new();
    private readonly Label memoryValue = new();
    private readonly Label memorySub = new();
    private readonly Label diskValue = new();
    private readonly Label diskSub = new();
    private readonly Label networkValue = new();
    private readonly Label networkSub = new();

    private readonly ListBox alertsBox = new();
    private readonly DataGridView processGrid = NewGrid();
    private readonly DataGridView adapterGrid = NewGrid();
    private readonly DataGridView listeningPortGrid = NewGrid();
    private readonly DataGridView connectionGrid = NewGrid();
    private readonly Label listeningRiskSummary = new();
    private readonly DataGridView securityGrid = NewGrid();
    private readonly DataGridView firewallGrid = NewGrid();
    private readonly DataGridView eventGrid = NewGrid();
    private readonly DataGridView driveGrid = NewGrid();
    private readonly DataGridView physicalDiskGrid = NewGrid();
    private readonly TextBox securityNotes = NewTextBox();
    private readonly TextBox systemInfo = NewTextBox();

    private readonly ToolTip processTip = NewTip();
    private readonly ToolTip listeningTip = NewTip();
    private readonly ToolTip connectionTip = NewTip();

    private readonly TabControl tabs = new();
    private bool darkMode = true;
    private bool isRefreshing;

    private Color window;
    private Color header;
    private Color surface;
    private Color surfaceAlt;
    private Color text;
    private Color muted;
    private Color accent;
    private Color accentText;
    private Color accentSoft;
    private Color warning;
    private Color border;
    private Color buttonAlt;

    public MainForm()
    {
        Text = "PC Sentinel .NET - PC, Network, and Security Monitor";
        Size = new Size(1180, 760);
        MinimumSize = new Size(1180, 660);
        StartPosition = FormStartPosition.CenterScreen;
        Font = new Font("Segoe UI", 9F);
        DoubleBuffered = true;

        BuildHeader();
        BuildCards();
        BuildTabs();
        WireEvents();

        refreshTimer.Interval = 60000;
        refreshTimer.Tick += async (_, _) => await UpdateDashboardAsync();
        SetTheme(true);
    }

    private void BuildHeader()
    {
        var top = new Panel { Dock = DockStyle.Top, Height = 82 };
        var title = NewLabel("PC Sentinel .NET", 22, 14, 320, 28, 16F, FontStyle.Bold);
        var subtitle = NewLabel("Local monitoring for system load, traffic, connections, and security posture", 24, 43, 600, 22);

        var actions = new Panel { Dock = DockStyle.Right, Width = 530 };
        lastScan.SetBounds(0, 16, 220, 24);
        lastScan.Text = "Last scan: pending";
        lastScan.Font = new Font("Segoe UI", 9F, FontStyle.Bold);

        var refreshLabel = NewLabel("Refresh every:", 0, 47, 96, 22);
        refreshInterval.SetBounds(100, 44, 120, 24);
        refreshInterval.DropDownStyle = ComboBoxStyle.DropDownList;
        refreshInterval.Items.AddRange(new object[] { "5 seconds", "30 seconds", "1 minute", "Never" });
        refreshInterval.SelectedItem = "1 minute";

        themeButton.SetBounds(304, 24, 112, 34);
        themeButton.Text = "Light Mode";
        refreshButton.SetBounds(426, 24, 92, 34);
        refreshButton.Text = "Refresh";

        actions.Controls.AddRange(new Control[] { lastScan, refreshLabel, refreshInterval, themeButton, refreshButton });
        top.Controls.AddRange(new Control[] { actions, title, subtitle });
        Controls.Add(top);
    }

    private void BuildCards()
    {
        var panel = new Panel { Dock = DockStyle.Top, Height = 142, Padding = new Padding(18, 16, 18, 16) };
        AddCard(panel, "CPU", cpuValue, cpuSub, 20);
        AddCard(panel, "Memory", memoryValue, memorySub, 278);
        AddCard(panel, "System Disk", diskValue, diskSub, 536);
        AddCard(panel, "Network Throughput", networkValue, networkSub, 794);
        Controls.Add(panel);
    }

    private static void AddCard(Control parent, string title, Label value, Label sub, int x)
    {
        var card = new Panel { Location = new Point(x, 16), Size = new Size(240, 110), BorderStyle = BorderStyle.FixedSingle };
        card.Controls.Add(NewLabel(title, 14, 12, 210, 22, 9F, FontStyle.Bold));
        value.SetBounds(14, 42, 210, 34);
        value.Font = new Font("Segoe UI", 18F, FontStyle.Bold);
        value.Text = "--";
        sub.SetBounds(14, 78, 210, 22);
        sub.Text = "Waiting for scan";
        card.Controls.AddRange(new Control[] { value, sub });
        parent.Controls.Add(card);
    }

    private void BuildTabs()
    {
        tabs.Dock = DockStyle.Fill;
        tabs.DrawMode = TabDrawMode.OwnerDrawFixed;
        tabs.ItemSize = new Size(138, 30);
        tabs.SizeMode = TabSizeMode.Fixed;
        tabs.DrawItem += DrawTab;

        var overview = new TabPage("Overview");
        alertsBox.SetBounds(18, 18, 1090, 170);
        alertsBox.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
        processGrid.SetBounds(18, 214, 1090, 310);
        processGrid.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
        overview.Controls.AddRange(new Control[] {
            NewLabel("Alerts", 18, 0, 200, 20, 9F, FontStyle.Bold),
            alertsBox,
            NewLabel("Top Processes", 18, 194, 200, 20, 9F, FontStyle.Bold),
            processGrid
        });

        var network = new TabPage("Network & Firewall");
        adapterGrid.SetBounds(18, 18, 1090, 120);
        listeningPortGrid.SetBounds(18, 168, 1090, 110);
        listeningRiskSummary.SetBounds(250, 148, 858, 20);
        listeningRiskSummary.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
        listeningRiskSummary.Text = "Danger: waiting for scan";
        connectionGrid.SetBounds(18, 312, 1090, 212);
        connectionGrid.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
        network.Controls.AddRange(new Control[] {
            NewLabel("Active Interfaces", 18, 0, 200, 20, 9F, FontStyle.Bold),
            adapterGrid,
            NewLabel("Listening TCP Ports", 18, 148, 220, 20, 9F, FontStyle.Bold),
            listeningRiskSummary,
            listeningPortGrid,
            NewLabel("TCP Connections", 18, 292, 200, 20, 9F, FontStyle.Bold),
            connectionGrid
        });

        var security = new TabPage("Security");
        var securityButton = NewButton("Open Security Settings", 918, 4, 178, 30);
        securityButton.Anchor = AnchorStyles.Top | AnchorStyles.Right;
        securityButton.Click += (_, _) => StartUri("windowsdefender:", "ms-settings:windowsdefender");
        securityGrid.SetBounds(18, 42, 1090, 140);
        firewallGrid.SetBounds(18, 212, 1090, 100);
        securityNotes.SetBounds(18, 344, 1090, 180);
        securityNotes.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
        security.Controls.AddRange(new Control[] {
            NewLabel("Security Checks", 18, 0, 200, 20, 9F, FontStyle.Bold),
            securityButton,
            securityGrid,
            NewLabel("Firewall Profiles", 18, 192, 200, 20, 9F, FontStyle.Bold),
            firewallGrid,
            NewLabel("Notes", 18, 324, 200, 20, 9F, FontStyle.Bold),
            securityNotes
        });

        var events = new TabPage("Event Viewer");
        eventGrid.SetBounds(18, 62, 1090, 462);
        eventGrid.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
        events.Controls.AddRange(new Control[] {
            NewLabel("Recent Windows Events", 18, 0, 220, 20, 9F, FontStyle.Bold),
            NewLabel("System and Application errors/warnings from the last 7 days", 18, 20, 500, 20),
            NewLabel("Short guide: Critical/Error events need review first; repeated Warnings can point to drivers, services, or app problems.", 18, 40, 900, 20),
            eventGrid
        });

        var disk = new TabPage("Disk & Info");
        driveGrid.SetBounds(18, 18, 1090, 170);
        physicalDiskGrid.SetBounds(18, 220, 1090, 120);
        var diskMgmt = NewButton("Disk Management", 18, 366, 150, 32);
        diskMgmt.Click += (_, _) => StartUri("diskmgmt.msc");
        var storage = NewButton("Storage Settings", 184, 366, 140, 32);
        storage.Click += (_, _) => StartUri("ms-settings:storagesense");
        systemInfo.SetBounds(350, 366, 758, 158);
        systemInfo.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
        disk.Controls.AddRange(new Control[] {
            NewLabel("Volumes", 18, 0, 200, 20, 9F, FontStyle.Bold),
            driveGrid,
            NewLabel("Physical Disks", 18, 200, 200, 20, 9F, FontStyle.Bold),
            physicalDiskGrid,
            NewLabel("Management", 18, 346, 200, 20, 9F, FontStyle.Bold),
            diskMgmt,
            storage,
            NewLabel("Hardware & Software Info", 350, 346, 240, 20, 9F, FontStyle.Bold),
            systemInfo
        });

        tabs.TabPages.AddRange(new[] { overview, network, security, events, disk });
        Controls.Add(tabs);
        tabs.BringToFront();
    }

    private void WireEvents()
    {
        Shown += async (_, _) =>
        {
            SetRefreshInterval();
            await UpdateDashboardAsync();
        };
        refreshButton.Click += async (_, _) => await UpdateDashboardAsync();
        themeButton.Click += (_, _) => SetTheme(!darkMode);
        refreshInterval.SelectedIndexChanged += (_, _) => SetRefreshInterval();

        AddSearchMenu(processGrid, "Search Internet for Process", row =>
        {
            var name = Cell(row, "Process");
            SearchOnline($"{name} Windows process security");
        }, row => $"Double-click or right-click to search internet for {Cell(row, "Process")}", processTip);

        AddSearchMenu(listeningPortGrid, "Search Internet for Listening Port", row =>
        {
            SearchOnline(GetListeningSearchText(row));
        }, row => GetListeningHoverText(row), listeningTip);

        AddSearchMenu(connectionGrid, "Search Internet for Connection", row =>
        {
            SearchOnline(GetConnectionSearchText(row));
        }, row => "Double-click or right-click to search internet for this TCP connection", connectionTip);
    }

    private async Task UpdateDashboardAsync()
    {
        if (isRefreshing) return;
        isRefreshing = true;
        refreshTimer.Stop();
        Cursor = Cursors.WaitCursor;
        refreshButton.Enabled = false;
        try
        {
            cpuSub.Text = "Scanning...";
            memorySub.Text = "Scanning...";
            diskSub.Text = "Scanning...";
            networkSub.Text = "Scanning...";

            var data = await CollectDashboardDataAsync();
            var snapshot = data.Snapshot;

            cpuValue.Text = snapshot.CpuPercent + "%";
            cpuSub.Text = "Average processor load";
            memoryValue.Text = snapshot.MemoryPercent + "%";
            memorySub.Text = snapshot.MemoryDetail;
            diskValue.Text = snapshot.DiskPercent + "%";
            diskSub.Text = snapshot.DiskDetail;
            networkValue.Text = snapshot.NetworkTotal;
            networkSub.Text = snapshot.NetworkDetail;

            alertsBox.Items.Clear();
            foreach (var alert in snapshot.Alerts) alertsBox.Items.Add(alert);

            Bind(processGrid, data.Processes);
            Bind(adapterGrid, data.NetworkAdapters);
            Bind(listeningPortGrid, data.ListeningPorts);
            listeningRiskSummary.Text = BuildListeningRiskSummary(data.ListeningPorts);
            Bind(connectionGrid, data.Connections);
            Bind(securityGrid, data.Security);
            Bind(firewallGrid, data.Firewall);
            Bind(eventGrid, data.Events);
            Bind(driveGrid, data.Drives);
            Bind(physicalDiskGrid, data.PhysicalDisks);
            systemInfo.Text = data.SystemInfo;

            ApplyListeningTooltips();
            ApplyConnectionTooltips();
            ApplyProcessTooltips();

            securityNotes.Text = string.Join(Environment.NewLine, new[]
            {
                $"Latest scan: {DateTime.Now:yyyy-MM-dd HH:mm:ss}",
                "",
                $"Established TCP connections: {snapshot.EstablishedConnections}",
                $"Listening TCP ports: {snapshot.ListeningPorts}",
                "",
                "This app performs local visibility checks only. It does not block traffic, replace antivirus, or inspect encrypted payload contents.",
                "Run as Administrator for the most complete process and network ownership details."
            });
            lastScan.Text = $"Last scan: {DateTime.Now:HH:mm:ss}";
        }
        catch (Exception ex)
        {
            MessageBox.Show($"Scan failed: {ex.Message}", "PC Sentinel .NET", MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
        finally
        {
            refreshButton.Enabled = true;
            Cursor = Cursors.Default;
            isRefreshing = false;
            if ((string?)refreshInterval.SelectedItem != "Never")
                refreshTimer.Start();
        }
    }

    private static async Task<DashboardData> CollectDashboardDataAsync()
    {
        var snapshotTask = SafeTask(GetDashboardSnapshot, new DashboardSnapshot { Alerts = new[] { "Snapshot probe timed out or failed." } }, "Snapshot");
        var processTask = SafeTableTask(ProcessScript, "Processes");
        var networkTask = SafeTableTask(NetworkScript, "Network adapters");
        var listeningTask = SafeTask(() => BuildTcpTable(listeningOnly: true), ErrorTable("Listening TCP ports", "Native TCP probe failed."), "Listening TCP ports");
        var connectionTask = SafeTask(() => BuildTcpTable(listeningOnly: false), ErrorTable("TCP connections", "Native TCP probe failed."), "TCP connections");
        var securityTask = SafeTableTask(SecurityScript, "Security checks");
        var firewallTask = SafeTableTask(FirewallScript, "Firewall profiles");
        var eventTask = SafeTableTask(EventViewerScript, "Event Viewer");
        var driveTask = SafeTableTask(DriveScript, "Drives");
        var physicalDiskTask = SafeTableTask(PhysicalDiskScript, "Physical disks");
        var systemInfoTask = SafeTask(() => PsText(SystemInfoScript), "System information probe timed out or failed.", "System info");

        await Task.WhenAll(snapshotTask, processTask, networkTask, listeningTask, connectionTask, securityTask, firewallTask, eventTask, driveTask, physicalDiskTask, systemInfoTask);

        return new DashboardData
        {
            Snapshot = snapshotTask.Result,
            Processes = processTask.Result,
            NetworkAdapters = networkTask.Result,
            ListeningPorts = listeningTask.Result,
            Connections = connectionTask.Result,
            Security = securityTask.Result,
            Firewall = firewallTask.Result,
            Events = eventTask.Result,
            Drives = driveTask.Result,
            PhysicalDisks = physicalDiskTask.Result,
            SystemInfo = systemInfoTask.Result
        };
    }

    private static DashboardSnapshot GetDashboardSnapshot()
    {
        var text = PsText(SnapshotScript);
        return JsonSerializer.Deserialize<DashboardSnapshot>(text, JsonOptions()) ?? new DashboardSnapshot();
    }

    private static Task<T> SafeTask<T>(Func<T> action, T fallback, string label)
    {
        return Task.Run(async () =>
        {
            await ProbeConcurrency.WaitAsync();
            try
            {
                return action();
            }
            catch
            {
                return fallback;
            }
            finally
            {
                ProbeConcurrency.Release();
            }
        });
    }

    private static Task<DataTable> SafeTableTask(string script, string label)
    {
        return SafeTask(() => PsTable(script), ErrorTable(label, "Probe timed out or failed."), label);
    }

    private static DataTable ErrorTable(string area, string message)
    {
        var table = new DataTable();
        table.Columns.Add("Area");
        table.Columns.Add("Status");
        table.Rows.Add(area, message);
        return table;
    }

    private static DataTable BuildTcpTable(bool listeningOnly)
    {
        var table = new DataTable();
        table.Columns.Add("Process");
        table.Columns.Add("LocalAddress");
        table.Columns.Add("LocalPort");
        if (!listeningOnly)
        {
            table.Columns.Add("RemoteAddress");
            table.Columns.Add("RemotePort");
            table.Columns.Add("State");
        }
        else
        {
            table.Columns.Add("Risk");
            table.Columns.Add("Reason");
        }
        table.Columns.Add("OwningProcess");

        foreach (var row in GetNativeTcpRows())
        {
            if (listeningOnly && row.State != "LISTENING") continue;
            if (listeningOnly)
            {
                var risk = AssessListeningRisk(row);
                table.Rows.Add(row.Process, row.LocalAddress, row.LocalPort, risk.Level, risk.Reason, row.OwningProcess);
            }
            else
                table.Rows.Add(row.Process, row.LocalAddress, row.LocalPort, row.RemoteAddress, row.RemotePort, row.State, row.OwningProcess);
        }

        return table;
    }

    private static (string Level, string Reason) AssessListeningRisk(NativeTcpRow row)
    {
        var port = int.TryParse(row.LocalPort, out var parsedPort) ? parsedPort : 0;
        var process = row.Process.ToLowerInvariant();
        var allInterfaces = IsAllInterfaces(row.LocalAddress);
        var loopback = IsLoopback(row.LocalAddress);

        if (loopback)
            return ("Low", "Loopback-only listener. Usually reachable only from this PC.");

        if (port is 23 or 3389 or 5900)
            return (allInterfaces ? "High" : "Medium", "Remote access service. Confirm it is expected and firewall-restricted.");

        if (port == 445)
            return (allInterfaces ? "High" : "Medium", "SMB file sharing listener. Risk is higher on public or untrusted networks.");

        if (port == 135)
            return (allInterfaces ? "Medium" : "Low", "Windows RPC endpoint mapper. Common on Windows, but should not be exposed to untrusted networks.");

        if (port == 139)
            return (allInterfaces ? "Medium" : "Medium", "NetBIOS file sharing listener. Review sharing needs and network profile.");

        if (port == 27036 || process.Contains("steam"))
            return (allInterfaces ? "Medium" : "Low", "Steam local network listener. Review if game streaming or LAN discovery is not used.");

        if (port is 2869 or 5357)
            return (allInterfaces ? "Medium" : "Low", "Windows device discovery/web services listener. Expected on some private networks.");

        if (port >= 49152 && allInterfaces)
            return ("Low", "Dynamic Windows service/RPC listener. Usually normal, but review unknown processes.");

        if (allInterfaces)
            return ("Medium", "Listens on all network interfaces. Verify the process and whether the port should be reachable.");

        return ("Low", "Bound to a specific local address. Verify if this service is unexpected.");
    }

    private static bool IsAllInterfaces(string address) => address is "0.0.0.0" or "::" or "[::]";

    private static bool IsLoopback(string address) =>
        address is "127.0.0.1" or "::1" or "localhost" || address.StartsWith("127.", StringComparison.Ordinal);

    private static string BuildListeningRiskSummary(DataTable table)
    {
        if (!table.Columns.Contains("Risk") || table.Rows.Count == 0)
            return "Danger: Unknown - no listening port risk data available";

        var high = CountRisk(table, "High");
        var medium = CountRisk(table, "Medium");
        var low = CountRisk(table, "Low");
        var level = high > 0 ? "High" : medium > 0 ? "Medium" : "Low";
        var reviewPorts = table.Rows.Cast<DataRow>()
            .Where(row => IsReviewRisk(Convert.ToString(row["Risk"])))
            .Select(row => Convert.ToString(row["LocalPort"]) ?? "")
            .Where(port => !string.IsNullOrWhiteSpace(port))
            .Distinct()
            .Take(6)
            .ToArray();

        var review = reviewPorts.Length > 0 ? $" Review: ports {string.Join(", ", reviewPorts)}." : "";
        return $"Danger: {level} - {high} high, {medium} medium, {low} low listening ports.{review}";
    }

    private static int CountRisk(DataTable table, string risk)
    {
        return table.Rows.Cast<DataRow>().Count(row => string.Equals(Convert.ToString(row["Risk"]), risk, StringComparison.OrdinalIgnoreCase));
    }

    private static bool IsReviewRisk(string? risk) => string.Equals(risk, "High", StringComparison.OrdinalIgnoreCase)
        || string.Equals(risk, "Medium", StringComparison.OrdinalIgnoreCase);

    private static int CountTcpRows(string state)
    {
        return GetNativeTcpRows().Count(row => string.Equals(row.State, state, StringComparison.OrdinalIgnoreCase));
    }

    private static IEnumerable<NativeTcpRow> GetNativeTcpRows()
    {
        var bufferLength = 0;
        _ = GetExtendedTcpTable(IntPtr.Zero, ref bufferLength, true, AfInet, TcpTableClass.TCP_TABLE_OWNER_PID_ALL, 0);
        var buffer = Marshal.AllocHGlobal(bufferLength);
        try
        {
            var result = GetExtendedTcpTable(buffer, ref bufferLength, true, AfInet, TcpTableClass.TCP_TABLE_OWNER_PID_ALL, 0);
            if (result != 0) yield break;

            var rowCount = Marshal.ReadInt32(buffer);
            var rowPtr = IntPtr.Add(buffer, sizeof(int));
            var rowSize = Marshal.SizeOf<MibTcpRowOwnerPid>();
            for (var i = 0; i < rowCount; i++)
            {
                var native = Marshal.PtrToStructure<MibTcpRowOwnerPid>(IntPtr.Add(rowPtr, i * rowSize));
                var pid = unchecked((int)native.OwningPid);
                yield return new NativeTcpRow(
                    Process: ProcessName(pid),
                    LocalAddress: new IPAddress(native.LocalAddr).ToString(),
                    LocalPort: DecodePort(native.LocalPort).ToString(),
                    RemoteAddress: new IPAddress(native.RemoteAddr).ToString(),
                    RemotePort: DecodePort(native.RemotePort).ToString(),
                    State: TcpStateName(native.State),
                    OwningProcess: pid.ToString());
            }
        }
        finally
        {
            Marshal.FreeHGlobal(buffer);
        }
    }

    private static string ProcessName(int pid)
    {
        try { return Process.GetProcessById(pid).ProcessName; }
        catch { return "PID " + pid; }
    }

    private static ushort DecodePort(uint port)
    {
        var bytes = BitConverter.GetBytes(port);
        return (ushort)((bytes[0] << 8) + bytes[1]);
    }

    private static string TcpStateName(uint state) => state switch
    {
        1 => "CLOSED",
        2 => "LISTENING",
        3 => "SYN_SENT",
        4 => "SYN_RECEIVED",
        5 => "ESTABLISHED",
        6 => "FIN_WAIT_1",
        7 => "FIN_WAIT_2",
        8 => "CLOSE_WAIT",
        9 => "CLOSING",
        10 => "LAST_ACK",
        11 => "TIME_WAIT",
        12 => "DELETE_TCB",
        _ => state.ToString()
    };

    private const int AfInet = 2;

    private enum TcpTableClass
    {
        TCP_TABLE_OWNER_PID_ALL = 5
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MibTcpRowOwnerPid
    {
        public uint State;
        public uint LocalAddr;
        public uint LocalPort;
        public uint RemoteAddr;
        public uint RemotePort;
        public uint OwningPid;
    }

    private sealed record NativeTcpRow(string Process, string LocalAddress, string LocalPort, string RemoteAddress, string RemotePort, string State, string OwningProcess);

    [DllImport("iphlpapi.dll", SetLastError = true)]
    private static extern uint GetExtendedTcpTable(IntPtr tcpTable, ref int tcpTableLength, bool sort, int ipVersion, TcpTableClass tableClass, uint reserved);

    private void SetRefreshInterval()
    {
        var wasEnabled = refreshTimer.Enabled;
        switch ((string?)refreshInterval.SelectedItem)
        {
            case "Never":
                refreshTimer.Stop();
                return;
            case "30 seconds":
                refreshTimer.Interval = 30000;
                break;
            case "1 minute":
                refreshTimer.Interval = 60000;
                break;
            default:
                refreshTimer.Interval = 5000;
                break;
        }
        if (wasEnabled && !isRefreshing) refreshTimer.Start();
    }

    private void SetTheme(bool dark)
    {
        darkMode = dark;
        if (dark)
        {
            window = Color.FromArgb(16, 20, 18);
            header = Color.FromArgb(13, 17, 16);
            surface = Color.FromArgb(23, 32, 28);
            surfaceAlt = Color.FromArgb(32, 43, 38);
            text = Color.FromArgb(243, 246, 241);
            muted = Color.FromArgb(185, 197, 189);
            accent = Color.FromArgb(110, 231, 166);
            accentText = Color.FromArgb(7, 32, 19);
            accentSoft = Color.FromArgb(32, 51, 41);
            warning = Color.FromArgb(246, 199, 106);
            border = Color.FromArgb(49, 65, 57);
            buttonAlt = Color.FromArgb(32, 43, 38);
            themeButton.Text = "Light Mode";
        }
        else
        {
            window = Color.FromArgb(243, 246, 241);
            header = Color.FromArgb(23, 32, 28);
            surface = Color.White;
            surfaceAlt = Color.FromArgb(235, 241, 237);
            text = Color.FromArgb(23, 32, 28);
            muted = Color.FromArgb(56, 69, 63);
            accent = Color.FromArgb(22, 118, 75);
            accentText = Color.White;
            accentSoft = Color.FromArgb(220, 239, 229);
            warning = Color.FromArgb(178, 114, 19);
            border = Color.FromArgb(196, 211, 202);
            buttonAlt = Color.FromArgb(56, 69, 63);
            themeButton.Text = "Dark Mode";
        }

        ApplyThemeRecursive(this);
        foreach (var grid in new[] { processGrid, adapterGrid, listeningPortGrid, connectionGrid, securityGrid, firewallGrid, eventGrid, driveGrid, physicalDiskGrid })
            ApplyGridTheme(grid);
        tabs.Invalidate();
    }

    private void ApplyThemeRecursive(Control control)
    {
        if (control is Form || control is TabPage)
            control.BackColor = window;
        else if (control is Panel { Dock: DockStyle.Top, Height: 82 })
            control.BackColor = header;
        else if (control.Parent is Panel { Dock: DockStyle.Top, Height: 82 })
            control.BackColor = header;
        else if (control is Panel { BorderStyle: BorderStyle.FixedSingle })
            control.BackColor = surface;
        else if (control.Parent is Panel { BorderStyle: BorderStyle.FixedSingle })
            control.BackColor = surface;
        else if (control is Panel panel && panel.Dock == DockStyle.Top)
            panel.BackColor = window;
        else if (control is TextBox or ListBox or ComboBox)
            control.BackColor = surface;
        else if (control is Button button)
        {
            var primary = button == refreshButton || button.Text.Contains("Security") || button.Text.Contains("Disk Management");
            button.BackColor = primary ? accent : buttonAlt;
            button.ForeColor = primary ? accentText : text;
            button.FlatStyle = FlatStyle.Flat;
            button.FlatAppearance.BorderSize = primary ? 0 : 1;
            button.FlatAppearance.BorderColor = border;
            button.Cursor = Cursors.Hand;
        }

        if (control is Label label)
        {
            label.ForeColor = label.Font.Bold ? text : muted;
            if (label.Text.StartsWith("PC Sentinel")) label.ForeColor = darkMode ? Color.White : Color.FromArgb(243, 246, 241);
            if (label.Text.StartsWith("Danger:")) label.ForeColor = warning;
            if (label == lastScan) label.ForeColor = darkMode ? muted : Color.FromArgb(226, 238, 231);
            if (label == cpuValue || label == memoryValue || label == diskValue || label == networkValue) label.ForeColor = accent;
        }
        else if (control is TextBox or ListBox or ComboBox or Form)
        {
            control.ForeColor = text;
        }

        if (control is ComboBox combo)
        {
            combo.FlatStyle = FlatStyle.Flat;
        }
        else if (control is ListBox list)
        {
            list.BorderStyle = BorderStyle.FixedSingle;
        }
        else if (control is TextBox box)
        {
            box.BorderStyle = BorderStyle.FixedSingle;
        }

        foreach (Control child in control.Controls)
            ApplyThemeRecursive(child);
    }

    private void ApplyGridTheme(DataGridView grid)
    {
        grid.BackgroundColor = surface;
        grid.BorderStyle = BorderStyle.FixedSingle;
        grid.CellBorderStyle = DataGridViewCellBorderStyle.SingleHorizontal;
        grid.GridColor = border;
        grid.EnableHeadersVisualStyles = false;
        grid.RowTemplate.Height = 28;
        grid.DefaultCellStyle.BackColor = surface;
        grid.DefaultCellStyle.ForeColor = text;
        grid.DefaultCellStyle.SelectionBackColor = accentSoft;
        grid.DefaultCellStyle.SelectionForeColor = text;
        grid.DefaultCellStyle.Padding = new Padding(4, 2, 4, 2);
        grid.AlternatingRowsDefaultCellStyle.BackColor = surfaceAlt;
        grid.AlternatingRowsDefaultCellStyle.ForeColor = text;
        grid.ColumnHeadersDefaultCellStyle.BackColor = surfaceAlt;
        grid.ColumnHeadersDefaultCellStyle.ForeColor = text;
        grid.ColumnHeadersDefaultCellStyle.Font = new Font("Segoe UI", 9F, FontStyle.Bold);
        grid.ColumnHeadersDefaultCellStyle.Padding = new Padding(4, 4, 4, 4);
        grid.ColumnHeadersHeight = 34;
    }

    private void DrawTab(object? sender, DrawItemEventArgs e)
    {
        var page = tabs.TabPages[e.Index];
        var selected = (e.State & DrawItemState.Selected) == DrawItemState.Selected;
        using var brush = new SolidBrush(selected ? surfaceAlt : window);
        e.Graphics.FillRectangle(brush, e.Bounds);
        var color = selected ? accent : muted;
        TextRenderer.DrawText(e.Graphics, page.Text, tabs.Font, e.Bounds, color, TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);
        if (selected)
        {
            using var pen = new Pen(accent, 3);
            e.Graphics.DrawLine(pen, e.Bounds.Left + 10, e.Bounds.Bottom - 2, e.Bounds.Right - 10, e.Bounds.Bottom - 2);
        }
    }

    private static DataGridView NewGrid() => new()
    {
        ReadOnly = true,
        AllowUserToAddRows = false,
        AllowUserToDeleteRows = false,
        RowHeadersVisible = false,
        SelectionMode = DataGridViewSelectionMode.FullRowSelect,
        ShowCellToolTips = true,
        AutoSizeColumnsMode = DataGridViewAutoSizeColumnsMode.Fill,
        AllowUserToResizeRows = false
    };

    private static TextBox NewTextBox() => new()
    {
        Multiline = true,
        ReadOnly = true,
        ScrollBars = ScrollBars.Vertical,
        Font = new Font("Consolas", 9.5F)
    };

    private static ToolTip NewTip() => new() { AutoPopDelay = 4500, InitialDelay = 400, ReshowDelay = 200 };

    private static Label NewLabel(string value, int x, int y, int width, int height, float size = 9F, FontStyle style = FontStyle.Regular)
    {
        return new Label { Text = value, Location = new Point(x, y), Size = new Size(width, height), Font = new Font("Segoe UI", size, style) };
    }

    private static Button NewButton(string value, int x, int y, int width, int height) => new()
    {
        Text = value,
        Location = new Point(x, y),
        Size = new Size(width, height),
        FlatStyle = FlatStyle.Flat
    };

    private static void Bind(DataGridView grid, DataTable table)
    {
        grid.DataSource = table;
        grid.AutoResizeColumns(DataGridViewAutoSizeColumnsMode.DisplayedCells);
        grid.AutoSizeColumnsMode = DataGridViewAutoSizeColumnsMode.Fill;
    }

    private static string Cell(DataGridViewRow row, string name)
    {
        if (row.DataGridView is null || !row.DataGridView.Columns.Contains(name))
            return "";
        return Convert.ToString(row.Cells[name].Value) ?? "";
    }

    private void ApplyProcessTooltips()
    {
        foreach (DataGridViewRow row in processGrid.Rows)
        {
            if (HasColumn(row, "Process"))
                row.Cells["Process"].ToolTipText = $"Search internet for {Cell(row, "Process")}";
        }
    }

    private void ApplyConnectionTooltips()
    {
        foreach (DataGridViewRow row in connectionGrid.Rows)
        {
            if (HasColumn(row, "Process")) row.Cells["Process"].ToolTipText = "Search internet for this connection";
            if (HasColumn(row, "RemoteAddress")) row.Cells["RemoteAddress"].ToolTipText = $"Search internet for {Cell(row, "RemoteAddress")}";
            if (HasColumn(row, "RemotePort")) row.Cells["RemotePort"].ToolTipText = $"Search internet for port {Cell(row, "RemotePort")}";
        }
    }

    private void ApplyListeningTooltips()
    {
        foreach (DataGridViewRow row in listeningPortGrid.Rows)
        {
            var tip = FirstLine(GetListeningHoverText(row));
            if (HasColumn(row, "Process")) row.Cells["Process"].ToolTipText = tip;
            if (HasColumn(row, "LocalPort")) row.Cells["LocalPort"].ToolTipText = tip;
            if (HasColumn(row, "Risk")) row.Cells["Risk"].ToolTipText = tip;
            if (HasColumn(row, "Reason")) row.Cells["Reason"].ToolTipText = tip;
        }
    }

    private static string FirstLine(string value) => value.Split(new[] { "\r\n", "\n" }, StringSplitOptions.None)[0];

    private static bool HasColumn(DataGridViewRow row, string name)
    {
        return row.DataGridView is not null && row.DataGridView.Columns.Contains(name);
    }

    private void AddSearchMenu(DataGridView grid, string menuText, Action<DataGridViewRow> searchAction, Func<DataGridViewRow, string> hoverText, ToolTip tip)
    {
        var menu = new ContextMenuStrip();
        var item = new ToolStripMenuItem(menuText);
        menu.Items.Add(item);
        grid.ContextMenuStrip = menu;

        grid.CellMouseEnter += (_, e) =>
        {
            if (e.RowIndex < 0) return;
            var row = grid.Rows[e.RowIndex];
            var point = grid.PointToClient(Cursor.Position);
            tip.Show(hoverText(row), grid, point.X + 12, point.Y + 18, 4500);
        };
        grid.CellMouseLeave += (_, _) => tip.Hide(grid);
        grid.CellMouseDown += (_, e) =>
        {
            if (e.Button != MouseButtons.Right || e.RowIndex < 0) return;
            grid.ClearSelection();
            grid.Rows[e.RowIndex].Selected = true;
            grid.CurrentCell = grid.Rows[e.RowIndex].Cells[0];
        };
        grid.CellDoubleClick += (_, e) =>
        {
            if (e.RowIndex >= 0) searchAction(grid.Rows[e.RowIndex]);
        };
        item.Click += (_, _) =>
        {
            if (grid.SelectedRows.Count > 0) searchAction(grid.SelectedRows[0]);
        };
    }

    private static string GetConnectionSearchText(DataGridViewRow row)
    {
        var parts = new[] { Cell(row, "Process"), Cell(row, "RemoteAddress"), PortPart(Cell(row, "RemotePort")), Cell(row, "State") }
            .Where(x => !string.IsNullOrWhiteSpace(x) && x != "0.0.0.0" && x != "::");
        return string.Join(" ", parts) + " TCP connection security";
    }

    private static string GetListeningSearchText(DataGridViewRow row)
    {
        var parts = new[] { Cell(row, "Process"), $"TCP listening port {Cell(row, "LocalPort")}", Cell(row, "LocalAddress") }
            .Where(x => !string.IsNullOrWhiteSpace(x) && x != "0.0.0.0" && x != "::");
        return string.Join(" ", parts) + " Windows security";
    }

    private static string GetListeningHoverText(DataGridViewRow row)
    {
        var process = Cell(row, "Process");
        var port = Cell(row, "LocalPort");
        var pid = Cell(row, "OwningProcess");
        var address = Cell(row, "LocalAddress");
        var risk = Cell(row, "Risk");
        var reason = Cell(row, "Reason");
        var line = $"Port {port} is listened on by {process}";
        if (!string.IsNullOrWhiteSpace(pid)) line += $" (PID {pid})";
        var details = new List<string> { line, $"Local address: {address}" };
        if (!string.IsNullOrWhiteSpace(risk))
            details.Add($"Risk: {risk} - {reason}");
        details.Add("Double-click or right-click to search online");
        return string.Join(Environment.NewLine, details);
    }

    private static string PortPart(string port) => string.IsNullOrWhiteSpace(port) || port == "0" ? "" : $"port {port}";

    private static void SearchOnline(string query)
    {
        if (string.IsNullOrWhiteSpace(query)) return;
        StartUri("https://www.bing.com/search?q=" + Uri.EscapeDataString(query));
    }

    private static void StartUri(string uri, string? fallback = null)
    {
        try
        {
            Process.Start(new ProcessStartInfo(uri) { UseShellExecute = true });
        }
        catch when (!string.IsNullOrWhiteSpace(fallback))
        {
            Process.Start(new ProcessStartInfo(fallback!) { UseShellExecute = true });
        }
        catch (Exception ex)
        {
            MessageBox.Show($"Could not open target: {ex.Message}", "PC Sentinel .NET", MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
    }

    private static string PsText(string script)
    {
        var psi = new ProcessStartInfo("powershell.exe")
        {
            Arguments = "-NoProfile -ExecutionPolicy Bypass -Command " + Convert.ToBase64String(Encoding.Unicode.GetBytes(script)),
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true,
            StandardOutputEncoding = Encoding.UTF8
        };
        psi.Arguments = "-NoProfile -ExecutionPolicy Bypass -EncodedCommand " + Convert.ToBase64String(Encoding.Unicode.GetBytes(script));
        using var process = Process.Start(psi) ?? throw new InvalidOperationException("Could not start PowerShell.");
        var outputTask = process.StandardOutput.ReadToEndAsync();
        var errorTask = process.StandardError.ReadToEndAsync();
        if (!process.WaitForExit(30000))
        {
            try { process.Kill(entireProcessTree: true); } catch { }
            throw new TimeoutException("PowerShell probe timed out.");
        }

        var output = outputTask.GetAwaiter().GetResult();
        var error = errorTask.GetAwaiter().GetResult();
        if (process.ExitCode != 0 && string.IsNullOrWhiteSpace(output))
            throw new InvalidOperationException(error.Trim());
        return output.Trim();
    }

    private static DataTable PsTable(string script)
    {
        var json = PsText(script);
        var table = new DataTable();
        if (string.IsNullOrWhiteSpace(json)) return table;

        using var doc = JsonDocument.Parse(json);
        var rows = doc.RootElement.ValueKind == JsonValueKind.Array ? doc.RootElement.EnumerateArray().ToArray() : new[] { doc.RootElement };
        foreach (var row in rows)
        {
            if (row.ValueKind != JsonValueKind.Object) continue;
            foreach (var property in row.EnumerateObject())
            {
                if (!table.Columns.Contains(property.Name))
                    table.Columns.Add(property.Name);
            }
        }
        foreach (var row in rows)
        {
            var data = table.NewRow();
            foreach (var property in row.EnumerateObject())
                data[property.Name] = property.Value.ValueKind == JsonValueKind.Null ? "" : property.Value.ToString();
            table.Rows.Add(data);
        }
        return table;
    }

    private static JsonSerializerOptions JsonOptions() => new() { PropertyNameCaseInsensitive = true };

    private sealed class DashboardSnapshot
    {
        public int CpuPercent { get; set; }
        public int MemoryPercent { get; set; }
        public string MemoryDetail { get; set; } = "";
        public int DiskPercent { get; set; }
        public string DiskDetail { get; set; } = "";
        public string NetworkTotal { get; set; } = "";
        public string NetworkDetail { get; set; } = "";
        public int EstablishedConnections { get; set; }
        public int ListeningPorts { get; set; }
        public string[] Alerts { get; set; } = Array.Empty<string>();
    }

    private sealed class DashboardData
    {
        public DashboardSnapshot Snapshot { get; init; } = new();
        public DataTable Processes { get; init; } = new();
        public DataTable NetworkAdapters { get; init; } = new();
        public DataTable ListeningPorts { get; init; } = new();
        public DataTable Connections { get; init; } = new();
        public DataTable Security { get; init; } = new();
        public DataTable Firewall { get; init; } = new();
        public DataTable Events { get; init; } = new();
        public DataTable Drives { get; init; } = new();
        public DataTable PhysicalDisks { get; init; } = new();
        public string SystemInfo { get; init; } = "";
    }

    private const string HelperScript = @"
function Format-Bytes([double]$Bytes) {
 if ($Bytes -ge 1TB) { return ('{0:N2} TB' -f ($Bytes / 1TB)) }
 if ($Bytes -ge 1GB) { return ('{0:N2} GB' -f ($Bytes / 1GB)) }
 if ($Bytes -ge 1MB) { return ('{0:N2} MB' -f ($Bytes / 1MB)) }
 if ($Bytes -ge 1KB) { return ('{0:N2} KB' -f ($Bytes / 1KB)) }
 return ('{0:N0} B' -f $Bytes)
}
";

    private const string SnapshotScript = HelperScript + @"
Add-Type -AssemblyName Microsoft.VisualBasic
$cpu = 0
try { $cpu = [math]::Round((Get-Counter '\Processor(_Total)\% Processor Time' -SampleInterval 1 -MaxSamples 1).CounterSamples[0].CookedValue,0) } catch {}
$ci = New-Object Microsoft.VisualBasic.Devices.ComputerInfo
$total = [double]$ci.TotalPhysicalMemory; $free = [double]$ci.AvailablePhysicalMemory; $used = $total - $free
$memPct = if ($total -gt 0) { [math]::Round(($used/$total)*100,0) } else { 0 }
$drive = [System.IO.DriveInfo]::GetDrives() | Where-Object { $_.Name -eq ($env:SystemDrive + '\') } | Select-Object -First 1
$diskPct = if ($drive.TotalSize -gt 0) { [math]::Round((($drive.TotalSize-$drive.AvailableFreeSpace)/$drive.TotalSize)*100,0) } else { 0 }
$rx=0; $tx=0
try {
 $samples = (Get-Counter '\Network Interface(*)\Bytes Received/sec','\Network Interface(*)\Bytes Sent/sec' -SampleInterval 1 -MaxSamples 1).CounterSamples
 $rx = ($samples | Where-Object Path -like '*Bytes Received/sec' | Measure-Object CookedValue -Sum).Sum
 $tx = ($samples | Where-Object Path -like '*Bytes Sent/sec' | Measure-Object CookedValue -Sum).Sum
} catch {}
$conns = netstat -ano | Select-Object -Skip 4
$listen = @($conns | Where-Object { $_ -match '\sLISTENING\s' }).Count
$est = @($conns | Where-Object { $_ -match '\sESTABLISHED\s' }).Count
$alerts = New-Object System.Collections.Generic.List[string]
if ($cpu -ge 90) { $alerts.Add('High CPU load: ' + $cpu + '%') }
if ($memPct -ge 90) { $alerts.Add('High memory use: ' + $memPct + '%') }
if ($diskPct -ge 90) { $alerts.Add('System drive is nearly full: ' + $diskPct + '% used') }
if ($alerts.Count -eq 0) { $alerts.Add('No active alerts from the latest scan.') }
[pscustomobject]@{
 CpuPercent=$cpu; MemoryPercent=$memPct; MemoryDetail=((Format-Bytes $used) + ' of ' + (Format-Bytes $total));
 DiskPercent=$diskPct; DiskDetail=($env:SystemDrive + ' free: ' + (Format-Bytes $drive.AvailableFreeSpace));
 NetworkTotal=((Format-Bytes ($rx+$tx)) + '/s'); NetworkDetail=('Down ' + (Format-Bytes $rx) + '/s  Up ' + (Format-Bytes $tx) + '/s');
 EstablishedConnections=$est; ListeningPorts=$listen; Alerts=$alerts.ToArray()
} | ConvertTo-Json -Depth 4
";

    private const string ProcessScript = HelperScript + @"Get-Process | Sort-Object CPU -Descending | Select-Object -First 20 @{n='Process';e={$_.ProcessName}},Id,@{n='CPUSeconds';e={('{0:N1}' -f $_.CPU)}},@{n='Memory';e={Format-Bytes $_.WorkingSet64}},Responding | ConvertTo-Json -Depth 3";
    private const string NetworkScript = HelperScript + @"& { try { Get-Counter '\Network Interface(*)\Bytes Received/sec','\Network Interface(*)\Bytes Sent/sec' -SampleInterval 1 -MaxSamples 1 | Select-Object -ExpandProperty CounterSamples | Group-Object InstanceName | ForEach-Object { $rx=($_.Group|Where-Object Path -like '*Bytes Received/sec'|Select-Object -First 1).CookedValue; $tx=($_.Group|Where-Object Path -like '*Bytes Sent/sec'|Select-Object -First 1).CookedValue; [pscustomobject]@{Adapter=$_.Name;Download=((Format-Bytes $rx)+'/s');Upload=((Format-Bytes $tx)+'/s');Total=((Format-Bytes ($rx+$tx))+'/s')} } } catch { @() } } | ConvertTo-Json -Depth 3";
    private const string ListeningPortsScript = @"$map=@{}; Get-Process | ForEach-Object { $map[[int]$_.Id]=$_.ProcessName }; netstat -ano | Select-Object -Skip 4 | ForEach-Object { $l=($_ -replace '\s+',' ').Trim(); $p=$l.Split(' '); if($p.Count -ge 5 -and $p[0] -eq 'TCP' -and $p[3] -eq 'LISTENING'){ $i=$p[1].LastIndexOf(':'); $addr=$p[1].Substring(0,$i).Trim('[',']'); $port=$p[1].Substring($i+1); $pid=[int]$p[-1]; [pscustomobject]@{Process=if($map[$pid]){$map[$pid]}else{'PID '+$pid};LocalAddress=$addr;LocalPort=$port;OwningProcess=$pid} } } | Select-Object -First 80 | ConvertTo-Json -Depth 3";
    private const string ConnectionScript = @"$map=@{}; Get-Process | ForEach-Object { $map[[int]$_.Id]=$_.ProcessName }; netstat -ano | Select-Object -Skip 4 | ForEach-Object { $l=($_ -replace '\s+',' ').Trim(); $p=$l.Split(' '); if($p.Count -ge 5 -and $p[0] -eq 'TCP'){ $li=$p[1].LastIndexOf(':'); $ri=$p[2].LastIndexOf(':'); $pid=[int]$p[-1]; [pscustomobject]@{Process=if($map[$pid]){$map[$pid]}else{'PID '+$pid};LocalAddress=$p[1].Substring(0,$li).Trim('[',']');LocalPort=$p[1].Substring($li+1);RemoteAddress=$p[2].Substring(0,$ri).Trim('[',']');RemotePort=$p[2].Substring($ri+1);State=$p[3];OwningProcess=$pid} } } | Select-Object -First 120 | ConvertTo-Json -Depth 3";
    private const string SecurityScript = @"$items=@(); try { Get-NetFirewallProfile -ErrorAction Stop | ForEach-Object { $items += [pscustomobject]@{Area='Firewall';Item=$_.Name;Status=if($_.Enabled){'On'}else{'Off'};Severity=if($_.Enabled){'OK'}else{'High'}} } } catch { $items += [pscustomobject]@{Area='Firewall';Item='Profiles';Status='Unavailable';Severity='Info'} }; try { $d=Get-MpComputerStatus -ErrorAction Stop; $items += [pscustomobject]@{Area='Defender';Item='Real-time protection';Status=if($d.RealTimeProtectionEnabled){'On'}else{'Off'};Severity=if($d.RealTimeProtectionEnabled){'OK'}else{'High'}}; $items += [pscustomobject]@{Area='Defender';Item='Antivirus signatures';Status=[string]$d.AntivirusSignatureLastUpdated;Severity='Info'} } catch { $items += [pscustomobject]@{Area='Defender';Item='Status';Status='Unavailable';Severity='Info'} }; $items | ConvertTo-Json -Depth 3";
    private const string FirewallScript = @"& { try { Get-NetFirewallProfile -ErrorAction Stop | Sort-Object Name | Select-Object @{n='Profile';e={$_.Name}},@{n='Enabled';e={if($_.Enabled){'On'}else{'Off'}}},DefaultInboundAction,DefaultOutboundAction,@{n='Notifications';e={if($_.NotifyOnListen){'On'}else{'Off'}}},@{n='LogBlocked';e={if($_.LogBlocked){'On'}else{'Off'}}},@{n='LogAllowed';e={if($_.LogAllowed){'On'}else{'Off'}}} } catch { [pscustomobject]@{Profile='Unavailable';Enabled='Unknown';DefaultInboundAction='Unknown';DefaultOutboundAction='Unknown';Notifications='Unknown';LogBlocked='Unknown';LogAllowed='Unknown'} } } | ConvertTo-Json -Depth 3";
    private const string EventViewerScript = @"& { try { Get-WinEvent -FilterHashtable @{LogName=@('System','Application'); Level=1,2,3; StartTime=(Get-Date).AddDays(-7)} -MaxEvents 80 -ErrorAction Stop | Select-Object @{n='Time';e={$_.TimeCreated.ToString('yyyy-MM-dd HH:mm:ss')}},@{n='Log';e={$_.LogName}},@{n='Level';e={$_.LevelDisplayName}},@{n='Provider';e={$_.ProviderName}},@{n='EventId';e={$_.Id}},@{n='Message';e={$m=($_.Message -replace '\s+',' ').Trim(); if($m.Length -gt 220){$m.Substring(0,220)+'...'}else{$m}}} } catch { [pscustomobject]@{Time=(Get-Date -Format 'yyyy-MM-dd HH:mm:ss');Log='Event Viewer';Level='Info';Provider='PC Sentinel NET';EventId='';Message='Event Viewer data unavailable. Run as Administrator for more complete event access.'} } } | ConvertTo-Json -Depth 3";
    private const string DriveScript = HelperScript + @"[System.IO.DriveInfo]::GetDrives() | Where-Object IsReady | ForEach-Object { $used=$_.TotalSize-$_.AvailableFreeSpace; [pscustomobject]@{Drive=$_.Name;Label=$_.VolumeLabel;FileSystem=$_.DriveFormat;Type=$_.DriveType;Used=if($_.TotalSize -gt 0){('{0}%' -f [math]::Round(($used/$_.TotalSize)*100,0))}else{'N/A'};Free=Format-Bytes $_.AvailableFreeSpace;Total=Format-Bytes $_.TotalSize} } | ConvertTo-Json -Depth 3";
    private const string PhysicalDiskScript = HelperScript + @"& { try { Get-PhysicalDisk -ErrorAction Stop | Select-Object FriendlyName,MediaType,BusType,HealthStatus,OperationalStatus,@{n='Size';e={Format-Bytes $_.Size}} } catch { try { Get-CimInstance Win32_DiskDrive -ErrorAction Stop | Select-Object @{n='FriendlyName';e={$_.Model}},MediaType,@{n='BusType';e={$_.InterfaceType}},@{n='HealthStatus';e={$_.Status}},@{n='OperationalStatus';e={$_.Status}},@{n='Size';e={Format-Bytes $_.Size}} } catch { [pscustomobject]@{FriendlyName='Unavailable';MediaType='Unknown';BusType='Unknown';HealthStatus='Unknown';OperationalStatus='Unknown';Size='Unknown'} } } } | ConvertTo-Json -Depth 3";
    private const string SystemInfoScript = @"$lines=New-Object System.Collections.Generic.List[string]; $lines.Add('System inventory'); $lines.Add('Generated: '+(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')); $lines.Add(''); try{$c=Get-CimInstance Win32_ComputerSystem; $lines.Add('Computer: '+$c.Name); $lines.Add('Manufacturer: '+$c.Manufacturer); $lines.Add('Model: '+$c.Model)}catch{$lines.Add('Computer: '+$env:COMPUTERNAME)}; try{$os=Get-CimInstance Win32_OperatingSystem; $lines.Add('OS: '+$os.Caption); $lines.Add('Version: '+$os.Version+' build '+$os.BuildNumber)}catch{$lines.Add('OS: '+[Environment]::OSVersion.VersionString)}; try{$cpu=Get-CimInstance Win32_Processor|Select-Object -First 1; $lines.Add('CPU: '+$cpu.Name)}catch{}; $lines.Add(''); $lines.Add('Installed software sample:'); Get-ItemProperty 'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*','HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*' -ErrorAction SilentlyContinue | Where-Object DisplayName | Sort-Object DisplayName | Select-Object -First 12 | ForEach-Object { $lines.Add('  '+$_.DisplayName+' '+$_.DisplayVersion+' - '+$_.Publisher) }; $lines -join [Environment]::NewLine";
}
