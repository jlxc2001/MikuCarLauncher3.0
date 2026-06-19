package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class Live2DAdjustActivity extends Activity {
    private FrameLayout rootLayout;
    private LauncherBackgroundView backgroundView;
    private Live2DDecorView live2DView;
    private Live2DGuideOverlayView guideOverlayView;
    private TextView hintView;
    private LinearLayout controlBar;
    private boolean firstAdjustLoad = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keepFullscreen();

        rootLayout = new FrameLayout(this);

        backgroundView = new LauncherBackgroundView(this);
        rootLayout.addView(backgroundView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        live2DView = new Live2DDecorView(this);
        live2DView.setAdjustMode(true);
        rootLayout.addView(live2DView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        guideOverlayView = new Live2DGuideOverlayView(this);
        rootLayout.addView(guideOverlayView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        hintView = new TextView(this);
        hintView.setText("拖动人物调整位置，双指捏合调整人物大小。白色半透明块是模拟首页卡片位置。");
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        hintView.setTextColor(Color.WHITE);
        hintView.setGravity(Gravity.CENTER_VERTICAL);
        hintView.setPadding(dp(28), 0, dp(28), 0);
        hintView.setBackgroundColor(0x99000000);
        hintView.setClickable(false);
        hintView.setFocusable(false);

        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(58)
        );
        hintLp.leftMargin = dp(34);
        hintLp.topMargin = dp(24);
        rootLayout.addView(hintView, hintLp);

        controlBar = new LinearLayout(this);
        controlBar.setOrientation(LinearLayout.HORIZONTAL);
        controlBar.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        controlBar.setPadding(dp(16), 0, dp(16), 0);
        controlBar.setBackgroundColor(0x77000000);
        controlBar.setClickable(true);
        controlBar.setFocusable(false);

        Button reset = new Button(this);
        reset.setText("恢复默认位置");
        reset.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        reset.setAllCaps(false);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetPosition();
            }
        });
        controlBar.addView(reset, new LinearLayout.LayoutParams(dp(220), dp(54)));

        Button reload = new Button(this);
        reload.setText("重载模型");
        reload.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        reload.setAllCaps(false);
        reload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                reloadLive2DForAdjust();
                Toast.makeText(Live2DAdjustActivity.this, "正在重新加载 Live2D", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams reloadLp = new LinearLayout.LayoutParams(dp(160), dp(54));
        reloadLp.setMargins(dp(16), 0, 0, 0);
        controlBar.addView(reload, reloadLp);

        Button done = new Button(this);
        done.setText("完成");
        done.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        done.setAllCaps(false);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(Live2DAdjustActivity.this, "已保存 Live2D 位置和大小", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(dp(140), dp(54));
        doneLp.setMargins(dp(16), 0, 0, 0);
        controlBar.addView(done, doneLp);

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(70)
        );
        barLp.gravity = Gravity.RIGHT | Gravity.BOTTOM;
        barLp.rightMargin = dp(34);
        barLp.bottomMargin = dp(24);
        rootLayout.addView(controlBar, barLp);

        setContentView(rootLayout);

        rootLayout.post(new Runnable() {
            @Override
            public void run() {
                live2DView.applySettings();
                live2DView.setVisibility(View.VISIBLE);
                bringAdjustOverlaysToFront();
                scheduleInitialAdjustReload();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        keepFullscreen();
        if (backgroundView != null) {
            backgroundView.invalidate();
        }
        if (live2DView != null) {
            live2DView.resumeLive2D();
            live2DView.applySettings();
            live2DView.setVisibility(View.VISIBLE);
            if (firstAdjustLoad && rootLayout != null) {
                scheduleInitialAdjustReload();
            }
        }
        if (guideOverlayView != null) {
            guideOverlayView.invalidate();
        }
        bringAdjustOverlaysToFront();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            keepFullscreen();
            if (live2DView != null) {
                live2DView.resumeLive2D();
                live2DView.applySettings();
                if (firstAdjustLoad) {
                    scheduleInitialAdjustReload();
                }
                bringAdjustOverlaysToFront();
            }
        }
    }

    private void scheduleInitialAdjustReload() {
        if (rootLayout == null || live2DView == null) {
            return;
        }
        firstAdjustLoad = false;

        // 刚导入模型后立刻进调整页时，车机 WebView 偶尔第一次没把模型拉起来。
        // 这里只在调整页首次进入时做两次延迟重载，不影响后续拖动/缩放。
        rootLayout.postDelayed(new Runnable() {
            @Override
            public void run() {
                reloadLive2DForAdjust();
            }
        }, 900L);

        rootLayout.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (live2DView != null) {
                    live2DView.resumeLive2D();
                    live2DView.applySettings();
                    live2DView.setVisibility(View.VISIBLE);
                }
            }
        }, 2100L);
    }

    private void reloadLive2DForAdjust() {
        if (live2DView == null) {
            return;
        }
        live2DView.setVisibility(View.VISIBLE);
        live2DView.resumeLive2D();
        live2DView.reloadLive2D();

        // 关键：重载 WebView 后不能把 Live2DView 放到最上层，
        // 否则会压住“恢复默认位置 / 重载模型 / 完成”按钮。
        bringAdjustOverlaysToFront();
    }

    private void bringAdjustOverlaysToFront() {
        if (guideOverlayView != null) {
            guideOverlayView.bringToFront();
        }
        if (hintView != null) {
            hintView.bringToFront();
        }
        if (controlBar != null) {
            controlBar.bringToFront();
            controlBar.requestLayout();
            controlBar.invalidate();
        }
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
        if (live2DView != null) {
            live2DView.applySettings();
        }
        bringAdjustOverlaysToFront();
        Toast.makeText(this, "已恢复默认位置和大小", Toast.LENGTH_SHORT).show();
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
