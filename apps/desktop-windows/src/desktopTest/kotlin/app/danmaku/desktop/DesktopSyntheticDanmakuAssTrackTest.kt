package app.danmaku.desktop

import app.danmaku.domain.DanmakuEvent
import app.danmaku.domain.DanmakuDisplaySettings
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopSyntheticDanmakuAssTrackTest {
    @Test
    fun rendersScrollingAssEventsForMpvSubtitleOverlay() {
        val ass = SyntheticDanmakuAssRenderer.render(
            listOf(
                DanmakuEvent(id = "one", timestampMs = 1_230, text = "hello {world}"),
            ),
        )

        assertContains(ass, "PlayResX: 1280")
        assertContains(ass, "Style: Danmaku")
        assertContains(ass, "Dialogue: 0,0:00:01.23,0:00:08.23,Danmaku")
        assertContains(ass, "{\\move(1280,24,-")
        assertContains(ass, "hello \\{world\\}")
    }

    @Test
    fun appliesDanmakuDisplaySettingsToRenderedAss() {
        val ass = SyntheticDanmakuAssRenderer.render(
            events = listOf(
                DanmakuEvent(id = "one", timestampMs = 1_000, text = "safe comment"),
                DanmakuEvent(id = "two", timestampMs = 2_000, text = "spoiler comment"),
            ),
            settings = DanmakuDisplaySettings(
                opacityPercent = 80,
                fontScalePercent = 125,
                speedPercent = 200,
                offsetMs = 2_500,
                keywordFilters = listOf("spoiler"),
            ),
        )

        assertContains(ass, "Style: Danmaku,Arial,45,&H33FFFFFF")
        assertContains(ass, "Dialogue: 0,0:00:03.50,0:00:07.00,Danmaku")
        assertContains(ass, "safe comment")
        assertTrue("spoiler comment" !in ass)
    }

    @Test
    fun clampsNegativeOffsetAtZeroForAssTimestamps() {
        val ass = SyntheticDanmakuAssRenderer.render(
            events = listOf(DanmakuEvent(id = "one", timestampMs = 1_000, text = "early comment")),
            settings = DanmakuDisplaySettings(offsetMs = -2_000),
        )

        assertContains(ass, "Dialogue: 0,0:00:00.00,0:00:07.00,Danmaku")
    }

    @Test
    fun hiddenDanmakuSettingsRenderNoDialogueRows() {
        val ass = SyntheticDanmakuAssRenderer.render(
            events = listOf(DanmakuEvent(id = "one", timestampMs = 1_000, text = "hidden")),
            settings = DanmakuDisplaySettings(visible = false),
        )

        assertTrue("Dialogue:" !in ass)
    }

    @Test
    fun createsSubtitleFileAndAttachesItToMpv() {
        val outputPath = Files.createTempFile("danmaku-test-overlay-", ".ass")
        val track = DesktopSyntheticDanmakuAssTrack.create(
            events = listOf(DanmakuEvent(id = "one", timestampMs = 0, text = "overlay")),
            outputPath = outputPath,
        )
        val executor = RecordingDesktopMpvCommandExecutor()

        track.attachTo(executor)

        assertTrue(outputPath.readText().contains("overlay"))
        assertEquals(
            listOf(
                DesktopMpvCommand(
                    listOf(
                        "sub-add",
                        outputPath.toAbsolutePath().normalize().toString(),
                        "select",
                        "Danmaku synthetic overlay",
                    ),
                ),
            ),
            executor.commands,
        )
    }

    private class RecordingDesktopMpvCommandExecutor : DesktopMpvCommandExecutor {
        val commands = mutableListOf<DesktopMpvCommand>()

        override fun execute(command: DesktopMpvCommand) {
            commands += command
        }
    }
}
