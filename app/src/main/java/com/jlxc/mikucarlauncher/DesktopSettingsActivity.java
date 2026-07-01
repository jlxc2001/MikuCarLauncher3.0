package com.jlxc.mikucarlauncher;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.OpenableColumns;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class DesktopSettingsActivity extends Activity {
    private static final String PREFS = MainActivity.PREFS;

    private static final int REQ_SELECT_WIDGET_PROVIDER = 2501;
    private static final int REQ_BIND_WIDGET = 2502;
    private static final int REQ_CONFIG_WIDGET = 2503;
    private static final int REQ_PICK_DAY_BACKGROUND = 2601;
    private static final int REQ_PICK_NIGHT_BACKGROUND = 2602;

    private TextView navValue;
    private TextView musicValue;
    private TextView drawerStyleValue;
    private TextView card1WidgetValue;
    private TextView commonAppsValue;
    private TextView weatherValue;
    private TextView turnSignalValue;
    private TextView vehicleHookValue;
    private TextView hudBroadcastValue;
    private TextView rearAiVisionValue;
    private TextView textDisplayNodeValue;
    private TextView dayBackgroundValue;
    private TextView nightBackgroundValue;
    private TextView nightModeValue;
    private TextView iconPackValue;

    private RoundedAppWidgetHost appWidgetHost;
    private AppWidgetManager appWidgetManager;
    private int pendingWidgetId = -1;
    private ComponentName pendingProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appWidgetHost = new RoundedAppWidgetHost(this, MainActivity.APPWIDGET_HOST_ID);
        appWidgetManager = AppWidgetManager.getInstance(this);
        keepFullscreen();
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        keepFullscreen();
        refreshValues();
        if (appWidgetHost != null) {
            appWidgetHost.startListening();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (appWidgetHost != null) {
            appWidgetHost.stopListening();
        }
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(238, 241, 246));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(46), dp(34), dp(46), dp(46));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("车机桌面设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        navValue = addValue(root, "默认导航软件：");
        Button chooseNav = addButton(root, "选择默认导航软件");
        chooseNav.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openPicker("nav");
            }
        });

        musicValue = addValue(root, "默认音乐软件：");
        Button chooseMusic = addButton(root, "选择默认音乐软件");
        chooseMusic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openPicker("music");
            }
        });

        Button musicPermission = addButton(root, "开启音乐信息读取权限");
        musicPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openNotificationListenerSettings();
            }
        });

        card1WidgetValue = addValue(root, "1号卡片悬浮高德：");
        Button amapFloatingSettings = addButton(root, "1号卡片悬浮高德设置");
        amapFloatingSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DesktopSettingsActivity.this, AmapFloatingCardSettingsActivity.class));
            }
        });

        Button overlayPermission = addButton(root, "打开悬浮版高德地图悬浮窗权限设置");
        overlayPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AmapFloatingCardController.openAmapOverlayPermissionPage(DesktopSettingsActivity.this);
            }
        });

        Button clearWidget = addButton(root, "清除旧版 1号卡片小组件配置");
        clearWidget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearCard1Widget();
            }
        });

        drawerStyleValue = addValue(root, "应用抽屉显示：");
        Button drawerSettings = addButton(root, "应用抽屉显示设置");
        drawerSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DesktopSettingsActivity.this, AppDrawerSettingsActivity.class));
            }
        });

        Button rebuildDrawerCache = addButton(root, "手动更新应用列表 / APP 缩略图缓存");
        rebuildDrawerCache.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rebuildAppThumbCache();
            }
        });

        Button clearDrawerCache = addButton(root, "清空应用列表 / APP 缩略图缓存");
        clearDrawerCache.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AppDrawerCacheManager.clearCache(DesktopSettingsActivity.this);
                Toast.makeText(DesktopSettingsActivity.this, "已清空 APP 缩略图缓存，下次进入应用抽屉会重新生成", Toast.LENGTH_LONG).show();
            }
        });

        iconPackValue = addValue(root, "应用图标包：");
        Button iconPackSettings = addButton(root, "图标包设置 / 导入第三方图标包");
        iconPackSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DesktopSettingsActivity.this, IconPackSettingsActivity.class));
            }
        });

        commonAppsValue = addValue(root, "4号卡片常用软件：");
        Button commonAppsSettings = addButton(root, "设置 4号卡片常用软件");
        commonAppsSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DesktopSettingsActivity.this, CommonAppsSettingsActivity.class));
            }
        });

        weatherValue = addValue(root, "6号卡片天气：");
        Button weatherSettings = addButton(root, "设置 6号天气卡片");
        weatherSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DesktopSettingsActivity.this, WeatherSettingsActivity.class));
            }
        });

        turnSignalValue = addValue(root, "转向音：");
        Button turnSignalSettings = addButton(root, "转向音 / 转向提示设置");
        turnSignalSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DesktopSettingsActivity.this, TurnSignalSettingsActivity.class));
            }
        });

        vehicleHookValue = addValue(root, "车辆 Hook 数据：");
        Button vehicleHookSettings = addButton(root, "查看 Hook 原始数据 / 可读状态 / 轮询设置");
        vehicleHookSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DesktopSettingsActivity.this, VehicleHookSettingsActivity.class));
            }
        });

        hudBroadcastValue = addValue(root, "HUD 局域网广播：");
        Button hudBroadcastSettings = addButton(root, "HUD 数据广播设置");
        hudBroadcastSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DesktopSettingsActivity.this, HudBroadcastSettingsActivity.class));
            }
        });

        rearAiVisionValue = addValue(root, "后置 AI 视觉节点：");
        Button rearAiVisionSettings = addButton(root, "后置 AI 视觉节点设置");
        rearAiVisionSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DesktopSettingsActivity.this, RearAiVisionSettingsActivity.class));
            }
        });

        textDisplayNodeValue = addValue(root, "快捷文字屏节点：");
        Button textDisplayNodeSettings = addButton(root, "快捷文字屏节点设置");
        textDisplayNodeSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DesktopSettingsActivity.this, MikuTextDisplayNodeSettingsActivity.class));
            }
        });

        nightModeValue = addValue(root, "夜间模式：");
        Button nightModeSettings = addButton(root, "夜间模式设置");
        nightModeSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DesktopSettingsActivity.this, NightModeSettingsActivity.class));
            }
        });

        Button live2DSettings = addButton(root, "Live2D 装饰模型设置");
        live2DSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DesktopSettingsActivity.this, Live2DSettingsActivity.class));
            }
        });

        dayBackgroundValue = addValue(root, "日间背景图片：");
        Button chooseDayBackground = addButton(root, "更换日间背景图片");
        chooseDayBackground.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickAppBackground(REQ_PICK_DAY_BACKGROUND, "选择日间背景图片");
            }
        });

        nightBackgroundValue = addValue(root, "夜间背景图片：");
        Button chooseNightBackground = addButton(root, "更换夜间背景图片");
        chooseNightBackground.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickAppBackground(REQ_PICK_NIGHT_BACKGROUND, "选择夜间背景图片");
            }
        });

        Button resetBackground = addButton(root, "恢复默认日间/夜间背景");
        resetBackground.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetAppBackground();
            }
        });

        Button hiddenApps = addButton(root, "隐藏应用抽屉里的软件");
        hiddenApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DesktopSettingsActivity.this, HiddenAppsActivity.class));
            }
        });

        Button presetBackup = addButton(root, "预设备份 / 导入备份");
        presetBackup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DesktopSettingsActivity.this, PresetBackupActivity.class));
            }
        });

        Button back = addButton(root, "返回我的");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        setContentView(scrollView);
        refreshValues();
    }

    private void rebuildAppThumbCache() {
        Toast.makeText(this, "正在后台更新 APP 缩略图缓存…", Toast.LENGTH_SHORT).show();
        AppDrawerCacheManager.rebuildCacheAsync(this, new AppDrawerCacheManager.RefreshCallback() {
            @Override
            public void onFinished(boolean success, int count) {
                if (success) {
                    Toast.makeText(DesktopSettingsActivity.this, "APP 缩略图缓存已更新：" + count + " 个应用", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(DesktopSettingsActivity.this, "APP 缩略图缓存更新失败", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private TextView addValue(LinearLayout root, String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        tv.setTextColor(Color.rgb(28, 28, 28));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(dp(26), 0, dp(26), 0);
        tv.setSingleLine(false);
        tv.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        );
        lp.setMargins(0, dp(14), 0, dp(10));
        root.addView(tv, lp);
        return tv;
    }

    private Button addButton(LinearLayout root, String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        button.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(78)
        );
        lp.setMargins(0, dp(10), 0, dp(14));
        root.addView(button, lp);
        return button;
    }

    private void refreshValues() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (navValue != null) {
            navValue.setText("默认导航软件： " + sp.getString("nav_label", "高德地图车机版 / com.autonavi.amapauto"));
        }
        if (musicValue != null) {
            musicValue.setText("默认音乐软件： " + sp.getString("music_label", "车机蓝牙音乐 / com.ts.MainUI"));
        }
        if (card1WidgetValue != null) {
            boolean installed = AmapFloatingCardController.isAmapFloatingInstalled(this);
            card1WidgetValue.setText("1号卡片悬浮高德： "
                    + (installed ? "已安装 com.autonavi.amapautoys" : "未安装悬浮版高德地图")
                    + "，桌面将通过广播 com.autonavi.plus.showmap 伪嵌入"
                    + "\n" + AmapFloatingCardController.getSettingsSummary(this));
        }
        if (drawerStyleValue != null) {
            int iconSize = sp.getInt("drawer_icon_size_dp", 72);
            int textSize = sp.getInt("drawer_text_size_sp", 16);
            int columns = sp.getInt("drawer_grid_columns", 6);
            int rows = sp.getInt("drawer_grid_rows", 3);
            drawerStyleValue.setText("应用抽屉显示： " + rows + "×" + columns + "，图标 " + iconSize + "dp，文字 " + textSize + "sp");
        }
        if (iconPackValue != null) {
            iconPackValue.setText("应用图标包： " + IconPackManager.getCurrentIconPackLabel(this));
        }
        if (commonAppsValue != null) {
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (int i = 0; i < 6; i++) {
                String pkg = sp.getString("common_app_" + i + "_pkg", "");
                String label = sp.getString("common_app_" + i + "_label", "");
                if (pkg != null && pkg.length() > 0) {
                    if (sb.length() > 0) sb.append("、");
                    sb.append(label == null || label.length() == 0 ? pkg : label);
                    count++;
                }
            }
            commonAppsValue.setText("4号卡片常用软件： " + (count > 0 ? ("已配置 " + count + " 个（" + sb.toString() + "）") : "未设置"));
        }
        if (weatherValue != null) {
            String city = sp.getString(WeatherProvider.PREF_WEATHER_CITY_NAME, WeatherProvider.DEFAULT_CITY_NAME);
            String code = sp.getString(WeatherProvider.PREF_WEATHER_CITY_CODE, WeatherProvider.DEFAULT_CITY_CODE);
            if ("360300".equals(code) || code == null || !code.startsWith("101")) {
                code = WeatherProvider.DEFAULT_CITY_CODE;
            }
            weatherValue.setText("6号卡片天气： " + city + " / 中国天气ID " + code);
        }
        if (turnSignalValue != null) {
            boolean enabled = sp.getBoolean(VehicleDataProvider.PREF_TURN_SOUND_ENABLED, false);
            String name = sp.getString(VehicleDataProvider.PREF_TURN_SOUND_NAME, "");
            turnSignalValue.setText("转向音： " + (enabled ? "已启用" : "未启用")
                    + "，文件 " + (name == null || name.length() == 0 ? "未选择" : name)
                    + "，数据源 TsCarService 优先");
        }
        if (vehicleHookValue != null) {
            int interval = sp.getInt(VehicleDataProvider.PREF_POLL_INTERVAL_MS, VehicleDataProvider.DEFAULT_POLL_INTERVAL_MS);
            vehicleHookValue.setText("车辆 Hook 数据：轮询 " + interval + "ms，CarInfoService + TsCarService");
        }
        if (hudBroadcastValue != null) {
            boolean enabled = sp.getBoolean(VehicleDataBroadcaster.PREF_HUD_BROADCAST_ENABLED, VehicleDataBroadcaster.DEFAULT_ENABLED);
            String address = sp.getString(VehicleDataBroadcaster.PREF_HUD_BROADCAST_ADDRESS, VehicleDataBroadcaster.DEFAULT_ADDRESS);
            int port = sp.getInt(VehicleDataBroadcaster.PREF_HUD_BROADCAST_PORT, VehicleDataBroadcaster.DEFAULT_PORT);
            int interval = sp.getInt(VehicleDataBroadcaster.PREF_HUD_BROADCAST_INTERVAL_MS, VehicleDataBroadcaster.DEFAULT_INTERVAL_MS);
            hudBroadcastValue.setText("HUD 局域网广播： " + (enabled ? "已启用" : "未启用")
                    + "，UDP " + address + ":" + port + "，" + interval + "ms"
                    + "\n车机IP：" + firstLocalIp());
        }
        if (rearAiVisionValue != null) {
            rearAiVisionValue.setText("后置 AI 视觉节点：" + RearAiVisionController.settingsSummary(this)
                    + "\n视频流固定右半屏 1280×720，UDP 47210 / HTTP 47211");
        }
        if (textDisplayNodeValue != null) {
            textDisplayNodeValue.setText("快捷文字屏节点：" + MikuTextDisplayNodeController.settingsSummary(this)
                    + "\n4号卡片可添加快捷文字 / 喊话文字，点击后发送到后置超长条屏");
        }
        if (nightModeValue != null) {
            int sunrise = sp.getInt(NightModeHelper.PREF_SUNRISE_MIN, NightModeHelper.DEFAULT_SUNRISE_MIN);
            int sunset = sp.getInt(NightModeHelper.PREF_SUNSET_MIN, NightModeHelper.DEFAULT_SUNSET_MIN);
            nightModeValue.setText("夜间模式： " + NightModeHelper.modeName(this)
                    + "，当前显示 " + (NightModeHelper.isNightMode(this) ? "夜间" : "日间")
                    + "，日出 " + NightModeHelper.formatMinute(sunrise)
                    + " / 日落 " + NightModeHelper.formatMinute(sunset));
        }
        if (dayBackgroundValue != null) {
            String label = sp.getString("app_day_background_label", sp.getString("app_background_label", "默认日间背景"));
            dayBackgroundValue.setText("日间背景图片： " + label);
        }
        if (nightBackgroundValue != null) {
            String label = sp.getString("app_night_background_label", "默认夜间背景");
            nightBackgroundValue.setText("夜间背景图片： " + label);
        }
    }

    private void pickAppBackground(int requestCode, String title) {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, requestCode);
        } catch (Throwable t) {
            try {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(Intent.createChooser(intent, title), requestCode);
            } catch (Throwable e) {
                Toast.makeText(this, "无法打开图片选择器", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void resetAppBackground() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .remove("app_background_uri")
                .remove("app_background_label")
                .remove("app_day_background_uri")
                .putString("app_day_background_label", "默认日间背景")
                .remove("app_night_background_uri")
                .putString("app_night_background_label", "默认夜间背景")
                .apply();
        Toast.makeText(this, "已恢复默认日间/夜间背景", Toast.LENGTH_SHORT).show();
        refreshValues();
    }

    private String getDisplayName(Uri uri) {
        if (uri == null) {
            return "自定义背景";
        }
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && name.length() > 0) {
                        return name;
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        String text = uri.toString();
        int slash = text.lastIndexOf('/');
        return slash >= 0 && slash + 1 < text.length() ? text.substring(slash + 1) : "自定义背景";
    }

    private void openBuiltInWidgetPicker() {
        Intent intent = new Intent(this, WidgetPickerActivity.class);
        startActivityForResult(intent, REQ_SELECT_WIDGET_PROVIDER);
    }

    private void bindSelectedWidget(ComponentName provider) {
        pendingProvider = provider;
        pendingWidgetId = appWidgetHost.allocateAppWidgetId();

        boolean bound = false;
        try {
            bound = appWidgetManager.bindAppWidgetIdIfAllowed(pendingWidgetId, provider);
        } catch (Throwable ignored) {
            bound = false;
        }

        if (bound) {
            configureOrSaveWidget(pendingWidgetId);
            return;
        }

        // 没有直接绑定权限时，走系统授权页。部分阉割车机可能没有这个授权页，
        // 这种情况下会提示用户先把本软件设为默认桌面，或在系统里给小组件绑定权限。
        try {
            Intent bindIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
            bindIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId);
            bindIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider);
            startActivityForResult(bindIntent, REQ_BIND_WIDGET);
        } catch (Throwable t) {
            try {
                appWidgetHost.deleteAppWidgetId(pendingWidgetId);
            } catch (Throwable ignored) {
            }
            pendingWidgetId = -1;
            pendingProvider = null;
            Toast.makeText(this, "系统没有提供小组件绑定授权页。请先把本软件设为默认桌面，或允许本软件创建桌面小组件。", Toast.LENGTH_LONG).show();
        }
    }

    private void configureOrSaveWidget(int appWidgetId) {
        AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(appWidgetId);
        if (info == null) {
            saveCard1Widget(appWidgetId, "已选择小组件");
            return;
        }

        if (info.configure != null) {
            Intent configIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            configIntent.setComponent(info.configure);
            configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            pendingWidgetId = appWidgetId;
            try {
                startActivityForResult(configIntent, REQ_CONFIG_WIDGET);
            } catch (Throwable t) {
                saveCard1Widget(appWidgetId, makeWidgetLabel(info));
            }
        } else {
            saveCard1Widget(appWidgetId, makeWidgetLabel(info));
        }
    }

    private String makeWidgetLabel(AppWidgetProviderInfo info) {
        String label = info == null ? "" : info.label;
        if (label == null || label.length() == 0) {
            label = (info != null && info.provider != null) ? info.provider.flattenToShortString() : "已选择小组件";
        }
        return label;
    }

    private void clearCard1Widget() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        int oldId = sp.getInt(MainActivity.PREF_CARD1_WIDGET_ID, -1);
        if (oldId >= 0) {
            try {
                appWidgetHost.deleteAppWidgetId(oldId);
            } catch (Throwable ignored) {
            }
        }
        sp.edit()
                .remove(MainActivity.PREF_CARD1_WIDGET_ID)
                .putString("card1_widget_label", "未选择")
                .apply();
        Toast.makeText(this, "已清除 1号卡片小组件", Toast.LENGTH_SHORT).show();
        refreshValues();
    }

    private void saveCard1Widget(int appWidgetId, String label) {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        int oldId = sp.getInt(MainActivity.PREF_CARD1_WIDGET_ID, -1);
        if (oldId >= 0 && oldId != appWidgetId) {
            try {
                appWidgetHost.deleteAppWidgetId(oldId);
            } catch (Throwable ignored) {
            }
        }

        if (label == null || label.length() == 0) {
            label = "已选择小组件";
        }

        sp.edit()
                .putInt(MainActivity.PREF_CARD1_WIDGET_ID, appWidgetId)
                .putString("card1_widget_label", label)
                .apply();

        Toast.makeText(this, "已设置 1号卡片小组件：" + label, Toast.LENGTH_SHORT).show();
        refreshValues();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        keepFullscreen();

        if (requestCode == REQ_PICK_DAY_BACKGROUND || requestCode == REQ_PICK_NIGHT_BACKGROUND) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                try {
                    final int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    getContentResolver().takePersistableUriPermission(uri, flags);
                } catch (Throwable ignored) {
                }

                SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
                if (requestCode == REQ_PICK_DAY_BACKGROUND) {
                    editor.putString("app_day_background_uri", uri.toString())
                            .putString("app_day_background_label", getDisplayName(uri));
                    Toast.makeText(this, "已更换日间背景", Toast.LENGTH_SHORT).show();
                } else {
                    editor.putString("app_night_background_uri", uri.toString())
                            .putString("app_night_background_label", getDisplayName(uri));
                    Toast.makeText(this, "已更换夜间背景", Toast.LENGTH_SHORT).show();
                }
                editor.apply();
                refreshValues();
            }
            return;
        }

        if (requestCode == REQ_SELECT_WIDGET_PROVIDER) {
            if (resultCode != RESULT_OK || data == null) {
                return;
            }

            String pkg = data.getStringExtra(WidgetPickerActivity.EXTRA_PROVIDER_PACKAGE);
            String cls = data.getStringExtra(WidgetPickerActivity.EXTRA_PROVIDER_CLASS);
            if (pkg == null || cls == null) {
                Toast.makeText(this, "小组件信息无效，请重新选择", Toast.LENGTH_SHORT).show();
                return;
            }

            bindSelectedWidget(new ComponentName(pkg, cls));
            return;
        }

        if (requestCode == REQ_BIND_WIDGET) {
            int appWidgetId = pendingWidgetId;
            if (data != null) {
                appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            }

            if (resultCode == RESULT_OK) {
                configureOrSaveWidget(appWidgetId);
            } else {
                if (appWidgetId >= 0) {
                    try {
                        appWidgetHost.deleteAppWidgetId(appWidgetId);
                    } catch (Throwable ignored) {
                    }
                }
                Toast.makeText(this, "小组件授权被取消，无法放到 1号卡片", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (requestCode == REQ_CONFIG_WIDGET) {
            int appWidgetId = pendingWidgetId;
            if (data != null) {
                appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            }

            if (resultCode == RESULT_OK) {
                AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(appWidgetId);
                saveCard1Widget(appWidgetId, makeWidgetLabel(info));
            } else if (appWidgetId >= 0) {
                try {
                    appWidgetHost.deleteAppWidgetId(appWidgetId);
                } catch (Throwable ignored) {
                }
                Toast.makeText(this, "小组件配置被取消", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void openNotificationListenerSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开通知读取权限设置", Toast.LENGTH_SHORT).show();
        }
    }

    private void openPicker(String target) {
        Intent intent = new Intent(this, AppPickerActivity.class);
        intent.putExtra("target", target);
        startActivity(intent);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    private String firstLocalIp() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : interfaces) {
                if (nif == null || !nif.isUp() || nif.isLoopback()) {
                    continue;
                }
                List<InetAddress> addresses = Collections.list(nif.getInetAddresses());
                for (InetAddress addr : addresses) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "未获取到";
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
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (HomeKeyHelper.handle(this, event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }


}
