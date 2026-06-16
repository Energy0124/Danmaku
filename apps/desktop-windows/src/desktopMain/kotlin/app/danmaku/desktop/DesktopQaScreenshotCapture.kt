package app.danmaku.desktop

import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Window as AwtWindow
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import javax.imageio.ImageIO

internal data class DesktopQaScreenshotResult(
    val imagePath: Path,
    val manifestPath: Path,
    val width: Int,
    val height: Int,
)

internal object DesktopQaScreenshotCapture {
    fun capture(
        window: AwtWindow,
        options: DesktopQaScreenshotOptions,
        language: DesktopUiLanguage,
        selectedTab: DesktopShellTab,
    ): DesktopQaScreenshotResult {
        check(!GraphicsEnvironment.isHeadless()) {
            "Desktop QA screenshot capture requires a graphical desktop session."
        }

        val imagePath = options.outputPath(language, selectedTab)
        Files.createDirectories(imagePath.parent)

        val bounds = readWindowBounds(window)
        val image = Robot().createScreenCapture(bounds)
        ImageIO.write(image, "png", imagePath.toFile())

        val manifestPath = options.outputDirectory.resolve("manifest.txt").toAbsolutePath().normalize()
        Files.createDirectories(manifestPath.parent)
        Files.writeString(
            manifestPath,
            buildString {
                appendLine("Desktop app-level screenshot QA")
                appendLine("Captured: ${Instant.now()}")
                appendLine("Language: ${language.storageValue}")
                appendLine("Tab: ${selectedTab.qaFileSegment()}")
                appendLine("Window: ${bounds.width}x${bounds.height}")
                appendLine("Screenshot: $imagePath")
                appendLine()
            },
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )

        return DesktopQaScreenshotResult(
            imagePath = imagePath,
            manifestPath = manifestPath,
            width = image.width,
            height = image.height,
        )
    }

    private fun readWindowBounds(window: AwtWindow): Rectangle =
        onAwtEventThread {
            check(window.isDisplayable && window.isShowing) {
                "Danmaku window is not displayable or showing yet."
            }
            window.toFront()
            window.requestFocus()
            val location = window.locationOnScreen
            val size = window.size
            check(size.width > 0 && size.height > 0) {
                "Danmaku window has invalid screenshot bounds: ${size.width}x${size.height}."
            }
            Rectangle(location.x, location.y, size.width, size.height)
        }

    private fun <T> onAwtEventThread(block: () -> T): T {
        if (EventQueue.isDispatchThread()) {
            return block()
        }

        var result: Result<T>? = null
        EventQueue.invokeAndWait {
            result = runCatching(block)
        }
        return result?.getOrThrow() ?: error("AWT screenshot read did not return a result.")
    }

    private fun DesktopQaScreenshotOptions.outputPath(
        language: DesktopUiLanguage,
        selectedTab: DesktopShellTab,
    ): Path {
        val defaultFileName = "desktop-qa-${language.storageValue}-${selectedTab.qaFileSegment()}.png"
        val resolvedFileName = fileName
            ?.takeIf(String::isNotBlank)
            ?.let(::ensurePngSuffix)
            ?: defaultFileName
        return outputDirectory.resolve(resolvedFileName).toAbsolutePath().normalize()
    }

    private fun ensurePngSuffix(fileName: String): String =
        if (fileName.endsWith(".png", ignoreCase = true)) {
            fileName
        } else {
            "$fileName.png"
        }

    private fun DesktopShellTab.qaFileSegment(): String =
        name.lowercase().replace('_', '-')
}
