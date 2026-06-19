package com.jlxc.mikucarlauncher;

import android.app.Activity;
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

public class NightModeSettingsActivity extends Activity {
    private static final String PREFS = MainActivity.PREFS;

    private TextView currentModeText;
    private EditText sunriseEdit;
    private EditText sunsetEdit;
    private EditText live2DDimEdit;

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
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);

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
        title.setText("夜间模式设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        currentModeText = addValue(root, "");

        Button day = addButton(root, "切换为日间模式");
        day.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setMode(NightModeHelper.MODE_DAY);
            }
        });

        Button night = addButton(root, "切换为夜间模式");
        night.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setMode(NightModeHelper.MODE_NIGHT);
            }
        });

        Button auto = addButton(root, "按日出日落时间自动切换");
        auto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setMode(NightModeHelper.MODE_AUTO);
            }
        });

        TextView tip = new TextView(this);
        tip.setText("自动模式会根据下面的日出/日落时间切换。日出后为日间，日落后为夜间。格式示例：06:00、18:00。");
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        tip.setTextColor(Color.rgb(85, 85, 85));
        tip.setGravity(Gravity.CENTER_VERTICAL);
        tip.setSingleLine(false);
        root.addView(tip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(100)
        ));

        addLabel(root, "日出时间");
        sunriseEdit = addEdit(root, NightModeHelper.formatMinute(
                sp.getInt(NightModeHelper.PREF_SUNRISE_MIN, NightModeHelper.DEFAULT_SUNRISE_MIN)));

        addLabel(root, "日落时间");
        sunsetEdit = addEdit(root, NightModeHelper.formatMinute(
                sp.getInt(NightModeHelper.PREF_SUNSET_MIN, NightModeHelper.DEFAULT_SUNSET_MIN)));

        Button saveTime = addButton(root, "保存自动切换时间");
        saveTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveTimes();
            }
        });

        TextView live2DTip = new TextView(this);
        live2DTip.setText("夜间模式下可以单独把 Live2D 人物压暗。这里相当于只给 Live2D 人物套黑色遮罩，不影响背景图和功能卡片。建议 25~45，0 为不变暗，最高 85。");
        live2DTip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        live2DTip.setTextColor(Color.rgb(85, 85, 85));
        live2DTip.setGravity(Gravity.CENTER_VERTICAL);
        live2DTip.setSingleLine(false);
        root.addView(live2DTip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(118)
        ));

        addLabel(root, "Live2D 夜间变暗透明度（0~85）");
        live2DDimEdit = addEdit(root, String.valueOf(
                sp.getInt(NightModeHelper.PREF_LIVE2D_NIGHT_DIM_ALPHA,
                        NightModeHelper.DEFAULT_LIVE2D_NIGHT_DIM_ALPHA)));
        live2DDimEdit.setInputType(InputType.TYPE_CLASS_NUMBER);

        Button saveLive2DDim = addButton(root, "保存 Live2D 夜间变暗透明度");
        saveLive2DDim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveLive2DDimAlpha();
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
        refreshValues();
    }

    private TextView addValue(LinearLayout root, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        tv.setTextColor(Color.rgb(28, 28, 28));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(dp(26), 0, dp(26), 0);
        tv.setSingleLine(false);
        tv.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(78)
        );
        lp.setMargins(0, dp(8), 0, dp(16));
        root.addView(tv, lp);
        return tv;
    }

    private void addLabel(LinearLayout root, String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        label.setTextColor(Color.rgb(70, 70, 70));
        label.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        root.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
        ));
    }

    private EditText addEdit(LinearLayout root, String value) {
        EditText edit = new EditText(this);
        edit.setText(value);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25);
        edit.setSingleLine(true);
        edit.setPadding(dp(26), 0, dp(26), 0);
        edit.setBackgroundColor(Color.WHITE);
        edit.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        );
        lp.setMargins(0, 0, 0, dp(16));
        root.addView(edit, lp);
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

    private void setMode(int mode) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt(NightModeHelper.PREF_NIGHT_MODE, mode)
                .apply();
        refreshValues();
        Toast.makeText(this, "已切换为：" + NightModeHelper.modeName(this), Toast.LENGTH_SHORT).show();
    }

    private void saveTimes() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        int oldSunrise = sp.getInt(NightModeHelper.PREF_SUNRISE_MIN, NightModeHelper.DEFAULT_SUNRISE_MIN);
        int oldSunset = sp.getInt(NightModeHelper.PREF_SUNSET_MIN, NightModeHelper.DEFAULT_SUNSET_MIN);

        int sunrise = NightModeHelper.parseTimeToMinute(sunriseEdit.getText().toString(), oldSunrise);
        int sunset = NightModeHelper.parseTimeToMinute(sunsetEdit.getText().toString(), oldSunset);

        sp.edit()
                .putInt(NightModeHelper.PREF_SUNRISE_MIN, sunrise)
                .putInt(NightModeHelper.PREF_SUNSET_MIN, sunset)
                .apply();

        sunriseEdit.setText(NightModeHelper.formatMinute(sunrise));
        sunsetEdit.setText(NightModeHelper.formatMinute(sunset));
        refreshValues();
        Toast.makeText(this, "已保存自动切换时间", Toast.LENGTH_SHORT).show();
    }

    private void saveLive2DDimAlpha() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        int oldAlpha = sp.getInt(NightModeHelper.PREF_LIVE2D_NIGHT_DIM_ALPHA,
                NightModeHelper.DEFAULT_LIVE2D_NIGHT_DIM_ALPHA);
        int alpha = NightModeHelper.parsePercent(
                live2DDimEdit == null ? "" : live2DDimEdit.getText().toString(),
                oldAlpha);

        sp.edit()
                .putInt(NightModeHelper.PREF_LIVE2D_NIGHT_DIM_ALPHA, alpha)
                .apply();

        if (live2DDimEdit != null) {
            live2DDimEdit.setText(String.valueOf(alpha));
        }
        refreshValues();
        Toast.makeText(this, "已保存 Live2D 夜间变暗透明度：" + alpha + "%", Toast.LENGTH_SHORT).show();
    }

    private void refreshValues() {
        if (currentModeText == null) {
            return;
        }
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        int sunrise = sp.getInt(NightModeHelper.PREF_SUNRISE_MIN, NightModeHelper.DEFAULT_SUNRISE_MIN);
        int sunset = sp.getInt(NightModeHelper.PREF_SUNSET_MIN, NightModeHelper.DEFAULT_SUNSET_MIN);
        int live2DDimAlpha = sp.getInt(NightModeHelper.PREF_LIVE2D_NIGHT_DIM_ALPHA,
                NightModeHelper.DEFAULT_LIVE2D_NIGHT_DIM_ALPHA);
        currentModeText.setText("当前模式： " + NightModeHelper.modeName(this)
                + "，当前实际显示：" + (NightModeHelper.isNightMode(this) ? "夜间" : "日间")
                + "\n日出 " + NightModeHelper.formatMinute(sunrise)
                + " / 日落 " + NightModeHelper.formatMinute(sunset)
                + "\nLive2D 夜间变暗透明度：" + NightModeHelper.clampPercent(live2DDimAlpha) + "%");
        if (live2DDimEdit != null) {
            live2DDimEdit.setText(String.valueOf(NightModeHelper.clampPercent(live2DDimAlpha)));
        }
    }

    private void keepFullscreen() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
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
