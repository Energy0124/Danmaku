package app.danmaku.desktop

internal enum class DesktopPlayerShortcut {
    TOGGLE_PLAY_PAUSE,
    SEEK_BACKWARD,
    SEEK_BACKWARD_LARGE,
    SEEK_FORWARD,
    SEEK_FORWARD_LARGE,
    VOLUME_UP,
    VOLUME_DOWN,
    CYCLE_PLAYBACK_RATE,
    CYCLE_AUDIO_TRACK,
    CYCLE_SUBTITLE_TRACK,
    CYCLE_ASPECT_MODE,
    TOGGLE_FULLSCREEN,
}

internal enum class DesktopPlayerShortcutKey {
    SPACE,
    K,
    LEFT,
    RIGHT,
    UP,
    DOWN,
    R,
    A,
    S,
    V,
    F,
}

internal data class DesktopPlayerShortcutInput(
    val key: DesktopPlayerShortcutKey,
    val shiftPressed: Boolean = false,
    val ctrlOrMetaPressed: Boolean = false,
    val altPressed: Boolean = false,
)

internal fun resolveDesktopPlayerShortcut(input: DesktopPlayerShortcutInput): DesktopPlayerShortcut? {
    if (input.ctrlOrMetaPressed || input.altPressed) return null

    return when (input.key) {
        DesktopPlayerShortcutKey.SPACE,
        DesktopPlayerShortcutKey.K,
            -> DesktopPlayerShortcut.TOGGLE_PLAY_PAUSE
        DesktopPlayerShortcutKey.LEFT -> if (input.shiftPressed) {
            DesktopPlayerShortcut.SEEK_BACKWARD_LARGE
        } else {
            DesktopPlayerShortcut.SEEK_BACKWARD
        }
        DesktopPlayerShortcutKey.RIGHT -> if (input.shiftPressed) {
            DesktopPlayerShortcut.SEEK_FORWARD_LARGE
        } else {
            DesktopPlayerShortcut.SEEK_FORWARD
        }
        DesktopPlayerShortcutKey.UP -> DesktopPlayerShortcut.VOLUME_UP
        DesktopPlayerShortcutKey.DOWN -> DesktopPlayerShortcut.VOLUME_DOWN
        DesktopPlayerShortcutKey.R -> DesktopPlayerShortcut.CYCLE_PLAYBACK_RATE
        DesktopPlayerShortcutKey.A -> DesktopPlayerShortcut.CYCLE_AUDIO_TRACK
        DesktopPlayerShortcutKey.S -> DesktopPlayerShortcut.CYCLE_SUBTITLE_TRACK
        DesktopPlayerShortcutKey.V -> DesktopPlayerShortcut.CYCLE_ASPECT_MODE
        DesktopPlayerShortcutKey.F -> DesktopPlayerShortcut.TOGGLE_FULLSCREEN
    }
}
