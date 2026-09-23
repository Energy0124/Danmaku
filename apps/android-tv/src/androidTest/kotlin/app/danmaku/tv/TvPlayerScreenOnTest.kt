package app.danmaku.tv

import android.app.Application
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackStatus
import app.danmaku.library.LanPlaybackPreparation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TvPlayerScreenOnTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun playbackKeepsScreenOnAndReleasesItForInactiveStatesAndNavigation() {
        val container = TvApplicationContainer(ApplicationProvider.getApplicationContext<Application>())
        val navigator = TvNavigator(TvRoute.Player("screen-on-test"))
        val viewModel = TvPlaybackViewModel(
            repository = container.libraryRepository,
            navigator = navigator,
            gateway = container.playbackGateway,
            preferencesStore = object : TvDanmakuPreferencesPersistence {
                override fun load() = TvDanmakuPreferences(enabled = false)
                override fun save(value: TvDanmakuPreferences) = Unit
            },
        )
        var navigation by mutableStateOf(navigator.state.value)
        lateinit var root: View
        composeRule.setContent {
            root = LocalView.current
            DanmakuTvTheme {
                TvPlayerRoute(viewModel, navigation, navigator)
            }
        }

        composeRule.runOnIdle {
            assertFalse(root.playerView().keepScreenOn)
        }
        // Each transition starts from active playback so stale screen-on flags fail.
        PlaybackStatus.entries.filter { it != PlaybackStatus.PLAYING }.forEach { status ->
            setStatus(viewModel, PlaybackStatus.PLAYING)
            composeRule.runOnIdle {
                assertTrue(root.playerView().keepScreenOn)
            }
            setStatus(viewModel, status)
            composeRule.runOnIdle {
                assertFalse("Screen stays on for $status", root.playerView().keepScreenOn)
            }
        }

        setStatus(viewModel, PlaybackStatus.PLAYING)
        lateinit var releasedView: PlayerView
        composeRule.runOnIdle {
            releasedView = root.playerView()
            assertTrue(releasedView.keepScreenOn)
            navigation = TvNavigationState(backStack = listOf(TvRoute.Home))
        }
        composeRule.runOnIdle {
            assertFalse(releasedView.keepScreenOn)
            assertFalse(releasedView.isAttachedToWindow)
            viewModel.detachController()
        }
    }

    private fun setStatus(viewModel: TvPlaybackViewModel, status: PlaybackStatus) {
        composeRule.runOnIdle {
            // No media, network, or playback service is needed to exercise the real route.
            viewModel.attachController(object : TvPlaybackController {
                override val androidPlayer: Player? = null
                override fun load(preparation: LanPlaybackPreparation) = Unit
                override fun dispatch(command: PlaybackCommand) = Unit
                override fun stop() = Unit
                override fun snapshot() = PlaybackSnapshot(status = status)
            })
        }
    }

    private fun View.playerView(): PlayerView =
        descendants().filterIsInstance<PlayerView>().single()

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) {
            for (index in 0 until childCount) {
                yieldAll(getChildAt(index).descendants())
            }
        }
    }
}
