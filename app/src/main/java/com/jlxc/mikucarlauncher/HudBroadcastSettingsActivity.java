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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class HudBroadcastSettingsActivity extends Activity {
    private CheckBox enabledCheck;
    private EditText addressEdit;
    private EditText portEdit;
    private EditText intervalEdit;
    private TextView ipValue;
    private TextView infoValue;

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
        title.setText("HUD 数据广播设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        TextView hint = new TextView(this);
        hint.setText("用于旧手机 HUD 接收端在同一局域网内接收车辆 Hook 数据。\n"
                + "协议：UDP JSON，默认广播地址 255.255.255.255，端口 36970。\n"
                + "HUD 端只需要监听 UDP 36970 并解析 protocol=MikuCarHUD 的 JSON。");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        hint.setTextColor(Color.rgb(72, 72, 72));
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setSingleLine(false);
        root.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(136)
        ));

        ipValue = addValue(root, "车机当前 IP：正在获取…");

        Button refreshIp = addButton(root, "刷新车机 IP 显示");
        refreshIp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refreshValues();
                Toast.makeText(HudBroadcastSettingsActivity.this, "已刷新 IP", Toast.LENGTH_SHORT).show();
            }
        });

        enabledCheck = addCheck(root, "启用 HUD 局域网广播");

        addressEdit = addEdit(root, "广播地址（默认 255.255.255.255；也可以填旧手机 IP 定向发送）",
                VehicleDataBroadcaster.DEFAULT_ADDRESS,
                InputType.TYPE_CLASS_TEXT);

        portEdit = addEdit(root, "UDP 端口（1024~65535，默认 36970）",
                String.valueOf(VehicleDataBroadcaster.DEFAULT_PORT),
                InputType.TYPE_CLASS_NUMBER);

        intervalEdit = addEdit(root, "广播间隔 ms（100~5000，默认 200）",
                String.valueOf(VehicleDataBroadcaster.DEFAULT_INTERVAL_MS),
                InputType.TYPE_CLASS_NUMBER);

        infoValue = addValue(root, "广播数据字段：speedKmh、rpm、rangeKm、doors、leftTurn、rightTurn、radar、rawBaseInfo 等");

        Button save = addButton(root, "保存 HUD 广播设置");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveSettings();
            }
        });

        Button defaultBtn = addButton(root, "恢复默认广播设置");
        defaultBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enabledCheck.setChecked(VehicleDataBroadcaster.DEFAULT_ENABLED);
                addressEdit.setText(VehicleDataBroadcaster.DEFAULT_ADDRESS);
                portEdit.setText(String.valueOf(VehicleDataBroadcaster.DEFAULT_PORT));
                intervalEdit.setText(String.valueOf(VehicleDataBroadcaster.DEFAULT_INTERVAL_MS));
                saveSettings();
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

    private TextView addValue(LinearLayout root, String text) {
        TextView value = new TextView(this);
        value.setText(text);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        value.setTextColor(Color.rgb(35, 35, 35));
        value.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        value.setSingleLine(false);
        value.setPadding(dp(26), 0, dp(26), 0);
        value.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(104)
        );
        lp.setMargins(0, dp(12), 0, dp(14));
        root.addView(value, lp);
        return value;
    }

    private EditText addEdit(LinearLayout root, String label, String value, int inputType) {
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
        edit.setInputType(inputType);

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

    private void saveSettings() {
        int port = parseInt(portEdit, VehicleDataBroadcaster.DEFAULT_PORT);
        if (port < 1024 || port > 65535) port = VehicleDataBroadcaster.DEFAULT_PORT;

        int interval = parseInt(intervalEdit, VehicleDataBroadcaster.DEFAULT_INTERVAL_MS);
        if (interval < 100) interval = 100;
        if (interval > 5000) interval = 5000;

        String address = addressEdit.getText().toString().trim();
        if (address.length() == 0) address = VehicleDataBroadcaster.DEFAULT_ADDRESS;

        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                .putBoolean(VehicleDataBroadcaster.PREF_HUD_BROADCAST_ENABLED, enabledCheck.isChecked())
                .putString(VehicleDataBroadcaster.PREF_HUD_BROADCAST_ADDRESS, address)
                .putInt(VehicleDataBroadcaster.PREF_HUD_BROADCAST_PORT, port)
                .putInt(VehicleDataBroadcaster.PREF_HUD_BROADCAST_INTERVAL_MS, interval)
                .apply();

        refreshValues();
        Toast.makeText(this, "已保存 HUD 广播设置", Toast.LENGTH_SHORT).show();
    }

    private void refreshValues() {
        SharedPreferences sp = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        if (ipValue != null) {
            ipValue.setText("车机当前 IP：\n" + getLocalIpSummary()
                    + "\n\nHUD 端如果需要手动填写 IP，请填写上面和旧手机处于同一 Wi-Fi / 热点网段的 IPv4 地址。");
        }
        if (enabledCheck != null) {
            enabledCheck.setChecked(sp.getBoolean(VehicleDataBroadcaster.PREF_HUD_BROADCAST_ENABLED,
                    VehicleDataBroadcaster.DEFAULT_ENABLED));
        }
        if (addressEdit != null) {
            addressEdit.setText(sp.getString(VehicleDataBroadcaster.PREF_HUD_BROADCAST_ADDRESS,
                    VehicleDataBroadcaster.DEFAULT_ADDRESS));
        }
        if (portEdit != null) {
            portEdit.setText(String.valueOf(sp.getInt(VehicleDataBroadcaster.PREF_HUD_BROADCAST_PORT,
                    VehicleDataBroadcaster.DEFAULT_PORT)));
        }
        if (intervalEdit != null) {
            intervalEdit.setText(String.valueOf(sp.getInt(VehicleDataBroadcaster.PREF_HUD_BROADCAST_INTERVAL_MS,
                    VehicleDataBroadcaster.DEFAULT_INTERVAL_MS)));
        }
        if (infoValue != null) {
            infoValue.setText("协议：UDP JSON / protocol=MikuCarHUD / version=1\n"
                    + "默认：" + VehicleDataBroadcaster.DEFAULT_ADDRESS + ":" + VehicleDataBroadcaster.DEFAULT_PORT
                    + " / " + VehicleDataBroadcaster.DEFAULT_INTERVAL_MS + "ms\n"
                    + "字段：speedKmh, rpm, rangeKm, fuelLevel, doors, leftTurn, rightTurn, highBeam, hazard, radar, rawBaseInfo");
        }
    }

    private String getLocalIpSummary() {
        StringBuilder sb = new StringBuilder();
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : interfaces) {
                if (nif == null || !nif.isUp() || nif.isLoopback()) {
                    continue;
                }

                String name = nif.getName();
                List<InetAddress> addresses = Collections.list(nif.getInetAddresses());
                for (InetAddress addr : addresses) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        if (sb.length() > 0) {
                            sb.append("\n");
                        }
                        sb.append(name).append(" : ").append(addr.getHostAddress());
                    }
                }
            }
        } catch (Throwable t) {
            return "获取失败：" + t.getClass().getSimpleName();
        }

        if (sb.length() == 0) {
            return "未获取到 IPv4。请确认车机已连接 Wi-Fi 或手机热点。";
        }

        return sb.toString();
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
        if (HomeKeyHelper.handle(this, event)) return true;
        return super.dispatchKeyEvent(event);
    }
}
