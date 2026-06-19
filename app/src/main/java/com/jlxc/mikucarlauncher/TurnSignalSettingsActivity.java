package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class TurnSignalSettingsActivity extends Activity {
    private static final int REQ_PICK_WAV = 6801;

    private CheckBox enabledCheck;
    private CheckBox debugOverlayCheck;
    private TextView wavValue;
    private TextView hookTipValue;
    private EditText intervalEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keepFullscreen();
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        keepFullscreen();
        refreshValues();
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
        title.setText("转向音 / 转向提示设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        TextView hint = new TextView(this);
        hint.setText("v70 已改为内置 Hook 数据服务，不再猜 baseInfo 索引。\n"
                + "转向状态优先来自 TsCarService：leftTurn code=19，rightTurn code=20。\n"
                + "如果 TsCarService 不可用，才回退到 baseInfo[17]/[18]。");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        hint.setTextColor(Color.rgb(72, 72, 72));
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setSingleLine(false);
        root.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(132)
        ));

        enabledCheck = addCheck(root, "启用转向音");
        debugOverlayCheck = addCheck(root, "显示转向调试浮层");

        wavValue = addValue(root, "WAV 文件：未选择");
        hookTipValue = addValue(root, "数据源：Hook 服务");

        Button choose = addButton(root, "选择转向音 WAV 文件");
        choose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickWav();
            }
        });

        Button clear = addButton(root, "清除转向音文件");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                        .remove(VehicleDataProvider.PREF_TURN_SOUND_URI)
                        .remove(VehicleDataProvider.PREF_TURN_SOUND_NAME)
                        .apply();
                refreshValues();
                Toast.makeText(TurnSignalSettingsActivity.this, "已清除转向音文件", Toast.LENGTH_SHORT).show();
            }
        });

        intervalEdit = addEdit(root, "车辆数据轮询间隔 ms（500~10000；默认 650）",
                String.valueOf(getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                        .getInt(VehicleDataProvider.PREF_POLL_INTERVAL_MS, VehicleDataProvider.DEFAULT_POLL_INTERVAL_MS)));

        Button save = addButton(root, "保存转向音 / 调试 / 轮询设置");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveSettings();
            }
        });

        Button openHook = addButton(root, "查看 Hook 原始数据 / 可读状态");
        openHook.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(TurnSignalSettingsActivity.this, VehicleHookSettingsActivity.class));
            }
        });

        Button back = addButton(root, "返回车机桌面设置");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        setContentView(scrollView);
        refreshValues();
    }

    private CheckBox addCheck(LinearLayout root, String text) {
        CheckBox check = new CheckBox(this);
        check.setText(text);
        check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        check.setTextColor(Color.rgb(28, 28, 28));
        check.setGravity(Gravity.CENTER_VERTICAL);
        check.setPadding(dp(26), 0, dp(26), 0);
        check.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        );
        lp.setMargins(0, 0, 0, dp(10));
        root.addView(check, lp);
        return check;
    }

    private void pickWav() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_WAV);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_WAV && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) {
            }
            String name = queryDisplayName(uri);
            getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                    .putString(VehicleDataProvider.PREF_TURN_SOUND_URI, uri.toString())
                    .putString(VehicleDataProvider.PREF_TURN_SOUND_NAME, name)
                    .apply();
            refreshValues();
            Toast.makeText(this, "已选择转向音：" + name, Toast.LENGTH_SHORT).show();
        }
    }

    private void saveSettings() {
        int interval = parseInt(intervalEdit, VehicleDataProvider.DEFAULT_POLL_INTERVAL_MS);
        if (interval < 500) interval = 500;
        if (interval > 10000) interval = 10000;

        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                .putBoolean(VehicleDataProvider.PREF_TURN_SOUND_ENABLED, enabledCheck.isChecked())
                .putBoolean(VehicleDataProvider.PREF_TURN_DEBUG_OVERLAY, debugOverlayCheck.isChecked())
                .putInt(VehicleDataProvider.PREF_POLL_INTERVAL_MS, interval)
                .apply();

        refreshValues();
        Toast.makeText(this, "已保存转向音设置", Toast.LENGTH_SHORT).show();
    }

    private void refreshValues() {
        SharedPreferences sp = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        if (enabledCheck != null) {
            enabledCheck.setChecked(sp.getBoolean(VehicleDataProvider.PREF_TURN_SOUND_ENABLED, false));
        }
        if (debugOverlayCheck != null) {
            debugOverlayCheck.setChecked(sp.getBoolean(VehicleDataProvider.PREF_TURN_DEBUG_OVERLAY, false));
        }
        if (wavValue != null) {
            String name = sp.getString(VehicleDataProvider.PREF_TURN_SOUND_NAME, "");
            String uri = sp.getString(VehicleDataProvider.PREF_TURN_SOUND_URI, "");
            wavValue.setText("WAV 文件： " + (uri == null || uri.length() == 0 ? "未选择" : name));
        }
        if (hookTipValue != null) {
            hookTipValue.setText("数据源：TsCarService 优先，失败后回退 baseInfo[17]/[18]");
        }
        if (intervalEdit != null) {
            intervalEdit.setText(String.valueOf(sp.getInt(VehicleDataProvider.PREF_POLL_INTERVAL_MS, VehicleDataProvider.DEFAULT_POLL_INTERVAL_MS)));
        }
    }

    private TextView addValue(LinearLayout root, String text) {
        TextView value = new TextView(this);
        value.setText(text);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        value.setTextColor(Color.rgb(35, 35, 35));
        value.setGravity(Gravity.CENTER_VERTICAL);
        value.setSingleLine(false);
        value.setPadding(dp(26), 0, dp(26), 0);
        value.setBackgroundColor(Color.WHITE);
        root.addView(value, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(88)
        ));
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

    private String queryDisplayName(Uri uri) {
        String fallback = uri == null ? "turn_signal.wav" : uri.getLastPathSegment();
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && name.length() > 0) return name;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return fallback == null ? "turn_signal.wav" : fallback;
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
        if (HomeKeyHelper.handle(this, event)) return true;
        return super.dispatchKeyEvent(event);
    }
}
