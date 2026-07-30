package app.danmaku.tv

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.danmaku.library.LanDanmakuLoader
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.android.AndroidLanLibraryConnectionStore
import app.danmaku.library.android.AndroidLibraryFavoriteStore
import app.danmaku.library.android.LanLibraryClient
import app.danmaku.library.android.LanLibraryDiscoveryClient

internal class TvApplicationContainer(
    application: Application,
) {
    private val applicationContext = application.applicationContext
    private val libraryClient = LanLibraryClient()
    private val connectionStore = AndroidLanLibraryConnectionStore(applicationContext)
    private val favoriteStore = AndroidLibraryFavoriteStore(applicationContext)
    private val connectionSession = LanLibraryConnectionSession(libraryClient)

    val navigator = TvNavigator()
    val libraryRepository = TvLibraryRepository(
        connectionSession = connectionSession,
        connectionStore = connectionStore,
        favoriteStore = favoriteStore,
        catalogCache = AndroidTvCatalogCache(applicationContext),
        defaultServerUrl = BuildConfig.DEFAULT_SERVER_URL,
        defaultPairingToken = BuildConfig.DEFAULT_PAIRING_TOKEN,
    )
    val browsePresenter = TvBrowsePresenter()
    val libraryDiscovery = TvLibraryDiscovery {
        LanLibraryDiscoveryClient().discover()
    }
    val playbackProgressSync = LanPlaybackProgressSync(libraryClient, System::currentTimeMillis)
    val playbackPreparer = LanPlaybackPreparer(libraryClient)
    val danmakuLoader = LanDanmakuLoader(libraryClient)
    val playbackGateway = LanTvPlaybackGateway(
        progressSync = playbackProgressSync,
        playbackPreparer = playbackPreparer,
        danmakuLoader = danmakuLoader,
    )
    val danmakuPreferencesStore = TvDanmakuPreferencesStore(applicationContext)

    fun installQaFixture() {
        libraryRepository.installQaFixture(createTvQaFixture())
        navigator.reset(TvRoute.Home)
    }
}

internal class TvViewModelFactory(
    private val container: TvApplicationContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        when {
            modelClass.isAssignableFrom(TvNavigationViewModel::class.java) ->
                TvNavigationViewModel(container.navigator)
            modelClass.isAssignableFrom(TvSessionViewModel::class.java) ->
                TvSessionViewModel(
                    repository = container.libraryRepository,
                    navigator = container.navigator,
                    libraryDiscovery = container.libraryDiscovery,
                )
            modelClass.isAssignableFrom(TvBrowseViewModel::class.java) ->
                TvBrowseViewModel(
                    repository = container.libraryRepository,
                    presenter = container.browsePresenter,
                )
            modelClass.isAssignableFrom(TvPlaybackViewModel::class.java) ->
                TvPlaybackViewModel(
                    repository = container.libraryRepository,
                    navigator = container.navigator,
                    gateway = container.playbackGateway,
                    preferencesStore = container.danmakuPreferencesStore,
                )
            else -> error("Unsupported ViewModel ${modelClass.name}")
        } as T
}
