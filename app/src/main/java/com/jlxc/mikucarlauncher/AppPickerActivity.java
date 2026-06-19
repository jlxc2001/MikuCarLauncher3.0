package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AppPickerActivity extends Activity {
    private static final String PREFS = MainActivity.PREFS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keepFullscreen();
        buildUi();
    }

    private void buildUi() {
        final String target = getIntent().getStringExtra("target") == null ? "nav" : getIntent().getStringExtra("target");
        final PackageManager pm = getPackageManager();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(46, 34, 46, 46);
        root.setBackgroundColor(Color.rgb(238, 241, 246));

        TextView title = new TextView(this);
        String pageTitle;
        if (target != null && target.startsWith("common_")) {
            int slot = 0;
            try {
                slot = Integer.parseInt(target.substring("common_".length()));
            } catch (Throwable ignored) {
            }
            pageTitle = "选择4号卡片位置" + (slot + 1) + "的应用";
        } else {
            pageTitle = "选择" + ("music".equals(target) ? "默认音乐软件" : "默认导航软件");
        }
        title.setText(pageTitle);
        title.setTextSize(32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 76
        ));

        ScrollView scrollView = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(list);

        Intent queryIntent = new Intent(Intent.ACTION_MAIN, null);
        queryIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(queryIntent, 0);
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                return String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm)));
            }
        });

        for (final ResolveInfo info : apps) {
            final String label = String.valueOf(info.loadLabel(pm));
            final String pkg = info.activityInfo.packageName;
            final String cls = info.activityInfo.name;

            TextView row = new TextView(this);
            row.setText(label + "\n" + pkg);
            row.setTextSize(24);
            row.setTextColor(Color.rgb(28, 28, 28));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(32, 18, 32, 18);
            row.setBackgroundColor(Color.WHITE);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
                    if (target != null && target.startsWith("common_")) {
                        int slot = 0;
                        try {
                            slot = Integer.parseInt(target.substring("common_".length()));
                        } catch (Throwable ignored) {
                        }
                        editor.putString("common_app_" + slot + "_pkg", pkg);
                        editor.putString("common_app_" + slot + "_cls", cls);
                        editor.putString("common_app_" + slot + "_label", label);
                    } else if ("music".equals(target)) {
                        editor.putString("music_package", pkg);
                        editor.putString("music_label", label + " / " + pkg);
                    } else {
                        editor.putString("nav_package", pkg);
                        editor.putString("nav_label", label + " / " + pkg);
                    }
                    editor.apply();
                    finish();
                }
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 96
            );
            lp.setMargins(0, 0, 0, 14);
            list.addView(row, lp);
        }

        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1
        ));
        setContentView(root);
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
