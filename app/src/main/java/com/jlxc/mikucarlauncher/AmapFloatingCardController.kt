package com.jlxc.mikucarlauncher

import android.app.Activity
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
import android.util.Log
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
        const val PREF_AMAP_COLD_START_FRONT_WARMUP_ENABLED = "amap_cold_start_front_warmup_enabled"
        const val PREF_AMAP_COLD_START_RETURN_DELAY_MS = "amap_cold_start_return_delay_ms"
        const val PREF_AMAP_COLD_START_LAST_WARMUP_AT = "amap_cold_start_last_warmup_at"

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
        const val DEFAULT_COLD_START_RETURN_DELAY_MS = 8000
        const val DEFAULT_COLD_START_WARMUP_COOLDOWN_MS = 30 * 60 * 1000L

        private const val MIN_SCALE_PERCENT = 10
        private const val MAX_SCALE_PERCENT = 300
        // V0.7.4.0：高德逻辑重置，只保留轻量 showmap 补发，不再做长时间守护/预热状态机。
        private val AMAP_PASSIVE_SHOW_RETRY_DELAYS_MS = longArrayOf(300L, 900L, 1800L)


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
                    "强制 ${s.forceWidthPx}×${s.forceHeightPx}px，DPI $dpiText，高德预热已停用"
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
            Log.i("MikuAmap", "showmap x=${rect.left} y=${rect.top} w=${rect.width()} h=${rect.height()} dpi=${dpi.coerceAtLeast(0)}")
        }

        @JvmStatic
        fun sendCloseMapBroadcast(context: Context) {
            val intent = Intent(ACTION_CLOSE_MAP)
            intent.setPackage(AMAP_FLOATING_PACKAGE)
            context.sendBroadcast(intent)
            Log.i("MikuAmap", "closemap broadcast")
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
        val wasAllowed = allowShowOnHome
        allowShowOnHome = visible
        if (visible) {
            postUpdateMapWindow()
        } else if (wasAllowed || isShown) {
            closeMap()
        }
    }

    fun onLayoutReady() {
        if (allowShowOnHome) {
            postUpdateMapWindow()
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

    /**
     * 首页重复点击“首页”或实体 HOME 键时用于手动修复悬浮窗：
     * 只强制补发 showmap，不再先发送 closemap。
     * 部分高德共存悬浮版在“先 close 后 show”时会出现主悬浮窗刚出现又消失。
     */
    fun reloadMapWindow() {
        if (!allowShowOnHome) {
            return
        }
        // 只补发 showmap，不 closemap，不启动高德进程。
        isShown = false
        lastRect = null
        lastDpi = -1
        hasTriedWakeAmapProcess = false
        pendingWakeRetry = false
        forceShowCurrentMapWindow()
    }

    /**
     * 主动保障主悬浮窗出现：不依赖 lastRect 去重，持续补发 showmap。
     * 用于：开机高德预热返回桌面、首页再次点击首页、实体 HOME 重入、导航过程中高德已有其它小浮窗但主悬浮窗未恢复。
     */
    fun ensureMapWindowVisibleAggressively(closeFirst: Boolean) {
        // V0.7.4.0：保留方法兼容旧调用，但行为降级为单次 showmap。
        // closeFirst 参数不再触发 closemap，避免打断高德导航状态。
        reloadMapWindow()
    }

    fun postUpdateMapWindow() {
        mapCardContainer.post {
            updateMapWindow()
        }
    }

    fun updateMapWindow() {
        if (!allowShowOnHome) {
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

        val firstShow = !isShown
        sendShowMapIfNeeded(rect, settings.dpi, false)

        // 首页首次显示时轻量补发几次 showmap，绝不 closemap / startActivity / force-stop 高德。
        if (firstShow && shouldSchedulePassiveAmapShowRetries()) {
            schedulePassiveAmapShowRetries(rect, settings.dpi)
        }
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

    private fun shouldSchedulePassiveAmapShowRetries(): Boolean {
        if (hasTriedWakeAmapProcess || pendingWakeRetry) {
            return false
        }
        return true
    }

    private fun schedulePassiveAmapShowRetries(rect: Rect, dpi: Int) {
        hasTriedWakeAmapProcess = true
        pendingWakeRetry = true
        val retryRect = Rect(rect)
        val retryDpi = dpi
        AMAP_PASSIVE_SHOW_RETRY_DELAYS_MS.forEachIndexed { index, delayMs ->
            mainHandler.postDelayed({
                if (!allowShowOnHome) {
                    if (index == AMAP_PASSIVE_SHOW_RETRY_DELAYS_MS.lastIndex) {
                        pendingWakeRetry = false
                    }
                    return@postDelayed
                }
                try {
                    sendShowMapBroadcast(activity, retryRect, retryDpi)
                    isShown = true
                    lastRect = Rect(retryRect)
                    lastDpi = retryDpi
                } catch (_: Throwable) {
                }
                if (index == AMAP_PASSIVE_SHOW_RETRY_DELAYS_MS.lastIndex) {
                    pendingWakeRetry = false
                }
            }, delayMs)
        }
    }

    private fun forceShowCurrentMapWindow() {
        if (!allowShowOnHome) {
            return
        }
        if (!isAmapFloatingInstalled(activity)) {
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
        sendShowMapBroadcast(activity, rect, settings.dpi)
        isShown = true
        lastRect = Rect(rect)
        lastDpi = settings.dpi
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
