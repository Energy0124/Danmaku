package app.danmaku.desktop

import app.danmaku.domain.DanmakuDisplaySettings
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopManualDanmakuOverlayRendererTest {
    @Test
    fun rendersBilibiliXmlDanmakuFileIntoAssOverlay() {
        val temp = createTempDirectory("manual-danmaku-xml")
        val inputPath = temp.resolve("Episode 01.xml").apply {
            writeText("""<i><d p="1.5,1,25,16777215,0,0,0,abc">hello xml</d></i>""")
        }

        val overlay = DesktopManualDanmakuOverlayRenderer.render(
            inputPath = inputPath,
            settings = DanmakuDisplaySettings(offsetMs = 500),
            cacheDirectory = temp.resolve("cache"),
        )

        assertEquals(inputPath.toAbsolutePath().normalize(), overlay.inputPath)
        assertEquals(1, overlay.eventCount)
        assertEquals("Manual danmaku: Episode 01.xml", overlay.subtitle.label)
        assertTrue(overlay.subtitle.isDanmakuOverlay)
        val ass = java.nio.file.Path.of(overlay.subtitle.source).readText()
        assertContains(ass, "Dialogue: 0,0:00:02.00,0:00:09.00,Danmaku")
        assertContains(ass, "hello xml")

        temp.toFile().deleteRecursively()
    }

    @Test
    fun rendersNormalizedJsonDanmakuFileIntoAssOverlay() {
        val temp = createTempDirectory("manual-danmaku-json")
        val inputPath = temp.resolve("Episode 01.json").apply {
            writeText("""{"events":[{"id":"one","timestampMs":1000,"text":"hello json"}]}""")
        }

        val overlay = DesktopManualDanmakuOverlayRenderer.render(
            inputPath = inputPath,
            cacheDirectory = temp.resolve("cache"),
        )

        assertEquals(1, overlay.eventCount)
        val ass = java.nio.file.Path.of(overlay.subtitle.source).readText()
        assertContains(ass, "Dialogue: 0,0:00:01.00,0:00:08.00,Danmaku")
        assertContains(ass, "hello json")

        temp.toFile().deleteRecursively()
    }

    @Test
    fun rejectsFilesWithoutSupportedComments() {
        val temp = createTempDirectory("manual-danmaku-empty")
        val inputPath = temp.resolve("empty.xml").apply {
            writeText("<i></i>")
        }

        assertFailsWith<IllegalArgumentException> {
            DesktopManualDanmakuOverlayRenderer.render(
                inputPath = inputPath,
                cacheDirectory = temp.resolve("cache"),
            )
        }

        temp.toFile().deleteRecursively()
    }
}
