package com.sentinel.mobile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.BatteryManager;
import android.app.KeyguardManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
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
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REFRESH_NEVER = -1;
    private static final int LOCATION_PERMISSION_REQUEST = 42;
    private static final String[] TABS = {"Overview", "Network", "Security", "Storage", "Device"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<TextView> labels = new ArrayList<>();
    private final List<TextView> strongLabels = new ArrayList<>();
    private final List<View> panels = new ArrayList<>();
    private final List<Button> tabButtons = new ArrayList<>();
    private final List<Button> actionButtons = new ArrayList<>();

    private LinearLayout root;
    private LinearLayout content;
    private TextView gpsValue;
    private TextView memoryValue;
    private TextView storageValue;
    private TextView networkValue;
    private TextView lastScan;
    private Spinner refreshSpinner;
    private Button themeButton;

    private boolean darkMode = true;
    private boolean locationRequestActive;
    private String activeTab = "Overview";
    private String lastGpsCoordinates = "";
    private int refreshMs = 60_000;
    private float swipeStartX;
    private float swipeStartY;
    private long lastRx = TrafficStats.getTotalRxBytes();
    private long lastTx = TrafficStats.getTotalTxBytes();
    private long lastNetworkAt = System.currentTimeMillis();

    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            refreshNow();
            scheduleNextRefresh();
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        requestLocationPermissionIfNeeded();
        refreshNow();
        scheduleNextRefresh();
    }

    @Override protected void onDestroy() {
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

        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.VERTICAL);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        addLabel(title, "Sentinel Mobile", 24, true);
        addLabel(title, "Local Android monitoring for device health, network, and security posture", 13, false);

        themeButton = new Button(this);
        themeButton.setAllCaps(false);
        themeButton.setOnClickListener(v -> { darkMode = !darkMode; applyTheme(); });
        top.addView(themeButton, new LinearLayout.LayoutParams(dp(128), dp(48)));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(0, dp(14), 0, dp(8));
        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        lastScan = addLabel(controls, "Last scan: --", 13, true);
        addSpacer(controls, 12);
        addLabel(controls, "Refresh:", 13, false);

        refreshSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"5 seconds", "30 seconds", "1 minute", "Never"}) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(foreground());
                view.setTextSize(14);
                view.setSingleLine(true);
                return view;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(Color.rgb(20, 28, 24));
                view.setTextSize(14);
                return view;
            }
        };
        refreshSpinner.setAdapter(adapter);
        refreshSpinner.setSelection(2);
        refreshSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshMs = new int[]{5_000, 30_000, 60_000, REFRESH_NEVER}[position];
                handler.removeCallbacks(refreshRunnable);
                scheduleNextRefresh();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        controls.addView(refreshSpinner, new LinearLayout.LayoutParams(dp(140), dp(44)));

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.VERTICAL);
        root.addView(metrics, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout rowOne = metricRow(metrics);
        LinearLayout rowTwo = metricRow(metrics);
        gpsValue = metricCard(rowOne, "GPS", "--");
        gpsValue.setSingleLine(false);
        gpsValue.setMaxLines(3);
        gpsValue.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        gpsValue.setOnClickListener(v -> openLocationSettingsIfNeeded());
        gpsValue.setOnLongClickListener(v -> { copyGpsCoordinates(); return true; });
        View gpsCard = (View) gpsValue.getParent();
        gpsCard.setOnClickListener(v -> openLocationSettingsIfNeeded());
        gpsCard.setOnLongClickListener(v -> { copyGpsCoordinates(); return true; });
        memoryValue = metricCard(rowOne, "Memory", "--");
        storageValue = metricCard(rowTwo, "Storage", "--");
        networkValue = metricCard(rowTwo, "Network", "--");

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, dp(12), 0, dp(6));
        root.addView(tabs, new LinearLayout.LayoutParams(-1, -2));
        for (String tab : TABS) {
            Button button = new Button(this);
            button.setText(tab);
            button.setAllCaps(false);
            button.setTextSize(12);
            button.setPadding(0, 0, 0, 0);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setOnClickListener(v -> { activeTab = ((Button) v).getText().toString(); refreshNow(); });
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
                    switchTab(dx < 0 ? 1 : -1);
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

    private LinearLayout metricRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private TextView metricCard(LinearLayout parent, String label, String value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        panels.add(card);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, dp(112), 1f);
        layout.setMargins(0, 0, dp(10), dp(10));
        parent.addView(card, layout);
        addLabel(card, label, 13, true);
        TextView number = addLabel(card, value, 24, true);
        number.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        number.setIncludeFontPadding(false);
        number.setSingleLine(true);
        number.setAutoSizeTextTypeUniformWithConfiguration(10, 24, 1, TypedValue.COMPLEX_UNIT_SP);
        return number;
    }

    private TextView addLabel(LinearLayout parent, String text, int sp, boolean strong) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(sp);
        label.setGravity(Gravity.START);
        label.setPadding(0, dp(2), 0, dp(2));
        if (strong) label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        labels.add(label);
        if (strong) strongLabels.add(label);
        parent.addView(label, new LinearLayout.LayoutParams(-2, -2));
        return label;
    }

    private void addSpacer(LinearLayout parent, int widthDp) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(dp(widthDp), 1));
    }

    private void refreshNow() {
        gpsValue.setText(readGpsCoordinates());
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(memoryInfo);
        long usedMemory = memoryInfo.totalMem - memoryInfo.availMem;
        memoryValue.setText(percent(usedMemory, memoryInfo.totalMem));

        StatFs stat = new StatFs(getDataDir().getAbsolutePath());
        long totalStorage = stat.getTotalBytes();
        long freeStorage = stat.getAvailableBytes();
        storageValue.setText(percent(totalStorage - freeStorage, totalStorage));

        NetworkDelta delta = readNetworkDelta();
        networkValue.setText(formatRate(delta.rxPerSecond + delta.txPerSecond));
        lastScan.setText("Last scan: " + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()));
        renderTab(memoryInfo, totalStorage, freeStorage, delta);
        applyTheme();
    }

    private void renderTab(ActivityManager.MemoryInfo memoryInfo, long totalStorage, long freeStorage, NetworkDelta delta) {
        content.removeAllViews();
        actionButtons.clear();
        updateTabButtons();
        if ("Overview".equals(activeTab)) {
            List<String> alerts = buildAlerts(memoryInfo, totalStorage, freeStorage);
            section("Alerts", alerts.isEmpty() ? Collections.singletonList("No active alerts from the latest scan.") : alerts);
            section("Battery Information", readBatteryInfo());
            section("Android Limits", Collections.singletonList("Restricted by Android: normal apps cannot inspect global TCP ports, firewall profiles, or full process CPU usage."));
        } else if ("Network".equals(activeTab)) {
            actionButton("Open Network Settings", v -> openNetworkSettings());
            section("Network Throughput", list("Download " + formatRate(delta.rxPerSecond), "Upload " + formatRate(delta.txPerSecond)));
            section("Active Network", readActiveNetwork());
            section("Network Interfaces", readInterfaces());
        } else if ("Security".equals(activeTab)) {
            actionButton("Open Security Settings", v -> startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)));
            section("Security Checks", readSecurityChecks());
        } else if ("Storage".equals(activeTab)) {
            actionButton("Open Phone Storage Settings", v -> openStorageSettings());
            section("Storage", list("Used " + Formatter.formatFileSize(this, totalStorage - freeStorage), "Free " + Formatter.formatFileSize(this, freeStorage), "Total " + Formatter.formatFileSize(this, totalStorage)));
            section("Android Storage Limits", Collections.singletonList("Full file inventory belongs in Android storage settings; Sentinel Mobile reports safe storage totals only."));
        } else {
            actionButton("Open Device Settings", v -> openDeviceSettings());
            section("Device Info", readDeviceInfo());
            section("Installed Apps", readInstalledApps());
        }
    }

    private void section(String title, List<String> rows) {
        TextView heading = addLabel(content, title, 16, true);
        heading.setPadding(0, dp(14), 0, dp(6));
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panels.add(panel);
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
        button.setOnClickListener(listener);
        actionButtons.add(button);
        content.addView(button, new LinearLayout.LayoutParams(-1, dp(48)));
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

    private void requestLocationPermissionIfNeeded() {
        if (!hasLocationPermission()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) refreshNow();
    }

    @SuppressLint("MissingPermission") private String readGpsCoordinates() {
        if (!hasLocationPermission()) {
            lastGpsCoordinates = "";
            return "Allow location";
        }
        try {
            LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (manager == null) return "Location unavailable";
            if (!isLocationEnabled(manager)) {
                lastGpsCoordinates = "";
                locationRequestActive = false;
                return "Turn on\nLocation";
            }
            Location best = null;
            for (String provider : manager.getProviders(true)) {
                Location location = manager.getLastKnownLocation(provider);
                if (location != null && (best == null || location.getAccuracy() < best.getAccuracy())) best = location;
            }
            if (best == null) {
                requestFreshLocation(manager);
                return "Waiting for GPS";
            }
            return formatCoordinates(best);
        } catch (Exception ex) {
            lastGpsCoordinates = "";
            return "Location unavailable";
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isLocationEnabled(LocationManager manager) {
        if (Build.VERSION.SDK_INT >= 28) return manager.isLocationEnabled();
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void openLocationSettingsIfNeeded() {
        try {
            LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (manager != null && !isLocationEnabled(manager)) {
                Toast.makeText(this, "Turn on Location, then return to Sentinel Mobile", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        } catch (Exception ex) {
            Toast.makeText(this, "Location settings unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("MissingPermission") private void requestFreshLocation(LocationManager manager) {
        if (locationRequestActive || !hasLocationPermission()) return;
        try {
            locationRequestActive = true;
            LocationListener listener = new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    locationRequestActive = false;
                    gpsValue.setText(formatCoordinates(location));
                }
                @Override public void onProviderDisabled(String provider) { locationRequestActive = false; }
                @Override public void onProviderEnabled(String provider) { }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
            };
            for (String provider : manager.getAllProviders()) {
                try { manager.requestSingleUpdate(provider, listener, Looper.getMainLooper()); } catch (Exception ignored) { }
            }
        } catch (Exception ex) {
            locationRequestActive = false;
        }
    }

    private String formatCoordinates(Location location) {
        lastGpsCoordinates = String.format(Locale.US, "%.5f, %.5f", location.getLatitude(), location.getLongitude());
        return String.format(Locale.US, "Lat %.5f\nLon %.5f", location.getLatitude(), location.getLongitude());
    }

    private void copyGpsCoordinates() {
        if (lastGpsCoordinates == null || lastGpsCoordinates.isEmpty()) {
            Toast.makeText(this, "GPS coordinates not available", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("GPS coordinates", lastGpsCoordinates));
        String copied = lastGpsCoordinates;
        handler.postDelayed(() -> clearGpsClipboardIfUnchanged(copied), 30_000);
        Toast.makeText(this, "GPS coordinates copied", Toast.LENGTH_SHORT).show();
    }

    private void clearGpsClipboardIfUnchanged(String copied) {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) return;
            ClipData current = clipboard.getPrimaryClip();
            if (current == null || current.getItemCount() == 0) return;
            CharSequence text = current.getItemAt(0).coerceToText(this);
            if (copied.contentEquals(text)) clipboard.setPrimaryClip(ClipData.newPlainText("Sentinel Mobile", ""));
        } catch (Exception ignored) { }
    }

    private List<String> readBatteryInfo() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return Collections.singletonList("Battery information unavailable.");
        List<String> rows = new ArrayList<>();
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int percent = scale > 0 && level >= 0 ? Math.round(level * 100f / scale) : -1;
        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int health = battery.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
        int temperature = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        int voltage = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        rows.add(percent >= 0 ? "Level: " + percent + "%" : "Level: unknown");
        rows.add("Status: " + batteryStatus(status));
        rows.add("Power source: " + powerSource(plugged));
        rows.add("Health: " + batteryHealth(health));
        if (temperature != Integer.MIN_VALUE) rows.add(String.format(Locale.US, "Temperature: %.1f C", temperature / 10f));
        if (voltage > 0) rows.add(String.format(Locale.US, "Voltage: %.2f V", voltage / 1000f));
        rows.add("Estimated time remaining: " + estimateBatteryTime(status));
        return rows;
    }

    private String estimateBatteryTime(int status) {
        if (status == BatteryManager.BATTERY_STATUS_FULL) return "fully charged";
        if (status == BatteryManager.BATTERY_STATUS_CHARGING) return "charging";
        try {
            BatteryManager manager = (BatteryManager) getSystemService(BATTERY_SERVICE);
            if (manager == null) return "not exposed by Android";
            int charge = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            int current = Math.abs(manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE));
            if (charge <= 0 || current <= 0 || current == Integer.MIN_VALUE) return "not exposed by Android";
            int minutes = Math.max(1, (int) Math.round(charge * 60.0 / current));
            return minutes >= 60 ? (minutes / 60) + " h " + (minutes % 60) + " min estimate" : minutes + " min estimate";
        } catch (Exception ex) {
            return "not exposed by Android";
        }
    }

    private String batteryStatus(int status) {
        if (status == BatteryManager.BATTERY_STATUS_CHARGING) return "charging";
        if (status == BatteryManager.BATTERY_STATUS_DISCHARGING) return "discharging";
        if (status == BatteryManager.BATTERY_STATUS_FULL) return "full";
        if (status == BatteryManager.BATTERY_STATUS_NOT_CHARGING) return "not charging";
        return "unknown";
    }

    private String powerSource(int plugged) {
        if ((plugged & BatteryManager.BATTERY_PLUGGED_USB) != 0) return "USB";
        if ((plugged & BatteryManager.BATTERY_PLUGGED_AC) != 0) return "AC";
        if ((plugged & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0) return "wireless";
        return "battery";
    }

    private String batteryHealth(int health) {
        if (health == BatteryManager.BATTERY_HEALTH_GOOD) return "good";
        if (health == BatteryManager.BATTERY_HEALTH_OVERHEAT) return "overheat";
        if (health == BatteryManager.BATTERY_HEALTH_DEAD) return "dead";
        if (health == BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE) return "over voltage";
        if (health == BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE) return "failure";
        if (Build.VERSION.SDK_INT >= 26 && health == BatteryManager.BATTERY_HEALTH_COLD) return "cold";
        return "unknown";
    }

    private List<String> readActiveNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        Network network = cm.getActiveNetwork();
        if (network == null) return Collections.singletonList("No active network.");
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) return Collections.singletonList("Network capabilities unavailable.");
        return list(
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi active" : "Wi-Fi inactive",
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "Cellular active" : "Cellular inactive",
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? "VPN active" : "VPN inactive",
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? "Internet validated" : "Internet not validated",
                cm.isActiveNetworkMetered() ? "Metered connection" : "Unmetered connection");
    }

    private List<String> readInterfaces() {
        List<String> rows = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface item = interfaces.nextElement();
                if (item.isUp()) rows.add(item.getName() + " | mtu " + item.getMTU() + " | loopback " + yesNo(item.isLoopback()));
            }
        } catch (Exception ex) {
            rows.add("Network interfaces unavailable: " + ex.getClass().getSimpleName());
        }
        if (rows.isEmpty()) rows.add("No active interfaces reported.");
        return rows;
    }

    private List<String> readSecurityChecks() {
        return list(
                isDeviceLockMissing() ? "Screen lock: not secure" : "Screen lock: secure or managed",
                isAdbEnabled() ? "USB debugging: enabled" : "USB debugging: disabled",
                canInstallUnknownApps() ? "Unknown app installs: allowed for this app" : "Unknown app installs: not allowed for this app",
                isVpnActive() ? "VPN: active" : "VPN: inactive",
                "Private DNS: " + readGlobalSetting("private_dns_mode", "unknown"),
                "Developer options: " + ("1".equals(readGlobalSetting("development_settings_enabled", "0")) ? "enabled" : "disabled"),
                "Security patch: " + Build.VERSION.SECURITY_PATCH);
    }

    private List<String> readDeviceInfo() {
        return list("Manufacturer: " + Build.MANUFACTURER, "Model: " + Build.MODEL, "Device: " + Build.DEVICE,
                "Android: " + Build.VERSION.RELEASE + " API " + Build.VERSION.SDK_INT, "Build: " + Build.DISPLAY, "Hardware: " + Build.HARDWARE);
    }

    private List<String> readInstalledApps() {
        List<String> rows = new ArrayList<>();
        PackageManager pm = getPackageManager();
        List<android.content.pm.ApplicationInfo> apps = pm.getInstalledApplications(0);
        apps.sort(Comparator.comparing(app -> String.valueOf(pm.getApplicationLabel(app))));
        int count = 0;
        for (android.content.pm.ApplicationInfo app : apps) {
            if ((app.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            rows.add(pm.getApplicationLabel(app) + " | " + app.packageName);
            if (++count >= 25) break;
        }
        if (rows.isEmpty()) rows.add("No user-installed apps visible.");
        return rows;
    }

    private NetworkDelta readNetworkDelta() {
        long now = System.currentTimeMillis();
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        long elapsed = Math.max(1, now - lastNetworkAt);
        NetworkDelta delta = new NetworkDelta(Math.max(0, (rx - lastRx) * 1000 / elapsed), Math.max(0, (tx - lastTx) * 1000 / elapsed));
        lastRx = rx;
        lastTx = tx;
        lastNetworkAt = now;
        return delta;
    }

    private void scheduleNextRefresh() {
        handler.removeCallbacks(refreshRunnable);
        if (refreshMs != REFRESH_NEVER) handler.postDelayed(refreshRunnable, refreshMs);
    }

    private void switchTab(int direction) {
        int index = 0;
        for (int i = 0; i < TABS.length; i++) if (TABS[i].equals(activeTab)) index = i;
        int next = index + direction;
        if (next >= 0 && next < TABS.length) {
            activeTab = TABS[next];
            refreshNow();
        }
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
        if (selectedRefreshView instanceof TextView) ((TextView) selectedRefreshView).setTextColor(foreground());
        for (TextView label : labels) label.setTextColor(foreground());
        for (TextView label : strongLabels) label.setTextColor(accent());
        for (View panel : panels) panel.setBackgroundColor(panel());
        for (Button button : actionButtons) {
            button.setTextColor(foreground());
            button.setBackgroundColor(panel());
        }
        updateTabButtons();
    }

    private void openStorageSettings() { openSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS); }
    private void openNetworkSettings() { openSettings(Settings.ACTION_WIRELESS_SETTINGS); }
    private void openDeviceSettings() { openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS); }
    private void openSettings(String action) {
        try { startActivity(new Intent(action)); }
        catch (Exception ex) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
    }

    private boolean isAdbEnabled() { return "1".equals(readGlobalSetting("adb_enabled", "0")); }
    private boolean canInstallUnknownApps() {
        try { return getPackageManager().canRequestPackageInstalls(); }
        catch (SecurityException ex) { return false; }
    }
    private boolean isDeviceLockMissing() {
        try {
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            return km != null && !km.isDeviceSecure();
        } catch (Exception ex) { return false; }
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
        } catch (Exception ex) { return fallback; }
    }

    private int background() { return darkMode ? Color.rgb(9, 14, 12) : Color.rgb(239, 244, 241); }
    private int panel() { return darkMode ? Color.rgb(20, 34, 27) : Color.WHITE; }
    private int foreground() { return darkMode ? Color.WHITE : Color.rgb(20, 28, 24); }
    private int accent() { return darkMode ? Color.rgb(105, 240, 168) : Color.rgb(0, 112, 64); }
    private String percent(long used, long total) { return total <= 0 ? "--" : String.format(Locale.US, "%d%%", Math.round((used * 100.0) / total)); }
    private String formatRate(long bytesPerSecond) { return Formatter.formatFileSize(this, bytesPerSecond) + "/s"; }
    private String yesNo(boolean value) { return value ? "yes" : "no"; }
    private List<String> list(String... rows) { ArrayList<String> out = new ArrayList<>(); Collections.addAll(out, rows); return out; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private static class NetworkDelta {
        final long rxPerSecond;
        final long txPerSecond;
        NetworkDelta(long rxPerSecond, long txPerSecond) {
            this.rxPerSecond = rxPerSecond;
            this.txPerSecond = txPerSecond;
        }
    }
}
