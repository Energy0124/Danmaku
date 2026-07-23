package app.danmaku.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.delay
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.Window as AwtWindow

internal class DesktopFullscreenTaskbarController(
    private val isWindows: Boolean,
    private val readAlwaysOnTop: () -> Boolean,
    private val writeAlwaysOnTop: (Boolean) -> Unit,
    private val bringToFront: () -> Unit,
) {
    private var isFullscreen = false
    private var previousAlwaysOnTop: Boolean? = null

    fun setFullscreen(enabled: Boolean) {
        if (!isWindows || enabled == isFullscreen) {
            return
        }
        isFullscreen = enabled
        if (enabled) {
            previousAlwaysOnTop = readAlwaysOnTop()
            promoteAboveTaskbar()
        } else {
            restoreAlwaysOnTop()
        }
    }

    fun setWindowFocused(focused: Boolean) {
        if (!isWindows || !isFullscreen) {
            return
        }
        if (focused) {
            promoteAboveTaskbar()
        } else {
            previousAlwaysOnTop?.let(writeAlwaysOnTop)
        }
    }

    private fun promoteAboveTaskbar() {
        writeAlwaysOnTop(true)
        bringToFront()
    }

    private fun restoreAlwaysOnTop() {
        previousAlwaysOnTop?.let(writeAlwaysOnTop)
        previousAlwaysOnTop = null
    }
}

internal class DesktopShellWindowState(
    private val awtWindow: AwtWindow,
    private val windowState: WindowState,
    private val fullscreenTaskbarController: DesktopFullscreenTaskbarController,
    private val appendDiagnostic: (String, String) -> Unit,
) {
    private var floatingWindowBounds by mutableStateOf(awtWindow.captureFloatingWindowRestoreBounds())
    private var floatingWindowSize by mutableStateOf(windowState.size)
    private var floatingWindowPosition by mutableStateOf(windowState.position)
    var pendingFloatingWindowRestore by mutableStateOf(false)
        private set

    val isFullscreen: Boolean
        get() = windowState.placement == WindowPlacement.Fullscreen

    fun setFullscreen(enabled: Boolean, source: String) {
        appendDiagnostic("playback", "$source set window fullscreen ${if (enabled) "on" else "off"}")
        if (enabled) {
            pendingFloatingWindowRestore = false
            floatingWindowBounds = awtWindow.captureFloatingWindowRestoreBounds()
            floatingWindowSize = windowState.size
            floatingWindowPosition = windowState.position
            appendDiagnostic(
                "playback",
                "Saved floating window ${floatingWindowBounds.describe()} " +
                    "size=${floatingWindowSize.width}x${floatingWindowSize.height} " +
                    "position=${floatingWindowPosition.x},${floatingWindowPosition.y} ${awtWindow.windowScaleDescription()}",
            )
            windowState.placement = WindowPlacement.Fullscreen
            fullscreenTaskbarController.setFullscreen(true)
        } else {
            fullscreenTaskbarController.setFullscreen(false)
            windowState.placement = WindowPlacement.Floating
            pendingFloatingWindowRestore = true
        }
    }

    suspend fun restoreFloatingBoundsIfPending() {
        if (isFullscreen || !pendingFloatingWindowRestore) {
            return
        }
        delay(150)
        restoreFloatingWindowState()
        delay(150)
        restoreFloatingWindowState()
        pendingFloatingWindowRestore = false
    }

    private fun restoreFloatingWindowState() {
        val restoreBounds = awtWindow.floatingRestoreBounds(floatingWindowBounds)
        appendDiagnostic(
            "playback",
            "Restoring floating window ${restoreBounds.x},${restoreBounds.y} ${restoreBounds.width}x${restoreBounds.height} " +
                "from ${floatingWindowBounds.describe()} size=${floatingWindowSize.width}x${floatingWindowSize.height} " +
                "position=${floatingWindowPosition.x},${floatingWindowPosition.y} ${awtWindow.windowScaleDescription()}",
        )
        awtWindow.bounds = restoreBounds
        windowState.size = floatingWindowSize
        windowState.position = floatingWindowPosition
        awtWindow.invalidate()
        awtWindow.validate()
        awtWindow.repaint()
    }
}

@Composable
internal fun rememberDesktopShellWindowState(
    awtWindow: AwtWindow,
    windowState: WindowState,
    hostPlatform: DesktopHostPlatform,
    mpvRuntime: DesktopMpvCommandExecutorRuntime,
    appendDiagnostic: (String, String) -> Unit,
): DesktopShellWindowState {
    val fullscreenTaskbarController = remember(awtWindow, hostPlatform) {
        DesktopFullscreenTaskbarController(
            isWindows = hostPlatform == DesktopHostPlatform.WINDOWS,
            readAlwaysOnTop = { awtWindow.isAlwaysOnTop },
            writeAlwaysOnTop = { awtWindow.isAlwaysOnTop = it },
            bringToFront = awtWindow::toFront,
        )
    }
    DisposableEffect(awtWindow, fullscreenTaskbarController) {
        val focusListener = object : WindowAdapter() {
            override fun windowGainedFocus(event: WindowEvent) {
                fullscreenTaskbarController.setWindowFocused(true)
            }

            override fun windowLostFocus(event: WindowEvent) {
                fullscreenTaskbarController.setWindowFocused(false)
            }
        }
        awtWindow.addWindowFocusListener(focusListener)
        onDispose {
            awtWindow.removeWindowFocusListener(focusListener)
            fullscreenTaskbarController.setFullscreen(false)
        }
    }
    val shellWindowState = remember(awtWindow, windowState, fullscreenTaskbarController) {
        DesktopShellWindowState(
            awtWindow = awtWindow,
            windowState = windowState,
            fullscreenTaskbarController = fullscreenTaskbarController,
            appendDiagnostic = appendDiagnostic,
        )
    }
    val isFullscreen = shellWindowState.isFullscreen
    LaunchedEffect(isFullscreen, shellWindowState.pendingFloatingWindowRestore) {
        shellWindowState.restoreFloatingBoundsIfPending()
    }
    LaunchedEffect(mpvRuntime, hostPlatform, isFullscreen) {
        if (hostPlatform != DesktopHostPlatform.WINDOWS) {
            return@LaunchedEffect
        }
        runCatching {
            mpvRuntime.executor.execute(
                DesktopMpvCommand(
                    listOf(
                        "script-binding",
                        if (isFullscreen) {
                            MPV_OSC_APP_FULLSCREEN_ON_BINDING
                        } else {
                            MPV_OSC_APP_FULLSCREEN_OFF_BINDING
                        },
                    ),
                ),
            )
        }.onFailure { error ->
            appendDiagnostic("mpv", "Custom OSC fullscreen state sync failed: ${error.message ?: error::class.simpleName}")
        }
    }
    LaunchedEffect(mpvRuntime, hostPlatform, isFullscreen) {
        if (hostPlatform != DesktopHostPlatform.WINDOWS) {
            return@LaunchedEffect
        }
        val reader = mpvRuntime.propertyReader ?: return@LaunchedEffect
        var lastRequest = reader.readProperty(MPV_OSC_FULLSCREEN_REQUEST_PROPERTY)
        while (true) {
            delay(200)
            val nextRequest = reader.readProperty(MPV_OSC_FULLSCREEN_REQUEST_PROPERTY)
            if (!nextRequest.isNullOrBlank() && nextRequest != lastRequest) {
                lastRequest = nextRequest
                shellWindowState.setFullscreen(!isFullscreen, "mpv OSC")
            }
        }
    }
    return shellWindowState
}
