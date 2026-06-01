package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DanmakuEventTest {
    @Test
    fun rejectsNegativeTimestamps() {
        assertFailsWith<IllegalArgumentException> {
            DanmakuEvent(id = "event-1", timestampMs = -1, text = "hello")
        }
    }

    @Test
    fun usesScrollingNormalWhiteStyleByDefault() {
        val event = DanmakuEvent(id = "event-1", timestampMs = 100, text = "hello")

        assertEquals(DanmakuMode.SCROLLING, event.style.mode)
        assertEquals(DanmakuSize.NORMAL, event.style.size)
        assertEquals(0xFFFFFFFFu, event.style.colorArgb)
    }
}

