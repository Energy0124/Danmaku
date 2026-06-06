package app.danmaku.desktop

import app.danmaku.domain.DanmakuEvent
import app.danmaku.domain.DanmakuStyle
import app.danmaku.domain.MeasuredDanmakuEvent
import app.danmaku.domain.ScrollingDanmakuLaneScheduler
import app.danmaku.domain.ScrollingDanmakuLayoutConfig
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class DesktopSyntheticDanmakuAssTrack private constructor(
    val path: Path,
) {
    fun attachTo(commandExecutor: DesktopMpvCommandExecutor) {
        commandExecutor.execute(DesktopMpvCommandPlanner.addSubtitle(path, "Danmaku synthetic overlay"))
    }

    companion object {
        fun createDefault(): DesktopSyntheticDanmakuAssTrack =
            create(events = SYNTHETIC_DANMAKU_EVENTS)

        internal fun create(
            events: List<DanmakuEvent>,
            outputPath: Path = Files.createTempFile("danmaku-synthetic-overlay-", ".ass"),
        ): DesktopSyntheticDanmakuAssTrack {
            Files.writeString(
                outputPath,
                SyntheticDanmakuAssRenderer.render(events),
                StandardCharsets.UTF_8,
            )
            outputPath.toFile().deleteOnExit()
            return DesktopSyntheticDanmakuAssTrack(outputPath)
        }
    }
}

internal object SyntheticDanmakuAssRenderer {
    private const val PLAY_RES_X = 1280
    private const val PLAY_RES_Y = 720
    private const val TRAVEL_DURATION_MS = 7_000L
    private const val LANE_COUNT = 8
    private const val LANE_HEIGHT = 58
    private const val HORIZONTAL_GAP_PX = 36f

    fun render(events: List<DanmakuEvent>): String {
        val measuredEvents = events.map { event ->
            MeasuredDanmakuEvent(
                event = event,
                widthPx = estimateTextWidthPx(event.text),
            )
        }
        val schedule = ScrollingDanmakuLaneScheduler.schedule(
            events = measuredEvents,
            config = ScrollingDanmakuLayoutConfig(
                viewportWidthPx = PLAY_RES_X.toFloat(),
                laneCount = LANE_COUNT,
                travelDurationMs = TRAVEL_DURATION_MS,
                horizontalGapPx = HORIZONTAL_GAP_PX,
            ),
        )

        return buildString {
            appendLine("[Script Info]")
            appendLine("ScriptType: v4.00+")
            appendLine("PlayResX: $PLAY_RES_X")
            appendLine("PlayResY: $PLAY_RES_Y")
            appendLine("ScaledBorderAndShadow: yes")
            appendLine()
            appendLine("[V4+ Styles]")
            appendLine(
                "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, " +
                    "OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, " +
                    "ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, " +
                    "Alignment, MarginL, MarginR, MarginV, Encoding",
            )
            appendLine(
                "Style: Danmaku,Arial,36,&H00FFFFFF,&H00FFFFFF,&H80000000,&H40000000," +
                    "-1,0,0,0,100,100,0,0,1,2,1,7,0,0,0,1",
            )
            appendLine()
            appendLine("[Events]")
            appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
            schedule.placements.forEach { placement ->
                val startMs = placement.measuredEvent.event.timestampMs
                val endMs = startMs + TRAVEL_DURATION_MS
                val y = 24 + placement.laneIndex * LANE_HEIGHT
                val width = placement.measuredEvent.widthPx.toInt()
                val text = placement.measuredEvent.event.text.escapeAssText()
                appendLine(
                    "Dialogue: 0,${startMs.toAssTimestamp()},${endMs.toAssTimestamp()}," +
                        "Danmaku,,0,0,0,," +
                        "{\\move($PLAY_RES_X,$y,${-width},$y)\\an7}$text",
                )
            }
        }
    }

    private fun estimateTextWidthPx(text: String): Float =
        (text.length * 22 + 32).toFloat()

    private fun Long.toAssTimestamp(): String {
        val totalCentiseconds = this.coerceAtLeast(0) / 10
        val centiseconds = totalCentiseconds % 100
        val totalSeconds = totalCentiseconds / 100
        val seconds = totalSeconds % 60
        val totalMinutes = totalSeconds / 60
        val minutes = totalMinutes % 60
        val hours = totalMinutes / 60
        return "$hours:%02d:%02d.%02d".format(minutes, seconds, centiseconds)
    }

    private fun String.escapeAssText(): String =
        replace("\\", "\\\\")
            .replace("{", "\\{")
            .replace("}", "\\}")
            .replace("\n", "\\N")
}

internal fun DesktopMpvCommandPlanner.addSubtitle(
    path: Path,
    title: String,
): DesktopMpvCommand =
    addSubtitle(
        source = path.toAbsolutePath().normalize().absolutePathString(),
        title = title,
    )

internal fun DesktopMpvCommandPlanner.addSubtitle(
    source: String,
    title: String,
): DesktopMpvCommand =
    DesktopMpvCommand(
        listOf(
            "sub-add",
            source,
            "select",
            title,
        ),
    )

private val SYNTHETIC_DANMAKU_EVENTS = listOf(
    syntheticDanmakuEvent(id = "one", timestampMs = 0, text = "Shared Kotlin scheduler"),
    syntheticDanmakuEvent(id = "two", timestampMs = 650, text = "Now rendered on top of mpv"),
    syntheticDanmakuEvent(id = "three", timestampMs = 1_100, text = "Collision-aware lanes"),
    syntheticDanmakuEvent(id = "four", timestampMs = 1_750, text = "Seek-safe deterministic layout"),
    syntheticDanmakuEvent(id = "five", timestampMs = 2_900, text = "Desktop ASS overlay path"),
    syntheticDanmakuEvent(id = "six", timestampMs = 4_200, text = "Real danmaku parser comes next"),
    syntheticDanmakuEvent(id = "seven", timestampMs = 6_400, text = "Looping synthetic track"),
    syntheticDanmakuEvent(id = "eight", timestampMs = 7_200, text = "Danmaku"),
)

private fun syntheticDanmakuEvent(
    id: String,
    timestampMs: Long,
    text: String,
): DanmakuEvent =
    DanmakuEvent(
        id = id,
        timestampMs = timestampMs,
        text = text,
        style = DanmakuStyle(),
    )
