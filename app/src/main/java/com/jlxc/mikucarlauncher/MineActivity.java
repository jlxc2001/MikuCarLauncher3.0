package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MineActivity extends Activity {
    private static final String PREFS = MainActivity.PREFS;

    private EditText ownerName;
    private EditText carBrand;
    private EditText signature;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keepFullscreen();
        buildUi();
    }

    private void buildUi() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);

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
        title.setText("我的");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        ownerName = addEdit(root, "车主名称", sp.getString("owner_name", "江灵夏草"));
        carBrand = addEdit(root, "汽车品牌", sp.getString("car_brand", "奥迪"));
        signature = addEdit(root, "签名", sp.getString("signature", "MikuCarLauncher"));

        Button save = addButton(root, "保存个人信息");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveProfile();
            }
        });

        Button about = addButton(root, "关于软件");
        about.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(MineActivity.this)
                        .setTitle("关于软件")
                        .setMessage("MikuCarLauncher / A4L 车机桌面\n\n作者：江灵夏草\n\nB站主页：\nhttps://space.bilibili.com/130914376\n\n抖音：JLXC2001\nX（原推特）：jlxc2001\n\n软件介绍：\n这是一款面向第三方安卓车机的自定义车机桌面。当前项目以奥迪 A4L 风格 UI 为基础，整合导航、音乐、车辆界面、360 全景、应用抽屉和车主个性化信息。后续会继续接入车辆实时数据、HUD、战斗模式等功能。")
                        .setPositiveButton("确定", null)
                        .create();
                dialog.show();
            }
        });

        Button desktopSettings = addButton(root, "车机桌面设置");
        desktopSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MineActivity.this, DesktopSettingsActivity.class));
            }
        });

        Button back = addButton(root, "返回首页");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        setContentView(scrollView);
    }

    private EditText addEdit(LinearLayout root, String hint, String value) {
        TextView label = new TextView(this);
        label.setText(hint);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        label.setTextColor(Color.rgb(30, 30, 30));
        label.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(38)
        );
        labelLp.setMargins(0, dp(12), 0, dp(4));
        root.addView(label, labelLp);

        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value);
        edit.setSingleLine(true);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        edit.setPadding(dp(28), 0, dp(28), 0);
        edit.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(82)
        );
        lp.setMargins(0, 0, 0, dp(8));
        root.addView(edit, lp);
        return edit;
    }

    private Button addButton(LinearLayout root, String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(82)
        );
        lp.setMargins(0, dp(12), 0, dp(14));
        root.addView(button, lp);
        return button;
    }

    private void saveProfile() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString("owner_name", ownerName.getText().toString())
                .putString("car_brand", carBrand.getText().toString())
                .putString("signature", signature.getText().toString())
                .apply();
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
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
    protected void onResume() {
        super.onResume();
        keepFullscreen();
    }
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (HomeKeyHelper.handle(this, event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }


}
