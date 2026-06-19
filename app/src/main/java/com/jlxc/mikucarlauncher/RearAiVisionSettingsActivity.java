package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
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

public class RearAiVisionSettingsActivity extends Activity {
    private static final String PREFS = MainActivity.PREFS;
    private static final int REQ_LEFT_AUDIO = 6101;
    private static final int REQ_RIGHT_AUDIO = 6102;

    private CheckBox enabledBox;
    private EditText ipEdit;
    private EditText hideDelayEdit;
    private TextView summaryValue;
    private TextView leftAudioValue;
    private TextView rightAudioValue;

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
        root.setPadding(dp(46), dp(34), dp(46), dp(46));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("后置 AI 视觉节点设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        TextView desc = addValue(root, "后置手机 App：com.jlxc.vehicleinfoncnn\nUDP 控制端口：47210\nHTTP / MJPEG 端口：47211\n视频区域：车机右半屏 1280×720\n状态 500ms 超时按安全处理，避免误报警。", dp(150));
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);

        enabledBox = new CheckBox(this);
        enabledBox.setText("启用后置 AI 视觉节点");
        enabledBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        enabledBox.setTextColor(Color.rgb(25, 25, 25));
        enabledBox.setGravity(Gravity.CENTER_VERTICAL);
        enabledBox.setPadding(dp(24), 0, dp(24), 0);
        enabledBox.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(78));
        cbLp.setMargins(0, dp(12), 0, dp(12));
        root.addView(enabledBox, cbLp);

        summaryValue = addValue(root, "当前状态：", dp(96));

        TextView ipLabel = addSmallLabel(root, "后置手机 IP，例如 192.168.100.127");
        ipEdit = new EditText(this);
        ipEdit.setSingleLine(true);
        ipEdit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        ipEdit.setHint("后置手机 IP");
        ipEdit.setPadding(dp(24), 0, dp(24), 0);
        ipEdit.setBackgroundColor(Color.WHITE);
        root.addView(ipEdit, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(76)));

        addSmallLabel(root, "转向关闭后的隐藏延迟，单位秒，建议 1~3 秒");
        hideDelayEdit = new EditText(this);
        hideDelayEdit.setSingleLine(true);
        hideDelayEdit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        hideDelayEdit.setHint(String.valueOf(RearAiVisionController.DEFAULT_HIDE_DELAY_SEC));
        hideDelayEdit.setPadding(dp(24), 0, dp(24), 0);
        hideDelayEdit.setBackgroundColor(Color.WHITE);
        root.addView(hideDelayEdit, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(76)));

        leftAudioValue = addValue(root, "左侧风险提示音：", dp(76));
        Button pickLeftAudio = addButton(root, "选择左侧风险提示音");
        pickLeftAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickAudio(REQ_LEFT_AUDIO, "选择左侧风险提示音");
            }
        });

        rightAudioValue = addValue(root, "右侧风险提示音：", dp(76));
        Button pickRightAudio = addButton(root, "选择右侧风险提示音");
        pickRightAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickAudio(REQ_RIGHT_AUDIO, "选择右侧风险提示音");
            }
        });

        Button save = addButton(root, "保存设置");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveSettings();
            }
        });

        Button ping = addButton(root, "保存并发送 PING 测试");
        ping.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveSettings();
                RearAiVisionController controller = new RearAiVisionController(
                        RearAiVisionSettingsActivity.this,
                        null,
                        null,
                        null,
                        null
                );
                controller.sendPing();
                Toast.makeText(RearAiVisionSettingsActivity.this, "已向后置手机发送 PING", Toast.LENGTH_SHORT).show();
            }
        });

        TextView adb = addValue(root,
                "ADB / 网络调试：\n"
                        + "UDP 指令：TURN_LEFT / TURN_RIGHT / TURN_OFF / PING\n"
                        + "视频流：http://后置手机IP:47211/stream\n"
                        + "状态：http://后置手机IP:47211/status",
                dp(150)
        );
        adb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);

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

    private void refreshValues() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (enabledBox != null) {
            enabledBox.setChecked(sp.getBoolean(RearAiVisionController.PREF_ENABLED, true));
        }
        if (ipEdit != null) {
            ipEdit.setText(sp.getString(RearAiVisionController.PREF_IP, ""));
        }
        if (hideDelayEdit != null) {
            hideDelayEdit.setText(String.valueOf(sp.getInt(RearAiVisionController.PREF_HIDE_DELAY_SEC, RearAiVisionController.DEFAULT_HIDE_DELAY_SEC)));
        }
        if (summaryValue != null) {
            summaryValue.setText("当前状态：" + RearAiVisionController.settingsSummary(this));
        }
        if (leftAudioValue != null) {
            leftAudioValue.setText("左侧风险提示音：" + sp.getString(RearAiVisionController.PREF_LEFT_AUDIO_NAME, "未选择"));
        }
        if (rightAudioValue != null) {
            rightAudioValue.setText("右侧风险提示音：" + sp.getString(RearAiVisionController.PREF_RIGHT_AUDIO_NAME, "未选择"));
        }
    }

    private void saveSettings() {
        String ip = ipEdit == null ? "" : ipEdit.getText().toString().trim();
        int delay = RearAiVisionController.DEFAULT_HIDE_DELAY_SEC;
        try {
            delay = Integer.parseInt(hideDelayEdit.getText().toString().trim());
        } catch (Throwable ignored) {
        }
        if (delay < 0) delay = 0;
        if (delay > 30) delay = 30;

        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(RearAiVisionController.PREF_ENABLED, enabledBox == null || enabledBox.isChecked())
                .putString(RearAiVisionController.PREF_IP, ip)
                .putInt(RearAiVisionController.PREF_HIDE_DELAY_SEC, delay)
                .apply();
        Toast.makeText(this, "后置 AI 视觉节点设置已保存", Toast.LENGTH_SHORT).show();
        refreshValues();
    }

    private void pickAudio(int requestCode, String title) {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("audio/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, requestCode);
        } catch (Throwable t) {
            try {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("audio/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(Intent.createChooser(intent, title), requestCode);
            } catch (Throwable e) {
                Toast.makeText(this, "无法打开音频选择器", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        keepFullscreen();
        if ((requestCode == REQ_LEFT_AUDIO || requestCode == REQ_RIGHT_AUDIO) && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                final int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Throwable ignored) {
            }
            String name = getDisplayName(uri);
            SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
            if (requestCode == REQ_LEFT_AUDIO) {
                editor.putString(RearAiVisionController.PREF_LEFT_AUDIO_URI, uri.toString())
                        .putString(RearAiVisionController.PREF_LEFT_AUDIO_NAME, name);
            } else {
                editor.putString(RearAiVisionController.PREF_RIGHT_AUDIO_URI, uri.toString())
                        .putString(RearAiVisionController.PREF_RIGHT_AUDIO_NAME, name);
            }
            editor.apply();
            Toast.makeText(this, "已选择提示音：" + name, Toast.LENGTH_SHORT).show();
            refreshValues();
        }
    }

    private TextView addSmallLabel(LinearLayout root, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tv.setTextColor(Color.rgb(90, 95, 105));
        tv.setPadding(dp(8), dp(12), dp(8), dp(6));
        root.addView(tv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    private TextView addValue(LinearLayout root, String text, int height) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tv.setTextColor(Color.rgb(28, 28, 28));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(dp(26), 0, dp(26), 0);
        tv.setSingleLine(false);
        tv.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height);
        lp.setMargins(0, dp(12), 0, dp(10));
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(78));
        lp.setMargins(0, dp(10), 0, dp(14));
        root.addView(button, lp);
        return button;
    }

    private String getDisplayName(Uri uri) {
        if (uri == null) return "音频文件";
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
        String text = uri.toString();
        int slash = text.lastIndexOf('/');
        return slash >= 0 && slash + 1 < text.length() ? text.substring(slash + 1) : "音频文件";
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
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
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (HomeKeyHelper.isBackKey(keyCode) || HomeKeyHelper.isHomeKey(keyCode)) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
