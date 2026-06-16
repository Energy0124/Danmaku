package app.danmaku.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.PlaybackStatus
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.android.AndroidLibraryFavoriteStore
import app.danmaku.library.android.AndroidLanLibraryConnectionStore
import app.danmaku.library.android.LanLibraryClient
import app.danmaku.library.android.LanLibraryDiscoveryClient
import app.danmaku.player.android.Media3PlaybackController
import app.danmaku.player.android.Media3PlaybackServiceConnection
import kotlinx.coroutines.delay

internal val AppBackground = Color(0xFF101214)
internal val PlayerBlack = Color(0xFF050607)
internal val PanelColor = Color(0xFF191D21)
internal val PanelAltColor = Color(0xFF20262B)
internal val SubtleText = Color(0xFFB7C0C9)
internal val AccentBlue = Color(0xFF7DD3FC)
internal val AccentAmber = Color(0xFFFBBF24)
internal val DangerRed = Color(0xFFFCA5A5)

internal data class LibraryPosterEndpoint(
    val baseUrl: String,
    val pairingToken: String,
) {
    fun posterUrl(item: LibraryMediaItem): String? {
        val path = item.posterPath ?: return null
        return "${baseUrl.trim().trimEnd('/')}$path?token=${pairingToken.encodedQueryValue()}"
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = AccentBlue,
                    secondary = AccentAmber,
                    background = AppBackground,
                    surface = PanelColor,
                    surfaceVariant = PanelAltColor,
                    onPrimary = PlayerBlack,
                    onSecondary = PlayerBlack,
                    onBackground = Color(0xFFF7F7F8),
                    onSurface = Color(0xFFF7F7F8),
                    onSurfaceVariant = SubtleText,
                    outline = Color(0xFF3A4149),
                ),
            ) {
                MobilePlayerScreen()
            }
        }
    }
}

@Composable
private fun MobilePlayerScreen() {
    val context = LocalContext.current
    val playbackConnection = remember {
        Media3PlaybackServiceConnection(context.applicationContext)
    }
    val libraryClient = remember { LanLibraryClient() }
    val libraryConnectionSession = remember(libraryClient) { LanLibraryConnectionSession(libraryClient) }
    val progressSync = remember(libraryClient) {
        LanPlaybackProgressSync(libraryClient, System::currentTimeMillis)
    }
    val playbackPreparer = remember(libraryClient) { LanPlaybackPreparer(libraryClient) }
    val connectionStore = remember(context) {
        AndroidLanLibraryConnectionStore(context.applicationContext)
    }
    val favoriteStore = remember(context) {
        AndroidLibraryFavoriteStore(context.applicationContext)
    }
    val discoveryClient = remember { LanLibraryDiscoveryClient() }
    val scope = rememberCoroutineScope()
    val appState = remember(connectionStore, favoriteStore) {
        MobilePlayerState(
            initialSavedConnections = connectionStore.loadProfiles(),
            initialFavoriteMediaIds = favoriteStore.loadFavoriteMediaIds(),
        )
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        appState.controller?.let {
            appState.nowPlaying = null
            it.load(PlaybackSource.LocalFile(uri.toString()))
            appState.snapshot = it.snapshot()
        }
    }
    val actionHandler = remember(
        appState,
        scope,
        libraryConnectionSession,
        progressSync,
        playbackPreparer,
        connectionStore,
        favoriteStore,
        discoveryClient,
    ) {
        MobilePlayerActionHandler(
            state = appState,
            scope = scope,
            libraryConnectionSession = libraryConnectionSession,
            progressSync = progressSync,
            playbackPreparer = playbackPreparer,
            connectionStore = connectionStore,
            favoriteStore = favoriteStore,
            discoveryClient = discoveryClient,
            openVideoPicker = { openDocument.launch(arrayOf("video/*")) },
        )
    }

    DisposableEffect(playbackConnection) {
        playbackConnection.connect(
            executor = ContextCompat.getMainExecutor(context),
            onConnected = {
                appState.controller = it
                appState.snapshot = it.snapshot()
                appState.playbackError = null
            },
            onFailure = {
                appState.playbackError = it.message
            },
        )
        onDispose {
            appState.controller = null
            playbackConnection.close()
        }
    }

    LaunchedEffect(appState.controller) {
        val activeController = appState.controller ?: return@LaunchedEffect
        while (true) {
            appState.snapshot = activeController.snapshot()
            delay(250)
        }
    }

    MobileAppScaffold(
        state = appState.toUiState(),
        actions = actionHandler.toAppActions(),
    )
}

@Composable
internal fun LibraryPosterTile(
    item: LibraryMediaItem,
    title: String,
    selected: Boolean,
    posterEndpoint: LibraryPosterEndpoint?,
    progressLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val posterUrl = posterEndpoint?.posterUrl(item)
    val posterImage = rememberPosterImage(posterUrl)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) AccentBlue else Color(0xFF26313A)),
    ) {
        if (posterImage.bitmap != null) {
            Image(
                bitmap = posterImage.bitmap,
                contentDescription = stringResource(R.string.poster_content_description, title),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                title.initials(),
                color = if (selected) PlayerBlack else Color(0xFFE5E7EB),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (posterImage.state == PosterImageLoadState.LOADING) {
            PosterPill(
                label = "Loading",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
        }
        progressLabel?.let {
            PosterPill(
                label = it,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun PosterPill(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.62f),
    ) {
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

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
internal fun ConnectionPanel(
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

@Composable
internal fun EmptyPanel(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF15191D),
        border = BorderStroke(1.dp, Color(0xFF2B3239)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, color = SubtleText)
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
internal fun StatusPill(label: String) {
    Surface(
        shape = CircleShape,
        color = Color(0xFF1E2930),
        border = BorderStroke(1.dp, Color(0xFF35424D)),
    ) {
        Text(
            label,
            modifier = Modifier
                .widthIn(min = 72.dp)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            color = AccentAmber,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun ErrorText(message: String) {
    Text(
        message,
        color = DangerRed,
        style = MaterialTheme.typography.bodySmall,
    )
}
