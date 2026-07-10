package app.danmaku.desktop

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DesktopDiagnosticFileLog private constructor(
    val appLogPath: Path,
    val mpvLogPath: Path,
) : AutoCloseable {
    private var isClosed = false
    private val writer = Files.newBufferedWriter(
        appLogPath,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND,
    )

    @Synchronized
    fun append(
        occurredAtEpochMs: Long,
        category: String,
        message: String,
    ) {
        if (isClosed) {
            return
        }
        writer.write(
            buildString {
                append(LOG_TIME_FORMATTER.format(Instant.ofEpochMilli(occurredAtEpochMs).atZone(ZoneId.systemDefault())))
                append('\t')
                append(category)
                append('\t')
                append(message.replaceLineBreaks())
                append(System.lineSeparator())
            },
        )
        writer.flush()
    }

    @Synchronized
    override fun close() {
        if (isClosed) {
            return
        }
        isClosed = true
        writer.close()
    }

    companion object {
        fun createDefault(): DesktopDiagnosticFileLog {
            val logDirectory = defaultLogDirectory()
            Files.createDirectories(logDirectory)
            val stamp = "${FILE_STAMP_FORMATTER.format(Instant.now().atZone(ZoneId.systemDefault()))}-${ProcessHandle.current().pid()}"
            return DesktopDiagnosticFileLog(
                appLogPath = logDirectory.resolve("danmaku-$stamp.log"),
                mpvLogPath = logDirectory.resolve("mpv-$stamp.log"),
            )
        }

        private fun defaultLogDirectory(): Path {
            val appDataRoot = System.getenv("LOCALAPPDATA")
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?: Path.of(System.getProperty("user.home"), ".danmaku")
            return appDataRoot.resolve("Danmaku").resolve("logs")
        }
    }
}

private fun String.replaceLineBreaks(): String =
    replace('\r', ' ').replace('\n', ' ')

private val FILE_STAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")

private val LOG_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
