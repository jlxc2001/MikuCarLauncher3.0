package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
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
import java.util.List;

public class IconPackIconPickerActivity extends Activity {
    private String appLabel;
    private String appPkg;
    private String appCls;
    private String iconPackPkg;
    private String iconPackLabel;
    private GridView gridView;

    private final List<IconPackManager.PackIconInfo> icons = new ArrayList<IconPackManager.PackIconInfo>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keepFullscreen();

        Intent intent = getIntent();
        appLabel = intent.getStringExtra("label");
        appPkg = intent.getStringExtra("pkg");
        appCls = intent.getStringExtra("cls");

        if (appPkg == null || appPkg.length() == 0) {
            finish();
            return;
        }

        SharedPreferences sp = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        iconPackPkg = sp.getString(IconPackManager.PREF_ICON_PACK_PKG, "");
        iconPackLabel = sp.getString(IconPackManager.PREF_ICON_PACK_LABEL, "");
        if (iconPackPkg == null || iconPackPkg.length() == 0) {
            Toast.makeText(this, "请先在图标包设置里选择一个第三方图标包", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        buildUi();
        loadIcons();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(238, 241, 246));
        root.setPadding(dp(46), dp(28), dp(46), dp(38));

        TextView title = new TextView(this);
        title.setText("从图标包选择图标");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(62)
        ));

        TextView hint = new TextView(this);
        hint.setText("当前图标包：" + (iconPackLabel == null || iconPackLabel.length() == 0 ? iconPackPkg : iconPackLabel)
                + "。这里可以给“" + (appLabel == null ? appPkg : appLabel) + "”选择图标包里的任意一个图标。");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        hint.setTextColor(Color.rgb(82, 82, 82));
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setSingleLine(false);
        root.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(72)
        ));

        gridView = new GridView(this);
        gridView.setNumColumns(8);
        gridView.setHorizontalSpacing(dp(18));
        gridView.setVerticalSpacing(dp(18));
        gridView.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        gridView.setClipToPadding(false);
        gridView.setPadding(dp(8), dp(8), dp(8), dp(8));
        gridView.setSelector(android.R.color.transparent);
        gridView.setAdapter(new IconAdapter());
        root.addView(gridView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        setContentView(root);
    }

    private void loadIcons() {
        Toast.makeText(this, "正在读取图标包图标…", Toast.LENGTH_SHORT).show();

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                final List<IconPackManager.PackIconInfo> list = IconPackManager.listIconsFromPack(
                        IconPackIconPickerActivity.this.getApplicationContext(), iconPackPkg);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        icons.clear();
                        icons.addAll(list);
                        if (gridView != null && gridView.getAdapter() != null) {
                            ((BaseAdapter) gridView.getAdapter()).notifyDataSetChanged();
                        }
                        if (icons.isEmpty()) {
                            Toast.makeText(IconPackIconPickerActivity.this, "这个图标包没有读取到可选图标", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }, "MikuCarLauncher-IconPackIconLoader");
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    private class IconAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return icons.size();
        }

        @Override
        public Object getItem(int position) {
            return icons.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final IconPackManager.PackIconInfo item = icons.get(position);
            Holder holder;
            if (convertView == null) {
                LinearLayout root = new LinearLayout(IconPackIconPickerActivity.this);
                root.setOrientation(LinearLayout.VERTICAL);
                root.setGravity(Gravity.CENTER);
                root.setPadding(dp(8), dp(8), dp(8), dp(8));
                root.setBackgroundColor(Color.WHITE);
                root.setFocusable(true);
                root.setLayoutParams(new AbsListView.LayoutParams(
                        AbsListView.LayoutParams.MATCH_PARENT, dp(128)
                ));

                ImageView image = new ImageView(IconPackIconPickerActivity.this);
                LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(dp(64), dp(64));
                imageLp.gravity = Gravity.CENTER_HORIZONTAL;
                root.addView(image, imageLp);

                TextView name = new TextView(IconPackIconPickerActivity.this);
                name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                name.setTextColor(Color.rgb(80, 80, 80));
                name.setGravity(Gravity.CENTER);
                name.setSingleLine(true);
                LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                );
                nameLp.setMargins(0, dp(8), 0, 0);
                root.addView(name, nameLp);

                holder = new Holder();
                holder.icon = image;
                holder.name = name;
                convertView = root;
                convertView.setTag(holder);
            } else {
                holder = (Holder) convertView.getTag();
            }

            Drawable drawable = item.icon != null
                    ? item.icon
                    : IconPackManager.loadDrawableFromPack(IconPackIconPickerActivity.this, item.iconPackPackage, item.drawableName);
            if (drawable != null) {
                holder.icon.setImageDrawable(drawable);
            } else {
                holder.icon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
            holder.name.setText(item.drawableName);

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    IconPackManager.setCustomIconFromIconPack(
                            IconPackIconPickerActivity.this, appPkg, appCls, item.iconPackPackage, item.drawableName);
                    Toast.makeText(IconPackIconPickerActivity.this, "已选择图标：" + item.drawableName, Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

            return convertView;
        }
    }

    private static class Holder {
        ImageView icon;
        TextView name;
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
