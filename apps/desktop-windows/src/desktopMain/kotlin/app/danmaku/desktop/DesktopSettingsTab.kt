package app.danmaku.desktop

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
internal fun SettingsSectionRail(
    selectedSection: DesktopSettingsSection,
    strings: DesktopStrings,
    onSectionSelected: (DesktopSettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(DanmakuColors.Surface, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(strings.settingsTitle, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
        Text(strings.settingsDescription, color = DanmakuColors.TextMuted, maxLines = 2)
        Divider(color = DanmakuColors.SurfaceRaised)
        DesktopSettingsSection.entries.forEach { section ->
            AppRailItem(
                icon = section.icon,
                label = strings.settingsSectionTitle(section),
                selected = selectedSection == section,
                onClick = { onSectionSelected(section) },
            )
        }
    }
}

@Composable
internal fun ProfileTab(
    desktopLanguage: DesktopUiLanguage,
    strings: DesktopStrings,
    mpvRuntimeStatus: String,
    videoHostStatus: String,
    serverBaseUrl: String,
    networkUrls: List<String>,
    pairingToken: String,
    recentServerEvents: List<LocalLibraryServerEvent>,
    appLogPath: Path,
    mpvLogPath: Path,
    diagnosticLog: List<DesktopDiagnosticLogEntry>,
    danmakuSettings: DanmakuDisplaySettings,
    onSaveDanmakuSettings: (DanmakuDisplaySettings) -> Unit,
    dandanplayCacheEntries: List<DesktopDandanplayCommentCache>,
    onRefreshDandanplayCacheEntries: () -> Unit,
    onDeleteDandanplayCacheEntry: (String) -> Unit,
    onCleanupExpiredDandanplayCaches: () -> Unit,
    onDesktopLanguageChange: (DesktopUiLanguage) -> Unit,
    dandanplaySettings: DandanplayProviderSettings,
    onSaveDandanplaySettings: (String, String?, String?, DandanplayAuthenticationMode, Int) -> Unit,
    onClearDandanplaySettings: () -> Unit,
    dandanplayConnectionTestStatus: SettingsConnectionTestStatus?,
    onTestDandanplayConnection: () -> Unit,
    externalAnimeProviderSettings: ExternalAnimeProviderSettings,
    onSaveExternalAnimeProviderSettings: (String?, String?, String?, String, String, String?) -> Unit,
    onStartMyAnimeListOAuth: (String?, String?) -> Unit,
    myAnimeListConnectionTestStatus: SettingsConnectionTestStatus?,
    bangumiConnectionTestStatus: SettingsConnectionTestStatus?,
    onTestMyAnimeListConnection: () -> Unit,
    onTestBangumiConnection: () -> Unit,
    onClearMyAnimeListSettings: () -> Unit,
    onClearBangumiSettings: () -> Unit,
    localServerConnectionTestStatus: SettingsConnectionTestStatus?,
    onTestLocalServerConnection: () -> Unit,
) {
    var selectedSection by remember { mutableStateOf(DesktopSettingsSection.GENERAL) }
    TabScaffold {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 1120.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingsSectionRail(
                        selectedSection = selectedSection,
                        strings = strings,
                        onSectionSelected = { selectedSection = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SettingsSectionContent(
                        selectedSection = selectedSection,
                        desktopLanguage = desktopLanguage,
                        strings = strings,
                        mpvRuntimeStatus = mpvRuntimeStatus,
                        videoHostStatus = videoHostStatus,
                        serverBaseUrl = serverBaseUrl,
                        networkUrls = networkUrls,
                        pairingToken = pairingToken,
                        recentServerEvents = recentServerEvents,
                        appLogPath = appLogPath,
                        mpvLogPath = mpvLogPath,
                        diagnosticLog = diagnosticLog,
                        danmakuSettings = danmakuSettings,
                        onSaveDanmakuSettings = onSaveDanmakuSettings,
                        dandanplayCacheEntries = dandanplayCacheEntries,
                        onRefreshDandanplayCacheEntries = onRefreshDandanplayCacheEntries,
                        onDeleteDandanplayCacheEntry = onDeleteDandanplayCacheEntry,
                        onCleanupExpiredDandanplayCaches = onCleanupExpiredDandanplayCaches,
                        onDesktopLanguageChange = onDesktopLanguageChange,
                        dandanplaySettings = dandanplaySettings,
                        onSaveDandanplaySettings = onSaveDandanplaySettings,
                        onClearDandanplaySettings = onClearDandanplaySettings,
                        dandanplayConnectionTestStatus = dandanplayConnectionTestStatus,
                        onTestDandanplayConnection = onTestDandanplayConnection,
                        externalAnimeProviderSettings = externalAnimeProviderSettings,
                        onSaveExternalAnimeProviderSettings = onSaveExternalAnimeProviderSettings,
                        onStartMyAnimeListOAuth = onStartMyAnimeListOAuth,
                        myAnimeListConnectionTestStatus = myAnimeListConnectionTestStatus,
                        bangumiConnectionTestStatus = bangumiConnectionTestStatus,
                        onTestMyAnimeListConnection = onTestMyAnimeListConnection,
                        onTestBangumiConnection = onTestBangumiConnection,
                        onClearMyAnimeListSettings = onClearMyAnimeListSettings,
                        onClearBangumiSettings = onClearBangumiSettings,
                        localServerConnectionTestStatus = localServerConnectionTestStatus,
                        onTestLocalServerConnection = onTestLocalServerConnection,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsSectionRail(
                        selectedSection = selectedSection,
                        strings = strings,
                        onSectionSelected = { selectedSection = it },
                        modifier = Modifier.width(238.dp),
                    )
                    SettingsSectionContent(
                        selectedSection = selectedSection,
                        desktopLanguage = desktopLanguage,
                        strings = strings,
                        mpvRuntimeStatus = mpvRuntimeStatus,
                        videoHostStatus = videoHostStatus,
                        serverBaseUrl = serverBaseUrl,
                        networkUrls = networkUrls,
                        pairingToken = pairingToken,
                        recentServerEvents = recentServerEvents,
                        appLogPath = appLogPath,
                        mpvLogPath = mpvLogPath,
                        diagnosticLog = diagnosticLog,
                        danmakuSettings = danmakuSettings,
                        onSaveDanmakuSettings = onSaveDanmakuSettings,
                        dandanplayCacheEntries = dandanplayCacheEntries,
                        onRefreshDandanplayCacheEntries = onRefreshDandanplayCacheEntries,
                        onDeleteDandanplayCacheEntry = onDeleteDandanplayCacheEntry,
                        onCleanupExpiredDandanplayCaches = onCleanupExpiredDandanplayCaches,
                        onDesktopLanguageChange = onDesktopLanguageChange,
                        dandanplaySettings = dandanplaySettings,
                        onSaveDandanplaySettings = onSaveDandanplaySettings,
                        onClearDandanplaySettings = onClearDandanplaySettings,
                        dandanplayConnectionTestStatus = dandanplayConnectionTestStatus,
                        onTestDandanplayConnection = onTestDandanplayConnection,
                        externalAnimeProviderSettings = externalAnimeProviderSettings,
                        onSaveExternalAnimeProviderSettings = onSaveExternalAnimeProviderSettings,
                        onStartMyAnimeListOAuth = onStartMyAnimeListOAuth,
                        myAnimeListConnectionTestStatus = myAnimeListConnectionTestStatus,
                        bangumiConnectionTestStatus = bangumiConnectionTestStatus,
                        onTestMyAnimeListConnection = onTestMyAnimeListConnection,
                        onTestBangumiConnection = onTestBangumiConnection,
                        onClearMyAnimeListSettings = onClearMyAnimeListSettings,
                        onClearBangumiSettings = onClearBangumiSettings,
                        localServerConnectionTestStatus = localServerConnectionTestStatus,
                        onTestLocalServerConnection = onTestLocalServerConnection,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun DesktopLanguageSettingsCard(
    selectedLanguage: DesktopUiLanguage,
    strings: DesktopStrings,
    onLanguageSelected: (DesktopUiLanguage) -> Unit,
) {
    SectionCard(strings.languageTitle) {
        Text(strings.languageDescription, color = DanmakuColors.TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DesktopUiLanguage.entries.forEach { language ->
                Button(
                    onClick = { onLanguageSelected(language) },
                    enabled = selectedLanguage != language,
                ) {
                    Text(language.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun SettingsSectionContent(
    selectedSection: DesktopSettingsSection,
    desktopLanguage: DesktopUiLanguage,
    strings: DesktopStrings,
    mpvRuntimeStatus: String,
    videoHostStatus: String,
    serverBaseUrl: String,
    networkUrls: List<String>,
    pairingToken: String,
    recentServerEvents: List<LocalLibraryServerEvent>,
    appLogPath: Path,
    mpvLogPath: Path,
    diagnosticLog: List<DesktopDiagnosticLogEntry>,
    danmakuSettings: DanmakuDisplaySettings,
    onSaveDanmakuSettings: (DanmakuDisplaySettings) -> Unit,
    dandanplayCacheEntries: List<DesktopDandanplayCommentCache>,
    onRefreshDandanplayCacheEntries: () -> Unit,
    onDeleteDandanplayCacheEntry: (String) -> Unit,
    onCleanupExpiredDandanplayCaches: () -> Unit,
    onDesktopLanguageChange: (DesktopUiLanguage) -> Unit,
    dandanplaySettings: DandanplayProviderSettings,
    onSaveDandanplaySettings: (String, String?, String?, DandanplayAuthenticationMode, Int) -> Unit,
    onClearDandanplaySettings: () -> Unit,
    dandanplayConnectionTestStatus: SettingsConnectionTestStatus?,
    onTestDandanplayConnection: () -> Unit,
    externalAnimeProviderSettings: ExternalAnimeProviderSettings,
    onSaveExternalAnimeProviderSettings: (String?, String?, String?, String, String, String?) -> Unit,
    onStartMyAnimeListOAuth: (String?, String?) -> Unit,
    myAnimeListConnectionTestStatus: SettingsConnectionTestStatus?,
    bangumiConnectionTestStatus: SettingsConnectionTestStatus?,
    onTestMyAnimeListConnection: () -> Unit,
    onTestBangumiConnection: () -> Unit,
    onClearMyAnimeListSettings: () -> Unit,
    onClearBangumiSettings: () -> Unit,
    localServerConnectionTestStatus: SettingsConnectionTestStatus?,
    onTestLocalServerConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showServerDashboard by remember { mutableStateOf(false) }
    var showDanmakuCacheManager by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (selectedSection) {
            DesktopSettingsSection.GENERAL -> {
                SectionCard(strings.settingsSectionTitle(DesktopSettingsSection.GENERAL)) {
                    MetadataRow(strings.appLabel, "Danmaku desktop")
                    MetadataRow(strings.primaryTargetsLabel, "Windows desktop, Android mobile/tablet, Android TV")
                    MetadataRow(strings.uiLanguageLabel, desktopLanguage.displayName)
                    MetadataRow(strings.supportedLabel, strings.uiLanguagesValue)
                }
                DesktopLanguageSettingsCard(
                    selectedLanguage = desktopLanguage,
                    strings = strings,
                    onLanguageSelected = onDesktopLanguageChange,
                )
                SectionCard(strings.privacyTitle) {
                    Text(
                        strings.credentialsPrivacyText,
                        color = DanmakuColors.TextMuted,
                    )
                    MetadataRow("MyAnimeList", externalAnimeProviderSettings.myAnimeListStatusText)
                    MetadataRow("Bangumi", externalAnimeProviderSettings.bangumiStatusText)
                    MetadataRow("dandanplay", dandanplaySettings.statusText)
                }
            }
            DesktopSettingsSection.LIBRARY -> {
                SectionCard(strings.settingsSectionTitle(DesktopSettingsSection.LIBRARY)) {
                    Text(
                        strings.librarySettingsDescription,
                        color = DanmakuColors.TextMuted,
                    )
                    MetadataRow(strings.metadataLabel, strings.metadataRefreshLibraryDetailsText)
                    MetadataRow(strings.importsLabel, strings.aniRssImportsManagedDownloadsText)
                }
            }
            DesktopSettingsSection.PLAYBACK -> {
                SectionCard(strings.playbackRuntimeTitle) {
                    MetadataRow(strings.mpvExecutorLabel, mpvRuntimeStatus)
                    MetadataRow(strings.videoHostLabel, videoHostStatus)
                    MetadataRow(strings.rendererLabel, strings.mpvRendererDescription)
                    MetadataRow(strings.focusModeLabel, strings.playerFocusModeDescription)
                }
            }
            DesktopSettingsSection.DANMAKU -> {
                DanmakuDisplaySettingsCard(
                    strings = strings,
                    settings = danmakuSettings,
                    onSave = onSaveDanmakuSettings,
                )
                SectionCard(strings.danmakuCacheSettingsTitle) {
                    Text(
                        strings.danmakuCacheSettingsDescription,
                        color = DanmakuColors.TextMuted,
                    )
                    MetadataRow(strings.cachedEpisodesLabel, dandanplayCacheEntries.size.toString())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LibraryActionButton(
                            imageVector = Icons.Filled.Subtitles,
                            label = strings.openCacheManagerAction,
                            onClick = {
                                onRefreshDandanplayCacheEntries()
                                showDanmakuCacheManager = true
                            },
                        )
                        LibraryActionButton(
                            imageVector = Icons.Filled.Refresh,
                            label = strings.refreshAction,
                            onClick = onRefreshDandanplayCacheEntries,
                        )
                    }
                }
            }
            DesktopSettingsSection.PROVIDERS -> {
                DandanplayProviderCard(
                    strings = strings,
                    settings = dandanplaySettings,
                    onSave = onSaveDandanplaySettings,
                    onClear = onClearDandanplaySettings,
                    connectionTestStatus = dandanplayConnectionTestStatus,
                    onTestConnection = onTestDandanplayConnection,
                    onCleanupExpiredCaches = onCleanupExpiredDandanplayCaches,
                )
                ExternalAnimeProviderSettingsCard(
                    strings = strings,
                    settings = externalAnimeProviderSettings,
                    onSave = onSaveExternalAnimeProviderSettings,
                    onStartMyAnimeListOAuth = onStartMyAnimeListOAuth,
                    myAnimeListConnectionTestStatus = myAnimeListConnectionTestStatus,
                    bangumiConnectionTestStatus = bangumiConnectionTestStatus,
                    onTestMyAnimeListConnection = onTestMyAnimeListConnection,
                    onTestBangumiConnection = onTestBangumiConnection,
                    onClearMyAnimeList = onClearMyAnimeListSettings,
                    onClearBangumi = onClearBangumiSettings,
                )
            }
            DesktopSettingsSection.SERVER -> {
                SectionCard(strings.localServerTitle) {
                    MetadataRow(strings.serverBaseUrlLabel, serverBaseUrl)
                    MetadataRow(strings.pairingCodeLabel, pairingToken)
                    networkUrls.forEach { MetadataRow(strings.lanUrlLabel, it) }
                    MetadataRow(
                        strings.discoveryLabel,
                        "UDP ${app.danmaku.domain.LanLibraryServerAnnouncement.DEFAULT_DISCOVERY_PORT}",
                    )
                    localServerConnectionTestStatus?.let {
                        SettingsConnectionTestStatusRow(strings = strings, label = strings.lastTestLabel, status = it)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LibraryActionButton(
                            imageVector = Icons.Filled.Devices,
                            label = strings.openServerDashboardAction,
                            onClick = { showServerDashboard = true },
                        )
                        LibraryActionButton(
                            imageVector = Icons.Filled.Refresh,
                            label = strings.testLocalServerAction,
                            onClick = onTestLocalServerConnection,
                        )
                    }
                }
            }
            DesktopSettingsSection.STORAGE -> {
                SectionCard(strings.settingsSectionTitle(DesktopSettingsSection.STORAGE)) {
                    MetadataRow(strings.appLogLabel, appLogPath.toString())
                    MetadataRow(strings.mpvLogLabel, mpvLogPath.toString())
                    Text(
                        strings.storageCleanupDescription,
                        color = DanmakuColors.TextMuted,
                    )
                }
            }
            DesktopSettingsSection.PRIVACY -> {
                SectionCard(strings.privacyCredentialsTitle) {
                    Text(
                        strings.privacyCredentialsDescription,
                        color = DanmakuColors.TextMuted,
                    )
                    MetadataRow("MyAnimeList", externalAnimeProviderSettings.myAnimeListStatusText)
                    MetadataRow("Bangumi", externalAnimeProviderSettings.bangumiStatusText)
                    MetadataRow("dandanplay", dandanplaySettings.statusText)
                }
            }
            DesktopSettingsSection.DIAGNOSTICS -> {
                SectionCard(strings.desktopRuntimeTitle) {
                    MetadataRow(strings.mpvExecutorLabel, mpvRuntimeStatus)
                    MetadataRow(strings.videoHostLabel, videoHostStatus)
                    MetadataRow(strings.appLogLabel, appLogPath.toString())
                    MetadataRow(strings.mpvLogLabel, mpvLogPath.toString())
                }
                DiagnosticsPanel(strings = strings, diagnosticLog = diagnosticLog)
            }
        }
    }
    if (showServerDashboard) {
        ServerDashboardDialog(
            strings = strings,
            serverBaseUrl = serverBaseUrl,
            networkUrls = networkUrls,
            pairingToken = pairingToken,
            recentServerEvents = recentServerEvents,
            localServerConnectionTestStatus = localServerConnectionTestStatus,
            onTestLocalServerConnection = onTestLocalServerConnection,
            onDismiss = { showServerDashboard = false },
        )
    }
    if (showDanmakuCacheManager) {
        DanmakuCacheManagerDialog(
            strings = strings,
            cacheEntries = dandanplayCacheEntries,
            cacheMaxAgeDays = dandanplaySettings.cacheMaxAgeDays,
            onRefresh = onRefreshDandanplayCacheEntries,
            onDeleteEntry = onDeleteDandanplayCacheEntry,
            onCleanupExpired = onCleanupExpiredDandanplayCaches,
            onDismiss = { showDanmakuCacheManager = false },
        )
    }
}

@Composable
internal fun DanmakuDisplaySettingsCard(
    strings: DesktopStrings,
    settings: DanmakuDisplaySettings,
    onSave: (DanmakuDisplaySettings) -> Unit,
) {
    var visible by remember(settings) { mutableStateOf(settings.visible) }
    var opacityText by remember(settings) { mutableStateOf(settings.opacityPercent.toString()) }
    var fontScaleText by remember(settings) { mutableStateOf(settings.fontScalePercent.toString()) }
    var speedText by remember(settings) { mutableStateOf(settings.speedPercent.toString()) }
    var densityText by remember(settings) { mutableStateOf(settings.densityPercent.toString()) }
    var displayAreaText by remember(settings) { mutableStateOf(settings.displayAreaPercent.toString()) }
    var offsetText by remember(settings) { mutableStateOf(settings.offsetMs.toString()) }
    var keywordFiltersText by remember(settings) { mutableStateOf(settings.keywordFilters.joinToString("\n")) }
    var regexFiltersText by remember(settings) { mutableStateOf(settings.regexFilters.joinToString("\n")) }
    val opacityError = integerRangeError(opacityText, 0..100, "%")
    val fontScaleError = integerRangeError(fontScaleText, 50..200, "%")
    val speedError = integerRangeError(speedText, 25..300, "%")
    val densityError = integerRangeError(densityText, 10..200, "%")
    val displayAreaError = integerRangeError(displayAreaText, 10..100, "%")
    val offsetError = longRangeError(offsetText, -3_600_000L..3_600_000L, "ms")
    val draftSettings = if (
        opacityError == null &&
        fontScaleError == null &&
        speedError == null &&
        densityError == null &&
        displayAreaError == null &&
        offsetError == null
    ) {
        DanmakuDisplaySettings(
            visible = visible,
            opacityPercent = opacityText.toInt(),
            fontScalePercent = fontScaleText.toInt(),
            speedPercent = speedText.toInt(),
            densityPercent = densityText.toInt(),
            displayAreaPercent = displayAreaText.toInt(),
            offsetMs = offsetText.toLong(),
            keywordFilters = keywordFiltersText.toFilterEntries(),
            regexFilters = regexFiltersText.toFilterEntries(),
        )
    } else {
        null
    }
    val isDirty = draftSettings != null && draftSettings != settings

    SectionCard(strings.danmakuDisplaySettingsTitle) {
        Text(
            strings.danmakuDisplaySettingsDescription,
            color = DanmakuColors.TextMuted,
        )
        MetadataRow(strings.visibilityLabel, if (settings.visible) strings.shownLabel else strings.hiddenLabel)
        MetadataRow(strings.opacityLabel, "${settings.opacityPercent}%")
        MetadataRow(strings.fontScaleLabel, "${settings.fontScalePercent}%")
        MetadataRow(strings.speedLabel, "${settings.speedPercent}%")
        MetadataRow(strings.densityLabel, "${settings.densityPercent}%")
        MetadataRow(strings.displayAreaLabel, "${settings.displayAreaPercent}%")
        MetadataRow(strings.offsetLabel, strings.offsetMsValueLabel(settings.offsetMs))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { visible = true },
                enabled = !visible,
            ) {
                Text(strings.showDanmakuAction)
            }
            Button(
                onClick = { visible = false },
                enabled = visible,
            ) {
                Text(strings.hideDanmakuAction)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = opacityText,
                onValueChange = { opacityText = it },
                label = { Text(strings.opacityPercentLabel) },
                isError = opacityError != null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = fontScaleText,
                onValueChange = { fontScaleText = it },
                label = { Text(strings.fontScalePercentLabel) },
                isError = fontScaleError != null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = speedText,
                onValueChange = { speedText = it },
                label = { Text(strings.speedPercentLabel) },
                isError = speedError != null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        SettingsValidationText(listOfNotNull(opacityError, fontScaleError, speedError))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = densityText,
                onValueChange = { densityText = it },
                label = { Text(strings.densityPercentLabel) },
                isError = densityError != null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = displayAreaText,
                onValueChange = { displayAreaText = it },
                label = { Text(strings.displayAreaPercentLabel) },
                isError = displayAreaError != null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = offsetText,
                onValueChange = { offsetText = it },
                label = { Text(strings.offsetMsLabel) },
                isError = offsetError != null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        SettingsValidationText(listOfNotNull(densityError, displayAreaError, offsetError))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = keywordFiltersText,
                onValueChange = { keywordFiltersText = it },
                label = { Text(strings.keywordFiltersLabel) },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = regexFiltersText,
                onValueChange = { regexFiltersText = it },
                label = { Text(strings.regexFiltersLabel) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    draftSettings?.let(onSave)
                },
                enabled = isDirty,
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.saveDanmakuDisplayAction)
            }
            Button(
                onClick = {
                    visible = true
                    opacityText = "100"
                    fontScaleText = "100"
                    speedText = "100"
                    densityText = "100"
                    displayAreaText = "100"
                    offsetText = "0"
                    keywordFiltersText = ""
                    regexFiltersText = ""
                },
            ) {
                Text(strings.resetDraftAction)
            }
        }
    }
}

internal fun String.toFilterEntries(): List<String> =
    lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .toList()

@Composable
internal fun SettingsValidationText(errors: List<String>) {
    if (errors.isEmpty()) {
        return
    }
    Text(
        text = errors.joinToString(separator = "\n"),
        color = DanmakuColors.Warning,
    )
}

@Composable
internal fun SettingsConnectionTestStatusRow(
    strings: DesktopStrings,
    label: String,
    status: SettingsConnectionTestStatus,
) {
    val statusLabel = when (status.outcome) {
        SettingsConnectionTestOutcome.TESTING -> strings.testingStatusLabel
        SettingsConnectionTestOutcome.SUCCESS -> strings.okStatusLabel
        SettingsConnectionTestOutcome.FAILURE -> strings.failedStatusLabel
    }
    val color = when (status.outcome) {
        SettingsConnectionTestOutcome.TESTING -> DanmakuColors.Info
        SettingsConnectionTestOutcome.SUCCESS -> DanmakuColors.Good
        SettingsConnectionTestOutcome.FAILURE -> DanmakuColors.Warning
    }
    val icon = when (status.outcome) {
        SettingsConnectionTestOutcome.TESTING -> Icons.Filled.Refresh
        SettingsConnectionTestOutcome.SUCCESS -> Icons.Filled.CheckCircle
        SettingsConnectionTestOutcome.FAILURE -> Icons.Filled.Warning
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = DanmakuColors.TextMuted,
            modifier = Modifier.width(140.dp),
            maxLines = 1,
        )
        StatusPill(
            text = statusLabel,
            icon = icon,
            active = status.outcome != SettingsConnectionTestOutcome.FAILURE,
            color = color,
        )
        Text(
            text = status.detail,
            color = DanmakuColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun ServerDashboardDialog(
    strings: DesktopStrings,
    serverBaseUrl: String,
    networkUrls: List<String>,
    pairingToken: String,
    recentServerEvents: List<LocalLibraryServerEvent>,
    localServerConnectionTestStatus: SettingsConnectionTestStatus?,
    onTestLocalServerConnection: () -> Unit,
    onDismiss: () -> Unit,
) {
    fun copyToClipboard(value: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(value), null)
    }
    val recentEvents = recentServerEvents.takeLast(10).asReversed()

    AlertDialog(
        modifier = Modifier.width(760.dp),
        onDismissRequest = onDismiss,
        title = { Text(strings.serverDashboardTitle) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.pairingAndLanAccessTitle, fontWeight = FontWeight.Bold)
                    ServerDashboardCopyRow(
                        strings = strings,
                        label = strings.serverBaseUrlLabel,
                        value = serverBaseUrl,
                        onCopy = { copyToClipboard(serverBaseUrl) },
                    )
                    ServerDashboardCopyRow(
                        strings = strings,
                        label = strings.pairingCodeLabel,
                        value = pairingToken,
                        onCopy = { copyToClipboard(pairingToken) },
                    )
                    if (networkUrls.isEmpty()) {
                        MetadataRow(strings.lanUrlsLabel, strings.noLanUrlDetectedLabel, DanmakuColors.TextMuted)
                    } else {
                        networkUrls.forEachIndexed { index, url ->
                            ServerDashboardCopyRow(
                                strings = strings,
                                label = strings.lanUrlNumberedLabel(index + 1),
                                value = url,
                                onCopy = { copyToClipboard(url) },
                            )
                        }
                    }
                    MetadataRow(
                        strings.discoveryLabel,
                        "UDP ${app.danmaku.domain.LanLibraryServerAnnouncement.DEFAULT_DISCOVERY_PORT}",
                    )
                }

                Divider(color = DanmakuColors.SurfaceRaised)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.healthTitle, fontWeight = FontWeight.Bold)
                    localServerConnectionTestStatus?.let {
                        SettingsConnectionTestStatusRow(strings = strings, label = strings.lastTestLabel, status = it)
                    } ?: MetadataRow(strings.lastTestLabel, strings.notCheckedThisSessionLabel, DanmakuColors.TextMuted)
                    MetadataRow(strings.recentRequestsLabel, recentServerEvents.size.toString())
                    MetadataRow(strings.connectedClientsLabel, strings.connectedClientsPlannedText, DanmakuColors.TextMuted)
                    MetadataRow(strings.bandwidthLabel, strings.bandwidthPlannedText, DanmakuColors.TextMuted)
                }

                Divider(color = DanmakuColors.SurfaceRaised)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.recentServerRequestsTitle, fontWeight = FontWeight.Bold)
                    if (recentEvents.isEmpty()) {
                        Text(strings.noServerRequestsText, color = DanmakuColors.TextMuted)
                    } else {
                        recentEvents.forEach { event ->
                            ServerDashboardEventRow(event)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onTestLocalServerConnection) {
                Text(strings.testServerAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.closeAction)
            }
        },
    )
}

@Composable
internal fun ServerDashboardCopyRow(
    strings: DesktopStrings,
    label: String,
    value: String,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, color = DanmakuColors.TextMuted, modifier = Modifier.width(110.dp), maxLines = 1)
        Text(
            value,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LibraryActionButton(
            imageVector = Icons.Filled.ContentCopy,
            label = strings.copyAction,
            onClick = onCopy,
        )
    }
}

@Composable
internal fun ServerDashboardEventRow(event: LocalLibraryServerEvent) {
    val isHealthyStatus = event.status in 200..399
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            event.occurredAtEpochMs.formatEpochTime(),
            color = DanmakuColors.TextMuted,
            modifier = Modifier.width(118.dp),
            maxLines = 1,
        )
        StatusPill(
            text = event.status.toString(),
            icon = if (isHealthyStatus) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            active = isHealthyStatus,
            color = if (isHealthyStatus) DanmakuColors.Good else DanmakuColors.Warning,
        )
        Text(
            "${event.method} ${event.path}",
            modifier = Modifier.weight(1.15f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            event.detail.redactToken(),
            color = DanmakuColors.TextMuted,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun DanmakuCacheManagerDialog(
    strings: DesktopStrings,
    cacheEntries: List<DesktopDandanplayCommentCache>,
    cacheMaxAgeDays: Int,
    onRefresh: () -> Unit,
    onDeleteEntry: (String) -> Unit,
    onCleanupExpired: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedMediaId by remember(cacheEntries) {
        mutableStateOf(cacheEntries.firstOrNull()?.mediaId)
    }
    var pendingDeleteEntry by remember { mutableStateOf<DesktopDandanplayCommentCache?>(null) }
    var confirmCleanupExpired by remember { mutableStateOf(false) }
    val selectedEntry = cacheEntries.firstOrNull { it.mediaId == selectedMediaId }
        ?: cacheEntries.firstOrNull()
    val staleCount = cacheEntries.count { it.isExpiredForCacheManager(cacheMaxAgeDays) }

    AlertDialog(
        modifier = Modifier.width(880.dp),
        onDismissRequest = onDismiss,
        title = { Text(strings.danmakuCacheManagerTitle) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard(
                        title = strings.cachedSummaryTitle,
                        value = cacheEntries.size.toString(),
                        caption = strings.episodesLabel,
                        modifier = Modifier.weight(1f),
                    )
                    SummaryCard(
                        title = strings.expiredSummaryTitle,
                        value = staleCount.toString(),
                        caption = strings.cacheDayRuleCaption(cacheMaxAgeDays),
                        modifier = Modifier.weight(1f),
                    )
                    SummaryCard(
                        title = strings.commentsSummaryTitle,
                        value = cacheEntries.sumOf { it.commentCountForCacheManager() }.toString(),
                        caption = strings.cachedEventsCaption,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1.1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(strings.persistedEntriesTitle, fontWeight = FontWeight.Bold)
                        if (cacheEntries.isEmpty()) {
                            Text(strings.noDandanplayCachesText, color = DanmakuColors.TextMuted)
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 360.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(cacheEntries, key = DesktopDandanplayCommentCache::mediaId) { entry ->
                                    DanmakuCacheEntryRow(
                                        strings = strings,
                                        entry = entry,
                                        selected = selectedEntry?.mediaId == entry.mediaId,
                                        cacheMaxAgeDays = cacheMaxAgeDays,
                                        onSelect = { selectedMediaId = entry.mediaId },
                                    )
                                }
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(strings.selectedCacheTitle, fontWeight = FontWeight.Bold)
                        if (selectedEntry == null) {
                            Text(
                                strings.selectCachePromptText,
                                color = DanmakuColors.TextMuted,
                            )
                        } else {
                            DanmakuCacheEntryDetails(
                                strings = strings,
                                entry = selectedEntry,
                                cacheMaxAgeDays = cacheMaxAgeDays,
                                onDelete = { pendingDeleteEntry = selectedEntry },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onRefresh) {
                Text(strings.refreshAction)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { confirmCleanupExpired = true },
                    enabled = cacheEntries.isNotEmpty(),
                ) {
                    Text(strings.cleanExpiredAction)
                }
                TextButton(onClick = onDismiss) {
                    Text(strings.closeAction)
                }
            }
        },
    )

    pendingDeleteEntry?.let { entry ->
        SettingsConfirmationDialog(
            title = strings.deleteCachedDanmakuTitle,
            text = strings.deleteCachedDanmakuText(entry.displayTitleForCacheManager()),
            confirmLabel = strings.deleteCacheAction,
            cancelLabel = strings.cancelAction,
            onConfirm = { onDeleteEntry(entry.mediaId) },
            onDismiss = { pendingDeleteEntry = null },
        )
    }
    if (confirmCleanupExpired) {
        SettingsConfirmationDialog(
            title = strings.cleanExpiredDanmakuCachesTitle,
            text = strings.cleanExpiredDanmakuCachesText(cacheMaxAgeDays),
            confirmLabel = strings.cleanExpiredAction,
            cancelLabel = strings.cancelAction,
            onConfirm = onCleanupExpired,
            onDismiss = { confirmCleanupExpired = false },
        )
    }
}

@Composable
internal fun DanmakuCacheEntryRow(
    strings: DesktopStrings,
    entry: DesktopDandanplayCommentCache,
    selected: Boolean,
    cacheMaxAgeDays: Int,
    onSelect: () -> Unit,
) {
    val isExpired = entry.isExpiredForCacheManager(cacheMaxAgeDays)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) DanmakuColors.AccentSoft else DanmakuColors.SurfaceRaised.copy(alpha = 0.56f))
            .clickable(onClick = onSelect)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusPill(
            text = if (isExpired) strings.expiredStatusLabel else strings.readyStatusLabel,
            icon = if (isExpired) Icons.Filled.Warning else Icons.Filled.CheckCircle,
            active = !isExpired,
            color = if (isExpired) DanmakuColors.Warning else DanmakuColors.Good,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                entry.displayTitleForCacheManager(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                strings.cacheEntryCommentSummary(
                    entry.commentCountForCacheManager(),
                    entry.fetchedAtEpochMs,
                    entry.fileName,
                ),
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun DanmakuCacheEntryDetails(
    strings: DesktopStrings,
    entry: DesktopDandanplayCommentCache,
    cacheMaxAgeDays: Int,
    onDelete: () -> Unit,
) {
    val isExpired = entry.isExpiredForCacheManager(cacheMaxAgeDays)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetadataRow(
            strings.statusLabel,
            if (isExpired) strings.expiredStatusLabel else strings.readyStatusLabel,
            if (isExpired) DanmakuColors.Warning else DanmakuColors.Good,
        )
        MetadataRow(strings.animeLabel, entry.animeTitle ?: strings.unknownAnimeLabel)
        MetadataRow(strings.episodeLabel, entry.episodeTitle ?: entry.episodeId?.toString() ?: strings.unknownEpisodeLabel)
        MetadataRow(strings.mediaIdLabel, entry.mediaId)
        MetadataRow(strings.fileLabel, entry.fileName)
        MetadataRow(strings.fileSizeLabel, entry.fileSizeBytes.formatLibrarySize())
        MetadataRow(strings.commentsLabel, entry.commentCountForCacheManager().toString())
        MetadataRow(strings.fetchedLabel, entry.fetchedAtEpochMs.formatEpochTime())
        MetadataRow(strings.shiftLabel, entry.shiftSeconds?.let(strings.shiftSecondsLabel) ?: strings.noneLabel)
        entry.renderedAssPath?.let { MetadataRow(strings.assCacheLabel, it) }
        LibraryActionButton(
            imageVector = Icons.Filled.Delete,
            label = strings.deleteCacheAction,
            onClick = onDelete,
        )
    }
}

@Composable
internal fun SettingsConfirmationDialog(
    title: String,
    text: String,
    confirmLabel: String,
    cancelLabel: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelLabel)
            }
        },
    )
}

internal fun integerRangeError(
    text: String,
    range: IntRange,
    unit: String,
): String? {
    val value = text.trim().toIntOrNull()
        ?: return "Enter a whole number from ${range.first} to ${range.last} $unit."
    return if (value in range) {
        null
    } else {
        "Enter a value from ${range.first} to ${range.last} $unit."
    }
}

internal fun longRangeError(
    text: String,
    range: LongRange,
    unit: String,
): String? {
    val value = text.trim().toLongOrNull()
        ?: return "Enter a whole number from ${range.first} to ${range.last} $unit."
    return if (value in range) {
        null
    } else {
        "Enter a value from ${range.first} to ${range.last} $unit."
    }
}

internal fun httpUrlError(
    text: String,
    label: String,
): String? {
    val value = text.trim()
    if (value.isEmpty()) {
        return "$label is required."
    }
    val uri = runCatching { java.net.URI(value) }.getOrNull()
        ?: return "$label must be a valid HTTP or HTTPS URL."
    val scheme = uri.scheme?.lowercase()
    return when {
        scheme != "http" && scheme != "https" -> "$label must use HTTP or HTTPS."
        uri.host.isNullOrBlank() -> "$label must include a host."
        else -> null
    }
}

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
        MetadataRow("dandanplay", settings.statusText)
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
                strings.currentAuthLabel(authenticationMode.name.lowercase()),
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
        MetadataRow("MyAnimeList", settings.myAnimeListStatusText)
        MetadataRow("Bangumi", settings.bangumiStatusText)
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

