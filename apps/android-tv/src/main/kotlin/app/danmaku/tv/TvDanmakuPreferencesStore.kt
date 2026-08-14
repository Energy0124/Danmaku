package app.danmaku.tv

import android.content.Context

internal interface TvDanmakuPreferencesPersistence {
    fun load(): TvDanmakuPreferences

    fun save(value: TvDanmakuPreferences)
}

internal class TvDanmakuPreferencesStore(
    context: Context,
): TvDanmakuPreferencesPersistence {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun load(): TvDanmakuPreferences =
        TvDanmakuPreferences(
            enabled = preferences.getBoolean(ENABLED, true),
            showScrolling = preferences.getBoolean(SHOW_SCROLLING, true),
            showTop = preferences.getBoolean(SHOW_TOP, true),
            showBottom = preferences.getBoolean(SHOW_BOTTOM, true),
            opacity = preferences.getFloat(OPACITY, 0.9f).coerceIn(0.2f, 1f),
            fontScale = preferences.getFloat(FONT_SCALE, 1f).coerceIn(0.75f, 1.5f),
            speed = preferences.getFloat(SPEED, 1f).coerceIn(0.5f, 2f),
            maxScreenArea = preferences.getFloat(MAX_SCREEN_AREA, 0.5f).coerceIn(0.2f, 0.8f),
        )

    override fun save(value: TvDanmakuPreferences) {
        preferences.edit()
            .putBoolean(ENABLED, value.enabled)
            .putBoolean(SHOW_SCROLLING, value.showScrolling)
            .putBoolean(SHOW_TOP, value.showTop)
            .putBoolean(SHOW_BOTTOM, value.showBottom)
            .putFloat(OPACITY, value.opacity)
            .putFloat(FONT_SCALE, value.fontScale)
            .putFloat(SPEED, value.speed)
            .putFloat(MAX_SCREEN_AREA, value.maxScreenArea)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "danmaku_tv_playback_preferences"
        const val ENABLED = "danmaku_enabled"
        const val SHOW_SCROLLING = "danmaku_show_scrolling"
        const val SHOW_TOP = "danmaku_show_top"
        const val SHOW_BOTTOM = "danmaku_show_bottom"
        const val OPACITY = "danmaku_opacity"
        const val FONT_SCALE = "danmaku_font_scale"
        const val SPEED = "danmaku_speed"
        const val MAX_SCREEN_AREA = "danmaku_max_screen_area"
    }
}
