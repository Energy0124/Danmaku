package app.danmaku.tv

import app.danmaku.domain.DanmakuMode
import org.junit.Assert.assertEquals
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

    @Test
    fun textSizeSupportsTenPercent() {
        val preferences = TvDanmakuPreferences(fontScale = 0.1f)

        assertEquals(0.1f, preferences.fontScale, 0.0001f)
    }

    @Test
    fun steppedValuesMoveWithLeftAndRightAndStopAtTheirBounds() {
        assertEquals(
            0.9f,
            adjustSteppedValue(1f, 0.1f..1.5f, 0.1f, increase = false),
            0.0001f,
        )
        assertEquals(
            1.1f,
            adjustSteppedValue(1f, 0.1f..1.5f, 0.1f, increase = true),
            0.0001f,
        )
        assertEquals(
            0.1f,
            adjustSteppedValue(0.1f, 0.1f..1.5f, 0.1f, increase = false),
            0.0001f,
        )
        assertEquals(
            1.5f,
            adjustSteppedValue(1.5f, 0.1f..1.5f, 0.1f, increase = true),
            0.0001f,
        )
    }
}
