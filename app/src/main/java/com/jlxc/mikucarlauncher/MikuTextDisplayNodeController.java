package com.jlxc.mikucarlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MikuTextDisplayNode / 快捷文字屏节点。
 *
 * 接收端：com.jlxc.mikutextdisplay
 * UDP 47230：SHOW:文字 / CLEAR / PING，UTF-8。
 * HTTP 47231：/show?text=URL编码文字 / /clear，作为 UDP 失败时的备用链路。
 */
public final class MikuTextDisplayNodeController {
    public static final String PREF_ENABLED = "miku_text_node_enabled";
    public static final String PREF_IP = "miku_text_node_ip";
    public static final String PREF_DEBOUNCE_MS = "miku_text_node_debounce_ms";

    public static final int UDP_PORT = 47230;
    public static final int HTTP_PORT = 47231;
    public static final int DEFAULT_DEBOUNCE_MS = 500;

    public static final String COMMON_TYPE_TEXT = "text_display";
    public static final String COMMON_TYPE_SHOUT = "text_shout";
    public static final String COMMON_PKG_MARKER = "__miku_text_display_node__";

    private static final String TAG = "MikuTextNode";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private static final Object LOCK = new Object();
    private static String lastShowText = "";
    private static long lastShowAtMs = 0L;

    private MikuTextDisplayNodeController() {
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences sp = prefs(context);
        return sp.getBoolean(PREF_ENABLED, false);
    }

    public static String getIp(Context context) {
        String ip = prefs(context).getString(PREF_IP, "");
        return ip == null ? "" : ip.trim();
    }

    public static int getDebounceMs(Context context) {
        return clamp(prefs(context).getInt(PREF_DEBOUNCE_MS, DEFAULT_DEBOUNCE_MS), 300, 1000);
    }

    public static String settingsSummary(Context context) {
        String ip = getIp(context);
        return (isEnabled(context) ? "已启用" : "未启用")
                + "，IP " + (ip.length() == 0 ? "未设置" : ip)
                + "，UDP " + UDP_PORT
                + " / HTTP " + HTTP_PORT
                + "，去抖 " + getDebounceMs(context) + "ms";
    }

    public static void sendShow(Context context, String text) {
        if (text == null) {
            text = "";
        }
        final String finalText = text.trim();
        if (finalText.length() == 0) {
            return;
        }
        if (!shouldSendShow(context, finalText)) {
            return;
        }
        sendPacket(context, "SHOW:" + finalText, "/show?text=" + urlEncode(finalText), true);
    }

    public static void sendClear(Context context) {
        sendPacket(context, "CLEAR", "/clear", true);
    }

    public static void sendPing(Context context) {
        sendPacket(context, "PING", null, false);
    }

    public static void sendRawText(Context context, String text) {
        if (text == null) {
            text = "";
        }
        final String finalText = text.trim();
        if (finalText.length() == 0) {
            return;
        }
        if (!shouldSendShow(context, finalText)) {
            return;
        }
        sendPacket(context, finalText, "/show?text=" + urlEncode(finalText), true);
    }

    private static boolean shouldSendShow(Context context, String text) {
        long now = System.currentTimeMillis();
        int debounce = getDebounceMs(context);
        synchronized (LOCK) {
            if (text.equals(lastShowText) && now - lastShowAtMs < debounce) {
                return false;
            }
            lastShowText = text;
            lastShowAtMs = now;
            return true;
        }
    }

    private static void sendPacket(Context context, String udpMessage, String httpPath, boolean useHttpFallback) {
        if (context == null) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        if (!isEnabled(appContext)) {
            return;
        }
        final String ip = getIp(appContext);
        if (ip.length() == 0) {
            return;
        }
        final String message = udpMessage == null ? "" : udpMessage;
        final String fallback = httpPath;
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                boolean ok = sendUdp(ip, message);
                if (!ok && useHttpFallback && fallback != null && fallback.length() > 0) {
                    sendHttp(ip, fallback);
                }
            }
        });
    }

    private static boolean sendUdp(String ip, String message) {
        DatagramSocket socket = null;
        try {
            byte[] data = message.getBytes(UTF8);
            InetAddress address = InetAddress.getByName(ip);
            DatagramPacket packet = new DatagramPacket(data, data.length, address, UDP_PORT);
            socket = new DatagramSocket();
            socket.setSoTimeout(600);
            socket.send(packet);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "UDP send failed: " + t.getMessage());
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static boolean sendHttp(String ip, String path) {
        HttpURLConnection conn = null;
        InputStream input = null;
        try {
            URL url = new URL("http://" + ip + ":" + HTTP_PORT + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(800);
            conn.setReadTimeout(800);
            conn.setUseCaches(false);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            input = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (input != null) {
                byte[] buffer = new byte[128];
                while (input.read(buffer) >= 0) {
                    // drain
                }
            }
            return code >= 200 && code < 400;
        } catch (Throwable t) {
            Log.w(TAG, "HTTP fallback failed: " + t.getMessage());
            return false;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable ignored) {
                }
            }
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String urlEncode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8");
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static boolean isTextDisplayCommonType(String type) {
        return COMMON_TYPE_TEXT.equals(type) || COMMON_TYPE_SHOUT.equals(type);
    }

    public static boolean isTextDisplayMarker(String pkg) {
        return TextUtils.equals(COMMON_PKG_MARKER, pkg);
    }
}
