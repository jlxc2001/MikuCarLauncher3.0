package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PresetBackupActivity extends Activity {
    private static final int REQ_EXPORT = 7301;
    private static final int REQ_IMPORT = 7302;
    private TextView summary;
    private JSONObject pendingExportJson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keepFullscreen();
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(238, 241, 246));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(46), dp(34), dp(46), dp(46));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("预设备份 / 导入备份");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(76)));

        summary = new TextView(this);
        summary.setText("会导出 MikuCarLauncher 的全部 SharedPreferences 预设，包括隐藏应用列表、常用 APP、应用自定义图标映射、Live2D 模型 URI/位置、日间/夜间壁纸 URI、转向音、天气、高德、后置 AI、文字屏节点等设置。\n\n注意：备份保存的是文件位置 / URI 和设置值，不会把音频、壁纸、Live2D 模型文件本体打包进去。导入后请保证原文件仍然存在或重新授权。\n\n当前设置项数量：" + getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getAll().size());
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        summary.setTextColor(Color.rgb(48, 48, 48));
        summary.setPadding(dp(26), dp(14), dp(26), dp(14));
        summary.setBackgroundColor(Color.WHITE);
        root.addView(summary, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(190)));

        Button export = addButton(root, "导出当前预设备份 JSON");
        export.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { exportPreset(); }
        });

        Button imp = addButton(root, "导入预设备份 JSON（覆盖当前设置）");
        imp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { importPreset(); }
        });

        Button back = addButton(root, "返回车机桌面设置");
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        setContentView(scroll);
    }

    private Button addButton(LinearLayout root, String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        b.setGravity(Gravity.CENTER);
        root.addView(b, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(78)));
        return b;
    }

    private void exportPreset() {
        try {
            pendingExportJson = buildBackupJson();
            String name = "MikuCarLauncher3_preset_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".json";
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, name);
            startActivityForResult(intent, REQ_EXPORT);
        } catch (Throwable t) {
            Toast.makeText(this, "生成备份失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importPreset() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQ_IMPORT);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        keepFullscreen();
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            try {
                OutputStream os = getContentResolver().openOutputStream(uri);
                if (os == null) throw new RuntimeException("openOutputStream failed");
                String json = pendingExportJson == null ? buildBackupJson().toString(2) : pendingExportJson.toString(2);
                os.write(json.getBytes(Charset.forName("UTF-8")));
                os.close();
                Toast.makeText(this, "预设备份已导出", Toast.LENGTH_LONG).show();
            } catch (Throwable t) {
                Toast.makeText(this, "写入备份失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_IMPORT) {
            try {
                String json = readAll(uri);
                restoreBackupJson(new JSONObject(json));
                AppDrawerCacheManager.requestLauncherRefresh(this);
                IconPackManager.bumpVersion(this);
                Toast.makeText(this, "预设已导入，请返回首页查看", Toast.LENGTH_LONG).show();
                summary.setText("已导入备份。当前设置项数量：" + getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getAll().size());
            } catch (Throwable t) {
                Toast.makeText(this, "导入失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private JSONObject buildBackupJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("type", "miku_car_launcher_preset_backup");
        root.put("version", 1);
        root.put("exportedAt", System.currentTimeMillis());
        root.put("app", "MikuCarLauncher3.0");
        JSONObject prefsJson = new JSONObject();
        Map<String, ?> all = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getAll();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            Object v = e.getValue();
            JSONObject item = new JSONObject();
            if (v instanceof String) {
                item.put("type", "string"); item.put("value", v);
            } else if (v instanceof Integer) {
                item.put("type", "int"); item.put("value", ((Integer) v).intValue());
            } else if (v instanceof Long) {
                item.put("type", "long"); item.put("value", ((Long) v).longValue());
            } else if (v instanceof Float) {
                item.put("type", "float"); item.put("value", ((Float) v).doubleValue());
            } else if (v instanceof Boolean) {
                item.put("type", "boolean"); item.put("value", ((Boolean) v).booleanValue());
            } else if (v instanceof Set) {
                item.put("type", "string_set");
                JSONArray arr = new JSONArray();
                for (Object s : (Set<?>) v) arr.put(String.valueOf(s));
                item.put("value", arr);
            } else {
                continue;
            }
            prefsJson.put(e.getKey(), item);
        }
        root.put("prefs", prefsJson);
        return root;
    }

    private void restoreBackupJson(JSONObject root) throws Exception {
        if (!"miku_car_launcher_preset_backup".equals(root.optString("type"))) {
            throw new RuntimeException("不是 MikuCarLauncher 预设备份");
        }
        JSONObject prefsJson = root.getJSONObject("prefs");
        SharedPreferences.Editor editor = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit();
        editor.clear();
        JSONArray names = prefsJson.names();
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                String key = names.getString(i);
                JSONObject item = prefsJson.getJSONObject(key);
                String type = item.optString("type");
                if ("string".equals(type)) editor.putString(key, item.optString("value", ""));
                else if ("int".equals(type)) editor.putInt(key, item.optInt("value", 0));
                else if ("long".equals(type)) editor.putLong(key, item.optLong("value", 0L));
                else if ("float".equals(type)) editor.putFloat(key, (float) item.optDouble("value", 0d));
                else if ("boolean".equals(type)) editor.putBoolean(key, item.optBoolean("value", false));
                else if ("string_set".equals(type)) {
                    JSONArray arr = item.optJSONArray("value");
                    Set<String> set = new HashSet<String>();
                    if (arr != null) for (int j = 0; j < arr.length(); j++) set.add(arr.optString(j));
                    editor.putStringSet(key, set);
                }
            }
        }
        editor.apply();
    }

    private String readAll(Uri uri) throws Exception {
        InputStream is = getContentResolver().openInputStream(uri);
        if (is == null) throw new RuntimeException("openInputStream failed");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) >= 0) bos.write(buf, 0, n);
        is.close();
        return new String(bos.toByteArray(), Charset.forName("UTF-8"));
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
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
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        keepFullscreen();
        AmapFloatingCardController.sendCloseMapBroadcast(this);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (HomeKeyHelper.handle(this, event)) return true;
        return super.dispatchKeyEvent(event);
    }
}
