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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HiddenAppsActivity extends Activity {
    private static final String PREFS = MainActivity.PREFS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keepFullscreen();
        buildUi();
    }

    private void buildUi() {
        final PackageManager pm = getPackageManager();
        final SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        final Set<String> hidden = new HashSet<String>(sp.getStringSet("hidden_apps", new HashSet<String>()));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(46, 34, 46, 46);
        root.setBackgroundColor(Color.rgb(238, 241, 246));

        TextView title = new TextView(this);
        title.setText("隐藏应用抽屉里的软件");
        title.setTextSize(32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 76
        ));

        TextView hint = new TextView(this);
        hint.setText("点击应用即可切换显示/隐藏。隐藏后不会出现在“应用”抽屉里。");
        hint.setTextSize(20);
        hint.setTextColor(Color.rgb(80, 80, 80));
        hint.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 56
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

            final TextView row = new TextView(this);
            row.setText(makeRowText(label, pkg, hidden.contains(pkg)));
            row.setTextSize(24);
            row.setTextColor(Color.rgb(28, 28, 28));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(32, 18, 32, 18);
            row.setBackgroundColor(hidden.contains(pkg) ? Color.rgb(225, 231, 241) : Color.WHITE);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Set<String> current = new HashSet<String>(sp.getStringSet("hidden_apps", new HashSet<String>()));
                    if (current.contains(pkg)) {
                        current.remove(pkg);
                    } else {
                        current.add(pkg);
                    }
                    sp.edit().putStringSet("hidden_apps", current).apply();
                    buildUi();
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

    private String makeRowText(String label, String pkg, boolean isHidden) {
        return (isHidden ? "已隐藏  " : "显示中  ") + label + "\n" + pkg;
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
