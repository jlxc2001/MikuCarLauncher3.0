package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
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

public class MikuTextDisplayNodeSettingsActivity extends Activity {
    private static final String PREFS = MainActivity.PREFS;

    private CheckBox enabledBox;
    private EditText ipEdit;
    private EditText debounceEdit;
    private TextView summaryValue;

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
        loadValues();
        refreshSummary();
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
        title.setText("快捷文字屏节点 / MikuTextDisplayNode");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        TextView tip = new TextView(this);
        tip.setText("用于向后置超长条文字屏发送快捷文字。接收端包名：com.jlxc.mikutextdisplay，UDP 47230，HTTP 47231。优先 UDP，失败时 SHOW/CLEAR 会尝试 HTTP 备用接口。");
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tip.setTextColor(Color.rgb(90, 90, 90));
        tip.setLineSpacing(dp(5), 1f);
        LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tipLp.setMargins(0, dp(4), 0, dp(14));
        root.addView(tip, tipLp);

        summaryValue = addValue(root, "当前状态：");

        enabledBox = new CheckBox(this);
        enabledBox.setText("启用快捷文字屏节点");
        enabledBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        enabledBox.setTextColor(Color.rgb(20, 20, 20));
        enabledBox.setGravity(Gravity.CENTER_VERTICAL);
        enabledBox.setPadding(dp(22), 0, dp(22), 0);
        enabledBox.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76));
        cbLp.setMargins(0, dp(10), 0, dp(10));
        root.addView(enabledBox, cbLp);

        ipEdit = addEdit(root, "文字屏 IP，例如 192.168.100.128", InputType.TYPE_CLASS_TEXT);
        debounceEdit = addEdit(root, "相同文字去抖 ms，建议 300~1000，默认 500", InputType.TYPE_CLASS_NUMBER);

        Button save = addButton(root, "保存设置");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveValues();
                Toast.makeText(MikuTextDisplayNodeSettingsActivity.this, "已保存快捷文字屏节点设置", Toast.LENGTH_SHORT).show();
                refreshSummary();
            }
        });

        Button ping = addButton(root, "发送 PING 测试在线");
        ping.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveValues();
                MikuTextDisplayNodeController.sendPing(MikuTextDisplayNodeSettingsActivity.this);
                Toast.makeText(MikuTextDisplayNodeSettingsActivity.this, "已发送 PING", Toast.LENGTH_SHORT).show();
            }
        });

        Button testShow = addButton(root, "测试显示：左方扫描");
        testShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveValues();
                MikuTextDisplayNodeController.sendShow(MikuTextDisplayNodeSettingsActivity.this, "左方扫描");
                Toast.makeText(MikuTextDisplayNodeSettingsActivity.this, "已发送 SHOW:左方扫描", Toast.LENGTH_SHORT).show();
            }
        });

        Button clear = addButton(root, "清空文字屏");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveValues();
                MikuTextDisplayNodeController.sendClear(MikuTextDisplayNodeSettingsActivity.this);
                Toast.makeText(MikuTextDisplayNodeSettingsActivity.this, "已发送 CLEAR", Toast.LENGTH_SHORT).show();
            }
        });

        TextView adb = new TextView(this);
        adb.setText("ADB / 局域网协议示例：\n"
                + "UDP UTF-8：SHOW:左方扫描 / CLEAR / PING\n"
                + "HTTP：http://屏幕IP:47231/show?text=URL编码后的文字\n"
                + "HTTP：http://屏幕IP:47231/clear");
        adb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        adb.setTextColor(Color.rgb(70, 70, 70));
        adb.setLineSpacing(dp(4), 1f);
        adb.setPadding(dp(24), dp(18), dp(24), dp(18));
        adb.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams adbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        adbLp.setMargins(0, dp(10), 0, dp(14));
        root.addView(adb, adbLp);

        Button back = addButton(root, "返回桌面设置");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        setContentView(scrollView);
        loadValues();
        refreshSummary();
    }

    private void loadValues() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (enabledBox != null) enabledBox.setChecked(sp.getBoolean(MikuTextDisplayNodeController.PREF_ENABLED, false));
        if (ipEdit != null) ipEdit.setText(sp.getString(MikuTextDisplayNodeController.PREF_IP, ""));
        if (debounceEdit != null) debounceEdit.setText(String.valueOf(sp.getInt(MikuTextDisplayNodeController.PREF_DEBOUNCE_MS, MikuTextDisplayNodeController.DEFAULT_DEBOUNCE_MS)));
    }

    private void saveValues() {
        String ip = ipEdit == null ? "" : ipEdit.getText().toString().trim();
        int debounce = MikuTextDisplayNodeController.DEFAULT_DEBOUNCE_MS;
        try {
            debounce = Integer.parseInt(debounceEdit.getText().toString().trim());
        } catch (Throwable ignored) {
        }
        debounce = Math.max(300, Math.min(1000, debounce));
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(MikuTextDisplayNodeController.PREF_ENABLED, enabledBox != null && enabledBox.isChecked())
                .putString(MikuTextDisplayNodeController.PREF_IP, ip)
                .putInt(MikuTextDisplayNodeController.PREF_DEBOUNCE_MS, debounce)
                .apply();
    }

    private void refreshSummary() {
        if (summaryValue != null) {
            summaryValue.setText("当前状态：" + MikuTextDisplayNodeController.settingsSummary(this));
        }
    }

    private TextView addValue(LinearLayout root, String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tv.setTextColor(Color.rgb(28, 28, 28));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(dp(26), 0, dp(26), 0);
        tv.setSingleLine(false);
        tv.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(82));
        lp.setMargins(0, dp(10), 0, dp(10));
        root.addView(tv, lp);
        return tv;
    }

    private EditText addEdit(LinearLayout root, String hint, int inputType) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        edit.setSingleLine(true);
        edit.setInputType(inputType);
        edit.setPadding(dp(24), 0, dp(24), 0);
        edit.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(78));
        lp.setMargins(0, dp(8), 0, dp(10));
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
                LinearLayout.LayoutParams.MATCH_PARENT, dp(78));
        lp.setMargins(0, dp(8), 0, dp(10));
        root.addView(button, lp);
        return button;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
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
