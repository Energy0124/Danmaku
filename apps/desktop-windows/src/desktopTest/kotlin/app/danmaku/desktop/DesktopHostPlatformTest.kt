package app.danmaku.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopHostPlatformTest {
    @Test
    fun detectsSupportedDesktopHosts() {
        assertEquals(DesktopHostPlatform.WINDOWS, DesktopHostPlatform.current("Windows 11"))
        assertEquals(DesktopHostPlatform.MACOS, DesktopHostPlatform.current("Mac OS X"))
        assertEquals(DesktopHostPlatform.MACOS, DesktopHostPlatform.current("Darwin"))
        assertEquals(DesktopHostPlatform.LINUX, DesktopHostPlatform.current("Linux"))
    }

    @Test
    fun onlyWindowsRequiresAnEmbeddedMpvVideoHost() {
        assertTrue(DesktopHostPlatform.WINDOWS.requiresEmbeddedMpvVideoHost)
        assertFalse(DesktopHostPlatform.MACOS.requiresEmbeddedMpvVideoHost)
    }
}
