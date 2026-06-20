package app.danmaku.desktop

import app.danmaku.provider.dandanplay.DandanplayAuthenticationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.darkColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.danmaku.domain.DanmakuDisplaySettings
import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeMatchCandidate
import app.danmaku.domain.ExternalAnimeMatchQuery
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeMappingSource
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeSyncFailure
import app.danmaku.domain.ExternalAnimeTrackingPlan
import app.danmaku.domain.ExternalAnimeTrackingPlanUpdate
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogQuery
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryItemMetadataStatus
import app.danmaku.domain.LocalDanmakuParser
import app.danmaku.domain.LibraryEpisodeDetail
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibraryNextUpItem
import app.danmaku.domain.LibraryNextUpReason
import app.danmaku.domain.LibraryPlaybackProgressItem
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LibrarySeriesWatchSummary
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.LibraryWatchState
import app.danmaku.domain.LibraryWatchStatus
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackStatus
import app.danmaku.domain.PlaybackTrack
import app.danmaku.domain.PlaybackTrackKind
import app.danmaku.domain.continueWatchingItems
import app.danmaku.domain.displayName
import app.danmaku.domain.episodeDetail
import app.danmaku.domain.externalAnimeTrackingPlan
import app.danmaku.domain.externalAnimeSyncRetryAfterEpochMs
import app.danmaku.domain.filteredItems
import app.danmaku.domain.groupedSeries
import app.danmaku.domain.nextItem
import app.danmaku.domain.nextUpItems
import app.danmaku.domain.previousItem
import app.danmaku.domain.recentlyWatchedItems
import app.danmaku.domain.toPlaybackProgress
import app.danmaku.domain.seriesWatchSummaryById
import app.danmaku.domain.watchStatusByMediaId
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.LanPlaybackPreparation
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.LanPlaybackTarget
import app.danmaku.library.jvm.JvmLanLibraryClient
import app.danmaku.server.LocalLibraryDiscoveryAnnouncer
import app.danmaku.server.LocalLibraryServerEvent
import app.danmaku.server.PublicGetHookResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Rectangle
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.Window as AwtWindow
import java.awt.datatransfer.StringSelection
import java.net.URI
import kotlin.math.roundToInt
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.jetbrains.skia.Image as SkiaImage

@Composable
internal fun DandanplayProviderCard(
    strings: DesktopStrings,
    settings: DandanplayProviderSettings,
    onSave: (String, String?, String?, DandanplayAuthenticationMode, Int) -> Unit,
    onClear: () -> Unit,
    connectionTestStatus: SettingsConnectionTestStatus?,
    onTestConnection: () -> Unit,
    onCleanupExpiredCaches: () -> Unit,
) {
    var baseUrl by remember(settings) { mutableStateOf(settings.baseUrl) }
    var appId by remember(settings) { mutableStateOf(settings.appId.orEmpty()) }
    var appSecret by remember(settings) { mutableStateOf("") }
    var authenticationMode by remember(settings) { mutableStateOf(settings.authenticationMode) }
    var cacheMaxAgeDaysText by remember(settings) { mutableStateOf(settings.cacheMaxAgeDays.toString()) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showCleanupConfirm by remember { mutableStateOf(false) }
    val baseUrlError = httpUrlError(baseUrl, strings.apiBaseUrlLabel)
    val cacheMaxAgeDaysError = integerRangeError(cacheMaxAgeDaysText, 1..3650, "days")
    val normalizedAppId = appId.trim().ifEmpty { null }
    val parsedCacheMaxAgeDays = cacheMaxAgeDaysText.toIntOrNull()
    val isDirty = baseUrl.trim() != settings.baseUrl ||
        normalizedAppId != settings.appId ||
        appSecret.isNotBlank() ||
        authenticationMode != settings.authenticationMode ||
        parsedCacheMaxAgeDays != settings.cacheMaxAgeDays
    val canSave = isDirty && baseUrlError == null && cacheMaxAgeDaysError == null

    SectionCard(strings.dandanplayProvidersTitle) {
        Text(
            strings.dandanplayProvidersDescription,
            color = DanmakuColors.TextMuted,
        )
        Spacer(modifier = Modifier.height(10.dp))
        MetadataRow("dandanplay", settings.statusLabel(strings))
        MetadataRow(strings.cacheExpiryLabel, "${settings.cacheMaxAgeDays} days")
        connectionTestStatus?.let {
            SettingsConnectionTestStatusRow(strings = strings, label = strings.lastTestLabel, status = it)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text(strings.apiBaseUrlLabel) },
            modifier = Modifier.fillMaxWidth(),
            isError = baseUrlError != null,
            singleLine = true,
        )
        SettingsValidationText(listOfNotNull(baseUrlError))
        OutlinedTextField(
            value = appId,
            onValueChange = { appId = it },
            label = { Text(strings.appIdOptionalLabel) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = appSecret,
            onValueChange = { appSecret = it },
            label = {
                Text(
                    if (settings.hasAppSecret) {
                        strings.appSecretKeepLabel
                    } else {
                        strings.appSecretOptionalLabel
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            value = cacheMaxAgeDaysText,
            onValueChange = { cacheMaxAgeDaysText = it },
            label = { Text(strings.cacheMaxAgeDaysLabel) },
            modifier = Modifier.fillMaxWidth(),
            isError = cacheMaxAgeDaysError != null,
            singleLine = true,
        )
        SettingsValidationText(listOfNotNull(cacheMaxAgeDaysError))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { authenticationMode = DandanplayAuthenticationMode.SIGNED },
                enabled = authenticationMode != DandanplayAuthenticationMode.SIGNED,
            ) {
                Text(strings.signedAuthAction)
            }
            Button(
                onClick = { authenticationMode = DandanplayAuthenticationMode.CREDENTIAL },
                enabled = authenticationMode != DandanplayAuthenticationMode.CREDENTIAL,
            ) {
                Text(strings.credentialAuthAction)
            }
            Text(
                strings.currentAuthLabel(authenticationMode.localizedLabel(strings)),
                color = DanmakuColors.TextMuted,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSave(
                        baseUrl.trim(),
                        normalizedAppId,
                        appSecret.takeIf(String::isNotBlank),
                        authenticationMode,
                        parsedCacheMaxAgeDays ?: settings.cacheMaxAgeDays,
                    )
                    appSecret = ""
                },
                enabled = canSave,
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.saveDandanplaySettingsAction)
            }
            Button(onClick = onTestConnection) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.testSavedAction)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showClearConfirm = true }) {
                Text(strings.clearAction)
            }
            Button(onClick = { showCleanupConfirm = true }) {
                Text(strings.cleanExpiredCacheAction)
            }
        }
    }
    if (showClearConfirm) {
        SettingsConfirmationDialog(
            title = strings.clearDandanplayTitle,
            text = strings.clearDandanplayText,
            confirmLabel = strings.clearAction,
            cancelLabel = strings.cancelAction,
            onConfirm = onClear,
            onDismiss = { showClearConfirm = false },
        )
    }
    if (showCleanupConfirm) {
        SettingsConfirmationDialog(
            title = strings.cleanExpiredDandanplayTitle,
            text = strings.cleanExpiredDandanplayText,
            confirmLabel = strings.cleanExpiredCacheAction,
            cancelLabel = strings.cancelAction,
            onConfirm = onCleanupExpiredCaches,
            onDismiss = { showCleanupConfirm = false },
        )
    }
}

@Composable
internal fun ExternalAnimeProviderSettingsCard(
    strings: DesktopStrings,
    settings: ExternalAnimeProviderSettings,
    onSave: (String?, String?, String?, String, String, String?) -> Unit,
    onStartMyAnimeListOAuth: (String?, String?) -> Unit,
    myAnimeListConnectionTestStatus: SettingsConnectionTestStatus?,
    bangumiConnectionTestStatus: SettingsConnectionTestStatus?,
    onTestMyAnimeListConnection: () -> Unit,
    onTestBangumiConnection: () -> Unit,
    onClearMyAnimeList: () -> Unit,
    onClearBangumi: () -> Unit,
) {
    var myAnimeListClientId by remember(settings) { mutableStateOf(settings.myAnimeListClientId.orEmpty()) }
    var myAnimeListClientSecret by remember(settings) { mutableStateOf("") }
    var myAnimeListAccessToken by remember(settings) { mutableStateOf("") }
    var bangumiBaseUrl by remember(settings) { mutableStateOf(settings.bangumiBaseUrl) }
    var bangumiUserAgent by remember(settings) { mutableStateOf(settings.bangumiUserAgent) }
    var bangumiAccessToken by remember(settings) { mutableStateOf("") }
    var showClearMyAnimeListConfirm by remember { mutableStateOf(false) }
    var showClearBangumiConfirm by remember { mutableStateOf(false) }
    val bangumiBaseUrlError = httpUrlError(bangumiBaseUrl, strings.bangumiApiBaseUrlLabel)
    val bangumiUserAgentError = if (bangumiUserAgent.isBlank()) {
        strings.bangumiUserAgentRequiredError
    } else {
        null
    }
    val normalizedMyAnimeListClientId = myAnimeListClientId.trim().ifEmpty { null }
    val isDirty = normalizedMyAnimeListClientId != settings.myAnimeListClientId ||
        myAnimeListClientSecret.isNotBlank() ||
        myAnimeListAccessToken.isNotBlank() ||
        bangumiBaseUrl.trim() != settings.bangumiBaseUrl ||
        bangumiUserAgent.trim() != settings.bangumiUserAgent ||
        bangumiAccessToken.isNotBlank()
    val canSave = isDirty && bangumiBaseUrlError == null && bangumiUserAgentError == null

    SectionCard(strings.externalAnimeListsTitle) {
        Text(
            strings.externalAnimeListsDescription,
            color = DanmakuColors.TextMuted,
        )
        MetadataRow("MyAnimeList", settings.myAnimeListStatusLabel(strings))
        MetadataRow("Bangumi", settings.bangumiStatusLabel(strings))
        myAnimeListConnectionTestStatus?.let {
            SettingsConnectionTestStatusRow(strings = strings, label = strings.myAnimeListTestLabel, status = it)
        }
        bangumiConnectionTestStatus?.let {
            SettingsConnectionTestStatusRow(strings = strings, label = strings.bangumiTestLabel, status = it)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = myAnimeListClientId,
            onValueChange = { myAnimeListClientId = it },
            label = { Text(strings.myAnimeListClientIdLabel) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = myAnimeListAccessToken,
            onValueChange = { myAnimeListAccessToken = it },
            label = {
                Text(
                    if (settings.hasMyAnimeListAccessToken) {
                        strings.myAnimeListAccessTokenKeepLabel
                    } else {
                        strings.myAnimeListAccessTokenOptionalLabel
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            value = myAnimeListClientSecret,
            onValueChange = { myAnimeListClientSecret = it },
            label = {
                Text(
                    if (settings.hasMyAnimeListClientSecret) {
                        strings.myAnimeListClientSecretKeepLabel
                    } else {
                        strings.myAnimeListClientSecretOptionalLabel
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = bangumiBaseUrl,
                onValueChange = { bangumiBaseUrl = it },
                label = { Text(strings.bangumiApiBaseUrlLabel) },
                modifier = Modifier.weight(1f),
                isError = bangumiBaseUrlError != null,
                singleLine = true,
            )
            OutlinedTextField(
                value = bangumiUserAgent,
                onValueChange = { bangumiUserAgent = it },
                label = { Text(strings.bangumiUserAgentLabel) },
                modifier = Modifier.weight(1f),
                isError = bangumiUserAgentError != null,
                singleLine = true,
            )
        }
        SettingsValidationText(listOfNotNull(bangumiBaseUrlError, bangumiUserAgentError))
        OutlinedTextField(
            value = bangumiAccessToken,
            onValueChange = { bangumiAccessToken = it },
            label = {
                Text(
                    if (settings.hasBangumiAccessToken) {
                        strings.bangumiAccessTokenKeepLabel
                    } else {
                        strings.bangumiAccessTokenOptionalLabel
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSave(
                        normalizedMyAnimeListClientId,
                        myAnimeListClientSecret.takeIf(String::isNotBlank),
                        myAnimeListAccessToken.takeIf(String::isNotBlank),
                        bangumiBaseUrl.trim(),
                        bangumiUserAgent.trim(),
                        bangumiAccessToken.takeIf(String::isNotBlank),
                    )
                    myAnimeListClientSecret = ""
                    myAnimeListAccessToken = ""
                    bangumiAccessToken = ""
                },
                enabled = canSave,
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.saveExternalListsAction)
            }
            Button(
                onClick = {
                    onStartMyAnimeListOAuth(myAnimeListClientId, myAnimeListClientSecret)
                    myAnimeListClientSecret = ""
                },
                enabled = myAnimeListClientId.isNotBlank(),
            ) {
                Text(strings.connectMyAnimeListAction)
            }
            Button(
                onClick = onTestMyAnimeListConnection,
                enabled = settings.myAnimeListClientId != null,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.testMyAnimeListAction)
            }
            Button(onClick = onTestBangumiConnection) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.testBangumiAction)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showClearMyAnimeListConfirm = true }) {
                Text(strings.clearMyAnimeListAction)
            }
            Button(onClick = { showClearBangumiConfirm = true }) {
                Text(strings.clearBangumiAction)
            }
        }
    }
    if (showClearMyAnimeListConfirm) {
        SettingsConfirmationDialog(
            title = strings.clearMyAnimeListTitle,
            text = strings.clearMyAnimeListText,
            confirmLabel = strings.clearMyAnimeListAction,
            cancelLabel = strings.cancelAction,
            onConfirm = onClearMyAnimeList,
            onDismiss = { showClearMyAnimeListConfirm = false },
        )
    }
    if (showClearBangumiConfirm) {
        SettingsConfirmationDialog(
            title = strings.clearBangumiTitle,
            text = strings.clearBangumiText,
            confirmLabel = strings.clearBangumiAction,
            cancelLabel = strings.cancelAction,
            onConfirm = onClearBangumi,
            onDismiss = { showClearBangumiConfirm = false },
        )
    }
}


