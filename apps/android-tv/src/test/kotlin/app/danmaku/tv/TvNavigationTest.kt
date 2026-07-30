package app.danmaku.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvNavigationTest {
    @Test
    fun backClosesOverlayThenRestoresPreviousRouteAndFocus() {
        val navigator = TvNavigator(TvRoute.Home)

        navigator.saveFocus(TvRoute.Home, "home-series:one")
        navigator.navigateTopLevel(TvRoute.Library)
        navigator.saveFocus(TvRoute.Library, "library-series:two")
        navigator.navigate(TvRoute.SeriesDetail("two"))
        navigator.showOverlay(TvOverlay.LibraryFilters)

        assertTrue(navigator.back())
        assertNull(navigator.state.value.overlay)
        assertEquals(TvRoute.SeriesDetail("two"), navigator.state.value.route)

        assertTrue(navigator.back())
        assertEquals(TvRoute.Library, navigator.state.value.route)
        assertEquals(
            "library-series:two",
            navigator.state.value.focusKeys[TvRoute.Library],
        )

        assertTrue(navigator.back())
        assertEquals(TvRoute.Home, navigator.state.value.route)
        assertEquals("home-series:one", navigator.state.value.focusKeys[TvRoute.Home])
        assertFalse(navigator.back())
    }

    @Test
    fun choosingEarlierTopLevelRouteTruncatesHistoryWithoutDuplicates() {
        val navigator = TvNavigator(TvRoute.Home)

        navigator.navigateTopLevel(TvRoute.Library)
        navigator.navigateTopLevel(TvRoute.Search)
        navigator.navigateTopLevel(TvRoute.Library)

        assertEquals(listOf(TvRoute.Home, TvRoute.Library), navigator.state.value.backStack)
    }
    @Test
    fun playerDirectionKeysOnlySeekWhenControlsAreHidden() {
        assertFalse(shouldHandlePlayerSeekKey(controlsVisible = true, overlay = null))
        assertTrue(shouldHandlePlayerSeekKey(controlsVisible = false, overlay = null))
        assertFalse(
            shouldHandlePlayerSeekKey(controlsVisible = false, overlay = TvOverlay.AudioTracks),
        )
    }

}
