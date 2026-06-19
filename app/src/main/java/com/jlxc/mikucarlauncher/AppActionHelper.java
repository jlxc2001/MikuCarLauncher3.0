package com.jlxc.mikucarlauncher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

public final class AppActionHelper {
    private AppActionHelper() {
    }

    public static void showAppActions(final Context context,
                                      final String label,
                                      final String pkg,
                                      final String cls,
                                      final Runnable afterChange) {
        if (context == null || pkg == null) {
            return;
        }

        String title = label == null || label.length() == 0 ? pkg : label;
        final String[] items = new String[]{
                "选择 / 打开",
                "重命名",
                "更换图标",
                "隐藏应用",
                "卸载",
                "软件详情"
        };

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        if (which == 0) {
                            openApp(context, label, pkg, cls);
                        } else if (which == 1) {
                            renameApp(context, label, pkg, cls, afterChange);
                        } else if (which == 2) {
                            openIconEditor(context, label, pkg, cls);
                        } else if (which == 3) {
                            hideApp(context, label, pkg, afterChange);
                        } else if (which == 4) {
                            uninstallApp(context, pkg);
                        } else if (which == 5) {
                            openAppDetails(context, pkg);
                        }
                    }
                })
                .create();
        dialog.show();
    }

    public static void openApp(Context context, String label, String pkg, String cls) {
        try {
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(Intent.CATEGORY_LAUNCHER);
            if (cls != null && cls.length() > 0) {
                launch.setClassName(pkg, cls);
            } else {
                launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch == null) {
                    throw new RuntimeException("No launch intent");
                }
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
        } catch (Throwable t) {
            Toast.makeText(context, "无法打开：" + (label == null ? pkg : label), Toast.LENGTH_SHORT).show();
        }
    }

    private static void renameApp(final Context context,
                                  String label,
                                  final String pkg,
                                  final String cls,
                                  final Runnable afterChange) {
        final EditText edit = new EditText(context);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT);
        edit.setText(label == null ? "" : label);
        edit.setSelectAllOnFocus(true);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("重命名应用")
                .setMessage("只会修改桌面/应用抽屉里的显示名称，不会修改原应用。")
                .setView(edit)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        IconPackManager.setCustomLabel(context, pkg, cls, edit.getText().toString());
                        if (afterChange != null) {
                            afterChange.run();
                        }
                        Toast.makeText(context, "已重命名", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认名", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        IconPackManager.setCustomLabel(context, pkg, cls, "");
                        if (afterChange != null) {
                            afterChange.run();
                        }
                        Toast.makeText(context, "已恢复默认名称", Toast.LENGTH_SHORT).show();
                    }
                })
                .create();
        dialog.show();
    }

    private static void openIconEditor(Context context, String label, String pkg, String cls) {
        Intent intent = new Intent(context, CustomIconActivity.class);
        intent.putExtra("label", label);
        intent.putExtra("pkg", pkg);
        intent.putExtra("cls", cls);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private static void hideApp(Context context, String label, String pkg, Runnable afterChange) {
        SharedPreferences sp = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        Set<String> hidden = new HashSet<String>(sp.getStringSet("hidden_apps", new HashSet<String>()));
        hidden.add(pkg);
        sp.edit().putStringSet("hidden_apps", hidden).apply();
        IconPackManager.bumpVersion(context);
        if (afterChange != null) {
            afterChange.run();
        }
        Toast.makeText(context, "已隐藏：" + (label == null ? pkg : label), Toast.LENGTH_SHORT).show();
    }

    private static void uninstallApp(Context context, String pkg) {
        if (context == null || pkg == null || pkg.length() == 0) {
            return;
        }

        // 车机 ROM 对卸载 Intent 支持不一致：先用新接口，再降级到 ACTION_DELETE，最后打开应用详情。
        try {
            Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
            intent.setData(Uri.parse("package:" + pkg));
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, false);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        } catch (Throwable ignored) {
        }

        try {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + pkg));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        } catch (Throwable ignored) {
        }

        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + pkg));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Toast.makeText(context, "车机未开放卸载界面，已打开软件详情", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(context, "无法打开卸载界面或软件详情", Toast.LENGTH_LONG).show();
        }
    }

    private static void openAppDetails(Context context, String pkg) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + pkg));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(context, "无法打开软件详情", Toast.LENGTH_SHORT).show();
        }
    }
}
