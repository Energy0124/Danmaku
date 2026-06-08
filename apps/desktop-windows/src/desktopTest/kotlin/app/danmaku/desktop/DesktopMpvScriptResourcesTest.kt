package app.danmaku.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopMpvScriptResourcesTest {
    @Test
    fun installsBundledDanmakuOscScript() {
        val targetDirectory = Files.createTempDirectory("danmaku-osc-test")

        val scriptPath = DesktopMpvScriptResources.installDanmakuOscScript(targetDirectory)
        val script = Files.readString(scriptPath)

        assertEquals(targetDirectory.toAbsolutePath().normalize().resolve("danmaku-osc.lua"), scriptPath)
        assertTrue(script.contains("danmaku-osc-left-click"))
        assertTrue(script.contains("mp.create_osd_overlay"))
        assertEquals(scriptPath, DesktopMpvScriptResources.installDanmakuOscScript(targetDirectory))
    }
}
