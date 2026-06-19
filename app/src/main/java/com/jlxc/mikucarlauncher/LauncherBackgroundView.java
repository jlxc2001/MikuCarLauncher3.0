package com.jlxc.mikucarlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.view.View;

public class LauncherBackgroundView extends View {
    private static final String PREFS = MainActivity.PREFS;
    private static final float DESIGN_W = 2560f;
    private static final float DESIGN_H = 720f;

    private final Bitmap dayBackground;
    private final Bitmap nightBackground;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private Bitmap customDayBackground;
    private String customDayBackgroundUri;
    private Bitmap customNightBackground;
    private String customNightBackgroundUri;

    public LauncherBackgroundView(Context context) {
        super(context);
        dayBackground = BitmapFactory.decodeResource(getResources(), R.drawable.bg_a4l);
        nightBackground = BitmapFactory.decodeResource(getResources(), R.drawable.bg_a4l_night);
        setFocusable(false);
        setClickable(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Bitmap bg = getCurrentBackground();
        if (bg == null) {
            return;
        }
        float sx = getWidth() / DESIGN_W;
        float sy = getHeight() / DESIGN_H;
        canvas.save();
        canvas.scale(sx, sy);
        canvas.drawBitmap(bg, null, new RectF(0, 0, DESIGN_W, DESIGN_H), bitmapPaint);
        canvas.restore();
    }

    private Bitmap getCurrentBackground() {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (NightModeHelper.isNightMode(getContext())) {
            Bitmap fallbackNight = nightBackground != null ? nightBackground : dayBackground;
            String uriText = sp.getString("app_night_background_uri", "");
            if (uriText == null || uriText.length() == 0) {
                customNightBackgroundUri = "";
                customNightBackground = null;
                return fallbackNight;
            }
            if (uriText.equals(customNightBackgroundUri) && customNightBackground != null) {
                return customNightBackground;
            }
            customNightBackgroundUri = uriText;
            customNightBackground = loadBackgroundBitmap(uriText);
            return customNightBackground != null ? customNightBackground : fallbackNight;
        }

        String uriText = sp.getString("app_day_background_uri", sp.getString("app_background_uri", ""));
        if (uriText == null || uriText.length() == 0) {
            customDayBackgroundUri = "";
            customDayBackground = null;
            return dayBackground;
        }
        if (uriText.equals(customDayBackgroundUri) && customDayBackground != null) {
            return customDayBackground;
        }
        customDayBackgroundUri = uriText;
        customDayBackground = loadBackgroundBitmap(uriText);
        return customDayBackground != null ? customDayBackground : dayBackground;
    }

    private Bitmap loadBackgroundBitmap(String uriText) {
        try {
            java.io.InputStream input = getContext().getContentResolver().openInputStream(Uri.parse(uriText));
            if (input != null) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                input.close();
                return bitmap;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
