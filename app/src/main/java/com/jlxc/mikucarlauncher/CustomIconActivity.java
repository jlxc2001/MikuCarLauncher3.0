package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.content.Intent;
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

public class CustomIconActivity extends Activity {
    private static final int REQ_PICK_ICON = 2701;

    private String label;
    private String pkg;
    private String cls;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keepFullscreen();

        Intent intent = getIntent();
        label = intent.getStringExtra("label");
        pkg = intent.getStringExtra("pkg");
        cls = intent.getStringExtra("cls");
        if (label == null || label.length() == 0) {
            label = pkg == null ? "应用" : pkg;
        }
        if (pkg == null) {
            finish();
            return;
        }

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
        title.setText("更换图标");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        TextView hint = new TextView(this);
        hint.setText(label + "\n可以单独覆盖这个应用的图标。可以从当前图标包里任选一个图标，也可以选择透明 PNG/普通图片作为自定义图标，或者恢复为当前图标包/系统默认图标。");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        hint.setTextColor(Color.rgb(80, 80, 80));
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setSingleLine(false);
        root.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(126)
        ));

        Button pick = addButton(root, "从图片选择自定义图标");
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickImage();
            }
        });

        Button chooseFromIconPack = addButton(root, "从当前图标包选择任意图标");
        chooseFromIconPack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(CustomIconActivity.this, IconPackIconPickerActivity.class);
                intent.putExtra("label", label);
                intent.putExtra("pkg", pkg);
                intent.putExtra("cls", cls);
                startActivity(intent);
            }
        });

        Button iconPack = addButton(root, "使用当前图标包对应图标");
        iconPack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                IconPackManager.useIconPackForApp(CustomIconActivity.this, pkg, cls);
                Toast.makeText(CustomIconActivity.this, "已使用当前图标包", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        Button system = addButton(root, "恢复系统默认图标");
        system.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                IconPackManager.useSystemIconForApp(CustomIconActivity.this, pkg, cls);
                Toast.makeText(CustomIconActivity.this, "已恢复系统默认图标", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        Button back = addButton(root, "返回");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        setContentView(scrollView);
    }

    private Button addButton(LinearLayout root, String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23);
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        button.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(82)
        );
        lp.setMargins(0, dp(12), 0, dp(12));
        root.addView(button, lp);
        return button;
    }

    private void pickImage() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, REQ_PICK_ICON);
        } catch (Throwable t) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(Intent.createChooser(intent, "选择图标图片"), REQ_PICK_ICON);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_PICK_ICON) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                try {
                    final int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    getContentResolver().takePersistableUriPermission(uri, flags);
                } catch (Throwable ignored) {
                }
                IconPackManager.setCustomIconUri(this, pkg, cls, uri.toString());
                Toast.makeText(this, "已更换图标", Toast.LENGTH_SHORT).show();
                finish();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
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
