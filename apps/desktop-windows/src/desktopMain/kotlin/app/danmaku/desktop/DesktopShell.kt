package app.danmaku.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeTrackingPlan
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackStatus
import app.danmaku.domain.externalAnimeTrackingPlan
import app.danmaku.domain.groupedSeries
import app.danmaku.domain.nextItem
import app.danmaku.domain.previousItem
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.jvm.JvmLanLibraryClient
import app.danmaku.server.LocalLibraryServerEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Window as AwtWindow
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

@Composable
internal fun DesktopShell(
    awtWindow: AwtWindow,
    windowState: WindowState,
    launchOptions: DesktopLaunchOptions = DesktopLaunchOptions(),
    onRequestExit: () -> Unit = {},
) {
    val hostPlatform = remember { DesktopHostPlatform.current() }
    val selectionStore = remember { LocalLibrarySelectionStore.default() }
    val catalogStore = remember { DesktopLibraryCatalogStore.default() }
    val playbackPreferencesStore = remember(catalogStore) {
        DesktopPlaybackPreferencesStore(catalogStore)
    }
    val rootRegistry = remember(catalogStore) { DesktopLibraryRootRegistry(catalogStore) }
    val rootScanner = remember(catalogStore, rootRegistry) {
        DesktopLibraryRootScanner(catalogStore, rootRegistry)
    }
    val dandanplayCredentialStore = remember(catalogStore) {
        DandanplayCredentialStore(catalogStore)
    }
    val externalAnimeCredentialStore = remember(catalogStore) {
        ExternalAnimeCredentialStore(catalogStore)
    }
    val posterCache = remember { DesktopAnimePosterCache.default() }
    val animeMetadataResolver = remember(catalogStore, dandanplayCredentialStore, posterCache) {
        DesktopAnimeMetadataResolver(
            catalogStore = catalogStore,
            loadConnection = dandanplayCredentialStore::loadConnection,
            posterCache = posterCache,
        )
    }
    val settingsState = rememberDesktopShellSettingsState(
        playbackPreferencesStore = playbackPreferencesStore,
        dandanplayCredentialStore = dandanplayCredentialStore,
        externalAnimeCredentialStore = externalAnimeCredentialStore,
        catalogStore = catalogStore,
    )
    val myAnimeListOAuthService = remember(externalAnimeCredentialStore) {
        MyAnimeListOAuthService(externalAnimeCredentialStore)
    }
    val localPlaybackPreparer = remember(catalogStore) {
        DesktopLocalPlaybackPreparer(catalogStore)
    }
    val dandanplayDanmakuResolver = remember(dandanplayCredentialStore) {
        DesktopDandanplayDanmakuResolver(
            loadConnection = dandanplayCredentialStore::loadConnection,
            cacheMaxAgeDays = { dandanplayCredentialStore.loadSettings().cacheMaxAgeDays },
            danmakuSettings = { settingsState.danmakuSettings },
            cacheStore = catalogStore,
        )
    }
    val lanProgressSync = remember {
        LanPlaybackProgressSync(
            libraryClient = JvmLanLibraryClient(),
            currentTimeMillis = System::currentTimeMillis,
        )
    }
    val scope = rememberCoroutineScope()
    val diagnosticsState = rememberDesktopShellDiagnosticsState()
    val mpvCommandLog = diagnosticsState.mpvCommandLog
    val diagnosticLog = diagnosticsState.diagnosticLog
    val serverEvents = diagnosticsState.serverEvents
    val diagnosticFileLog = diagnosticsState.fileLog
    fun appendDiagnostic(category: String, message: String) =
        diagnosticsState.appendDiagnostic(category, message)
    val myAnimeListOAuthCallbackRuntime = remember(myAnimeListOAuthService, scope) {
        DesktopMyAnimeListOAuthCallbackRuntime.start(
            oauthService = myAnimeListOAuthService,
            onSettingsUpdated = { updatedSettings ->
                scope.launch {
                    settingsState.externalAnimeProviderSettings = updatedSettings
                }
            },
            onDiagnostic = { message ->
                scope.launch {
                    appendDiagnostic("settings", message)
                }
            },
        )
    }

    var mpvVideoWindowId by remember { mutableStateOf<Long?>(null) }
    val requiresNativeVideoHost = hostPlatform.requiresEmbeddedMpvVideoHost
    val nativeVideoHostReady = !requiresNativeVideoHost || mpvVideoWindowId != null
    val mpvControlScriptPath: Result<Path?> = remember(hostPlatform, mpvVideoWindowId) {
        if (hostPlatform == DesktopHostPlatform.WINDOWS) {
            runCatching { DesktopMpvScriptResources.installDanmakuOscScript() }
        } else {
            Result.success(null)
        }
    }
    LaunchedEffect(mpvControlScriptPath) {
        mpvControlScriptPath
            .onSuccess { scriptPath ->
                if (scriptPath != null) {
                    val sizeText = runCatching { Files.size(scriptPath) }
                        .map { " ($it bytes)" }
                        .getOrDefault("")
                    appendDiagnostic("mpv", "Installed custom mpv OSC script: $scriptPath$sizeText")
                }
            }
            .onFailure { error ->
                appendDiagnostic("mpv", "Custom mpv OSC script unavailable: ${error.message}")
            }
    }
    val videoHostStatus = if (requiresNativeVideoHost) {
        if (mpvVideoWindowId == null) {
            "waiting for native window"
        } else {
            "attached"
        }
    } else {
        "${hostPlatform.displayName} mpv-managed output"
    }
    val mpvNativeOptions = remember(hostPlatform, mpvVideoWindowId, diagnosticFileLog.mpvLogPath, mpvControlScriptPath) {
        buildMap {
            put("config", "no")
            put("terminal", "no")
            put("msg-level", "all=v")
            put("log-file", diagnosticFileLog.mpvLogPath.toAbsolutePath().normalize().toString())
            if (hostPlatform == DesktopHostPlatform.WINDOWS) {
                mpvVideoWindowId
                    ?.let { windowId ->
                        DesktopMpvWindowsOptions.forWindowId(
                            windowId = windowId,
                            controlScriptPath = mpvControlScriptPath.getOrNull(),
                        )
                    }
                    ?.let(::putAll)
            }
        }
    }
    val mpvRuntime = remember(hostPlatform, nativeVideoHostReady, mpvNativeOptions) {
        if (!nativeVideoHostReady) {
            DesktopMpvCommandExecutorRuntime(
                executor = DesktopMpvCommandExecutor { command ->
                    mpvCommandLog += command
                    appendDiagnostic("mpv", "command ${command.args.joinToString(separator = " ")}")
                },
                mode = DesktopMpvCommandExecutorMode.COMMAND_LOG_ONLY,
                statusMessage = "Native mpv video host is not ready on ${hostPlatform.displayName}. " +
                    "Command-log-only mode is active.",
            )
        } else {
            DesktopMpvCommandExecutorRuntimeFactory(platform = hostPlatform).create(
                nativeOptions = mpvNativeOptions,
            ) { command ->
                mpvCommandLog += command
                appendDiagnostic("mpv", "command ${command.args.joinToString(separator = " ")}")
            }
        }
    }
    val playbackController = remember(mpvRuntime) {
        DesktopMpvPlaybackController(
            commandExecutor = mpvRuntime.executor,
            propertyReader = mpvRuntime.propertyReader,
            initialSnapshot = PlaybackSnapshot(
                playbackRate = settingsState.playbackPreferences.playbackRate,
                volumePercent = settingsState.playbackPreferences.volumePercent,
            ),
            initialVideoAspectMode = settingsState.playbackPreferences.videoAspectMode,
        )
    }
    LaunchedEffect(mpvRuntime, mpvVideoWindowId) {
        appendDiagnostic(
            "mpv",
            if (requiresNativeVideoHost && mpvVideoWindowId == null) {
                "Created mpv runtime without video window on ${hostPlatform.displayName}"
            } else if (requiresNativeVideoHost) {
                "Created mpv runtime for native window $mpvVideoWindowId on ${hostPlatform.displayName}"
            } else {
                "Created mpv runtime with ${hostPlatform.displayName} mpv-managed output"
            },
        )
        appendDiagnostic("mpv", "Runtime mode: ${mpvRuntime.mode}; ${mpvRuntime.statusMessage}")
        appendDiagnostic("diagnostics", "App log: ${diagnosticFileLog.appLogPath}")
        appendDiagnostic("diagnostics", "mpv log: ${diagnosticFileLog.mpvLogPath}")
    }
    val mpvOscActions = remember(hostPlatform, mpvRuntime) {
        DesktopShellMpvOscActions(
            hostPlatform = hostPlatform,
            executor = mpvRuntime.executor,
            appendDiagnostic = ::appendDiagnostic,
        )
    }
    var overlayStatus by remember { mutableStateOf("Danmaku overlay: waiting for matched comments") }
    val playbackSession = remember(playbackController, mpvRuntime) {
        DesktopPlaybackSession(
            controller = playbackController,
            afterLoad = { request ->
                if (request.subtitles.any(DesktopPlaybackSubtitle::isDanmakuOverlay)) {
                    overlayStatus = "Matched danmaku overlay: attached to mpv video"
                    appendDiagnostic("overlay", "Matched/manual danmaku overlay attached for ${request.label}")
                } else {
                    overlayStatus = "No matched danmaku overlay attached"
                    appendDiagnostic("overlay", "Loaded ${request.label} without danmaku overlay")
                }
            },
            attachSubtitle = { subtitle ->
                runCatching {
                    mpvRuntime.executor.execute(
                        DesktopMpvCommandPlanner.addSubtitle(subtitle.source, subtitle.label),
                    )
                }.onSuccess {
                    appendDiagnostic("subtitle", "Attached subtitle track ${subtitle.label}: ${subtitle.source.redactToken()}")
                }.onFailure { error ->
                    appendDiagnostic("subtitle", "Subtitle attach failed for ${subtitle.label}: ${error.message}")
                }
            },
        )
    }
    val legacySelectedLibraryRoot = remember { selectionStore.load() }
    val libraryState = rememberDesktopShellLibraryState()
    var startupProgress by remember { mutableStateOf(DesktopStartupProgress.Initial) }
    var startupLoadComplete by remember { mutableStateOf(false) }
    var startupPublishHandled by remember { mutableStateOf(false) }
    var playbackSnapshot by remember(playbackController) { mutableStateOf(playbackController.snapshot()) }
    val desktopWindowState = rememberDesktopShellWindowState(
        awtWindow = awtWindow,
        windowState = windowState,
        hostPlatform = hostPlatform,
        mpvRuntime = mpvRuntime,
        appendDiagnostic = ::appendDiagnostic,
    )
    val isFullscreen = desktopWindowState.isFullscreen
    var videoAspectMode by remember(playbackController) { mutableStateOf(playbackController.videoAspectMode) }
    var displayIndexedLibrary by remember { mutableStateOf<IndexedLocalLibrary?>(null) }
    val originalSeriesTitleByMediaId = remember(libraryState.indexedLibrary) {
        libraryState.indexedLibrary?.catalog?.items?.associate { item -> item.id to item.seriesTitle }.orEmpty()
    }
    var seriesPosterById by remember { mutableStateOf<Map<String, Path?>>(emptyMap()) }
    var externalAnimeMappings by remember { mutableStateOf<List<ExternalAnimeMapping>>(emptyList()) }
    var externalAnimeItemMappingsByMediaId by remember {
        mutableStateOf<Map<String, List<DesktopExternalAnimeItemMapping>>>(emptyMap())
    }
    fun startRustSidecar(libraryRoots: List<Path>): RustServerSidecarRuntime =
        RustServerSidecarRuntime.start(
            launchOptions = launchOptions,
            libraryRoots = libraryRoots,
            onStatusChanged = { status ->
                scope.launch {
                    appendDiagnostic("rust-sidecar", status.message)
                    if (status is RustServerSidecarStatus.Failed) {
                        libraryState.libraryError = status.message
                    }
                }
            },
        )

    var rustSidecarRuntime by remember(launchOptions.rustSidecar, launchOptions.serverPort, launchOptions.serverPairingToken, launchOptions.webAssetsRoot) {
        mutableStateOf(
            if (!launchOptions.rustSidecar.enabled) {
                null
            } else {
                val sidecarRoots = buildList {
                    addAll(rootRegistry.loadRoots().filter { it.state == DesktopLibraryRootState.AVAILABLE }.map(DesktopLibraryRoot::normalizedPath))
                    launchOptions.qaLibraryRoot?.let(::add)
                }
                startRustSidecar(sidecarRoots)
            },
        )
    }
    val effectiveRemoteClient = rustSidecarRuntime?.remoteClientOptions ?: launchOptions.remoteClient
    val serverBaseUrl = effectiveRemoteClient?.normalizedServerUrl.orEmpty()
    val serverPairingToken = effectiveRemoteClient?.pairingToken.orEmpty()
    val settingsActions = remember(serverBaseUrl, serverPairingToken, myAnimeListOAuthCallbackRuntime, settingsState, libraryState) {
        DesktopShellSettingsActions(
            scope = scope,
            playbackPreferencesStore = playbackPreferencesStore,
            dandanplayCredentialStore = dandanplayCredentialStore,
            externalAnimeCredentialStore = externalAnimeCredentialStore,
            myAnimeListOAuthService = myAnimeListOAuthService,
            dandanplayDanmakuResolver = dandanplayDanmakuResolver,
            catalogStore = catalogStore,
            settingsState = settingsState,
            libraryState = libraryState,
            serverBaseUrl = { serverBaseUrl },
            oauthCallbackBaseUrl = { myAnimeListOAuthCallbackRuntime.baseUrl },
            pairingToken = { serverPairingToken },
            appendDiagnostic = ::appendDiagnostic,
            updateOverlayStatus = { overlayStatus = it },
        )
    }
    val libraryActions = remember(rustSidecarRuntime, settingsState, libraryState) {
        DesktopShellLibraryActions(
            scope = scope,
            catalogStore = catalogStore,
            selectionStore = selectionStore,
            rootRegistry = rootRegistry,
            rootScanner = rootScanner,
            animeMetadataResolver = animeMetadataResolver,
            dandanplayDanmakuResolver = dandanplayDanmakuResolver,
            externalAnimeCredentialStore = externalAnimeCredentialStore,
            posterCache = posterCache,
            settingsState = settingsState,
            libraryState = libraryState,
            publishLibrary = { library ->
                val sidecarRoots = buildList {
                    addAll(
                        rootRegistry.loadRoots()
                            .filter { it.state == DesktopLibraryRootState.AVAILABLE }
                            .map(DesktopLibraryRoot::normalizedPath),
                    )
                    launchOptions.qaLibraryRoot?.let(::add)
                }
                scope.launch {
                    val previous = rustSidecarRuntime
                    if (previous == null) {
                        return@launch
                    }
                    appendDiagnostic(
                        "rust-sidecar",
                        "Restarting Rust sidecar after local scan of ${library.catalog.items.size} item(s).",
                    )
                    runCatching {
                        withContext(Dispatchers.IO) {
                            previous.close()
                            startRustSidecar(sidecarRoots)
                        }
                    }.onSuccess { replacement ->
                        rustSidecarRuntime = replacement
                        libraryState.libraryError = null
                    }.onFailure { error ->
                        rustSidecarRuntime = null
                        libraryState.libraryError = error.message
                        appendDiagnostic(
                            "rust-sidecar",
                            "Rust sidecar restart after local scan failed: ${error.message ?: error::class.simpleName}",
                        )
                    }
                }
            },
            appendDiagnostic = ::appendDiagnostic,
        )
    }
    val downloadActions = remember(libraryState) {
        DesktopShellDownloadActions(
            scope = scope,
            catalogStore = catalogStore,
            libraryState = libraryState,
            appendDiagnostic = ::appendDiagnostic,
        )
    }
    val networkUrls = remember(rustSidecarRuntime) {
        rustSidecarRuntime?.localNetworkUrls().orEmpty()
    }

    LaunchedEffect(playbackController) {
        playbackController.dispatch(PlaybackCommand.SetPlaybackRate(settingsState.playbackPreferences.playbackRate))
        playbackController.dispatch(PlaybackCommand.SetVolume(settingsState.playbackPreferences.volumePercent))
        playbackController.setVideoAspectMode(settingsState.playbackPreferences.videoAspectMode)
        playbackSnapshot = playbackController.snapshot()
        appendDiagnostic(
            "settings",
            "Applied playback defaults: rate=${settingsState.playbackPreferences.playbackRate}x, " +
                "volume=${settingsState.playbackPreferences.volumePercent}%, aspect=${settingsState.playbackPreferences.videoAspectMode.label}",
        )
    }

    LaunchedEffect(startupLoadComplete, libraryState.indexedLibrary, settingsState.dandanplaySettings.isFetchEnabled) {
        if (startupLoadComplete && libraryState.indexedLibrary != null && settingsState.dandanplaySettings.isFetchEnabled) {
            appendDiagnostic(
                "metadata",
                "Deferred automatic missing poster refresh at startup; use Refresh metadata or rescan to fetch posters.",
            )
        }
    }

    LaunchedEffect(Unit) {
        libraryActions.cleanupLegacySeriesAnimeMappings()
    }

    val navigationState = rememberDesktopShellNavigationState(
        catalogStore = catalogStore,
        scope = scope,
        appendDiagnostic = ::appendDiagnostic,
        initialLanguage = launchOptions.initialLanguage,
        initialTab = launchOptions.initialTab,
    )
    val playbackState = rememberDesktopShellPlaybackState(catalogStore)
    val playbackActions = remember(playbackController, playbackSession, navigationState, playbackState, settingsState, libraryState) {
        DesktopShellPlaybackActions(
            scope = scope,
            catalogStore = catalogStore,
            playbackPreferencesStore = playbackPreferencesStore,
            lanProgressSync = lanProgressSync,
            settingsState = settingsState,
            libraryState = libraryState,
            playbackState = playbackState,
            playbackSession = playbackSession,
            playbackController = playbackController,
            hostDisplayName = hostPlatform.displayName,
            requiresNativeVideoHost = requiresNativeVideoHost,
            selectMediaFile = ::selectMediaFile,
            getPlaybackSnapshot = { playbackSnapshot },
            setPlaybackSnapshot = { playbackSnapshot = it },
            updateVideoAspectMode = { videoAspectMode = it },
            selectPlaybackTab = { navigationState.selectedTab = DesktopShellTab.PLAYBACK },
            appendDiagnostic = ::appendDiagnostic,
        )
    }
    val desktopStrings = rememberDesktopResourceStrings(navigationState.desktopLanguage)
    val localPlaybackActions = remember(playbackActions, settingsState, libraryState, desktopStrings) {
        DesktopShellLocalPlaybackActions(
            scope = scope,
            localPlaybackPreparer = localPlaybackPreparer,
            dandanplayDanmakuResolver = dandanplayDanmakuResolver,
            animeMetadataResolver = animeMetadataResolver,
            settingsState = settingsState,
            libraryState = libraryState,
            queuePlaybackUntilHostReady = playbackActions::queuePlaybackUntilHostReady,
            selectDanmakuFile = ::selectDanmakuFile,
            appendDiagnostic = ::appendDiagnostic,
            currentStrings = { desktopStrings },
        )
    }

    LaunchedEffect(catalogStore, rootRegistry, legacySelectedLibraryRoot) {
        val timings = mutableListOf<DesktopStartupTiming>()

        suspend fun <T> loadStep(
            stage: String,
            detail: String,
            progress: Float,
            block: () -> T,
        ): T {
            startupProgress = DesktopStartupProgress(
                stage = stage,
                detail = detail,
                progress = progress,
            )
            val startedAtNanos = System.nanoTime()
            return withContext(Dispatchers.IO) { block() }
                .also {
                    timings += DesktopStartupTiming(
                        stage = stage,
                        elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000,
                    )
                }
        }

        try {
            val roots = loadStep(
                stage = "Loading library roots",
                detail = "Reading saved local folders",
                progress = 0.14f,
            ) {
                rootRegistry.loadRoots()
            }
            val cachedLibrary = loadStep(
                stage = "Loading cached catalog",
                detail = if (roots.isEmpty()) {
                    "Checking the last selected library"
                } else {
                    "Opening ${roots.size} registered library root(s)"
                },
                progress = 0.34f,
            ) {
                if (roots.isNotEmpty()) {
                    catalogStore.loadRegisteredLibrary()
                } else {
                    legacySelectedLibraryRoot?.let(catalogStore::load)
                }
            }
            val playbackProgresses = loadStep(
                stage = "Loading watch progress",
                detail = "Restoring continue-watching positions",
                progress = 0.50f,
            ) {
                catalogStore.loadPlaybackProgress()
            }
            val favoriteIds = loadStep(
                stage = "Loading local list state",
                detail = "Restoring favorites, scores, and quality decisions",
                progress = 0.64f,
            ) {
                catalogStore.loadFavoriteMediaIds()
            }
            val localListEntries = loadStep(
                stage = "Loading local list state",
                detail = "Restoring local anime list entries",
                progress = 0.72f,
            ) {
                catalogStore.loadLocalAnimeListEntries()
            }
            val qualityDecisions = loadStep(
                stage = "Loading quality state",
                detail = "Restoring ignored and resolved library issues",
                progress = 0.80f,
            ) {
                catalogStore.loadLibraryQualityIssueDecisions()
            }
            val downloads = loadStep(
                stage = "Loading downloads",
                detail = "Restoring queued download records",
                progress = 0.86f,
            ) {
                catalogStore.loadDownloads()
            }
            val syncFailures = loadStep(
                stage = "Loading external sync",
                detail = "Restoring provider failures and readback entries",
                progress = 0.92f,
            ) {
                catalogStore.loadExternalAnimeSyncFailures()
            }
            val externalEntries = loadStep(
                stage = "Loading external sync",
                detail = "Restoring provider list readback entries",
                progress = 0.96f,
            ) {
                catalogStore.loadExternalAnimeListEntries()
            }

            libraryState.applySnapshot(
                DesktopShellLibrarySnapshot(
                    registeredRoots = roots,
                    indexedLibrary = cachedLibrary,
                    playbackProgresses = playbackProgresses,
                    favoriteMediaIds = favoriteIds,
                    localAnimeListEntries = localListEntries,
                    libraryQualityIssueDecisions = qualityDecisions,
                    downloadQueueItems = downloads,
                    externalAnimeSyncFailures = syncFailures,
                    externalAnimeListEntries = externalEntries,
                ),
            )
            startupLoadComplete = true
            val itemCount = cachedLibrary?.catalog?.items?.size ?: 0
            val totalMillis = timings.sumOf(DesktopStartupTiming::elapsedMillis)
            appendDiagnostic(
                "startup",
                "Loaded persisted desktop state in ${totalMillis}ms; " +
                    "items=$itemCount, roots=${roots.size}, timings=" +
                    timings.joinToString { timing -> "${timing.stage}=${timing.elapsedMillis}ms" },
            )
            startupProgress = DesktopStartupProgress(
                stage = "Ready",
                detail = "$itemCount episode${if (itemCount == 1) "" else "s"} loaded in ${totalMillis}ms",
                progress = 1f,
            )
            delay(450)
            startupProgress = DesktopStartupProgress.Hidden
        } catch (error: Throwable) {
            val message = error.message ?: error.javaClass.simpleName
            libraryState.libraryError = "Startup load failed: $message"
            startupLoadComplete = true
            appendDiagnostic("startup", "Persisted startup load failed: $message")
            startupProgress = DesktopStartupProgress(
                stage = "Library cache unavailable",
                detail = message,
                progress = 1f,
            )
            delay(1_200)
            startupProgress = DesktopStartupProgress.Hidden
        }
    }

    LaunchedEffect(libraryState.indexedLibrary, libraryState.libraryMetadataVersion, animeMetadataResolver) {
        val library = libraryState.indexedLibrary
        if (library == null) {
            displayIndexedLibrary = null
            return@LaunchedEffect
        }
        displayIndexedLibrary = library
        val startedAtNanos = System.nanoTime()
        val enrichedLibrary = withContext(Dispatchers.IO) {
            library.withExternalAnimeMetadata(animeMetadataResolver)
        }
        if (libraryState.indexedLibrary === library) {
            displayIndexedLibrary = enrichedLibrary
            appendDiagnostic(
                "startup",
                "Prepared cached metadata presentation in ${(System.nanoTime() - startedAtNanos) / 1_000_000}ms",
            )
        }
    }

    LaunchedEffect(displayIndexedLibrary, libraryState.libraryMetadataVersion, animeMetadataResolver) {
        val library = displayIndexedLibrary
        if (library == null) {
            seriesPosterById = emptyMap()
            return@LaunchedEffect
        }
        val startedAtNanos = System.nanoTime()
        val posters = withContext(Dispatchers.IO) {
            loadSeriesPosterById(library, animeMetadataResolver)
        }
        if (displayIndexedLibrary === library) {
            seriesPosterById = posters
            appendDiagnostic(
                "startup",
                "Loaded ${posters.size} series poster reference(s) in ${(System.nanoTime() - startedAtNanos) / 1_000_000}ms",
            )
        }
    }

    LaunchedEffect(displayIndexedLibrary, libraryState.libraryMetadataVersion, catalogStore) {
        val library = displayIndexedLibrary
        if (library == null) {
            externalAnimeMappings = emptyList()
            externalAnimeItemMappingsByMediaId = emptyMap()
            return@LaunchedEffect
        }
        val startedAtNanos = System.nanoTime()
        val mappings = withContext(Dispatchers.IO) {
            library.catalog.groupedSeries()
                .flatMap { series -> catalogStore.loadExternalAnimeMappings(series.id) }
        }
        val itemMappings = withContext(Dispatchers.IO) {
            library.catalog.items
                .associate { item -> item.id to catalogStore.loadExternalAnimeItemMappings(item.id) }
        }
        if (displayIndexedLibrary === library) {
            externalAnimeMappings = mappings
            externalAnimeItemMappingsByMediaId = itemMappings
            appendDiagnostic(
                "startup",
                "Loaded ${mappings.size} series mapping(s) and ${itemMappings.size} item mapping bucket(s) " +
                    "in ${(System.nanoTime() - startedAtNanos) / 1_000_000}ms",
            )
        }
    }
    fun triggerPrimaryPageAction(): Boolean {
        return when (navigationState.selectedTab) {
            DesktopShellTab.HOME,
            DesktopShellTab.MEDIA_LIBRARY -> {
                if (libraryState.registeredRoots.isEmpty() || libraryState.isIndexing) {
                    false
                } else {
                    appendDiagnostic("shell", "Primary shortcut requested library rescan from ${desktopStrings.tabTitle(navigationState.selectedTab)}")
                    libraryActions.rescanRegisteredRoots()
                    true
                }
            }
            DesktopShellTab.DOWNLOADS -> {
                downloadActions.refreshQueue()
                true
            }
            DesktopShellTab.PLAYBACK -> {
                if (playbackSnapshot.source == null || playbackSnapshot.status == PlaybackStatus.LOADING) {
                    false
                } else if (playbackSnapshot.status == PlaybackStatus.PLAYING) {
                    playbackActions.pauseActivePlaybackAndPersist()
                    true
                } else {
                    playbackActions.playActivePlayback()
                    true
                }
            }
            DesktopShellTab.TRACKING -> {
                val plan = libraryState.indexedLibrary
                    ?.catalog
                    ?.externalAnimeTrackingPlan(
                        mappings = externalAnimeMappings,
                        progresses = libraryState.playbackProgresses,
                        externalEntries = libraryState.externalAnimeListEntries,
                        failures = libraryState.externalAnimeSyncFailures,
                    )
                    ?: return false
                if (libraryState.isExternalAnimeSyncing || plan.updates.isEmpty()) {
                    false
                } else {
                    appendDiagnostic("external-sync", "Primary shortcut started ${plan.updates.size} tracking update(s)")
                    libraryActions.syncExternalAnimePlan(plan)
                    true
                }
            }
            DesktopShellTab.PROFILE -> false
        }
    }

    fun handleDesktopShellShortcut(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) {
            return false
        }
        val ctrlOrMetaPressed = event.isCtrlPressed || event.isMetaPressed
        if (!ctrlOrMetaPressed || event.isAltPressed || event.isShiftPressed) {
            return false
        }
        return when (event.key) {
            Key.K -> {
                if (!isFullscreen && navigationState.selectedTab != DesktopShellTab.PLAYBACK) {
                    navigationState.globalSearchFocusRequester.requestFocus()
                    true
                } else {
                    false
                }
            }
            Key.Enter -> triggerPrimaryPageAction()
            Key.R -> {
                if (libraryState.registeredRoots.isNotEmpty() && !libraryState.isIndexing) {
                    appendDiagnostic("shell", "Shortcut requested library rescan")
                    libraryActions.rescanRegisteredRoots()
                    true
                } else {
                    false
                }
            }
            Key.One -> navigationState.selectTabFromShortcut(DesktopShellTab.HOME)
            Key.Two -> navigationState.selectTabFromShortcut(DesktopShellTab.MEDIA_LIBRARY)
            Key.Three -> navigationState.selectTabFromShortcut(DesktopShellTab.DOWNLOADS)
            Key.Four -> navigationState.selectTabFromShortcut(DesktopShellTab.PLAYBACK)
            Key.Five -> navigationState.selectTabFromShortcut(DesktopShellTab.TRACKING)
            Key.Six -> navigationState.selectTabFromShortcut(DesktopShellTab.PROFILE)
            else -> false
        }
    }

    LaunchedEffect(playbackState.activeProgressMediaId, playbackState.activeProgressTarget, libraryState.indexedLibrary?.catalog) {
        val mediaId = playbackState.activeProgressMediaId ?: return@LaunchedEffect
        if (playbackState.activeProgressTarget != null) {
            return@LaunchedEffect
        }
        val activeItem = libraryState.indexedLibrary
            ?.catalog
            ?.items
            ?.firstOrNull { item -> item.id == mediaId }
            ?: return@LaunchedEffect
        localPlaybackActions.inspectCachedDandanplay(activeItem)
    }

    LaunchedEffect(startupLoadComplete, libraryState.indexedLibrary, libraryState.registeredRoots, launchOptions.qaLibraryRoot) {
        if (!startupLoadComplete || startupPublishHandled) {
            return@LaunchedEffect
        }
        startupPublishHandled = true
        val cachedLibrary = libraryState.indexedLibrary
        when {
            launchOptions.qaLibraryRoot != null -> libraryActions.registerAndScanUserRoot(launchOptions.qaLibraryRoot)
            libraryState.registeredRoots.isNotEmpty() && cachedLibrary?.catalog?.items?.isNotEmpty() == true -> {
                appendDiagnostic(
                    "startup",
                    "Skipped automatic startup rescan because cached catalog is available; use Refresh to rescan registered roots.",
                )
            }
            libraryState.registeredRoots.isNotEmpty() -> libraryActions.rescanRegisteredRoots()
            else -> legacySelectedLibraryRoot?.let(libraryActions::registerAndScanUserRoot)
        }
    }

    LaunchedEffect(launchOptions.smokePlayback) {
        val smokePlayback = launchOptions.smokePlayback ?: return@LaunchedEffect
        if (playbackState.smokePlaybackQueued) {
            return@LaunchedEffect
        }
        playbackState.smokePlaybackQueued = true
        playbackActions.queueSmokePlayback(smokePlayback)
    }

    LaunchedEffect(launchOptions.qaSidecarAutoplayFirst, rustSidecarRuntime) {
        val sidecar = rustSidecarRuntime ?: return@LaunchedEffect
        if (!launchOptions.qaSidecarAutoplayFirst || playbackState.sidecarQaAutoplayQueued) {
            return@LaunchedEffect
        }
        playbackState.sidecarQaAutoplayQueued = true
        appendDiagnostic("smoke", "QA sidecar autoplay fetching catalog from ${sidecar.baseUrl}")
        runCatching {
            withContext(Dispatchers.IO) {
                val client = JvmLanLibraryClient()
                val pairingToken = sidecar.remoteClientOptions.pairingToken
                val catalog = LanLibraryConnectionSession(client).fetchCatalog(sidecar.baseUrl, pairingToken)
                val item = catalog.items.firstOrNull()
                    ?: error("Rust sidecar catalog is empty.")
                LanPlaybackPreparer(client).prepare(
                    baseUrl = sidecar.baseUrl,
                    pairingToken = pairingToken,
                    item = item,
                    resumePositionMs = null,
                )
            }
        }.onSuccess { preparation ->
            appendDiagnostic(
                "smoke",
                "QA sidecar autoplay loading ${preparation.item.id}; stream=${preparation.source.url.redactToken()}",
            )
            playbackActions.loadPreparedRemotePlayback(preparation)
        }.onFailure { error ->
            appendDiagnostic("smoke", "QA sidecar autoplay failed: ${error.message ?: error::class.simpleName}")
            libraryState.libraryError = error.message ?: "QA sidecar autoplay failed."
        }
    }

    DesktopShellQaScreenshotEffect(
        awtWindow = awtWindow,
        launchOptions = launchOptions,
        navigationState = navigationState,
        onRequestExit = onRequestExit,
        appendDiagnostic = ::appendDiagnostic,
    )

    LaunchedEffect(navigationState.selectedTab, nativeVideoHostReady, playbackState.pendingPlaybackRequest, playbackSession) {
        val request = playbackState.pendingPlaybackRequest ?: return@LaunchedEffect
        if (navigationState.selectedTab != DesktopShellTab.PLAYBACK || !nativeVideoHostReady) {
            return@LaunchedEffect
        }
        appendDiagnostic(
            "playback",
            if (requiresNativeVideoHost) {
                "Native video host ready; waiting ${PLAYBACK_HOST_SETTLE_DELAY_MS}ms before queued load: ${request.label}"
            } else {
                "Native mpv runtime ready; loading queued playback: ${request.label}"
            },
        )
        if (requiresNativeVideoHost) {
            delay(PLAYBACK_HOST_SETTLE_DELAY_MS)
        }
        if (playbackState.pendingPlaybackRequest != request || navigationState.selectedTab != DesktopShellTab.PLAYBACK || !nativeVideoHostReady) {
            appendDiagnostic("playback", "Queued playback changed before load; skipping stale request: ${request.label}")
            return@LaunchedEffect
        }
        appendDiagnostic("playback", "Loading queued playback after host settle: ${request.label}")
        playbackActions.loadPlaybackRequest(request)
        playbackState.pendingPlaybackRequest = null
    }

    LaunchedEffect(playbackController) {
        while (true) {
            delay(PLAYBACK_SNAPSHOT_POLL_INTERVAL_MS)
            val nextSnapshot = playbackController.snapshot()
            if (nextSnapshot != playbackSnapshot) {
                playbackSnapshot = nextSnapshot
            }
            playbackActions.persistActivePlaybackProgress(nextSnapshot)
            val mediaId = playbackState.activeProgressMediaId
            if (
                playbackState.autoNextLocalPlayback &&
                nextSnapshot.status == PlaybackStatus.ENDED &&
                mediaId != null &&
                playbackState.activeProgressTarget == null &&
                playbackState.lastAutoNextMediaId != mediaId
            ) {
                val nextItem = libraryState.indexedLibrary?.catalog?.nextItem(mediaId)
                playbackState.lastAutoNextMediaId = mediaId
                if (nextItem == null) {
                    appendDiagnostic("playback", "Auto-next reached end of local catalog at $mediaId")
                } else {
                    appendDiagnostic("playback", "Auto-next preparing ${nextItem.id} after $mediaId")
                    localPlaybackActions.prepareLocalPlayback(nextItem, loadAfterPrepare = true)
                }
            }
        }
    }

    LaunchedEffect(launchOptions.smokePlayback, playbackSnapshot.status) {
        val smokePlayback = launchOptions.smokePlayback ?: return@LaunchedEffect
        if (!smokePlayback.autoExit || playbackState.smokePlaybackExitStarted || playbackSnapshot.status != PlaybackStatus.PLAYING) {
            return@LaunchedEffect
        }
        playbackState.smokePlaybackExitStarted = true
        appendDiagnostic(
            "smoke",
            "Smoke playback reached PLAYING; waiting ${smokePlayback.playbackDuration.inWholeSeconds}s before exit",
        )
        delay(smokePlayback.playbackDuration)
        val finalSnapshot = playbackController.snapshot()
        appendDiagnostic(
            "smoke",
            "Smoke playback complete: status=${finalSnapshot.status}; " +
                "position=${finalSnapshot.position.positionMs}ms; duration=${finalSnapshot.position.durationMs ?: "unknown"}",
        )
        exitProcess(0)
    }

    DisposableEffect(mpvRuntime) {
        onDispose {
            mpvRuntime.close()
        }
    }

    val sidecarRuntimeForDisposal = rustSidecarRuntime
    DisposableEffect(sidecarRuntimeForDisposal) {
        onDispose {
            sidecarRuntimeForDisposal?.close()
        }
    }

    DisposableEffect(myAnimeListOAuthCallbackRuntime, catalogStore) {
        onDispose {
            myAnimeListOAuthCallbackRuntime.close()
            catalogStore.close()
        }
    }

    DisposableEffect(diagnosticFileLog) {
        onDispose {
            diagnosticFileLog.close()
        }
    }

    MaterialTheme(colors = DanmakuDarkColors) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent(::handleDesktopShellShortcut),
            color = DanmakuColors.Background,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                val showAppChrome = !isFullscreen && navigationState.selectedTab != DesktopShellTab.PLAYBACK
                if (showAppChrome) {
                    AppNavigationRail(
                        selectedTab = navigationState.selectedTab,
                        strings = desktopStrings,
                        onTabSelected = { navigationState.selectedTab = it },
                        playbackLabel = playbackState.activePlaybackLabel,
                        playbackStatus = playbackSnapshot.status,
                        episodeCount = libraryState.indexedLibrary?.catalog?.items?.size ?: 0,
                        modifier = Modifier.width(224.dp),
                    )
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    if (showAppChrome) {
                        ShellHeader(
                            selectedTab = navigationState.selectedTab,
                            strings = desktopStrings,
                            searchText = navigationState.globalSearchText,
                            onSearchTextChange = { navigationState.globalSearchText = it },
                            onSubmitSearch = navigationState::submitGlobalSearch,
                            searchFocusRequester = navigationState.globalSearchFocusRequester,
                            onRefresh = libraryActions::rescanRegisteredRoots,
                            onShowSettings = { navigationState.selectedTab = DesktopShellTab.PROFILE },
                            playerStatus = playbackSnapshot.status,
                            episodeCount = libraryState.indexedLibrary?.catalog?.items?.size ?: 0,
                            isRefreshEnabled = libraryState.registeredRoots.isNotEmpty() && !libraryState.isIndexing,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                if (isFullscreen || navigationState.selectedTab == DesktopShellTab.PLAYBACK) {
                                    0.dp
                                } else {
                                    18.dp
                                },
                            ),
                    ) {
                    if (navigationState.selectedTab == DesktopShellTab.PLAYBACK) {
                        val activeLocalMediaId = playbackState.activeProgressMediaId.takeIf { playbackState.activeProgressTarget == null }
                        val previousLocalPlaybackItem = activeLocalMediaId
                            ?.let { mediaId -> libraryState.indexedLibrary?.catalog?.previousItem(mediaId) }
                        val nextLocalPlaybackItem = activeLocalMediaId
                            ?.let { mediaId -> libraryState.indexedLibrary?.catalog?.nextItem(mediaId) }
                        PlaybackTab(
                            strings = desktopStrings,
                            playbackLabel = playbackState.activePlaybackLabel,
                            playbackSnapshot = playbackSnapshot,
                            mpvRuntimeStatus = mpvRuntime.statusMessage,
                            videoHostStatus = videoHostStatus,
                            overlayStatus = overlayStatus,
                            danmakuSettings = settingsState.danmakuSettings,
                            dandanplayCacheStatus = libraryState.dandanplayCacheStatus,
                            isPreparingLocalPlayback = libraryState.isPreparingLocalPlayback,
                            libraryError = libraryState.libraryError,
                            onWindowIdChanged = { windowId ->
                                if (mpvVideoWindowId != windowId) {
                                    appendDiagnostic(
                                        "video-host",
                                        windowId
                                            ?.let { "Native video window attached: $it" }
                                            ?: "Native video window detached",
                                    )
                                }
                                mpvVideoWindowId = windowId
                            },
                            onMpvPointerMove = { x, y, width, height ->
                                mpvOscActions.forwardBinding("danmaku-osc-mouse-move", x, y, width, height)
                            },
                            onMpvPrimaryClick = { x, y, width, height ->
                                mpvOscActions.forwardBinding("danmaku-osc-left-click", x, y, width, height)
                            },
                            onMpvWheel = { x, y, width, height, rotation ->
                                mpvOscActions.forwardWheel(x, y, width, height, rotation)
                            },
                            previousEpisodeLabel = previousLocalPlaybackItem?.episodeTitle,
                            nextEpisodeLabel = nextLocalPlaybackItem?.episodeTitle,
                            onPlayPreviousEpisode = previousLocalPlaybackItem?.let { item ->
                                {
                                    appendDiagnostic("playback", "Preparing previous local episode ${item.id}")
                                    localPlaybackActions.prepareLocalPlayback(item, loadAfterPrepare = true)
                                }
                            },
                            onPlayNextEpisode = nextLocalPlaybackItem?.let { item ->
                                {
                                    appendDiagnostic("playback", "Preparing next local episode ${item.id}")
                                    localPlaybackActions.prepareLocalPlayback(item, loadAfterPrepare = true)
                                }
                            },
                            onOpenMediaFile = {
                                playbackActions.openDirectMediaFile(
                                    title = desktopStrings.chooseMediaFileTitle(hostPlatform.displayName),
                                )
                            },
                            onPlay = playbackActions::dispatchPlay,
                            onPause = playbackActions::dispatchPause,
                            onSeekBackward = { playbackActions.seekBy(-10_000, "Dispatch Seek -10s") },
                            onSeekBackwardLarge = { playbackActions.seekBy(-30_000, "Dispatch Seek -30s") },
                            onSeekForward = { playbackActions.seekBy(10_000, "Dispatch Seek +10s") },
                            onSeekForwardLarge = { playbackActions.seekBy(30_000, "Dispatch Seek +30s") },
                            onSeekTo = playbackActions::seekTo,
                            onSetPlaybackRate = playbackActions::setPlaybackRate,
                            onSetVolume = playbackActions::setVolume,
                            onSelectAudioTrack = playbackActions::selectAudioTrack,
                            onSelectSubtitleTrack = playbackActions::selectSubtitleTrack,
                            isFullscreen = isFullscreen,
                            videoAspectMode = videoAspectMode,
                            onSetFullscreen = { enabled -> desktopWindowState.setFullscreen(enabled, "playback") },
                            onSetVideoAspectMode = playbackActions::setVideoAspectMode,
                            onSaveDanmakuSettings = settingsActions::saveDanmakuSettings,
                            onShowHome = { navigationState.selectedTab = DesktopShellTab.HOME },
                            onShowLibrary = { navigationState.selectedTab = DesktopShellTab.MEDIA_LIBRARY },
                            canOpenMedia = mpvVideoWindowId != null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    when (navigationState.selectedTab) {
                        DesktopShellTab.HOME -> HomeTab(
                            strings = desktopStrings,
                            playbackSnapshot = playbackSnapshot,
                            registeredRoots = libraryState.registeredRoots,
                            indexedLibrary = displayIndexedLibrary,
                            seriesPosterById = seriesPosterById,
                            playbackProgresses = libraryState.playbackProgresses,
                            favoriteMediaIds = libraryState.favoriteMediaIds,
                            externalAnimeMappings = externalAnimeMappings,
                            externalAnimeListEntries = libraryState.externalAnimeListEntries,
                            externalAnimeSyncFailures = libraryState.externalAnimeSyncFailures,
                            isIndexing = libraryState.isIndexing,
                            isPreparingLocalPlayback = libraryState.isPreparingLocalPlayback,
                            isRefreshingSeriesPosters = libraryState.isRefreshingSeriesPosters,
                            refreshingMetadataMediaIds = libraryState.refreshingMetadataMediaIds,
                            refreshingMetadataSeriesIds = libraryState.refreshingMetadataSeriesIds,
                            dandanplayCacheStatus = libraryState.dandanplayCacheStatus,
                            episodeCount = libraryState.indexedLibrary?.catalog?.items?.size ?: 0,
                            networkUrls = networkUrls,
                            pairingToken = serverPairingToken,
                            overlayStatus = overlayStatus,
                            libraryError = libraryState.libraryError,
                            lastScanStats = libraryState.lastScanStats,
                            diagnosticLog = diagnosticLog,
                            onOpenLibrary = { navigationState.selectedTab = DesktopShellTab.MEDIA_LIBRARY },
                            onOpenDownloads = { navigationState.selectedTab = DesktopShellTab.DOWNLOADS },
                            onOpenSettings = { navigationState.selectedTab = DesktopShellTab.PROFILE },
                            onOpenTracking = { navigationState.selectedTab = DesktopShellTab.TRACKING },
                            onRefreshMetadata = {
                                displayIndexedLibrary?.let(libraryActions::refreshMissingSeriesPosters)
                            },
                            onPlayLocalPlayback = { item ->
                                localPlaybackActions.prepareLocalPlayback(item, loadAfterPrepare = true)
                            },
                        )
                        DesktopShellTab.PLAYBACK -> Unit
                        DesktopShellTab.TRACKING -> TrackingTab(
                            strings = desktopStrings,
                            indexedLibrary = displayIndexedLibrary,
                            externalAnimeMappings = externalAnimeMappings,
                            playbackProgresses = libraryState.playbackProgresses,
                            externalAnimeListEntries = libraryState.externalAnimeListEntries,
                            externalAnimeSyncFailures = libraryState.externalAnimeSyncFailures,
                            isExternalAnimeSyncing = libraryState.isExternalAnimeSyncing,
                            isExternalAnimeReadbackRefreshing = libraryState.isExternalAnimeReadbackRefreshing,
                            isExternalAnimeProgressImporting = libraryState.isExternalAnimeProgressImporting,
                            isExternalAnimeMappingSuggesting = libraryState.isExternalAnimeMappingSuggesting,
                            externalAnimeProviderSettings = settingsState.externalAnimeProviderSettings,
                            onSyncExternalAnimePlan = libraryActions::syncExternalAnimePlan,
                            onRefreshExternalAnimeReadback = libraryActions::refreshExternalAnimeReadback,
                            onApplyExternalAnimeProgressImport = libraryActions::applyExternalAnimeProgressImport,
                            onSuggestMissingExternalAnimeMappings = libraryActions::suggestMissingExternalAnimeMappings,
                            onOpenSettings = { navigationState.selectedTab = DesktopShellTab.PROFILE },
                            onOpenLibrary = { navigationState.selectedTab = DesktopShellTab.MEDIA_LIBRARY },
                        )
                        DesktopShellTab.MEDIA_LIBRARY -> MediaLibraryTab(
                            strings = desktopStrings,
                            registeredRoots = libraryState.registeredRoots,
                            indexedLibrary = displayIndexedLibrary,
                            searchSeed = navigationState.librarySearchSeed,
                            searchSeedVersion = navigationState.librarySearchSeedVersion,
                            seriesPosterById = seriesPosterById,
                            externalAnimeMappings = externalAnimeMappings,
                            externalAnimeItemMappingsByMediaId = externalAnimeItemMappingsByMediaId,
                            externalAnimeProviderSettings = settingsState.externalAnimeProviderSettings,
                            originalSeriesTitleByMediaId = originalSeriesTitleByMediaId,
                            refreshingMetadataMediaIds = libraryState.refreshingMetadataMediaIds,
                            refreshingMetadataSeriesIds = libraryState.refreshingMetadataSeriesIds,
                            playbackProgresses = libraryState.playbackProgresses,
                            externalAnimeListEntries = libraryState.externalAnimeListEntries,
                            localAnimeListEntries = libraryState.localAnimeListEntries,
                            libraryQualityIssueDecisions = libraryState.libraryQualityIssueDecisions,
                            favoriteMediaIds = libraryState.favoriteMediaIds,
                            externalAnimeSyncFailures = libraryState.externalAnimeSyncFailures,
                            isExternalAnimeSyncing = libraryState.isExternalAnimeSyncing,
                            isIndexing = libraryState.isIndexing,
                            isPreparingLocalPlayback = libraryState.isPreparingLocalPlayback,
                            selectedLocalPlaybackPreparation = libraryState.selectedLocalPlaybackPreparation,
                            dandanplayCacheStatus = libraryState.dandanplayCacheStatus,
                            autoNextLocalPlayback = playbackState.autoNextLocalPlayback,
                            libraryError = libraryState.libraryError,
                            lastScanStats = libraryState.lastScanStats,
                            onAddLibraryFolder = {
                                selectLibraryDirectory(
                                    title = desktopStrings.chooseAnimeLibraryFolderTitle,
                                )?.let(libraryActions::registerAndScanUserRoot)
                            },
                            onImportAniRssOutputFolder = {
                                selectLibraryDirectory(
                                    title = desktopStrings.chooseAniRssCompletedMediaFolderTitle,
                                )?.let(libraryActions::importAndScanAniRssRoot)
                            },
                            onRescanRegisteredRoots = libraryActions::rescanRegisteredRoots,
                            onRemoveRegisteredRoot = libraryActions::removeRegisteredRoot,
                            onPrepareLocalPlayback = { item -> localPlaybackActions.prepareLocalPlayback(item) },
                            onPlayLocalPlayback = { item ->
                                localPlaybackActions.prepareLocalPlayback(item, loadAfterPrepare = true)
                            },
                            onInspectCachedDandanplay = localPlaybackActions::inspectCachedDandanplay,
                            onSetFavorite = libraryActions::setFavorite,
                            onSetLibraryQualityIssueDecision = libraryActions::setLibraryQualityIssueDecision,
                            onApplyLibraryQualityIssueMappings = { issue ->
                                displayIndexedLibrary?.catalog?.let { catalog ->
                                    libraryActions.applyLibraryQualityIssueMappings(issue, catalog)
                                }
                            },
                            onRefreshLibraryQualityIssueMetadata = libraryActions::refreshLibraryQualityIssueMetadata,
                            onSaveLocalAnimeListEntry = libraryActions::saveLocalAnimeListEntry,
                            onDeleteLocalAnimeListEntry = libraryActions::deleteLocalAnimeListEntry,
                            onSetAutoNextLocalPlayback = playbackActions::setAutoNextLocalPlayback,
                            onRefreshDandanplay = localPlaybackActions::refreshPreparedDandanplay,
                            onSelectDandanplayMatch = localPlaybackActions::selectPreparedDandanplayMatch,
                            onClearDandanplayCache = localPlaybackActions::clearPreparedDandanplayCache,
                            onClearDanmakuOverlay = localPlaybackActions::clearPreparedDanmakuOverlay,
                            onAttachManualDanmaku = localPlaybackActions::attachManualDanmaku,
                            onRefreshEpisodeMetadata = libraryActions::refreshEpisodeAnimeMetadata,
                            onRefreshSeriesMetadata = libraryActions::refreshSeriesAnimeMetadata,
                            onSaveExternalAnimeMapping = libraryActions::saveManualExternalAnimeMapping,
                            onDeleteExternalAnimeMapping = libraryActions::deleteManualExternalAnimeMapping,
                            onSaveExternalAnimeItemMapping = libraryActions::saveManualExternalAnimeItemMapping,
                            onDeleteExternalAnimeItemMapping = libraryActions::deleteManualExternalAnimeItemMapping,
                            onSearchExternalAnimeMatches = libraryActions::searchExternalAnimeMatches,
                            onFetchMetadataMatchPoster = libraryActions::fetchMetadataMatchPoster,
                            onSyncExternalAnimePlan = libraryActions::syncExternalAnimePlan,
                            onLoadPreparedPlayback = playbackActions::loadPreparedLocalPlayback,
                            remoteBrowser = {
                                val remoteClient = effectiveRemoteClient
                                RemoteLibraryBrowser(
                                    strings = desktopStrings,
                                    defaultServerUrl = remoteClient?.normalizedServerUrl ?: serverBaseUrl,
                                    defaultPairingToken = remoteClient?.pairingToken ?: serverPairingToken,
                                    autoLoadOnStart = remoteClient?.autoLoad == true,
                                    appendDiagnostic = ::appendDiagnostic,
                                    onLoadPreparedPlayback = playbackActions::loadPreparedRemotePlayback,
                                )
                            },
                        )
                        DesktopShellTab.DOWNLOADS -> DownloadsTab(
                            strings = desktopStrings,
                            isIndexing = libraryState.isIndexing,
                            webhookUrls = emptyList(),
                            webhookToken = "",
                            registeredRoots = libraryState.registeredRoots,
                            downloadQueueItems = libraryState.downloadQueueItems,
                            onAddAniRssOutputFolder = {
                                selectLibraryDirectory(
                                    title = desktopStrings.chooseAniRssCompletedMediaFolderTitle,
                                )?.let(libraryActions::importAndScanAniRssRoot)
                            },
                            onRefreshQueue = downloadActions::refreshQueue,
                            onRemoveQueueItem = downloadActions::removeQueueItem,
                            onOpenOutputFolder = downloadActions::openOutputFolder,
                        )
                        DesktopShellTab.PROFILE -> ProfileTab(
                            desktopLanguage = navigationState.desktopLanguage,
                            strings = desktopStrings,
                            mpvRuntimeStatus = mpvRuntime.statusMessage,
                            videoHostStatus = videoHostStatus,
                            serverBaseUrl = serverBaseUrl,
                            networkUrls = networkUrls,
                            pairingToken = serverPairingToken,
                            recentServerEvents = serverEvents,
                            appLogPath = diagnosticFileLog.appLogPath,
                            mpvLogPath = diagnosticFileLog.mpvLogPath,
                            diagnosticLog = diagnosticLog,
                            danmakuSettings = settingsState.danmakuSettings,
                            onSaveDanmakuSettings = settingsActions::saveDanmakuSettings,
                            dandanplayCacheEntries = settingsState.dandanplayCacheEntries,
                            onRefreshDandanplayCacheEntries = settingsActions::refreshDandanplayCacheEntries,
                            onDeleteDandanplayCacheEntry = settingsActions::deleteDandanplayCacheEntry,
                            onCleanupExpiredDandanplayCaches = settingsActions::cleanupExpiredDandanplayCaches,
                            onDesktopLanguageChange = navigationState::updateDesktopLanguage,
                            dandanplaySettings = settingsState.dandanplaySettings,
                            onSaveDandanplaySettings = settingsActions::saveDandanplaySettings,
                            onClearDandanplaySettings = settingsActions::clearDandanplaySettings,
                            dandanplayConnectionTestStatus = settingsState.dandanplayConnectionTestStatus,
                            onTestDandanplayConnection = settingsActions::testDandanplayConnection,
                            externalAnimeProviderSettings = settingsState.externalAnimeProviderSettings,
                            onSaveExternalAnimeProviderSettings = settingsActions::saveExternalAnimeProviderSettings,
                            onStartMyAnimeListOAuth = settingsActions::startMyAnimeListOAuth,
                            myAnimeListConnectionTestStatus = settingsState.myAnimeListConnectionTestStatus,
                            bangumiConnectionTestStatus = settingsState.bangumiConnectionTestStatus,
                            onTestMyAnimeListConnection = settingsActions::testMyAnimeListConnection,
                            onTestBangumiConnection = settingsActions::testBangumiConnection,
                            onClearMyAnimeListSettings = settingsActions::clearMyAnimeListSettings,
                            onClearBangumiSettings = settingsActions::clearBangumiSettings,
                            localServerConnectionTestStatus = settingsState.localServerConnectionTestStatus,
                            onTestLocalServerConnection = settingsActions::testLocalServerConnection,
                        )
                    }
                }
            }
                if (startupProgress.isVisible && !isFullscreen) {
                    DesktopStartupSplash(progress = startupProgress)
                }
            }
        }
    }
}
}
