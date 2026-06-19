package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class IconPackSettingsActivity extends Activity {
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
        root.setPadding(dp(46), dp(34), dp(46), dp(54));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("图标包设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        TextView hint = new TextView(this);
        hint.setText("支持大部分第三方图标包 app（读取 appfilter.xml）。选择后会应用到应用抽屉和4号常用软件卡片；单个应用仍可长按更换图标或恢复系统图标。当前：" + IconPackManager.getCurrentIconPackLabel(this));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        hint.setTextColor(Color.rgb(85, 85, 85));
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setSingleLine(false);
        root.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(112)
        ));

        addRow(root, "系统默认图标", "不使用第三方图标包", null, "", "系统默认图标");

        List<IconPackManager.IconPackInfo> packs = IconPackManager.findIconPacks(this);
        if (packs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("没有扫描到已安装的图标包。安装图标包 app 后重新进入这里即可显示。");
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            empty.setTextColor(Color.rgb(90, 90, 90));
            empty.setGravity(Gravity.CENTER_VERTICAL);
            root.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(120)
            ));
        } else {
            for (IconPackManager.IconPackInfo info : packs) {
                addRow(root, info.label, info.packageName, info.icon, info.packageName, info.label);
            }
        }

        setContentView(scrollView);
    }

    private void addRow(LinearLayout root,
                        String title,
                        String sub,
                        android.graphics.drawable.Drawable icon,
                        final String pkg,
                        final String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(24), 0, dp(24), 0);
        row.setBackgroundColor(Color.WHITE);
        row.setFocusable(true);

        ImageView iconView = new ImageView(this);
        if (icon != null) {
            iconView.setImageDrawable(icon);
        } else {
            iconView.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(58), dp(58));
        iconLp.setMargins(0, 0, dp(22), 0);
        row.addView(iconView, iconLp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        t.setTextColor(Color.rgb(30, 30, 30));
        texts.addView(t);

        TextView st = new TextView(this);
        st.setText(sub == null ? "" : sub);
        st.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        st.setTextColor(Color.rgb(105, 105, 105));
        texts.addView(st);

        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                IconPackManager.selectIconPack(IconPackSettingsActivity.this, pkg, label);
                Toast.makeText(IconPackSettingsActivity.this, "已选择：" + label, Toast.LENGTH_SHORT).show();
                buildUi();
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(92)
        );
        lp.setMargins(0, dp(10), 0, dp(10));
        root.addView(row, lp);
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
        if (HomeKeyHelper.handle(this, event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
