package com.jlxc.mikucarlauncher;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.Locale;

/**
 * 轻量前台包名检测服务。
 *
 * 只监听窗口状态变化，不读取节点内容，不启用交互窗口扫描，不监听内容变化。
 * 目的：识别 com.baony.avm360 / AVMBVActivity 这类全景前台窗口，并关闭高德悬浮窗。
 */
public class MikuForegroundAccessibilityService extends AccessibilityService {
    private static final String TAG = "MikuA11y";
    public static final String ACTION_FOREGROUND_CHANGED = "com.jlxc.mikucarlauncher.ACCESSIBILITY_FOREGROUND_CHANGED";
    public static final String EXTRA_PACKAGE = "package";
    public static final String EXTRA_CLASS = "class";
    public static final String EXTRA_AVM = "avm";

    private static volatile boolean sConnected = false;
    private static volatile String sCurrentPackage = "";
    private static volatile String sCurrentClass = "";
    private static volatile long sLastEventAtMs = 0L;
    private static volatile long sAvmHoldUntilMs = 0L;
    private static volatile long sLastCloseMapAtMs = 0L;

    private static final long AVM_HOLD_MS = 12000L;
    private static final long CLOSEMAP_THROTTLE_MS = 800L;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sConnected = true;
        try {
            AccessibilityServiceInfo info = new AccessibilityServiceInfo();
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags = 0;
            info.notificationTimeout = 120L;
            // 只监听全景 App，避免对 MainApp / com.ts.MainUI 产生额外无障碍事件压力。
            info.packageNames = new String[]{"com.baony.avm360"};
            setServiceInfo(info);
        } catch (Throwable ignored) {
        }
        Log.i(TAG, "connected safe-mode");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkgCs = event.getPackageName();
        CharSequence clsCs = event.getClassName();
        String pkg = pkgCs == null ? "" : pkgCs.toString();
        String cls = clsCs == null ? "" : clsCs.toString();
        if (pkg.length() == 0 && cls.length() == 0) return;

        sCurrentPackage = pkg;
        sCurrentClass = cls;
        sLastEventAtMs = System.currentTimeMillis();

        boolean avm = isPanoramaLike(pkg, cls);
        if (avm) {
            sAvmHoldUntilMs = System.currentTimeMillis() + AVM_HOLD_MS;
            closeAmapFromService("a11y-avm " + pkg + "/" + cls);
        }
        sendForegroundBroadcast(pkg, cls, avm);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        sConnected = false;
        return super.onUnbind(intent);
    }

    private void sendForegroundBroadcast(String pkg, String cls, boolean avm) {
        try {
            Intent intent = new Intent(ACTION_FOREGROUND_CHANGED);
            intent.setPackage(getPackageName());
            intent.putExtra(EXTRA_PACKAGE, pkg == null ? "" : pkg);
            intent.putExtra(EXTRA_CLASS, cls == null ? "" : cls);
            intent.putExtra(EXTRA_AVM, avm);
            sendBroadcast(intent);
        } catch (Throwable ignored) {
        }
    }

    private void closeAmapFromService(String reason) {
        long now = System.currentTimeMillis();
        if (now - sLastCloseMapAtMs < CLOSEMAP_THROTTLE_MS) return;
        sLastCloseMapAtMs = now;
        try {
            AmapFloatingCardController.sendCloseMapBroadcast(this);
            Log.i(TAG, "closemap reason=" + reason);
        } catch (Throwable t) {
            Log.w(TAG, "closemap failed", t);
        }
    }

    public static boolean isServiceConnected() {
        return sConnected;
    }

    public static String getCurrentPackage() {
        return sCurrentPackage == null ? "" : sCurrentPackage;
    }

    public static String getCurrentClassName() {
        return sCurrentClass == null ? "" : sCurrentClass;
    }

    public static ComponentName getCurrentComponent() {
        String pkg = getCurrentPackage();
        String cls = getCurrentClassName();
        if (pkg.length() == 0) return null;
        if (cls.length() == 0) cls = pkg;
        return new ComponentName(pkg, cls);
    }

    public static boolean isPanoramaForegroundOrHold() {
        if (isPanoramaLike(getCurrentPackage(), getCurrentClassName())) return true;
        return System.currentTimeMillis() <= sAvmHoldUntilMs;
    }

    public static long getLastEventAgeMs() {
        long at = sLastEventAtMs;
        if (at <= 0L) return Long.MAX_VALUE;
        return Math.max(0L, System.currentTimeMillis() - at);
    }

    public static boolean isPanoramaLike(String pkg, String cls) {
        if (pkg == null) pkg = "";
        if (cls == null) cls = "";
        String p = pkg.toLowerCase(Locale.US);
        String c = cls.toLowerCase(Locale.US);
        if ("com.baony.avm360".equals(pkg)) return true;
        if (c.contains("avmbvactivity")) return true;
        if (p.contains("avm") || p.contains("panorama") || p.contains("birdview")) return true;
        if (c.contains("avm") || c.contains("panorama") || c.contains("birdview")) return true;
        return false;
    }

    public static boolean isAccessibilityEnabled(Context context) {
        if (context == null) return false;
        try {
            String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null || enabled.length() == 0) return false;
            String target = context.getPackageName() + "/" + MikuForegroundAccessibilityService.class.getName();
            String targetShort = context.getPackageName() + "/.MikuForegroundAccessibilityService";
            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
            splitter.setString(enabled);
            while (splitter.hasNext()) {
                String item = splitter.next();
                if (target.equalsIgnoreCase(item) || targetShort.equalsIgnoreCase(item)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static void openAccessibilitySettings(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable ignored) {
            try {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Throwable ignored2) {
            }
        }
    }
}
