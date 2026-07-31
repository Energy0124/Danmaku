package app.danmaku.tv

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.danmaku.player.android.Media3PlaybackServiceConnection

@Composable
internal fun TvApp(container: TvApplicationContainer) {
    val factory = remember(container) { TvViewModelFactory(container) }
    val navigationViewModel: TvNavigationViewModel = viewModel(factory = factory)
    val sessionViewModel: TvSessionViewModel = viewModel(factory = factory)
    val browseViewModel: TvBrowseViewModel = viewModel(factory = factory)
    val playbackViewModel: TvPlaybackViewModel = viewModel(factory = factory)
    val navigator = navigationViewModel.navigator
    val navigation by navigationViewModel.state.collectAsStateWithLifecycle()
    val session by sessionViewModel.state.collectAsStateWithLifecycle()
    val browse by browseViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val imageLoader = remember(context) { createTvImageLoader(context.applicationContext) }
    val playbackConnection = remember(context) {
        Media3PlaybackServiceConnection(context.applicationContext)
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
                        sessionViewModel = sessionViewModel,
                        browseViewModel = browseViewModel,
                        playbackViewModel = playbackViewModel,
                        onNavigate = navigationViewModel::navigate,
                        onShowOverlay = navigationViewModel::showOverlay,
                        onCloseOverlay = navigationViewModel::closeOverlay,
                    )
            }
        }
    }
}
