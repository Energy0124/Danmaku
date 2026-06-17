package app.danmaku.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.player.android.Media3PlaybackController

internal class TvPlayerState(
    initialSavedConnections: List<LanLibraryConnectionProfile>,
    initialFavoriteMediaIds: Set<String>,
) {
    var controller by mutableStateOf<Media3PlaybackController?>(null)
    var snapshot by mutableStateOf(PlaybackSnapshot())
    var playbackError by mutableStateOf<String?>(null)
    var serverUrl by mutableStateOf("http://10.0.2.2:8686")
    var pairingToken by mutableStateOf("")
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
