package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Surface
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.PlaybackCommand

@Composable
internal fun TvPlayerContent(
    state: TvPlayerState,
    actionHandler: TvPlayerActionHandler,
    refreshPcFocusRequester: FocusRequester,
    discoverPcFocusRequester: FocusRequester,
) {
    if (state.isFullscreenPlayback) {
        TvFullscreenPlaybackScreen(
            state = state,
            actionHandler = actionHandler,
        )
        return
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(TvAppBackground)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TvAppNavigationRail(
                selectedDestination = state.selectedDestination,
                catalog = state.catalog,
                favoriteCount = state.favoriteMediaIds.size,
                onSelectDestination = { state.selectedDestination = it },
            )
            if (state.selectedDestination.isLibraryDestination()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TvDestinationHeader(
                        selectedDestination = state.selectedDestination,
                        catalog = state.catalog,
                        snapshot = state.snapshot,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        TvDestinationContent(
                            state = state,
                            actionHandler = actionHandler,
                            refreshPcFocusRequester = refreshPcFocusRequester,
                            discoverPcFocusRequester = discoverPcFocusRequester,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        TvDestinationHeader(
                            selectedDestination = state.selectedDestination,
                            catalog = state.catalog,
                            snapshot = state.snapshot,
                        )
                    }
                    if (state.selectedDestination == TvDestination.Home && state.snapshot.source != null) {
                        item { TvPlaybackChrome(state) }
                    }
                    item {
                        TvDestinationContent(
                            state = state,
                            actionHandler = actionHandler,
                            refreshPcFocusRequester = refreshPcFocusRequester,
                            discoverPcFocusRequester = discoverPcFocusRequester,
                        )
                    }
                }
            }
        }
    }
}

private fun TvDestination.isLibraryDestination(): Boolean =
    this == TvDestination.Library || this == TvDestination.Search || this == TvDestination.Favorites

@Composable
private fun TvPlaybackChrome(state: TvPlayerState) {
    TvPlayerPanel(
        controller = state.controller,
        snapshot = state.snapshot,
        playbackError = state.playbackError,
        onSeekTo = { state.controller?.dispatch(PlaybackCommand.SeekTo(it)) },
        onSelectAudio = {
            state.controller?.dispatch(PlaybackCommand.SelectAudioTrack(it))
        },
        onSelectSubtitle = {
            state.controller?.dispatch(PlaybackCommand.SelectSubtitleTrack(it))
        },
        onPlay = { state.controller?.dispatch(PlaybackCommand.Play) },
        onPause = { state.controller?.dispatch(PlaybackCommand.Pause) },
        onVolumeDown = {
            state.controller?.dispatch(
                PlaybackCommand.SetVolume((state.snapshot.volumePercent - 10).coerceAtLeast(0)),
            )
        },
        onVolumeUp = {
            state.controller?.dispatch(
                PlaybackCommand.SetVolume((state.snapshot.volumePercent + 10).coerceAtMost(100)),
            )
        },
    )
}

@Composable
private fun TvDestinationContent(
    state: TvPlayerState,
    actionHandler: TvPlayerActionHandler,
    refreshPcFocusRequester: FocusRequester,
    discoverPcFocusRequester: FocusRequester,
) {
    when (state.selectedDestination) {
        TvDestination.Pc ->
            TvPcConnectionPanel(
                serverUrl = state.serverUrl,
                onServerUrlChange = { state.serverUrl = it },
                savedConnections = state.savedConnections,
                selectedBaseUrl = state.serverUrl.trim().trimEnd('/'),
                libraryError = state.libraryError,
                refreshPcFocusRequester = refreshPcFocusRequester,
                discoverPcFocusRequester = discoverPcFocusRequester,
                onRefresh = actionHandler::refreshLibrary,
                onDiscover = actionHandler::discoverPc,
                onSave = actionHandler::saveConnection,
                onSelectConnection = actionHandler::selectConnection,
                onForgetConnection = actionHandler::forgetConnection,
            )
        TvDestination.Home ->
            TvHomePanel(
                catalog = state.catalog,
                playbackProgresses = state.playbackProgresses,
                posterEndpoint = state.posterEndpoint,
                onShowLibrary = { state.selectedDestination = TvDestination.Library },
                onShowPc = { state.selectedDestination = TvDestination.Pc },
                onPlay = actionHandler::playItem,
            )
        TvDestination.Library,
        TvDestination.Search,
        TvDestination.Favorites,
        ->
            LibraryItems(
                catalog = state.catalog,
                posterEndpoint = state.posterEndpoint,
                playbackProgresses = state.playbackProgresses,
                favoriteMediaIds = state.favoriteMediaIds,
                initialFavoriteFilter = if (state.selectedDestination == TvDestination.Favorites) {
                    LibraryFavoriteFilter.FAVORITES_ONLY
                } else {
                    LibraryFavoriteFilter.ANY
                },
                focusSearchOnStart = state.selectedDestination == TvDestination.Search,
                onSetFavorite = actionHandler::setFavorite,
                onPlay = actionHandler::playItem,
            )
    }
}
