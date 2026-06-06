package app.danmaku.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

enum class DesktopPlaybackShortcut {
    TOGGLE_PLAY_PAUSE,
    SEEK_BACKWARD,
    SEEK_BACKWARD_LARGE,
    SEEK_FORWARD,
    SEEK_FORWARD_LARGE,
    VOLUME_UP,
    VOLUME_DOWN,
    TOGGLE_FULLSCREEN,
    EXIT_FULLSCREEN,
    OPEN_MEDIA,
}

fun KeyEvent.toDesktopPlaybackShortcut(): DesktopPlaybackShortcut? {
    if (type != KeyEventType.KeyDown) {
        return null
    }
    return desktopPlaybackShortcutForKey(
        key = key,
        shiftPressed = isShiftPressed,
        ctrlPressed = isCtrlPressed,
        metaPressed = isMetaPressed,
    )
}

internal fun desktopPlaybackShortcutForKey(
    key: Key,
    shiftPressed: Boolean = false,
    ctrlPressed: Boolean = false,
    metaPressed: Boolean = false,
): DesktopPlaybackShortcut? {
    if (ctrlPressed || metaPressed) {
        return null
    }
    return when (key) {
        Key.Spacebar,
        Key.K,
        -> DesktopPlaybackShortcut.TOGGLE_PLAY_PAUSE
        Key.DirectionLeft -> if (shiftPressed) {
            DesktopPlaybackShortcut.SEEK_BACKWARD_LARGE
        } else {
            DesktopPlaybackShortcut.SEEK_BACKWARD
        }
        Key.DirectionRight -> if (shiftPressed) {
            DesktopPlaybackShortcut.SEEK_FORWARD_LARGE
        } else {
            DesktopPlaybackShortcut.SEEK_FORWARD
        }
        Key.DirectionUp -> DesktopPlaybackShortcut.VOLUME_UP
        Key.DirectionDown -> DesktopPlaybackShortcut.VOLUME_DOWN
        Key.F -> DesktopPlaybackShortcut.TOGGLE_FULLSCREEN
        Key.Escape -> DesktopPlaybackShortcut.EXIT_FULLSCREEN
        Key.O -> DesktopPlaybackShortcut.OPEN_MEDIA
        else -> null
    }
}
