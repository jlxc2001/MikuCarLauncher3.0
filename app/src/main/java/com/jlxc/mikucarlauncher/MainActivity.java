package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.provider.Settings;
import android.widget.TextView;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
    public static final String PREFS = "miku_car_launcher_settings";
    private static boolean sAmapColdStartWarmupDone = false;
    public static final String PREF_CARD1_WIDGET_ID = "card1_widget_id";
    public static final int APPWIDGET_HOST_ID = 1001;

    private static final float DESIGN_W = 2560f;
    private static final float DESIGN_H = 720f;

    // 1 号卡片坐标：和已确认 UI 保持一致。
    private static final float CARD1_L = 210f;
    private static final float CARD1_T = 35.5f;
    private static final float CARD1_R = 1140f;
    private static final float CARD1_B = 528.5f;
    private static final float CARD1_WIDGET_INSET = 12f;

    private LauncherBackgroundView backgroundView;
    private Live2DDecorView live2DView;
    private LauncherCanvasView launcherView;
    private FrameLayout rootLayout;
    private AppWidgetManager appWidgetManager;
    private RoundedAppWidgetHost appWidgetHost;
    private AppWidgetHostView card1WidgetView;
    private int currentCard1WidgetId = -1;
    private FrameLayout mapCardContainer;
    private TextView mapCardHintView;
    private AmapFloatingCardController amapFloatingCardController;
    private FrameLayout rearAiOverlay;
    private RearAiMjpegView rearAiMjpegView;
    private TextView rearAiStatusView;
    private RearAiVisionController rearAiVisionController;
    private long lastLive2DReloadAt = 0L;
    private boolean pendingLive2DReload = false;
    private boolean isActivityResumed = false;
    private boolean hasWindowFocusNow = false;
    private long keepAmapOnHomeKeyUntilMs = 0L;
    private long amapColdStartWarmupUntilMs = 0L;
    private long explicitExternalLaunchUntilMs = 0L;
    private boolean pendingLive2DHealthCheck = false;
    private BroadcastReceiver homeKeyReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        keepFullscreen();

        appWidgetManager = AppWidgetManager.getInstance(this);
        appWidgetHost = new RoundedAppWidgetHost(this, APPWIDGET_HOST_ID);

        rootLayout = new FrameLayout(this);

        // 层级顺序：
        // 1. 背景层
        // 2. Live2D 装饰层
        // 3. 桌面 UI / 功能卡片层
        // 4. 1号卡片 AppWidget 层（需要时 bringToFront）
        backgroundView = new LauncherBackgroundView(this);
        rootLayout.addView(backgroundView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        live2DView = new Live2DDecorView(this);
        rootLayout.addView(live2DView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        launcherView = new LauncherCanvasView(this);
        launcherView.setDrawBackgroundLayer(false);
        launcherView.setOnMenuClickListener(new LauncherCanvasView.OnMenuClickListener() {
            @Override
            public void onMenuClick(int index, String label) {
                handleMenuClick(index);
                updateLive2DVisibility();
                updateAmapFloatingCardVisibility();
                positionRearAiOverlay();
            }
        });
        launcherView.setOnLive2DClickListener(new LauncherCanvasView.OnLive2DClickListener() {
            @Override
            public void onLive2DClick() {
                if (live2DView != null && live2DView.getVisibility() == View.VISIBLE) {
                    live2DView.playNextMotion();
                }
            }
        });

        rootLayout.addView(launcherView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        mapCardContainer = new FrameLayout(this);
        mapCardContainer.setBackgroundColor(Color.TRANSPARENT);
        mapCardContainer.setClickable(false);
        mapCardContainer.setFocusable(false);

        mapCardHintView = new TextView(this);
        mapCardHintView.setTextSize(18f);
        mapCardHintView.setTextColor(Color.rgb(120, 120, 120));
        mapCardHintView.setGravity(android.view.Gravity.CENTER);
        mapCardHintView.setText("");
        mapCardContainer.addView(mapCardHintView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        rootLayout.addView(mapCardContainer);
        amapFloatingCardController = new AmapFloatingCardController(this, mapCardContainer, AmapFloatingCardController.DEFAULT_INSET_DP);

        rearAiOverlay = new FrameLayout(this);
        rearAiOverlay.setBackgroundColor(Color.BLACK);
        rearAiOverlay.setVisibility(View.GONE);
        rearAiOverlay.setClickable(false);
        rearAiOverlay.setFocusable(false);

        rearAiMjpegView = new RearAiMjpegView(this);
        rearAiOverlay.addView(rearAiMjpegView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        rearAiStatusView = new TextView(this);
        rearAiStatusView.setTextSize(20f);
        rearAiStatusView.setTextColor(Color.WHITE);
        rearAiStatusView.setBackgroundColor(0x99000000);
        rearAiStatusView.setGravity(android.view.Gravity.CENTER_VERTICAL);
        rearAiStatusView.setPadding(22, 0, 22, 0);
        FrameLayout.LayoutParams rearStatusLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                56
        );
        rearStatusLp.gravity = android.view.Gravity.BOTTOM;
        rearAiOverlay.addView(rearAiStatusView, rearStatusLp);

        rootLayout.addView(rearAiOverlay);
        rearAiVisionController = new RearAiVisionController(this, rearAiOverlay, rearAiMjpegView, rearAiStatusView, new RearAiVisionController.SnapshotSource() {
            @Override
            public VehicleDataProvider.Snapshot getVehicleSnapshot() {
                return launcherView == null ? VehicleDataProvider.Snapshot.empty() : launcherView.getVehicleSnapshot();
            }
        });

        setContentView(rootLayout);
        registerHomeKeyReceiver();
        maybeStartAmapColdStartWarmup();

        rootLayout.post(new Runnable() {
            @Override
            public void run() {
                updateLive2DVisibility();
                updateAmapFloatingCardVisibility();
            }
        });
    }

    private void handleMenuClick(int index) {
        switch (index) {
            case 0: // 首页
                // 在首页再次点击“首页”：作为手动修复，重载高德悬浮窗和 Live2D。
                if (isHomePage()) {
                    reloadHomeSurfaceOnHomeKey();
                } else {
                    showHomePage(false);
                    reloadHomeSurfaceOnHomeKey();
                }
                break;

            case 1: // 导航
                closeAmapFloatingImmediately();
                launchSelectedPackage("nav_package", "com.autonavi.amapauto", "导航软件未找到，请到 我的 → 车机桌面设置 里选择默认导航软件");
                break;

            case 2: // 音乐
                closeAmapFloatingImmediately();
                launchMusic();
                break;

            case 3: // 车辆
                closeAmapFloatingImmediately();
                launchComponent(
                        "com.ts.MainUI",
                        "com.ts.can.audi.xhd.CanAudiWithCDExdActivity",
                        "无法打开车辆界面"
                );
                break;

            case 4: // 全景
                closeAmapFloatingImmediately();
                launchComponent(
                        "com.baony.avm360",
                        "com.baony.ui.activity.AVMBVActivity",
                        "无法打开全景影像"
                );
                break;

            case 5: // 应用
                closeAmapFloatingImmediately();
                if (launcherView != null) {
                    launcherView.invalidate();
                }
                break;

            case 6: // 我的
                closeAmapFloatingImmediately();
                if (launcherView != null) {
                    launcherView.invalidate();
                }
                break;
        }
    }

    private void launchMusic() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String pkg = sp.getString("music_package", "");
        if (pkg != null && pkg.length() > 0 && launchPackage(pkg)) {
            return;
        }

        if (launchComponent(
                "com.ts.MainUI",
                "com.ts.bt.BtMusicActivity",
                null
        )) {
            return;
        }

        showToast("音乐软件未找到，请到 我的 → 车机桌面设置 里选择默认音乐软件");
    }

    private void launchSelectedPackage(String prefKey, String defaultPackage, String failMsg) {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String pkg = sp.getString(prefKey, defaultPackage);
        if (!launchPackage(pkg)) {
            showToast(failMsg);
        }
    }

    private void markExplicitExternalLaunch() {
        explicitExternalLaunchUntilMs = System.currentTimeMillis() + 5000L;
        closeAmapFloatingImmediately();
    }

    private void closeAmapFloatingImmediately() {
        try {
            if (mapCardContainer != null) {
                mapCardContainer.setVisibility(View.GONE);
            }
            if (amapFloatingCardController != null) {
                amapFloatingCardController.setHomeVisible(false);
            } else {
                AmapFloatingCardController.sendCloseMapBroadcast(this);
            }
        } catch (Throwable ignored) {
            try {
                AmapFloatingCardController.sendCloseMapBroadcast(this);
            } catch (Throwable ignored2) {
            }
        }
    }

    private void maybeStartAmapColdStartWarmup() {
        if (sAmapColdStartWarmupDone || rootLayout == null) {
            return;
        }
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!sp.getBoolean(AmapFloatingCardController.PREF_AMAP_COLD_START_FRONT_WARMUP_ENABLED, true)) {
            return;
        }
        if (!AmapFloatingCardController.isAmapFloatingInstalled(this)) {
            return;
        }
        Intent launchIntent;
        try {
            launchIntent = getPackageManager().getLaunchIntentForPackage(AmapFloatingCardController.AMAP_FLOATING_PACKAGE);
        } catch (Throwable t) {
            launchIntent = null;
        }
        if (launchIntent == null) {
            return;
        }

        int delayMs = sp.getInt(
                AmapFloatingCardController.PREF_AMAP_COLD_START_RETURN_DELAY_MS,
                AmapFloatingCardController.DEFAULT_COLD_START_RETURN_DELAY_MS);
        delayMs = Math.max(0, Math.min(30000, delayMs));

        sAmapColdStartWarmupDone = true;
        amapColdStartWarmupUntilMs = System.currentTimeMillis() + delayMs + 12000L;
        keepAmapOnHomeKeyUntilMs = Math.max(keepAmapOnHomeKeyUntilMs, amapColdStartWarmupUntilMs);

        try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchIntent);
        } catch (Throwable ignored) {
            return;
        }

        rootLayout.postDelayed(new Runnable() {
            @Override
            public void run() {
                bringLauncherBackAfterAmapWarmup();
            }
        }, delayMs);
    }

    private void bringLauncherBackAfterAmapWarmup() {
        bringLauncherToFrontForAmapWarmup();
        if (rootLayout != null) {
            rootLayout.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showHomePage(false);
                    updateLive2DVisibility();
                    updateAmapFloatingCardVisibility();
                    ensureAmapMainFloatingWindowLoaded(true);
                }
            }, 500L);

            // 部分车机上高德第一次加载资源完成后会再次抢到前台，延迟多拉回两次并补发 showmap。
            rootLayout.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (shouldKeepAmapDuringColdStartWarmup()) {
                        bringLauncherToFrontForAmapWarmup();
                        showHomePage(false);
                        ensureAmapMainFloatingWindowLoaded(false);
                    }
                }
            }, 2500L);
            rootLayout.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (shouldKeepAmapDuringColdStartWarmup()) {
                        bringLauncherToFrontForAmapWarmup();
                        showHomePage(false);
                        ensureAmapMainFloatingWindowLoaded(false);
                    }
                }
            }, 5600L);
        }
    }

    private void bringLauncherToFrontForAmapWarmup() {
        try {
            Intent back = new Intent(this, MainActivity.class);
            back.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(back);
        } catch (Throwable ignored) {
        }
    }

    private void reloadHomeSurfaceOnHomeKey() {
        markHomeKeyTransientForAmap();
        showHomePage(false);
        reloadLive2DOnHome();
        updateAmapFloatingCardVisibility();
        ensureAmapMainFloatingWindowLoaded(true);
    }

    private void ensureAmapMainFloatingWindowLoaded(final boolean closeFirst) {
        if (rootLayout == null || amapFloatingCardController == null || !isHomePage()) {
            return;
        }
        rootLayout.post(new Runnable() {
            @Override
            public void run() {
                updateAmapFloatingCardVisibility();
                if (amapFloatingCardController != null && shouldShowAmapFloatingCardOnHome()) {
                    amapFloatingCardController.ensureMapWindowVisibleAggressively(closeFirst);
                }
            }
        });
    }

    private boolean launchPackage(String pkg) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent == null) return false;
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            markExplicitExternalLaunch();
            startActivity(intent);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean launchComponent(String pkg, String cls, String failMsg) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(pkg, cls));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            markExplicitExternalLaunch();
            startActivity(intent);
            return true;
        } catch (Throwable t) {
            if (failMsg != null) {
                showToast(failMsg);
            }
            return false;
        }
    }

    private void updateLive2DVisibility() {
        if (rootLayout == null || launcherView == null || live2DView == null) {
            return;
        }

        boolean shouldShow = launcherView.getActiveIndex() == 0 && live2DView.isLive2DEnabled();
        if (!shouldShow) {
            live2DView.setVisibility(View.GONE);
            live2DView.applySettings();
            return;
        }

        live2DView.setVisibility(View.VISIBLE);
        live2DView.resumeLive2D();
        live2DView.applySettings();
        scheduleLive2DHealthCheck();
    }

    private void scheduleLive2DHealthCheck() {
        if (rootLayout == null || live2DView == null || pendingLive2DHealthCheck) {
            return;
        }
        pendingLive2DHealthCheck = true;
        rootLayout.postDelayed(new Runnable() {
            @Override
            public void run() {
                pendingLive2DHealthCheck = false;
                if (rootLayout == null || live2DView == null || !isHomePage() || !live2DView.isLive2DEnabled()) {
                    return;
                }
                live2DView.setVisibility(View.VISIBLE);
                live2DView.resumeLive2D();
                if (!live2DView.isLive2DHealthy()) {
                    live2DView.hardReloadLive2D();
                    lastLive2DReloadAt = System.currentTimeMillis();
                } else {
                    live2DView.applySettings();
                }
            }
        }, 13500L);
    }

    private void updateAmapFloatingCardVisibility() {
        if (rootLayout == null || launcherView == null || mapCardContainer == null) {
            if (amapFloatingCardController != null) {
                amapFloatingCardController.setHomeVisible(false);
            }
            return;
        }

        boolean shouldShow = shouldShowAmapFloatingCardOnHome();
        if (!shouldShow) {
            mapCardContainer.setVisibility(View.GONE);
            if (amapFloatingCardController != null) {
                amapFloatingCardController.setHomeVisible(false);
            }
            return;
        }

        positionMapCardContainer();
        mapCardContainer.setVisibility(View.VISIBLE);
        mapCardContainer.bringToFront();

        boolean installed = AmapFloatingCardController.isAmapFloatingInstalled(this);
        if (mapCardHintView != null) {
            if (!installed) {
                mapCardHintView.setText("未安装悬浮版高德地图\n包名：com.autonavi.amapautoys");
                mapCardHintView.setVisibility(View.VISIBLE);
            } else {
                mapCardHintView.setText("");
                mapCardHintView.setVisibility(View.GONE);
            }
        }

        if (amapFloatingCardController != null) {
            amapFloatingCardController.setHomeVisible(installed);
        }
    }

    private boolean shouldShowAmapFloatingCardOnHome() {
        boolean homePage = launcherView != null && launcherView.getActiveIndex() == 0;
        return homePage && isActivityResumed && hasWindowFocusNow;
    }

    private void markHomeKeyTransientForAmap() {
        if (isHomePage()) {
            // 部分车机实体 HOME 键会造成较长的瞬时失焦/重入，保护时间加长，避免首页悬浮高德被误关。
            keepAmapOnHomeKeyUntilMs = System.currentTimeMillis() + 8000L;
        }
    }

    private boolean shouldKeepAmapDuringHomeKeyTransient() {
        return isHomePage() && System.currentTimeMillis() <= keepAmapOnHomeKeyUntilMs;
    }

    private boolean shouldKeepAmapDuringColdStartWarmup() {
        return System.currentTimeMillis() <= amapColdStartWarmupUntilMs;
    }

    private boolean isExplicitExternalLaunchActive() {
        return System.currentTimeMillis() <= explicitExternalLaunchUntilMs;
    }

    private void positionMapCardContainer() {
        if (mapCardContainer == null || rootLayout == null) {
            return;
        }

        int rw = rootLayout.getWidth();
        int rh = rootLayout.getHeight();
        if (rw <= 0 || rh <= 0) {
            return;
        }

        float sx = rw / DESIGN_W;
        float sy = rh / DESIGN_H;

        int left = Math.round((CARD1_L + CARD1_WIDGET_INSET) * sx);
        int top = Math.round((CARD1_T + CARD1_WIDGET_INSET) * sy);
        int width = Math.round((CARD1_R - CARD1_L - CARD1_WIDGET_INSET * 2f) * sx);
        int height = Math.round((CARD1_B - CARD1_T - CARD1_WIDGET_INSET * 2f) * sy);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height);
        lp.leftMargin = left;
        lp.topMargin = top;
        mapCardContainer.setLayoutParams(lp);
    }

    private void positionRearAiOverlay() {
        if (rearAiOverlay == null || rootLayout == null) {
            return;
        }
        int rw = rootLayout.getWidth();
        int rh = rootLayout.getHeight();
        if (rw <= 0 || rh <= 0) {
            return;
        }
        int width = rw / 2;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, rh);
        lp.leftMargin = rw - width;
        lp.topMargin = 0;
        rearAiOverlay.setLayoutParams(lp);
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void keepFullscreen() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 作为默认 Launcher 时，系统 HOME 可能以新 Intent 形式拉起已有 singleTask。
        // 无论是首页重复 HOME 还是从其它 App 回桌面，都执行一次首页自修复。
        showHomePage(false);
        reloadHomeSurfaceOnHomeKey();
    }

    public void showHomePage() {
        showHomePage(false);
    }

    public void showHomePage(boolean reloadLive2D) {
        if (launcherView != null) {
            launcherView.showHomePage();
        }
        updateLive2DVisibility();
        updateAmapFloatingCardVisibility();
        if (reloadLive2D) {
            reloadLive2DOnHome();
        }
    }

    private void reloadLive2DOnHome() {
        if (live2DView != null && live2DView.isLive2DEnabled()) {
            live2DView.resumeLive2D();
            live2DView.reloadLive2D();
            live2DView.setVisibility(View.VISIBLE);
            lastLive2DReloadAt = System.currentTimeMillis();
            scheduleLive2DHealthCheck();
        }
    }

    private void scheduleLive2DReloadIfHome(final boolean force, long delayMs) {
        if (rootLayout == null || live2DView == null || !live2DView.isLive2DEnabled() || !isHomePage()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!force && lastLive2DReloadAt > 0L && now - lastLive2DReloadAt < 12000L) {
            // 避免短时间反复进出页面时连续 reload，防止低配车机更卡。
            return;
        }

        if (pendingLive2DReload) {
            return;
        }

        pendingLive2DReload = true;
        rootLayout.postDelayed(new Runnable() {
            @Override
            public void run() {
                pendingLive2DReload = false;
                if (rootLayout == null || live2DView == null || !live2DView.isLive2DEnabled() || !isHomePage()) {
                    return;
                }

                live2DView.setVisibility(View.VISIBLE);
                live2DView.resumeLive2D();

                if (force || lastLive2DReloadAt <= 0L || System.currentTimeMillis() - lastLive2DReloadAt >= 12000L) {
                    live2DView.reloadLive2D();
                    lastLive2DReloadAt = System.currentTimeMillis();
                } else {
                    live2DView.applySettings();
                }
            }
        }, Math.max(0L, delayMs));
    }

    public boolean isHomePage() {
        return launcherView == null || launcherView.getActiveIndex() == 0;
    }

    private void registerHomeKeyReceiver() {
        if (homeKeyReceiver != null) {
            return;
        }
        homeKeyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !"android.intent.action.CLOSE_SYSTEM_DIALOGS".equals(intent.getAction())) {
                    return;
                }
                String reason = intent.getStringExtra("reason");
                if ("homekey".equals(reason)) {
                    showHomePage(false);
                    reloadHomeSurfaceOnHomeKey();
                } else if ("recentapps".equals(reason)) {
                    closeAmapFloatingImmediately();
                    showHomePage(false);
                }
            }
        };
        try {
            registerReceiver(homeKeyReceiver, new IntentFilter("android.intent.action.CLOSE_SYSTEM_DIALOGS"));
        } catch (Throwable ignored) {
        }
    }

    private void unregisterHomeKeyReceiver() {
        if (homeKeyReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(homeKeyReceiver);
        } catch (Throwable ignored) {
        }
        homeKeyReceiver = null;
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (isExplicitExternalLaunchActive()) {
            closeAmapFloatingImmediately();
        } else if (!(shouldKeepAmapDuringHomeKeyTransient() || shouldKeepAmapDuringColdStartWarmup())) {
            closeAmapFloatingImmediately();
        }
    }

    @Override
    protected void onDestroy() {
        if (rearAiVisionController != null) {
            rearAiVisionController.stop();
        }
        if (amapFloatingCardController != null) {
            amapFloatingCardController.onPause();
        } else {
            AmapFloatingCardController.sendCloseMapBroadcast(this);
        }
        unregisterHomeKeyReceiver();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Launcher 语义：返回键只回到首页，首页下返回键无效果，绝不 finish，也不露出上一个 App。
        if (!isHomePage()) {
            showHomePage(false);
        }
    }

    private boolean consumeLauncherNavigationKey(int keyCode, KeyEvent event) {
        if (!HomeKeyHelper.isHomeKey(keyCode) && !HomeKeyHelper.isBackKey(keyCode)) {
            return false;
        }

        // DOWN / UP 全部消费，避免部分车机在 UP 阶段继续交给系统导致回到上一个 App。
        if (event == null || event.getAction() == KeyEvent.ACTION_DOWN) {
            if (HomeKeyHelper.isHomeKey(keyCode)) {
                showHomePage(false);
                reloadHomeSurfaceOnHomeKey();
            } else if (HomeKeyHelper.isBackKey(keyCode)) {
                if (!isHomePage()) {
                    showHomePage(false);
                }
            }
        }
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null && consumeLauncherNavigationKey(event.getKeyCode(), event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (consumeLauncherNavigationKey(keyCode, event)) {
            return true;
        }
        if (launcherView != null && launcherView.handleHardwareKey(keyCode)) {
            updateAmapFloatingCardVisibility();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (consumeLauncherNavigationKey(keyCode, event)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActivityResumed = true;
        keepFullscreen();
        if (appWidgetHost != null) {
            appWidgetHost.startListening();
        }
        if (backgroundView != null) {
            backgroundView.invalidate();
        }
        if (launcherView != null) {
            launcherView.setTurnSignalAudioAllowed(hasWindowFocusNow);
            launcherView.invalidateAppIconCaches();
        }
        // 不在 onResume 主动预加载应用列表，避免从外部 App 返回桌面时低速存储重新扫描导致卡顿。
        if (live2DView != null) {
            live2DView.resumeLive2D();
            live2DView.applySettings();
            if (isHomePage()) {
                live2DView.setVisibility(live2DView.isLive2DEnabled() ? View.VISIBLE : View.GONE);
                scheduleLive2DHealthCheck();
            }
        }
        if (rearAiVisionController != null) {
            rearAiVisionController.start();
        }
        if (rootLayout != null) {
            rootLayout.post(new Runnable() {
                @Override
                public void run() {
                    updateLive2DVisibility();
                    updateAmapFloatingCardVisibility();
                    if (isHomePage()) {
                        ensureAmapMainFloatingWindowLoaded(false);
                    }
                    positionRearAiOverlay();
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityResumed = false;
        hasWindowFocusNow = false;
        if (appWidgetHost != null) {
            appWidgetHost.stopListening();
        }
        if (live2DView != null) {
            live2DView.pauseLive2D();
        }
        if (launcherView != null) {
            launcherView.setTurnSignalAudioAllowed(false);
        }
        if (shouldKeepAmapDuringHomeKeyTransient() || shouldKeepAmapDuringColdStartWarmup()) {
            // 首页实体 HOME 重入 / 高德预热期间不主动 closemap，避免刚显示出的主悬浮窗被瞬时 onPause 关掉。
        } else if (amapFloatingCardController != null) {
            amapFloatingCardController.onPause();
        } else {
            AmapFloatingCardController.sendCloseMapBroadcast(this);
        }
        if (rearAiVisionController != null) {
            rearAiVisionController.stop();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        hasWindowFocusNow = hasFocus;
        if (launcherView != null) {
            launcherView.setTurnSignalAudioAllowed(hasFocus && isActivityResumed);
        }
        if (hasFocus) {
            keepFullscreen();
            if (backgroundView != null) {
                backgroundView.invalidate();
            }
            updateLive2DVisibility();
            positionRearAiOverlay();
            updateAmapFloatingCardVisibility();
            if (isHomePage()) {
                ensureAmapMainFloatingWindowLoaded(false);
            }
        } else {
            if (!(shouldKeepAmapDuringHomeKeyTransient() || shouldKeepAmapDuringColdStartWarmup())) {
                updateAmapFloatingCardVisibility();
            }
        }
        positionRearAiOverlay();
    }
}
