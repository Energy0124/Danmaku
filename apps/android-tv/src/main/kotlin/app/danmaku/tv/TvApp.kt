package app.danmaku.tv

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.danmaku.player.android.Media3PlaybackServiceConnection
import app.danmaku.updater.android.AndroidAppUpdateViewModel
import app.danmaku.updater.android.AndroidAppUpdateViewModelFactory
import app.danmaku.updater.android.AppUpdateConfiguration
import app.danmaku.updater.android.AppUpdateInstaller
import app.danmaku.updater.android.AppUpdateKind
import app.danmaku.updater.android.AppUpdateState

@Composable
internal fun TvApp(container: TvApplicationContainer) {
    val factory = remember(container) { TvViewModelFactory(container) }
    val navigationViewModel: TvNavigationViewModel = viewModel(factory = factory)
    val sessionViewModel: TvSessionViewModel = viewModel(factory = factory)
    val browseViewModel: TvBrowseViewModel = viewModel(factory = factory)
    val playbackViewModel: TvPlaybackViewModel = viewModel(factory = factory)
    val context = LocalContext.current
    val updateFactory = remember(context) {
        AndroidAppUpdateViewModelFactory(
            context = context.applicationContext,
            configuration = AppUpdateConfiguration(
                manifestUrl = BuildConfig.UPDATE_MANIFEST_URL,
                appKind = AppUpdateKind.TV,
                applicationId = BuildConfig.APPLICATION_ID,
                currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                currentVersionName = BuildConfig.VERSION_NAME,
            ),
        )
    }
    val updateViewModel: AndroidAppUpdateViewModel = viewModel(
        key = "app-update",
        factory = updateFactory,
    )
    val appUpdateState by updateViewModel.state.collectAsStateWithLifecycle()
    val updateInstaller = remember(context) { AppUpdateInstaller(context) }
    var permissionRequired by remember { mutableStateOf(false) }
    var installerUnavailable by remember { mutableStateOf(false) }
    var pendingInstallPath by remember { mutableStateOf<String?>(null) }
    var automaticInstallAttemptPath by remember { mutableStateOf<String?>(null) }
    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val path = pendingInstallPath
        if (path != null && updateInstaller.canRequestPackageInstalls()) {
            permissionRequired = false
            installerUnavailable = !updateInstaller.launchPackageInstaller(path)
        } else {
            permissionRequired = path != null
        }
    }
    val requestUpdateInstall: (String) -> Unit = { path ->
        pendingInstallPath = path
        installerUnavailable = false
        if (updateInstaller.canRequestPackageInstalls()) {
            permissionRequired = false
            installerUnavailable = !updateInstaller.launchPackageInstaller(path)
        } else {
            permissionRequired = true
        }
    }
    val navigator = navigationViewModel.navigator
    val navigation by navigationViewModel.state.collectAsStateWithLifecycle()
    val session by sessionViewModel.state.collectAsStateWithLifecycle()
    val browse by browseViewModel.state.collectAsStateWithLifecycle()
    val playback by playbackViewModel.state.collectAsStateWithLifecycle()
    val imageLoader = remember(context) { createTvImageLoader(context.applicationContext) }
    val playbackConnection = remember(context) {
        Media3PlaybackServiceConnection(context.applicationContext)
    }

    LaunchedEffect(updateViewModel) {
        updateViewModel.startAutomaticCheck()
    }
    val readyUpdatePath = (appUpdateState as? AppUpdateState.Ready)?.apkPath
    LaunchedEffect(readyUpdatePath, navigation.route, playback.isActive) {
        val path = readyUpdatePath ?: return@LaunchedEffect
        if (!playback.isActive && navigation.route !is TvRoute.Player && automaticInstallAttemptPath != path) {
            automaticInstallAttemptPath = path
            requestUpdateInstall(path)
        }
    }

    DisposableEffect(playbackConnection) {
        playbackConnection.connect(
            executor = ContextCompat.getMainExecutor(context),
            onConnected = { playbackViewModel.attachController(Media3TvPlaybackController(it)) },
            onFailure = { playbackViewModel.detachController() },
        )
        onDispose {
            playbackViewModel.detachController()
            playbackConnection.close()
        }
    }

    LaunchedEffect(navigation.route) {
        playbackViewModel.setPlayerVisible(navigation.route is TvRoute.Player)
    }

    BackHandler {
        val handled = if (navigation.route is TvRoute.Player) {
            playbackViewModel.handleBack()
        } else {
            navigationViewModel.back()
        }
        if (!handled) {
            (context as? Activity)?.finish()
        }
    }

    DanmakuTvTheme {
        TvImageLoaderProvider(imageLoader) {
            when (val route = navigation.route) {
                TvRoute.Onboarding ->
                    TvOnboardingScreen(
                        navigation = navigation,
                        navigator = navigator,
                        isDiscovering = session.isRefreshing,
                        errorMessage = session.errorMessage,
                        onDiscover = sessionViewModel::discoverPc,
                        onOpenPc = { navigationViewModel.navigate(TvRoute.Pc) },
                    )
                is TvRoute.Player ->
                    TvPlayerRoute(
                        playbackViewModel = playbackViewModel,
                        navigation = navigation,
                        navigator = navigator,
                    )
                else ->
                    TvConsumerShell(
                        route = route,
                        navigation = navigation,
                        navigator = navigator,
                        session = session,
                        browse = browse,
                        playback = playback,
                        sessionViewModel = sessionViewModel,
                        browseViewModel = browseViewModel,
                        playbackViewModel = playbackViewModel,
                        onNavigate = navigationViewModel::navigate,
                        onShowOverlay = navigationViewModel::showOverlay,
                        onCloseOverlay = navigationViewModel::closeOverlay,
                        appUpdateState = appUpdateState,
                        currentVersionName = BuildConfig.VERSION_NAME,
                        onCheckForUpdates = updateViewModel::checkNow,
                        onDownloadUpdate = updateViewModel::download,
                        onInstallUpdate = requestUpdateInstall,
                    )
            }
            if (!playback.isActive && navigation.route !is TvRoute.Player) {
                TvAppUpdateDialog(
                    state = appUpdateState,
                    permissionRequired = permissionRequired,
                    installerUnavailable = installerUnavailable,
                    onDownload = updateViewModel::download,
                    onRetry = {
                        if ((appUpdateState as? AppUpdateState.Failed)?.update == null) {
                            updateViewModel.checkNow()
                        } else {
                            updateViewModel.download()
                        }
                    },
                    onInstall = requestUpdateInstall,
                    onOpenPermissionSettings = {
                        val intent = updateInstaller.unknownSourcesSettingsIntent()
                        if (intent == null) {
                            pendingInstallPath?.let(requestUpdateInstall)
                        } else {
                            runCatching { unknownSourcesLauncher.launch(intent) }
                                .onFailure { installerUnavailable = true }
                        }
                    },
                    onLater = {
                        permissionRequired = false
                        installerUnavailable = false
                        pendingInstallPath = null
                        updateViewModel.dismiss()
                    },
                )
            }
        }
    }
}
