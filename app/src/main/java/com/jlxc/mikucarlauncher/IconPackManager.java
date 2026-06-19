package com.jlxc.mikucarlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class IconPackManager {
    public static final String PREF_ICON_PACK_PKG = "icon_pack_pkg";
    public static final String PREF_ICON_PACK_LABEL = "icon_pack_label";
    public static final String PREF_ICON_CHANGE_VERSION = "icon_change_version";

    private static String cachedPackPackage = null;
    private static HashMap<String, String> cachedComponentToDrawable = null;

    private IconPackManager() {
    }

    public static class IconPackInfo {
        public final String packageName;
        public final String label;
        public final Drawable icon;

        IconPackInfo(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    public static class PackIconInfo {
        public final String iconPackPackage;
        public final String drawableName;
        public final Drawable icon;

        PackIconInfo(String iconPackPackage, String drawableName, Drawable icon) {
            this.iconPackPackage = iconPackPackage;
            this.drawableName = drawableName;
            this.icon = icon;
        }
    }

    public static Drawable getIcon(Context context, String pkg, String cls, Drawable fallback) {
        if (context == null || pkg == null) {
            return fallback;
        }

        String componentKey = componentKey(pkg, cls);
        SharedPreferences sp = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);

        String mode = sp.getString(customIconModeKey(componentKey), "");
        if ("system".equals(mode)) {
            return fallback;
        }

        if ("custom".equals(mode)) {
            String uriText = sp.getString(customIconUriKey(componentKey), "");
            if (uriText != null && uriText.length() > 0) {
                Drawable custom = loadUriDrawable(context, uriText);
                if (custom != null) {
                    return custom;
                }
            }
        }

        if ("pack_drawable".equals(mode)) {
            String packPkg = sp.getString(customIconPackPkgKey(componentKey), "");
            String drawableName = sp.getString(customIconDrawableKey(componentKey), "");
            Drawable selected = loadDrawableFromPack(context, packPkg, drawableName);
            if (selected != null) {
                return selected;
            }
        }

        String iconPackPkg = sp.getString(PREF_ICON_PACK_PKG, "");
        if (iconPackPkg == null || iconPackPkg.length() == 0) {
            return fallback;
        }

        Drawable themed = loadIconFromPack(context, iconPackPkg, pkg, cls);
        return themed != null ? themed : fallback;
    }

    public static String getLabel(Context context, String pkg, String cls, String fallback) {
        if (context == null || pkg == null) {
            return fallback;
        }
        SharedPreferences sp = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        String label = sp.getString(customLabelKey(componentKey(pkg, cls)), "");
        if (label != null && label.trim().length() > 0) {
            return label.trim();
        }
        return fallback;
    }

    public static void setCustomLabel(Context context, String pkg, String cls, String label) {
        if (context == null || pkg == null) {
            return;
        }
        String key = customLabelKey(componentKey(pkg, cls));
        SharedPreferences.Editor editor = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit();
        if (label == null || label.trim().length() == 0) {
            editor.remove(key);
        } else {
            editor.putString(key, label.trim());
        }
        editor.apply();
        bumpVersion(context);
    }

    public static void setCustomIconUri(Context context, String pkg, String cls, String uriText) {
        if (context == null || pkg == null) {
            return;
        }
        String componentKey = componentKey(pkg, cls);
        SharedPreferences.Editor editor = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit();
        editor.putString(customIconModeKey(componentKey), "custom");
        editor.putString(customIconUriKey(componentKey), uriText == null ? "" : uriText);
        editor.remove(customIconPackPkgKey(componentKey));
        editor.remove(customIconDrawableKey(componentKey));
        editor.apply();
        bumpVersion(context);
    }

    public static void setCustomIconFromIconPack(Context context, String pkg, String cls, String iconPackPkg, String drawableName) {
        if (context == null || pkg == null || iconPackPkg == null || drawableName == null) {
            return;
        }
        String componentKey = componentKey(pkg, cls);
        SharedPreferences.Editor editor = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit();
        editor.putString(customIconModeKey(componentKey), "pack_drawable");
        editor.putString(customIconPackPkgKey(componentKey), iconPackPkg);
        editor.putString(customIconDrawableKey(componentKey), drawableName);
        editor.remove(customIconUriKey(componentKey));
        editor.apply();
        bumpVersion(context);
    }

    public static void useIconPackForApp(Context context, String pkg, String cls) {
        if (context == null || pkg == null) {
            return;
        }
        String componentKey = componentKey(pkg, cls);
        context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(customIconModeKey(componentKey))
                .remove(customIconUriKey(componentKey))
                .remove(customIconPackPkgKey(componentKey))
                .remove(customIconDrawableKey(componentKey))
                .apply();
        bumpVersion(context);
    }

    public static void useSystemIconForApp(Context context, String pkg, String cls) {
        if (context == null || pkg == null) {
            return;
        }
        String componentKey = componentKey(pkg, cls);
        context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(customIconModeKey(componentKey), "system")
                .remove(customIconUriKey(componentKey))
                .remove(customIconPackPkgKey(componentKey))
                .remove(customIconDrawableKey(componentKey))
                .apply();
        bumpVersion(context);
    }

    public static int getIconSignature(Context context) {
        if (context == null) {
            return 0;
        }
        SharedPreferences sp = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        String pack = sp.getString(PREF_ICON_PACK_PKG, "");
        int version = sp.getInt(PREF_ICON_CHANGE_VERSION, 0);
        return (String.valueOf(pack) + "#" + version).hashCode();
    }

    public static String getCurrentIconPackLabel(Context context) {
        if (context == null) {
            return "系统默认图标";
        }
        SharedPreferences sp = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        String pkg = sp.getString(PREF_ICON_PACK_PKG, "");
        if (pkg == null || pkg.length() == 0) {
            return "系统默认图标";
        }
        String label = sp.getString(PREF_ICON_PACK_LABEL, "");
        if (label != null && label.length() > 0) {
            return label;
        }
        try {
            PackageManager pm = context.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Throwable ignored) {
            return pkg;
        }
    }

    public static void selectIconPack(Context context, String pkg, String label) {
        if (context == null) {
            return;
        }
        SharedPreferences.Editor editor = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit();
        if (pkg == null || pkg.length() == 0) {
            editor.remove(PREF_ICON_PACK_PKG);
            editor.putString(PREF_ICON_PACK_LABEL, "系统默认图标");
        } else {
            editor.putString(PREF_ICON_PACK_PKG, pkg);
            editor.putString(PREF_ICON_PACK_LABEL, label == null || label.length() == 0 ? pkg : label);
        }
        editor.apply();
        clearCache();
        bumpVersion(context);
    }

    public static List<IconPackInfo> findIconPacks(Context context) {
        final List<IconPackInfo> result = new ArrayList<IconPackInfo>();
        if (context == null) {
            return result;
        }

        PackageManager pm = context.getPackageManager();
        List<PackageInfo> packages;
        try {
            packages = pm.getInstalledPackages(0);
        } catch (Throwable t) {
            packages = new ArrayList<PackageInfo>();
        }

        for (PackageInfo pi : packages) {
            if (pi == null || pi.packageName == null) {
                continue;
            }
            if (pi.packageName.equals(context.getPackageName())) {
                continue;
            }
            if (!hasAppFilter(context, pi.packageName)) {
                continue;
            }

            String label = pi.packageName;
            Drawable icon = null;
            try {
                label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pi.packageName, 0)));
                icon = pm.getApplicationIcon(pi.packageName);
            } catch (Throwable ignored) {
            }

            result.add(new IconPackInfo(pi.packageName, label, icon));
        }

        Collections.sort(result, new Comparator<IconPackInfo>() {
            @Override
            public int compare(IconPackInfo a, IconPackInfo b) {
                return String.valueOf(a.label).compareToIgnoreCase(String.valueOf(b.label));
            }
        });
        return result;
    }

    public static List<PackIconInfo> listIconsFromPack(Context context, String iconPackPkg) {
        final List<PackIconInfo> result = new ArrayList<PackIconInfo>();
        if (context == null || iconPackPkg == null || iconPackPkg.length() == 0) {
            return result;
        }

        HashMap<String, String> map = getAppFilterMap(context, iconPackPkg);
        if (map == null || map.isEmpty()) {
            return result;
        }

        Set<String> names = new HashSet<String>(map.values());
        List<String> sortedNames = new ArrayList<String>(names);
        Collections.sort(sortedNames, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return String.valueOf(a).compareToIgnoreCase(String.valueOf(b));
            }
        });

        for (String name : sortedNames) {
            if (name == null || name.length() == 0) {
                continue;
            }
            // 这里只列出图标名称，不预加载全部 Drawable，避免大图标包一次性占用太多内存。
            // 真正显示时再按需加载可见图标。
            result.add(new PackIconInfo(iconPackPkg, name, null));
        }
        return result;
    }

    public static Drawable loadDrawableFromPack(Context context, String iconPackPkg, String drawableName) {
        if (context == null || iconPackPkg == null || drawableName == null || drawableName.length() == 0) {
            return null;
        }
        try {
            Resources res = context.getPackageManager().getResourcesForApplication(iconPackPkg);
            int id = res.getIdentifier(drawableName, "drawable", iconPackPkg);
            if (id == 0) {
                id = res.getIdentifier(drawableName, "mipmap", iconPackPkg);
            }
            if (id == 0) {
                return null;
            }
            return res.getDrawable(id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void bumpVersion(Context context) {
        if (context == null) {
            return;
        }
        SharedPreferences sp = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        int version = sp.getInt(PREF_ICON_CHANGE_VERSION, 0);
        sp.edit().putInt(PREF_ICON_CHANGE_VERSION, version + 1).apply();
    }

    public static String componentKey(String pkg, String cls) {
        if (cls == null || cls.length() == 0) {
            cls = "";
        }
        return pkg + "/" + cls;
    }

    private static String customLabelKey(String componentKey) {
        return "app_custom_label_" + componentKey;
    }

    private static String customIconModeKey(String componentKey) {
        return "app_custom_icon_mode_" + componentKey;
    }

    private static String customIconUriKey(String componentKey) {
        return "app_custom_icon_uri_" + componentKey;
    }

    private static String customIconPackPkgKey(String componentKey) {
        return "app_custom_icon_pack_pkg_" + componentKey;
    }

    private static String customIconDrawableKey(String componentKey) {
        return "app_custom_icon_drawable_" + componentKey;
    }

    private static Drawable loadUriDrawable(Context context, String uriText) {
        InputStream input = null;
        try {
            input = context.getContentResolver().openInputStream(Uri.parse(uriText));
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                return null;
            }
            return new BitmapDrawable(context.getResources(), bitmap);
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static Drawable loadIconFromPack(Context context, String iconPackPkg, String pkg, String cls) {
        try {
            HashMap<String, String> map = getAppFilterMap(context, iconPackPkg);
            if (map == null || map.isEmpty()) {
                return null;
            }

            String drawableName = findDrawableName(map, pkg, cls);
            if (drawableName == null || drawableName.length() == 0) {
                return null;
            }

            return loadDrawableFromPack(context, iconPackPkg, drawableName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String findDrawableName(HashMap<String, String> map, String pkg, String cls) {
        if (pkg == null) {
            return null;
        }
        if (cls == null) {
            cls = "";
        }

        String fullCls = cls;
        if (cls.startsWith(".")) {
            fullCls = pkg + cls;
        }

        String[] keys = new String[]{
                "ComponentInfo{" + pkg + "/" + cls + "}",
                "ComponentInfo{" + pkg + "/" + fullCls + "}",
                pkg + "/" + cls,
                pkg + "/" + fullCls
        };

        for (String key : keys) {
            String value = map.get(key);
            if (value != null && value.length() > 0) {
                return value;
            }
        }
        return null;
    }

    private static synchronized HashMap<String, String> getAppFilterMap(Context context, String iconPackPkg) {
        if (iconPackPkg == null || iconPackPkg.length() == 0) {
            return null;
        }
        if (iconPackPkg.equals(cachedPackPackage) && cachedComponentToDrawable != null) {
            return cachedComponentToDrawable;
        }

        HashMap<String, String> map = new HashMap<String, String>();
        InputStream input = null;
        try {
            Resources res = context.getPackageManager().getResourcesForApplication(iconPackPkg);
            AssetManager assets = res.getAssets();
            input = assets.open("appfilter.xml");

            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(input, "UTF-8");
            int type = parser.getEventType();

            while (type != XmlPullParser.END_DOCUMENT) {
                if (type == XmlPullParser.START_TAG && "item".equals(parser.getName())) {
                    String component = parser.getAttributeValue(null, "component");
                    String drawable = parser.getAttributeValue(null, "drawable");
                    if (component != null && drawable != null) {
                        map.put(component.trim(), drawable.trim());
                    }
                }
                type = parser.next();
            }
        } catch (Throwable ignored) {
            map.clear();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable ignored) {
                }
            }
        }

        cachedPackPackage = iconPackPkg;
        cachedComponentToDrawable = map;
        return cachedComponentToDrawable;
    }

    private static boolean hasAppFilter(Context context, String packageName) {
        InputStream input = null;
        try {
            Resources res = context.getPackageManager().getResourcesForApplication(packageName);
            input = res.getAssets().open("appfilter.xml");
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static synchronized void clearCache() {
        cachedPackPackage = null;
        cachedComponentToDrawable = null;
    }
}
