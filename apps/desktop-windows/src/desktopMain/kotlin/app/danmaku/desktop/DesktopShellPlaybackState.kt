package app.danmaku.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.danmaku.domain.PlaybackProgress
import app.danmaku.library.LanPlaybackTarget

internal class DesktopShellPlaybackState(
    autoNextEnabled: Boolean,
) {
    var pendingPlaybackRequest by mutableStateOf<DesktopPlaybackRequest?>(null)
    var activePlaybackLabel by mutableStateOf<String?>(null)
    var activeProgressMediaId by mutableStateOf<String?>(null)
    var activeProgressTarget by mutableStateOf<LanPlaybackTarget?>(null)
    var lastSavedPlaybackProgress by mutableStateOf<PlaybackProgress?>(null)
    var lastAutoNextMediaId by mutableStateOf<String?>(null)
    var smokePlaybackQueued by mutableStateOf(false)
    var smokePlaybackExitStarted by mutableStateOf(false)
    var sidecarQaAutoplayQueued by mutableStateOf(false)
    var autoNextLocalPlayback by mutableStateOf(autoNextEnabled)

    fun markPlaybackLoaded(request: DesktopPlaybackRequest) {
        activePlaybackLabel = request.label
        activeProgressMediaId = request.progressMediaId
        activeProgressTarget = request.progressTarget
        lastSavedPlaybackProgress = null
        lastAutoNextMediaId = null
    }
}

@Composable
internal fun rememberDesktopShellPlaybackState(
    catalogStore: DesktopLibraryCatalogStore,
): DesktopShellPlaybackState =
    remember(catalogStore) {
        DesktopShellPlaybackState(
            autoNextEnabled = catalogStore.loadSetting(LOCAL_AUTO_NEXT_SETTING_KEY)?.value == "true",
        )
    }
