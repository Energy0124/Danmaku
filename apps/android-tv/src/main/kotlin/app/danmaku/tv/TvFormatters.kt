package app.danmaku.tv

internal fun Long.formatPlaybackTime(): String {
    val totalSeconds = coerceAtLeast(0) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

internal fun String.initials(): String =
    trim()
        .split(' ', '.', '_', '-', '[', ']')
        .filter(String::isNotBlank)
        .take(2)
        .joinToString(separator = "") { it.first().uppercaseChar().toString() }
        .ifBlank { "?" }
