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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class CommonAppsSettingsActivity extends Activity {
    private static final String PREFS = MainActivity.PREFS;
    private static final int SLOT_COUNT = 6;

    private final TextView[] valueViews = new TextView[SLOT_COUNT];

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
        title.setText("4号卡片常用软件设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        TextView tip = new TextView(this);
        tip.setText("4号卡片会在首页底部左侧显示常用软件。当前支持 6 个位置，可分别指定应用。空位不会显示。建议把电话、短信、音乐、播客、导航等高频应用放进来。");
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tip.setTextColor(Color.rgb(100, 100, 100));
        tip.setLineSpacing(dp(6), 1f);
        LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tipLp.setMargins(0, dp(6), 0, dp(12));
        root.addView(tip, tipLp);

        for (int i = 0; i < SLOT_COUNT; i++) {
            valueViews[i] = addValue(root, "位置" + (i + 1) + "：");
            final int slot = i;

            Button choose = addButton(root, "选择位置" + (i + 1) + "的应用");
            choose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(CommonAppsSettingsActivity.this, AppPickerActivity.class);
                    intent.putExtra("target", "common_" + slot);
                    startActivity(intent);
                }
            });

            Button clear = addButton(root, "清空位置" + (i + 1));
            clear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
                    editor.remove("common_app_" + slot + "_pkg");
                    editor.remove("common_app_" + slot + "_cls");
                    editor.remove("common_app_" + slot + "_label");
                    editor.apply();
                    refreshValues();
                }
            });
        }

        Button back = addButton(root, "返回桌面设置");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        setContentView(scrollView);
        refreshValues();
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
        lp.setMargins(0, dp(8), 0, dp(10));
        root.addView(button, lp);
        return button;
    }

    private void refreshValues() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        for (int i = 0; i < SLOT_COUNT; i++) {
            String label = sp.getString("common_app_" + i + "_label", "未设置");
            String pkg = sp.getString("common_app_" + i + "_pkg", "");
            if (pkg == null || pkg.length() == 0) {
                valueViews[i].setText("位置" + (i + 1) + "： 未设置");
            } else {
                valueViews[i].setText("位置" + (i + 1) + "： " + label + " / " + pkg);
            }
        }
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
