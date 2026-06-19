package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppListActivity extends Activity {
    private static final String PREFS = MainActivity.PREFS;

    private final List<AppItem> appItems = new ArrayList<>();

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
        buildUi();
    }

    private void buildUi() {
        final SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        final int columns = clamp(sp.getInt("drawer_grid_columns", 6), 3, 8);
        final int rows = clamp(sp.getInt("drawer_grid_rows", 3), 1, 6);
        final int iconSizeDp = clamp(sp.getInt("drawer_icon_size_dp", 72), 40, 128);
        final int textSizeSp = clamp(sp.getInt("drawer_text_size_sp", 16), 10, 30);

        loadApps();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245, 246, 248));
        root.setPadding(dp(24), dp(14), dp(24), dp(14));

        TextView title = new TextView(this);
        title.setText("应用抽屉");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTextColor(Color.rgb(25, 25, 25));
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(6), 0, dp(6), 0);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)
        ));

        TextView hint = new TextView(this);
        hint.setText("平板式 " + rows + "×" + columns + " 网格排列。点击打开应用，长按弹出选择、重命名、更换图标、卸载、软件详情等操作。可在 我的 → 车机桌面设置 → 应用抽屉显示设置 里调节图标、文字和网格数量。");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        hint.setTextColor(Color.rgb(92, 92, 92));
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setPadding(dp(6), dp(4), dp(6), dp(10));
        root.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
        ));

        GridView gridView = new GridView(this);
        gridView.setNumColumns(columns);
        gridView.setHorizontalSpacing(dp(18));
        gridView.setVerticalSpacing(dp(10));
        gridView.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        gridView.setClipToPadding(false);
        gridView.setPadding(dp(10), dp(8), dp(10), dp(8));
        gridView.setSelector(android.R.color.transparent);
        gridView.setVerticalScrollBarEnabled(false);

        int availableHeight = getAvailableGridHeight();
        int cellHeight = Math.max(dp(128), (availableHeight - dp(10) * (rows - 1)) / rows);
        gridView.setAdapter(new AppGridAdapter(appItems, iconSizeDp, textSizeSp, cellHeight));

        root.addView(gridView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        setContentView(root);
    }

    private int getAvailableGridHeight() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenHeight = dm.heightPixels;
        int reserved = dp(14 + 14 + 42 + 48 + 8 + 8 + 16);
        return Math.max(dp(400), screenHeight - reserved);
    }

    private void loadApps() {
        appItems.clear();
        final PackageManager pm = getPackageManager();
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> hidden = new HashSet<String>(sp.getStringSet("hidden_apps", new HashSet<String>()));

        Intent queryIntent = new Intent(Intent.ACTION_MAIN, null);
        queryIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(queryIntent, 0);
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                return String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm)));
            }
        });

        for (ResolveInfo info : apps) {
            String label = String.valueOf(info.loadLabel(pm));
            String pkg = info.activityInfo.packageName;
            String cls = info.activityInfo.name;
            if (hidden.contains(pkg)) {
                continue;
            }
            Drawable icon;
            try {
                icon = info.loadIcon(pm);
            } catch (Throwable t) {
                icon = null;
            }
            label = IconPackManager.getLabel(this, pkg, cls, label);
            icon = IconPackManager.getIcon(this, pkg, cls, icon);
            appItems.add(new AppItem(label, pkg, cls, icon));
        }
    }

    private void openApp(String label, String pkg, String cls) {
        try {
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(Intent.CATEGORY_LAUNCHER);
            launch.setClassName(pkg, cls);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开：" + label, Toast.LENGTH_SHORT).show();
        }
    }

    private void hideApp(String pkg, String label) {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> hidden = new HashSet<String>(sp.getStringSet("hidden_apps", new HashSet<String>()));
        hidden.add(pkg);
        sp.edit().putStringSet("hidden_apps", hidden).apply();
        Toast.makeText(this, "已隐藏：" + label, Toast.LENGTH_SHORT).show();
        buildUi();
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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

    private static class AppItem {
        final String label;
        final String pkg;
        final String cls;
        final Drawable icon;

        AppItem(String label, String pkg, String cls, Drawable icon) {
            this.label = label;
            this.pkg = pkg;
            this.cls = cls;
            this.icon = icon;
        }
    }

    private class AppGridAdapter extends BaseAdapter {
        private final List<AppItem> items;
        private final int iconSizeDp;
        private final int textSizeSp;
        private final int cellHeightPx;

        AppGridAdapter(List<AppItem> items, int iconSizeDp, int textSizeSp, int cellHeightPx) {
            this.items = items;
            this.iconSizeDp = iconSizeDp;
            this.textSizeSp = textSizeSp;
            this.cellHeightPx = cellHeightPx;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final AppItem item = items.get(position);
            ItemHolder holder;
            if (convertView == null) {
                LinearLayout itemRoot = new LinearLayout(AppListActivity.this);
                itemRoot.setOrientation(LinearLayout.VERTICAL);
                itemRoot.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
                itemRoot.setBackgroundColor(Color.TRANSPARENT);
                itemRoot.setPadding(dp(6), dp(8), dp(6), dp(8));
                itemRoot.setLayoutParams(new AbsListView.LayoutParams(
                        AbsListView.LayoutParams.MATCH_PARENT, cellHeightPx
                ));

                ImageView iconView = new ImageView(AppListActivity.this);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(iconSizeDp), dp(iconSizeDp));
                iconLp.gravity = Gravity.CENTER_HORIZONTAL;
                itemRoot.addView(iconView, iconLp);

                TextView labelView = new TextView(AppListActivity.this);
                labelView.setTextColor(Color.rgb(70, 70, 70));
                labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
                labelView.setGravity(Gravity.CENTER);
                labelView.setMaxLines(2);
                labelView.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                );
                textLp.topMargin = dp(10);
                itemRoot.addView(labelView, textLp);

                holder = new ItemHolder();
                holder.iconView = iconView;
                holder.labelView = labelView;
                convertView = itemRoot;
                convertView.setTag(holder);
            } else {
                holder = (ItemHolder) convertView.getTag();
                convertView.setLayoutParams(new AbsListView.LayoutParams(
                        AbsListView.LayoutParams.MATCH_PARENT, cellHeightPx
                ));
            }

            if (item.icon != null) {
                holder.iconView.setImageDrawable(item.icon);
            } else {
                holder.iconView.setImageResource(android.R.drawable.sym_def_app_icon);
            }
            ViewGroup.LayoutParams iconLp = holder.iconView.getLayoutParams();
            iconLp.width = dp(iconSizeDp);
            iconLp.height = dp(iconSizeDp);
            holder.iconView.setLayoutParams(iconLp);
            holder.labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
            holder.labelView.setText(item.label);

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openApp(item.label, item.pkg, item.cls);
                }
            });
            convertView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    AppActionHelper.showAppActions(AppListActivity.this, item.label, item.pkg, item.cls, new Runnable() {
                        @Override
                        public void run() {
                            buildUi();
                        }
                    });
                    return true;
                }
            });

            return convertView;
        }
    }

    private static class ItemHolder {
        ImageView iconView;
        TextView labelView;
    }
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (HomeKeyHelper.handle(this, event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }


}
