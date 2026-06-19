package com.jlxc.mikucarlauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class PackageChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }

        String action = intent.getAction();
        if (action == null) {
            return;
        }

        if (Intent.ACTION_PACKAGE_ADDED.equals(action)
                || Intent.ACTION_PACKAGE_REMOVED.equals(action)
                || Intent.ACTION_PACKAGE_CHANGED.equals(action)
                || Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            // 安装/卸载/更新应用后才刷新 APP 缩略图缓存。
            // 平时打开应用抽屉不再扫描全量应用，避免车规级慢存储卡顿。
            AppDrawerCacheManager.rebuildCacheAsync(context.getApplicationContext(), null);
        }
    }
}
