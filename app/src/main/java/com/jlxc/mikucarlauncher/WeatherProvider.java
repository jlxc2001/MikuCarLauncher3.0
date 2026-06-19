package com.jlxc.mikucarlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WeatherProvider {
    private static final String PREFS = MainActivity.PREFS;

    public static final String PREF_WEATHER_CITY_NAME = "weather_city_name";
    // 中国天气 cityId，例如安源 101240904。
    public static final String PREF_WEATHER_CITY_CODE = "weather_city_code";
    public static final String DEFAULT_CITY_NAME = "萍乡";
    public static final String DEFAULT_CITY_CODE = "101240904";

    private static final long UPDATE_INTERVAL_MS = 10L * 60L * 1000L;
    private static final long RETRY_INTERVAL_MS = 60L * 1000L;

    private final Context context;
    private final Object lock = new Object();

    private HandlerThread workerThread;
    private Handler workerHandler;
    private boolean started;
    private volatile Snapshot snapshot = Snapshot.empty();

    public WeatherProvider(Context context) {
        this.context = context.getApplicationContext();
    }

    public Snapshot getSnapshot() {
        return snapshot;
    }

    public void start() {
        synchronized (lock) {
            if (started) {
                return;
            }
            started = true;
        }

        if (workerThread == null) {
            workerThread = new HandlerThread("MikuCarLauncher-Weather");
            workerThread.start();
            workerHandler = new Handler(workerThread.getLooper());
        }

        workerHandler.removeCallbacks(updateRunnable);
        workerHandler.post(updateRunnable);
    }

    public void stop() {
        synchronized (lock) {
            started = false;
        }

        if (workerHandler != null) {
            workerHandler.removeCallbacksAndMessages(null);
        }

        if (workerThread != null) {
            workerThread.quitSafely();
            workerThread = null;
        }
        workerHandler = null;
    }

    public void refreshNow() {
        Handler handler = workerHandler;
        if (handler != null) {
            handler.removeCallbacks(updateRunnable);
            handler.post(updateRunnable);
        }
    }

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            long nextDelay = UPDATE_INTERVAL_MS;
            try {
                boolean ok = updateOnce();
                if (!ok) {
                    nextDelay = RETRY_INTERVAL_MS;
                }
            } catch (Throwable ignored) {
                nextDelay = RETRY_INTERVAL_MS;
            }

            Handler handler = workerHandler;
            synchronized (lock) {
                if (started && handler != null) {
                    handler.postDelayed(this, nextDelay);
                }
            }
        }
    };

    private boolean updateOnce() {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String cityName = sp.getString(PREF_WEATHER_CITY_NAME, DEFAULT_CITY_NAME);
        String cityCode = sp.getString(PREF_WEATHER_CITY_CODE, DEFAULT_CITY_CODE);

        if (cityName == null || cityName.trim().length() == 0) {
            cityName = DEFAULT_CITY_NAME;
        }
        if (cityCode == null || cityCode.trim().length() == 0) {
            cityCode = DEFAULT_CITY_CODE;
        }
        cityCode = normalizeCityCode(cityCode);

        try {
            Snapshot s = requestSk2d(cityName, cityCode);
            if (s != null && s.valid) {
                snapshot = s;
                return true;
            }
        } catch (Throwable ignored) {
        }

        try {
            Snapshot s = requestMWeatherPage(cityName, cityCode);
            if (s != null) {
                snapshot = s;
                return s.valid;
            }
        } catch (Throwable ignored) {
        }

        snapshot = Snapshot.error(cityName, "天气获取失败");
        return false;
    }

    private String normalizeCityCode(String cityCode) {
        if (cityCode == null) {
            return DEFAULT_CITY_CODE;
        }
        cityCode = cityCode.trim();

        // v41 之前默认保存过高德 adcode：360300。中国天气城市 ID 不是 6 位 adcode，
        // 为了老版本升级后不读错，自动迁移到用户给的中国天气安源 ID。
        if ("360300".equals(cityCode)) {
            return DEFAULT_CITY_CODE;
        }

        if (cityCode.length() < 8 || !cityCode.startsWith("101")) {
            return DEFAULT_CITY_CODE;
        }
        return cityCode;
    }

    private Snapshot requestSk2d(String fallbackCityName, String cityCode) throws Exception {
        // 中国天气当前实况 JSONP。部分车机网络环境下需要 Referer 和 User-Agent。
        String urlText = "https://d1.weather.com.cn/sk_2d/" + cityCode + ".html?_=" + System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlText).openConnection();
            conn.setConnectTimeout(4500);
            conn.setReadTimeout(4500);
            conn.setUseCaches(false);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) MikuCarLauncher");
            conn.setRequestProperty("Referer", "https://e.weather.com.cn/mweather/" + cityCode + ".shtml");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }

            String body = readAll(conn.getInputStream());
            JSONObject json = extractFirstJsonObject(body);
            if (json == null) {
                return null;
            }

            String city = firstNonEmpty(
                    json.optString("cityname", ""),
                    json.optString("city", ""),
                    fallbackCityName
            );
            String weather = firstNonEmpty(
                    json.optString("weather", ""),
                    json.optString("weath", ""),
                    json.optString("weathercode", "")
            );
            String temp = firstNonEmpty(
                    json.optString("temp", ""),
                    json.optString("temperature", ""),
                    json.optString("tempf", "")
            );
            String aqi = firstNonEmpty(
                    json.optString("aqi", ""),
                    json.optString("aqi_pm25", ""),
                    json.optString("aqi_pm10", "")
            );

            if (weather.length() == 0 && temp.length() == 0) {
                return null;
            }

            return Snapshot.valid(city, weather.length() == 0 ? "未知" : weather, cleanTemperature(temp), aqi, "");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private Snapshot requestMWeatherPage(String fallbackCityName, String cityCode) throws Exception {
        // 用户给的中国天气页面。主要作为兜底：能确认城市，若页面没有直接暴露实况数据则提示稍后刷新。
        String urlText = "https://e.weather.com.cn/mweather/" + cityCode + ".shtml";
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlText).openConnection();
            conn.setConnectTimeout(4500);
            conn.setReadTimeout(4500);
            conn.setUseCaches(false);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) MikuCarLauncher");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                return Snapshot.error(fallbackCityName, "天气获取失败");
            }

            String body = readAll(conn.getInputStream());
            String titleCity = findTitleCity(body);
            String city = titleCity.length() == 0 ? fallbackCityName : titleCity;

            // 尝试从页面脚本里抓天气与温度；若页面改版抓不到，也保持城市显示。
            String weather = findByRegex(body, "\"weather\"\\s*:\\s*\"([^\"]+)\"");
            String temp = findByRegex(body, "\"temperature\"\\s*:\\s*\"?([^\",}]+)");
            if (weather.length() == 0) {
                weather = findByRegex(body, "\"wea\"\\s*:\\s*\"([^\"]+)\"");
            }
            if (temp.length() == 0) {
                temp = findByRegex(body, "\"tem\"\\s*:\\s*\"?([^\",}]+)");
            }

            if (weather.length() > 0 || temp.length() > 0) {
                return Snapshot.valid(city, weather.length() == 0 ? "未知" : weather, cleanTemperature(temp), "", "");
            }

            return Snapshot.error(city, "天气读取中");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private JSONObject extractFirstJsonObject(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return new JSONObject(text.substring(start, end + 1));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String findTitleCity(String html) {
        String title = findByRegex(html, "<title>\\s*([^<]+)");
        if (title.length() == 0) {
            return "";
        }
        int idx = title.indexOf("天气");
        if (idx > 0) {
            title = title.substring(0, idx);
        }
        title = title.replace("〖", "").replace("〗", "").trim();
        return title;
    }

    private String findByRegex(String text, String regex) {
        if (text == null) {
            return "";
        }
        try {
            Matcher matcher = Pattern.compile(regex).matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private String cleanTemperature(String temp) {
        if (temp == null) {
            return "";
        }
        temp = temp.replace("℃", "").replace("°", "").trim();
        if (temp.endsWith(".0")) {
            temp = temp.substring(0, temp.length() - 2);
        }
        return temp;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return "";
    }

    private String readAll(InputStream input) throws Exception {
        if (input == null) {
            return "";
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(input, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        br.close();
        return sb.toString();
    }

    public static class Snapshot {
        public final boolean valid;
        public final boolean needsSetup;
        public final String city;
        public final String weather;
        public final String temperature;
        public final String aqi;
        public final String reportTime;
        public final String message;
        public final long updateElapsedMs;

        private Snapshot(boolean valid, boolean needsSetup, String city, String weather, String temperature, String aqi, String reportTime, String message) {
            this.valid = valid;
            this.needsSetup = needsSetup;
            this.city = city;
            this.weather = weather;
            this.temperature = temperature;
            this.aqi = aqi;
            this.reportTime = reportTime;
            this.message = message;
            this.updateElapsedMs = SystemClock.elapsedRealtime();
        }

        public static Snapshot empty() {
            return new Snapshot(false, false, DEFAULT_CITY_NAME, "", "", "", "", "天气读取中");
        }

        public static Snapshot error(String city, String msg) {
            return new Snapshot(false, false, city == null || city.length() == 0 ? DEFAULT_CITY_NAME : city, "", "", "", "", msg);
        }

        public static Snapshot valid(String city, String weather, String temperature, String aqi, String reportTime) {
            return new Snapshot(true, false,
                    city == null || city.length() == 0 ? DEFAULT_CITY_NAME : city,
                    weather == null ? "" : weather,
                    temperature == null ? "" : temperature,
                    aqi == null ? "" : aqi,
                    reportTime == null ? "" : reportTime,
                    "");
        }
    }
}
