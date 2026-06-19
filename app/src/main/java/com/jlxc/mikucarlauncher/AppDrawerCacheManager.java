package com.jlxc.mikucarlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AppDrawerCacheManager {
    private static final String CACHE_DIR = "app_drawer_thumb_cache";
    private static final String ICON_DIR = "icons";
    private static final String INDEX_FILE = "index.json";
    // 车规级存储较慢，缩略图不需要太大；72dp 足够应用抽屉显示，也能明显降低解码/IO 压力。
    private static final int ICON_CACHE_DP = 72;

    private static List<CacheEntry> memoryCache;
    private static int memoryHiddenSignature;
    private static int memoryIconSignature;

    private AppDrawerCacheManager() {
    }

    public static class CacheEntry {
        public final String label;
        public final String pkg;
        public final String cls;
        public final Bitmap icon;

        CacheEntry(String label, String pkg, String cls, Bitmap icon) {
            this.label = label;
            this.pkg = pkg;
            this.cls = cls;
            this.icon = icon;
        }
    }

    public interface RefreshCallback {
        void onFinished(boolean success, int count);
    }

    public static synchronized List<CacheEntry> loadCachedEntriesFast(Context context, int hiddenSignature, int iconSignature) {
        if (context == null) {
            return null;
        }
        if (memoryCache != null
                && memoryHiddenSignature == hiddenSignature
                && memoryIconSignature == iconSignature) {
            return new ArrayList<CacheEntry>(memoryCache);
        }
        try {
            File index = indexFile(context);
            if (!index.exists()) {
                return null;
            }

            JSONObject root = new JSONObject(readText(index));
            if (root.optInt("hiddenSignature", 0) != hiddenSignature) {
                return null;
            }
            if (root.optInt("iconSignature", 0) != iconSignature) {
                return null;
            }

            JSONArray arr = root.optJSONArray("apps");
            if (arr == null) {
                return null;
            }

            List<CacheEntry> result = new ArrayList<CacheEntry>();
            File icons = iconDir(context);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;
                String label = item.optString("label", "");
                String pkg = item.optString("pkg", "");
                String cls = item.optString("cls", "");
                String iconName = item.optString("icon", "");
                if (pkg.length() == 0 || cls.length() == 0) continue;

                Bitmap icon = null;
                if (iconName.length() > 0) {
                    File iconFile = new File(icons, iconName);
                    if (iconFile.exists()) {
                        icon = android.graphics.BitmapFactory.decodeFile(iconFile.getAbsolutePath());
                    }
                }
                result.add(new CacheEntry(label, pkg, cls, icon));
            }
            rememberMemoryCache(result, hiddenSignature, iconSignature);
            return result;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isPackageSignatureChanged(Context context) {
        if (context == null) {
            return true;
        }
        try {
            File index = indexFile(context);
            if (!index.exists()) {
                return true;
            }
            JSONObject root = new JSONObject(readText(index));
            String cached = root.optString("installedSignature", "");
            String current = computeInstalledSignature(context);
            return !current.equals(cached);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static List<CacheEntry> rebuildCache(Context context, Set<String> hidden, int hiddenSignature, int iconSignature) {
        if (context == null) {
            return new ArrayList<CacheEntry>();
        }
        if (hidden == null) {
            hidden = new HashSet<String>();
        }

        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> apps;
        try {
            Intent queryIntent = new Intent(Intent.ACTION_MAIN, null);
            queryIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            apps = pm.queryIntentActivities(queryIntent, 0);
        } catch (Throwable t) {
            apps = new ArrayList<ResolveInfo>();
        }

        final java.util.Map<ResolveInfo, String> labelCache = new java.util.HashMap<ResolveInfo, String>();
        for (ResolveInfo info : apps) {
            try {
                labelCache.put(info, String.valueOf(info.loadLabel(pm)));
            } catch (Throwable t) {
                labelCache.put(info, "");
            }
        }

        Collections.sort(apps, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                String la = labelCache.get(a);
                String lb = labelCache.get(b);
                if (la == null) la = "";
                if (lb == null) lb = "";
                return la.compareToIgnoreCase(lb);
            }
        });

        File dir = cacheDir(context);
        File icons = iconDir(context);
        deleteRecursively(dir);
        icons.mkdirs();

        List<CacheEntry> result = new ArrayList<CacheEntry>();
        JSONArray arr = new JSONArray();

        int iconPx = Math.max(64, Math.round(ICON_CACHE_DP * context.getResources().getDisplayMetrics().density));
        for (ResolveInfo info : apps) {
            if (info == null || info.activityInfo == null) {
                continue;
            }
            String pkg = info.activityInfo.packageName;
            String cls = info.activityInfo.name;
            if (hidden.contains(pkg)) {
                continue;
            }

            String label = labelCache.get(info);
            if (label == null || label.length() == 0) {
                try {
                    label = String.valueOf(info.loadLabel(pm));
                } catch (Throwable t) {
                    label = pkg;
                }
            }

            Drawable icon;
            try {
                icon = info.loadIcon(pm);
            } catch (Throwable t) {
                icon = null;
            }
            label = IconPackManager.getLabel(context, pkg, cls, label);
            icon = IconPackManager.getIcon(context, pkg, cls, icon);

            Bitmap bitmap = drawableToBitmap(icon, iconPx);
            String iconName = safeName(pkg + "_" + cls) + ".png";
            if (bitmap != null) {
                savePng(bitmap, new File(icons, iconName));
            } else {
                iconName = "";
            }

            result.add(new CacheEntry(label, pkg, cls, bitmap));
            try {
                JSONObject item = new JSONObject();
                item.put("label", label == null ? "" : label);
                item.put("pkg", pkg);
                item.put("cls", cls);
                item.put("icon", iconName);
                arr.put(item);
            } catch (Throwable ignored) {
            }
        }

        try {
            JSONObject root = new JSONObject();
            root.put("version", 2);
            root.put("createdAt", System.currentTimeMillis());
            root.put("installedSignature", computeInstalledSignature(context));
            root.put("hiddenSignature", hiddenSignature);
            root.put("iconSignature", iconSignature);
            root.put("apps", arr);
            writeText(indexFile(context), root.toString(2));
        } catch (Throwable ignored) {
        }

        rememberMemoryCache(result, hiddenSignature, iconSignature);
        return result;
    }

    public static void rebuildCacheAsync(final Context context, final RefreshCallback callback) {
        if (context == null) {
            if (callback != null) callback.onFinished(false, 0);
            return;
        }
        final Context appContext = context.getApplicationContext();
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean success = false;
                int count = 0;
                try {
                    android.content.SharedPreferences sp = appContext.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
                    Set<String> hidden = new HashSet<String>(sp.getStringSet("hidden_apps", new HashSet<String>()));
                    int hiddenSignature = hidden.hashCode();
                    int iconSignature = IconPackManager.getIconSignature(appContext);
                    List<CacheEntry> entries = rebuildCache(appContext, hidden, hiddenSignature, iconSignature);
                    count = entries.size();
                    success = true;
                } catch (Throwable ignored) {
                }
                if (callback != null) {
                    final boolean finalSuccess = success;
                    final int finalCount = count;
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onFinished(finalSuccess, finalCount);
                        }
                    });
                }
            }
        }, "MikuCarLauncher-AppThumbManualRebuild");
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    public static synchronized void clearCache(Context context) {
        memoryCache = null;
        try {
            deleteRecursively(cacheDir(context));
        } catch (Throwable ignored) {
        }
    }

    private static synchronized void rememberMemoryCache(List<CacheEntry> entries, int hiddenSignature, int iconSignature) {
        memoryCache = entries == null ? null : new ArrayList<CacheEntry>(entries);
        memoryHiddenSignature = hiddenSignature;
        memoryIconSignature = iconSignature;
    }

    public static String computeInstalledSignature(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent queryIntent = new Intent(Intent.ACTION_MAIN, null);
            queryIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> apps = pm.queryIntentActivities(queryIntent, 0);
            List<String> parts = new ArrayList<String>();
            for (ResolveInfo info : apps) {
                if (info == null || info.activityInfo == null) continue;
                String pkg = info.activityInfo.packageName;
                String cls = info.activityInfo.name;
                long update = 0L;
                try {
                    PackageInfo pi = pm.getPackageInfo(pkg, 0);
                    update = pi.lastUpdateTime;
                } catch (Throwable ignored) {
                }
                parts.add(pkg + "/" + cls + "@" + update);
            }
            Collections.sort(parts);
            MessageDigest digest = MessageDigest.getInstance("MD5");
            for (String part : parts) {
                digest.update(part.getBytes("UTF-8"));
                digest.update((byte) '\n');
            }
            byte[] data = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : data) {
                String h = Integer.toHexString(b & 0xff);
                if (h.length() == 1) sb.append('0');
                sb.append(h);
            }
            return sb.toString();
        } catch (Throwable t) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    private static Bitmap drawableToBitmap(Drawable drawable, int size) {
        if (drawable == null) return null;
        try {
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.TRANSPARENT);
            drawable.setBounds(0, 0, size, size);
            drawable.draw(canvas);
            return bitmap;
        } catch (Throwable ignored) {
            try {
                if (drawable instanceof BitmapDrawable) {
                    return ((BitmapDrawable) drawable).getBitmap();
                }
            } catch (Throwable ignored2) {
            }
            return null;
        }
    }

    private static void savePng(Bitmap bitmap, File out) {
        if (bitmap == null || out == null) return;
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(out);
            bitmap.compress(Bitmap.CompressFormat.PNG, 88, fos);
            fos.flush();
        } catch (Throwable ignored) {
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static String safeName(String text) {
        if (text == null) return "app";
        return text.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static File cacheDir(Context context) {
        return new File(context.getFilesDir(), CACHE_DIR);
    }

    private static File iconDir(Context context) {
        return new File(cacheDir(context), ICON_DIR);
    }

    private static File indexFile(Context context) {
        return new File(cacheDir(context), INDEX_FILE);
    }

    private static String readText(File file) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line).append('\n');
        }
        br.close();
        return sb.toString();
    }

    private static void writeText(File file, String text) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        writer.write(text == null ? "" : text);
        writer.flush();
        writer.close();
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] list = f.listFiles();
            if (list != null) {
                for (File child : list) {
                    deleteRecursively(child);
                }
            }
        }
        try { f.delete(); } catch (Throwable ignored) {}
    }
}
