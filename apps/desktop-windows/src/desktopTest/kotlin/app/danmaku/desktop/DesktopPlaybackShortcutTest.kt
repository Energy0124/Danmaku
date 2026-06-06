package app.danmaku.desktop

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopPlaybackShortcutTest {
    @Test
    fun resolvesPlaybackControlKeys() {
        assertEquals(DesktopPlaybackShortcut.TOGGLE_PLAY_PAUSE, desktopPlaybackShortcutForKey(Key.Spacebar))
        assertEquals(DesktopPlaybackShortcut.TOGGLE_PLAY_PAUSE, desktopPlaybackShortcutForKey(Key.K))
        assertEquals(DesktopPlaybackShortcut.TOGGLE_FULLSCREEN, desktopPlaybackShortcutForKey(Key.F))
        assertEquals(DesktopPlaybackShortcut.EXIT_FULLSCREEN, desktopPlaybackShortcutForKey(Key.Escape))
        assertEquals(DesktopPlaybackShortcut.OPEN_MEDIA, desktopPlaybackShortcutForKey(Key.O))
    }

    @Test
    fun resolvesSeekAndVolumeKeys() {
        assertEquals(DesktopPlaybackShortcut.SEEK_BACKWARD, desktopPlaybackShortcutForKey(Key.DirectionLeft))
        assertEquals(
            DesktopPlaybackShortcut.SEEK_BACKWARD_LARGE,
            desktopPlaybackShortcutForKey(Key.DirectionLeft, shiftPressed = true),
        )
        assertEquals(DesktopPlaybackShortcut.SEEK_FORWARD, desktopPlaybackShortcutForKey(Key.DirectionRight))
        assertEquals(
            DesktopPlaybackShortcut.SEEK_FORWARD_LARGE,
            desktopPlaybackShortcutForKey(Key.DirectionRight, shiftPressed = true),
        )
        assertEquals(DesktopPlaybackShortcut.VOLUME_UP, desktopPlaybackShortcutForKey(Key.DirectionUp))
        assertEquals(DesktopPlaybackShortcut.VOLUME_DOWN, desktopPlaybackShortcutForKey(Key.DirectionDown))
    }

    @Test
    fun ignoresSystemModifiedShortcuts() {
        assertNull(desktopPlaybackShortcutForKey(Key.F, ctrlPressed = true))
        assertNull(desktopPlaybackShortcutForKey(Key.O, metaPressed = true))
    }
}
