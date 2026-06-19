package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;

public class VehicleHookSettingsActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private VehicleDataProvider provider;

    private EditText intervalEdit;
    private TextView readableValue;
    private TextView rawValue;
    private TextView debugValue;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshDataViews();
            handler.postDelayed(this, 500L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keepFullscreen();
        provider = new VehicleDataProvider(this);
        provider.start();
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        keepFullscreen();
        handler.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refreshRunnable);
        if (provider != null) {
            provider.stop();
        }
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(238, 241, 246));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(46), dp(34), dp(46), dp(54));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("车辆 Hook 数据 / 轮询设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        TextView hint = new TextView(this);
        hint.setText("这里显示 Launcher 内置 Hook 服务读取到的数据。\n"
                + "数据源与测试 Demo 一致：CarInfoService requestCarBaseInfo + TsCarService 补充信号。\n"
                + "转向、双闪、总里程、雷达等优先使用 TsCarService；车门、续航、转速等来自 baseInfo。");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        hint.setTextColor(Color.rgb(72, 72, 72));
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setSingleLine(false);
        root.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(136)
        ));

        intervalEdit = addEdit(root, "车辆数据轮询间隔 ms（500~10000；默认 650，越低越实时但越吃 MainApp）",
                String.valueOf(getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                        .getInt(VehicleDataProvider.PREF_POLL_INTERVAL_MS, VehicleDataProvider.DEFAULT_POLL_INTERVAL_MS)));

        Button save = addButton(root, "保存轮询率");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveInterval();
            }
        });

        Button default650 = addButton(root, "恢复默认 650ms");
        default650.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                intervalEdit.setText(String.valueOf(VehicleDataProvider.DEFAULT_POLL_INTERVAL_MS));
                saveInterval();
            }
        });

        Button safe1000 = addButton(root, "保守模式 1000ms");
        safe1000.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                intervalEdit.setText("1000");
                saveInterval();
            }
        });

        Button hudBroadcast = addButton(root, "HUD 数据广播设置");
        hudBroadcast.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new android.content.Intent(VehicleHookSettingsActivity.this, HudBroadcastSettingsActivity.class));
            }
        });

        readableValue = addValue(root, "可读状态：等待车辆数据…", dp(390));
        rawValue = addValue(root, "原始 baseInfo：等待车辆数据…", dp(260));
        debugValue = addValue(root, "调试数据：等待车辆数据…", dp(190));

        Button back = addButton(root, "返回车机桌面设置");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        setContentView(scrollView);
        refreshDataViews();
    }

    private void saveInterval() {
        int value = parseInt(intervalEdit, VehicleDataProvider.DEFAULT_POLL_INTERVAL_MS);
        if (value < 500) value = 500;
        if (value > 10000) value = 10000;

        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                .putInt(VehicleDataProvider.PREF_POLL_INTERVAL_MS, value)
                .apply();

        intervalEdit.setText(String.valueOf(value));
        Toast.makeText(this, "已保存轮询间隔：" + value + "ms", Toast.LENGTH_SHORT).show();

        if (provider != null) {
            provider.stop();
            provider.start();
        }
    }

    private void refreshDataViews() {
        if (provider == null) return;

        VehicleDataProvider.Snapshot s = provider.getSnapshot();
        if (s == null || !s.valid) {
            if (readableValue != null) readableValue.setText("可读状态：暂无有效车辆数据\n\n"
                    + (s == null ? "" : s.debugText));
            return;
        }

        if (readableValue != null) {
            readableValue.setText("可读状态：\n" + s.readableText);
        }
        if (rawValue != null) {
            rawValue.setText("原始 baseInfo：\n" + (s.rawBaseInfo == null ? "null" : Arrays.toString(s.rawBaseInfo))
                    + "\n\nfrontRadar=" + (s.frontRadar == null ? "null" : Arrays.toString(s.frontRadar))
                    + "\nrearRadar=" + (s.rearRadar == null ? "null" : Arrays.toString(s.rearRadar)));
        }
        if (debugValue != null) {
            debugValue.setText("调试数据：\n" + s.debugText);
        }
    }

    private TextView addValue(LinearLayout root, String text, int height) {
        TextView value = new TextView(this);
        value.setText(text);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        value.setTextColor(Color.rgb(35, 35, 35));
        value.setGravity(Gravity.LEFT | Gravity.TOP);
        value.setSingleLine(false);
        value.setPadding(dp(22), dp(18), dp(22), dp(18));
        value.setBackgroundColor(Color.WHITE);
        value.setTypeface(android.graphics.Typeface.MONOSPACE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, height
        );
        lp.setMargins(0, dp(12), 0, dp(14));
        root.addView(value, lp);
        return value;
    }

    private EditText addEdit(LinearLayout root, String label, String value) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tv.setTextColor(Color.rgb(70, 70, 70));
        tv.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        root.addView(tv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54)
        ));

        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setText(value);
        edit.setSelectAllOnFocus(true);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        edit.setTextColor(Color.rgb(20, 20, 20));
        edit.setPadding(dp(22), 0, dp(22), 0);
        edit.setBackgroundColor(Color.WHITE);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        );
        lp.setMargins(0, dp(6), 0, dp(12));
        root.addView(edit, lp);
        return edit;
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

    private int parseInt(EditText edit, int fallback) {
        try {
            return Integer.parseInt(edit.getText().toString().trim());
        } catch (Throwable ignored) {
            return fallback;
        }
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

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (HomeKeyHelper.handle(this, event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
