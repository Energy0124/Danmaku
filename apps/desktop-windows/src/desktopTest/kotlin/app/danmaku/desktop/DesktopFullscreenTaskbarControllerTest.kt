package app.danmaku.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopFullscreenTaskbarControllerTest {
    @Test
    fun promotesWindowsFullscreenAndRestoresOnFocusChangesAndExit() {
        var alwaysOnTop = false
        var bringToFrontCalls = 0
        val controller = DesktopFullscreenTaskbarController(
            isWindows = true,
            readAlwaysOnTop = { alwaysOnTop },
            writeAlwaysOnTop = { alwaysOnTop = it },
            bringToFront = { bringToFrontCalls += 1 },
        )

        controller.setFullscreen(true)

        assertTrue(alwaysOnTop)
        assertEquals(1, bringToFrontCalls)

        controller.setWindowFocused(false)
        assertFalse(alwaysOnTop)

        controller.setWindowFocused(true)
        assertTrue(alwaysOnTop)
        assertEquals(2, bringToFrontCalls)

        controller.setFullscreen(false)
        assertFalse(alwaysOnTop)
    }

    @Test
    fun preservesAnExistingAlwaysOnTopPreference() {
        var alwaysOnTop = true
        val controller = DesktopFullscreenTaskbarController(
            isWindows = true,
            readAlwaysOnTop = { alwaysOnTop },
            writeAlwaysOnTop = { alwaysOnTop = it },
            bringToFront = {},
        )

        controller.setFullscreen(true)
        controller.setWindowFocused(false)
        controller.setFullscreen(false)

        assertTrue(alwaysOnTop)
    }

    @Test
    fun ignoresFullscreenTaskbarPromotionOnOtherPlatforms() {
        var alwaysOnTop = false
        var bringToFrontCalls = 0
        val controller = DesktopFullscreenTaskbarController(
            isWindows = false,
            readAlwaysOnTop = { alwaysOnTop },
            writeAlwaysOnTop = { alwaysOnTop = it },
            bringToFront = { bringToFrontCalls += 1 },
        )

        controller.setFullscreen(true)
        controller.setWindowFocused(true)
        controller.setFullscreen(false)

        assertFalse(alwaysOnTop)
        assertEquals(0, bringToFrontCalls)
    }
}
