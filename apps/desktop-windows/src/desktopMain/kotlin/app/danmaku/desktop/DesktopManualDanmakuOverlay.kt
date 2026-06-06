package app.danmaku.desktop

import app.danmaku.domain.DanmakuDisplaySettings
import app.danmaku.domain.LocalDanmakuParser
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.name

data class DesktopManualDanmakuOverlay(
    val inputPath: Path,
    val eventCount: Int,
    val subtitle: DesktopPlaybackSubtitle,
)

object DesktopManualDanmakuOverlayRenderer {
    fun render(
        inputPath: Path,
        settings: DanmakuDisplaySettings = DanmakuDisplaySettings(),
        cacheDirectory: Path = defaultCacheDirectory(),
    ): DesktopManualDanmakuOverlay {
        val normalizedInputPath = inputPath.toAbsolutePath().normalize()
        require(Files.isRegularFile(normalizedInputPath)) {
            "Danmaku file does not exist: $normalizedInputPath"
        }
        val source = Files.readString(normalizedInputPath, StandardCharsets.UTF_8)
        val events = parseEvents(normalizedInputPath, source)
        require(events.isNotEmpty()) {
            "Danmaku file did not contain supported comments: $normalizedInputPath"
        }

        Files.createDirectories(cacheDirectory)
        val outputPath = Files.createTempFile(cacheDirectory, "manual-danmaku-", ".ass")
        Files.writeString(
            outputPath,
            SyntheticDanmakuAssRenderer.render(events, settings),
            StandardCharsets.UTF_8,
        )
        outputPath.toFile().deleteOnExit()

        return DesktopManualDanmakuOverlay(
            inputPath = normalizedInputPath,
            eventCount = events.size,
            subtitle = DesktopPlaybackSubtitle(
                source = outputPath.toAbsolutePath().normalize().absolutePathString(),
                label = "Manual danmaku: ${normalizedInputPath.name}",
                isDanmakuOverlay = true,
            ),
        )
    }

    private fun parseEvents(
        inputPath: Path,
        source: String,
    ) = when (inputPath.extension.lowercase()) {
        "xml" -> LocalDanmakuParser.parseBilibiliXml(source)
        "json", "danmaku" -> LocalDanmakuParser.parseNormalizedJson(source)
        else -> {
            val trimmed = source.trimStart()
            if (trimmed.startsWith("<")) {
                LocalDanmakuParser.parseBilibiliXml(source)
            } else {
                LocalDanmakuParser.parseNormalizedJson(source)
            }
        }
    }

    private fun defaultCacheDirectory(): Path {
        val localAppData = System.getenv("LOCALAPPDATA")
        if (!localAppData.isNullOrBlank()) {
            return Path.of(localAppData).resolve("Danmaku").resolve("danmaku-cache").resolve("manual")
        }
        return Path.of(System.getProperty("java.io.tmpdir")).resolve("Danmaku").resolve("danmaku-cache").resolve("manual")
    }
}
