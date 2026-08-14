package app.danmaku.mobile

import android.content.Context
import app.danmaku.domain.DanmakuDisplaySettings

internal interface MobileDanmakuSettingsPersistence {
    fun load(): DanmakuDisplaySettings

    fun save(value: DanmakuDisplaySettings)
}

internal class MobileDanmakuSettingsStore(
    context: Context,
) : MobileDanmakuSettingsPersistence {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun load(): DanmakuDisplaySettings =
        DanmakuDisplaySettings(
            visible = preferences.getBoolean(VISIBLE, true),
            showScrolling = preferences.getBoolean(SHOW_SCROLLING, true),
            showTop = preferences.getBoolean(SHOW_TOP, true),
            showBottom = preferences.getBoolean(SHOW_BOTTOM, true),
            opacityPercent = preferences.getInt(OPACITY_PERCENT, 90).coerceIn(0, 100),
            fontScalePercent = preferences.getInt(FONT_SCALE_PERCENT, 100).coerceIn(50, 200),
            speedPercent = preferences.getInt(SPEED_PERCENT, 100).coerceIn(25, 300),
            densityPercent = preferences.getInt(DENSITY_PERCENT, 100).coerceIn(10, 200),
            displayAreaPercent = preferences.getInt(DISPLAY_AREA_PERCENT, 50).coerceIn(10, 100),
            offsetMs = preferences.getLong(OFFSET_MS, 0L).coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS),
        )

    override fun save(value: DanmakuDisplaySettings) {
        preferences.edit()
            .putBoolean(VISIBLE, value.visible)
            .putBoolean(SHOW_SCROLLING, value.showScrolling)
            .putBoolean(SHOW_TOP, value.showTop)
            .putBoolean(SHOW_BOTTOM, value.showBottom)
            .putInt(OPACITY_PERCENT, value.opacityPercent)
            .putInt(FONT_SCALE_PERCENT, value.fontScalePercent)
            .putInt(SPEED_PERCENT, value.speedPercent)
            .putInt(DENSITY_PERCENT, value.densityPercent)
            .putInt(DISPLAY_AREA_PERCENT, value.displayAreaPercent)
            .putLong(OFFSET_MS, value.offsetMs)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "danmaku_mobile_playback_preferences"
        const val VISIBLE = "danmaku_visible"
        const val SHOW_SCROLLING = "danmaku_show_scrolling"
        const val SHOW_TOP = "danmaku_show_top"
        const val SHOW_BOTTOM = "danmaku_show_bottom"
        const val OPACITY_PERCENT = "danmaku_opacity_percent"
        const val FONT_SCALE_PERCENT = "danmaku_font_scale_percent"
        const val SPEED_PERCENT = "danmaku_speed_percent"
        const val DENSITY_PERCENT = "danmaku_density_percent"
        const val DISPLAY_AREA_PERCENT = "danmaku_display_area_percent"
        const val OFFSET_MS = "danmaku_offset_ms"
        const val MAX_OFFSET_MS = 60L * 60L * 1_000L
    }
}
