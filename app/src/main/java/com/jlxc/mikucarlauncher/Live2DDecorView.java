package com.jlxc.mikucarlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.net.URLEncoder;

public class Live2DDecorView extends FrameLayout {
    public static final String PREF_ENABLED = "live2d_enabled";
    public static final String PREF_MODEL_PATH = "live2d_model_path";

    // v48/v49 旧位置参数保留，避免设置页/旧配置报错。
    public static final String PREF_X = "live2d_x";
    public static final String PREF_Y = "live2d_y";
    public static final String PREF_W = "live2d_w";
    public static final String PREF_H = "live2d_h";

    // v51 起真正调节的是“模型中心点 + 模型缩放”，不再调 WebView 框大小。
    public static final String PREF_CENTER_X = "live2d_center_x";
    public static final String PREF_CENTER_Y = "live2d_center_y";
    public static final String PREF_SCALE = "live2d_scale";
    public static final String PREF_RENDER_QUALITY = "live2d_render_quality";
    public static final String PREF_TARGET_FPS = "live2d_target_fps";

    public static final float DEFAULT_X = 1188f;
    public static final float DEFAULT_Y = 246f;
    public static final float DEFAULT_W = 520f;
    public static final float DEFAULT_H = 300f;
    public static final float DEFAULT_CENTER_X = DEFAULT_X + DEFAULT_W / 2f;
    public static final float DEFAULT_CENTER_Y = DEFAULT_Y + DEFAULT_H * 0.58f;
    public static final float DEFAULT_SCALE = 1.0f;
    public static final float DEFAULT_RENDER_QUALITY = 1.0f;
    public static final int DEFAULT_TARGET_FPS = 60;

    private static final float DESIGN_W = 2560f;
    private static final float DESIGN_H = 720f;
    private static final float MIN_SCALE = 0.25f;
    private static final float MAX_SCALE = 3.5f;

    private WebView webView;
    private String lastUrl = "";
    private int reloadToken = 0;
    private boolean adjustMode = false;

    // v67: Live2D 自愈 watchdog。车机长时间运行后 WebView/Surface 可能空白但 Java 层仍存在。
    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());
    private long lastHeartbeatMs = 0L;
    private long lastPageLoadMs = 0L;
    private int watchdogReloadCount = 0;
    private boolean watchdogRunning = false;

    private float downRawX;
    private float downRawY;
    private float startCenterX;
    private float startCenterY;
    private float startScale;
    private float startDist;
    // 0=idle, 1=single finger drag, 2=pinch zoom, 3=pinch ended but still one finger remains; ignore move until all fingers lift.
    private int pointerMode = 0;

    public Live2DDecorView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        setClipChildren(false);
        setClipToPadding(false);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        webView = new WebView(context);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setFocusable(false);
        webView.setClickable(false);
        webView.setLongClickable(false);
        webView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setVerticalScrollBarEnabled(false);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                lastPageLoadMs = System.currentTimeMillis();
                lastHeartbeatMs = lastPageLoadMs;
                watchdogReloadCount = 0;

                // 页面完成后延迟再同步一次位置，修复偶发错位。
                watchdogHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        resendCurrentTransform();
                    }
                }, 350L);
                watchdogHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        resendCurrentTransform();
                    }
                }, 1500L);
            }
        });
        webView.addJavascriptInterface(new Live2DJsBridge(), "MikuLive2DAndroid");
        webView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return adjustMode && Live2DDecorView.this.handleAdjustTouch(event);
            }
        });

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        try {
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
        } catch (Throwable ignored) {
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            }
        } catch (Throwable ignored) {
        }

        addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        setVisibility(View.GONE);
    }

    public void setAdjustMode(boolean enable) {
        adjustMode = enable;
        setClickable(enable);
        setFocusable(enable);
        // 只在调整页允许触摸；首页装饰层不吃触摸。
        setBackgroundColor(Color.TRANSPARENT);
        webView.setClickable(false);
        webView.setLongClickable(false);
    }

    public boolean isLive2DEnabled() {
        SharedPreferences sp = getContext().getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        String model = sp.getString(PREF_MODEL_PATH, "");
        return sp.getBoolean(PREF_ENABLED, false) && model != null && model.trim().length() > 0;
    }

    public void applySettings() {
        if (!isLive2DEnabled()) {
            setVisibility(View.GONE);
            stopWatchdog();
            return;
        }

        SharedPreferences sp = getContext().getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        migrateOldFramePrefsIfNeeded(sp);

        String model = normalizeModelPath(sp.getString(PREF_MODEL_PATH, ""));
        float centerX = sp.getFloat(PREF_CENTER_X, DEFAULT_CENTER_X);
        float centerY = sp.getFloat(PREF_CENTER_Y, DEFAULT_CENTER_Y);
        float scale = clampFloat(sp.getFloat(PREF_SCALE, DEFAULT_SCALE), MIN_SCALE, MAX_SCALE);
        float renderQuality = clampFloat(sp.getFloat(PREF_RENDER_QUALITY, DEFAULT_RENDER_QUALITY), 0.5f, 2.0f);
        int targetFps = clampInt(sp.getInt(PREF_TARGET_FPS, DEFAULT_TARGET_FPS), 15, 60);
        boolean night = NightModeHelper.isNightMode(getContext());
        int live2DNightDimAlpha = NightModeHelper.live2DNightDimAlpha(getContext());

        if (TextUtils.isEmpty(model)) {
            setVisibility(View.GONE);
            return;
        }

        String url = buildViewerUrl(model, centerX, centerY, scale, renderQuality, targetFps, night, live2DNightDimAlpha);
        if (!url.equals(lastUrl)) {
            lastUrl = url;
            lastPageLoadMs = System.currentTimeMillis();
            lastHeartbeatMs = lastPageLoadMs;
            webView.loadUrl(url);
        } else {
            sendTransformToJs(centerX, centerY, scale);
        }
        startWatchdog();
    }

    public void sendTransformToJs(float centerX, float centerY, float scale) {
        try {
            String js = "window.__mikuLive2DUpdate && window.__mikuLive2DUpdate("
                    + centerX + "," + centerY + "," + scale + ");";
            webView.evaluateJavascript(js, null);
        } catch (Throwable ignored) {
        }
    }

    public void playNextMotion() {
        try {
            webView.evaluateJavascript("window.__mikuLive2DNextMotion && window.__mikuLive2DNextMotion();", null);
        } catch (Throwable ignored) {
        }
    }

    public void reloadLive2D() {
        try {
            reloadToken++;
            lastUrl = "";
            lastPageLoadMs = System.currentTimeMillis();
            lastHeartbeatMs = lastPageLoadMs;
            webView.stopLoading();
            webView.clearHistory();
            // 不清空所有缓存，避免低速存储反而更慢；只让当前页面重新载入。
        } catch (Throwable ignored) {
        }
        applySettings();
        startWatchdog();
    }

    public void hardReloadLive2D() {
        try {
            reloadToken++;
            lastUrl = "";
            lastPageLoadMs = System.currentTimeMillis();
            lastHeartbeatMs = lastPageLoadMs;
            webView.stopLoading();
            webView.loadUrl("about:blank");
        } catch (Throwable ignored) {
        }
        watchdogHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                applySettings();
            }
        }, 250L);
        startWatchdog();
    }

    public boolean isLive2DHealthy() {
        if (!isLive2DEnabled() || getVisibility() != View.VISIBLE) {
            return true;
        }
        long now = System.currentTimeMillis();
        // 刚加载阶段给 WebView 足够时间，避免误判。
        if (lastPageLoadMs > 0L && now - lastPageLoadMs < 12000L) {
            return true;
        }
        return lastHeartbeatMs > 0L && now - lastHeartbeatMs < 9000L;
    }

    public void pauseLive2D() {
        try {
            webView.onPause();
            webView.pauseTimers();
        } catch (Throwable ignored) {
        }
        stopWatchdog();
    }

    public void resumeLive2D() {
        try {
            webView.resumeTimers();
            webView.onResume();
        } catch (Throwable ignored) {
        }
        startWatchdog();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startWatchdog();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopWatchdog();
        super.onDetachedFromWindow();
    }

    private void startWatchdog() {
        if (watchdogRunning) {
            return;
        }
        watchdogRunning = true;
        watchdogHandler.postDelayed(watchdogRunnable, 5000L);
    }

    private void stopWatchdog() {
        watchdogRunning = false;
        watchdogHandler.removeCallbacks(watchdogRunnable);
    }

    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (!watchdogRunning) {
                return;
            }

            if (isLive2DEnabled() && getVisibility() == View.VISIBLE) {
                long now = System.currentTimeMillis();

                // 定期校正位置，解决偶发显示错位。
                resendCurrentTransform();

                if (lastPageLoadMs > 0L && now - lastPageLoadMs > 12000L
                        && (lastHeartbeatMs <= 0L || now - lastHeartbeatMs > 10000L)) {
                    watchdogReloadCount++;
                    if (watchdogReloadCount >= 2) {
                        hardReloadLive2D();
                        watchdogReloadCount = 0;
                    } else {
                        reloadLive2D();
                    }
                }
            }

            watchdogHandler.postDelayed(this, 5000L);
        }
    };

    private void resendCurrentTransform() {
        try {
            SharedPreferences sp = getContext().getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
            migrateOldFramePrefsIfNeeded(sp);
            float centerX = sp.getFloat(PREF_CENTER_X, DEFAULT_CENTER_X);
            float centerY = sp.getFloat(PREF_CENTER_Y, DEFAULT_CENTER_Y);
            float scale = clampFloat(sp.getFloat(PREF_SCALE, DEFAULT_SCALE), MIN_SCALE, MAX_SCALE);
            sendTransformToJs(centerX, centerY, scale);
        } catch (Throwable ignored) {
        }
    }

    private final class Live2DJsBridge {
        @JavascriptInterface
        public void heartbeat(String type) {
            lastHeartbeatMs = System.currentTimeMillis();
            watchdogReloadCount = 0;
        }
    }


    private void migrateOldFramePrefsIfNeeded(SharedPreferences sp) {
        if (sp.contains(PREF_CENTER_X) && sp.contains(PREF_CENTER_Y)) {
            return;
        }

        float x = sp.getFloat(PREF_X, DEFAULT_X);
        float y = sp.getFloat(PREF_Y, DEFAULT_Y);
        float w = sp.getFloat(PREF_W, DEFAULT_W);
        float h = sp.getFloat(PREF_H, DEFAULT_H);
        sp.edit()
                .putFloat(PREF_CENTER_X, x + w / 2f)
                .putFloat(PREF_CENTER_Y, y + h * 0.58f)
                .apply();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!adjustMode) {
            return false;
        }
        return handleAdjustTouch(event);
    }

    private boolean handleAdjustTouch(MotionEvent event) {
        SharedPreferences sp = getContext().getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        migrateOldFramePrefsIfNeeded(sp);

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pointerMode = 1;
            downRawX = event.getRawX();
            downRawY = event.getRawY();
            startCenterX = sp.getFloat(PREF_CENTER_X, DEFAULT_CENTER_X);
            startCenterY = sp.getFloat(PREF_CENTER_Y, DEFAULT_CENTER_Y);
            startScale = clampFloat(sp.getFloat(PREF_SCALE, DEFAULT_SCALE), MIN_SCALE, MAX_SCALE);
            return true;
        }

        if (action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2) {
            pointerMode = 2;
            startDist = distance(event);
            startCenterX = sp.getFloat(PREF_CENTER_X, DEFAULT_CENTER_X);
            startCenterY = sp.getFloat(PREF_CENTER_Y, DEFAULT_CENTER_Y);
            startScale = clampFloat(sp.getFloat(PREF_SCALE, DEFAULT_SCALE), MIN_SCALE, MAX_SCALE);
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (pointerMode == 2 && event.getPointerCount() >= 2) {
                float currentDist = distance(event);
                if (startDist > 4f) {
                    float factor = currentDist / startDist;
                    float nextScale = clampFloat(startScale * factor, MIN_SCALE, MAX_SCALE);
                    sendTransformToJs(startCenterX, startCenterY, nextScale);
                    saveTransform(startCenterX, startCenterY, nextScale);
                }
            } else if (pointerMode == 1) {
                float sx = getWidth() / DESIGN_W;
                float sy = getHeight() / DESIGN_H;
                if (sx <= 0f) sx = 1f;
                if (sy <= 0f) sy = 1f;

                float nextX = clampFloat(startCenterX + (event.getRawX() - downRawX) / sx, 0f, DESIGN_W);
                float nextY = clampFloat(startCenterY + (event.getRawY() - downRawY) / sy, 0f, DESIGN_H);
                sendTransformToJs(nextX, nextY, startScale);
                saveTransform(nextX, nextY, startScale);
            } else {
                // pointerMode == 3：双指缩放后只松开了其中一根手指。
                // 这里必须忽略剩余手指的移动，否则人物会被慢松手的那根手指拖走。
            }
            return true;
        }

        if (action == MotionEvent.ACTION_POINTER_UP) {
            if (pointerMode == 2) {
                pointerMode = 3;
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            pointerMode = 0;
            return true;
        }

        return true;
    }

    private void saveTransform(float centerX, float centerY, float scale) {
        getContext().getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit()
                .putFloat(PREF_CENTER_X, centerX)
                .putFloat(PREF_CENTER_Y, centerY)
                .putFloat(PREF_SCALE, clampFloat(scale, MIN_SCALE, MAX_SCALE))
                .apply();
    }

    private float distance(MotionEvent event) {
        if (event.getPointerCount() < 2) {
            return 0f;
        }
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private String buildViewerUrl(String model, float centerX, float centerY, float scale, float renderQuality, int targetFps, boolean night, int live2DNightDimAlpha) {
        try {
            return "file:///android_asset/live2d/live2d_decor.html"
                    + "?model=" + URLEncoder.encode(model, "UTF-8")
                    + "&cx=" + URLEncoder.encode(String.valueOf(centerX), "UTF-8")
                    + "&cy=" + URLEncoder.encode(String.valueOf(centerY), "UTF-8")
                    + "&scale=" + URLEncoder.encode(String.valueOf(scale), "UTF-8")
                    + "&dw=2560&dh=720"
                    + "&clip=" + (adjustMode ? "0" : "1")
                    + "&quality=" + URLEncoder.encode(String.valueOf(renderQuality), "UTF-8")
                    + "&fps=" + URLEncoder.encode(String.valueOf(targetFps), "UTF-8")
                    + "&night=" + (night ? "1" : "0")
                    + "&dim=" + URLEncoder.encode(String.valueOf(live2DNightDimAlpha), "UTF-8")
                    + "&reload=" + reloadToken;
        } catch (Throwable t) {
            return "file:///android_asset/live2d/live2d_decor.html";
        }
    }

    private int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private String normalizeModelPath(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.length() == 0) {
            return "";
        }

        if (text.startsWith("http://")
                || text.startsWith("https://")
                || text.startsWith("file://")
                || text.startsWith("content://")) {
            return text;
        }

        if (text.startsWith("/")) {
            return Uri.fromFile(new java.io.File(text)).toString();
        }

        return "file:///sdcard/" + text;
    }

    private float clampFloat(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
