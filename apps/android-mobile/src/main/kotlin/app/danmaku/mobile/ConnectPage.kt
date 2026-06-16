package app.danmaku.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.library.LanLibraryConnectionProfile

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
    onServerUrlChange: (String) -> Unit,
    onPairingTokenChange: (String) -> Unit,
    onSelectConnection: (LanLibraryConnectionProfile) -> Unit,
    onEditConnection: (LanLibraryConnectionProfile) -> Unit,
    onForgetConnection: (LanLibraryConnectionProfile) -> Unit,
    onSaveConnection: () -> Unit,
    onDiscover: () -> Unit,
    onRefresh: () -> Unit,
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
        item(key = "connect-help") {
            EmptyPanel(
                title = stringResource(R.string.connect_help_title),
                body = stringResource(R.string.connect_help_body),
            )
        }
    }
}

@Composable
private fun ConnectionPanel(
    catalog: LibraryCatalog?,
    serverUrl: String,
    pairingToken: String,
    savedConnections: List<LanLibraryConnectionProfile>,
    libraryError: String?,
    onServerUrlChange: (String) -> Unit,
    onPairingTokenChange: (String) -> Unit,
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
                    modifier = Modifier.fillMaxWidth(),
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
