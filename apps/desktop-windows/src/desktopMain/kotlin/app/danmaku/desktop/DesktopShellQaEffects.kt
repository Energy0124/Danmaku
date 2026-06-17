package app.danmaku.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Window as AwtWindow

@Composable
internal fun DesktopShellQaScreenshotEffect(
    awtWindow: AwtWindow,
    launchOptions: DesktopLaunchOptions,
    navigationState: DesktopShellNavigationState,
    onRequestExit: () -> Unit,
    appendDiagnostic: (String, String) -> Unit,
) {
    LaunchedEffect(launchOptions.qaScreenshot) {
        val qaScreenshot = launchOptions.qaScreenshot ?: return@LaunchedEffect
        appendDiagnostic(
            "qa",
            "Waiting ${qaScreenshot.delay.inWholeSeconds}s before desktop screenshot capture",
        )
        delay(qaScreenshot.delay)
        runCatching {
            withContext(Dispatchers.IO) {
                DesktopQaScreenshotCapture.capture(
                    window = awtWindow,
                    options = qaScreenshot,
                    language = navigationState.desktopLanguage,
                    selectedTab = navigationState.selectedTab,
                )
            }
        }.onSuccess { result ->
            appendDiagnostic(
                "qa",
                "Captured desktop screenshot ${result.width}x${result.height}: ${result.imagePath}",
            )
            if (qaScreenshot.autoExit) {
                onRequestExit()
            }
        }.onFailure { error ->
            appendDiagnostic(
                "qa",
                "Desktop screenshot capture failed: ${error.message ?: error::class.simpleName}",
            )
            if (qaScreenshot.autoExit) {
                onRequestExit()
            }
        }
    }
}
