package app.danmaku.domain

import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class DanmakuDisplaySettings(
    val visible: Boolean = true,
    val opacityPercent: Int = 100,
    val fontScalePercent: Int = 100,
    val speedPercent: Int = 100,
    val densityPercent: Int = 100,
    val displayAreaPercent: Int = 100,
    val offsetMs: Long = 0,
    val keywordFilters: List<String> = emptyList(),
    val regexFilters: List<String> = emptyList(),
) {
    init {
        require(opacityPercent in 0..100) { "opacityPercent must be between 0 and 100" }
        require(fontScalePercent in 50..200) { "fontScalePercent must be between 50 and 200" }
        require(speedPercent in 25..300) { "speedPercent must be between 25 and 300" }
        require(densityPercent in 10..200) { "densityPercent must be between 10 and 200" }
        require(displayAreaPercent in 10..100) { "displayAreaPercent must be between 10 and 100" }
        require(offsetMs in -MAX_OFFSET_MS..MAX_OFFSET_MS) {
            "offsetMs must be between -$MAX_OFFSET_MS and $MAX_OFFSET_MS"
        }
        require(keywordFilters.all { it.isNotBlank() }) { "keywordFilters must not contain blank values" }
        require(regexFilters.all { it.isNotBlank() }) { "regexFilters must not contain blank values" }
    }

    fun filter(events: List<DanmakuEvent>): List<DanmakuEvent> =
        if (!visible) {
            emptyList()
        } else {
            val normalizedKeywords = keywordFilters.map { it.trim().lowercase() }
            val regexes = regexFilters.mapNotNull { pattern -> runCatching { Regex(pattern) }.getOrNull() }
            events.filter { event ->
                val text = event.text
                val normalizedText = text.lowercase()
                normalizedKeywords.none { keyword -> keyword in normalizedText } &&
                    regexes.none { regex -> regex.containsMatchIn(text) }
            }
        }

    fun scaledFontSize(baseFontSize: Int): Int =
        (baseFontSize * fontScalePercent / 100f).roundToInt().coerceAtLeast(1)

    fun scaledTravelDurationMs(baseTravelDurationMs: Long): Long =
        (baseTravelDurationMs * 100.0 / speedPercent).roundToLong().coerceAtLeast(1)

    fun scaledLaneCount(baseLaneCount: Int): Int =
        (baseLaneCount * densityPercent / 100f * displayAreaPercent / 100f)
            .roundToInt()
            .coerceIn(1, baseLaneCount * 2)

    fun assAlphaHex(): String {
        val alpha = ((100 - opacityPercent) * 255 / 100f).roundToInt().coerceIn(0, 255)
        return alpha.toString(radix = 16).uppercase().padStart(2, '0')
    }

    fun shiftedTimestampMs(timestampMs: Long): Long =
        (timestampMs + offsetMs).coerceAtLeast(0)

    private companion object {
        const val MAX_OFFSET_MS = 60L * 60L * 1_000L
    }
}
