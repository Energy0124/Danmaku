package app.danmaku.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopMpvWindowsOptionsTest {
    @Test
    fun convertsWindowsHandlesToUnsignedWidOption() {
        assertEquals(
            mapOf("wid" to "4294967295"),
            DesktopMpvWindowsOptions.forWindowId(-1L),
        )
    }

    @Test
    fun rejectsZeroWindowHandles() {
        assertFailsWith<IllegalArgumentException> {
            DesktopMpvWindowsOptions.forWindowId(0L)
        }
    }
}
