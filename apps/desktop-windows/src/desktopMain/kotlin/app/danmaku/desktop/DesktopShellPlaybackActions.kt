package app.danmaku.desktop

import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackController
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.toPlaybackProgress
import app.danmaku.library.LanPlaybackProgressSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files

internal class DesktopShellPlaybackActions(
    private val scope: CoroutineScope,
    private val catalogStore: DesktopLibraryCatalogStore,
    private val playbackPreferencesStore: DesktopPlaybackPreferencesStore,
    private val lanProgressSync: LanPlaybackProgressSync,
    private val settingsState: DesktopShellSettingsState,
    private val libraryState: DesktopShellLibraryState,
    private val playbackState: DesktopShellPlaybackState,
    private val playbackSession: DesktopPlaybackSession,
    private val playbackController: PlaybackController,
    private val hostDisplayName: String,
    private val requiresNativeVideoHost: Boolean,
    private val getPlaybackSnapshot: () -> PlaybackSnapshot,
    private val setPlaybackSnapshot: (PlaybackSnapshot) -> Unit,
    private val updateVideoAspectMode: (DesktopVideoAspectMode) -> Unit,
    private val selectPlaybackTab: () -> Unit,
    private val appendDiagnostic: (String, String) -> Unit,
) {
    fun persistActivePlaybackProgress(
        snapshot: PlaybackSnapshot,
        force: Boolean = false,
    ) {
        val mediaId = playbackState.activeProgressMediaId ?: return
        val progress = snapshot.toPlaybackProgress(mediaId, System.currentTimeMillis()) ?: return
        if (!shouldPersistPlaybackProgress(progress, force)) {
            return
        }
        playbackState.lastSavedPlaybackProgress = progress
        val target = playbackState.activeProgressTarget
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (target == null) {
                        catalogStore.saveProgress(progress)
                        catalogStore.loadPlaybackProgress()
                    } else {
                        lanProgressSync.saveProgress(target, snapshot)
                        null
                    }
                }
            }.onSuccess { updatedProgresses ->
                updatedProgresses?.let {
                    libraryState.playbackProgresses = it
                }
                val destination = if (target == null) "local catalog" else "paired LAN server"
                appendDiagnostic(
                    "progress",
                    "Saved $destination progress for $mediaId at ${progress.positionMs}ms/${progress.durationMs ?: "unknown"}",
                )
            }.onFailure { error ->
                appendDiagnostic("progress", "Save progress failed for $mediaId: ${error.message}")
            }
        }
    }

    fun loadPlaybackRequest(request: DesktopPlaybackRequest): PlaybackSnapshot {
        playbackState.markPlaybackLoaded(request)
        return playbackSession.load(request).also(setPlaybackSnapshot)
    }

    fun queuePlaybackUntilHostReady(request: DesktopPlaybackRequest) {
        playbackState.pendingPlaybackRequest = request
        appendDiagnostic(
            "playback",
            if (requiresNativeVideoHost) {
                "Queued playback until native video host attaches: ${request.label}; source=${request.source.toString().redactToken()}"
            } else {
                "Queued playback for $hostDisplayName mpv output: ${request.label}; source=${request.source.toString().redactToken()}"
            },
        )
        selectPlaybackTab()
    }

    fun queueSmokePlayback(options: DesktopSmokePlaybackOptions) {
        val mediaPath = options.mediaPath.toAbsolutePath().normalize()
        if (!Files.isRegularFile(mediaPath)) {
            appendDiagnostic("smoke", "Smoke playback media does not exist: $mediaPath")
            return
        }
        appendDiagnostic(
            "smoke",
            "Queueing smoke playback: media=$mediaPath; duration=${options.playbackDuration.inWholeSeconds}s; " +
                "autoExit=${options.autoExit}",
        )
        queuePlaybackUntilHostReady(
            mediaPath.toDirectLocalPlaybackRequest().copy(
                label = "Smoke playback - ${mediaPath.fileName ?: mediaPath}",
            ),
        )
    }

    fun setAutoNextLocalPlayback(enabled: Boolean) {
        playbackState.autoNextLocalPlayback = enabled
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    catalogStore.saveSetting(
                        DesktopAppSetting(
                            key = LOCAL_AUTO_NEXT_SETTING_KEY,
                            value = enabled.toString(),
                            updatedAtEpochMs = System.currentTimeMillis(),
                        ),
                    )
                }
            }.onSuccess {
                appendDiagnostic("settings", "Local auto-next ${if (enabled) "enabled" else "disabled"}")
            }.onFailure {
                appendDiagnostic("settings", "Failed to save local auto-next setting: ${it.message}")
            }
        }
    }

    fun savePlaybackPreference(
        label: String,
        save: DesktopPlaybackPreferencesStore.() -> Unit,
    ) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    playbackPreferencesStore.save()
                    playbackPreferencesStore.load()
                }
            }.onSuccess { updatedPreferences ->
                settingsState.updatePlaybackPreferences(updatedPreferences)
                appendDiagnostic("settings", "Saved $label playback preference")
            }.onFailure {
                appendDiagnostic("settings", "Failed to save $label playback preference: ${it.message}")
            }
        }
    }

    fun pauseActivePlaybackAndPersist() {
        appendDiagnostic("playback", "Primary shortcut paused playback")
        playbackController.dispatch(PlaybackCommand.Pause)
        val snapshot = updatePlaybackSnapshot()
        persistActivePlaybackProgress(snapshot, force = true)
    }

    fun playActivePlayback() {
        appendDiagnostic("playback", "Primary shortcut started playback")
        playbackController.dispatch(PlaybackCommand.Play)
        updatePlaybackSnapshot()
    }

    fun dispatchPlay() {
        appendDiagnostic("playback", "Dispatch Play")
        playbackController.dispatch(PlaybackCommand.Play)
        updatePlaybackSnapshot()
    }

    fun dispatchPause() {
        appendDiagnostic("playback", "Dispatch Pause")
        playbackController.dispatch(PlaybackCommand.Pause)
        val snapshot = updatePlaybackSnapshot()
        persistActivePlaybackProgress(snapshot, force = true)
    }

    fun seekBy(offsetMs: Long, diagnosticLabel: String) {
        appendDiagnostic("playback", diagnosticLabel)
        val currentPositionMs = getPlaybackSnapshot().position.positionMs
        seekToPosition(maxOf(0, currentPositionMs + offsetMs))
    }

    fun seekTo(positionMs: Long) {
        appendDiagnostic("playback", "Dispatch Seek ${positionMs}ms")
        seekToPosition(positionMs)
    }

    fun setPlaybackRate(rate: Float) {
        appendDiagnostic("playback", "Dispatch playback rate ${rate}x")
        playbackController.dispatch(PlaybackCommand.SetPlaybackRate(rate))
        updatePlaybackSnapshot()
        savePlaybackPreference("rate") {
            savePlaybackRate(rate)
        }
    }

    fun setVolume(volumePercent: Int) {
        appendDiagnostic("playback", "Dispatch volume $volumePercent%")
        playbackController.dispatch(PlaybackCommand.SetVolume(volumePercent))
        updatePlaybackSnapshot()
        savePlaybackPreference("volume") {
            saveVolumePercent(volumePercent)
        }
    }

    fun selectAudioTrack(trackId: String) {
        appendDiagnostic("playback", "Dispatch audio track $trackId")
        playbackController.dispatch(PlaybackCommand.SelectAudioTrack(trackId))
        updatePlaybackSnapshot()
    }

    fun selectSubtitleTrack(trackId: String?) {
        appendDiagnostic("playback", "Dispatch subtitle track ${trackId ?: "off"}")
        playbackController.dispatch(PlaybackCommand.SelectSubtitleTrack(trackId))
        updatePlaybackSnapshot()
    }

    fun setVideoAspectMode(mode: DesktopVideoAspectMode) {
        appendDiagnostic("playback", "Dispatch video aspect ${mode.label}")
        if (playbackController is DesktopMpvPlaybackController) {
            playbackController.setVideoAspectMode(mode)
            updateVideoAspectMode(playbackController.videoAspectMode)
        }
        updatePlaybackSnapshot()
        savePlaybackPreference("aspect") {
            saveVideoAspectMode(mode)
        }
    }

    private fun shouldPersistPlaybackProgress(
        progress: PlaybackProgress,
        force: Boolean,
    ): Boolean {
        if (progress.positionMs <= 0) {
            return false
        }
        val lastSaved = playbackState.lastSavedPlaybackProgress
        return force ||
            lastSaved == null ||
            lastSaved.mediaId != progress.mediaId ||
            progress.positionMs - lastSaved.positionMs >= WINDOWS_PROGRESS_SAVE_INTERVAL_MS ||
            lastSaved.positionMs - progress.positionMs >= WINDOWS_PROGRESS_SAVE_INTERVAL_MS ||
            progress.durationMs != lastSaved.durationMs
    }

    private fun seekToPosition(positionMs: Long) {
        playbackController.dispatch(PlaybackCommand.SeekTo(positionMs))
        val snapshot = updatePlaybackSnapshot()
        persistActivePlaybackProgress(snapshot, force = true)
    }

    private fun updatePlaybackSnapshot(): PlaybackSnapshot =
        playbackController.snapshot().also(setPlaybackSnapshot)
}
