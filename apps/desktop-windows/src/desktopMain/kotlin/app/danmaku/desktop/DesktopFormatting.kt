package app.danmaku.desktop

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DIAGNOSTIC_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss")

internal fun Long.toDiagnosticTime(): String =
    DIAGNOSTIC_TIME_FORMATTER.format(
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()),
    )

internal fun Long.formatEpochTime(): String =
    DIAGNOSTIC_TIME_FORMATTER.format(
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()),
    )

internal fun Double.formatConfidence(): String =
    "${((coerceIn(0.0, 1.0) * 100.0) + 0.5).toInt()}%"

internal fun Long.formatPlaybackTime(): String {
    val totalSeconds = (this / 1_000).coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

internal fun Long.formatLibrarySize(): String {
    val bytes = coerceAtLeast(0).toDouble()
    val gib = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gib >= 1.0) {
        String.format(Locale.US, "%.1f GiB", gib)
    } else {
        String.format(Locale.US, "%.1f MiB", bytes / (1024.0 * 1024.0))
    }
}
