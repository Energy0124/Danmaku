package app.danmaku.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.library.android.ExternalTrackingPlanUpdate
import app.danmaku.library.android.ProviderAccountState
import app.danmaku.domain.ExternalAnimeListStatus
import app.danmaku.domain.ExternalAnimeProvider

@Composable
internal fun ConnectPage(
    contentPadding: PaddingValues,
    catalog: LibraryCatalog?,
    snapshot: PlaybackSnapshot,
    nowPlaying: LibraryMediaItem?,
    serverUrl: String,
    pairingToken: String,
    savedConnections: List<LanLibraryConnectionProfile>,
    libraryError: String?,
    tracking: MobileTrackingState,
    onServerUrlChange: (String) -> Unit,
    onPairingTokenChange: (String) -> Unit,
    onSelectConnection: (LanLibraryConnectionProfile) -> Unit,
    onEditConnection: (LanLibraryConnectionProfile) -> Unit,
    onForgetConnection: (LanLibraryConnectionProfile) -> Unit,
    onSaveConnection: () -> Unit,
    onDiscover: () -> Unit,
    onRefresh: () -> Unit,
    onLoadTracking: () -> Unit,
    onReadTracking: () -> Unit,
    onSyncTracking: () -> Unit,
    onPlayPause: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    PageColumn(contentPadding) {
        item(key = "connect-page-header") {
            PageHeader(
                icon = Icons.Filled.Settings,
                title = stringResource(R.string.nav_connect),
                subtitle = if (catalog == null) {
                    stringResource(R.string.connect_page_pair_subtitle)
                } else {
                    stringResource(R.string.connect_page_connected_to, catalog.rootName)
                },
            )
        }
        if (snapshot.source != null) {
            item(key = "mini-player") {
                MiniPlayerBar(
                    snapshot = snapshot,
                    nowPlaying = nowPlaying,
                    onPlayPause = onPlayPause,
                    onOpenPlayer = onOpenPlayer,
                )
            }
        }
        item(key = "connect") {
            ConnectionPanel(
                catalog = catalog,
                serverUrl = serverUrl,
                pairingToken = pairingToken,
                savedConnections = savedConnections,
                libraryError = libraryError,
                onServerUrlChange = onServerUrlChange,
                onPairingTokenChange = onPairingTokenChange,
                onSelectConnection = onSelectConnection,
                onEditConnection = onEditConnection,
                onForgetConnection = onForgetConnection,
                onSaveConnection = onSaveConnection,
                onDiscover = onDiscover,
                onRefresh = onRefresh,
            )
        }
        item(key = "tracking") {
            MobileTrackingCard(
                state = tracking,
                hasConnection = catalog != null,
                hasAccessCode = pairingToken.isNotBlank(),
                onLoad = onLoadTracking,
                onReadback = onReadTracking,
                onSync = onSyncTracking,
            )
        }
        item(key = "connect-help") {
            EmptyPanel(
                title = stringResource(R.string.connect_help_title),
                body = stringResource(R.string.connect_help_body),
            )
        }
    }
}

@Composable
internal fun ConnectionPanel(
    catalog: LibraryCatalog?,
    serverUrl: String,
    pairingToken: String = "",
    savedConnections: List<LanLibraryConnectionProfile>,
    libraryError: String?,
    onServerUrlChange: (String) -> Unit,
    onPairingTokenChange: (String) -> Unit = {},
    onSelectConnection: (LanLibraryConnectionProfile) -> Unit,
    onEditConnection: (LanLibraryConnectionProfile) -> Unit,
    onForgetConnection: (LanLibraryConnectionProfile) -> Unit,
    onSaveConnection: () -> Unit,
    onDiscover: () -> Unit,
    onRefresh: () -> Unit,
) {
    var showManualFields by remember(savedConnections.isEmpty()) {
        mutableStateOf(savedConnections.isEmpty())
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PanelAltColor,
        border = BorderStroke(1.dp, Color(0xFF343D45)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.connect_windows_pc),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        catalog?.rootName ?: serverUrl.serverDisplayName(),
                        color = SubtleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusPill(
                    if (catalog == null) {
                        stringResource(R.string.status_offline)
                    } else {
                        stringResource(R.string.status_ready)
                    },
                )
            }

            if (savedConnections.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.connect_saved_pcs_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.connect_saved_pcs_body),
                        color = SubtleText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    savedConnections.forEach { connection ->
                        SavedConnectionRow(
                            connection = connection,
                            isSelected = connection.normalizedBaseUrl == serverUrl.trim().trimEnd('/'),
                            onSelect = { onSelectConnection(connection) },
                            onEdit = {
                                onEditConnection(connection)
                                showManualFields = true
                            },
                            onForget = { onForgetConnection(connection) },
                        )
                    }
                }
            }

            if (showManualFields) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = onServerUrlChange,
                    label = { Text(stringResource(R.string.connect_server_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = pairingToken,
                    onValueChange = onPairingTokenChange,
                    label = { Text(stringResource(R.string.connect_pairing_code_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pairing-token-field"),
                )

            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = onDiscover) {
                    Text(stringResource(R.string.action_discover_pc))
                }
                if (showManualFields) {
                    OutlinedButton(onClick = onRefresh) {
                        Text(stringResource(R.string.action_connect_current))
                    }
                    OutlinedButton(onClick = onSaveConnection) {
                        Text(stringResource(R.string.action_save_current))
                    }
                } else {
                    OutlinedButton(onClick = { showManualFields = true }) {
                        Text(stringResource(R.string.action_manual_setup))
                    }
                }
            }

            libraryError?.let {
                ErrorText(stringResource(R.string.library_error_prefix, it))
            }
        }
    }
}

@Composable
private fun MobileTrackingCard(
    state: MobileTrackingState,
    hasConnection: Boolean,
    hasAccessCode: Boolean,
    onLoad: () -> Unit,
    onReadback: () -> Unit,
    onSync: () -> Unit,
) {
    var confirmUpdates by remember(
        state.document?.generatedAtEpochMs,
        state.hasFreshReadback,
    ) { mutableStateOf<List<ExternalTrackingPlanUpdate>?>(null) }
    val document = state.document
    val updates = document?.plan?.updates.orEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("tracking-card"),
        shape = RoundedCornerShape(20.dp),
        color = PanelAltColor,
        border = BorderStroke(1.dp, Color(0xFF343D45)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.tracking_status_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(stringResource(R.string.tracking_managed_on_windows), color = SubtleText)
                }
                if (state.isBusy) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
            if (!hasConnection || !hasAccessCode) {
                Text(stringResource(R.string.tracking_connect_first), color = SubtleText)
            } else {
                state.accounts?.let { accounts ->
                    Text("MyAnimeList · ${accountLabel(accounts.myAnimeList.state, accounts.myAnimeList.displayName)}")
                    Text("Bangumi · ${accountLabel(accounts.bangumi.state, accounts.bangumi.displayName)}")
                }
                document?.plan?.summary?.let { summary ->
                    Text(
                        stringResource(
                            R.string.tracking_summary,
                            summary.updateCount,
                            summary.conflictCount,
                            summary.skippedCount,
                            summary.failureCount,
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                updates.forEach { TrackingUpdateRow(it) }
                if ((document?.plan?.conflicts?.size ?: 0) +
                    (document?.plan?.mappingConflicts?.size ?: 0) > 0
                ) {
                    Text(stringResource(R.string.tracking_conflicts_windows), color = AccentAmber)
                }
                state.lastResponse?.let { response ->
                    Text(
                        stringResource(
                            R.string.tracking_operation_result,
                            response.successCount,
                            response.missingCount,
                            response.errors.size,
                        ),
                        color = if (response.errors.isEmpty()) AccentBlue else AccentAmber,
                    )
                }
                state.error?.let { ErrorText(trackingErrorLabel(it, state.errorDetail)) }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(enabled = !state.isBusy, onClick = onLoad) {
                        Text(stringResource(R.string.action_refresh))
                    }
                    Button(
                        enabled = !state.isBusy && document?.mappings?.isNotEmpty() == true,
                        onClick = onReadback,
                        modifier = Modifier.testTag("tracking-readback"),
                    ) {
                        Text(stringResource(R.string.tracking_check_provider))
                    }
                    Button(
                        enabled = !state.isBusy && state.hasFreshReadback && updates.isNotEmpty(),
                        onClick = { confirmUpdates = updates.toList() },
                        modifier = Modifier.testTag("tracking-sync"),
                    ) {
                        Text(stringResource(R.string.tracking_sync_updates, updates.size))
                    }
                }
            }
        }
    }
    confirmUpdates?.let { previewUpdates ->
        AlertDialog(
            onDismissRequest = { confirmUpdates = null },
            title = { Text(stringResource(R.string.tracking_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.tracking_confirm_body, previewUpdates.size))
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(previewUpdates.size) { index -> TrackingUpdateRow(previewUpdates[index]) }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUpdates = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (state.hasFreshReadback && updates == previewUpdates) onSync()
                        confirmUpdates = null
                    },
                    enabled = state.hasFreshReadback && updates == previewUpdates,
                    modifier = Modifier.testTag("tracking-confirm-sync"),
                ) {
                    Text(stringResource(R.string.tracking_confirm_action))
                }
            },
        )
    }
}

@Composable
private fun accountLabel(state: ProviderAccountState, displayName: String?): String = when (state) {
    ProviderAccountState.CONNECTED -> stringResource(
        R.string.tracking_connected_as,
        displayName ?: stringResource(R.string.tracking_account),
    )
    ProviderAccountState.DISCONNECTED -> stringResource(R.string.tracking_not_connected)
    ProviderAccountState.NEEDS_RECONNECT -> stringResource(R.string.tracking_reconnect_windows)
    ProviderAccountState.UNAVAILABLE -> stringResource(R.string.tracking_unavailable)
}

@Composable
private fun TrackingUpdateRow(candidate: ExternalTrackingPlanUpdate) {
    Surface(color = PanelColor, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(candidate.seriesTitle, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(
                    R.string.tracking_update_detail,
                    providerLabel(candidate.mapping.animeId.provider),
                    statusLabel(candidate.update.status),
                    candidate.update.watchedEpisodes ?: 0,
                    candidate.episodeCount,
                ),
                color = SubtleText,
            )
        }
    }
}

@Composable
private fun providerLabel(provider: ExternalAnimeProvider): String = when (provider) {
    ExternalAnimeProvider.MY_ANIME_LIST -> stringResource(R.string.tracking_provider_mal)
    ExternalAnimeProvider.BANGUMI -> stringResource(R.string.tracking_provider_bangumi)
    ExternalAnimeProvider.DANDANPLAY -> stringResource(R.string.tracking_provider_dandanplay)
}

@Composable
private fun statusLabel(status: ExternalAnimeListStatus?): String = when (status) {
    ExternalAnimeListStatus.WATCHING -> stringResource(R.string.tracking_status_watching)
    ExternalAnimeListStatus.COMPLETED -> stringResource(R.string.tracking_status_completed)
    ExternalAnimeListStatus.ON_HOLD -> stringResource(R.string.tracking_status_on_hold)
    ExternalAnimeListStatus.DROPPED -> stringResource(R.string.tracking_status_dropped)
    ExternalAnimeListStatus.PLAN_TO_WATCH -> stringResource(R.string.tracking_status_plan_to_watch)
    null -> stringResource(R.string.tracking_status_unchanged)
}

@Composable
private fun trackingErrorLabel(error: MobileTrackingError, detail: String?): String = when (error) {
    MobileTrackingError.ACCESS_CODE_REQUIRED -> stringResource(R.string.tracking_error_access_code_required)
    MobileTrackingError.ACCESS_CODE_REJECTED -> stringResource(R.string.tracking_error_access_code_rejected)
    MobileTrackingError.PREVIEW_CHANGED -> stringResource(R.string.tracking_error_preview_changed)
    MobileTrackingError.REQUEST_FAILED -> detail ?: stringResource(R.string.tracking_error_request_failed)
}

@Composable
private fun SavedConnectionRow(
    connection: LanLibraryConnectionProfile,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onForget: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) Color(0xFF273747) else PanelColor,
        border = BorderStroke(1.dp, if (isSelected) AccentBlue else Color(0xFF343D45)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        connection.displayName,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        connection.normalizedBaseUrl,
                        color = SubtleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isSelected) {
                    StatusPill(stringResource(R.string.status_selected))
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onSelect,
                    modifier = Modifier.testTag("saved-connection:${connection.id}"),
                ) {
                    Text(
                        if (isSelected) {
                            stringResource(R.string.action_reconnect)
                        } else {
                            stringResource(R.string.action_connect)
                        },
                    )
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("saved-connection-edit:${connection.id}"),
                ) {
                    Text(stringResource(R.string.action_edit))
                }
                TextButton(
                    onClick = onForget,
                    modifier = Modifier.testTag("saved-connection-forget:${connection.id}"),
                ) {
                    Text(stringResource(R.string.action_forget))
                }
            }
        }
    }
}
