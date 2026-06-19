package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.content.Intent;
import android.view.KeyEvent;

public final class HomeKeyHelper {
    public static final String EXTRA_GO_HOME = "com.jlxc.mikucarlauncher.GO_HOME";

    private HomeKeyHelper() {
    }

    public static boolean handle(Activity activity, KeyEvent event) {
        if (activity == null || event == null) {
            return false;
        }

        int keyCode = event.getKeyCode();
        if (!isHomeKey(keyCode) && !isBackKey(keyCode)) {
            return false;
        }

        // DOWN 和 UP 都消费掉，避免部分车机重复触发。
        // 作为 Launcher：返回键不退出、不回到上一个应用，只回到本桌面首页；如果已经在首页则无操作。
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (isBackKey(keyCode) && activity instanceof MainActivity && ((MainActivity) activity).isHomePage()) {
                return true;
            }
            goHome(activity);
        }
        return true;
    }

    public static boolean isHomeKey(int keyCode) {
        // KEYCODE_HOME 是标准 Home。
        // KEYCODE_MOVE_HOME 是部分硬件键盘/车机把“回首页”发成的按键。
        return keyCode == KeyEvent.KEYCODE_HOME
                || keyCode == KeyEvent.KEYCODE_MOVE_HOME;
    }

    public static boolean isBackKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BACK
                || keyCode == KeyEvent.KEYCODE_ESCAPE;
    }

    public static void goHome(Activity activity) {
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).showHomePage(false);
            return;
        }

        Intent intent = new Intent(activity, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_GO_HOME, true);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
    }
}
