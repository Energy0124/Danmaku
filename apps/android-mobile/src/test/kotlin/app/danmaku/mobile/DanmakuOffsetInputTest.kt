package app.danmaku.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DanmakuOffsetInputTest {
    @Test
    fun parsesTheFullSupportedOffsetRange() {
        assertEquals(-3_600_000L, parseDanmakuOffset("-60:00.000"))
        assertEquals(3_599_999L, parseDanmakuOffset("+59:59.999"))
        assertEquals(150_500L, parseDanmakuOffset("02:30.5"))
    }

    @Test
    fun rejectsMalformedOrOutOfRangeOffsets() {
        assertNull(parseDanmakuOffset("60:00.001"))
        assertNull(parseDanmakuOffset("01:60.000"))
        assertNull(parseDanmakuOffset("not a time"))
    }

    @Test
    fun formatsOffsetsWithoutLosingMillisecondPrecision() {
        assertEquals("-02:30.500", formatDanmakuOffset(-150_500L))
        assertEquals("+00:00.001", formatDanmakuOffset(1L))
    }
}
