package app.danmaku.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import app.danmaku.library.LanLibraryConnectionProfile

@Composable
internal fun TvPcScreen(
    navigation: TvNavigationState,
    navigator: TvNavigator,
    session: TvSessionUiState,
    onServerUrlChange: (String) -> Unit,
    onPairingTokenChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onDiscover: () -> Unit,
    onSave: () -> Unit,
    onSelectConnection: (LanLibraryConnectionProfile) -> Unit,
    onForgetConnection: (LanLibraryConnectionProfile) -> Unit,
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
                TvTextInput(
                    value = session.pairingToken,
                    onValueChange = onPairingTokenChange,
                    placeholder = stringResource(R.string.pairing_token_placeholder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvRouteFocus(navigation, navigator, TvRoute.Pc, "pc-token")
                        .testTag("pc-token"),
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
    }
}
