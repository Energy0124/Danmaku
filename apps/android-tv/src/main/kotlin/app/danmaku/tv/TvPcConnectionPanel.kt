package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.danmaku.library.LanLibraryConnectionProfile

@Composable
internal fun TvPcConnectionPanel(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    pairingToken: String,
    onPairingTokenChange: (String) -> Unit,
    savedConnections: List<LanLibraryConnectionProfile>,
    selectedBaseUrl: String,
    libraryError: String?,
    refreshPcFocusRequester: FocusRequester,
    discoverPcFocusRequester: FocusRequester,
    onRefresh: () -> Unit,
    onDiscover: () -> Unit,
    onSave: () -> Unit,
    onSelectConnection: (LanLibraryConnectionProfile) -> Unit,
    onForgetConnection: (LanLibraryConnectionProfile) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(TvPanelRaisedColor)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.pc_connection_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(stringResource(R.string.pc_connection_body), color = TvMutedText)
            }
            TvRailPill(
                if (serverUrl.isBlank()) {
                    stringResource(R.string.pc_no_server)
                } else {
                    stringResource(R.string.pc_server_set)
                },
                active = serverUrl.isNotBlank(),
                modifier = Modifier.width(180.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onRefresh,
                modifier = Modifier
                    .focusRequester(refreshPcFocusRequester)
                    .focusProperties {
                        right = discoverPcFocusRequester
                    }
                    .tvFocusHalo(RoundedCornerShape(18.dp)),
            ) {
                Text(stringResource(R.string.action_refresh_pc_library))
            }
            Button(
                onClick = onDiscover,
                modifier = Modifier
                    .focusRequester(discoverPcFocusRequester)
                    .focusProperties {
                        left = refreshPcFocusRequester
                    }
                    .tvFocusHalo(RoundedCornerShape(18.dp)),
            ) {
                Text(stringResource(R.string.action_discover_pc))
            }
            Button(
                onClick = onSave,
                modifier = Modifier.tvFocusHalo(RoundedCornerShape(18.dp)),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvTextInput(
                value = serverUrl,
                onValueChange = onServerUrlChange,
                placeholder = stringResource(R.string.pc_server_url_placeholder),
                modifier = Modifier.weight(1f),
            )
            TvTextInput(
                value = pairingToken,
                onValueChange = onPairingTokenChange,
                placeholder = stringResource(R.string.pairing_token_placeholder),
                modifier = Modifier.weight(1f),
            )
        }
        if (savedConnections.isNotEmpty()) {
            Text(
                stringResource(R.string.saved_pcs_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(savedConnections, key = { it.id }) { connection ->
                    TvSavedConnectionCard(
                        connection = connection,
                        isSelected = connection.normalizedBaseUrl == selectedBaseUrl,
                        onSelect = { onSelectConnection(connection) },
                        onForget = { onForgetConnection(connection) },
                    )
                }
            }
        }
        libraryError?.let {
            Text(stringResource(R.string.library_error_prefix, it), color = Color(0xFFFCA5A5))
        }
    }
}
