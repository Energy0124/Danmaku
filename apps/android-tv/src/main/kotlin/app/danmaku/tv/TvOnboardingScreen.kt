package app.danmaku.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.library.lanLibraryConnectionProfile

@Composable
internal fun TvOnboardingScreen(
    navigation: TvNavigationState,
    navigator: TvNavigator,
    session: TvSessionUiState,
    onDiscover: () -> Unit,
    onSetManualEntry: (Boolean) -> Unit,
    onConnectAddress: (String) -> Unit,
    onSelectConnection: (LanLibraryConnectionProfile) -> Unit,
) {
    val setup = session.connection
    val busy = setup.isDiscovering || session.isRefreshing
    var address by rememberSaveable { mutableStateOf(session.serverUrl) }
    val keyboard = LocalSoftwareKeyboardController.current
    val connect = {
        keyboard?.hide()
        onConnectAddress(address)
    }
    BackHandler(enabled = setup.isManualEntry) { onSetManualEntry(false) }
    val connections = (
        session.savedConnections + setup.discoveredUrls.map { lanLibraryConnectionProfile(it) }
    ).distinctBy { it.normalizedBaseUrl }
    val availableConnections = if (session.isRefreshing) emptyList() else connections
    val focusKeys = availableConnections.map { "onboarding-pc:${it.id}" } +
        (if (busy) emptyList() else listOf("onboarding-discover")) + "onboarding-manual"
    val defaultKey = availableConnections.firstOrNull()?.let { "onboarding-pc:${it.id}" }
        ?: if (busy) "onboarding-manual" else "onboarding-discover"
    val fallback = navigator.savedFocus(TvRoute.Onboarding) !in focusKeys

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = 48.dp, vertical = 32.dp)
            .testTag("screen-connect"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.app_name),
                color = TvAccent,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            Text(
                text = stringResource(R.string.onboarding_title),
                color = TvContent,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                text = stringResource(R.string.onboarding_body),
                color = TvSecondaryContent,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 680.dp),
            )
        }
        if (busy) {
            item {
                Text(
                    text = stringResource(
                        if (setup.isDiscovering) R.string.status_discovering else R.string.status_connecting,
                    ),
                    color = TvAccent,
                    modifier = Modifier.testTag("onboarding-status"),
                )
            }
        }
        setup.error?.let { error ->
            item {
                Text(
                    text = stringResource(
                        when (error) {
                            TvConnectionError.DISCOVERY_FAILED -> R.string.connection_discovery_failed
                            TvConnectionError.CONNECTION_FAILED -> R.string.connection_failed
                            TvConnectionError.INVALID_ADDRESS -> R.string.connection_invalid_address
                        },
                    ),
                    color = TvError,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 680.dp).testTag("onboarding-error"),
                )
            }
        }
        if (setup.isManualEntry) {
            item {
                TvTextInput(
                    value = address,
                    onValueChange = { address = it },
                    placeholder = stringResource(R.string.connection_address_hint),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = { if (!busy && address.isNotBlank()) connect() },
                    ),
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth()
                        .tvRouteFocus(
                            navigation, navigator, TvRoute.Onboarding, "onboarding-address",
                            isDefault = true,
                            fallbackToDefault = true,
                        )
                        .testTag("onboarding-address"),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = connect,
                        enabled = address.isNotBlank() && !busy,
                        modifier = Modifier.testTag("onboarding-connect"),
                        colors = tvButtonColors(selected = true),
                        scale = tvButtonScale(),
                    ) { Text(stringResource(R.string.action_connect)) }
                    Button(
                        onClick = { onSetManualEntry(false) },
                        modifier = Modifier.testTag("onboarding-back"),
                        colors = tvButtonColors(),
                        scale = tvButtonScale(),
                    ) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        } else {
            if (connections.isNotEmpty()) {
                item { Text(stringResource(R.string.connection_choose_pc), color = TvSecondaryContent) }
            } else if (setup.hasSearched && !busy && setup.error == null) {
                item {
                    Text(
                        stringResource(R.string.connection_none_found),
                        color = TvSecondaryContent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 680.dp).testTag("onboarding-empty"),
                    )
                }
            }
            itemsIndexed(connections, key = { _, pc -> pc.id }) { _, pc ->
                val key = "onboarding-pc:${pc.id}"
                Button(
                    onClick = { onSelectConnection(pc) },
                    enabled = !session.isRefreshing,
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth()
                        .tvRouteFocus(
                            navigation, navigator, TvRoute.Onboarding, key,
                            isDefault = key == defaultKey,
                            fallbackToDefault = fallback,
                        )
                        .tvFocusHalo(RoundedCornerShape(16.dp))
                        .testTag(key),
                    colors = tvButtonColors(selected = key == defaultKey),
                    scale = tvButtonScale(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(pc.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (pc.displayName != pc.normalizedBaseUrl
                                .removePrefix("http://").removePrefix("https://")
                        ) {
                            Text(
                                pc.normalizedBaseUrl,
                                color = TvSecondaryContent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = onDiscover,
                        enabled = !busy,
                        modifier = Modifier
                            .tvRouteFocus(
                                navigation, navigator, TvRoute.Onboarding, "onboarding-discover",
                                isDefault = defaultKey == "onboarding-discover",
                                fallbackToDefault = fallback,
                            )
                            .testTag("onboarding-discover"),
                        colors = tvButtonColors(),
                        scale = tvButtonScale(),
                    ) { Text(stringResource(R.string.action_search_again)) }
                    Button(
                        onClick = { onSetManualEntry(true) },
                        modifier = Modifier
                            .tvRouteFocus(
                                navigation, navigator, TvRoute.Onboarding, "onboarding-manual",
                                isDefault = defaultKey == "onboarding-manual",
                                fallbackToDefault = fallback,
                            )
                            .testTag("onboarding-manual"),
                        colors = tvButtonColors(),
                        scale = tvButtonScale(),
                    ) { Text(stringResource(R.string.action_manual_connection)) }
                }
            }
        }
    }
}
