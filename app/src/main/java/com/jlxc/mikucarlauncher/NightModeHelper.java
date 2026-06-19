package com.jlxc.mikucarlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

public final class NightModeHelper {
    public static final String PREF_NIGHT_MODE = "night_mode";
    public static final String PREF_SUNRISE_MIN = "night_sunrise_min";
    public static final String PREF_SUNSET_MIN = "night_sunset_min";
    public static final String PREF_LIVE2D_NIGHT_DIM_ALPHA = "live2d_night_dim_alpha";

    public static final int MODE_DAY = 0;
    public static final int MODE_NIGHT = 1;
    public static final int MODE_AUTO = 2;

    public static final int DEFAULT_SUNRISE_MIN = 6 * 60;
    public static final int DEFAULT_SUNSET_MIN = 18 * 60;
    public static final int DEFAULT_LIVE2D_NIGHT_DIM_ALPHA = 35;

    private NightModeHelper() {
    }

    public static boolean isNightMode(Context context) {
        if (context == null) {
            return false;
        }
        SharedPreferences sp = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        int mode = sp.getInt(PREF_NIGHT_MODE, MODE_DAY);
        if (mode == MODE_NIGHT) {
            return true;
        }
        if (mode != MODE_AUTO) {
            return false;
        }

        int sunrise = clampMinute(sp.getInt(PREF_SUNRISE_MIN, DEFAULT_SUNRISE_MIN));
        int sunset = clampMinute(sp.getInt(PREF_SUNSET_MIN, DEFAULT_SUNSET_MIN));
        int now = nowMinute();

        if (sunrise == sunset) {
            return false;
        }

        // 常见情况：日出早于日落，夜间为日落后到次日日出前。
        if (sunrise < sunset) {
            return now < sunrise || now >= sunset;
        }

        // 极端/用户自定义跨天：日间跨过 0 点，则夜间是 sunrise 到 sunset 之间。
        return now >= sunset && now < sunrise;
    }

    public static String modeName(Context context) {
        if (context == null) {
            return "日间模式";
        }
        int mode = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getInt(PREF_NIGHT_MODE, MODE_DAY);
        if (mode == MODE_NIGHT) {
            return "夜间模式";
        }
        if (mode == MODE_AUTO) {
            return "按日出日落自动";
        }
        return "日间模式";
    }

    public static int parseTimeToMinute(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        text = text.trim();
        if (text.length() == 0) {
            return fallback;
        }

        try {
            String[] parts = text.split(":");
            if (parts.length == 2) {
                int h = Integer.parseInt(parts[0].trim());
                int m = Integer.parseInt(parts[1].trim());
                if (h >= 0 && h <= 23 && m >= 0 && m <= 59) {
                    return h * 60 + m;
                }
            }

            int h = Integer.parseInt(text);
            if (h >= 0 && h <= 23) {
                return h * 60;
            }
        } catch (Throwable ignored) {
        }

        return fallback;
    }


    public static int live2DNightDimAlpha(Context context) {
        if (context == null) {
            return DEFAULT_LIVE2D_NIGHT_DIM_ALPHA;
        }
        SharedPreferences sp = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        return clampPercent(sp.getInt(PREF_LIVE2D_NIGHT_DIM_ALPHA, DEFAULT_LIVE2D_NIGHT_DIM_ALPHA));
    }

    public static int parsePercent(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            String clean = text.trim().replace("%", "");
            if (clean.length() == 0) {
                return fallback;
            }
            return clampPercent(Integer.parseInt(clean));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static int clampPercent(int value) {
        if (value < 0) return 0;
        if (value > 85) return 85;
        return value;
    }

    public static String formatMinute(int minute) {
        minute = clampMinute(minute);
        int h = minute / 60;
        int m = minute % 60;
        return String.format("%02d:%02d", h, m);
    }

    public static int clampMinute(int minute) {
        if (minute < 0) return 0;
        if (minute > 23 * 60 + 59) return 23 * 60 + 59;
        return minute;
    }

    private static int nowMinute() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }
}
