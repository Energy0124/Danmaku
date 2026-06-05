package app.danmaku.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object LocalDanmakuParser {
    fun parseBilibiliXml(source: String): List<DanmakuEvent> =
        DANMAKU_XML_ELEMENT_REGEX
            .findAll(source)
            .mapIndexedNotNull { index, match ->
                val attributes = match.groupValues[1].parseXmlAttributes()
                val text = match.groupValues[2].decodeXmlText().trim()
                    .takeIf(String::isNotBlank)
                    ?: return@mapIndexedNotNull null
                parseBilibiliParameterString(
                    parameter = attributes["p"] ?: return@mapIndexedNotNull null,
                    text = text,
                    fallbackId = "xml-$index",
                )
            }
            .toList()

    fun parseBilibiliParameterString(
        parameter: String,
        text: String,
        fallbackId: String,
    ): DanmakuEvent? {
        val parts = parameter
            .split(',')
            .map(String::trim)
        val timestampMs = parts.getOrNull(0)
            ?.toDoubleOrNull()
            ?.takeIf { it >= 0 }
            ?.let { (it * 1_000.0).toLong() }
            ?: return null
        val trimmedText = text.trim()
            .takeIf(String::isNotBlank)
            ?: return null

        return DanmakuEvent(
            id = parts.getOrNull(7)?.takeIf(String::isNotBlank) ?: fallbackId,
            timestampMs = timestampMs,
            text = trimmedText,
            style = DanmakuStyle(
                colorArgb = parts.getOrNull(3)?.toArgbColor() ?: DEFAULT_DANMAKU_COLOR,
                mode = parts.getOrNull(1)?.toBilibiliDanmakuMode() ?: DanmakuMode.SCROLLING,
                size = parts.getOrNull(2)?.toDanmakuSize() ?: DanmakuSize.NORMAL,
            ),
        )
    }

    fun parseNormalizedJson(source: String): List<DanmakuEvent> {
        val root = Json.parseToJsonElement(source)
        val items = when (root) {
            is JsonArray -> root
            is JsonObject -> root["events"] as? JsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        return items.mapIndexedNotNull { index, element ->
            val item = element as? JsonObject ?: return@mapIndexedNotNull null
            val timestampMs = item.long("timestampMs")
                ?: item.long("timeMs")
                ?: item.doubleSecondsToMillis("time")
                ?: return@mapIndexedNotNull null
            val text = item.string("text")
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: return@mapIndexedNotNull null
            val style = (item["style"] as? JsonObject) ?: item

            DanmakuEvent(
                id = item.string("id")?.takeIf(String::isNotBlank) ?: "json-$index",
                timestampMs = timestampMs,
                text = text,
                style = DanmakuStyle(
                    colorArgb = style.color("colorArgb")
                        ?: style.color("color")
                        ?: DEFAULT_DANMAKU_COLOR,
                    mode = style.string("mode")?.toDanmakuMode() ?: DanmakuMode.SCROLLING,
                    size = style.string("size")?.toDanmakuSize() ?: DanmakuSize.NORMAL,
                ),
            )
        }
    }
}

private fun String.parseXmlAttributes(): Map<String, String> =
    XML_ATTRIBUTE_REGEX
        .findAll(this)
        .associate { match -> match.groupValues[1] to match.groupValues[2].decodeXmlText() }

private fun String.decodeXmlText(): String =
    replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")

private fun String.toBilibiliDanmakuMode(): DanmakuMode? =
    when (toIntOrNull()) {
        4 -> DanmakuMode.BOTTOM
        5 -> DanmakuMode.TOP
        1, 2, 3 -> DanmakuMode.SCROLLING
        else -> null
    }

private fun String.toDanmakuMode(): DanmakuMode? =
    when (uppercase()) {
        "SCROLLING", "SCROLL", "ROLLING" -> DanmakuMode.SCROLLING
        "TOP" -> DanmakuMode.TOP
        "BOTTOM" -> DanmakuMode.BOTTOM
        else -> null
    }

private fun String.toDanmakuSize(): DanmakuSize? =
    when (uppercase()) {
        "SMALL" -> DanmakuSize.SMALL
        "NORMAL" -> DanmakuSize.NORMAL
        "LARGE" -> DanmakuSize.LARGE
        else -> toIntOrNull()?.toDanmakuSize()
    }

private fun Int.toDanmakuSize(): DanmakuSize =
    when {
        this < 25 -> DanmakuSize.SMALL
        this > 25 -> DanmakuSize.LARGE
        else -> DanmakuSize.NORMAL
    }

private fun String.toArgbColor(): UInt? {
    val trimmed = trim()
    if (trimmed.startsWith("#")) {
        return trimmed.drop(1).toRgbColor()
    }
    return trimmed.toLongOrNull()
        ?.takeIf { it in 0..0xFFFFFFFF }
        ?.let {
            if (it <= 0xFFFFFF) {
                0xFF000000u or it.toUInt()
            } else {
                it.toUInt()
            }
        }
}

private fun String.toRgbColor(): UInt? =
    toLongOrNull(radix = 16)
        ?.takeIf { it in 0..0xFFFFFF }
        ?.let { (0xFF000000u or it.toUInt()) }

private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(key: String): Long? =
    (get(key) as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 }

private fun JsonObject.doubleSecondsToMillis(key: String): Long? =
    (get(key) as? JsonPrimitive)
        ?.jsonPrimitive
        ?.contentOrNull
        ?.toDoubleOrNull()
        ?.takeIf { it >= 0 }
        ?.let { (it * 1_000.0).toLong() }

private fun JsonObject.color(key: String): UInt? {
    val primitive = get(key) as? JsonPrimitive ?: return null
    return primitive.contentOrNull
        ?.toArgbColor()
}

private val DANMAKU_XML_ELEMENT_REGEX =
    Regex("<d\\s+([^>]*)>(.*?)</d>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

private val XML_ATTRIBUTE_REGEX =
    Regex("""([A-Za-z_:][A-Za-z0-9_.:-]*)\s*=\s*"([^"]*)"""")

private const val DEFAULT_DANMAKU_COLOR: UInt = 0xFFFFFFFFu
