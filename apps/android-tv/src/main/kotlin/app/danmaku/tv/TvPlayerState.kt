package app.danmaku.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.library.LanLibraryConnectionProfile

internal class TvPlayerState(
    initialSavedConnections: List<LanLibraryConnectionProfile>,
    initialFavoriteMediaIds: Set<String>,
    initialServerUrl: String = initialSavedConnections.firstOrNull()?.baseUrl.orEmpty(),
    initialPairingToken: String = initialSavedConnections.firstOrNull()?.pairingToken.orEmpty(),
) {
    var controller by mutableStateOf<TvPlaybackController?>(null)
    var snapshot by mutableStateOf(PlaybackSnapshot())
    var playbackError by mutableStateOf<String?>(null)
    var serverUrl by mutableStateOf(initialServerUrl)
    var pairingToken by mutableStateOf(initialPairingToken)
    var savedConnections by mutableStateOf(initialSavedConnections)
    var favoriteMediaIds by mutableStateOf(initialFavoriteMediaIds)
    var catalog by mutableStateOf<LibraryCatalog?>(null)
    var playbackProgresses by mutableStateOf<List<PlaybackProgress>>(emptyList())
    var libraryError by mutableStateOf<String?>(null)
    var selectedDestination by mutableStateOf(TvDestination.Pc)

    val posterEndpoint: LibraryPosterEndpoint?
        get() = catalog?.let {
            LibraryPosterEndpoint(serverUrl, pairingToken)
        }
}
