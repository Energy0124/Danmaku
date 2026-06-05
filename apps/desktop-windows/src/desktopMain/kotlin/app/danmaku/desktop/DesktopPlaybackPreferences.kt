package app.danmaku.desktop

data class DesktopPlaybackPreferences(
    val playbackRate: Float = DEFAULT_PLAYBACK_RATE,
    val volumePercent: Int = DEFAULT_VOLUME_PERCENT,
    val videoAspectMode: DesktopVideoAspectMode = DesktopVideoAspectMode.DEFAULT,
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

private const val DEFAULT_PLAYBACK_RATE = 1f
private const val DEFAULT_VOLUME_PERCENT = 100
private const val PLAYBACK_RATE_SETTING_KEY = "playback.default_rate"
private const val VOLUME_SETTING_KEY = "playback.volume_percent"
private const val VIDEO_ASPECT_MODE_SETTING_KEY = "playback.video_aspect_mode"
