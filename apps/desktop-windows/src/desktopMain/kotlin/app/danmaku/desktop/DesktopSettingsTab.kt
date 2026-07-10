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
import app.danmaku.server.LocalLibraryServerEvent
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
                    MetadataRow("MyAnimeList", externalAnimeProviderSettings.myAnimeListStatusLabel(strings))
                    MetadataRow("Bangumi", externalAnimeProviderSettings.bangumiStatusLabel(strings))
                    MetadataRow("dandanplay", dandanplaySettings.statusLabel(strings))
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
                    MetadataRow("MyAnimeList", externalAnimeProviderSettings.myAnimeListStatusLabel(strings))
                    MetadataRow("Bangumi", externalAnimeProviderSettings.bangumiStatusLabel(strings))
                    MetadataRow("dandanplay", dandanplaySettings.statusLabel(strings))
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
