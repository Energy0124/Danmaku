package app.danmaku.tv

import app.danmaku.domain.DanmakuMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvDanmakuPreferencesTest {
    @Test
    fun eachDanmakuTypeCanBeHiddenIndependently() {
        val preferences = TvDanmakuPreferences(
            showScrolling = false,
            showTop = true,
            showBottom = false,
        )

        assertFalse(preferences.shows(DanmakuMode.SCROLLING))
        assertTrue(preferences.shows(DanmakuMode.TOP))
        assertFalse(preferences.shows(DanmakuMode.BOTTOM))
    }
}
