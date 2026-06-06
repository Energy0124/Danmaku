package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DanmakuDisplaySettingsTest {
    @Test
    fun hidesAllEventsWhenDanmakuIsDisabled() {
        val settings = DanmakuDisplaySettings(visible = false)

        assertEquals(emptyList(), settings.filter(listOf(event("one", "hello"))))
    }

    @Test
    fun filtersEventsByKeywordAndRegex() {
        val settings = DanmakuDisplaySettings(
            keywordFilters = listOf("spoiler"),
            regexFilters = listOf("episode\\s+\\d+"),
        )

        assertEquals(
            listOf(event("safe", "nice timing")),
            settings.filter(
                listOf(
                    event("keyword", "major Spoiler"),
                    event("regex", "episode 12 leaked"),
                    event("safe", "nice timing"),
                ),
            ),
        )
    }

    @Test
    fun ignoresInvalidRegexFiltersWithoutDroppingSafeEvents() {
        val settings = DanmakuDisplaySettings(regexFilters = listOf("["))

        assertEquals(listOf(event("safe", "hello")), settings.filter(listOf(event("safe", "hello"))))
    }

    @Test
    fun scalesRendererValues() {
        val settings = DanmakuDisplaySettings(
            opacityPercent = 80,
            fontScalePercent = 125,
            speedPercent = 200,
            densityPercent = 150,
            displayAreaPercent = 50,
        )

        assertEquals("33", settings.assAlphaHex())
        assertEquals(45, settings.scaledFontSize(36))
        assertEquals(3_500, settings.scaledTravelDurationMs(7_000))
        assertEquals(6, settings.scaledLaneCount(8))
    }

    @Test
    fun validatesRangesAndBlankFilters() {
        assertFailsWith<IllegalArgumentException> {
            DanmakuDisplaySettings(opacityPercent = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            DanmakuDisplaySettings(fontScalePercent = 25)
        }
        assertFailsWith<IllegalArgumentException> {
            DanmakuDisplaySettings(keywordFilters = listOf(" "))
        }
    }

    private fun event(
        id: String,
        text: String,
    ): DanmakuEvent =
        DanmakuEvent(id = id, timestampMs = 0, text = text)
}
