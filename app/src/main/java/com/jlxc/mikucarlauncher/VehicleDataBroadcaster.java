package com.jlxc.mikucarlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class VehicleDataBroadcaster {
    public static final String PREF_HUD_BROADCAST_ENABLED = "hud_broadcast_enabled";
    public static final String PREF_HUD_BROADCAST_PORT = "hud_broadcast_port";
    public static final String PREF_HUD_BROADCAST_INTERVAL_MS = "hud_broadcast_interval_ms";
    public static final String PREF_HUD_BROADCAST_ADDRESS = "hud_broadcast_address";

    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_PORT = 36970;
    public static final int DEFAULT_INTERVAL_MS = 200;
    public static final String DEFAULT_ADDRESS = "255.255.255.255";

    private final Context context;
    private final VehicleDataProvider provider;
    private final Object lock = new Object();

    private HandlerThread thread;
    private Handler handler;
    private boolean started;
    private DatagramSocket socket;
    private long seq = 0L;
    private String lastStatus = "未启动";
    private long lastSentElapsed = 0L;

    public VehicleDataBroadcaster(Context context, VehicleDataProvider provider) {
        this.context = context.getApplicationContext();
        this.provider = provider;
    }

    public void start() {
        synchronized (lock) {
            if (started) return;
            started = true;
        }

        if (thread == null) {
            thread = new HandlerThread("MikuCarLauncher-HUD-Broadcast");
            thread.start();
            handler = new Handler(thread.getLooper());
        }
        handler.removeCallbacks(broadcastRunnable);
        handler.post(broadcastRunnable);
    }

    public void stop() {
        synchronized (lock) {
            started = false;
        }

        if (handler != null) {
            handler.removeCallbacks(broadcastRunnable);
        }

        closeSocket();

        if (thread != null) {
            thread.quitSafely();
            thread = null;
            handler = null;
        }
        lastStatus = "已停止";
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public long getLastSentElapsed() {
        return lastSentElapsed;
    }

    private final Runnable broadcastRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (isEnabled()) {
                    sendOnce();
                } else {
                    closeSocket();
                    lastStatus = "未启用";
                }
            } catch (Throwable t) {
                lastStatus = "发送失败: " + t.getClass().getSimpleName() + " " + String.valueOf(t.getMessage());
                closeSocket();
            }

            Handler h = handler;
            synchronized (lock) {
                if (started && h != null) {
                    h.postDelayed(this, getIntervalMs());
                }
            }
        }
    };

    private boolean isEnabled() {
        SharedPreferences sp = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        return sp.getBoolean(PREF_HUD_BROADCAST_ENABLED, DEFAULT_ENABLED);
    }

    private int getPort() {
        SharedPreferences sp = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        int port = sp.getInt(PREF_HUD_BROADCAST_PORT, DEFAULT_PORT);
        if (port < 1024) port = DEFAULT_PORT;
        if (port > 65535) port = DEFAULT_PORT;
        return port;
    }

    private int getIntervalMs() {
        SharedPreferences sp = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        int interval = sp.getInt(PREF_HUD_BROADCAST_INTERVAL_MS, DEFAULT_INTERVAL_MS);
        if (interval < 100) interval = 100;
        if (interval > 5000) interval = 5000;
        return interval;
    }

    private String getBroadcastAddress() {
        SharedPreferences sp = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        String addr = sp.getString(PREF_HUD_BROADCAST_ADDRESS, DEFAULT_ADDRESS);
        if (addr == null || addr.trim().length() == 0) {
            return DEFAULT_ADDRESS;
        }
        return addr.trim();
    }

    private void sendOnce() throws Exception {
        VehicleDataProvider.Snapshot s = provider == null ? null : provider.getSnapshot();
        if (s == null || !s.valid) {
            lastStatus = "等待车辆数据";
            return;
        }

        JSONObject json = snapshotToJson(s);
        byte[] bytes = json.toString().getBytes("UTF-8");

        DatagramSocket ds = ensureSocket();
        ds.setBroadcast(true);

        InetAddress address = InetAddress.getByName(getBroadcastAddress());
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, getPort());
        ds.send(packet);

        lastSentElapsed = SystemClock.elapsedRealtime();
        lastStatus = "已广播 seq=" + seq + " bytes=" + bytes.length + " → " + getBroadcastAddress() + ":" + getPort();
    }

    private DatagramSocket ensureSocket() throws Exception {
        if (socket == null || socket.isClosed()) {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setReuseAddress(true);
        }
        return socket;
    }

    private void closeSocket() {
        if (socket != null) {
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
        }
        socket = null;
    }

    private JSONObject snapshotToJson(VehicleDataProvider.Snapshot s) throws Exception {
        JSONObject json = new JSONObject();
        json.put("protocol", "MikuCarHUD");
        json.put("version", 1);
        json.put("source", "MikuCarLauncher");
        json.put("seq", ++seq);
        json.put("timestampElapsedMs", SystemClock.elapsedRealtime());
        json.put("valid", s.valid);

        json.put("speedKmh", safe(s.speed));
        json.put("rpm", safe(s.rpm));
        json.put("rangeKm", safe(s.rangeKm));
        json.put("fuelLevel", safe(s.fuelLevel));
        json.put("totalMileageKm", safe(s.totalMileageKm));

        json.put("driverSeatbelt", s.driverSeatbelt);
        json.put("passengerSeatbelt", s.passengerSeatbelt);

        JSONObject doors = new JSONObject();
        doors.put("frontLeft", s.frontLeftDoorOpen);
        doors.put("frontRight", s.frontRightDoorOpen);
        doors.put("rearLeft", s.rearLeftDoorOpen);
        doors.put("rearRight", s.rearRightDoorOpen);
        doors.put("trunk", s.trunkOpen);
        doors.put("hood", s.hoodOpen);
        json.put("doors", doors);

        json.put("leftTurn", s.leftTurnOn);
        json.put("rightTurn", s.rightTurnOn);
        json.put("highBeam", s.highBeam);
        json.put("hazard", s.hazard);

        json.put("frontRadar", intArrayToJson(s.frontRadar));
        json.put("rearRadar", intArrayToJson(s.rearRadar));
        json.put("rawBaseInfo", intArrayToJson(s.rawBaseInfo));

        json.put("dataSource", s.dataSource == null ? "" : s.dataSource);
        json.put("debugText", s.debugText == null ? "" : s.debugText);
        return json;
    }

    private int safe(int v) {
        return v < 0 ? -1 : v;
    }

    private JSONArray intArrayToJson(int[] arr) {
        JSONArray a = new JSONArray();
        if (arr != null) {
            for (int v : arr) {
                a.put(v);
            }
        }
        return a;
    }
}
