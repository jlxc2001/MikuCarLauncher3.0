package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
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

public class AmapFloatingCardSettingsActivity extends Activity {
    private static final String PREFS = MainActivity.PREFS;

    private static final float DESIGN_W = 2560f;
    private static final float DESIGN_H = 720f;
    private static final float CARD1_L = 210f;
    private static final float CARD1_T = 35.5f;
    private static final float CARD1_R = 1140f;
    private static final float CARD1_B = 528.5f;
    private static final float CARD1_WIDGET_INSET = 12f;

    private EditText insetDpEdit;
    private EditText xOffsetEdit;
    private EditText yOffsetEdit;
    private EditText widthScaleEdit;
    private EditText heightScaleEdit;
    private EditText forceWidthEdit;
    private EditText forceHeightEdit;
    private EditText dpiEdit;
    private TextView summaryValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keepFullscreen();
        buildUi();
        loadSettings();
        refreshSummary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        keepFullscreen();
        refreshSummary();
        // 设置页属于非首页，进入后确保自动悬浮窗关闭。
        AmapFloatingCardController.sendCloseMapBroadcast(this);
    }

    @Override
    protected void onPause() {
        AmapFloatingCardController.sendCloseMapBroadcast(this);
        super.onPause();
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
        title.setText("1号卡片悬浮高德设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        TextView desc = new TextView(this);
        desc.setText("用于微调 com.autonavi.plus.showmap 广播里的 x / y / w / h / dpi。"
                + "\n强制宽高为 0 时自动按 1号卡片区域计算；DPI 为 0 时不强制高德显示 DPI。"
                + "\n当前默认按测试机完美值换算：x=225 y=50 w=1125 h=515 dpi=200。"
                + "\n注意：自动悬浮只会在 Launcher 首页显示；进入本页或其它页面会自动关闭。");
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        desc.setTextColor(Color.rgb(64, 64, 64));
        desc.setGravity(Gravity.CENTER_VERTICAL);
        desc.setPadding(dp(26), dp(10), dp(26), dp(10));
        desc.setBackgroundColor(Color.WHITE);
        root.addView(desc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(96)
        ));

        summaryValue = addValue(root, "当前参数：");

        insetDpEdit = addEdit(root, "inset 内缩 dp", false);
        xOffsetEdit = addEdit(root, "X 偏移 px", true);
        yOffsetEdit = addEdit(root, "Y 偏移 px", true);
        widthScaleEdit = addEdit(root, "宽度缩放 %", false);
        heightScaleEdit = addEdit(root, "高度缩放 %", false);
        forceWidthEdit = addEdit(root, "强制宽度 px，0 表示自动", false);
        forceHeightEdit = addEdit(root, "强制高度 px，0 表示自动", false);
        dpiEdit = addEdit(root, "高德显示 DPI，0 表示不强制", false);

        Button saveAndTest = addButton(root, "保存并回首页测试显示悬浮地图");
        saveAndTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveSettings();
                testShowFloatingMap();
            }
        });

        Button applyRecommended = addButton(root, "应用推荐参数并回首页测试（225,50,1125,515,DPI200）");
        applyRecommended.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                applyRecommendedSettings();
                testShowFloatingMap();
            }
        });

        Button closeMap = addButton(root, "关闭悬浮地图");
        closeMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AmapFloatingCardController.sendCloseMapBroadcast(AmapFloatingCardSettingsActivity.this);
                Toast.makeText(AmapFloatingCardSettingsActivity.this, "已发送 closemap 广播", Toast.LENGTH_SHORT).show();
            }
        });

        Button overlayPermission = addButton(root, "打开悬浮版高德地图悬浮窗权限设置");
        overlayPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AmapFloatingCardController.openAmapOverlayPermissionPage(AmapFloatingCardSettingsActivity.this);
            }
        });

        TextView adb = new TextView(this);
        adb.setText("ADB 调试："
                + "\nadb shell am broadcast -a com.autonavi.plus.showmap --ei x 225 --ei y 50 --ei w 1125 --ei h 515 --ei dpi 200"
                + "\nadb shell am broadcast -a com.autonavi.plus.closemap");
        adb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        adb.setTextColor(Color.rgb(38, 38, 38));
        adb.setPadding(dp(26), dp(12), dp(26), dp(12));
        adb.setBackgroundColor(Color.WHITE);
        root.addView(adb, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(124)
        ));

        Button reset = addButton(root, "恢复默认参数");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetSettings();
            }
        });

        Button back = addButton(root, "返回车机桌面设置");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        setContentView(scrollView);
    }

    private TextView addValue(LinearLayout root, String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tv.setTextColor(Color.rgb(28, 28, 28));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(dp(26), 0, dp(26), 0);
        tv.setSingleLine(false);
        tv.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(82)
        );
        lp.setMargins(0, dp(14), 0, dp(10));
        root.addView(tv, lp);
        return tv;
    }

    private EditText addEdit(LinearLayout root, String label, boolean signed) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tv.setTextColor(Color.rgb(48, 48, 48));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(38)
        );
        labelLp.setMargins(0, dp(8), 0, 0);
        root.addView(tv, labelLp);

        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        edit.setPadding(dp(22), 0, dp(22), 0);
        edit.setBackgroundColor(Color.WHITE);
        edit.setSelectAllOnFocus(true);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER | (signed ? InputType.TYPE_NUMBER_FLAG_SIGNED : 0));
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(62)
        );
        editLp.setMargins(0, 0, 0, dp(8));
        root.addView(edit, editLp);
        return edit;
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
        lp.setMargins(0, dp(10), 0, dp(14));
        root.addView(button, lp);
        return button;
    }

    private void loadSettings() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        insetDpEdit.setText(String.valueOf(sp.getInt(
                AmapFloatingCardController.PREF_AMAP_CARD_INSET_DP,
                AmapFloatingCardController.DEFAULT_INSET_DP)));
        xOffsetEdit.setText(String.valueOf(sp.getInt(
                AmapFloatingCardController.PREF_AMAP_CARD_X_OFFSET_PX,
                AmapFloatingCardController.DEFAULT_X_OFFSET_PX)));
        yOffsetEdit.setText(String.valueOf(sp.getInt(
                AmapFloatingCardController.PREF_AMAP_CARD_Y_OFFSET_PX,
                AmapFloatingCardController.DEFAULT_Y_OFFSET_PX)));
        widthScaleEdit.setText(String.valueOf(sp.getInt(
                AmapFloatingCardController.PREF_AMAP_CARD_WIDTH_SCALE_PERCENT,
                AmapFloatingCardController.DEFAULT_WIDTH_SCALE_PERCENT)));
        heightScaleEdit.setText(String.valueOf(sp.getInt(
                AmapFloatingCardController.PREF_AMAP_CARD_HEIGHT_SCALE_PERCENT,
                AmapFloatingCardController.DEFAULT_HEIGHT_SCALE_PERCENT)));
        forceWidthEdit.setText(String.valueOf(sp.getInt(
                AmapFloatingCardController.PREF_AMAP_CARD_FORCE_WIDTH_PX,
                AmapFloatingCardController.DEFAULT_FORCE_WIDTH_PX)));
        forceHeightEdit.setText(String.valueOf(sp.getInt(
                AmapFloatingCardController.PREF_AMAP_CARD_FORCE_HEIGHT_PX,
                AmapFloatingCardController.DEFAULT_FORCE_HEIGHT_PX)));
        dpiEdit.setText(String.valueOf(sp.getInt(
                AmapFloatingCardController.PREF_AMAP_CARD_DPI,
                AmapFloatingCardController.DEFAULT_DPI)));
    }

    private void saveSettings() {
        int insetDp = readInt(insetDpEdit, AmapFloatingCardController.DEFAULT_INSET_DP);
        int xOffset = readInt(xOffsetEdit, AmapFloatingCardController.DEFAULT_X_OFFSET_PX);
        int yOffset = readInt(yOffsetEdit, AmapFloatingCardController.DEFAULT_Y_OFFSET_PX);
        int widthScale = clamp(readInt(widthScaleEdit, AmapFloatingCardController.DEFAULT_WIDTH_SCALE_PERCENT), 10, 300);
        int heightScale = clamp(readInt(heightScaleEdit, AmapFloatingCardController.DEFAULT_HEIGHT_SCALE_PERCENT), 10, 300);
        int forceWidth = Math.max(0, readInt(forceWidthEdit, AmapFloatingCardController.DEFAULT_FORCE_WIDTH_PX));
        int forceHeight = Math.max(0, readInt(forceHeightEdit, AmapFloatingCardController.DEFAULT_FORCE_HEIGHT_PX));
        int dpi = Math.max(0, readInt(dpiEdit, AmapFloatingCardController.DEFAULT_DPI));

        insetDp = Math.max(0, insetDp);

        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt(AmapFloatingCardController.PREF_AMAP_CARD_INSET_DP, insetDp)
                .putInt(AmapFloatingCardController.PREF_AMAP_CARD_X_OFFSET_PX, xOffset)
                .putInt(AmapFloatingCardController.PREF_AMAP_CARD_Y_OFFSET_PX, yOffset)
                .putInt(AmapFloatingCardController.PREF_AMAP_CARD_WIDTH_SCALE_PERCENT, widthScale)
                .putInt(AmapFloatingCardController.PREF_AMAP_CARD_HEIGHT_SCALE_PERCENT, heightScale)
                .putInt(AmapFloatingCardController.PREF_AMAP_CARD_FORCE_WIDTH_PX, forceWidth)
                .putInt(AmapFloatingCardController.PREF_AMAP_CARD_FORCE_HEIGHT_PX, forceHeight)
                .putInt(AmapFloatingCardController.PREF_AMAP_CARD_DPI, dpi)
                .apply();

        insetDpEdit.setText(String.valueOf(insetDp));
        widthScaleEdit.setText(String.valueOf(widthScale));
        heightScaleEdit.setText(String.valueOf(heightScale));
        forceWidthEdit.setText(String.valueOf(forceWidth));
        forceHeightEdit.setText(String.valueOf(forceHeight));
        dpiEdit.setText(String.valueOf(dpi));

        refreshSummary();
        Toast.makeText(this, "已保存 1号卡片悬浮高德参数", Toast.LENGTH_SHORT).show();
    }

    private void testShowFloatingMap() {
        // 自动悬浮只允许在 Launcher 首页显示。
        // 因此设置页不再直接发送 showmap，而是保存后回到首页，由 MainActivity 的首页闸门自动拉起。
        AmapFloatingCardController.sendCloseMapBroadcast(this);
        Toast.makeText(this, "已保存，返回首页后自动显示悬浮地图", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private Rect makeCurrentCard1Rect() {
        int rw = getWindow().getDecorView().getWidth();
        int rh = getWindow().getDecorView().getHeight();
        if (rw <= 0 || rh <= 0) {
            rw = getResources().getDisplayMetrics().widthPixels;
            rh = getResources().getDisplayMetrics().heightPixels;
        }
        if (rw <= 0) rw = (int) DESIGN_W;
        if (rh <= 0) rh = (int) DESIGN_H;

        float sx = rw / DESIGN_W;
        float sy = rh / DESIGN_H;

        int left = Math.round((CARD1_L + CARD1_WIDGET_INSET) * sx);
        int top = Math.round((CARD1_T + CARD1_WIDGET_INSET) * sy);
        int width = Math.round((CARD1_R - CARD1_L - CARD1_WIDGET_INSET * 2f) * sx);
        int height = Math.round((CARD1_B - CARD1_T - CARD1_WIDGET_INSET * 2f) * sy);

        return AmapFloatingCardController.adjustedRectFromRaw(this, left, top, width, height);
    }

    private void applyRecommendedSettings() {
        insetDpEdit.setText(String.valueOf(AmapFloatingCardController.DEFAULT_INSET_DP));
        xOffsetEdit.setText(String.valueOf(AmapFloatingCardController.DEFAULT_X_OFFSET_PX));
        yOffsetEdit.setText(String.valueOf(AmapFloatingCardController.DEFAULT_Y_OFFSET_PX));
        widthScaleEdit.setText(String.valueOf(AmapFloatingCardController.DEFAULT_WIDTH_SCALE_PERCENT));
        heightScaleEdit.setText(String.valueOf(AmapFloatingCardController.DEFAULT_HEIGHT_SCALE_PERCENT));
        forceWidthEdit.setText(String.valueOf(AmapFloatingCardController.DEFAULT_FORCE_WIDTH_PX));
        forceHeightEdit.setText(String.valueOf(AmapFloatingCardController.DEFAULT_FORCE_HEIGHT_PX));
        dpiEdit.setText(String.valueOf(AmapFloatingCardController.DEFAULT_DPI));
        saveSettings();
    }

    private void resetSettings() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .remove(AmapFloatingCardController.PREF_AMAP_CARD_INSET_DP)
                .remove(AmapFloatingCardController.PREF_AMAP_CARD_X_OFFSET_PX)
                .remove(AmapFloatingCardController.PREF_AMAP_CARD_Y_OFFSET_PX)
                .remove(AmapFloatingCardController.PREF_AMAP_CARD_WIDTH_SCALE_PERCENT)
                .remove(AmapFloatingCardController.PREF_AMAP_CARD_HEIGHT_SCALE_PERCENT)
                .remove(AmapFloatingCardController.PREF_AMAP_CARD_FORCE_WIDTH_PX)
                .remove(AmapFloatingCardController.PREF_AMAP_CARD_FORCE_HEIGHT_PX)
                .remove(AmapFloatingCardController.PREF_AMAP_CARD_DPI)
                .apply();
        loadSettings();
        refreshSummary();
        Toast.makeText(this, "已恢复默认参数", Toast.LENGTH_SHORT).show();
    }

    private void refreshSummary() {
        if (summaryValue != null) {
            boolean installed = AmapFloatingCardController.isAmapFloatingInstalled(this);
            summaryValue.setText("当前参数： " + AmapFloatingCardController.getSettingsSummary(this)
                    + "\n悬浮版高德：" + (installed ? "已安装" : "未安装")
                    + "，包名 com.autonavi.amapautoys");
        }
    }

    private int readInt(EditText editText, int fallback) {
        if (editText == null || editText.getText() == null) {
            return fallback;
        }
        String text = editText.getText().toString().trim();
        if (text.length() == 0 || "-".equals(text)) {
            return fallback;
        }
        try {
            return Integer.parseInt(text);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
