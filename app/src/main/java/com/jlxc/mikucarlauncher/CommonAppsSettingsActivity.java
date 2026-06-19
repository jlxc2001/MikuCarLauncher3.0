package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
        title.setText("4号卡片常用软件 / 快捷文字设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        TextView tip = new TextView(this);
        tip.setText("4号卡片支持 6 个位置。每个位置可以设置为应用，也可以设置为快捷文字屏节点的快捷文字。\n"
                + "快捷文字会通过 UDP 47230 发送到 com.jlxc.mikutextdisplay；喊话文字会弹出输入框，确认后发送到后置超长条屏。");
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

            Button quickText = addButton(root, "设置位置" + (i + 1) + "为快捷文字");
            quickText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showQuickTextDialog(slot);
                }
            });

            Button shout = addButton(root, "设置位置" + (i + 1) + "为喊话文字");
            shout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveShoutShortcut(slot);
                }
            });

            Button clear = addButton(root, "清空位置" + (i + 1));
            clear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clearSlot(slot);
                    refreshValues();
                }
            });
        }

        Button textNodeSettings = addButton(root, "打开快捷文字屏节点设置");
        textNodeSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(CommonAppsSettingsActivity.this, MikuTextDisplayNodeSettingsActivity.class));
            }
        });

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

    private void showQuickTextDialog(final int slot) {
        final SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(8), dp(22), 0);

        final EditText labelEdit = dialogEdit("卡片显示名称，例如 让我先过", InputType.TYPE_CLASS_TEXT);
        labelEdit.setText(sp.getString("common_app_" + slot + "_label", "快捷文字"));
        root.addView(labelEdit);

        final EditText iconEdit = dialogEdit("图标文字/Emoji，例如 文、谢、🚗", InputType.TYPE_CLASS_TEXT);
        iconEdit.setText(sp.getString("common_app_" + slot + "_text_icon", "文"));
        root.addView(iconEdit);

        final EditText textEdit = dialogEdit("发送到后屏的文字，例如 让我先过一下，谢谢大哥", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        textEdit.setSingleLine(false);
        textEdit.setMinLines(2);
        textEdit.setText(sp.getString("common_app_" + slot + "_text_payload", "让我先过一下，谢谢大哥"));
        root.addView(textEdit);

        new AlertDialog.Builder(this)
                .setTitle("设置位置" + (slot + 1) + "为快捷文字")
                .setView(root)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String label = labelEdit.getText().toString().trim();
                        String icon = iconEdit.getText().toString().trim();
                        String text = textEdit.getText().toString().trim();
                        if (label.length() == 0) label = "快捷文字";
                        if (icon.length() == 0) icon = "文";
                        if (text.length() == 0) {
                            Toast.makeText(CommonAppsSettingsActivity.this, "快捷文字不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        saveTextShortcut(slot, label, text, icon);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private EditText dialogEdit(String hint, int inputType) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        edit.setSingleLine(true);
        edit.setInputType(inputType);
        edit.setPadding(dp(12), dp(4), dp(12), dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(72));
        lp.setMargins(0, dp(8), 0, dp(6));
        edit.setLayoutParams(lp);
        return edit;
    }

    private void saveTextShortcut(int slot, String label, String text, String icon) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        clearSlotKeys(editor, slot);
        editor.putString("common_app_" + slot + "_type", MikuTextDisplayNodeController.COMMON_TYPE_TEXT);
        editor.putString("common_app_" + slot + "_pkg", MikuTextDisplayNodeController.COMMON_PKG_MARKER);
        editor.putString("common_app_" + slot + "_cls", "text_" + slot);
        editor.putString("common_app_" + slot + "_label", label);
        editor.putString("common_app_" + slot + "_text_payload", text);
        editor.putString("common_app_" + slot + "_text_icon", icon);
        editor.putLong("common_apps_updated_at", System.currentTimeMillis());
        editor.apply();
        Toast.makeText(this, "已设置快捷文字：" + label, Toast.LENGTH_SHORT).show();
        refreshValues();
    }

    private void saveShoutShortcut(int slot) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        clearSlotKeys(editor, slot);
        editor.putString("common_app_" + slot + "_type", MikuTextDisplayNodeController.COMMON_TYPE_SHOUT);
        editor.putString("common_app_" + slot + "_pkg", MikuTextDisplayNodeController.COMMON_PKG_MARKER);
        editor.putString("common_app_" + slot + "_cls", "shout_" + slot);
        editor.putString("common_app_" + slot + "_label", "喊话文字");
        editor.putString("common_app_" + slot + "_text_payload", "");
        editor.putString("common_app_" + slot + "_text_icon", "喊");
        editor.putLong("common_apps_updated_at", System.currentTimeMillis());
        editor.apply();
        Toast.makeText(this, "已设置为喊话文字", Toast.LENGTH_SHORT).show();
        refreshValues();
    }

    private void clearSlot(int slot) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        clearSlotKeys(editor, slot);
        editor.putLong("common_apps_updated_at", System.currentTimeMillis());
        editor.apply();
    }

    private void clearSlotKeys(SharedPreferences.Editor editor, int slot) {
        editor.remove("common_app_" + slot + "_pkg");
        editor.remove("common_app_" + slot + "_cls");
        editor.remove("common_app_" + slot + "_label");
        editor.remove("common_app_" + slot + "_type");
        editor.remove("common_app_" + slot + "_text_payload");
        editor.remove("common_app_" + slot + "_text_icon");
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
            String type = sp.getString("common_app_" + i + "_type", "app");
            String label = sp.getString("common_app_" + i + "_label", "未设置");
            String pkg = sp.getString("common_app_" + i + "_pkg", "");
            if (MikuTextDisplayNodeController.COMMON_TYPE_TEXT.equals(type)) {
                String text = sp.getString("common_app_" + i + "_text_payload", "");
                valueViews[i].setText("位置" + (i + 1) + "：快捷文字 / " + label + " / " + text);
            } else if (MikuTextDisplayNodeController.COMMON_TYPE_SHOUT.equals(type)) {
                valueViews[i].setText("位置" + (i + 1) + "：喊话文字 / 点击后输入内容发送到文字屏");
            } else if (pkg == null || pkg.length() == 0) {
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
