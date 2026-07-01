package com.jlxc.mikucarlauncher;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ComponentName;
import android.graphics.Typeface;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.provider.Settings;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LauncherCanvasView extends View {
    public interface OnMenuClickListener {
        void onMenuClick(int index, String label);
    }

    public interface OnLive2DClickListener {
        void onLive2DClick();
    }

    private static final String PREFS = MainActivity.PREFS;

    private OnMenuClickListener menuClickListener;
    private OnLive2DClickListener live2DClickListener;

    public void setOnMenuClickListener(OnMenuClickListener listener) {
        this.menuClickListener = listener;
    }

    public void setOnLive2DClickListener(OnLive2DClickListener listener) {
        this.live2DClickListener = listener;
    }

    public int getActiveIndex() {
        return activeIndex;
    }

    public void showHomePage() {
        activeIndex = 0;
        sidebarFocusIndex = 0;
        focusArea = 0;
        selectedCardIndex = 0;
        selectedMineRowIndex = 0;
        selectedCommonAppIndex = 0;
        commonAppSelectionVisible = false;
        hardwareFocusVisible = false;
        appSelectionVisible = false;
        pressedMusicButton = -1;
        invalidate();
    }

    public void invalidateAppIconCaches() {
        appListLoaded = false;
        appListLoading = false;
        cachedApps.clear();
        commonAppsCache.clear();
        lastCommonAppsLoadTime = 0L;
        invalidate();
    }

    public void preloadAppDrawerCache() {
        loadAppsIfNeeded();
    }

    public void setTurnSignalAudioAllowed(boolean allowed) {
        turnSignalAudioAllowed = allowed;
        if (!allowed) {
            try {
                turnSignalSoundManager.stop();
            } catch (Throwable ignored) {
            }
        }
    }

    // 固定 32:9 车机画布。背景图保持用户指定版本，不做裁切替换。
    private static final float DESIGN_W = 2560f;
    private static final float DESIGN_H = 720f;

    private final Bitmap background;
    private final Bitmap nightBackground;
    private Bitmap customDayBackground;
    private String customDayBackgroundUri;
    private Bitmap customNightBackground;
    private String customNightBackgroundUri;
    private final Bitmap selectedBg;
    private final Bitmap[] icons = new Bitmap[7];
    private final Bitmap btStatusIcon;
    private final Bitmap btBatteryIcon;
    private final Bitmap btSignalIcon;
    private final Bitmap btStatusGroupIcon;
    private final Bitmap phonePreviewIcon;
    private final Bitmap carTopViewBitmap;
    private final Bitmap carTopViewFrontRightOpenBitmap;
    private final Bitmap carTopViewRearRightOpenBitmap;
    private final Bitmap carTopViewRearLeftOpenBitmap;
    private final Bitmap carTopViewFrontLeftOpenBitmap;
    private final Bitmap weatherSunnyIcon;
    private final Bitmap weatherClearNightIcon;
    private final Bitmap weatherCloudyIcon;
    private final Bitmap weatherOvercastIcon;
    private final Bitmap weatherLightRainIcon;
    private final Bitmap weatherModerateRainIcon;
    private final Bitmap weatherHeavyRainIcon;
    private final Bitmap weatherStormRainIcon;
    private final Bitmap weatherThunderIcon;
    private final Bitmap weatherSnowIcon;
    private final Bitmap weatherSleetIcon;
    private final Bitmap weatherFogIcon;
    private final Bitmap weatherHazeIcon;
    private final Bitmap weatherDustIcon;
    private final Bitmap weatherWindIcon;
    private final Bitmap weatherUnknownIcon;
    private final VehicleDataProvider vehicleDataProvider;
    private final WeatherProvider weatherProvider;
    private final TurnSignalSoundManager turnSignalSoundManager;
    private final VehicleDataBroadcaster vehicleDataBroadcaster;
    private int turnSignalState = TurnSignalSoundManager.STATE_NONE;
    private long turnBlinkStartMs = 0L;
    private final String[] labels = {"首页", "导航", "音乐", "车辆", "全景", "应用", "我的"};
    private boolean drawBackgroundLayer = true;

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sidebarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint musicButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // 2号卡片音乐按钮按下反馈：0=上一曲，1=播放/暂停，2=下一曲。
    private int pressedMusicButton = -1;

    private int activeIndex = 0;

    // 侧边栏布局：按用户最新参考图重排。
    // 未选中项只显示图标和文字；选中项才显示背景图。
    private final float sidebarW = 176f;
    private final float btnX = 0f;
    private final float btnW = 138f;
    private final float selectedBtnW = sidebarW;
    private final float btnH = 58f;
    private final float iconSize = 28f;
    private final float iconX = 22f;
    private final float textX = 68f;
    // 7 个按钮纵向拉开，并保证顶部/底部留白一致。
    private final float startY = 52f;
    private final float gap = 35f;

    // 应用抽屉 / 我的 页面触摸记录。
    private float downDesignX = 0f;
    private float downDesignY = 0f;
    private long downTimeMs = 0L;

    private final List<AppEntry> cachedApps = new ArrayList<>();
    private long lastAppLoadTime = 0L;

    // 应用抽屉优化：不要在 UI 线程里同步扫描全部应用和图标。
    // 老版本进入应用抽屉时会 queryIntentActivities + loadIcon，低配车机会明显卡顿。
    // 现在改成后台线程加载，主线程只负责绘制已有缓存。
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean appListLoaded = false;
    private boolean appListLoading = false;
    private boolean appCacheRefreshRunning = false;
    private int appHiddenSignature = 0;
    private int appIconSignature = 0;
    private long appDrawerForceReloadToken = 0L;

    private final List<AppEntry> commonAppsCache = new ArrayList<>();
    private final List<RectF> commonAppHitRects = new ArrayList<>();
    private long lastCommonAppsLoadTime = 0L;
    private int commonIconSignature = 0;
    private long commonConfigSignature = 0L;

    // 应用抽屉分页与实体按键选择状态。
    private int appDrawerPage = 0;
    private int selectedAppIndex = 0;

    // 选项框只在实体按键操作后出现；用户触摸屏幕后自动隐藏。
    private boolean appSelectionVisible = false;

    // 应用抽屉左右滑动翻页动画。
    private boolean appPageAnimating = false;
    private int appAnimFromPage = 0;
    private int appAnimToPage = 0;
    private int appAnimDirection = 0;
    private long appAnimStartMs = 0L;
    private static final long APP_PAGE_ANIM_DURATION_MS = 260L;

    // 全局实体按键焦点：0=左侧按钮列，1=当前页面内容区。
    private boolean hardwareFocusVisible = false;
    private int focusArea = 0;
    private int sidebarFocusIndex = 0;
    private int selectedCardIndex = 0;
    private int selectedMineRowIndex = 0;
    private int selectedCommonAppIndex = 0;
    private boolean commonAppSelectionVisible = false;
    private boolean turnSignalAudioAllowed = true;

    public LauncherCanvasView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        background = BitmapFactory.decodeResource(getResources(), R.drawable.bg_a4l);
        nightBackground = BitmapFactory.decodeResource(getResources(), R.drawable.bg_a4l_night);
        selectedBg = BitmapFactory.decodeResource(getResources(), R.drawable.sidebar_selected_bg);
        icons[0] = BitmapFactory.decodeResource(getResources(), R.drawable.ic_home);
        icons[1] = BitmapFactory.decodeResource(getResources(), R.drawable.ic_nav);
        icons[2] = BitmapFactory.decodeResource(getResources(), R.drawable.ic_music);
        icons[3] = BitmapFactory.decodeResource(getResources(), R.drawable.ic_vehicle);
        icons[4] = BitmapFactory.decodeResource(getResources(), R.drawable.ic_panorama);
        icons[5] = BitmapFactory.decodeResource(getResources(), R.drawable.ic_apps);
        icons[6] = BitmapFactory.decodeResource(getResources(), R.drawable.ic_mine);

        btStatusIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_bt_status_hd);
        btBatteryIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_bt_battery_hd);
        btSignalIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_bt_signal_hd);
        btStatusGroupIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_bt_status_group_hd);
        phonePreviewIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_phone_hd);
        carTopViewBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.car_top_view_a4l);
        carTopViewFrontRightOpenBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.car_top_view_a4l_right_front_open);
        carTopViewRearRightOpenBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.car_top_view_a4l_right_rear_open);
        carTopViewRearLeftOpenBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.car_top_view_a4l_left_rear_open);
        carTopViewFrontLeftOpenBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.car_top_view_a4l_left_front_open);
        weatherSunnyIcon = BitmapFactory.decodeResource(getResources(), R.drawable.weather_sunny);
        weatherClearNightIcon = BitmapFactory.decodeResource(getResources(), R.drawable.weather_clear_night);
        weatherCloudyIcon = BitmapFactory.decodeResource(getResources(), R.drawable.weather_cloudy);
        weatherOvercastIcon = BitmapFactory.decodeResource(getResources(), R.drawable.weather_overcast);
        weatherLightRainIcon = BitmapFactory.decodeResource(getResources(), R.drawable.weather_light_rain);
        weatherModerateRainIcon = BitmapFactory.decodeResource(getResources(), R.drawable.weather_moderate_rain);
        weatherHeavyRainIcon = BitmapFactory.decodeResource(getResources(), R.drawable.weather_heavy_rain);
        weatherStormRainIcon = BitmapFactory.decodeResource(getResources(), R.drawable.weather_storm_rain);
        weatherThunderIcon = BitmapFactory.decodeResource(getResources(), R.drawable.weather_thunder);
        weatherSnowIcon = BitmapFactory.decodeResource(getResources(), R.drawable.weather_snow);
        weatherSleetIcon = BitmapFactory.decodeResource(getResources(), R.drawable.weather_sleet);
        weatherFogIcon = BitmapFactory.decodeResource(getResources(), R.drawable.weather_fog);
        weatherHazeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.weather_haze);
        weatherDustIcon = BitmapFactory.decodeResource(getResources(), R.drawable.weather_dust);
        weatherWindIcon = BitmapFactory.decodeResource(getResources(), R.drawable.weather_wind);
        weatherUnknownIcon = BitmapFactory.decodeResource(getResources(), R.drawable.weather_unknown);
        vehicleDataProvider = new VehicleDataProvider(context);
        weatherProvider = new WeatherProvider(context);
        turnSignalSoundManager = new TurnSignalSoundManager(context);
        vehicleDataBroadcaster = new VehicleDataBroadcaster(context, vehicleDataProvider);

        // v59：不在桌面启动时主动扫描应用，避免低速车规存储导致桌面启动/返回卡顿。
        // 应用抽屉会优先使用磁盘略缩图缓存；需要刷新时走设置里的手动按钮或安装卸载广播。

        vehicleDataProvider.start();
        vehicleDataBroadcaster.start();
        weatherProvider.start();
        mainHandler.post(turnSignalRunnable);

        textPaint.setColor(mainTextColor());
        textPaint.setTextSize(24f);
        textPaint.setFakeBoldText(false);
        textPaint.setTextAlign(Paint.Align.LEFT);

        activeTextPaint.setColor(accentColor());
        activeTextPaint.setTextSize(24f);
        activeTextPaint.setFakeBoldText(false);
        activeTextPaint.setTextAlign(Paint.Align.LEFT);

        // 左侧按钮列背景：比主背景略深一点的灰色，按用户参考图处理。
        sidebarPaint.setColor(sidebarColor());

        // 首页卡片。后续功能填充前，保持已确认白底卡片样式。
        cardPaint.setColor(Color.WHITE);

        titlePaint.setColor(mainTextColor());
        titlePaint.setTextSize(34f);
        titlePaint.setFakeBoldText(true);
        titlePaint.setTextAlign(Paint.Align.LEFT);

        subTextPaint.setColor(subTextColor());
        subTextPaint.setTextSize(24f);
        subTextPaint.setTextAlign(Paint.Align.LEFT);

        smallTextPaint.setColor(mutedTextColor());
        smallTextPaint.setTextSize(18f);
        smallTextPaint.setTextAlign(Paint.Align.CENTER);

        rowPaint.setColor(rowSurfaceColor());
        dividerPaint.setColor(dividerColor());
        dividerPaint.setStrokeWidth(2f);

        musicButtonPaint.setColor(Color.rgb(10, 10, 10));
        musicButtonPaint.setTextAlign(Paint.Align.CENTER);
        musicButtonPaint.setTextSize(28f);
        musicButtonPaint.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public void setDrawBackgroundLayer(boolean drawBackgroundLayer) {
        this.drawBackgroundLayer = drawBackgroundLayer;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float sx = getWidth() / DESIGN_W;
        float sy = getHeight() / DESIGN_H;
        canvas.save();
        canvas.scale(sx, sy);
        drawDesign(canvas);
        canvas.restore();
    }

    private void drawAppBackground(Canvas c) {
        Bitmap bg = getCurrentBackground();
        c.drawBitmap(bg, null, new RectF(0, 0, DESIGN_W, DESIGN_H), bitmapPaint);
    }

    private Bitmap getCurrentBackground() {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (isNightMode()) {
            Bitmap fallbackNight = nightBackground != null ? nightBackground : background;
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

        // 兼容旧版本：如果用户之前设置过 app_background_uri，则作为日间背景继续使用。
        String uriText = sp.getString("app_day_background_uri", sp.getString("app_background_uri", ""));
        if (uriText == null || uriText.length() == 0) {
            customDayBackgroundUri = "";
            customDayBackground = null;
            return background;
        }
        if (uriText.equals(customDayBackgroundUri) && customDayBackground != null) {
            return customDayBackground;
        }
        customDayBackgroundUri = uriText;
        customDayBackground = loadBackgroundBitmap(uriText);
        return customDayBackground != null ? customDayBackground : background;
    }

    private Bitmap loadBackgroundBitmap(String uriText) {
        try {
            android.content.ContentResolver resolver = getContext().getContentResolver();
            java.io.InputStream input = resolver.openInputStream(Uri.parse(uriText));
            if (input != null) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                input.close();
                return bitmap;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean isNightMode() {
        return NightModeHelper.isNightMode(getContext());
    }

    private void applyThemeColors() {
        boolean night = isNightMode();

        textPaint.setColor(night ? Color.rgb(232, 238, 248) : mainTextColor());
        activeTextPaint.setColor(accentColor());

        sidebarPaint.setColor(sidebarColor());
        cardPaint.setColor(surfaceColor());

        titlePaint.setColor(mainTextColor());
        subTextPaint.setColor(subTextColor());
        smallTextPaint.setColor(mutedTextColor());

        rowPaint.setColor(rowSurfaceColor());
        dividerPaint.setColor(dividerColor());
        musicButtonPaint.setColor(mainTextColor());
    }

    private int accentColor() {
        return isNightMode() ? Color.rgb(80, 158, 255) : Color.rgb(46, 120, 255);
    }

    private int mainTextColor() {
        return isNightMode() ? Color.rgb(236, 241, 250) : Color.rgb(20, 20, 20);
    }

    private int subTextColor() {
        return isNightMode() ? Color.rgb(204, 214, 228) : Color.rgb(55, 55, 55);
    }

    private int mutedTextColor() {
        return isNightMode() ? Color.rgb(154, 166, 186) : Color.rgb(95, 95, 95);
    }

    private int sidebarColor() {
        return isNightMode() ? Color.rgb(18, 24, 35) : Color.rgb(233, 236, 242);
    }

    private int surfaceColor() {
        return isNightMode() ? Color.rgb(30, 38, 52) : Color.WHITE;
    }

    private int rowSurfaceColor() {
        return isNightMode() ? Color.rgb(39, 49, 66) : Color.rgb(247, 248, 251);
    }

    private int selectedSurfaceColor() {
        return isNightMode() ? Color.rgb(37, 51, 72) : Color.rgb(235, 243, 255);
    }

    private int placeholderSurfaceColor() {
        return isNightMode() ? Color.rgb(48, 58, 74) : Color.rgb(226, 232, 239);
    }

    private int dividerColor() {
        return isNightMode() ? Color.rgb(58, 69, 88) : Color.rgb(232, 235, 241);
    }

    private int mutedDotColor() {
        return isNightMode() ? Color.rgb(94, 108, 130) : Color.rgb(190, 195, 205);
    }

    private int iconLineColor() {
        return isNightMode() ? Color.rgb(220, 229, 244) : Color.rgb(80, 90, 105);
    }

    private int greenColor() {
        return Color.rgb(80, 210, 95);
    }

    private void drawDesign(Canvas c) {
        applyThemeColors();
        if (drawBackgroundLayer) {
            drawAppBackground(c);
        }

        // 左侧功能列底板，无论在哪个页面都一直保留。
        c.drawRect(0, 0, sidebarW, DESIGN_H, sidebarPaint);

        if (activeIndex == 5) {
            drawAppDrawerPage(c);
        } else if (activeIndex == 6) {
            drawMinePage(c);
        } else {
            drawHomeCards(c);
        }

        for (int i = 0; i < labels.length; i++) {
            drawMenuItem(c, i);
        }

        drawTurnSignalOverlay(c);
        drawTurnDebugOverlay(c);
    }

    private void drawHomeCards(Canvas c) {
        // 首页 1~6 号卡片：按用户最新确认的 V72.1 布局。
        // 1号卡片右侧拉长到与4号卡片右侧对齐；2/3号卡片右移到与5号卡片左侧对齐。
        RectF leftCard = new RectF(210f, 35.5f, 1140f, 528.5f);
        RectF rightTopCard = new RectF(1158f, 35.5f, 1550f, 350.5f);
        RectF rightBottomCard = new RectF(1158f, 368.5f, 1550f, 528.5f);

        RectF bottomLeftCard = new RectF(210f, 546.5f, 1140f, 684.5f);
        RectF bottomMiddleCard = new RectF(1158f, 546.5f, 1952f, 684.5f);
        RectF bottomRightCard = new RectF(1970f, 546.5f, 2540f, 684.5f);

        float radius = 18f;
        c.drawRoundRect(leftCard, radius, radius, cardPaint);
        c.drawRoundRect(rightTopCard, radius, radius, cardPaint);
        c.drawRoundRect(rightBottomCard, radius, radius, cardPaint);
        c.drawRoundRect(bottomLeftCard, radius, radius, cardPaint);
        c.drawRoundRect(bottomMiddleCard, radius, radius, cardPaint);
        c.drawRoundRect(bottomRightCard, radius, radius, cardPaint);

        drawAmapFloatingCardHint(c, leftCard);

        drawMusicPlayerCard(c, rightTopCard);
        drawBluetoothCard(c, rightBottomCard);
        drawCommonAppsCard(c, bottomLeftCard);
        drawVehicleStatusCard(c, bottomMiddleCard);
        drawWeatherCard(c, bottomRightCard);
        drawGreetingArea(c);

        if (hardwareFocusVisible && focusArea == 1 && activeIndex == 0) {
            if (selectedCardIndex == 3 && commonAppSelectionVisible && !commonAppHitRects.isEmpty()) {
                selectedCommonAppIndex = clamp(selectedCommonAppIndex, 0, commonAppHitRects.size() - 1);
                drawFocusStroke(c, commonAppHitRects.get(selectedCommonAppIndex));
            } else {
                RectF[] cards = new RectF[]{leftCard, rightTopCard, rightBottomCard, bottomLeftCard, bottomMiddleCard, bottomRightCard};
                drawFocusStroke(c, cards[clamp(selectedCardIndex, 0, cards.length - 1)]);
            }
        }
    }

    private void drawAmapFloatingCardHint(Canvas c, RectF card) {
        subTextPaint.setTextAlign(Paint.Align.CENTER);

        boolean installed = AmapFloatingCardController.isAmapFloatingInstalled(getContext());
        if (!installed) {
            subTextPaint.setTextSize(24f);
            subTextPaint.setColor(mutedTextColor());
            c.drawText("未安装悬浮版高德地图", (card.left + card.right) / 2f, 222f, subTextPaint);
            subTextPaint.setTextSize(18f);
            c.drawText("请安装包名 com.autonavi.amapautoys", (card.left + card.right) / 2f, 258f, subTextPaint);
        } else {
            subTextPaint.setTextSize(20f);
            subTextPaint.setColor(mutedTextColor());
            c.drawText("悬浮高德地图显示区域", (card.left + card.right) / 2f, card.bottom - 38f, subTextPaint);
        }

        subTextPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawMusicPlayerCard(Canvas c, RectF card) {
        MusicInfo musicInfo = getCurrentMusicInfo();

        titlePaint.setTextAlign(Paint.Align.LEFT);
        titlePaint.setTextSize(22f);
        titlePaint.setFakeBoldText(true);
        titlePaint.setColor(mainTextColor());
        c.drawText("音乐", card.left + 28f, card.top + 42f, titlePaint);

        titlePaint.setTextSize(28f);
        titlePaint.setFakeBoldText(false);
        titlePaint.setTextAlign(Paint.Align.RIGHT);
        c.drawText("›", card.right - 28f, card.top + 44f, titlePaint);
        titlePaint.setTextAlign(Paint.Align.LEFT);

        boolean hasPermission = isNotificationListenerEnabled();
        if (!hasPermission) {
            subTextPaint.setTextAlign(Paint.Align.LEFT);
            subTextPaint.setTextSize(22f);
            subTextPaint.setColor(mainTextColor());
            c.drawText("未获取到播放信息", card.left + 28f, card.top + 92f, subTextPaint);

            subTextPaint.setTextSize(18f);
            subTextPaint.setColor(mutedTextColor());
            c.drawText("需要开启通知读取权限", card.left + 28f, card.top + 124f, subTextPaint);

            RectF authBtn = getMusicPermissionButtonRect();
            Paint authPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            authPaint.setColor(selectedSurfaceColor());
            c.drawRoundRect(authBtn, 14f, 14f, authPaint);

            Paint authStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            authStroke.setStyle(Paint.Style.STROKE);
            authStroke.setStrokeWidth(2f);
            authStroke.setColor(accentColor());
            c.drawRoundRect(authBtn, 14f, 14f, authStroke);

            subTextPaint.setTextAlign(Paint.Align.CENTER);
            subTextPaint.setTextSize(20f);
            subTextPaint.setColor(accentColor());
            c.drawText("开启音乐信息权限", (authBtn.left + authBtn.right) / 2f, authBtn.top + 38f, subTextPaint);
            subTextPaint.setTextAlign(Paint.Align.LEFT);
            return;
        }

        String title = musicInfo.title == null || musicInfo.title.length() == 0 ? "未获取到播放信息" : musicInfo.title;
        String artist = musicInfo.artist == null || musicInfo.artist.length() == 0 ? "请播放音乐" : musicInfo.artist;

        subTextPaint.setTextAlign(Paint.Align.LEFT);
        subTextPaint.setTextSize(23f);
        subTextPaint.setFakeBoldText(true);
        subTextPaint.setColor(mainTextColor());
        drawTextEllipsize(c, title, card.left + 28f, card.top + 88f, subTextPaint, card.width() - 70f);

        subTextPaint.setFakeBoldText(false);
        subTextPaint.setTextSize(19f);
        subTextPaint.setColor(subTextColor());
        drawTextEllipsize(c, artist, card.left + 28f, card.top + 120f, subTextPaint, card.width() - 70f);

        RectF cover = getMusicCoverRect();
        if (musicInfo.cover != null) {
            drawRoundedBitmap(c, musicInfo.cover, cover, 8f);
        } else {
            Paint coverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            coverPaint.setColor(placeholderSurfaceColor());
            c.drawRoundRect(cover, 10f, 10f, coverPaint);
            smallTextPaint.setTextAlign(Paint.Align.CENTER);
            smallTextPaint.setTextSize(16f);
            smallTextPaint.setColor(mutedTextColor());
            c.drawText("封面", (cover.left + cover.right) / 2f, (cover.top + cover.bottom) / 2f + 6f, smallTextPaint);
        }

        drawMusicControls(c, card, musicInfo.playing);

        // 首页常驻时每秒刷新一次播放信息。
        postInvalidateDelayed(1000L);
    }

    private RectF getMusicCoverRect() {
        return new RectF(1186f, 166f, 1288f, 268f);
    }

    private RectF getMusicPermissionButtonRect() {
        return new RectF(1186f, 154f, 1468f, 210f);
    }

    private RectF getMusicPrevButtonRect() {
        return new RectF(1180f, 292f, 1236f, 346f);
    }

    private RectF getMusicPlayButtonRect() {
        return new RectF(1326f, 292f, 1382f, 346f);
    }

    private RectF getMusicNextButtonRect() {
        return new RectF(1472f, 292f, 1528f, 346f);
    }

    private RectF getMusicOpenButtonRect() {
        return new RectF(1492f, 46f, 1548f, 92f);
    }

    private void drawMusicControls(Canvas c, RectF card, boolean playing) {
        musicButtonPaint.setColor(Color.rgb(10, 10, 10));
        musicButtonPaint.setTextSize(28f);
        musicButtonPaint.setTypeface(Typeface.DEFAULT_BOLD);

        RectF prev = getMusicPrevButtonRect();
        RectF play = getMusicPlayButtonRect();
        RectF next = getMusicNextButtonRect();

        drawMusicPressedFeedback(c, prev, 0);
        drawMusicPressedFeedback(c, play, 1);
        drawMusicPressedFeedback(c, next, 2);

        // 图标本身比上一版缩小约 5px，但触摸热区不变，车机上更容易点。
        drawPrevIcon(c, shrinkRect(prev, 5f));
        if (playing) {
            drawPauseIcon(c, shrinkRect(play, 5f));
        } else {
            drawPlayIcon(c, shrinkRect(play, 5f));
        }
        drawNextIcon(c, shrinkRect(next, 5f));
    }

    private RectF shrinkRect(RectF r, float px) {
        return new RectF(r.left + px, r.top + px, r.right - px, r.bottom - px);
    }

    private void drawMusicPressedFeedback(Canvas c, RectF r, int index) {
        if (pressedMusicButton != index) {
            return;
        }

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(placeholderSurfaceColor());
        c.drawRoundRect(r, 10f, 10f, fill);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f);
        stroke.setColor(Color.rgb(120, 140, 170));
        c.drawRoundRect(r, 10f, 10f, stroke);
    }

    private void drawPrevIcon(Canvas c, RectF r) {
        Paint p = musicButtonPaint;
        p.setStyle(Paint.Style.FILL);
        float cy = (r.top + r.bottom) / 2f;

        // 视觉尺寸比热区小，竖条和三角形分离，避免糊成一团。
        c.drawRoundRect(new RectF(r.left + 8f, cy - 14f, r.left + 13f, cy + 14f), 1.5f, 1.5f, p);
        PathWrapper.drawTriangle(c, p,
                r.left + 36f, cy - 16f,
                r.left + 16f, cy,
                r.left + 36f, cy + 16f);
    }

    private void drawNextIcon(Canvas c, RectF r) {
        Paint p = musicButtonPaint;
        p.setStyle(Paint.Style.FILL);
        float cy = (r.top + r.bottom) / 2f;

        // 修复下一首图标：竖条放在三角形右侧，不再穿进三角形里。
        PathWrapper.drawTriangle(c, p,
                r.left + 14f, cy - 16f,
                r.left + 34f, cy,
                r.left + 14f, cy + 16f);
        c.drawRoundRect(new RectF(r.left + 38f, cy - 14f, r.left + 43f, cy + 14f), 1.5f, 1.5f, p);
    }

    private void drawPlayIcon(Canvas c, RectF r) {
        Paint p = musicButtonPaint;
        float cy = (r.top + r.bottom) / 2f;
        PathWrapper.drawTriangle(c, p,
                r.left + 20f, cy - 18f,
                r.left + 20f, cy + 18f,
                r.left + 44f, cy);
    }

    private void drawPauseIcon(Canvas c, RectF r) {
        Paint p = musicButtonPaint;
        float cy = (r.top + r.bottom) / 2f;
        c.drawRoundRect(new RectF(r.left + 18f, cy - 18f, r.left + 26f, cy + 18f), 3f, 3f, p);
        c.drawRoundRect(new RectF(r.left + 34f, cy - 18f, r.left + 42f, cy + 18f), 3f, 3f, p);
    }

    private void drawTextEllipsize(Canvas c, String text, float x, float y, Paint paint, float maxWidth) {
        if (text == null) {
            text = "";
        }
        String result = text;
        while (paint.measureText(result) > maxWidth && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        if (!result.equals(text) && result.length() > 1) {
            result = result.substring(0, Math.max(1, result.length() - 1)) + "…";
        }
        c.drawText(result, x, y, paint);
    }

    private boolean isNotificationListenerEnabled() {
        try {
            String enabled = Settings.Secure.getString(
                    getContext().getContentResolver(),
                    "enabled_notification_listeners"
            );
            return enabled != null && enabled.toLowerCase().contains(getContext().getPackageName().toLowerCase());
        } catch (Throwable t) {
            return false;
        }
    }

    private MusicInfo getCurrentMusicInfo() {
        MusicInfo info = new MusicInfo();
        MediaController controller = getMusicController();
        if (controller == null) {
            return info;
        }

        try {
            PlaybackState state = controller.getPlaybackState();
            info.playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;

            MediaMetadata metadata = controller.getMetadata();
            if (metadata != null) {
                CharSequence title = metadata.getText(MediaMetadata.METADATA_KEY_TITLE);
                CharSequence artist = metadata.getText(MediaMetadata.METADATA_KEY_ARTIST);
                if (artist == null || artist.length() == 0) {
                    artist = metadata.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                }

                info.title = title == null ? "" : title.toString();
                info.artist = artist == null ? "" : artist.toString();
                info.cover = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (info.cover == null) {
                    info.cover = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
                }
                if (info.cover == null) {
                    info.cover = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                }
            }
        } catch (Throwable ignored) {
        }

        return info;
    }

    private MediaController getMusicController() {
        if (!isNotificationListenerEnabled()) {
            return null;
        }

        try {
            MediaSessionManager manager = (MediaSessionManager) getContext().getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) {
                return null;
            }

            ComponentName componentName = new ComponentName(getContext(), MusicNotificationListenerService.class);
            List<MediaController> controllers = manager.getActiveSessions(componentName);
            if (controllers == null || controllers.isEmpty()) {
                return null;
            }

            MediaController fallback = null;
            for (MediaController controller : controllers) {
                if (controller == null) continue;
                PlaybackState state = controller.getPlaybackState();
                MediaMetadata metadata = controller.getMetadata();

                if (metadata != null && fallback == null) {
                    fallback = controller;
                }

                if (state != null && metadata != null) {
                    int s = state.getState();
                    if (s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_PAUSED || s == PlaybackState.STATE_BUFFERING) {
                        return controller;
                    }
                }
            }

            return fallback;
        } catch (Throwable t) {
            return null;
        }
    }

    private void controlMusic(int action) {
        MediaController controller = getMusicController();
        if (controller == null) {
            Toast.makeText(getContext(), "未获取到音乐控制器，请先开启通知读取权限并播放音乐", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            MediaController.TransportControls controls = controller.getTransportControls();
            if (controls == null) {
                return;
            }

            if (action == 0) {
                controls.skipToPrevious();
            } else if (action == 1) {
                PlaybackState state = controller.getPlaybackState();
                boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
                if (playing) {
                    controls.pause();
                } else {
                    controls.play();
                }
            } else if (action == 2) {
                controls.skipToNext();
            }
            invalidate();
        } catch (Throwable t) {
            Toast.makeText(getContext(), "音乐控制失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void closeAmapFloatingMap() {
        try {
            AmapFloatingCardController.sendCloseMapBroadcast(getContext());
        } catch (Throwable ignored) {
        }
    }

    private void openNotificationListenerSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            closeAmapFloatingMap();
            getContext().startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(getContext(), "无法打开通知读取权限设置", Toast.LENGTH_SHORT).show();
        }
    }

    private void openDefaultMusicApp() {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String pkg = sp.getString("music_package", "");
        if (pkg != null && pkg.length() > 0) {
            try {
                Intent intent = getContext().getPackageManager().getLaunchIntentForPackage(pkg);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    closeAmapFloatingMap();
                    getContext().startActivity(intent);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }

        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.ts.MainUI", "com.ts.bt.BtMusicActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            closeAmapFloatingMap();
            getContext().startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(getContext(), "无法打开音乐软件", Toast.LENGTH_SHORT).show();
        }
    }

    private static class MusicInfo {
        String title = "";
        String artist = "";
        Bitmap cover;
        boolean playing = false;
    }

    private static class PathWrapper {
        static void drawTriangle(Canvas c, Paint p, float x1, float y1, float x2, float y2, float x3, float y3) {
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(x1, y1);
            path.lineTo(x2, y2);
            path.lineTo(x3, y3);
            path.close();
            c.drawPath(path, p);
        }
    }

    private void drawVehicleStatusCard(Canvas c, RectF card) {
        VehicleDataProvider.Snapshot data = vehicleDataProvider == null
                ? VehicleDataProvider.Snapshot.empty()
                : vehicleDataProvider.getSnapshot();

        titlePaint.setTextAlign(Paint.Align.LEFT);
        titlePaint.setTextSize(19f);
        titlePaint.setFakeBoldText(true);
        titlePaint.setColor(subTextColor());
        c.drawText("续航里程", card.left + 34f, card.top + 33f, titlePaint);

        titlePaint.setFakeBoldText(true);
        titlePaint.setTextSize(32f);
        titlePaint.setColor(mainTextColor());
        String rangeText = (data.valid && data.rangeKm >= 0) ? String.valueOf(data.rangeKm) : "--";
        float rangeX = card.left + 34f;
        float rangeY = card.top + 76f;

        // 先用大号数字字体测量宽度，再切到小号 km 字体。
        // 上一版是切到 18f 之后才 measureText，导致 "--" 和 "km" 挤在一起。
        float rangeTextWidth = titlePaint.measureText(rangeText);
        c.drawText(rangeText, rangeX, rangeY, titlePaint);

        titlePaint.setFakeBoldText(false);
        titlePaint.setTextSize(18f);
        titlePaint.setColor(subTextColor());
        c.drawText("km", rangeX + rangeTextWidth + 12f, rangeY, titlePaint);

        subTextPaint.setTextAlign(Paint.Align.LEFT);
        subTextPaint.setTextSize(18f);
        subTextPaint.setColor(subTextColor());
        String status;
        if (!data.valid || data.rangeKm < 0) {
            status = "读取中";
        } else if (data.rangeKm >= 100) {
            status = "续航充足";
        } else if (data.rangeKm >= 50) {
            status = "建议加油";
        } else {
            status = "燃油不足";
        }
        c.drawText(status, card.left + 34f, card.top + 106f, subTextPaint);

        RectF carRect = getCard5CarRect(card);
        Bitmap carBitmap = getVehicleTopViewBitmap(data);
        drawBitmapFitCenter(c, carBitmap, carRect);
        drawVehicleDoorFeedback(c, carRect, data);

        // 车辆数据低频刷新，绘制层只做轻量重绘。
        postInvalidateDelayed(900L);
    }

    private Bitmap getVehicleTopViewBitmap(VehicleDataProvider.Snapshot data) {
        if (data == null || !data.valid) {
            return carTopViewBitmap;
        }

        // 先按用户给的四张实车俯视图切换单门打开状态。
        // 如果同时有多门开启，先按前左 -> 前右 -> 后左 -> 后右优先显示其中一张，
        // 其余门位仍用下方的轻量反馈继续提示。
        if (data.frontLeftDoorOpen && carTopViewFrontLeftOpenBitmap != null) {
            return carTopViewFrontLeftOpenBitmap;
        }
        if (data.frontRightDoorOpen && carTopViewFrontRightOpenBitmap != null) {
            return carTopViewFrontRightOpenBitmap;
        }
        if (data.rearLeftDoorOpen && carTopViewRearLeftOpenBitmap != null) {
            return carTopViewRearLeftOpenBitmap;
        }
        if (data.rearRightDoorOpen && carTopViewRearRightOpenBitmap != null) {
            return carTopViewRearRightOpenBitmap;
        }
        return carTopViewBitmap;
    }

    private RectF getCard5CarRect(RectF card) {
        return new RectF(card.left + 286f, card.top + 12f, card.right - 60f, card.bottom - 12f);
    }

    private void drawVehicleDoorFeedback(Canvas c, RectF carRect, VehicleDataProvider.Snapshot data) {
        if (data == null || !data.valid) {
            return;
        }

        boolean anyOpen = data.frontLeftDoorOpen
                || data.frontRightDoorOpen
                || data.rearLeftDoorOpen
                || data.rearRightDoorOpen
                || data.trunkOpen
                || data.hoodOpen;

        boolean usingRealDoorBitmap = (data.frontLeftDoorOpen && carTopViewFrontLeftOpenBitmap != null)
                || (data.frontRightDoorOpen && carTopViewFrontRightOpenBitmap != null)
                || (data.rearLeftDoorOpen && carTopViewRearLeftOpenBitmap != null)
                || (data.rearRightDoorOpen && carTopViewRearRightOpenBitmap != null);

        if (!anyOpen) {
            return;
        }

        float pulse = (float) ((Math.sin(System.currentTimeMillis() / 180.0) + 1.0) * 0.5);
        int alpha = 120 + (int) (70f * pulse);

        Paint doorFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        doorFill.setStyle(Paint.Style.FILL);
        doorFill.setColor(Color.argb(alpha, 46, 120, 255));

        Paint doorStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        doorStroke.setStyle(Paint.Style.STROKE);
        doorStroke.setStrokeWidth(2.2f);
        doorStroke.setColor(Color.argb(220, 46, 120, 255));

        // 已接入你提供的四张开门俯视图。若当前门位有专用图，就不再额外叠蓝色门板；
        // 若以后出现多门同开、或没有对应素材的门位，再回退到轻量门板反馈。
        if (!usingRealDoorBitmap) {
            if (data.frontLeftDoorOpen) {
                drawDoorPanel(c, doorFill, doorStroke,
                        carRect.left + carRect.width() * 0.30f,
                        carRect.top + carRect.height() * 0.34f,
                        carRect.width() * 0.17f,
                        -1);
            }
            if (data.rearLeftDoorOpen) {
                drawDoorPanel(c, doorFill, doorStroke,
                        carRect.left + carRect.width() * 0.53f,
                        carRect.top + carRect.height() * 0.34f,
                        carRect.width() * 0.17f,
                        -1);
            }
            if (data.frontRightDoorOpen) {
                drawDoorPanel(c, doorFill, doorStroke,
                        carRect.left + carRect.width() * 0.30f,
                        carRect.top + carRect.height() * 0.66f,
                        carRect.width() * 0.17f,
                        1);
            }
            if (data.rearRightDoorOpen) {
                drawDoorPanel(c, doorFill, doorStroke,
                        carRect.left + carRect.width() * 0.53f,
                        carRect.top + carRect.height() * 0.66f,
                        carRect.width() * 0.17f,
                        1);
            }
        }

        if (data.hoodOpen) {
            RectF hood = new RectF(
                    carRect.left + carRect.width() * 0.06f,
                    carRect.top + carRect.height() * 0.35f,
                    carRect.left + carRect.width() * 0.22f,
                    carRect.top + carRect.height() * 0.65f
            );
            c.drawRoundRect(hood, 5f, 5f, doorFill);
            c.drawRoundRect(hood, 5f, 5f, doorStroke);
        }

        if (data.trunkOpen) {
            RectF trunk = new RectF(
                    carRect.left + carRect.width() * 0.78f,
                    carRect.top + carRect.height() * 0.35f,
                    carRect.left + carRect.width() * 0.94f,
                    carRect.top + carRect.height() * 0.65f
            );
            c.drawRoundRect(trunk, 5f, 5f, doorFill);
            c.drawRoundRect(trunk, 5f, 5f, doorStroke);
        }

        postInvalidateDelayed(180L);
    }

    private void drawDoorPanel(Canvas c, Paint fill, Paint stroke, float hingeX, float hingeY, float doorLength, int side) {
        float doorHeight = 16f;
        float openOffset = side < 0 ? -24f : 24f;

        Path path = new Path();
        path.moveTo(hingeX, hingeY);
        path.lineTo(hingeX + doorLength, hingeY + openOffset);
        path.lineTo(hingeX + doorLength, hingeY + openOffset + side * doorHeight);
        path.lineTo(hingeX, hingeY + side * doorHeight);
        path.close();

        c.drawPath(path, fill);
        c.drawPath(path, stroke);
    }

    private void drawGreetingArea(Canvas c) {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String ownerName = sp.getString("owner_name", "奥迪");
        String signature = sp.getString("signature", "专注当下，尽享驾趣");
        if (ownerName == null || ownerName.trim().length() == 0) {
            ownerName = "奥迪";
        }
        if (signature == null || signature.trim().length() == 0) {
            signature = "专注当下，尽享驾趣";
        }

        String hello = getTimeGreeting() + "， " + ownerName;

        titlePaint.setTextAlign(Paint.Align.LEFT);
        titlePaint.setFakeBoldText(true);
        titlePaint.setTextSize(43f);
        titlePaint.setColor(mainTextColor());
        drawTextEllipsize(c, hello, 1600f, 142f, titlePaint, 480f);

        subTextPaint.setTextAlign(Paint.Align.LEFT);
        subTextPaint.setTextSize(21f);
        subTextPaint.setColor(subTextColor());
        drawTextEllipsize(c, signature, 1604f, 184f, subTextPaint, 480f);
    }

    private String getTimeGreeting() {
        try {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            return hour < 12 ? "上午好" : "下午好";
        } catch (Throwable ignored) {
            return "下午好";
        }
    }

    private void drawWeatherCard(Canvas c, RectF card) {
        WeatherProvider.Snapshot weather = weatherProvider == null
                ? WeatherProvider.Snapshot.empty()
                : weatherProvider.getSnapshot();

        String city = weather.city == null || weather.city.length() == 0 ? "萍乡" : weather.city;
        String weatherText = weather.valid ? weather.weather : weather.message;
        if (weatherText == null || weatherText.length() == 0) {
            weatherText = "天气读取中";
        }

        titlePaint.setTextAlign(Paint.Align.LEFT);
        titlePaint.setFakeBoldText(true);
        titlePaint.setTextSize(20f);
        titlePaint.setColor(mainTextColor());
        c.drawText("天气", card.left + 28f, card.top + 35f, titlePaint);

        subTextPaint.setTextAlign(Paint.Align.LEFT);
        subTextPaint.setTextSize(18f);
        subTextPaint.setColor(mutedTextColor());
        drawTextEllipsize(c, city, card.left + 28f, card.top + 66f, subTextPaint, card.width() - 168f);

        titlePaint.setFakeBoldText(true);
        titlePaint.setTextSize(29f);
        titlePaint.setColor(mainTextColor());
        String mainLine;
        if (weather.valid) {
            String temp = weather.temperature == null || weather.temperature.length() == 0 ? "--" : weather.temperature;
            mainLine = weather.weather + "  " + temp + "℃";
        } else {
            mainLine = weather.needsSetup ? "请设置天气" : weatherText;
        }
        drawTextEllipsize(c, mainLine, card.left + 28f, card.top + 106f, titlePaint, card.width() - 168f);

        subTextPaint.setTextSize(16f);
        subTextPaint.setColor(mutedTextColor());
        String bottomLine = weather.valid
                ? (weather.aqi != null && weather.aqi.length() > 0 ? ("空气 " + weather.aqi) : "中国天气")
                : "稍后重试";
        c.drawText(bottomLine, card.left + 28f, card.top + 132f, subTextPaint);

        Bitmap icon = pickWeatherIcon(weather.valid ? weather.weather : "");
        RectF iconRect = new RectF(card.right - 146f, card.top + 14f, card.right - 22f, card.bottom - 12f);
        drawBitmapFitCenter(c, icon, iconRect);

        postInvalidateDelayed(weather.valid ? 60_000L : 3_000L);
    }

    private Bitmap pickWeatherIcon(String condition) {
        if (condition == null) {
            return weatherUnknownIcon;
        }

        String w = condition.trim();

        if (w.contains("雷")) return weatherThunderIcon;
        if (w.contains("暴雨") || w.contains("大暴雨") || w.contains("特大暴雨")) return weatherStormRainIcon;
        if (w.contains("大雨")) return weatherHeavyRainIcon;
        if (w.contains("中雨")) return weatherModerateRainIcon;
        if (w.contains("小雨") || w.contains("阵雨") || w.contains("毛毛雨") || w.contains("细雨")) return weatherLightRainIcon;
        if (w.contains("雨夹雪") || w.contains("冻雨")) return weatherSleetIcon;
        if (w.contains("雪")) return weatherSnowIcon;
        if (w.contains("雾")) return weatherFogIcon;
        if (w.contains("霾")) return weatherHazeIcon;
        if (w.contains("沙") || w.contains("尘")) return weatherDustIcon;
        if (w.contains("风")) return weatherWindIcon;
        if (w.contains("阴")) return weatherOvercastIcon;
        if (w.contains("云")) return weatherCloudyIcon;
        if (w.contains("晴")) return isNightNow() ? weatherClearNightIcon : weatherSunnyIcon;

        return weatherUnknownIcon;
    }

    private boolean isNightNow() {
        try {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            return hour >= 18 || hour < 6;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void drawCommonAppsCard(Canvas c, RectF card) {
        loadCommonAppsIfNeeded();
        commonAppHitRects.clear();

        if (commonAppsCache.isEmpty()) {
            subTextPaint.setTextAlign(Paint.Align.CENTER);
            subTextPaint.setTextSize(22f);
            subTextPaint.setColor(mutedTextColor());
            c.drawText("暂未设置常用软件", (card.left + card.right) / 2f, card.top + 54f, subTextPaint);
            subTextPaint.setTextSize(18f);
            subTextPaint.setColor(accentColor());
            c.drawText("请到 我的 → 车机桌面设置 里添加", (card.left + card.right) / 2f, card.top + 90f, subTextPaint);
            subTextPaint.setTextAlign(Paint.Align.LEFT);
            return;
        }

        int count = commonAppsCache.size();
        float leftPad = 24f;
        float rightPad = 24f;
        float cellW = (card.width() - leftPad - rightPad) / count;
        float cellTop = card.top + 10f;
        float cellBottom = card.bottom - 8f;
        float iconSize = 54f;

        for (int i = 0; i < count; i++) {
            AppEntry app = commonAppsCache.get(i);
            float cellLeft = card.left + leftPad + i * cellW;
            float cellRight = cellLeft + cellW;
            float centerX = (cellLeft + cellRight) / 2f;
            float iconLeft = centerX - iconSize / 2f;
            float iconTop = card.top + 18f;
            if (app.isTextDisplayShortcut()) {
                drawMikuTextShortcutIcon(c, new RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize), app.iconText, app.isShoutShortcut());
            } else if (app.icon != null) {
                app.icon.setBounds((int) iconLeft, (int) iconTop, (int) (iconLeft + iconSize), (int) (iconTop + iconSize));
                app.icon.draw(c);
            }

            smallTextPaint.setTextAlign(Paint.Align.CENTER);
            smallTextPaint.setTextSize(18f);
            smallTextPaint.setColor(mainTextColor());
            drawCenteredTextSingleLine(c, app.label, centerX, card.top + 104f, smallTextPaint, cellW - 10f);

            commonAppHitRects.add(new RectF(cellLeft, cellTop, cellRight, cellBottom));
        }
    }

    private void drawMikuTextShortcutIcon(Canvas c, RectF iconRect, String iconText, boolean shout) {
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(shout ? accentColor() : placeholderSurfaceColor());
        c.drawRoundRect(iconRect, 14f, 14f, fill);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2.5f);
        stroke.setColor(accentColor());
        c.drawRoundRect(iconRect, 14f, 14f, stroke);

        String s = iconText == null || iconText.trim().length() == 0 ? (shout ? "喊" : "文") : iconText.trim();
        if (s.length() > 2) {
            s = s.substring(0, 2);
        }

        Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconPaint.setTextAlign(Paint.Align.CENTER);
        iconPaint.setFakeBoldText(true);
        iconPaint.setTextSize(s.length() > 1 ? 24f : 30f);
        iconPaint.setColor(shout ? Color.WHITE : accentColor());
        Paint.FontMetrics fm = iconPaint.getFontMetrics();
        float cy = (iconRect.top + iconRect.bottom) / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText(s, (iconRect.left + iconRect.right) / 2f, cy, iconPaint);
    }

    private boolean handleCommonAppsTouch(float x, float y, long durationMs) {
        loadCommonAppsIfNeeded();
        for (int i = 0; i < commonAppHitRects.size() && i < commonAppsCache.size(); i++) {
            if (commonAppHitRects.get(i).contains(x, y)) {
                final AppEntry app = commonAppsCache.get(i);
                if (app.isShoutShortcut()) {
                    showMikuTextShoutDialog();
                } else if (app.isTextDisplayShortcut()) {
                    sendMikuTextShortcut(app.textPayload, app.label);
                } else if (durationMs >= 650) {
                    AppActionHelper.showAppActions(getContext(), app.label, app.pkg, app.cls, new Runnable() {
                        @Override
                        public void run() {
                            invalidateAppIconCaches();
                        }
                    });
                } else {
                    openApp(app.label, app.pkg, app.cls);
                }
                return true;
            }
        }
        return false;
    }

    private void openFirstCommonApp() {
        selectedCommonAppIndex = 0;
        openSelectedCommonApp();
    }

    private void openSelectedCommonApp() {
        loadCommonAppsIfNeeded();
        if (!commonAppsCache.isEmpty()) {
            selectedCommonAppIndex = clamp(selectedCommonAppIndex, 0, commonAppsCache.size() - 1);
            AppEntry app = commonAppsCache.get(selectedCommonAppIndex);
            if (app.isShoutShortcut()) {
                showMikuTextShoutDialog();
            } else if (app.isTextDisplayShortcut()) {
                sendMikuTextShortcut(app.textPayload, app.label);
            } else {
                openApp(app.label, app.pkg, app.cls);
            }
        } else {
            Toast.makeText(getContext(), "4号卡片尚未设置常用软件", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadCommonAppsIfNeeded() {
        long now = System.currentTimeMillis();
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long configSignature = sp.getLong("common_apps_updated_at", 0L);
        int iconSignature = IconPackManager.getIconSignature(getContext());
        if (!commonAppsCache.isEmpty()
                && now - lastCommonAppsLoadTime < 2000L
                && iconSignature == commonIconSignature
                && configSignature == commonConfigSignature) {
            return;
        }
        commonIconSignature = iconSignature;
        commonConfigSignature = configSignature;

        ensureCommonAppsConfigured();
        commonAppsCache.clear();
        sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        PackageManager pm = getContext().getPackageManager();
        for (int i = 0; i < 6; i++) {
            String type = sp.getString("common_app_" + i + "_type", "app");
            String pkg = sp.getString("common_app_" + i + "_pkg", "");
            String cls = sp.getString("common_app_" + i + "_cls", "");
            String label = sp.getString("common_app_" + i + "_label", "");
            if (MikuTextDisplayNodeController.isTextDisplayCommonType(type)
                    || MikuTextDisplayNodeController.isTextDisplayMarker(pkg)) {
                String payload = sp.getString("common_app_" + i + "_text_payload", "");
                String iconText = sp.getString("common_app_" + i + "_text_icon", MikuTextDisplayNodeController.COMMON_TYPE_SHOUT.equals(type) ? "喊" : "文");
                if (label == null || label.length() == 0) {
                    label = MikuTextDisplayNodeController.COMMON_TYPE_SHOUT.equals(type) ? "喊话文字" : "快捷文字";
                }
                commonAppsCache.add(AppEntry.textDisplay(label, type, payload, iconText));
                continue;
            }
            if (pkg == null || pkg.length() == 0) {
                continue;
            }
            Drawable icon = null;
            try {
                if (cls != null && cls.length() > 0) {
                    icon = pm.getActivityIcon(new ComponentName(pkg, cls));
                }
            } catch (Throwable ignored) {
            }
            if (icon == null) {
                try {
                    icon = pm.getApplicationIcon(pkg);
                } catch (Throwable ignored) {
                }
            }
            if ((label == null || label.length() == 0) && cls != null && cls.length() > 0) {
                try {
                    label = String.valueOf(pm.getActivityInfo(new ComponentName(pkg, cls), 0).loadLabel(pm));
                } catch (Throwable ignored) {
                }
            }
            if (label == null || label.length() == 0) {
                label = pkg;
            }
            label = IconPackManager.getLabel(getContext(), pkg, cls, label);
            icon = IconPackManager.getIcon(getContext(), pkg, cls, icon);
            commonAppsCache.add(new AppEntry(label, pkg, cls, icon));
        }
        lastCommonAppsLoadTime = now;
    }

    private void ensureCommonAppsConfigured() {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (sp.getBoolean("common_apps_initialized", false)) {
            if ((sp.getString("common_app_5_pkg", "") == null || sp.getString("common_app_5_pkg", "").length() == 0)) {
                SharedPreferences.Editor upgradeEditor = sp.edit();
                AppEntry sixthApp = resolvePackageApp("应用商店", "com.xiaomi.market", "com.android.vending", "com.huawei.appmarket", "com.oppo.market", "com.bbk.appstore", "com.meizu.mstore", "com.lenovo.leos.appstore");
                saveCommonAppIfFound(upgradeEditor, 5, sixthApp);
                upgradeEditor.apply();
            }
            return;
        }

        SharedPreferences.Editor editor = sp.edit();
        saveCommonAppIfFound(editor, 0, resolveActionApp(new Intent(Intent.ACTION_DIAL), "电话"));

        Intent msgIntent = new Intent(Intent.ACTION_MAIN);
        msgIntent.addCategory(Intent.CATEGORY_APP_MESSAGING);
        saveCommonAppIfFound(editor, 1, resolveActionApp(msgIntent, "短信"));

        saveCommonAppIfFound(editor, 2, resolvePackageApp("网易云音乐", "com.netease.cloudmusic.iot", "com.netease.cloudmusic"));
        saveCommonAppIfFound(editor, 3, resolvePackageApp("喜马拉雅", "com.ximalaya.ting.android.car", "com.ximalaya.ting.android"));

        SharedPreferences pref = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String navPkg = pref.getString("nav_package", "com.autonavi.amapauto");
        AppEntry navApp = resolvePackageApp("高德地图", navPkg, "com.autonavi.amapauto", "com.autonavi.minimap");
        saveCommonAppIfFound(editor, 4, navApp);

        AppEntry sixthApp = resolvePackageApp("应用商店", "com.xiaomi.market", "com.android.vending", "com.huawei.appmarket", "com.oppo.market", "com.bbk.appstore", "com.meizu.mstore", "com.lenovo.leos.appstore");
        saveCommonAppIfFound(editor, 5, sixthApp);

        editor.putBoolean("common_apps_initialized", true);
        editor.apply();
    }

    private void saveCommonAppIfFound(SharedPreferences.Editor editor, int slot, AppEntry app) {
        if (editor == null || app == null || app.pkg == null || app.pkg.length() == 0) {
            return;
        }
        editor.putString("common_app_" + slot + "_pkg", app.pkg);
        editor.putString("common_app_" + slot + "_cls", app.cls == null ? "" : app.cls);
        editor.putString("common_app_" + slot + "_label", app.label == null ? "" : app.label);
        editor.remove("common_app_" + slot + "_type");
        editor.remove("common_app_" + slot + "_text_payload");
        editor.remove("common_app_" + slot + "_text_icon");
        editor.putLong("common_apps_updated_at", System.currentTimeMillis());
    }

    private AppEntry resolveActionApp(Intent intent, String fallbackLabel) {
        try {
            PackageManager pm = getContext().getPackageManager();
            ResolveInfo info = pm.resolveActivity(intent, 0);
            if (info != null && info.activityInfo != null) {
                String label = String.valueOf(info.loadLabel(pm));
                String pkg = info.activityInfo.packageName;
                String cls = info.activityInfo.name;
                Drawable icon = info.loadIcon(pm);
                if (label == null || label.length() == 0) {
                    label = fallbackLabel;
                }
                return new AppEntry(label, pkg, cls, icon);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private AppEntry resolvePackageApp(String fallbackLabel, String... candidatePackages) {
        PackageManager pm = getContext().getPackageManager();
        if (candidatePackages == null) {
            return null;
        }
        for (String pkg : candidatePackages) {
            if (pkg == null || pkg.length() == 0) {
                continue;
            }
            try {
                Intent launch = pm.getLaunchIntentForPackage(pkg);
                if (launch == null || launch.getComponent() == null) {
                    continue;
                }
                String cls = launch.getComponent().getClassName();
                String label = fallbackLabel;
                Drawable icon = null;
                try {
                    ResolveInfo info = pm.resolveActivity(launch, 0);
                    if (info != null) {
                        CharSequence cs = info.loadLabel(pm);
                        if (cs != null && cs.length() > 0) {
                            label = cs.toString();
                        }
                        icon = info.loadIcon(pm);
                    }
                } catch (Throwable ignored) {
                }
                if (icon == null) {
                    try {
                        icon = pm.getApplicationIcon(pkg);
                    } catch (Throwable ignored) {
                    }
                }
                return new AppEntry(label, pkg, cls, icon);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private void drawBluetoothCard(Canvas c, RectF card) {
        titlePaint.setTextAlign(Paint.Align.LEFT);
        titlePaint.setTextSize(22f);
        titlePaint.setFakeBoldText(true);
        titlePaint.setColor(mainTextColor());
        c.drawText("蓝牙电话", card.left + 28f, card.top + 38f, titlePaint);

        // 整张 3 号卡片点击都已经能打开蓝牙音乐；按用户要求删除右侧手机图标。
        String deviceName = getConnectedBluetoothDeviceName();
        subTextPaint.setTextAlign(Paint.Align.LEFT);
        subTextPaint.setTextSize(23f);
        subTextPaint.setColor(mainTextColor());
        float textLeft = card.left + 28f;
        float textMaxWidth = Math.max(80f, card.width() - 56f);
        drawTextEllipsize(c, "已连接 " + deviceName, textLeft, card.top + 86f, subTextPaint, textMaxWidth);

        // 用户提供的是三枚状态图标已经排好的一整张图：蓝牙、电量、信号。
        // 这里继续作为一个整体绘制，不拆分、不打乱顺序。
        float iconY = card.top + 122f;
        RectF btGroupRect = new RectF(card.left + 24f, iconY - 12f, card.left + 170f, iconY + 20f);
        if (isNightMode()) {
            drawBitmapTintFitCenter(c, btStatusGroupIcon, btGroupRect, iconLineColor());
        } else {
            drawBitmapFitCenter(c, btStatusGroupIcon, btGroupRect);
        }
    }

    private String getConnectedBluetoothDeviceName() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                return "Miku Phone";
            }

            Set<BluetoothDevice> devices = adapter.getBondedDevices();
            if (devices == null || devices.isEmpty()) {
                return "Miku Phone";
            }

            for (BluetoothDevice device : devices) {
                if (device == null) continue;
                try {
                    java.lang.reflect.Method method = device.getClass().getMethod("isConnected");
                    Object result = method.invoke(device);
                    if (result instanceof Boolean && (Boolean) result) {
                        String name = device.getName();
                        if (name != null && name.trim().length() > 0) {
                            return name.trim();
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return "Miku Phone";
    }

    private void drawBluetoothStaticIcon(Canvas c, float x, float y) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(iconLineColor());
        p.setStrokeWidth(2.6f);
        p.setStyle(Paint.Style.STROKE);

        c.drawLine(x, y - 13f, x, y + 13f, p);
        c.drawLine(x, y - 13f, x + 9f, y - 5f, p);
        c.drawLine(x + 9f, y - 5f, x, y + 2f, p);
        c.drawLine(x, y + 2f, x + 9f, y + 10f, p);
        c.drawLine(x + 9f, y + 10f, x, y + 18f, p);
        c.drawLine(x, y + 2f, x - 8f, y - 7f, p);
        c.drawLine(x, y + 2f, x - 8f, y + 11f, p);
    }

    private void drawBatteryStaticIcon(Canvas c, float x, float y) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(iconLineColor());
        RectF body = new RectF(x, y, x + 32f, y + 16f);
        c.drawRoundRect(body, 3f, 3f, p);
        c.drawRect(x + 34f, y + 5f, x + 38f, y + 11f, p);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(greenColor());
        fill.setStyle(Paint.Style.FILL);
        c.drawRoundRect(new RectF(x + 3f, y + 3f, x + 25f, y + 13f), 2f, 2f, fill);
    }

    private void drawSignalStaticIcon(Canvas c, float x, float y) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(iconLineColor());
        p.setStyle(Paint.Style.FILL);
        c.drawRect(x, y + 20f, x + 5f, y + 27f, p);
        c.drawRect(x + 9f, y + 14f, x + 14f, y + 27f, p);
        c.drawRect(x + 18f, y + 8f, x + 23f, y + 27f, p);
    }

    private void drawStaticPhonePreview(Canvas c, RectF card) {
        RectF phone = new RectF(card.right - 82f, card.top + 24f, card.right - 22f, card.bottom - 18f);
        drawBitmapFitCenter(c, phonePreviewIcon, phone);
    }

    private void drawBitmapFitCenter(Canvas c, Bitmap bitmap, RectF dst) {
        if (bitmap == null) {
            return;
        }
        float bw = bitmap.getWidth();
        float bh = bitmap.getHeight();
        float dw = dst.width();
        float dh = dst.height();
        float scale = Math.min(dw / bw, dh / bh);
        float rw = bw * scale;
        float rh = bh * scale;
        float left = dst.left + (dw - rw) / 2f;
        float top = dst.top + (dh - rh) / 2f;
        c.drawBitmap(bitmap, null, new RectF(left, top, left + rw, top + rh), bitmapPaint);
    }

    private void drawBitmapTintFitCenter(Canvas c, Bitmap bitmap, RectF dst, int tintColor) {
        if (bitmap == null) {
            return;
        }
        float bw = bitmap.getWidth();
        float bh = bitmap.getHeight();
        float dw = dst.width();
        float dh = dst.height();
        float scale = Math.min(dw / bw, dh / bh);
        float rw = bw * scale;
        float rh = bh * scale;
        float left = dst.left + (dw - rw) / 2f;
        float top = dst.top + (dh - rh) / 2f;

        Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        tintPaint.setColorFilter(new PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN));
        c.drawBitmap(bitmap, null, new RectF(left, top, left + rw, top + rh), tintPaint);
    }

    private void drawRoundedBitmap(Canvas c, Bitmap bitmap, RectF dst, float radius) {
        if (bitmap == null) {
            return;
        }
        int save = c.save();
        Path path = new Path();
        path.addRoundRect(dst, radius, radius, Path.Direction.CW);
        c.clipPath(path);
        c.drawBitmap(bitmap, null, dst, bitmapPaint);
        c.restoreToCount(save);
    }

    private void openBluetoothMusicActivity() {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.ts.MainUI", "com.ts.bt.BtMusicActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            closeAmapFloatingMap();
            getContext().startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(getContext(), "无法打开蓝牙音乐界面", Toast.LENGTH_SHORT).show();
        }
    }

    private void drawAppDrawerPage(Canvas c) {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int columns = clamp(sp.getInt("drawer_grid_columns", 6), 3, 8);
        int rows = clamp(sp.getInt("drawer_grid_rows", 3), 1, 6);
        int iconSize = clamp(sp.getInt("drawer_icon_size_dp", 72), 40, 128);
        int textSize = clamp(sp.getInt("drawer_text_size_sp", 16), 10, 30);

        loadAppsIfNeeded();
        int pageSize = Math.max(1, rows * columns);
        int pageCount = getAppPageCount(pageSize);
        appDrawerPage = clamp(appDrawerPage, 0, Math.max(0, pageCount - 1));

        int pageStart = appDrawerPage * pageSize;
        if (selectedAppIndex < pageStart || selectedAppIndex >= pageStart + pageSize) {
            selectedAppIndex = Math.min(pageStart, Math.max(0, cachedApps.size() - 1));
        }

        RectF pageCard = getLargePageCard();
        float radius = 24f;
        c.drawRoundRect(pageCard, radius, radius, cardPaint);

        titlePaint.setTextAlign(Paint.Align.LEFT);
        c.drawText("应用抽屉", pageCard.left + 46f, pageCard.top + 58f, titlePaint);

        subTextPaint.setTextSize(20f);
        subTextPaint.setColor(mutedTextColor());
        c.drawText("平板式 " + rows + "×" + columns + " 网格 · 左右滑动翻页 · 方向键选择 · 回车打开", pageCard.left + 46f, pageCard.top + 94f, subTextPaint);

        float gridLeft = pageCard.left + 56f;
        float gridTop = pageCard.top + 126f;
        float gridRight = pageCard.right - 56f;
        float gridBottom = pageCard.bottom - 46f;

        c.save();
        c.clipRect(gridLeft, gridTop, gridRight, gridBottom);

        float contentWidth = gridRight - gridLeft;
        if (cachedApps.isEmpty()) {
            drawAppDrawerLoadingOrEmpty(c, gridLeft, gridTop, gridRight, gridBottom);
        } else if (appPageAnimating) {
            float progress = (System.currentTimeMillis() - appAnimStartMs) / (float) APP_PAGE_ANIM_DURATION_MS;
            if (progress >= 1f) {
                progress = 1f;
                appPageAnimating = false;
            }
            progress = easeOutCubic(progress);

            // 下一页：旧页向左滑出，新页从右滑入；上一页相反。
            float fromOffset = -appAnimDirection * contentWidth * progress;
            float toOffset = appAnimDirection * contentWidth * (1f - progress);

            drawAppPageCells(c, appAnimFromPage, fromOffset, columns, rows, iconSize, textSize, gridLeft, gridTop, gridRight, gridBottom);
            drawAppPageCells(c, appAnimToPage, toOffset, columns, rows, iconSize, textSize, gridLeft, gridTop, gridRight, gridBottom);

            if (appPageAnimating) {
                postInvalidateOnAnimation();
            }
        } else {
            drawAppPageCells(c, appDrawerPage, 0f, columns, rows, iconSize, textSize, gridLeft, gridTop, gridRight, gridBottom);
        }

        c.restore();
        drawPageIndicator(c, pageCard, pageCount);
    }

    private void drawAppDrawerLoadingOrEmpty(Canvas c, float gridLeft, float gridTop, float gridRight, float gridBottom) {
        subTextPaint.setTextAlign(Paint.Align.CENTER);
        subTextPaint.setTextSize(26f);
        subTextPaint.setColor(mutedTextColor());
        String text = appListLoading ? "应用列表加载中…" : "暂无可显示应用";
        c.drawText(text, (gridLeft + gridRight) / 2f, (gridTop + gridBottom) / 2f - 10f, subTextPaint);

        subTextPaint.setTextSize(18f);
        subTextPaint.setColor(mutedTextColor());
        String sub = appListLoading ? "首次加载会在后台扫描应用，不会卡住主界面" : "可到设置里检查隐藏应用列表";
        c.drawText(sub, (gridLeft + gridRight) / 2f, (gridTop + gridBottom) / 2f + 28f, subTextPaint);
        subTextPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawAppPageCells(Canvas c, int page, float offsetX, int columns, int rows, int iconSize, int textSize,
                                  float gridLeft, float gridTop, float gridRight, float gridBottom) {
        int pageSize = Math.max(1, rows * columns);
        int pageStart = page * pageSize;
        int pageEnd = Math.min(cachedApps.size(), pageStart + pageSize);
        float cellW = (gridRight - gridLeft) / columns;
        float cellH = (gridBottom - gridTop) / rows;

        for (int i = pageStart; i < pageEnd; i++) {
            AppEntry app = cachedApps.get(i);
            int pagePos = i - pageStart;
            int row = pagePos / columns;
            int col = pagePos % columns;
            float cellLeft = gridLeft + col * cellW + offsetX;
            float cellTop = gridTop + row * cellH;
            drawAppIconCell(c, app, cellLeft, cellTop, cellW, cellH, iconSize, textSize, appSelectionVisible && i == selectedAppIndex);
        }
    }

    private float easeOutCubic(float t) {
        t = clampFloat(t, 0f, 1f);
        float p = 1f - t;
        return 1f - p * p * p;
    }

    private void drawAppIconCell(Canvas c, AppEntry app, float cellLeft, float cellTop, float cellW, float cellH, int iconSizeDp, int textSizeSp, boolean selected) {
        float cellPad = 8f;
        if (selected) {
            Paint selectedCellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            selectedCellPaint.setColor(selectedSurfaceColor());
            c.drawRoundRect(new RectF(cellLeft + cellPad, cellTop + cellPad, cellLeft + cellW - cellPad, cellTop + cellH - cellPad), 18f, 18f, selectedCellPaint);

            Paint selectedStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            selectedStrokePaint.setStyle(Paint.Style.STROKE);
            selectedStrokePaint.setStrokeWidth(3f);
            selectedStrokePaint.setColor(accentColor());
            c.drawRoundRect(new RectF(cellLeft + cellPad, cellTop + cellPad, cellLeft + cellW - cellPad, cellTop + cellH - cellPad), 18f, 18f, selectedStrokePaint);
        }

        float iconPx = iconSizeDp;
        float iconLeft = cellLeft + (cellW - iconPx) / 2f;
        float iconTop = cellTop + 10f;

        if (app.icon != null) {
            app.icon.setBounds((int) iconLeft, (int) iconTop, (int) (iconLeft + iconPx), (int) (iconTop + iconPx));
            app.icon.draw(c);
        }

        Paint labelPaint = smallTextPaint;
        labelPaint.setColor(subTextColor());
        labelPaint.setTextSize(textSizeSp);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        String label = app.label;
        float textY = iconTop + iconPx + 26f;
        drawCenteredTextSingleLine(c, label, cellLeft + cellW / 2f, textY, labelPaint, cellW - 12f);
    }

    private void drawPageIndicator(Canvas c, RectF pageCard, int pageCount) {
        if (pageCount <= 1) {
            smallTextPaint.setColor(mutedTextColor());
            smallTextPaint.setTextSize(18f);
            smallTextPaint.setTextAlign(Paint.Align.RIGHT);
            c.drawText("共 " + cachedApps.size() + " 个应用", pageCard.right - 44f, pageCard.bottom - 18f, smallTextPaint);
            return;
        }

        smallTextPaint.setColor(mutedTextColor());
        smallTextPaint.setTextSize(18f);
        smallTextPaint.setTextAlign(Paint.Align.RIGHT);
        c.drawText("第 " + (appDrawerPage + 1) + " / " + pageCount + " 页 · 共 " + cachedApps.size() + " 个应用", pageCard.right - 44f, pageCard.bottom - 18f, smallTextPaint);

        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float centerX = (pageCard.left + pageCard.right) / 2f;
        float y = pageCard.bottom - 20f;
        float startX = centerX - (pageCount - 1) * 10f;
        for (int i = 0; i < pageCount; i++) {
            dotPaint.setColor(i == appDrawerPage ? accentColor() : mutedDotColor());
            c.drawCircle(startX + i * 20f, y, i == appDrawerPage ? 5.5f : 4f, dotPaint);
        }
    }

    private void drawMinePage(Canvas c) {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String owner = sp.getString("owner_name", "江灵夏草");
        String brand = sp.getString("car_brand", "奥迪");
        String signature = sp.getString("signature", "MikuCarLauncher");

        RectF pageCard = getLargePageCard();
        float radius = 24f;
        c.drawRoundRect(pageCard, radius, radius, cardPaint);

        titlePaint.setTextAlign(Paint.Align.LEFT);
        c.drawText("我的", pageCard.left + 46f, pageCard.top + 62f, titlePaint);

        subTextPaint.setColor(mutedTextColor());
        subTextPaint.setTextSize(20f);
        c.drawText("车主信息与车机桌面设置", pageCard.left + 46f, pageCard.top + 98f, subTextPaint);

        float rowLeft = pageCard.left + 50f;
        float rowRight = pageCard.right - 50f;
        float rowTop = pageCard.top + 138f;
        float rowH = 74f;
        float rowGap = 16f;

        drawMineRow(c, rowLeft, rowTop, rowRight, rowTop + rowH, "车主名称", owner, "点击修改");
        drawMineRow(c, rowLeft, rowTop + (rowH + rowGap), rowRight, rowTop + (rowH + rowGap) + rowH, "汽车品牌", brand, "点击修改");
        drawMineRow(c, rowLeft, rowTop + 2f * (rowH + rowGap), rowRight, rowTop + 2f * (rowH + rowGap) + rowH, "签名", signature, "点击修改");
        drawMineRow(c, rowLeft, rowTop + 3f * (rowH + rowGap), rowRight, rowTop + 3f * (rowH + rowGap) + rowH, "车机桌面设置", "默认导航/音乐、应用抽屉、隐藏应用", "进入");
        drawMineRow(c, rowLeft, rowTop + 4f * (rowH + rowGap), rowRight, rowTop + 4f * (rowH + rowGap) + rowH, "关于软件", "作者、主页与项目说明", "查看");

        if (hardwareFocusVisible && focusArea == 1 && activeIndex == 6) {
            int i = clamp(selectedMineRowIndex, 0, 4);
            float top = rowTop + i * (rowH + rowGap);
            drawFocusStroke(c, new RectF(rowLeft, top, rowRight, top + rowH));
        }
    }

    private void drawMineRow(Canvas c, float left, float top, float right, float bottom, String title, String value, String action) {
        RectF r = new RectF(left, top, right, bottom);
        c.drawRoundRect(r, 16f, 16f, rowPaint);

        subTextPaint.setTextAlign(Paint.Align.LEFT);
        subTextPaint.setTextSize(22f);
        subTextPaint.setColor(mainTextColor());
        c.drawText(title, left + 28f, top + 46f, subTextPaint);

        subTextPaint.setTextSize(20f);
        subTextPaint.setColor(mutedTextColor());
        c.drawText(value, left + 240f, top + 46f, subTextPaint);

        subTextPaint.setTextAlign(Paint.Align.RIGHT);
        subTextPaint.setTextSize(18f);
        subTextPaint.setColor(accentColor());
        c.drawText(action, right - 28f, top + 46f, subTextPaint);
        subTextPaint.setTextAlign(Paint.Align.LEFT);
    }

    private RectF getLargePageCard() {
        // “应用抽屉”和“我的”页面作为大卡片浮在首页区域里，保留左侧按钮列。
        return new RectF(210f, 35.5f, 2396f, 684.5f);
    }

    private void drawMenuItem(Canvas c, int index) {
        float y = startY + index * (btnH + gap);
        // 选中背景在上下方向各扩展 gap/2，这样如果所有按钮都处于选中态，背景之间可无缝衔接。
        RectF selectedRect = new RectF(0f, y - gap / 2f, selectedBtnW, y + btnH + gap / 2f);
        boolean active = index == activeIndex;

        if (active) {
            if (isNightMode()) {
                Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                selectedPaint.setColor(selectedSurfaceColor());
                c.drawRect(selectedRect, selectedPaint);

                Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                barPaint.setColor(accentColor());
                c.drawRect(selectedRect.left, selectedRect.top, selectedRect.left + 6f, selectedRect.bottom, barPaint);
            } else if (selectedBg != null) {
                // 选中背景按用户要求直接拉伸到整列宽度，避免右侧留白。
                c.drawBitmap(selectedBg, null, selectedRect, bitmapPaint);
            }
        }

        if (hardwareFocusVisible && focusArea == 0 && sidebarFocusIndex == index) {
            drawFocusStroke(c, selectedRect);
        }

        Bitmap icon = icons[index];
        if (icon != null) {
            float iconY = y + (btnH - iconSize) / 2f;
            RectF dst = new RectF(iconX, iconY, iconX + iconSize, iconY + iconSize);
            if (active) {
                iconPaint.setColorFilter(new PorterDuffColorFilter(accentColor(), PorterDuff.Mode.SRC_IN));
            } else if (isNightMode()) {
                iconPaint.setColorFilter(new PorterDuffColorFilter(mainTextColor(), PorterDuff.Mode.SRC_IN));
            } else {
                iconPaint.setColorFilter(null);
            }
            c.drawBitmap(icon, null, dst, iconPaint);
        }

        Paint paint = active ? activeTextPaint : textPaint;
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textY = y + btnH / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText(labels[index], textX, textY, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX() * DESIGN_W / Math.max(1, getWidth());
        float y = event.getY() * DESIGN_H / Math.max(1, getHeight());

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            downDesignX = x;
            downDesignY = y;
            downTimeMs = System.currentTimeMillis();
            if (hardwareFocusVisible || appSelectionVisible) {
                hardwareFocusVisible = false;
                appSelectionVisible = false;
                invalidate();
            }

            pressedMusicButton = -1;
            commonAppSelectionVisible = false;
            if (activeIndex == 0) {
                if (getMusicPrevButtonRect().contains(x, y)) {
                    pressedMusicButton = 0;
                    invalidate();
                } else if (getMusicPlayButtonRect().contains(x, y)) {
                    pressedMusicButton = 1;
                    invalidate();
                } else if (getMusicNextButtonRect().contains(x, y)) {
                    pressedMusicButton = 2;
                    invalidate();
                }
            }
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            // 先处理左侧按钮列，保证任何页面都可以随时切换。
            for (int i = 0; i < labels.length; i++) {
                float by = startY + i * (btnH + gap);
                if (x >= 0 && x <= sidebarW && y >= by - gap / 2f && y <= by + btnH + gap / 2f) {
                    activeIndex = i;
                    sidebarFocusIndex = i;
                    hardwareFocusVisible = false;
                    appSelectionVisible = false;
                    focusArea = 0;
                    if (activeIndex == 5) {
                        clampAppDrawerSelection();
                    }
                    invalidate();
                    if (menuClickListener != null) {
                        menuClickListener.onMenuClick(i, labels[i]);
                    }
                    return true;
                }
            }

            if (activeIndex == 0) {
                RectF card2 = new RectF(1158f, 35.5f, 1550f, 350.5f);
                if (card2.contains(x, y)) {
                    int pressed = pressedMusicButton;
                    pressedMusicButton = -1;
                    invalidate();

                    if (!isNotificationListenerEnabled()) {
                        if (getMusicPermissionButtonRect().contains(x, y) || card2.contains(x, y)) {
                            openNotificationListenerSettings();
                            return true;
                        }
                    }

                    if (pressed == 0 && getMusicPrevButtonRect().contains(x, y)) {
                        controlMusic(0);
                        return true;
                    }
                    if (pressed == 1 && getMusicPlayButtonRect().contains(x, y)) {
                        controlMusic(1);
                        return true;
                    }
                    if (pressed == 2 && getMusicNextButtonRect().contains(x, y)) {
                        controlMusic(2);
                        return true;
                    }
                    if (getMusicOpenButtonRect().contains(x, y)) {
                        openDefaultMusicApp();
                        return true;
                    }
                } else if (pressedMusicButton != -1) {
                    pressedMusicButton = -1;
                    invalidate();
                }

                RectF card3 = new RectF(1158f, 368.5f, 1550f, 528.5f);
                // 3号蓝牙电话卡片：整张卡片任意区域点击都打开蓝牙音乐 Activity。
                if (card3.contains(x, y)) {
                    openBluetoothMusicActivity();
                    return true;
                }

                RectF card4 = new RectF(210f, 546.5f, 1140f, 684.5f);
                if (card4.contains(x, y)) {
                    if (handleCommonAppsTouch(x, y, System.currentTimeMillis() - downTimeMs)) {
                        return true;
                    }
                }

                RectF card6 = new RectF(1970f, 546.5f, 2540f, 684.5f);
                if (card6.contains(x, y)) {
                    closeAmapFloatingMap();
                    getContext().startActivity(new Intent(getContext(), WeatherSettingsActivity.class));
                    return true;
                }

                // Live2D 位于背景之上、卡片之下，CanvasView 会吃掉触摸事件。
                // 因此在首页中间空白的模型区域里做一次点击转发，用于“点击人物切换下一个动作”。
                if (isLive2DClickArea(x, y)
                        && Math.abs(x - downDesignX) < 36f
                        && Math.abs(y - downDesignY) < 36f
                        && System.currentTimeMillis() - downTimeMs < 450L) {
                    if (live2DClickListener != null) {
                        live2DClickListener.onLive2DClick();
                        return true;
                    }
                }
            }

            if (activeIndex == 5) {
                float dx = x - downDesignX;
                float dy = y - downDesignY;
                if (Math.abs(dx) > 120f && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                    if (dx < 0f) {
                        moveAppDrawerPage(1);
                    } else {
                        moveAppDrawerPage(-1);
                    }
                    return true;
                }
                handleAppDrawerTouch(x, y, System.currentTimeMillis() - downTimeMs);
                return true;
            } else if (activeIndex == 6) {
                handleMineTouch(x, y);
                return true;
            }
        }
        return true;
    }

    private final Runnable turnSignalRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                updateTurnSignalState();
            } catch (Throwable ignored) {
            }
            mainHandler.postDelayed(this, 220L);
        }
    };

    private void updateTurnSignalState() {
        VehicleDataProvider.Snapshot snapshot = vehicleDataProvider.getSnapshot();
        int next = TurnSignalSoundManager.STATE_NONE;
        if (snapshot != null && snapshot.valid) {
            if (snapshot.leftTurnOn && snapshot.rightTurnOn) {
                next = TurnSignalSoundManager.STATE_BOTH;
            } else if (snapshot.leftTurnOn) {
                next = TurnSignalSoundManager.STATE_LEFT;
            } else if (snapshot.rightTurnOn) {
                next = TurnSignalSoundManager.STATE_RIGHT;
            }
        }

        if (next != turnSignalState) {
            turnSignalState = next;
            turnBlinkStartMs = SystemClock.elapsedRealtime();
            invalidate();
        }

        if (turnSignalAudioAllowed && getWindowToken() != null) {
            turnSignalSoundManager.update(turnSignalState);
        } else {
            turnSignalSoundManager.stop();
        }

        if (turnSignalState != TurnSignalSoundManager.STATE_NONE) {
            invalidate();
        }
    }

    private void drawTurnDebugOverlay(Canvas c) {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!sp.getBoolean(VehicleDataProvider.PREF_TURN_DEBUG_OVERLAY, false)) {
            return;
        }

        VehicleDataProvider.Snapshot snapshot = vehicleDataProvider.getSnapshot();
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(isNightMode() ? 0xDD111820 : 0xEFFFFFFF);
        bg.setStyle(Paint.Style.FILL);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(isNightMode() ? Color.WHITE : Color.rgb(20, 20, 20));
        text.setTextSize(20f);
        text.setTextAlign(Paint.Align.LEFT);

        RectF box = new RectF(1180f, 112f, 2390f, 260f);
        c.drawRoundRect(box, 24f, 24f, bg);

        String line1 = "转向调试: ";
        String line2 = "";
        String line3 = "";
        if (snapshot == null || !snapshot.valid) {
            line1 += "暂无有效车辆数据";
        } else {
            line1 += "L=" + snapshot.leftTurnOn + "  R=" + snapshot.rightTurnOn
                    + "  rpm=" + snapshot.rpm + "  range=" + snapshot.rangeKm
                    + "  source=" + snapshot.dataSource;
            String debug = snapshot.debugText == null ? "" : snapshot.debugText;
            int split = Math.min(debug.length(), 92);
            line2 = debug.substring(0, split);
            line3 = debug.length() > split ? debug.substring(split, Math.min(debug.length(), split + 92)) : "";
        }

        c.drawText(line1, box.left + 26f, box.top + 42f, text);
        if (line2.length() > 0) c.drawText(line2, box.left + 26f, box.top + 84f, text);
        if (line3.length() > 0) c.drawText(line3, box.left + 26f, box.top + 124f, text);
    }

    private void drawTurnSignalOverlay(Canvas c) {
        if (turnSignalState == TurnSignalSoundManager.STATE_NONE) {
            return;
        }

        long elapsed = SystemClock.elapsedRealtime() - turnBlinkStartMs;
        boolean visible = ((elapsed / 360L) % 2L) == 0L;
        if (!visible) {
            return;
        }

        if (turnSignalState == TurnSignalSoundManager.STATE_LEFT || turnSignalState == TurnSignalSoundManager.STATE_BOTH) {
            drawTurnArrow(c, true);
        }
        if (turnSignalState == TurnSignalSoundManager.STATE_RIGHT || turnSignalState == TurnSignalSoundManager.STATE_BOTH) {
            drawTurnArrow(c, false);
        }
    }

    private void drawTurnArrow(Canvas c, boolean left) {
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(isNightMode() ? 0xCC111820 : 0xDDF8FBFF);
        bg.setStyle(Paint.Style.FILL);

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(isNightMode() ? Color.rgb(90, 255, 180) : Color.rgb(0, 180, 95));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(10f);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(line.getColor());
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(28f);
        text.setFakeBoldText(true);

        String label = left ? "左转" : "右转";
        float textWidth = text.measureText(label);
        float iconWidth = 82f;
        float gap = 10f;
        float sidePad = 18f;
        float boxTop = 12f;
        float boxBottom = 100f;
        float boxRadius = 30f;
        float boxWidth = sidePad * 2f + textWidth + gap + iconWidth;

        RectF box;
        float textX;
        float iconLeft;
        if (left) {
            // 左转：整个提示框距离屏幕左边约 30px，文字在左，图标在右。
            float boxLeft = 30f;
            box = new RectF(boxLeft, boxTop, boxLeft + boxWidth, boxBottom);
            textX = box.left + sidePad;
            iconLeft = textX + textWidth + gap;
        } else {
            // 右转：整个提示框距离屏幕右边约 30px，图标在左，文字在右。
            float boxRight = DESIGN_W - 30f;
            box = new RectF(boxRight - boxWidth, boxTop, boxRight, boxBottom);
            iconLeft = box.left + sidePad;
            textX = iconLeft + iconWidth + gap;
        }
        c.drawRoundRect(box, boxRadius, boxRadius, bg);

        float arrowCx = iconLeft + iconWidth / 2f;
        float arrowCy = 56f;
        float textY = 67f;

        Path arrow = new Path();
        if (left) {
            arrow.moveTo(arrowCx - 26f, arrowCy);
            arrow.lineTo(arrowCx + 8f, arrowCy - 28f);
            arrow.moveTo(arrowCx - 26f, arrowCy);
            arrow.lineTo(arrowCx + 8f, arrowCy + 28f);
            arrow.moveTo(arrowCx - 20f, arrowCy);
            arrow.lineTo(arrowCx + 62f, arrowCy);
            c.drawPath(arrow, line);
        } else {
            arrow.moveTo(arrowCx + 26f, arrowCy);
            arrow.lineTo(arrowCx - 8f, arrowCy - 28f);
            arrow.moveTo(arrowCx + 26f, arrowCy);
            arrow.lineTo(arrowCx - 8f, arrowCy + 28f);
            arrow.moveTo(arrowCx + 20f, arrowCy);
            arrow.lineTo(arrowCx - 62f, arrowCy);
            c.drawPath(arrow, line);
        }
        c.drawText(label, textX, textY, text);
    }

    private boolean isLive2DClickArea(float x, float y) {
        // 覆盖首页中间空白区和建议模型区域，但不覆盖任何已确认功能卡片。
        return x >= 1568f && x <= 1965f && y >= 120f && y <= 545f;
    }

    private void handleAppDrawerTouch(float x, float y, long durationMs) {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int columns = clamp(sp.getInt("drawer_grid_columns", 6), 3, 8);
        int rows = clamp(sp.getInt("drawer_grid_rows", 3), 1, 6);
        int pageSize = Math.max(1, rows * columns);

        RectF pageCard = getLargePageCard();
        float gridLeft = pageCard.left + 56f;
        float gridTop = pageCard.top + 126f;
        float gridRight = pageCard.right - 56f;
        float gridBottom = pageCard.bottom - 46f;

        if (x < gridLeft || x > gridRight || y < gridTop || y > gridBottom) {
            return;
        }

        float cellW = (gridRight - gridLeft) / columns;
        float cellH = (gridBottom - gridTop) / rows;
        int col = (int) ((x - gridLeft) / cellW);
        int row = (int) ((y - gridTop) / cellH);
        int pageIndex = row * columns + col;
        int index = appDrawerPage * pageSize + pageIndex;

        loadAppsIfNeeded();
        if (index < 0 || index >= cachedApps.size() || pageIndex >= pageSize) {
            return;
        }

        selectedAppIndex = index;
        AppEntry app = cachedApps.get(index);
        if (durationMs >= 650) {
            AppActionHelper.showAppActions(getContext(), app.label, app.pkg, app.cls, new Runnable() {
                @Override
                public void run() {
                    invalidateAppIconCaches();
                }
            });
        } else {
            invalidate();
            openApp(app.label, app.pkg, app.cls);
        }
    }

    private void handleMineTouch(float x, float y) {
        RectF pageCard = getLargePageCard();
        float rowLeft = pageCard.left + 50f;
        float rowRight = pageCard.right - 50f;
        float rowTop = pageCard.top + 138f;
        float rowH = 74f;
        float rowGap = 16f;

        if (x < rowLeft || x > rowRight) {
            return;
        }

        int index = -1;
        for (int i = 0; i < 6; i++) {
            float top = rowTop + i * (rowH + rowGap);
            if (y >= top && y <= top + rowH) {
                index = i;
                break;
            }
        }

        if (index == 0) {
            showEditDialog("车主名称", "owner_name", "江灵夏草");
        } else if (index == 1) {
            showEditDialog("汽车品牌", "car_brand", "奥迪");
        } else if (index == 2) {
            showEditDialog("签名", "signature", "MikuCarLauncher");
        } else if (index == 3) {
            Intent intent = new Intent(getContext(), DesktopSettingsActivity.class);
            closeAmapFloatingMap();
            getContext().startActivity(intent);
        } else if (index == 4) {
            showAboutDialog();
        }
    }

    private void showAboutDialog() {
        String message =
                "MikuCarLauncher / A4L 车机桌面\n\n" +
                "作者：江灵夏草\n\n" +
                "B站主页：\nhttps://space.bilibili.com/130914376\n\n" +
                "抖音：JLXC2001\n" +
                "X（原推特）：jlxc2001\n\n" +
                "软件介绍：\n" +
                "这是一款面向第三方安卓车机的自定义车机桌面。当前项目以奥迪 A4L 风格 UI 为基础，整合导航、音乐、车辆界面、360 全景、应用抽屉和车主个性化信息。后续会继续接入车辆实时数据、HUD、战斗模式等功能。";

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle("关于软件")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .create();
        dialog.show();
    }

    private void showEditDialog(final String title, final String key, final String defaultValue) {
        final SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        final EditText editText = new EditText(getContext());
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setText(sp.getString(key, defaultValue));
        editText.setSelectAllOnFocus(true);

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(title)
                .setView(editText)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        sp.edit().putString(key, editText.getText().toString()).apply();
                        invalidate();
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
    }

    public VehicleDataProvider.Snapshot getVehicleSnapshot() {
        return vehicleDataProvider == null ? VehicleDataProvider.Snapshot.empty() : vehicleDataProvider.getSnapshot();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (vehicleDataProvider != null) {
            vehicleDataProvider.start();
        }
        if (vehicleDataBroadcaster != null) {
            vehicleDataBroadcaster.start();
        }
        if (weatherProvider != null) {
            weatherProvider.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (vehicleDataBroadcaster != null) {
            vehicleDataBroadcaster.stop();
        }
        if (vehicleDataProvider != null) {
            vehicleDataProvider.stop();
        }
        if (weatherProvider != null) {
            weatherProvider.stop();
        }
        super.onDetachedFromWindow();
    }

    public boolean handleHardwareKey(int keyCode) {
        if (!isNavigationKey(keyCode)) {
            return false;
        }

        hardwareFocusVisible = true;

        // 左侧按钮列全局可控。
        if (focusArea == 0) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                sidebarFocusIndex = clamp(sidebarFocusIndex - 1, 0, labels.length - 1);
                invalidate();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                sidebarFocusIndex = clamp(sidebarFocusIndex + 1, 0, labels.length - 1);
                invalidate();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (activeIndex == 0) {
                    focusArea = 1;
                    selectedCardIndex = clamp(selectedCardIndex, 0, 5);
                    invalidate();
                    return true;
                } else if (activeIndex == 5) {
                    focusArea = 1;
                    appSelectionVisible = true;
                    clampAppDrawerSelection();
                    invalidate();
                    return true;
                } else if (activeIndex == 6) {
                    focusArea = 1;
                    selectedMineRowIndex = clamp(selectedMineRowIndex, 0, 4);
                    invalidate();
                    return true;
                }
                return true;
            }

            if (isEnterKey(keyCode)) {
                activeIndex = sidebarFocusIndex;
                if (activeIndex == 5) {
                    clampAppDrawerSelection();
                }
                invalidate();
                if (menuClickListener != null) {
                    menuClickListener.onMenuClick(activeIndex, labels[activeIndex]);
                }
                return true;
            }

            return true;
        }

        // 内容区：首页 1~6 号卡片。
        if (activeIndex == 0) {
            return handleHomeCardKey(keyCode);
        }

        // 内容区：应用抽屉。
        if (activeIndex == 5) {
            return handleAppDrawerKey(keyCode);
        }

        // 内容区：我的。
        if (activeIndex == 6) {
            return handleMineKey(keyCode);
        }

        return true;
    }

    private boolean handleHomeCardKey(int keyCode) {
        if (selectedCardIndex == 3) {
            loadCommonAppsIfNeeded();
            int commonCount = commonAppsCache.size();
            if (commonCount > 0) {
                selectedCommonAppIndex = clamp(selectedCommonAppIndex, 0, commonCount - 1);
            }

            if (commonAppSelectionVisible && commonCount > 0) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (selectedCommonAppIndex > 0) {
                        selectedCommonAppIndex--;
                    } else {
                        commonAppSelectionVisible = false;
                        focusArea = 0;
                    }
                    invalidate();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (selectedCommonAppIndex < commonCount - 1) {
                        selectedCommonAppIndex++;
                    } else {
                        commonAppSelectionVisible = false;
                        selectedCardIndex = 4;
                    }
                    invalidate();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    commonAppSelectionVisible = false;
                    selectedCardIndex = 0;
                    invalidate();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    invalidate();
                    return true;
                }
                if (isEnterKey(keyCode)) {
                    openSelectedCommonApp();
                    return true;
                }
            }
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            // 1/4 位于最左列，再向左回到左侧按钮列。
            if (selectedCardIndex == 0 || selectedCardIndex == 3) {
                commonAppSelectionVisible = false;
                focusArea = 0;
            } else {
                selectedCardIndex = Math.max(0, selectedCardIndex - 1);
                commonAppSelectionVisible = false;
            }
            invalidate();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (selectedCardIndex == 3) {
                loadCommonAppsIfNeeded();
                if (!commonAppsCache.isEmpty()) {
                    commonAppSelectionVisible = true;
                    selectedCommonAppIndex = clamp(selectedCommonAppIndex, 0, commonAppsCache.size() - 1);
                } else {
                    selectedCardIndex = 4;
                    commonAppSelectionVisible = false;
                }
            } else {
                selectedCardIndex = Math.min(5, selectedCardIndex + 1);
                commonAppSelectionVisible = false;
            }
            invalidate();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            commonAppSelectionVisible = false;
            if (selectedCardIndex >= 3) {
                // 底部 4/5/6 回到上方最近卡片。
                selectedCardIndex = selectedCardIndex == 3 ? 0 : (selectedCardIndex == 4 ? 2 : 2);
            } else if (selectedCardIndex == 2) {
                selectedCardIndex = 1;
            }
            invalidate();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            commonAppSelectionVisible = false;
            if (selectedCardIndex == 0) {
                selectedCardIndex = 3;
                loadCommonAppsIfNeeded();
                if (!commonAppsCache.isEmpty()) {
                    commonAppSelectionVisible = true;
                    selectedCommonAppIndex = clamp(selectedCommonAppIndex, 0, commonAppsCache.size() - 1);
                }
            } else if (selectedCardIndex == 2) {
                selectedCardIndex = 3;
                loadCommonAppsIfNeeded();
                if (!commonAppsCache.isEmpty()) {
                    commonAppSelectionVisible = true;
                    selectedCommonAppIndex = clamp(selectedCommonAppIndex, 0, commonAppsCache.size() - 1);
                }
            } else if (selectedCardIndex == 1) {
                selectedCardIndex = 2;
            }
            invalidate();
            return true;
        }

        if (isEnterKey(keyCode)) {
            if (selectedCardIndex == 1) {
                if (!isNotificationListenerEnabled()) {
                    openNotificationListenerSettings();
                } else {
                    controlMusic(1);
                }
            } else if (selectedCardIndex == 2) {
                openBluetoothMusicActivity();
            } else if (selectedCardIndex == 3) {
                loadCommonAppsIfNeeded();
                if (!commonAppsCache.isEmpty()) {
                    commonAppSelectionVisible = true;
                    selectedCommonAppIndex = clamp(selectedCommonAppIndex, 0, commonAppsCache.size() - 1);
                    openSelectedCommonApp();
                } else {
                    Toast.makeText(getContext(), "4号卡片尚未设置常用软件", Toast.LENGTH_SHORT).show();
                }
            } else if (selectedCardIndex == 5) {
                closeAmapFloatingMap();
                getContext().startActivity(new Intent(getContext(), WeatherSettingsActivity.class));
            } else {
                Toast.makeText(getContext(), (selectedCardIndex + 1) + "号卡片", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        return true;
    }

    private boolean handleAppDrawerKey(int keyCode) {
        loadAppsIfNeeded();
        if (cachedApps.isEmpty()) {
            return true;
        }

        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int columns = clamp(sp.getInt("drawer_grid_columns", 6), 3, 8);
        int rows = clamp(sp.getInt("drawer_grid_rows", 3), 1, 6);
        int pageSize = Math.max(1, rows * columns);

        clampAppDrawerSelection();
        appSelectionVisible = true;

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (selectedAppIndex % columns == 0) {
                if (appDrawerPage == 0) {
                    focusArea = 0;
                    appSelectionVisible = false;
                    invalidate();
                } else {
                    moveAppDrawerPage(-1);
                }
            } else {
                selectedAppIndex = Math.max(0, selectedAppIndex - 1);
                appDrawerPage = selectedAppIndex / pageSize;
                invalidate();
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (selectedAppIndex % columns == columns - 1 || selectedAppIndex == cachedApps.size() - 1) {
                moveAppDrawerPage(1);
            } else {
                selectedAppIndex = Math.min(cachedApps.size() - 1, selectedAppIndex + 1);
                appDrawerPage = selectedAppIndex / pageSize;
                invalidate();
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (selectedAppIndex - columns >= appDrawerPage * pageSize) {
                selectedAppIndex -= columns;
                invalidate();
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            int next = selectedAppIndex + columns;
            int pageEnd = Math.min(cachedApps.size(), (appDrawerPage + 1) * pageSize);
            if (next < pageEnd) {
                selectedAppIndex = next;
                invalidate();
            }
            return true;
        }

        if (isEnterKey(keyCode)) {
            if (selectedAppIndex >= 0 && selectedAppIndex < cachedApps.size()) {
                AppEntry app = cachedApps.get(selectedAppIndex);
                openApp(app.label, app.pkg, app.cls);
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
            moveAppDrawerPage(1);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
            moveAppDrawerPage(-1);
            return true;
        }

        return true;
    }

    private boolean handleMineKey(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            focusArea = 0;
            invalidate();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            selectedMineRowIndex = clamp(selectedMineRowIndex - 1, 0, 4);
            invalidate();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            selectedMineRowIndex = clamp(selectedMineRowIndex + 1, 0, 4);
            invalidate();
            return true;
        }

        if (isEnterKey(keyCode)) {
            if (selectedMineRowIndex == 0) {
                showEditDialog("车主名称", "owner_name", "江灵夏草");
            } else if (selectedMineRowIndex == 1) {
                showEditDialog("汽车品牌", "car_brand", "奥迪");
            } else if (selectedMineRowIndex == 2) {
                showEditDialog("签名", "signature", "MikuCarLauncher");
            } else if (selectedMineRowIndex == 3) {
                Intent intent = new Intent(getContext(), DesktopSettingsActivity.class);
                closeAmapFloatingMap();
                getContext().startActivity(intent);
            } else if (selectedMineRowIndex == 4) {
                showAboutDialog();
            }
            return true;
        }

        return true;
    }

    private boolean isNavigationKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_PAGE_DOWN
                || keyCode == KeyEvent.KEYCODE_PAGE_UP;
    }

    private boolean isEnterKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (handleHardwareKey(keyCode)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void moveAppDrawerPage(int delta) {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int columns = clamp(sp.getInt("drawer_grid_columns", 6), 3, 8);
        int rows = clamp(sp.getInt("drawer_grid_rows", 3), 1, 6);
        int pageSize = Math.max(1, rows * columns);

        loadAppsIfNeeded();
        int pageCount = getAppPageCount(pageSize);
        if (pageCount <= 0) {
            appDrawerPage = 0;
            selectedAppIndex = 0;
            invalidate();
            return;
        }

        int nextPage = clamp(appDrawerPage + delta, 0, pageCount - 1);
        if (nextPage == appDrawerPage) {
            invalidate();
            return;
        }

        appAnimFromPage = appDrawerPage;
        appAnimToPage = nextPage;
        appAnimDirection = nextPage > appDrawerPage ? 1 : -1;
        appAnimStartMs = System.currentTimeMillis();
        appPageAnimating = true;

        appDrawerPage = nextPage;
        selectedAppIndex = Math.min(cachedApps.size() - 1, appDrawerPage * pageSize);
        postInvalidateOnAnimation();
    }

    private void clampAppDrawerSelection() {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int columns = clamp(sp.getInt("drawer_grid_columns", 6), 3, 8);
        int rows = clamp(sp.getInt("drawer_grid_rows", 3), 1, 6);
        int pageSize = Math.max(1, rows * columns);

        loadAppsIfNeeded();
        int pageCount = getAppPageCount(pageSize);
        appDrawerPage = clamp(appDrawerPage, 0, Math.max(0, pageCount - 1));

        if (cachedApps.isEmpty()) {
            selectedAppIndex = 0;
            return;
        }

        selectedAppIndex = clamp(selectedAppIndex, 0, cachedApps.size() - 1);
        int pageStart = appDrawerPage * pageSize;
        int pageEnd = Math.min(cachedApps.size(), pageStart + pageSize);
        if (selectedAppIndex < pageStart || selectedAppIndex >= pageEnd) {
            selectedAppIndex = pageStart;
        }
    }

    private int getAppPageCount(int pageSize) {
        if (cachedApps.isEmpty()) {
            return 1;
        }
        return (cachedApps.size() + pageSize - 1) / pageSize;
    }

    private void sendMikuTextShortcut(String text, String label) {
        if (!MikuTextDisplayNodeController.isEnabled(getContext())
                || MikuTextDisplayNodeController.getIp(getContext()).length() == 0) {
            Toast.makeText(getContext(), "请先在设置里启用快捷文字屏节点并填写屏幕 IP", Toast.LENGTH_LONG).show();
            return;
        }
        if (text == null || text.trim().length() == 0) {
            Toast.makeText(getContext(), "快捷文字为空", Toast.LENGTH_SHORT).show();
            return;
        }
        MikuTextDisplayNodeController.sendShow(getContext(), text);
        Toast.makeText(getContext(), "已发送文字：" + (label == null || label.length() == 0 ? text : label), Toast.LENGTH_SHORT).show();
    }

    private void showMikuTextShoutDialog() {
        final EditText input = new EditText(getContext());
        input.setHint("输入要发送到后屏的喊话文字");
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setPadding(28, 12, 28, 12);

        new AlertDialog.Builder(getContext())
                .setTitle("喊话文字")
                .setView(input)
                .setPositiveButton("发送", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String text = input.getText() == null ? "" : input.getText().toString().trim();
                        if (text.length() == 0) {
                            Toast.makeText(getContext(), "喊话文字不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        sendMikuTextShortcut(text, "喊话文字");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openApp(String label, String pkg, String cls) {
        try {
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(Intent.CATEGORY_LAUNCHER);
            if (cls != null && cls.length() > 0) {
                launch.setClassName(pkg, cls);
            } else {
                Intent fallback = getContext().getPackageManager().getLaunchIntentForPackage(pkg);
                if (fallback != null) {
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    closeAmapFloatingMap();
                    getContext().startActivity(fallback);
                    return;
                }
                launch.setPackage(pkg);
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            closeAmapFloatingMap();
            getContext().startActivity(launch);
        } catch (Throwable t) {
            Toast.makeText(getContext(), "无法打开：" + label, Toast.LENGTH_SHORT).show();
        }
    }

    private void hideApp(String pkg, String label) {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> hidden = new HashSet<String>(sp.getStringSet("hidden_apps", new HashSet<String>()));
        hidden.add(pkg);
        sp.edit().putStringSet("hidden_apps", hidden).apply();
        cachedApps.clear();
        lastAppLoadTime = 0L;
        appListLoaded = false;
        appListLoading = false;
        Toast.makeText(getContext(), "已隐藏：" + label, Toast.LENGTH_SHORT).show();
        invalidate();
    }

    private void loadAppsIfNeeded() {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> hidden = new HashSet<String>(sp.getStringSet("hidden_apps", new HashSet<String>()));
        final int hiddenSignature = hidden.hashCode();
        final int iconSignature = IconPackManager.getIconSignature(getContext());
        final long forceReloadToken = sp.getLong(AppDrawerCacheManager.PREF_APP_DRAWER_FORCE_RELOAD_AT, 0L);

        if (forceReloadToken != appDrawerForceReloadToken) {
            AppDrawerCacheManager.clearMemoryCache();
            cachedApps.clear();
            appListLoaded = false;
            appListLoading = false;
            appDrawerForceReloadToken = forceReloadToken;
        }

        final Context appContext = getContext().getApplicationContext();
        final Set<String> hiddenSnapshot = new HashSet<String>(hidden);

        if (appListLoaded && hiddenSignature == appHiddenSignature && iconSignature == appIconSignature
                && forceReloadToken == appDrawerForceReloadToken) {
            return;
        }

        // 优先读取本地“应用略缩图”缓存。缓存命中时不再现场 loadIcon/query icon pack，低速车规级存储上会快很多。
        List<AppDrawerCacheManager.CacheEntry> diskCache = AppDrawerCacheManager.loadCachedEntriesFast(
                appContext, hiddenSignature, iconSignature);
        if (diskCache != null) {
            cachedApps.clear();
            cachedApps.addAll(toAppEntries(appContext, diskCache));
            appHiddenSignature = hiddenSignature;
            appIconSignature = iconSignature;
            appDrawerForceReloadToken = forceReloadToken;
            lastAppLoadTime = System.currentTimeMillis();
            appListLoaded = true;
            appListLoading = false;
            clampAppDrawerSelectionAfterLoad();
            invalidate();
            return;
        }

        if (appListLoading) {
            return;
        }

        appListLoading = true;
        appHiddenSignature = hiddenSignature;
        appIconSignature = iconSignature;
        appDrawerForceReloadToken = forceReloadToken;

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppDrawerCacheManager.CacheEntry> rebuilt = AppDrawerCacheManager.rebuildCache(
                        appContext, hiddenSnapshot, hiddenSignature, iconSignature);
                final List<AppEntry> result = toAppEntries(appContext, rebuilt);

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        cachedApps.clear();
                        cachedApps.addAll(result);
                        appDrawerForceReloadToken = forceReloadToken;
                        lastAppLoadTime = System.currentTimeMillis();
                        appListLoaded = true;
                        appListLoading = false;
                        clampAppDrawerSelectionAfterLoad();
                        invalidate();
                    }
                });
            }
        }, "MikuCarLauncher-AppDrawerThumbCacheBuilder");

        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    private List<AppEntry> toAppEntries(Context context, List<AppDrawerCacheManager.CacheEntry> entries) {
        List<AppEntry> result = new ArrayList<AppEntry>();
        if (entries == null) {
            return result;
        }
        for (AppDrawerCacheManager.CacheEntry item : entries) {
            Drawable icon = null;
            if (item.icon != null) {
                icon = new BitmapDrawable(context.getResources(), item.icon);
            }
            result.add(new AppEntry(item.label, item.pkg, item.cls, icon));
        }
        return result;
    }

    private void refreshAppDrawerCacheInBackgroundIfNeeded(final Context appContext,
                                                          final Set<String> hiddenSnapshot,
                                                          final int hiddenSignature,
                                                          final int iconSignature) {
        if (appCacheRefreshRunning) {
            return;
        }
        appCacheRefreshRunning = true;
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean changed = AppDrawerCacheManager.isPackageSignatureChanged(appContext);
                final List<AppEntry> refreshed;
                if (changed) {
                    refreshed = toAppEntries(appContext, AppDrawerCacheManager.rebuildCache(
                            appContext, hiddenSnapshot, hiddenSignature, iconSignature));
                } else {
                    refreshed = null;
                }

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        appCacheRefreshRunning = false;
                        if (refreshed != null) {
                            cachedApps.clear();
                            cachedApps.addAll(refreshed);
                            appListLoaded = true;
                            appListLoading = false;
                            clampAppDrawerSelectionAfterLoad();
                            invalidate();
                        }
                    }
                });
            }
        }, "MikuCarLauncher-AppDrawerThumbCacheRefresh");
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    private void clampAppDrawerSelectionAfterLoad() {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int columns = clamp(sp.getInt("drawer_grid_columns", 6), 3, 8);
        int rows = clamp(sp.getInt("drawer_grid_rows", 3), 1, 6);
        int pageSize = Math.max(1, rows * columns);
        int pageCount = getAppPageCount(pageSize);
        appDrawerPage = clamp(appDrawerPage, 0, Math.max(0, pageCount - 1));

        if (cachedApps.isEmpty()) {
            selectedAppIndex = 0;
            return;
        }

        int pageStart = appDrawerPage * pageSize;
        int pageEnd = Math.min(cachedApps.size(), pageStart + pageSize);
        selectedAppIndex = clamp(selectedAppIndex, 0, cachedApps.size() - 1);
        if (selectedAppIndex < pageStart || selectedAppIndex >= pageEnd) {
            selectedAppIndex = pageStart;
        }
    }

    private void drawFocusStroke(Canvas c, RectF r) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4f);
        p.setColor(accentColor());
        RectF rr = new RectF(r.left + 3f, r.top + 3f, r.right - 3f, r.bottom - 3f);
        c.drawRoundRect(rr, 18f, 18f, p);
    }

    private void drawCenteredTextSingleLine(Canvas c, String text, float centerX, float y, Paint paint, float maxWidth) {
        if (text == null) {
            text = "";
        }
        String result = text;
        while (paint.measureText(result) > maxWidth && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        if (!result.equals(text) && result.length() > 1) {
            result = result.substring(0, Math.max(1, result.length() - 1)) + "…";
        }
        c.drawText(result, centerX, y, paint);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class AppEntry {
        final String label;
        final String pkg;
        final String cls;
        final Drawable icon;
        final String type;
        final String textPayload;
        final String iconText;

        AppEntry(String label, String pkg, String cls, Drawable icon) {
            this(label, pkg, cls, icon, "app", "", "");
        }

        AppEntry(String label, String pkg, String cls, Drawable icon, String type, String textPayload, String iconText) {
            this.label = label;
            this.pkg = pkg;
            this.cls = cls;
            this.icon = icon;
            this.type = type == null ? "app" : type;
            this.textPayload = textPayload == null ? "" : textPayload;
            this.iconText = iconText == null ? "" : iconText;
        }

        static AppEntry textDisplay(String label, String type, String textPayload, String iconText) {
            return new AppEntry(label,
                    MikuTextDisplayNodeController.COMMON_PKG_MARKER,
                    type,
                    null,
                    type,
                    textPayload,
                    iconText);
        }

        boolean isTextDisplayShortcut() {
            return MikuTextDisplayNodeController.isTextDisplayCommonType(type);
        }

        boolean isShoutShortcut() {
            return MikuTextDisplayNodeController.COMMON_TYPE_SHOUT.equals(type);
        }
    }
}
