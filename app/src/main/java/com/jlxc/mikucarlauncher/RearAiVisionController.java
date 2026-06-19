package com.jlxc.mikucarlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class RearAiVisionController {
    public static final String PREF_ENABLED = "rear_ai_enabled";
    public static final String PREF_IP = "rear_ai_ip";
    public static final String PREF_HIDE_DELAY_SEC = "rear_ai_hide_delay_sec";
    public static final String PREF_LEFT_AUDIO_URI = "rear_ai_left_audio_uri";
    public static final String PREF_LEFT_AUDIO_NAME = "rear_ai_left_audio_name";
    public static final String PREF_RIGHT_AUDIO_URI = "rear_ai_right_audio_uri";
    public static final String PREF_RIGHT_AUDIO_NAME = "rear_ai_right_audio_name";

    public static final int UDP_PORT = 47210;
    public static final int HTTP_PORT = 47211;
    public static final int DEFAULT_HIDE_DELAY_SEC = 2;

    private static final int TURN_OFF = 0;
    private static final int TURN_LEFT = 1;
    private static final int TURN_RIGHT = 2;

    public interface SnapshotSource {
        VehicleDataProvider.Snapshot getVehicleSnapshot();
    }

    private final Context context;
    private final FrameLayout overlay;
    private final RearAiMjpegView videoView;
    private final TextView statusText;
    private final SnapshotSource snapshotSource;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private HandlerThread workerThread;
    private Handler workerHandler;
    private boolean started = false;
    private int lastTurnState = TURN_OFF;
    private boolean statusRequestInFlight = false;
    private long lastStatusRequestAt = 0L;
    private long lastPingAt = 0L;
    private String lastIp = "";
    private volatile Status latestStatus = Status.safe();
    private volatile long lastStatusAt = 0L;
    private MediaPlayer riskPlayer;
    private String playingAudioUri = "";

    private final Runnable hideRunnable = new Runnable() {
        @Override
        public void run() {
            hideOverlayNow();
        }
    };

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                tick();
            } catch (Throwable ignored) {
            }
            if (started) {
                mainHandler.postDelayed(this, 160L);
            }
        }
    };

    public RearAiVisionController(Context context, FrameLayout overlay, RearAiMjpegView videoView, TextView statusText, SnapshotSource snapshotSource) {
        this.context = context.getApplicationContext();
        this.overlay = overlay;
        this.videoView = videoView;
        this.statusText = statusText;
        this.snapshotSource = snapshotSource;
    }

    public void start() {
        if (started) return;
        started = true;
        if (workerThread == null) {
            workerThread = new HandlerThread("Miku-RearAI-Worker");
            workerThread.start();
            workerHandler = new Handler(workerThread.getLooper());
        }
        mainHandler.removeCallbacks(tickRunnable);
        mainHandler.post(tickRunnable);
    }

    public void stop() {
        started = false;
        mainHandler.removeCallbacks(tickRunnable);
        mainHandler.removeCallbacks(hideRunnable);
        if (lastTurnState != TURN_OFF) {
            sendUdpCommand("TURN_OFF");
        }
        lastTurnState = TURN_OFF;
        stopRiskAudio();
        hideOverlayNow();
        if (workerThread != null) {
            workerThread.quitSafely();
            workerThread = null;
            workerHandler = null;
        }
    }

    public static String streamUrl(String ip) {
        return "http://" + cleanIp(ip) + ":" + HTTP_PORT + "/stream";
    }

    public static String statusUrl(String ip) {
        return "http://" + cleanIp(ip) + ":" + HTTP_PORT + "/status";
    }

    public static String cleanIp(String ip) {
        return ip == null ? "" : ip.trim();
    }

    public static String settingsSummary(Context context) {
        SharedPreferences sp = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        boolean enabled = sp.getBoolean(PREF_ENABLED, true);
        String ip = sp.getString(PREF_IP, "");
        int delay = sp.getInt(PREF_HIDE_DELAY_SEC, DEFAULT_HIDE_DELAY_SEC);
        String leftName = sp.getString(PREF_LEFT_AUDIO_NAME, "");
        String rightName = sp.getString(PREF_RIGHT_AUDIO_NAME, "");
        return (enabled ? "已启用" : "未启用")
                + "，IP " + (ip == null || ip.trim().length() == 0 ? "未设置" : ip.trim())
                + "，隐藏延迟 " + delay + " 秒"
                + "\n左风险音：" + (leftName == null || leftName.length() == 0 ? "未选择" : leftName)
                + "；右风险音：" + (rightName == null || rightName.length() == 0 ? "未选择" : rightName);
    }

    public void sendPing() {
        sendUdpCommand("PING");
    }

    private void tick() {
        SharedPreferences sp = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        boolean enabled = sp.getBoolean(PREF_ENABLED, true);
        String ip = cleanIp(sp.getString(PREF_IP, ""));
        if (!enabled || ip.length() == 0) {
            if (lastTurnState != TURN_OFF) {
                sendUdpCommand("TURN_OFF");
            }
            lastTurnState = TURN_OFF;
            stopRiskAudio();
            hideOverlayNow();
            return;
        }

        int turnState = readTurnState();
        if (!ip.equals(lastIp)) {
            lastIp = ip;
            lastTurnState = TURN_OFF;
            latestStatus = Status.safe();
            lastStatusAt = 0L;
        }
        if (turnState != lastTurnState) {
            onTurnChanged(turnState, ip);
        }
        lastTurnState = turnState;

        if (turnState == TURN_OFF) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastPingAt > 5000L) {
                lastPingAt = now;
                sendUdpCommand("PING");
            }
            return;
        }

        showOverlay(ip, turnState);
        maybeRequestStatus(ip);
        Status status = effectiveStatus();
        boolean risk = isRiskForTurn(turnState, status);
        updateStatusText(turnState, status, risk);
        if (risk) {
            String audio = sp.getString(turnState == TURN_LEFT ? PREF_LEFT_AUDIO_URI : PREF_RIGHT_AUDIO_URI, "");
            playRiskAudio(audio);
        } else {
            stopRiskAudio();
        }
    }

    private int readTurnState() {
        VehicleDataProvider.Snapshot snapshot = snapshotSource == null ? null : snapshotSource.getVehicleSnapshot();
        if (snapshot == null || !snapshot.valid) return TURN_OFF;
        boolean left = snapshot.leftTurnOn;
        boolean right = snapshot.rightTurnOn;
        if (left && !right) return TURN_LEFT;
        if (right && !left) return TURN_RIGHT;
        return TURN_OFF;
    }

    private void onTurnChanged(int turnState, String ip) {
        mainHandler.removeCallbacks(hideRunnable);
        if (turnState == TURN_LEFT) {
            sendUdpCommand("TURN_LEFT");
            latestStatus = Status.safe();
            lastStatusAt = 0L;
            showOverlay(ip, turnState);
            return;
        }
        if (turnState == TURN_RIGHT) {
            sendUdpCommand("TURN_RIGHT");
            latestStatus = Status.safe();
            lastStatusAt = 0L;
            showOverlay(ip, turnState);
            return;
        }

        sendUdpCommand("TURN_OFF");
        stopRiskAudio();
        int delaySec = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getInt(PREF_HIDE_DELAY_SEC, DEFAULT_HIDE_DELAY_SEC);
        if (delaySec < 0) delaySec = 0;
        if (delaySec > 30) delaySec = 30;
        mainHandler.postDelayed(hideRunnable, delaySec * 1000L);
        updateStatusLine("后置 AI：转向关闭，" + delaySec + " 秒后隐藏视频");
    }

    private void showOverlay(String ip, int turnState) {
        if (overlay == null || videoView == null) return;
        if (overlay.getVisibility() != View.VISIBLE) {
            overlay.setVisibility(View.VISIBLE);
            overlay.bringToFront();
        }
        videoView.startStream(streamUrl(ip));
        updateStatusText(turnState, effectiveStatus(), false);
    }

    private void hideOverlayNow() {
        if (videoView != null) {
            videoView.stopStream();
        }
        if (overlay != null) {
            overlay.setVisibility(View.GONE);
        }
        updateStatusLine("");
    }

    private void maybeRequestStatus(final String ip) {
        long now = SystemClock.elapsedRealtime();
        if (statusRequestInFlight || now - lastStatusRequestAt < 230L) {
            return;
        }
        lastStatusRequestAt = now;
        statusRequestInFlight = true;
        Handler wh = workerHandler;
        if (wh == null) {
            statusRequestInFlight = false;
            return;
        }
        wh.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Status s = fetchStatus(ip);
                    if (s != null) {
                        latestStatus = s;
                        lastStatusAt = SystemClock.elapsedRealtime();
                    }
                } catch (Throwable ignored) {
                } finally {
                    statusRequestInFlight = false;
                }
            }
        });
    }

    private Status effectiveStatus() {
        long at = lastStatusAt;
        if (at <= 0L || SystemClock.elapsedRealtime() - at > 500L) {
            return Status.safe();
        }
        return latestStatus == null ? Status.safe() : latestStatus;
    }

    private boolean isRiskForTurn(int turnState, Status status) {
        if (status == null) status = Status.safe();
        if (turnState == TURN_LEFT) {
            return status.left || status.status == 1 || status.status == 0;
        }
        if (turnState == TURN_RIGHT) {
            return status.right || status.status == 2 || status.status == 0;
        }
        return false;
    }

    private Status fetchStatus(String ip) throws Exception {
        HttpURLConnection conn = null;
        InputStream input = null;
        try {
            URL url = new URL(statusUrl(ip));
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(300);
            conn.setReadTimeout(300);
            conn.setUseCaches(false);
            input = conn.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
            byte[] buf = new byte[1024];
            int n;
            while ((n = input.read(buf)) > 0 && out.size() < 16 * 1024) {
                out.write(buf, 0, n);
            }
            String json = out.toString("UTF-8");
            JSONObject o = new JSONObject(json);
            Status s = new Status();
            s.status = o.optInt("status", 3);
            s.left = o.optBoolean("left", false);
            s.right = o.optBoolean("right", false);
            s.turn = o.optString("turn", "");
            s.leftLine = o.optDouble("leftLine", -1d);
            s.rightLine = o.optDouble("rightLine", -1d);
            s.ts = o.optLong("ts", 0L);
            return s;
        } finally {
            try {
                if (input != null) input.close();
            } catch (Throwable ignored) {
            }
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void sendUdpCommand(final String cmd) {
        final String ip = cleanIp(context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).getString(PREF_IP, ""));
        if (ip.length() == 0 || cmd == null || cmd.length() == 0) return;
        Handler wh = workerHandler;
        if (wh == null) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    sendUdpNow(ip, cmd);
                }
            }, "Miku-RearAI-UDP-Once");
            t.setDaemon(true);
            t.start();
        } else {
            wh.post(new Runnable() {
                @Override
                public void run() {
                    sendUdpNow(ip, cmd);
                }
            });
        }
    }

    private void sendUdpNow(String ip, String cmd) {
        DatagramSocket socket = null;
        try {
            byte[] data = cmd.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(ip), UDP_PORT);
            socket = new DatagramSocket();
            socket.setSoTimeout(300);
            socket.send(packet);
        } catch (Throwable ignored) {
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void playRiskAudio(String uriText) {
        if (uriText == null || uriText.length() == 0) {
            stopRiskAudio();
            return;
        }
        if (uriText.equals(playingAudioUri) && riskPlayer != null) {
            try {
                if (!riskPlayer.isPlaying()) riskPlayer.start();
            } catch (Throwable ignored) {
            }
            return;
        }
        stopRiskAudio();
        try {
            Uri uri = Uri.parse(uriText);
            riskPlayer = MediaPlayer.create(context, uri);
            if (riskPlayer != null) {
                riskPlayer.setLooping(true);
                riskPlayer.start();
                playingAudioUri = uriText;
            }
        } catch (Throwable ignored) {
            stopRiskAudio();
        }
    }

    private void stopRiskAudio() {
        playingAudioUri = "";
        if (riskPlayer != null) {
            try {
                riskPlayer.stop();
            } catch (Throwable ignored) {
            }
            try {
                riskPlayer.release();
            } catch (Throwable ignored) {
            }
            riskPlayer = null;
        }
    }

    private void updateStatusText(int turnState, Status status, boolean risk) {
        String turn = turnState == TURN_LEFT ? "左转" : turnState == TURN_RIGHT ? "右转" : "关闭";
        String riskText = risk ? "检测到风险" : "安全 / 无车";
        String line = "后置 AI：" + turn + "，" + riskText + "，status=" + (status == null ? 3 : status.status);
        if (status != null && (status.leftLine >= 0d || status.rightLine >= 0d)) {
            line += "  L=" + format(status.leftLine) + " R=" + format(status.rightLine);
        }
        updateStatusLine(line);
    }

    private String format(double v) {
        if (v < 0d) return "--";
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    private void updateStatusLine(final String line) {
        if (statusText == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                statusText.setText(line == null ? "" : line);
            }
        });
    }

    private static class Status {
        int status = 3;
        boolean left = false;
        boolean right = false;
        String turn = "";
        double leftLine = -1d;
        double rightLine = -1d;
        long ts = 0L;

        static Status safe() {
            return new Status();
        }
    }
}
