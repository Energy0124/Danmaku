package app.danmaku.desktop

import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackTrack
import app.danmaku.domain.PlaybackTrackKind

internal const val PLAYBACK_HOST_SETTLE_DELAY_MS = 300L

internal const val PLAYBACK_SNAPSHOT_POLL_INTERVAL_MS = 500L

internal const val WINDOWS_PROGRESS_SAVE_INTERVAL_MS = 5_000L

internal const val MAX_DIAGNOSTIC_LOG_ENTRIES = 200

internal const val MAX_SERVER_DASHBOARD_EVENTS = 80

internal const val MAX_BACKGROUND_POSTER_REFRESH_SERIES = 32

internal const val LOCAL_AUTO_NEXT_SETTING_KEY = "playback.local_auto_next"

internal const val DESKTOP_UI_LANGUAGE_SETTING_KEY = "desktop.ui_language"

internal const val MPV_OSC_FULLSCREEN_REQUEST_PROPERTY = "user-data/danmaku-osc/fullscreen-toggle-request"

internal const val MPV_OSC_APP_FULLSCREEN_ON_BINDING = "danmaku_osc/danmaku-osc-app-fullscreen-on"

internal const val MPV_OSC_APP_FULLSCREEN_OFF_BINDING = "danmaku_osc/danmaku-osc-app-fullscreen-off"

internal const val PLAYER_CONTROLS_AUTO_HIDE_MS = 6_000L

internal const val PLAYER_WINDOW_NAVIGATION_HEIGHT_DP = 44

internal const val PLAYER_TOP_CONTROLS_HEIGHT_DP = 74

internal const val PLAYER_BOTTOM_CONTROLS_HEIGHT_DP = 154

private val PLAYBACK_RATE_STEPS = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)

internal fun Float.nextPlaybackRate(): Float {
    val currentIndex = PLAYBACK_RATE_STEPS.indexOfFirst { rate ->
        val delta = rate - this
        delta > -0.01f && delta < 0.01f
    }
    return if (currentIndex == -1) {
        PLAYBACK_RATE_STEPS.first()
    } else {
        PLAYBACK_RATE_STEPS[(currentIndex + 1) % PLAYBACK_RATE_STEPS.size]
    }
}

internal fun PlaybackSnapshot.selectedTrackButtonText(
    kind: PlaybackTrackKind,
    fallback: String,
): String {
    val selectedTrack = tracks.firstOrNull { it.kind == kind && it.selected }
    return if (selectedTrack == null) {
        "$fallback: off"
    } else {
        "$fallback: ${selectedTrack.label}"
    }
}

internal fun PlaybackSnapshot.nextTrackId(kind: PlaybackTrackKind): String? {
    val tracksOfKind = tracks.filter { it.kind == kind }
    if (tracksOfKind.isEmpty()) {
        return null
    }
    val selectedIndex = tracksOfKind.indexOfFirst(PlaybackTrack::selected)
    return tracksOfKind[(selectedIndex + 1).floorMod(tracksOfKind.size)].id
}

internal fun PlaybackSnapshot.nextSubtitleTrackId(): String? {
    val subtitleTracks = tracks.filter { it.kind == PlaybackTrackKind.SUBTITLE }
    if (subtitleTracks.isEmpty()) {
        return null
    }
    val selectedIndex = subtitleTracks.indexOfFirst(PlaybackTrack::selected)
    return when {
        selectedIndex == -1 -> subtitleTracks.first().id
        selectedIndex == subtitleTracks.lastIndex -> null
        else -> subtitleTracks[selectedIndex + 1].id
    }
}

internal fun DesktopVideoAspectMode.nextAspectMode(): DesktopVideoAspectMode {
    val entries = DesktopVideoAspectMode.entries
    return entries[(entries.indexOf(this) + 1).floorMod(entries.size)]
}

private fun Int.floorMod(size: Int): Int =
    ((this % size) + size) % size
