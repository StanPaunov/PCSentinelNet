package com.sentinel.mobile;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.text.InputType;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.net.NetworkInterface;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int REFRESH_NEVER = -1;
    private static final int REFRESH_DEFAULT_MS = 60_000;
    private static final String PREFS = "pc_sentinel_mobile";
    private static final String PREF_PC_AGENT_URL = "pc_agent_url";
    private static final String[] TABS = {"PC", "Network", "Security", "Clients", "Pro"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<TextView> themedLabels = new ArrayList<>();
    private final List<TextView> accentLabels = new ArrayList<>();
    private final List<TextView> highlightLabels = new ArrayList<>();
    private final List<View> cards = new ArrayList<>();
    private final List<Button> tabButtons = new ArrayList<>();
    private final List<Button> actionButtons = new ArrayList<>();

    private LinearLayout root;
    private LinearLayout content;
    private TextView gpsValue;
    private TextView memoryValue;
    private TextView diskValue;
    private TextView networkValue;
    private TextView lastScan;
    private TextView alertText;
    private Spinner refreshSpinner;
    private Button themeButton;

    private boolean darkMode = true;
    private String activeTab = "PC";
    private float swipeStartX;
    private float swipeStartY;
    private int refreshMs = REFRESH_DEFAULT_MS;
    private long lastRx = TrafficStats.getTotalRxBytes();
    private long lastTx = TrafficStats.getTotalTxBytes();
    private long lastNetworkAt = System.currentTimeMillis();
    private String pcAgentUrl = "";
    private boolean pcFetchInFlight = false;
    private PcSnapshot lastPcSnapshot = PcSnapshot.notConfigured();

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshNow();
            scheduleNextRefresh();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pcAgentUrl = getPreferences().getString(PREF_PC_AGENT_URL, "");
        activeTab = "PC";
        buildUi();
        refreshNow();
        scheduleNextRefresh();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refreshRunnable);
        super.onDestroy();
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        top.addView(titleBlock, new LinearLayout.LayoutParams(0, -2, 1f));
        addLabel(titleBlock, "PC Sentinel Mobile", 24, true);
        addLabel(titleBlock, "Android companion for monitoring your Windows PC", 13, false);

        themeButton = new Button(this);
        themeButton.setText("Light Mode");
        themeButton.setOnClickListener(v -> {
            darkMode = !darkMode;
            applyTheme();
        });
        top.addView(themeButton, new LinearLayout.LayoutParams(dp(128), dp(48)));
        themeButton.setAllCaps(false);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(0, dp(14), 0, dp(8));
        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        lastScan = addLabel(controls, "Last scan: --", 13, true);
        addSpacer(controls, 16);
        addLabel(controls, "Refresh every:", 13, false);
        refreshSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"5 seconds", "30 seconds", "1 minute", "Never"}) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(foreground());
                view.setTextSize(14);
                view.setSingleLine(true);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(Color.rgb(20, 28, 24));
                view.setTextSize(14);
                return view;
            }
        };
        refreshSpinner.setAdapter(adapter);
        refreshSpinner.setSelection(2);
        refreshSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int[] values = {5_000, 30_000, 60_000, REFRESH_NEVER};
                refreshMs = values[position];
                handler.removeCallbacks(refreshRunnable);
                scheduleNextRefresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        controls.addView(refreshSpinner, new LinearLayout.LayoutParams(dp(150), dp(44)));

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.VERTICAL);
        root.addView(metrics, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout metricsRowOne = metricRow(metrics);
        LinearLayout metricsRowTwo = metricRow(metrics);
        gpsValue = metricCard(metricsRowOne, "PC CPU", "--");
        memoryValue = metricCard(metricsRowOne, "PC Memory", "--");
        diskValue = metricCard(metricsRowTwo, "PC Disk", "--");
        networkValue = metricCard(metricsRowTwo, "PC Network", "--");

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, dp(12), 0, dp(6));
        root.addView(tabs, new LinearLayout.LayoutParams(-1, -2));
        for (String tab : TABS) {
            Button button = new Button(this);
            button.setText(tab);
            button.setAllCaps(false);
            button.setTextSize(12);
            button.setGravity(Gravity.CENTER);
            button.setPadding(0, 0, 0, 0);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setMinHeight(0);
            button.setMinimumHeight(0);
            button.setOnClickListener(v -> {
                activeTab = ((Button) v).getText().toString();
                refreshNow();
            });
            tabButtons.add(button);
            tabs.addView(button, new LinearLayout.LayoutParams(0, dp(50), 1f));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                swipeStartX = event.getX();
                swipeStartY = event.getY();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                float dx = event.getX() - swipeStartX;
                float dy = event.getY() - swipeStartY;
                if (Math.abs(dx) > dp(72) && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                    switchTabBySwipe(dx < 0 ? 1 : -1);
                    return true;
                }
            }
            return false;
        });
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        applyTheme();
    }

    private TextView metricCard(LinearLayout parent, String label, String value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        cards.add(card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(112), 1f);
        lp.setMargins(0, 0, dp(10), dp(10));
        parent.addView(card, lp);
        addLabel(card, label, 13, true);
        TextView number = addLabel(card, value, 24, true);
        number.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        number.setSingleLine(true);
        number.setIncludeFontPadding(false);
        autoscale(number, 12, 24, 1);
        return number;
    }

    private LinearLayout metricRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private TextView addLabel(LinearLayout parent, String text, int sp, boolean strong) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(sp);
        label.setGravity(Gravity.START);
        label.setPadding(0, dp(2), 0, dp(2));
        if (strong) label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        themedLabels.add(label);
        if (strong) accentLabels.add(label);
        parent.addView(label, new LinearLayout.LayoutParams(-2, -2));
        return label;
    }

    private void addSpacer(LinearLayout parent, int widthDp) {
        View spacer = new View(this);
        parent.addView(spacer, new LinearLayout.LayoutParams(dp(widthDp), 1));
    }

    private void autoscale(TextView textView, int minSp, int maxSp, int stepSp) {
        textView.setAutoSizeTextTypeUniformWithConfiguration(
                minSp,
                maxSp,
                stepSp,
                TypedValue.COMPLEX_UNIT_SP
        );
    }

    private void switchTabBySwipe(int direction) {
        int current = 0;
        for (int i = 0; i < TABS.length; i++) {
            if (TABS[i].equals(activeTab)) {
                current = i;
                break;
            }
        }
        int next = current + direction;
        if (next < 0 || next >= TABS.length) return;
        activeTab = TABS[next];
        refreshNow();
    }

    private void refreshNow() {
        updatePcMetricCards();
        startPcFetch(false);

        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(memoryInfo);
        long totalMemory = memoryInfo.totalMem;
        long usedMemory = totalMemory - memoryInfo.availMem;

        StatFs stat = new StatFs(getDataDir().getAbsolutePath());
        long totalStorage = stat.getTotalBytes();
        long freeStorage = stat.getAvailableBytes();

        NetworkDelta delta = readNetworkDelta();

        lastScan.setText("Last scan: " + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()));
        renderTab(memoryInfo, totalStorage, freeStorage, delta);
        applyTheme();
    }

    private void updatePcMetricCards() {
        PcSnapshot pc = lastPcSnapshot;
        gpsValue.setText(pc.cpuPercent >= 0 ? pc.cpuPercent + "%" : pc.shortStatus);
        memoryValue.setText(pc.memoryPercent >= 0 ? pc.memoryPercent + "%" : "--");
        diskValue.setText(pc.diskPercent >= 0 ? pc.diskPercent + "%" : "--");
        networkValue.setText(pc.networkBytesPerSecond >= 0 ? formatRate(pc.networkBytesPerSecond) : "--");
    }

    private void startPcFetch(boolean force) {
        String url = normalizedPcStatusUrl();
        if (url.isEmpty()) {
            lastPcSnapshot = PcSnapshot.notConfigured();
            updatePcMetricCards();
            return;
        }
        if (pcFetchInFlight && !force) return;
        pcFetchInFlight = true;
        lastPcSnapshot = lastPcSnapshot.asLoading();
        updatePcMetricCards();
        new Thread(() -> {
            PcSnapshot snapshot;
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(3500);
                connection.setReadTimeout(3500);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) {
                    snapshot = PcSnapshot.offline("HTTP " + code);
                } else {
                    java.io.InputStream stream = connection.getInputStream();
                    java.util.Scanner scanner = new java.util.Scanner(stream, "UTF-8").useDelimiter("\\A");
                    String body = scanner.hasNext() ? scanner.next() : "";
                    scanner.close();
                    stream.close();
                    snapshot = parsePcSnapshot(body);
                }
                connection.disconnect();
            } catch (Exception ex) {
                snapshot = PcSnapshot.offline(ex.getClass().getSimpleName());
            }
            PcSnapshot finalSnapshot = snapshot;
            handler.post(() -> {
                pcFetchInFlight = false;
                lastPcSnapshot = finalSnapshot;
                updatePcMetricCards();
                if ("PC".equals(activeTab) || "Pro".equals(activeTab)) {
                    refreshNowWithoutFetching();
                }
            });
        }).start();
    }

    private void refreshNowWithoutFetching() {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(memoryInfo);
        StatFs stat = new StatFs(getDataDir().getAbsolutePath());
        NetworkDelta delta = readNetworkDelta();
        renderTab(memoryInfo, stat.getTotalBytes(), stat.getAvailableBytes(), delta);
        applyTheme();
    }

    private PcSnapshot parsePcSnapshot(String body) throws Exception {
        JSONObject json = new JSONObject(body);
        PcSnapshot snapshot = new PcSnapshot();
        snapshot.online = true;
        snapshot.shortStatus = "Online";
        snapshot.statusDetail = json.optString("status", "PC agent online");
        snapshot.pcName = firstNonEmpty(json.optString("pcName", ""), json.optString("machineName", ""), json.optString("computerName", "PC"));
        snapshot.cpuPercent = readInt(json, "cpuPercent", "cpu", "cpuUsage");
        snapshot.memoryPercent = readInt(json, "memoryPercent", "memory", "memoryUsage");
        snapshot.diskPercent = readInt(json, "diskPercent", "systemDiskPercent", "diskUsage");
        snapshot.networkBytesPerSecond = readLong(json, "networkBytesPerSecond", "networkBps", "networkThroughput");
        snapshot.firewall = json.optString("firewall", json.optString("firewallStatus", "Firewall status not reported"));
        snapshot.defender = json.optString("defender", json.optString("defenderStatus", "Defender status not reported"));
        snapshot.alerts = readStringArray(json, "alerts");
        snapshot.connections = readStringArray(json, "tcpConnections");
        snapshot.listeningPorts = readStringArray(json, "listeningPorts");
        snapshot.lastUpdated = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        if (snapshot.alerts.isEmpty()) snapshot.alerts.add("No PC alerts reported by the agent.");
        return snapshot;
    }

    private int readInt(JSONObject json, String... keys) {
        for (String key : keys) {
            if (!json.has(key)) continue;
            double value = json.optDouble(key, -1);
            if (value >= 0) return (int) Math.round(value);
        }
        return -1;
    }

    private long readLong(JSONObject json, String... keys) {
        for (String key : keys) {
            if (!json.has(key)) continue;
            double value = json.optDouble(key, -1);
            if (value >= 0) return (long) value;
        }
        return -1L;
    }

    private List<String> readStringArray(JSONObject json, String key) {
        List<String> rows = new ArrayList<>();
        JSONArray array = json.optJSONArray(key);
        if (array == null) return rows;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "");
            if (!value.isEmpty()) rows.add(value);
        }
        return rows;
    }

    private String normalizedPcStatusUrl() {
        String value = pcAgentUrl == null ? "" : pcAgentUrl.trim();
        if (value.isEmpty()) return "";
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://" + value;
        }
        if (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (!value.endsWith("/status") && !value.endsWith("/api/status")) {
            value = value + "/api/status";
        }
        return value;
    }

    private SharedPreferences getPreferences() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void showPcAgentUrlDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(pcAgentUrl);
        input.setHint("http://192.168.1.10:8787");
        input.setSelectAllOnFocus(true);
        input.setTextColor(Color.rgb(20, 28, 24));
        input.setHintTextColor(Color.rgb(90, 104, 96));
        int pad = dp(18);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(pad, 0, pad, 0);
        wrapper.addView(input, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle("PC Agent URL")
                .setMessage("Enter the LAN address of the PC Sentinel agent. Free edition supports one PC. Multi-client monitoring is reserved for Pro.")
                .setView(wrapper)
                .setPositiveButton("Save", (dialog, which) -> {
                    pcAgentUrl = input.getText().toString().trim();
                    getPreferences().edit().putString(PREF_PC_AGENT_URL, pcAgentUrl).apply();
                    startPcFetch(true);
                    refreshNowWithoutFetching();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private void renderTab(ActivityManager.MemoryInfo memoryInfo, long totalStorage, long freeStorage, NetworkDelta delta) {
        content.removeAllViews();
        actionButtons.clear();
        updateTabButtons();
        List<String> alerts = buildAlerts(memoryInfo, totalStorage, freeStorage);
        if ("PC".equals(activeTab)) {
            actionButton("Set PC Agent URL", v -> showPcAgentUrlDialog());
            actionButton("Refresh PC Now", v -> startPcFetch(true));
            section("PC Connection", readPcConnectionRows());
            section("PC Resource Usage", readPcResourceRows());
            section("PC Security", readPcSecurityRows());
            section("PC Alerts", lastPcSnapshot.alerts);
        } else if ("Network".equals(activeTab)) {
            actionButton("Open Network Settings", v -> openNetworkSettings());
            section("PC TCP Connections", pcRowsOrLocked(lastPcSnapshot.connections, "Connect a PC agent to show TCP connections from the monitored PC."));
            section("PC Listening Ports", pcRowsOrLocked(lastPcSnapshot.listeningPorts, "Connect a PC agent to show listening TCP ports from the monitored PC."));
            section("Network Throughput", list("Download " + formatRate(delta.rxPerSecond), "Upload " + formatRate(delta.txPerSecond)));
            section("Active Network", readActiveNetwork());
            section("Network Interfaces", readInterfaces());
        } else if ("Security".equals(activeTab)) {
            actionButton("Open Security Settings", v -> startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)));
            section("PC Security", readPcSecurityRows());
            section("Security Checks", readSecurityChecks());
        } else if ("Clients".equals(activeTab)) {
            section("Free Client", readProClientRows());
            section("How It Works", list(
                    "Install/run the PC Sentinel NET agent on the Windows PC.",
                    "Connect this Android companion to the PC agent URL on the same network.",
                    "Free mode is one Windows PC. Pro will unlock saved multi-client monitoring."
            ));
        } else {
            section("PC Sentinel Pro", readProRows());
            section("Future Multi-Client Dashboard", readProClientRows());
        }
    }

    private List<String> readPcConnectionRows() {
        PcSnapshot pc = lastPcSnapshot;
        List<String> rows = new ArrayList<>();
        rows.add("Status: " + pc.statusDetail);
        rows.add("PC: " + pc.pcName);
        rows.add("Agent URL: " + (pcAgentUrl == null || pcAgentUrl.trim().isEmpty() ? "not configured" : normalizedPcStatusUrl()));
        rows.add("Last update: " + pc.lastUpdated);
        rows.add("Free edition: one PC connection");
        return rows;
    }

    private List<String> readPcResourceRows() {
        PcSnapshot pc = lastPcSnapshot;
        return list(
                "CPU: " + formatPercentOrUnknown(pc.cpuPercent),
                "Memory: " + formatPercentOrUnknown(pc.memoryPercent),
                "System disk: " + formatPercentOrUnknown(pc.diskPercent),
                "Network throughput: " + (pc.networkBytesPerSecond >= 0 ? formatRate(pc.networkBytesPerSecond) : "unknown")
        );
    }

    private List<String> readPcSecurityRows() {
        PcSnapshot pc = lastPcSnapshot;
        return list(
                "Firewall: " + pc.firewall,
                "Microsoft Defender: " + pc.defender,
                "Security data source: " + (pc.online ? "PC agent" : "waiting for PC agent")
        );
    }

    private List<String> pcRowsOrLocked(List<String> rows, String fallback) {
        if (rows != null && !rows.isEmpty()) return rows;
        return Collections.singletonList(fallback);
    }

    private List<String> readProRows() {
        return list(
                "Pro version planned: multi-client PC monitoring",
                "Free version: monitor one PC from this phone",
                "Pro targets: 3, 10, 25, or more PCs",
                "Planned features: saved clients, groups, alert history, export reports, and remote status dashboard",
                "License hook: disabled in this build"
        );
    }

    private List<String> readProClientRows() {
        return list(
                "Client 1: " + (lastPcSnapshot.online ? lastPcSnapshot.pcName + " | online | " + formatPercentOrUnknown(lastPcSnapshot.cpuPercent) + " CPU" : "available in free mode after connecting one PC"),
                "Client 2: Pro",
                "Client 3: Pro",
                "More clients: Pro Business"
        );
    }

    private String formatPercentOrUnknown(int value) {
        return value >= 0 ? value + "%" : "unknown";
    }

    private void section(String title, List<String> rows) {
        TextView heading = addLabel(content, title, 16, true);
        heading.setPadding(0, dp(14), 0, dp(6));
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        cards.add(panel);
        content.addView(panel, new LinearLayout.LayoutParams(-1, -2));
        for (String row : rows) {
            TextView label = addLabel(panel, row, 13, false);
            label.setPadding(0, dp(4), 0, dp(4));
        }
    }

    private void actionButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(foreground());
        button.setBackgroundColor(panel());
        button.setOnClickListener(listener);
        actionButtons.add(button);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, dp(48));
        layout.setMargins(0, 0, 0, 0);
        content.addView(button, layout);
    }

    private List<String> buildAlerts(ActivityManager.MemoryInfo memoryInfo, long totalStorage, long freeStorage) {
        List<String> alerts = new ArrayList<>();
        long usedMemory = memoryInfo.totalMem - memoryInfo.availMem;
        if (usedMemory * 100 / memoryInfo.totalMem >= 85) alerts.add("High memory usage detected.");
        if ((totalStorage - freeStorage) * 100 / totalStorage >= 90) alerts.add("Storage usage is high.");
        if (isAdbEnabled()) alerts.add("USB debugging appears enabled.");
        if (canInstallUnknownApps()) alerts.add("This app is allowed to install unknown apps.");
        if (isDeviceLockMissing()) alerts.add("No secure screen lock detected.");
        return alerts;
    }

    private void openNetworkSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        } catch (Exception ex) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
                Toast.makeText(this, "Network settings unavailable", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private List<String> readActiveNetwork() {
        List<String> rows = new ArrayList<>();
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        Network network = cm.getActiveNetwork();
        if (network == null) return Collections.singletonList("No active network.");
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) return Collections.singletonList("Network capabilities unavailable.");
        rows.add(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi active" : "Wi-Fi inactive");
        rows.add(caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "Cellular active" : "Cellular inactive");
        rows.add(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? "VPN active" : "VPN inactive");
        rows.add(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? "Internet validated" : "Internet not validated");
        rows.add(cm.isActiveNetworkMetered() ? "Metered connection" : "Unmetered connection");
        return rows;
    }

    private List<String> readInterfaces() {
        List<String> rows = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface item = interfaces.nextElement();
                if (!item.isUp()) continue;
                rows.add(item.getName() + " | mtu " + item.getMTU() + " | loopback " + yesNo(item.isLoopback()));
            }
        } catch (Exception ex) {
            rows.add("Network interfaces unavailable: " + ex.getClass().getSimpleName());
        }
        if (rows.isEmpty()) rows.add("No active interfaces reported.");
        return rows;
    }

    private List<String> readSecurityChecks() {
        List<String> rows = new ArrayList<>();
        rows.add(isDeviceLockMissing() ? "Screen lock: not secure" : "Screen lock: secure or managed");
        rows.add(isAdbEnabled() ? "USB debugging: enabled" : "USB debugging: disabled");
        rows.add(canInstallUnknownApps() ? "Unknown app installs: allowed for this app" : "Unknown app installs: not allowed for this app");
        rows.add(isVpnActive() ? "VPN: active" : "VPN: inactive");
        rows.add("Private DNS: " + readGlobalSetting("private_dns_mode", "unknown"));
        rows.add("Developer options: " + ("1".equals(readGlobalSetting("development_settings_enabled", "0")) ? "enabled" : "disabled"));
        rows.add("Security patch: " + Build.VERSION.SECURITY_PATCH);
        return rows;
    }

    private List<String> readDeviceInfo() {
        List<String> rows = new ArrayList<>();
        rows.add("Manufacturer: " + Build.MANUFACTURER);
        rows.add("Model: " + Build.MODEL);
        rows.add("Device: " + Build.DEVICE);
        rows.add("Android: " + Build.VERSION.RELEASE + " API " + Build.VERSION.SDK_INT);
        rows.add("Build: " + Build.DISPLAY);
        rows.add("Hardware: " + Build.HARDWARE);
        return rows;
    }

    private List<String> readInstalledApps() {
        List<String> rows = new ArrayList<>();
        PackageManager pm = getPackageManager();
        List<android.content.pm.ApplicationInfo> apps = pm.getInstalledApplications(0);
        apps.sort(Comparator.comparing(app -> String.valueOf(pm.getApplicationLabel(app))));
        int count = 0;
        for (android.content.pm.ApplicationInfo app : apps) {
            if ((app.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            rows.add(pm.getApplicationLabel(app).toString() + " | " + app.packageName);
            if (++count >= 25) break;
        }
        if (rows.isEmpty()) rows.add("No user-installed apps visible.");
        return rows;
    }

    private NetworkDelta readNetworkDelta() {
        long now = System.currentTimeMillis();
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        long elapsed = Math.max(1L, now - lastNetworkAt);
        long rxRate = Math.max(0L, (rx - lastRx) * 1000L / elapsed);
        long txRate = Math.max(0L, (tx - lastTx) * 1000L / elapsed);
        lastRx = rx;
        lastTx = tx;
        lastNetworkAt = now;
        return new NetworkDelta(rxRate, txRate);
    }

    private void scheduleNextRefresh() {
        handler.removeCallbacks(refreshRunnable);
        if (refreshMs != REFRESH_NEVER) handler.postDelayed(refreshRunnable, refreshMs);
    }

    private void updateTabButtons() {
        for (Button button : tabButtons) {
            boolean selected = activeTab.contentEquals(button.getText());
            button.setTextColor(selected ? accent() : foreground());
            button.setBackgroundColor(selected ? panel() : background());
        }
    }

    private void applyTheme() {
        root.setBackgroundColor(background());
        themeButton.setText(darkMode ? "Light Mode" : "Dark Mode");
        themeButton.setTextColor(foreground());
        themeButton.setBackgroundColor(panel());
        refreshSpinner.setBackgroundColor(background());
        View selectedRefreshView = refreshSpinner.getSelectedView();
        if (selectedRefreshView instanceof TextView) {
            ((TextView) selectedRefreshView).setTextColor(foreground());
        }
        for (TextView label : themedLabels) {
            label.setTextColor(foreground());
        }
        for (TextView label : accentLabels) {
            label.setTextColor(accent());
        }
        for (TextView label : highlightLabels) {
            label.setTextColor(accent());
            label.setBackgroundColor(highlight());
        }
        for (View card : cards) {
            card.setBackgroundColor(panel());
        }
        for (Button button : actionButtons) {
            button.setTextColor(foreground());
            button.setBackgroundColor(panel());
        }
        updateTabButtons();
    }

    private int background() {
        return darkMode ? Color.rgb(9, 14, 12) : Color.rgb(239, 244, 241);
    }

    private int panel() {
        return darkMode ? Color.rgb(20, 34, 27) : Color.WHITE;
    }

    private int foreground() {
        return darkMode ? Color.WHITE : Color.rgb(20, 28, 24);
    }

    private int accent() {
        return darkMode ? Color.rgb(105, 240, 168) : Color.rgb(0, 112, 64);
    }

    private int highlight() {
        return darkMode ? Color.rgb(28, 55, 40) : Color.rgb(218, 247, 231);
    }

    private boolean isAdbEnabled() {
        return "1".equals(readGlobalSetting("adb_enabled", "0"));
    }

    private boolean canInstallUnknownApps() {
        try {
            return getPackageManager().canRequestPackageInstalls();
        } catch (SecurityException ex) {
            return false;
        }
    }

    private boolean isDeviceLockMissing() {
        try {
            android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            return km != null && !km.isDeviceSecure();
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isVpnActive() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }

    private String readGlobalSetting(String key, String fallback) {
        try {
            String value = Settings.Global.getString(getContentResolver(), key);
            return value == null ? fallback : value;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String percent(long used, long total) {
        if (total <= 0) return "--";
        return String.format(Locale.US, "%d%%", Math.round((used * 100.0) / total));
    }

    private String formatRate(long bytesPerSecond) {
        return Formatter.formatFileSize(this, bytesPerSecond) + "/s";
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private List<String> list(String... rows) {
        ArrayList<String> out = new ArrayList<>();
        Collections.addAll(out, rows);
        return out;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class PcSnapshot {
        boolean online;
        String shortStatus = "--";
        String statusDetail = "PC agent not configured";
        String pcName = "PC";
        String firewall = "Firewall status not reported";
        String defender = "Defender status not reported";
        String lastUpdated = "--";
        int cpuPercent = -1;
        int memoryPercent = -1;
        int diskPercent = -1;
        long networkBytesPerSecond = -1L;
        List<String> alerts = new ArrayList<>();
        List<String> connections = new ArrayList<>();
        List<String> listeningPorts = new ArrayList<>();

        static PcSnapshot notConfigured() {
            PcSnapshot snapshot = new PcSnapshot();
            snapshot.shortStatus = "Set URL";
            snapshot.statusDetail = "PC agent URL is not configured.";
            snapshot.alerts.add("Set a PC agent URL to monitor a Windows PC from Android.");
            return snapshot;
        }

        static PcSnapshot offline(String reason) {
            PcSnapshot snapshot = new PcSnapshot();
            snapshot.shortStatus = "Offline";
            snapshot.statusDetail = "PC agent unreachable: " + reason;
            snapshot.alerts.add("PC agent is offline or blocked by firewall.");
            snapshot.alerts.add("Check the PC agent URL, Windows Firewall, and that phone and PC are on the same network.");
            return snapshot;
        }

        PcSnapshot asLoading() {
            PcSnapshot snapshot = copy();
            snapshot.shortStatus = "Scanning";
            snapshot.statusDetail = online ? "Refreshing PC agent data..." : statusDetail;
            return snapshot;
        }

        private PcSnapshot copy() {
            PcSnapshot snapshot = new PcSnapshot();
            snapshot.online = online;
            snapshot.shortStatus = shortStatus;
            snapshot.statusDetail = statusDetail;
            snapshot.pcName = pcName;
            snapshot.firewall = firewall;
            snapshot.defender = defender;
            snapshot.lastUpdated = lastUpdated;
            snapshot.cpuPercent = cpuPercent;
            snapshot.memoryPercent = memoryPercent;
            snapshot.diskPercent = diskPercent;
            snapshot.networkBytesPerSecond = networkBytesPerSecond;
            snapshot.alerts = new ArrayList<>(alerts);
            snapshot.connections = new ArrayList<>(connections);
            snapshot.listeningPorts = new ArrayList<>(listeningPorts);
            return snapshot;
        }
    }

    private static class NetworkDelta {
        final long rxPerSecond;
        final long txPerSecond;

        NetworkDelta(long rxPerSecond, long txPerSecond) {
            this.rxPerSecond = rxPerSecond;
            this.txPerSecond = txPerSecond;
        }
    }

}
