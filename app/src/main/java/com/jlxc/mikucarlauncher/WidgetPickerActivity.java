package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
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

public class WidgetPickerActivity extends Activity {
    public static final String EXTRA_PROVIDER_PACKAGE = "provider_package";
    public static final String EXTRA_PROVIDER_CLASS = "provider_class";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keepFullscreen();
        buildUi();
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
        title.setText("选择 1号卡片小组件");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        TextView hint = new TextView(this);
        hint.setText("请选择高德地图车机版的小组件。这里使用本软件自己的小组件选择器，避免系统桌面小组件选择器直接把你带回默认桌面。");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        hint.setTextColor(Color.rgb(90, 90, 90));
        hint.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(70)
        ));

        List<AppWidgetProviderInfo> widgets = AppWidgetManager.getInstance(this).getInstalledProviders();
        Collections.sort(widgets, new Comparator<AppWidgetProviderInfo>() {
            @Override
            public int compare(AppWidgetProviderInfo a, AppWidgetProviderInfo b) {
                String ap = a.provider == null ? "" : a.provider.getPackageName();
                String bp = b.provider == null ? "" : b.provider.getPackageName();

                int aw = isAmap(ap) ? 0 : 1;
                int bw = isAmap(bp) ? 0 : 1;
                if (aw != bw) return aw - bw;

                String al = a.label == null ? "" : a.label;
                String bl = b.label == null ? "" : b.label;
                return al.compareToIgnoreCase(bl);
            }
        });

        if (widgets.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("当前系统没有返回可用的小组件。请确认车机系统支持桌面小组件，并且高德地图车机版已安装。");
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            empty.setTextColor(Color.rgb(50, 50, 50));
            empty.setGravity(Gravity.CENTER_VERTICAL);
            empty.setPadding(dp(26), 0, dp(26), 0);
            empty.setBackgroundColor(Color.WHITE);
            root.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(120)
            ));
        }

        for (final AppWidgetProviderInfo info : widgets) {
            if (info.provider == null) {
                continue;
            }

            TextView row = new TextView(this);
            String label = info.label == null || info.label.length() == 0 ? "未命名小组件" : info.label;
            String provider = info.provider.flattenToShortString();
            String size = "min " + info.minWidth + "×" + info.minHeight + " / resize " + info.minResizeWidth + "×" + info.minResizeHeight;

            row.setText(label + "\n" + provider + "\n" + size);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            row.setTextColor(Color.rgb(28, 28, 28));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(30), dp(12), dp(30), dp(12));
            row.setBackgroundColor(isAmap(info.provider.getPackageName()) ? Color.rgb(232, 243, 255) : Color.WHITE);
            row.setFocusable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent result = new Intent();
                    result.putExtra(EXTRA_PROVIDER_PACKAGE, info.provider.getPackageName());
                    result.putExtra(EXTRA_PROVIDER_CLASS, info.provider.getClassName());
                    setResult(RESULT_OK, result);
                    finish();
                }
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(116)
            );
            lp.setMargins(0, dp(8), 0, dp(12));
            root.addView(row, lp);
        }

        setContentView(scrollView);
    }

    private boolean isAmap(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase();
        return p.contains("autonavi") || p.contains("amap");
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
