package app.danmaku.desktop

import app.danmaku.domain.DanmakuDisplaySettings

data class DesktopPlaybackPreferences(
    val playbackRate: Float = DEFAULT_PLAYBACK_RATE,
    val volumePercent: Int = DEFAULT_VOLUME_PERCENT,
    val videoAspectMode: DesktopVideoAspectMode = DesktopVideoAspectMode.DEFAULT,
    val danmakuSettings: DanmakuDisplaySettings = DanmakuDisplaySettings(),
) {
    init {
        require(playbackRate > 0) { "playbackRate must be positive" }
        require(volumePercent in 0..100) { "volumePercent must be between 0 and 100" }
    }
}

class DesktopPlaybackPreferencesStore(
    private val catalogStore: DesktopLibraryCatalogStore,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    fun load(): DesktopPlaybackPreferences =
        DesktopPlaybackPreferences(
            playbackRate = catalogStore.loadSetting(PLAYBACK_RATE_SETTING_KEY)
                ?.value
                ?.toFloatOrNull()
                ?.takeIf { it > 0 }
                ?: DEFAULT_PLAYBACK_RATE,
            volumePercent = catalogStore.loadSetting(VOLUME_SETTING_KEY)
                ?.value
                ?.toIntOrNull()
                ?.takeIf { it in 0..100 }
                ?: DEFAULT_VOLUME_PERCENT,
            videoAspectMode = catalogStore.loadSetting(VIDEO_ASPECT_MODE_SETTING_KEY)
                ?.value
                ?.toVideoAspectMode()
                ?: DesktopVideoAspectMode.DEFAULT,
            danmakuSettings = loadDanmakuSettings(),
        )

    fun savePlaybackRate(rate: Float) {
        require(rate > 0) { "rate must be positive" }
        saveSetting(PLAYBACK_RATE_SETTING_KEY, rate.toString())
    }

    fun saveVolumePercent(volumePercent: Int) {
        require(volumePercent in 0..100) { "volumePercent must be between 0 and 100" }
        saveSetting(VOLUME_SETTING_KEY, volumePercent.toString())
    }

    fun saveVideoAspectMode(mode: DesktopVideoAspectMode) {
        saveSetting(VIDEO_ASPECT_MODE_SETTING_KEY, mode.name)
    }

    fun saveDanmakuSettings(settings: DanmakuDisplaySettings) {
        saveSetting(DANMAKU_VISIBLE_SETTING_KEY, settings.visible.toString())
        saveSetting(DANMAKU_OPACITY_SETTING_KEY, settings.opacityPercent.toString())
        saveSetting(DANMAKU_FONT_SCALE_SETTING_KEY, settings.fontScalePercent.toString())
        saveSetting(DANMAKU_SPEED_SETTING_KEY, settings.speedPercent.toString())
        saveSetting(DANMAKU_DENSITY_SETTING_KEY, settings.densityPercent.toString())
        saveSetting(DANMAKU_DISPLAY_AREA_SETTING_KEY, settings.displayAreaPercent.toString())
        saveSetting(DANMAKU_OFFSET_SETTING_KEY, settings.offsetMs.toString())
        saveSetting(DANMAKU_KEYWORD_FILTERS_SETTING_KEY, settings.keywordFilters.joinToString("\n"))
        saveSetting(DANMAKU_REGEX_FILTERS_SETTING_KEY, settings.regexFilters.joinToString("\n"))
    }

    private fun loadDanmakuSettings(): DanmakuDisplaySettings =
        runCatching {
            DanmakuDisplaySettings(
                visible = catalogStore.loadSetting(DANMAKU_VISIBLE_SETTING_KEY)
                    ?.value
                    ?.toBooleanStrictOrNull()
                    ?: true,
                opacityPercent = catalogStore.loadSetting(DANMAKU_OPACITY_SETTING_KEY)
                    ?.value
                    ?.toIntOrNull()
                    ?: 100,
                fontScalePercent = catalogStore.loadSetting(DANMAKU_FONT_SCALE_SETTING_KEY)
                    ?.value
                    ?.toIntOrNull()
                    ?: 100,
                speedPercent = catalogStore.loadSetting(DANMAKU_SPEED_SETTING_KEY)
                    ?.value
                    ?.toIntOrNull()
                    ?: 100,
                densityPercent = catalogStore.loadSetting(DANMAKU_DENSITY_SETTING_KEY)
                    ?.value
                    ?.toIntOrNull()
                    ?: 100,
                displayAreaPercent = catalogStore.loadSetting(DANMAKU_DISPLAY_AREA_SETTING_KEY)
                    ?.value
                    ?.toIntOrNull()
                    ?: 100,
                offsetMs = catalogStore.loadSetting(DANMAKU_OFFSET_SETTING_KEY)
                    ?.value
                    ?.toLongOrNull()
                    ?: 0,
                keywordFilters = catalogStore.loadSetting(DANMAKU_KEYWORD_FILTERS_SETTING_KEY)
                    ?.value
                    .toFilterList(),
                regexFilters = catalogStore.loadSetting(DANMAKU_REGEX_FILTERS_SETTING_KEY)
                    ?.value
                    .toFilterList(),
            )
        }.getOrDefault(DanmakuDisplaySettings())

    private fun saveSetting(
        key: String,
        value: String,
    ) {
        catalogStore.saveSetting(
            DesktopAppSetting(
                key = key,
                value = value,
                updatedAtEpochMs = currentTimeMillis(),
            ),
        )
    }
}

private fun String.toVideoAspectMode(): DesktopVideoAspectMode? =
    runCatching { DesktopVideoAspectMode.valueOf(this) }.getOrNull()

private fun String?.toFilterList(): List<String> =
    this
        ?.lineSequence()
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?.toList()
        .orEmpty()

private const val DEFAULT_PLAYBACK_RATE = 1f
private const val DEFAULT_VOLUME_PERCENT = 100
private const val PLAYBACK_RATE_SETTING_KEY = "playback.default_rate"
private const val VOLUME_SETTING_KEY = "playback.volume_percent"
private const val VIDEO_ASPECT_MODE_SETTING_KEY = "playback.video_aspect_mode"
private const val DANMAKU_VISIBLE_SETTING_KEY = "danmaku.visible"
private const val DANMAKU_OPACITY_SETTING_KEY = "danmaku.opacity_percent"
private const val DANMAKU_FONT_SCALE_SETTING_KEY = "danmaku.font_scale_percent"
private const val DANMAKU_SPEED_SETTING_KEY = "danmaku.speed_percent"
private const val DANMAKU_DENSITY_SETTING_KEY = "danmaku.density_percent"
private const val DANMAKU_DISPLAY_AREA_SETTING_KEY = "danmaku.display_area_percent"
private const val DANMAKU_OFFSET_SETTING_KEY = "danmaku.offset_ms"
private const val DANMAKU_KEYWORD_FILTERS_SETTING_KEY = "danmaku.keyword_filters"
private const val DANMAKU_REGEX_FILTERS_SETTING_KEY = "danmaku.regex_filters"
