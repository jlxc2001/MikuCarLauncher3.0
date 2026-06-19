package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private static final String PREFS = "miku_car_launcher_settings";

    private TextView navValue;
    private TextView musicValue;
    private EditText ownerName;
    private EditText plateNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keepFullscreen();
        buildUi();
    }

    private void buildUi() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(42, 30, 42, 30);
        root.setBackgroundColor(Color.rgb(238, 241, 246));

        TextView title = new TextView(this);
        title.setText("我的 / 设置");
        title.setTextSize(26);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 56
        ));

        navValue = addValueRow(root, "默认导航软件", sp.getString("nav_label", "高德地图车机版 / com.autonavi.amapauto"));
        Button chooseNav = addButton(root, "选择默认导航软件");
        chooseNav.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPicker("nav");
            }
        });

        musicValue = addValueRow(root, "默认音乐软件", sp.getString("music_label", "车机蓝牙音乐 / com.ts.MainUI"));
        Button chooseMusic = addButton(root, "选择默认音乐软件");
        chooseMusic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPicker("music");
            }
        });

        ownerName = addEdit(root, "车主名字", sp.getString("owner_name", "奥迪"));
        plateNumber = addEdit(root, "汽车牌照", sp.getString("plate_number", "A4L"));

        Button save = addButton(root, "保存设置");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });

        Button back = addButton(root, "返回首页");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        setContentView(root);
    }

    private TextView addValueRow(LinearLayout root, String label, String value) {
        TextView tv = new TextView(this);
        tv.setText(label + "： " + value);
        tv.setTextSize(20);
        tv.setTextColor(Color.rgb(28, 28, 28));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(22, 0, 22, 0);
        tv.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 54
        );
        lp.setMargins(0, 12, 0, 8);
        root.addView(tv, lp);
        return tv;
    }

    private EditText addEdit(LinearLayout root, String hint, String value) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value);
        edit.setSingleLine(true);
        edit.setTextSize(20);
        edit.setPadding(22, 0, 22, 0);
        edit.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 56
        );
        lp.setMargins(0, 12, 0, 8);
        root.addView(edit, lp);
        return edit;
    }

    private Button addButton(LinearLayout root, String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 56
        );
        lp.setMargins(0, 8, 0, 8);
        root.addView(button, lp);
        return button;
    }

    private void openPicker(String target) {
        Intent intent = new Intent(this, AppPickerActivity.class);
        intent.putExtra("target", target);
        startActivity(intent);
    }

    private void saveSettings() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString("owner_name", ownerName.getText().toString())
                .putString("plate_number", plateNumber.getText().toString())
                .apply();
        finish();
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
    protected void onResume() {
        super.onResume();
        keepFullscreen();
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (navValue != null) navValue.setText("默认导航软件： " + sp.getString("nav_label", "高德地图车机版 / com.autonavi.amapauto"));
        if (musicValue != null) musicValue.setText("默认音乐软件： " + sp.getString("music_label", "车机蓝牙音乐 / com.ts.MainUI"));
    }
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (HomeKeyHelper.handle(this, event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }


}
