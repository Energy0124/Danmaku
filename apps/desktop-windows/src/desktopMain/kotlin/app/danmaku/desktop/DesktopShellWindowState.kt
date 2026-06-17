package app.danmaku.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.delay
import java.awt.Rectangle
import java.awt.Window as AwtWindow

internal class DesktopShellWindowState(
    private val awtWindow: AwtWindow,
    private val windowState: WindowState,
    private val appendDiagnostic: (String, String) -> Unit,
) {
    private var floatingWindowBounds by mutableStateOf(Rectangle(awtWindow.bounds))
    var pendingFloatingWindowRestore by mutableStateOf(false)
        private set

    val isFullscreen: Boolean
        get() = windowState.placement == WindowPlacement.Fullscreen

    fun setFullscreen(enabled: Boolean, source: String) {
        appendDiagnostic("playback", "$source set window fullscreen ${if (enabled) "on" else "off"}")
        if (enabled) {
            pendingFloatingWindowRestore = false
            floatingWindowBounds = Rectangle(awtWindow.bounds)
            windowState.placement = WindowPlacement.Fullscreen
        } else {
            windowState.placement = WindowPlacement.Floating
            pendingFloatingWindowRestore = true
        }
    }

    suspend fun restoreFloatingBoundsIfPending() {
        if (isFullscreen || !pendingFloatingWindowRestore) {
            return
        }
        delay(150)
        awtWindow.bounds = awtWindow.scaledRestoreBounds(floatingWindowBounds)
        awtWindow.validate()
        delay(150)
        awtWindow.bounds = awtWindow.scaledRestoreBounds(floatingWindowBounds)
        awtWindow.validate()
        pendingFloatingWindowRestore = false
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
    val shellWindowState = remember(awtWindow, windowState) {
        DesktopShellWindowState(
            awtWindow = awtWindow,
            windowState = windowState,
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
