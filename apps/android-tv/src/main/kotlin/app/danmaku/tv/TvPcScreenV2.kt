package app.danmaku.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.library.android.ExternalTrackingPlanUpdate
import app.danmaku.library.android.ProviderAccountState
import app.danmaku.domain.ExternalAnimeListStatus
import app.danmaku.domain.ExternalAnimeProvider

@Composable
internal fun TvPcScreen(
    navigation: TvNavigationState,
    navigator: TvNavigator,
    session: TvSessionUiState,
    onServerUrlChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onDiscover: () -> Unit,
    onSave: () -> Unit,
    onSelectConnection: (LanLibraryConnectionProfile) -> Unit,
    onForgetConnection: (LanLibraryConnectionProfile) -> Unit,
    onLoadTracking: () -> Unit = {},
    onReadTracking: () -> Unit = {},
    onSyncTracking: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen-pc"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            TvScreenHeader(
                title = stringResource(R.string.pc_connection_title),
                subtitle = stringResource(R.string.pc_connection_body),
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onDiscover,
                    modifier = Modifier
                        .tvRouteFocus(
                            navigation,
                            navigator,
                            TvRoute.Pc,
                            "pc-discover",
                            isDefault = true,
                        )
                        .tvFocusHalo(RoundedCornerShape(18.dp))
                        .testTag("pc-discover"),
                    colors = tvButtonColors(selected = true),
                    scale = tvButtonScale(),
                ) {
                    Text(stringResource(R.string.action_discover_pc))
                }
                Button(
                    onClick = onRefresh,
                    enabled = session.serverUrl.isNotBlank() && !session.isRefreshing,
                    modifier = Modifier
                        .tvRouteFocus(navigation, navigator, TvRoute.Pc, "pc-refresh")
                        .tvFocusHalo(RoundedCornerShape(18.dp))
                        .testTag("pc-refresh"),
                    colors = tvButtonColors(),
                    scale = tvButtonScale(),
                ) {
                    Text(
                        if (session.isRefreshing) {
                            stringResource(R.string.status_connecting)
                        } else {
                            stringResource(R.string.action_refresh_pc_library)
                        },
                    )
                }
                Button(
                    onClick = onSave,
                    enabled = session.serverUrl.isNotBlank(),
                    modifier = Modifier
                        .tvRouteFocus(navigation, navigator, TvRoute.Pc, "pc-save")
                        .tvFocusHalo(RoundedCornerShape(18.dp))
                        .testTag("pc-save"),
                    colors = tvButtonColors(),
                    scale = tvButtonScale(),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TvTextInput(
                    value = session.serverUrl,
                    onValueChange = onServerUrlChange,
                    placeholder = stringResource(R.string.pc_server_url_placeholder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvRouteFocus(navigation, navigator, TvRoute.Pc, "pc-url")
                        .testTag("pc-url"),
                )
            }
        }
        session.errorMessage?.let { error ->
            item {
                Text(
                    text = stringResource(R.string.library_error_prefix, error),
                    color = TvError,
                )
            }
        }
        if (session.savedConnections.isNotEmpty()) {
            item {
                Text(stringResource(R.string.saved_pcs_title))
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(session.savedConnections, key = { it.id }) { connection ->
                        TvSavedConnectionCard(
                            connection = connection,
                            isSelected = connection.normalizedBaseUrl ==
                                session.serverUrl.trim().trimEnd('/'),
                            onSelect = { onSelectConnection(connection) },
                            onForget = { onForgetConnection(connection) },
                        )
                    }
                }
            }
        }
        item {
            TvTrackingCard(
                navigation = navigation,
                navigator = navigator,
                session = session,
                onLoad = onLoadTracking,
                onReadback = onReadTracking,
                onSync = onSyncTracking,
            )
        }
    }
}

@Composable
private fun TvTrackingCard(
    navigation: TvNavigationState,
    navigator: TvNavigator,
    session: TvSessionUiState,
    onLoad: () -> Unit,
    onReadback: () -> Unit,
    onSync: () -> Unit,
) {
    val state = session.tracking
    val updates = state.document?.plan?.updates.orEmpty()
    var confirmUpdates by remember(
        state.document?.generatedAtEpochMs,
        state.hasFreshReadback,
    ) { mutableStateOf<List<ExternalTrackingPlanUpdate>?>(null) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        colors = SurfaceDefaults.colors(containerColor = TvSurfaceRaised),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp).testTag("tracking-card"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.tracking_status_title))
            Text(stringResource(R.string.tracking_managed_on_windows), color = TvSecondaryContent)
            if (session.serverUrl.isBlank()) {
                Text(stringResource(R.string.tracking_connect_first), color = TvSecondaryContent)
            }
            state.accounts?.let { accounts ->
                Text("MyAnimeList · ${tvAccountLabel(accounts.myAnimeList.state, accounts.myAnimeList.displayName)}")
                Text("Bangumi · ${tvAccountLabel(accounts.bangumi.state, accounts.bangumi.displayName)}")
            }
            state.document?.plan?.summary?.let { summary ->
                Text(
                    stringResource(
                        R.string.tracking_summary,
                        summary.updateCount,
                        summary.conflictCount,
                        summary.skippedCount,
                        summary.failureCount,
                    ),
                )
            }
            updates.forEach { update ->
                Text(
                    stringResource(
                        R.string.tracking_update_row,
                        update.seriesTitle,
                        tvProviderLabel(update.mapping.animeId.provider),
                        tvStatusLabel(update.update.status),
                        update.update.watchedEpisodes ?: 0,
                        update.episodeCount,
                    ),
                    color = TvSecondaryContent,
                )
            }
            if ((state.document?.plan?.conflicts?.size ?: 0) +
                (state.document?.plan?.mappingConflicts?.size ?: 0) > 0
            ) {
                Text(stringResource(R.string.tracking_conflicts_windows), color = TvError)
            }
            state.lastResponse?.let { response ->
                Text(
                    stringResource(
                        R.string.tracking_operation_result,
                        response.successCount,
                        response.missingCount,
                        response.errors.size,
                    ),
                    color = if (response.errors.isEmpty()) TvSuccess else TvError,
                )
            }
            state.error?.let { Text(tvTrackingErrorLabel(it, state.errorDetail), color = TvError) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onLoad,
                    enabled = !state.isBusy && session.serverUrl.isNotBlank(),
                    modifier = Modifier.tvRouteFocus(navigation, navigator, TvRoute.Pc, "tracking-refresh"),
                ) { Text(stringResource(R.string.action_refresh)) }
                Button(
                    onClick = onReadback,
                    enabled = !state.isBusy && state.document?.mappings?.isNotEmpty() == true,
                    modifier = Modifier
                        .tvRouteFocus(navigation, navigator, TvRoute.Pc, "tracking-readback")
                        .testTag("tracking-readback"),
                ) { Text(stringResource(R.string.tracking_check_provider)) }
                Button(
                    onClick = { confirmUpdates = updates.toList() },
                    enabled = !state.isBusy && state.hasFreshReadback && updates.isNotEmpty(),
                    modifier = Modifier
                        .tvRouteFocus(navigation, navigator, TvRoute.Pc, "tracking-sync")
                        .testTag("tracking-sync"),
                ) { Text(stringResource(R.string.tracking_sync_updates, updates.size)) }
            }
        }
    }
    confirmUpdates?.let { previewUpdates ->
        val cancelFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { cancelFocusRequester.requestFocus() }
        Dialog(
            onDismissRequest = { confirmUpdates = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                colors = SurfaceDefaults.colors(containerColor = TvSurfaceRaised),
            ) {
                Column(
                    modifier = Modifier.padding(28.dp).testTag("tracking-confirm-dialog"),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Text(stringResource(R.string.tracking_confirm_title))
                    Text(stringResource(R.string.tracking_confirm_body, previewUpdates.size))
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(previewUpdates.size) { index ->
                            val update = previewUpdates[index]
                            Text(
                                stringResource(
                                    R.string.tracking_update_row,
                                    update.seriesTitle,
                                    tvProviderLabel(update.mapping.animeId.provider),
                                    tvStatusLabel(update.update.status),
                                    update.update.watchedEpisodes ?: 0,
                                    update.episodeCount,
                                ),
                                color = TvSecondaryContent,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { confirmUpdates = null },
                            modifier = Modifier
                                .focusRequester(cancelFocusRequester)
                                .testTag("tracking-cancel-sync"),
                        ) { Text(stringResource(R.string.action_cancel)) }
                        Button(
                            onClick = {
                                if (state.hasFreshReadback && updates == previewUpdates) onSync()
                                confirmUpdates = null
                            },
                            enabled = state.hasFreshReadback && updates == previewUpdates,
                            modifier = Modifier.testTag("tracking-confirm-sync"),
                        ) { Text(stringResource(R.string.tracking_confirm_action)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun tvAccountLabel(state: ProviderAccountState, displayName: String?): String = when (state) {
    ProviderAccountState.CONNECTED -> stringResource(R.string.tracking_connected_as, displayName ?: stringResource(R.string.tracking_account))
    ProviderAccountState.DISCONNECTED -> stringResource(R.string.tracking_not_connected)
    ProviderAccountState.NEEDS_RECONNECT -> stringResource(R.string.tracking_reconnect_windows)
    ProviderAccountState.UNAVAILABLE -> stringResource(R.string.tracking_unavailable)
}

@Composable
private fun tvProviderLabel(provider: ExternalAnimeProvider): String = when (provider) {
    ExternalAnimeProvider.MY_ANIME_LIST -> stringResource(R.string.tracking_provider_mal)
    ExternalAnimeProvider.BANGUMI -> stringResource(R.string.tracking_provider_bangumi)
    ExternalAnimeProvider.DANDANPLAY -> stringResource(R.string.tracking_provider_dandanplay)
}

@Composable
private fun tvStatusLabel(status: ExternalAnimeListStatus?): String = when (status) {
    ExternalAnimeListStatus.WATCHING -> stringResource(R.string.tracking_status_watching)
    ExternalAnimeListStatus.COMPLETED -> stringResource(R.string.tracking_status_completed)
    ExternalAnimeListStatus.ON_HOLD -> stringResource(R.string.tracking_status_on_hold)
    ExternalAnimeListStatus.DROPPED -> stringResource(R.string.tracking_status_dropped)
    ExternalAnimeListStatus.PLAN_TO_WATCH -> stringResource(R.string.tracking_status_plan_to_watch)
    null -> stringResource(R.string.tracking_status_unchanged)
}

@Composable
private fun tvTrackingErrorLabel(error: TvTrackingError, detail: String?): String = when (error) {
    TvTrackingError.PREVIEW_CHANGED -> stringResource(R.string.tracking_error_preview_changed)
    TvTrackingError.REQUEST_FAILED -> detail ?: stringResource(R.string.tracking_error_request_failed)
}
