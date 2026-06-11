package app.danmaku.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopPlayerShortcutTest {
    @Test
    fun resolvesPlaybackToggleKeys() {
        assertEquals(
            DesktopPlayerShortcut.TOGGLE_PLAY_PAUSE,
            resolveDesktopPlayerShortcut(DesktopPlayerShortcutInput(DesktopPlayerShortcutKey.SPACE)),
        )
        assertEquals(
            DesktopPlayerShortcut.TOGGLE_PLAY_PAUSE,
            resolveDesktopPlayerShortcut(DesktopPlayerShortcutInput(DesktopPlayerShortcutKey.K)),
        )
    }

    @Test
    fun resolvesSeekKeysWithShiftForLargeSteps() {
        assertEquals(
            DesktopPlayerShortcut.SEEK_BACKWARD,
            resolveDesktopPlayerShortcut(DesktopPlayerShortcutInput(DesktopPlayerShortcutKey.LEFT)),
        )
        assertEquals(
            DesktopPlayerShortcut.SEEK_BACKWARD_LARGE,
            resolveDesktopPlayerShortcut(
                DesktopPlayerShortcutInput(DesktopPlayerShortcutKey.LEFT, shiftPressed = true),
            ),
        )
        assertEquals(
            DesktopPlayerShortcut.SEEK_FORWARD,
            resolveDesktopPlayerShortcut(DesktopPlayerShortcutInput(DesktopPlayerShortcutKey.RIGHT)),
        )
        assertEquals(
            DesktopPlayerShortcut.SEEK_FORWARD_LARGE,
            resolveDesktopPlayerShortcut(
                DesktopPlayerShortcutInput(DesktopPlayerShortcutKey.RIGHT, shiftPressed = true),
            ),
        )
    }

    @Test
    fun resolvesVolumeAndCycleKeys() {
        assertEquals(
            DesktopPlayerShortcut.VOLUME_UP,
            resolveDesktopPlayerShortcut(DesktopPlayerShortcutInput(DesktopPlayerShortcutKey.UP)),
        )
        assertEquals(
            DesktopPlayerShortcut.VOLUME_DOWN,
            resolveDesktopPlayerShortcut(DesktopPlayerShortcutInput(DesktopPlayerShortcutKey.DOWN)),
        )
        assertEquals(
            DesktopPlayerShortcut.CYCLE_PLAYBACK_RATE,
            resolveDesktopPlayerShortcut(DesktopPlayerShortcutInput(DesktopPlayerShortcutKey.R)),
        )
        assertEquals(
            DesktopPlayerShortcut.CYCLE_AUDIO_TRACK,
            resolveDesktopPlayerShortcut(DesktopPlayerShortcutInput(DesktopPlayerShortcutKey.A)),
        )
        assertEquals(
            DesktopPlayerShortcut.CYCLE_SUBTITLE_TRACK,
            resolveDesktopPlayerShortcut(DesktopPlayerShortcutInput(DesktopPlayerShortcutKey.S)),
        )
        assertEquals(
            DesktopPlayerShortcut.CYCLE_ASPECT_MODE,
            resolveDesktopPlayerShortcut(DesktopPlayerShortcutInput(DesktopPlayerShortcutKey.V)),
        )
        assertEquals(
            DesktopPlayerShortcut.TOGGLE_FOCUS_MODE,
            resolveDesktopPlayerShortcut(DesktopPlayerShortcutInput(DesktopPlayerShortcutKey.H)),
        )
        assertEquals(
            DesktopPlayerShortcut.TOGGLE_FULLSCREEN,
            resolveDesktopPlayerShortcut(DesktopPlayerShortcutInput(DesktopPlayerShortcutKey.F)),
        )
    }

    @Test
    fun ignoresSystemModifiedShortcuts() {
        assertNull(
            resolveDesktopPlayerShortcut(
                DesktopPlayerShortcutInput(
                    key = DesktopPlayerShortcutKey.F,
                    ctrlOrMetaPressed = true,
                ),
            ),
        )
        assertNull(
            resolveDesktopPlayerShortcut(
                DesktopPlayerShortcutInput(
                    key = DesktopPlayerShortcutKey.F,
                    altPressed = true,
                ),
            ),
        )
    }
}
