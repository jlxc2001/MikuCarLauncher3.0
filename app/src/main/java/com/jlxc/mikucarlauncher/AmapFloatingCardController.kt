package com.jlxc.mikucarlauncher

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Toast
import kotlin.math.roundToInt

/**
 * 1号地图卡片“伪嵌入”控制器。
 *
 * 注意：
 * - 本类不把高德地图 Activity 嵌入到 Launcher View 中。
 * - Launcher 只负责测量 mapCardContainer 的屏幕绝对坐标。
 * - 真正的 WindowManager 悬浮窗由 com.autonavi.amapautoys 自己创建。
 * - Launcher 通过广播通知悬浮版高德地图显示/关闭地图窗口。
 */
class AmapFloatingCardController(
    private val activity: Activity,
    private val mapCardContainer: View,
    private val fallbackInsetDp: Float = DEFAULT_INSET_DP.toFloat()
) {
    companion object {
        const val AMAP_FLOATING_PACKAGE = "com.autonavi.amapautoys"
        const val ACTION_SHOW_MAP = "com.autonavi.plus.showmap"
        const val ACTION_CLOSE_MAP = "com.autonavi.plus.closemap"

        const val PREF_AMAP_CARD_INSET_DP = "amap_card_inset_dp"
        const val PREF_AMAP_CARD_X_OFFSET_PX = "amap_card_x_offset_px"
        const val PREF_AMAP_CARD_Y_OFFSET_PX = "amap_card_y_offset_px"
        const val PREF_AMAP_CARD_WIDTH_SCALE_PERCENT = "amap_card_width_scale_percent"
        const val PREF_AMAP_CARD_HEIGHT_SCALE_PERCENT = "amap_card_height_scale_percent"
        const val PREF_AMAP_CARD_FORCE_WIDTH_PX = "amap_card_force_width_px"
        const val PREF_AMAP_CARD_FORCE_HEIGHT_PX = "amap_card_force_height_px"
        const val PREF_AMAP_CARD_DPI = "amap_card_dpi"

        private const val PREF_AMAP_CARD_PRESET_VERSION = "amap_card_preset_version"
        private const val CURRENT_PRESET_VERSION = 2
        private const val OLD_DEFAULT_X_OFFSET_PX = 43
        private const val OLD_DEFAULT_FORCE_WIDTH_PX = 750

        const val DEFAULT_INSET_DP = 0
        const val DEFAULT_X_OFFSET_PX = 3
        const val DEFAULT_Y_OFFSET_PX = 2
        const val DEFAULT_WIDTH_SCALE_PERCENT = 100
        const val DEFAULT_HEIGHT_SCALE_PERCENT = 100
        const val DEFAULT_FORCE_WIDTH_PX = 1125
        const val DEFAULT_FORCE_HEIGHT_PX = 515
        const val DEFAULT_DPI = 200

        private const val MIN_SCALE_PERCENT = 10
        private const val MAX_SCALE_PERCENT = 300
        private const val AMAP_WAKE_RETRY_DELAY_MS = 900L
        private const val LAUNCHER_BACK_DELAY_MS = 550L


        data class FloatingCardSettings(
            val insetDp: Int,
            val xOffsetPx: Int,
            val yOffsetPx: Int,
            val widthScalePercent: Int,
            val heightScalePercent: Int,
            val forceWidthPx: Int,
            val forceHeightPx: Int,
            val dpi: Int
        )

        @JvmStatic
        fun isAmapFloatingInstalled(context: Context): Boolean {
            return try {
                context.packageManager.getPackageInfo(AMAP_FLOATING_PACKAGE, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            } catch (_: Throwable) {
                false
            }
        }

        @JvmStatic
        fun openAmapOverlayPermissionPage(context: Context) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$AMAP_FLOATING_PACKAGE")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Throwable) {
                try {
                    val fallback = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(fallback)
                } catch (_: Throwable) {
                    Toast.makeText(context, "无法打开悬浮窗权限设置", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JvmStatic
        fun overlayWindowTypeForAmapSide(): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                android.view.WindowManager.LayoutParams.TYPE_PHONE
            }
        }

        @JvmStatic
        fun readSettings(context: Context): FloatingCardSettings {
            migrateRecommendedDefaultsIfNeeded(context)
            val sp = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            return FloatingCardSettings(
                insetDp = sp.getInt(PREF_AMAP_CARD_INSET_DP, DEFAULT_INSET_DP).coerceAtLeast(0),
                xOffsetPx = sp.getInt(PREF_AMAP_CARD_X_OFFSET_PX, DEFAULT_X_OFFSET_PX),
                yOffsetPx = sp.getInt(PREF_AMAP_CARD_Y_OFFSET_PX, DEFAULT_Y_OFFSET_PX),
                widthScalePercent = sp.getInt(PREF_AMAP_CARD_WIDTH_SCALE_PERCENT, DEFAULT_WIDTH_SCALE_PERCENT)
                    .coerceIn(MIN_SCALE_PERCENT, MAX_SCALE_PERCENT),
                heightScalePercent = sp.getInt(PREF_AMAP_CARD_HEIGHT_SCALE_PERCENT, DEFAULT_HEIGHT_SCALE_PERCENT)
                    .coerceIn(MIN_SCALE_PERCENT, MAX_SCALE_PERCENT),
                forceWidthPx = sp.getInt(PREF_AMAP_CARD_FORCE_WIDTH_PX, DEFAULT_FORCE_WIDTH_PX).coerceAtLeast(0),
                forceHeightPx = sp.getInt(PREF_AMAP_CARD_FORCE_HEIGHT_PX, DEFAULT_FORCE_HEIGHT_PX).coerceAtLeast(0),
                dpi = sp.getInt(PREF_AMAP_CARD_DPI, DEFAULT_DPI).coerceAtLeast(0)
            )
        }

        private fun migrateRecommendedDefaultsIfNeeded(context: Context) {
            val sp = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            if (sp.getInt(PREF_AMAP_CARD_PRESET_VERSION, 0) >= CURRENT_PRESET_VERSION) {
                return
            }

            val hasSavedAmapSettings = sp.contains(PREF_AMAP_CARD_X_OFFSET_PX)
                    || sp.contains(PREF_AMAP_CARD_FORCE_WIDTH_PX)
                    || sp.contains(PREF_AMAP_CARD_FORCE_HEIGHT_PX)
                    || sp.contains(PREF_AMAP_CARD_DPI)

            val looksLikeOldRecommended = hasSavedAmapSettings
                    && sp.getInt(PREF_AMAP_CARD_INSET_DP, DEFAULT_INSET_DP) == DEFAULT_INSET_DP
                    && sp.getInt(PREF_AMAP_CARD_X_OFFSET_PX, OLD_DEFAULT_X_OFFSET_PX) == OLD_DEFAULT_X_OFFSET_PX
                    && sp.getInt(PREF_AMAP_CARD_Y_OFFSET_PX, DEFAULT_Y_OFFSET_PX) == DEFAULT_Y_OFFSET_PX
                    && sp.getInt(PREF_AMAP_CARD_WIDTH_SCALE_PERCENT, DEFAULT_WIDTH_SCALE_PERCENT) == DEFAULT_WIDTH_SCALE_PERCENT
                    && sp.getInt(PREF_AMAP_CARD_HEIGHT_SCALE_PERCENT, DEFAULT_HEIGHT_SCALE_PERCENT) == DEFAULT_HEIGHT_SCALE_PERCENT
                    && sp.getInt(PREF_AMAP_CARD_FORCE_WIDTH_PX, OLD_DEFAULT_FORCE_WIDTH_PX) == OLD_DEFAULT_FORCE_WIDTH_PX
                    && sp.getInt(PREF_AMAP_CARD_FORCE_HEIGHT_PX, DEFAULT_FORCE_HEIGHT_PX) == DEFAULT_FORCE_HEIGHT_PX
                    && sp.getInt(PREF_AMAP_CARD_DPI, DEFAULT_DPI) == DEFAULT_DPI

            val editor = sp.edit().putInt(PREF_AMAP_CARD_PRESET_VERSION, CURRENT_PRESET_VERSION)
            if (looksLikeOldRecommended) {
                editor.putInt(PREF_AMAP_CARD_INSET_DP, DEFAULT_INSET_DP)
                    .putInt(PREF_AMAP_CARD_X_OFFSET_PX, DEFAULT_X_OFFSET_PX)
                    .putInt(PREF_AMAP_CARD_Y_OFFSET_PX, DEFAULT_Y_OFFSET_PX)
                    .putInt(PREF_AMAP_CARD_WIDTH_SCALE_PERCENT, DEFAULT_WIDTH_SCALE_PERCENT)
                    .putInt(PREF_AMAP_CARD_HEIGHT_SCALE_PERCENT, DEFAULT_HEIGHT_SCALE_PERCENT)
                    .putInt(PREF_AMAP_CARD_FORCE_WIDTH_PX, DEFAULT_FORCE_WIDTH_PX)
                    .putInt(PREF_AMAP_CARD_FORCE_HEIGHT_PX, DEFAULT_FORCE_HEIGHT_PX)
                    .putInt(PREF_AMAP_CARD_DPI, DEFAULT_DPI)
            }
            editor.apply()
        }

        @JvmStatic
        fun getSettingsSummary(context: Context): String {
            val s = readSettings(context)
            val dpiText = if (s.dpi > 0) "${s.dpi}" else "不强制"
            return "内缩 ${s.insetDp}dp，偏移 X ${s.xOffsetPx}px / Y ${s.yOffsetPx}px，" +
                    "缩放 ${s.widthScalePercent}%×${s.heightScalePercent}%，" +
                    "强制 ${s.forceWidthPx}×${s.forceHeightPx}px，DPI $dpiText"
        }

        @JvmStatic
        fun adjustedRectFromRaw(context: Context, rawLeft: Int, rawTop: Int, rawWidth: Int, rawHeight: Int): Rect {
            return adjustedRectFromRaw(context, rawLeft, rawTop, rawWidth, rawHeight, readSettings(context))
        }

        private fun adjustedRectFromRaw(
            context: Context,
            rawLeft: Int,
            rawTop: Int,
            rawWidth: Int,
            rawHeight: Int,
            settings: FloatingCardSettings
        ): Rect {
            val inset = dp(context, settings.insetDp.toFloat()).roundToInt()
            val availableWidth = (rawWidth - inset * 2).coerceAtLeast(1)
            val availableHeight = (rawHeight - inset * 2).coerceAtLeast(1)

            val width = if (settings.forceWidthPx > 0) {
                settings.forceWidthPx
            } else {
                (availableWidth * settings.widthScalePercent / 100f).roundToInt()
            }.coerceAtLeast(1)

            val height = if (settings.forceHeightPx > 0) {
                settings.forceHeightPx
            } else {
                (availableHeight * settings.heightScalePercent / 100f).roundToInt()
            }.coerceAtLeast(1)

            val x = rawLeft + inset + settings.xOffsetPx
            val y = rawTop + inset + settings.yOffsetPx
            return Rect(x, y, x + width, y + height)
        }

        @JvmStatic
        fun sendShowMapBroadcast(context: Context, rect: Rect) {
            val settings = readSettings(context)
            sendShowMapBroadcast(context, rect, settings.dpi)
        }

        @JvmStatic
        fun sendShowMapBroadcast(context: Context, rect: Rect, dpi: Int) {
            val intent = Intent(ACTION_SHOW_MAP)
            intent.setPackage(AMAP_FLOATING_PACKAGE)
            intent.putExtra("x", rect.left)
            intent.putExtra("y", rect.top)
            intent.putExtra("w", rect.width())
            intent.putExtra("h", rect.height())
            intent.putExtra("dpi", dpi.coerceAtLeast(0))
            context.sendBroadcast(intent)
        }

        @JvmStatic
        fun sendCloseMapBroadcast(context: Context) {
            val intent = Intent(ACTION_CLOSE_MAP)
            intent.setPackage(AMAP_FLOATING_PACKAGE)
            context.sendBroadcast(intent)
        }

        private fun dp(context: Context, value: Float): Float {
            return value * context.resources.displayMetrics.density
        }
    }

    private var lastRect: Rect? = null
    private var lastDpi: Int = -1
    private var isShown = false
    private var allowShowOnHome = false
    private var hasTriedWakeAmapProcess = false
    private var pendingWakeRetry = false
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 统一由 MainActivity 告诉 controller 当前是否允许显示。
     * 只有 MainActivity 处于前台、有窗口焦点、并且当前页面是首页时才允许 true。
     * 这样可以避免设置页 / 应用抽屉 / 我的页面 / 切后台后又被 onResume 或焦点恢复误拉起。
     */
    fun setHomeVisible(visible: Boolean) {
        allowShowOnHome = visible
        if (visible) {
            postUpdateMapWindow()
        } else {
            closeMap()
        }
    }

    fun onLayoutReady() {
        if (allowShowOnHome) {
            postUpdateMapWindow()
        } else {
            closeMap()
        }
    }

    fun onPause() {
        allowShowOnHome = false
        closeMap()
    }

    fun closeMap() {
        isShown = false
        lastRect = null
        lastDpi = -1
        sendCloseMapBroadcast(activity)
    }

    fun postUpdateMapWindow() {
        mapCardContainer.post {
            updateMapWindow()
        }
    }

    fun updateMapWindow() {
        if (!allowShowOnHome) {
            closeMap()
            return
        }

        if (!isAmapFloatingInstalled(activity)) {
            closeMap()
            return
        }

        if (mapCardContainer.width <= 0 || mapCardContainer.height <= 0) {
            return
        }

        val settings = readInstanceSettings()
        val rect = measureCardRect(settings)
        if (rect.width() <= 0 || rect.height() <= 0) {
            return
        }

        // 有些高德共存悬浮版只在进程已启动后才注册 showmap 广播接收器。
        // 所以首次首页显示时，如果判断高德进程可能还没运行，先轻拉起一次高德，再回到 Launcher 后补发 showmap。
        if (!isShown && shouldWakeAmapProcessBeforeShow()) {
            wakeAmapProcessAndRetry()
            return
        }

        sendShowMapIfNeeded(rect, settings.dpi, false)
    }

    private fun sendShowMapIfNeeded(rect: Rect, dpi: Int, force: Boolean) {
        // 避免每一帧重复广播；位置/大小/DPI 改变或首次显示时才广播。
        if (!force && isShown && rect == lastRect && dpi == lastDpi) {
            return
        }

        sendShowMapBroadcast(activity, rect, dpi)

        isShown = true
        lastRect = Rect(rect)
        lastDpi = dpi
    }

    private fun shouldWakeAmapProcessBeforeShow(): Boolean {
        if (hasTriedWakeAmapProcess || pendingWakeRetry) {
            return false
        }
        return !isAmapProcessProbablyRunning()
    }

    private fun wakeAmapProcessAndRetry() {
        hasTriedWakeAmapProcess = true
        pendingWakeRetry = true

        // 先发一次广播：如果高德端有静态 Receiver，这次就能直接显示；如果没有，后面再补发。
        try {
            val settings = readInstanceSettings()
            val rect = measureCardRect(settings)
            if (rect.width() > 0 && rect.height() > 0) {
                sendShowMapBroadcast(activity, rect, settings.dpi)
            }
        } catch (_: Throwable) {
        }

        tryLaunchAmapForProcessWarmup()

        // 启动高德会让 Launcher 暂时失焦。短暂延迟后把 Launcher 拉回前台，随后首页逻辑会再次 showmap。
        mainHandler.postDelayed({
            bringLauncherBackToFront()
        }, LAUNCHER_BACK_DELAY_MS)

        mainHandler.postDelayed({
            pendingWakeRetry = false
            if (!allowShowOnHome) {
                return@postDelayed
            }
            try {
                val settings = readInstanceSettings()
                val rect = measureCardRect(settings)
                if (rect.width() > 0 && rect.height() > 0) {
                    sendShowMapIfNeeded(rect, settings.dpi, true)
                }
            } catch (_: Throwable) {
            }
        }, AMAP_WAKE_RETRY_DELAY_MS)
    }

    private fun tryLaunchAmapForProcessWarmup() {
        try {
            val launchIntent = activity.packageManager.getLaunchIntentForPackage(AMAP_FLOATING_PACKAGE) ?: return
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            activity.startActivity(launchIntent)
        } catch (_: Throwable) {
        }
    }

    private fun bringLauncherBackToFront() {
        try {
            val intent = Intent(activity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            activity.startActivity(intent)
        } catch (_: Throwable) {
        }
    }

    private fun isAmapProcessProbablyRunning(): Boolean {
        return try {
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
            val processes = am.runningAppProcesses ?: return false
            processes.any { info ->
                info.processName == AMAP_FLOATING_PACKAGE || info.pkgList?.contains(AMAP_FLOATING_PACKAGE) == true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun readInstanceSettings(): FloatingCardSettings {
        val settings = readSettings(activity)
        val sp = activity.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        if (!sp.contains(PREF_AMAP_CARD_INSET_DP) && fallbackInsetDp.roundToInt() != DEFAULT_INSET_DP) {
            return settings.copy(insetDp = fallbackInsetDp.roundToInt().coerceAtLeast(0))
        }
        return settings
    }

    private fun measureCardRect(settings: FloatingCardSettings): Rect {
        val location = IntArray(2)
        mapCardContainer.getLocationOnScreen(location)

        return adjustedRectFromRaw(
            activity,
            location[0],
            location[1],
            mapCardContainer.measuredWidth,
            mapCardContainer.measuredHeight,
            settings
        )
    }
}
