package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class Live2DSettingsActivity extends Activity {
    private static final int REQ_PICK_LIVE2D_FOLDER = 2810;

    private CheckBox enabledCheck;
    private TextView modelValue;
    private EditText qualityEdit;
    private EditText fpsEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keepFullscreen();
        buildUi();
    }

    private void buildUi() {
        SharedPreferences sp = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);

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
        title.setText("Live2D 装饰模型");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        TextView hint = new TextView(this);
        hint.setText("Live2D 会放在背景之上、所有功能卡片之下。\\n"
                + "点击“选择 Live2D 模型文件夹”，选择包含 model3.json / model.json 的模型文件夹即可。"
                + "本软件会把模型文件夹复制到应用内部目录，避免 WebView 读取 content:// 或外部存储时显示失败。\\n"
                + "位置和大小不用输入数值，点击“拖动/捏合调整位置大小”后直接用手操作。\\n"
                + "v62 增加画质倍率和帧率设置。画质倍率最高 2.0，帧率最高 60。"
                + "如果调整页面第一次加载不出来，会自动延迟重载，也可以点调整页里的“重载模型”。");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        hint.setTextColor(Color.rgb(82, 82, 82));
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setSingleLine(false);
        root.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(146)
        ));

        enabledCheck = new CheckBox(this);
        enabledCheck.setText("启用 Live2D 装饰模型");
        enabledCheck.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23);
        enabledCheck.setTextColor(Color.rgb(28, 28, 28));
        enabledCheck.setGravity(Gravity.CENTER_VERTICAL);
        enabledCheck.setPadding(dp(26), 0, dp(26), 0);
        enabledCheck.setBackgroundColor(Color.WHITE);
        enabledCheck.setChecked(sp.getBoolean(Live2DDecorView.PREF_ENABLED, false));
        root.addView(enabledCheck, rowLp());

        modelValue = addValue(root, "当前模型：");
        refreshModelValue();

        qualityEdit = addEdit(root,
                "Live2D 画质倍率（0.5~2.0，最高 2.0；1.0 为稳定默认，数值越高清晰度越高但越吃性能）",
                String.valueOf(sp.getFloat(Live2DDecorView.PREF_RENDER_QUALITY, Live2DDecorView.DEFAULT_RENDER_QUALITY)),
                true);

        fpsEdit = addEdit(root,
                "Live2D 帧率（15~60，最高 60；低配车机建议 30 或 45）",
                String.valueOf(sp.getInt(Live2DDecorView.PREF_TARGET_FPS, Live2DDecorView.DEFAULT_TARGET_FPS)),
                false);

        Button chooseFolder = addButton(root, "选择 Live2D 模型文件夹");
        chooseFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                chooseLive2DFolder();
            }
        });

        Button adjust = addButton(root, "拖动 / 捏合调整位置大小");
        adjust.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openAdjustActivity();
            }
        });

        Button save = addButton(root, "保存启用状态 / 画质 / 帧率");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveEnabledState();
            }
        });

        Button reset = addButton(root, "恢复默认位置大小");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetPosition();
            }
        });

        Button disable = addButton(root, "关闭 Live2D 装饰");
        disable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                        .putBoolean(Live2DDecorView.PREF_ENABLED, false)
                        .apply();
                Toast.makeText(Live2DSettingsActivity.this, "已关闭 Live2D 装饰", Toast.LENGTH_SHORT).show();
                enabledCheck.setChecked(false);
            }
        });

        Button webviewSettings = addButton(root, "打开系统 WebView / 应用详情");
        webviewSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openSelfAppDetails();
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

    private TextView addValue(LinearLayout root, String text) {
        TextView value = new TextView(this);
        value.setText(text);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        value.setTextColor(Color.rgb(35, 35, 35));
        value.setGravity(Gravity.CENTER_VERTICAL);
        value.setSingleLine(false);
        value.setPadding(dp(26), 0, dp(26), 0);
        value.setBackgroundColor(Color.WHITE);
        root.addView(value, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(88)
        ));
        return value;
    }

    private EditText addEdit(LinearLayout root, String label, String value, boolean allowDecimal) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tv.setTextColor(Color.rgb(70, 70, 70));
        tv.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        root.addView(tv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54)
        ));

        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setText(value == null ? "" : value);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        edit.setTextColor(Color.rgb(20, 20, 20));
        edit.setSelectAllOnFocus(true);
        edit.setPadding(dp(22), 0, dp(22), 0);
        edit.setBackgroundColor(Color.WHITE);
        edit.setInputType(allowDecimal
                ? (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL)
                : InputType.TYPE_CLASS_NUMBER);
        root.addView(edit, rowLp());
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

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        );
        lp.setMargins(0, dp(8), 0, dp(14));
        return lp;
    }

    private void chooseLive2DFolder() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            startActivityForResult(intent, REQ_PICK_LIVE2D_FOLDER);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开文件夹选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private void openAdjustActivity() {
        SharedPreferences sp = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String model = sp.getString(Live2DDecorView.PREF_MODEL_PATH, "");
        if (model == null || model.trim().length() == 0) {
            Toast.makeText(this, "请先选择 Live2D 模型文件夹", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(this, Live2DAdjustActivity.class));
    }

    private void saveEnabledState() {
        float quality = clampFloat(parseFloat(qualityEdit, Live2DDecorView.DEFAULT_RENDER_QUALITY), 0.5f, 2.0f);
        int fps = clampInt(parseInt(fpsEdit, Live2DDecorView.DEFAULT_TARGET_FPS), 15, 60);

        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                .putBoolean(Live2DDecorView.PREF_ENABLED, enabledCheck.isChecked())
                .putFloat(Live2DDecorView.PREF_RENDER_QUALITY, quality)
                .putInt(Live2DDecorView.PREF_TARGET_FPS, fps)
                .apply();

        qualityEdit.setText(String.valueOf(quality));
        fpsEdit.setText(String.valueOf(fps));

        Toast.makeText(this, "已保存 Live2D 设置。回到首页或重新进入调整页后生效", Toast.LENGTH_SHORT).show();
    }

    private void resetPosition() {
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                .putFloat(Live2DDecorView.PREF_CENTER_X, Live2DDecorView.DEFAULT_CENTER_X)
                .putFloat(Live2DDecorView.PREF_CENTER_Y, Live2DDecorView.DEFAULT_CENTER_Y)
                .putFloat(Live2DDecorView.PREF_SCALE, Live2DDecorView.DEFAULT_SCALE)
                .putFloat(Live2DDecorView.PREF_X, Live2DDecorView.DEFAULT_X)
                .putFloat(Live2DDecorView.PREF_Y, Live2DDecorView.DEFAULT_Y)
                .putFloat(Live2DDecorView.PREF_W, Live2DDecorView.DEFAULT_W)
                .putFloat(Live2DDecorView.PREF_H, Live2DDecorView.DEFAULT_H)
                .apply();
        Toast.makeText(this, "已恢复默认位置大小", Toast.LENGTH_SHORT).show();
    }

    private void openSelfAppDetails() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开系统详情", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshModelValue() {
        if (modelValue == null) {
            return;
        }
        SharedPreferences sp = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String label = sp.getString(Live2DModelImporter.PREF_MODEL_LABEL, "");
        String path = sp.getString(Live2DDecorView.PREF_MODEL_PATH, "");
        int motionCount = sp.getInt(Live2DModelImporter.PREF_MOTION_COUNT, 0);
        int expressionCount = sp.getInt(Live2DModelImporter.PREF_EXPRESSION_COUNT, 0);
        if (path == null || path.length() == 0) {
            modelValue.setText("当前模型：未选择");
        } else {
            modelValue.setText("当前模型：" + (label == null || label.length() == 0 ? "已选择" : label)
                    + "，动作文件 " + motionCount + " 个，表情文件 " + expressionCount + " 个");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        if (requestCode == REQ_PICK_LIVE2D_FOLDER) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                final Uri treeUri = data.getData();
                try {
                    final int flags = data.getFlags()
                            & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(
                            treeUri,
                            flags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    );
                } catch (Throwable ignored) {
                }

                Toast.makeText(this, "正在导入 Live2D 模型文件夹…", Toast.LENGTH_SHORT).show();
                Thread worker = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final Live2DModelImporter.Result result = Live2DModelImporter.importFromTreeUri(
                                Live2DSettingsActivity.this.getApplicationContext(),
                                treeUri
                        );

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (result.success) {
                                    getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                                            .putString(Live2DDecorView.PREF_MODEL_PATH, result.modelPath)
                                            .putString(Live2DModelImporter.PREF_MODEL_LABEL, result.label)
                                            .putInt(Live2DModelImporter.PREF_MOTION_COUNT, result.motionCount)
                                            .putInt(Live2DModelImporter.PREF_EXPRESSION_COUNT, result.expressionCount)
                                            .putBoolean(Live2DDecorView.PREF_ENABLED, true)
                                            .apply();
                                    enabledCheck.setChecked(true);
                                    refreshModelValue();
                                    Toast.makeText(Live2DSettingsActivity.this, result.message, Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(Live2DSettingsActivity.this, result.message, Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    }
                }, "MikuCarLauncher-Live2DImporter");
                worker.setPriority(Thread.MIN_PRIORITY);
                worker.start();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private float parseFloat(EditText edit, float fallback) {
        try {
            return Float.parseFloat(edit.getText().toString().trim());
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private int parseInt(EditText edit, int fallback) {
        try {
            return Integer.parseInt(edit.getText().toString().trim());
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private float clampFloat(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
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
