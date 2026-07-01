package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.app.ActivityManager;
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
import android.database.ContentObserver;
import android.widget.TextView;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.util.Log;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String TAG_AMAP = "MikuAmap";
    private static final String ACTION_FORFAN_REVERSING = "com.forfan.operator_reversing";
    // 雷达 / 双闪 / 触摸车机全景按钮时，AVM 模块会发这个广播；倒车不一定是唯一触发源。
    private static final String ACTION_LDFY_CAR360_CTRL = "com.ldfy.car360ctrl.action";
    private static final String SETTING_CARLETTER_REVERSE_STATE = "carletter_reserve_state";
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
    // 首页高德主悬浮窗守护窗口：用于抵消高德悬浮窗自身抢焦点 / HOME 重入造成的瞬时失焦。
    private long amapHomeGuardUntilMs = 0L;
    // 安全优先：倒车/全景/雷达靠近物体/第三方前台时，禁止首页守护重新拉起高德悬浮窗。
    private long amapSafetyBlockUntilMs = 0L;
    private boolean amapSafetyMonitorStarted = false;
    private final Runnable amapSafetyMonitorRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                checkAmapSafetySuppression("monitor");
            } catch (Throwable ignored) {
            }
            if (rootLayout != null && amapSafetyMonitorStarted) {
                rootLayout.postDelayed(this, 500L);
            }
        }
    };
    private boolean pendingLive2DHealthCheck = false;
    private BroadcastReceiver homeKeyReceiver;
    private BroadcastReceiver reverseStateReceiver;
    private ContentObserver reverseStateObserver;
    private boolean reverseCameraActive = false;
    private Handler mainHandler;
    private boolean panoramaForegroundActive = false;
    private boolean foregroundSafetyMonitorStarted = false;
    private long lastPanoramaCloseMapAt = 0L;
    // AVM 有时会把 Activity 短暂退到后台，但 BirdviewSurface / 摄像头画面仍在显示。
    // 记录一个安全保持窗口，避免刚识别到全景后马上回到 Launcher 又把高德 show 出来。
    private long avmSafetyHoldUntilMs = 0L;
    private static final long AVM_FOREGROUND_HOLD_MS = 12000L;
    private static final long AVM_SIGNAL_HOLD_MS = 12000L;
    private final Runnable foregroundSafetyMonitorRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                checkAmapForegroundSafety("foreground-monitor");
            } catch (Throwable ignored) {
            }
            if (foregroundSafetyMonitorStarted && mainHandler != null) {
                mainHandler.postDelayed(this, 350L);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        keepFullscreen();
        mainHandler = new Handler();

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
        registerReverseSafetyObserver();
        startAmapForegroundSafetyMonitor();
        updateReverseCameraSafetyState("create");
        checkAmapForegroundSafety("create");
        // V0.7.4.3：高德逻辑仍保持最小状态机；倒车档位用 carletter_reserve_state，
        // 雷达/双闪触发的全景则用前台 AVM Activity / Window 检测精准关闭。

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
        explicitExternalLaunchUntilMs = System.currentTimeMillis() + 2500L;
        // V0.7.4.0：打开外部 App 前只关闭悬浮窗，不再进入安全阻断，也不干预高德进程/导航状态。
        closeAmapForExternalForeground("explicit-external-launch");
    }

    private void closeAmapFloatingImmediately() {
        keepAmapOnHomeKeyUntilMs = 0L;
        amapHomeGuardUntilMs = 0L;
        try {
            if (mapCardContainer != null) {
                mapCardContainer.setVisibility(View.GONE);
            }
            if (amapFloatingCardController != null) {
                amapFloatingCardController.setHomeVisible(false);
            } else {
                AmapFloatingCardController.sendCloseMapBroadcast(this);
            }
            Log.i(TAG_AMAP, "closemap immediate");
        } catch (Throwable ignored) {
            try {
                AmapFloatingCardController.sendCloseMapBroadcast(this);
            } catch (Throwable ignored2) {
            }
        }
    }

    private void closeAmapForExternalForeground(String reason) {
        keepAmapOnHomeKeyUntilMs = 0L;
        amapHomeGuardUntilMs = 0L;
        try {
            if (mapCardContainer != null) {
                mapCardContainer.setVisibility(View.GONE);
            }
            if (amapFloatingCardController != null) {
                amapFloatingCardController.setHomeVisible(false);
            } else {
                AmapFloatingCardController.sendCloseMapBroadcast(this);
            }
            Log.i(TAG_AMAP, "closemap external reason=" + reason);
        } catch (Throwable ignored) {
            try {
                AmapFloatingCardController.sendCloseMapBroadcast(this);
            } catch (Throwable ignored2) {
            }
        }
    }

    private void closeAmapForReverseCamera(String reason) {
        keepAmapOnHomeKeyUntilMs = 0L;
        amapHomeGuardUntilMs = 0L;
        try {
            if (mapCardContainer != null) {
                mapCardContainer.setVisibility(View.GONE);
            }
            if (amapFloatingCardController != null) {
                amapFloatingCardController.setHomeVisible(false);
            } else {
                AmapFloatingCardController.sendCloseMapBroadcast(this);
            }
            Log.i(TAG_AMAP, "closemap reverse reason=" + reason);
        } catch (Throwable ignored) {
            try {
                AmapFloatingCardController.sendCloseMapBroadcast(this);
            } catch (Throwable ignored2) {
            }
        }
    }

    private void registerReverseSafetyObserver() {
        try {
            if (reverseStateObserver == null) {
                reverseStateObserver = new ContentObserver(new Handler()) {
                    @Override
                    public void onChange(boolean selfChange) {
                        super.onChange(selfChange);
                        updateReverseCameraSafetyState("settings-observer");
                    }

                    @Override
                    public void onChange(boolean selfChange, Uri uri) {
                        super.onChange(selfChange, uri);
                        updateReverseCameraSafetyState("settings-observer-uri");
                    }
                };
                getContentResolver().registerContentObserver(
                        Settings.System.getUriFor(SETTING_CARLETTER_REVERSE_STATE),
                        false,
                        reverseStateObserver
                );
            }
        } catch (Throwable t) {
            Log.w(TAG_AMAP, "register reverse settings observer failed", t);
        }

        try {
            if (reverseStateReceiver == null) {
                reverseStateReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (intent == null) {
                            return;
                        }
                        String action = intent.getAction();
                        if (ACTION_FORFAN_REVERSING.equals(action)) {
                            Log.i(TAG_AMAP, "receive reversing broadcast extras=" + (intent.getExtras() == null ? "null" : intent.getExtras().toString()));
                            // 车机广播和 carletter_reserve_state 写入几乎同时发生。
                            // 为了安全，收到倒车广播先立即 closemap，再短延迟读取设置确认状态。
                            markAvmSafetyHold("forfan-reversing-broadcast", AVM_SIGNAL_HOLD_MS);
                            closeAmapForReverseCamera("forfan-broadcast-immediate");
                            if (rootLayout != null) {
                                rootLayout.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        updateReverseCameraSafetyState("forfan-broadcast-delay");
                                    }
                                }, 120L);
                            } else {
                                updateReverseCameraSafetyState("forfan-broadcast");
                            }
                        } else if (ACTION_LDFY_CAR360_CTRL.equals(action)) {
                            // 双闪 / 雷达 / 全景按钮触发时，很多车机不会改 carletter_reserve_state，
                            // 也可能让 AVM Activity 很快退到后台，只剩 BirdviewSurface 继续显示。
                            // 收到全景控制广播后直接关闭高德，并保持一小段安全窗口。
                            Log.i(TAG_AMAP, "receive ldfy car360 broadcast extras=" + (intent.getExtras() == null ? "null" : intent.getExtras().toString()));
                            markAvmSafetyHold("ldfy-car360-broadcast", AVM_SIGNAL_HOLD_MS);
                            closeAmapForReverseCamera("ldfy-car360-broadcast");
                            if (mainHandler != null) {
                                mainHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        checkAmapForegroundSafety("ldfy-car360-delay-300");
                                    }
                                }, 300L);
                                mainHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        checkAmapForegroundSafety("ldfy-car360-delay-1200");
                                    }
                                }, 1200L);
                            }
                        }
                    }
                };
                IntentFilter filter = new IntentFilter(ACTION_FORFAN_REVERSING);
                filter.addAction(ACTION_LDFY_CAR360_CTRL);
                registerReceiver(reverseStateReceiver, filter);
            }
        } catch (Throwable t) {
            Log.w(TAG_AMAP, "register reverse broadcast failed", t);
        }
    }

    private void unregisterReverseSafetyObserver() {
        if (reverseStateReceiver != null) {
            try {
                unregisterReceiver(reverseStateReceiver);
            } catch (Throwable ignored) {
            }
            reverseStateReceiver = null;
        }
        if (reverseStateObserver != null) {
            try {
                getContentResolver().unregisterContentObserver(reverseStateObserver);
            } catch (Throwable ignored) {
            }
            reverseStateObserver = null;
        }
    }

    private int readSystemReverseState() {
        try {
            return Settings.System.getInt(getContentResolver(), SETTING_CARLETTER_REVERSE_STATE, 0);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private void updateReverseCameraSafetyState(String reason) {
        boolean active = readSystemReverseState() != 0;
        if (active == reverseCameraActive) {
            if (active) {
                closeAmapForReverseCamera(reason + "-still-active");
            }
            return;
        }

        reverseCameraActive = active;
        Log.i(TAG_AMAP, "reverseCameraActive=" + active + " reason=" + reason);

        if (active) {
            closeAmapForReverseCamera(reason);
        } else if (rootLayout != null) {
            rootLayout.post(new Runnable() {
                @Override
                public void run() {
                    updateAmapFloatingCardVisibility();
                }
            });
        }
    }


    private void markAvmSafetyHold(String reason, long durationMs) {
        long until = System.currentTimeMillis() + Math.max(1000L, durationMs);
        if (until > avmSafetyHoldUntilMs) {
            avmSafetyHoldUntilMs = until;
        }
        panoramaForegroundActive = true;
        Log.i(TAG_AMAP, "avmSafetyHold until=" + avmSafetyHoldUntilMs + " reason=" + reason);
    }

    private boolean isAvmSafetyHoldActive() {
        return System.currentTimeMillis() < avmSafetyHoldUntilMs;
    }

    private void startAmapForegroundSafetyMonitor() {
        if (foregroundSafetyMonitorStarted) {
            return;
        }
        foregroundSafetyMonitorStarted = true;
        if (mainHandler != null) {
            mainHandler.removeCallbacks(foregroundSafetyMonitorRunnable);
            mainHandler.postDelayed(foregroundSafetyMonitorRunnable, 250L);
        }
    }

    private void stopAmapForegroundSafetyMonitor() {
        foregroundSafetyMonitorStarted = false;
        if (mainHandler != null) {
            mainHandler.removeCallbacks(foregroundSafetyMonitorRunnable);
        }
    }

    private void checkAmapForegroundSafety(String reason) {
        ComponentName top = getTopActivityComponentCompat();
        boolean active = isKnownPanoramaOrVehicleForeground(top);
        long now = System.currentTimeMillis();
        if (active) {
            markAvmSafetyHold(reason + " top=" + flattenComponent(top), AVM_FOREGROUND_HOLD_MS);
            // 全景画面可能由雷达/双闪自动拉起，此时 MainActivity 会被 pause，不能依赖 onPause 的普通关闭逻辑。
            // 这里在 AVM 前台期间持续补发 closemap，但做轻微节流，避免 log 和广播过量。
            if (now - lastPanoramaCloseMapAt > 800L) {
                lastPanoramaCloseMapAt = now;
                closeAmapForReverseCamera(reason + " top=" + flattenComponent(top));
            }
            return;
        }

        // AVM Activity 有时已经 onStop / 退到后台，但 BirdviewSurface 摄像头层仍在显示。
        // 在保持窗口内继续禁止 showmap，并节流补发 closemap，防止高德悬浮窗覆盖全景画面。
        if (isAvmSafetyHoldActive()) {
            if (now - lastPanoramaCloseMapAt > 800L) {
                lastPanoramaCloseMapAt = now;
                closeAmapForReverseCamera(reason + " avm-hold top=" + flattenComponent(top));
            }
            return;
        }

        if (panoramaForegroundActive) {
            panoramaForegroundActive = false;
            Log.i(TAG_AMAP, "panoramaForegroundActive=false reason=" + reason + " top=" + flattenComponent(top));
            if (isActivityResumed && isHomePage() && rootLayout != null) {
                rootLayout.post(new Runnable() {
                    @Override
                    public void run() {
                        updateAmapFloatingCardVisibility();
                    }
                });
            }
        }
    }

    private ComponentName getTopActivityComponentCompat() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return null;
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks == null || tasks.isEmpty()) return null;
            return tasks.get(0).topActivity;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String flattenComponent(ComponentName cn) {
        if (cn == null) return "";
        try {
            return cn.flattenToShortString();
        } catch (Throwable ignored) {
            return cn.getPackageName() + "/" + cn.getClassName();
        }
    }

    private boolean isKnownPanoramaOrVehicleForeground(ComponentName cn) {
        if (cn == null) return false;
        String pkg = cn.getPackageName();
        String cls = cn.getClassName();
        if (pkg == null) pkg = "";
        if (cls == null) cls = "";
        String lowerPkg = pkg.toLowerCase(Locale.US);
        String lowerCls = cls.toLowerCase(Locale.US);

        // 实车 log：雷达/双闪/倒车触发的全景会出现 com.baony.avm360/com.baony.ui.activity.AVMBVActivity，
        // 有时还会先出现 Splash Screen com.baony.avm360。只要 AVM 进前台，就必须安全优先关闭高德悬浮窗。
        if ("com.baony.avm360".equals(pkg)) return true;
        if (lowerCls.contains("avmbvactivity")) return true;
        if (lowerPkg.contains("avm") || lowerPkg.contains("panorama")) return true;
        if (lowerCls.contains("avm") || lowerCls.contains("panorama")) return true;

        // 部分车机把倒车/泊车/车辆画面放在 com.ts.MainUI 内部 Activity。
        // 它在前台时不是 Launcher 首页，关闭高德悬浮窗是安全策略，不影响回到首页后的 showmap。
        if ("com.ts.MainUI".equals(pkg)) {
            return lowerCls.contains("reverse")
                    || lowerCls.contains("park")
                    || lowerCls.contains("camera")
                    || lowerCls.contains("can")
                    || lowerCls.contains("audi")
                    || lowerCls.contains("mainui");
        }
        return false;
    }

    private void maybeStartAmapColdStartWarmup() {
        // V0.7.4.0：已停用。
        // 旧版会主动 startActivity 打开 com.autonavi.amapautoys 做前台预热，
        // 实车反馈会打断高德导航状态，甚至导致路线被清空。Launcher 不再主动打开高德 App。
    }

    private void bringLauncherBackAfterAmapWarmup() {
        // V0.7.4.0：已停用高德前台预热。
    }

    private void bringLauncherToFrontForAmapWarmup() {
        // V0.7.4.0：已停用高德前台预热。
    }

    private void reloadHomeSurfaceOnHomeKey() {
        // V0.7.4.0：HOME / 再点首页只做“首页恢复 + Live2D 重载 + showmap 补发”。
        // 绝不 closemap 后再 show，也绝不重启/拉起/杀掉高德 App，避免正在导航的路线被打断。
        explicitExternalLaunchUntilMs = 0L;
        showHomePage(false);
        reloadLive2DOnHome();
        updateAmapFloatingCardVisibility();
        ensureAmapMainFloatingWindowLoaded(false);
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
                    amapFloatingCardController.reloadMapWindow();
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


    private void startAmapSafetyMonitor() {
        // V0.7.4.0：不再启动持续安全阻断监控，避免车辆数据/雷达误判导致首页高德被反复 close。
        // 全景/车辆界面通过显式点击入口关闭高德；后续若要做倒车自动关闭，需要接入明确的倒车档位字段。
    }

    private void stopAmapSafetyMonitor() {
        // V0.7.4.0：无持续监控。
    }

    private boolean isAmapSafetyBlocked() {
        return false;
    }

    private void blockAmapForSafety(String reason, long durationMs) {
        // V0.7.4.0：安全场景只 closemap，不保留任何阻断倒计时，避免普通 HOME 返回后无法恢复悬浮窗。
        closeAmapForExternalForeground("safety-" + reason);
    }

    private void checkAmapSafetySuppression(String reason) {
        // V0.7.4.0：不再基于雷达数组/普通第三方前台做自动 close。
        // 原逻辑在实车上会把普通返回首页也当作安全阻断，造成悬浮窗无法恢复。
    }

    private String getTopPackageCompat() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return "";
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks == null || tasks.isEmpty() || tasks.get(0).topActivity == null) {
                return "";
            }
            return tasks.get(0).topActivity.getPackageName();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private boolean isOurPackage(String pkg) {
        return pkg != null && pkg.equals(getPackageName());
    }

    private boolean isKnownPanoramaOrVehicleForegroundPackage(String pkg) {
        if (pkg == null) return false;
        return pkg.equals("com.baony.avm360")
                || pkg.equals("com.ts.MainUI")
                || pkg.toLowerCase(Locale.US).contains("avm")
                || pkg.toLowerCase(Locale.US).contains("camera")
                || pkg.toLowerCase(Locale.US).contains("panorama");
    }

    private boolean isVehiclePanoramaOrReverseLikely() {
        // V0.7.4.0：暂时禁用雷达数组推断。
        // 之前只要雷达值为正就 closemap，实车上会导致首页高德被 vehicle-monitor 反复关闭。
        return false;
    }

    private boolean hasActiveRadar(int[] values) {
        return false;
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
        return launcherView != null
                && launcherView.getActiveIndex() == 0
                && isActivityResumed
                && !reverseCameraActive
                && !panoramaForegroundActive
                && !isAvmSafetyHoldActive()
                && !isExplicitExternalLaunchActive();
    }

    private boolean shouldHoldAmapDuringTemporaryHomeTransition() {
        return false;
    }

    private void markHomeKeyTransientForAmap() {
        // V0.7.4.0：已停用首页守护倒计时。HOME 重入直接补发 showmap。
    }

    private boolean shouldKeepAmapDuringHomeKeyTransient() {
        return false;
    }

    private boolean shouldKeepAmapDuringColdStartWarmup() {
        return false;
    }

    private void extendAmapHomeGuard(long durationMs) {
        // V0.7.4.0：已停用首页守护倒计时。
    }

    private boolean shouldKeepAmapDuringHomeGuard() {
        return false;
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
        explicitExternalLaunchUntilMs = 0L;
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
        updateReverseCameraSafetyState("show-home-page");
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
                    // HOME 重入：只恢复首页并补发 showmap，不 closemap。
                    explicitExternalLaunchUntilMs = 0L;
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
        if (isExplicitExternalLaunchActive() || !isHomePage()) {
            closeAmapFloatingImmediately();
        }
        // 首页按 HOME 不 closemap；等 onNewIntent / homekey 广播回到首页后补发 showmap。
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
        unregisterReverseSafetyObserver();
        stopAmapForegroundSafetyMonitor();
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
        updateReverseCameraSafetyState("resume");
        checkAmapForegroundSafety("resume");
        if (isHomePage()) {
            // 回到首页：普通第三方 App 的外部启动保护立即结束，允许立即重新 showmap。
            explicitExternalLaunchUntilMs = 0L;
        }
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
        if (isExplicitExternalLaunchActive() || !isHomePage()) {
            if (amapFloatingCardController != null) {
                amapFloatingCardController.onPause();
            } else {
                AmapFloatingCardController.sendCloseMapBroadcast(this);
            }
        }
        // 首页 HOME 重入 / 高德悬浮窗自身导致的瞬时 pause 不 closemap。
        // 但雷达/双闪/倒车自动拉起 AVM 时，MainActivity 也会 pause；延迟读取前台 Activity，
        // 如果发现全景/车辆画面已在前台，就强制 closemap。
        if (mainHandler != null) {
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkAmapForegroundSafety("pause-delay-120");
                }
            }, 120L);
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkAmapForegroundSafety("pause-delay-500");
                }
            }, 500L);
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
            updateReverseCameraSafetyState("window-focus");
            positionRearAiOverlay();
            updateAmapFloatingCardVisibility();
            if (isHomePage()) {
                ensureAmapMainFloatingWindowLoaded(false);
            }
        } else {
            if (isExplicitExternalLaunchActive() || !isHomePage()) {
                updateAmapFloatingCardVisibility();
            }
            checkAmapForegroundSafety("window-focus-lost");
            // 首页失焦不 closemap。高德自己的悬浮窗/导航浮层可能会抢焦点；
            // 只有检测到 AVM / 车辆画面前台时才 closemap。
        }
        positionRearAiOverlay();
    }
}
