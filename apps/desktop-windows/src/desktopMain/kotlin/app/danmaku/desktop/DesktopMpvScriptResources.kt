package app.danmaku.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

object DesktopMpvScriptResources {
    private const val DANMAKU_OSC_RESOURCE = "/mpv/danmaku-osc.lua"
    private const val DANMAKU_OSC_FILE_NAME = "danmaku-osc.lua"

    fun installDanmakuOscScript(
        targetDirectory: Path = defaultScriptDirectory(),
    ): Path {
        val scriptBytes = DesktopMpvScriptResources::class.java
            .getResourceAsStream(DANMAKU_OSC_RESOURCE)
            ?.use { it.readBytes() }
            ?: error("Missing mpv OSC script resource: $DANMAKU_OSC_RESOURCE")
        Files.createDirectories(targetDirectory)
        val target = targetDirectory.resolve(DANMAKU_OSC_FILE_NAME).toAbsolutePath().normalize()
        val needsWrite = !Files.isRegularFile(target) || !Files.readAllBytes(target).contentEquals(scriptBytes)
        if (needsWrite) {
            Files.write(
                target,
                scriptBytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
        }
        val installedBytes = Files.readAllBytes(target)
        check(installedBytes.contentEquals(scriptBytes)) {
            "Installed mpv OSC script did not match bundled resource: $target"
        }
        return target
    }

    private fun defaultScriptDirectory(): Path {
        val root = System.getenv("LOCALAPPDATA")
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?: Path.of(System.getProperty("user.home"), ".danmaku")
        return root.resolve("Danmaku").resolve("logs").resolve("mpv-scripts")
    }
}
