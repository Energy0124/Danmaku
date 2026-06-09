package app.danmaku.desktop

import app.danmaku.domain.DanmakuEvent
import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeInfo
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeTitleSet
import app.danmaku.domain.LocalDanmakuParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.util.Base64

class DandanplayConnection(
    baseUrl: String = DEFAULT_BASE_URL,
    appId: String? = null,
    appSecret: String? = null,
    val authenticationMode: DandanplayAuthenticationMode = DandanplayAuthenticationMode.SIGNED,
) {
    val baseUri: URI = validatedBaseUri(baseUrl)
    internal val appId: String? = appId?.trim()?.takeIf(String::isNotEmpty)
    internal val appSecret: String? = appSecret?.trim()?.takeIf(String::isNotEmpty)

    init {
        require((this.appId == null) == (this.appSecret == null)) {
            "dandanplay AppId and AppSecret must be configured together"
        }
    }

    val hasCredentials: Boolean
        get() = appId != null && appSecret != null

    override fun toString(): String =
        "DandanplayConnection(baseUri=$baseUri, appId=${appId ?: "<none>"}, appSecret=<redacted>, authenticationMode=$authenticationMode)"

    companion object {
        const val DEFAULT_BASE_URL = "https://api.dandanplay.net"

        private fun validatedBaseUri(baseUrl: String): URI {
            val uri = URI(baseUrl.trim())
            require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
                "dandanplay base URL must use http or https"
            }
            require(!uri.host.isNullOrBlank()) { "dandanplay base URL must include a host" }
            require(uri.userInfo == null) { "dandanplay base URL must not include user info" }
            require(uri.query == null) { "dandanplay base URL must not include a query" }
            require(uri.fragment == null) { "dandanplay base URL must not include a fragment" }
            return URI(uri.toString().trimEnd('/') + "/")
        }
    }
}

enum class DandanplayAuthenticationMode {
    SIGNED,
    CREDENTIAL,
}

class DandanplayDanmakuClient(
    private val connection: DandanplayConnection = DandanplayConnection(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
    },
    private val clock: Clock = Clock.systemUTC(),
    private val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS,
    private val maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
) {
    init {
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis must be positive" }
        require(readTimeoutMillis > 0) { "readTimeoutMillis must be positive" }
        require(maxResponseBytes in 1 until Int.MAX_VALUE) {
            "maxResponseBytes must be positive and less than Int.MAX_VALUE"
        }
    }

    fun match(fingerprint: DandanplayMediaFingerprint): List<DandanplayMatch> {
        val data = requestJson(
            method = "POST",
            apiPath = "/api/v2/match",
            body = fingerprint.toMatchRequest(),
        ).asObject()
        check(data.boolean("success") != false) {
            "dandanplay match failed: ${data.string("message") ?: "unknown error"}"
        }
        return data.array("matches")
            .mapNotNull(JsonElement::asObjectOrNull)
            .mapNotNull(JsonObject::toDandanplayMatch)
    }

    fun fetchComments(
        episodeId: Long,
        withRelated: Boolean = false,
    ): List<DanmakuEvent> {
        require(episodeId > 0) { "episodeId must be positive" }
        val apiPath = "/api/v2/comment/$episodeId"
        val query = if (withRelated) "withRelated=true" else null
        val data = requestJson(
            method = "GET",
            apiPath = apiPath,
            query = query,
        ).asObject()
        check(data.boolean("success") != false) {
            "dandanplay comment fetch failed: ${data.string("message") ?: "unknown error"}"
        }
        return data.array("comments")
            .mapIndexedNotNull { index, element ->
                element.asObjectOrNull()?.toDanmakuEvent(index)
            }
    }

    fun fetchBestMatchComments(
        fingerprint: DandanplayMediaFingerprint,
        withRelated: Boolean = true,
    ): DandanplayCommentTrack? {
        val bestMatch = match(fingerprint).firstOrNull() ?: return null
        return DandanplayCommentTrack(
            match = bestMatch,
            events = fetchComments(bestMatch.episodeId, withRelated),
        )
    }

    fun fetchAnimeDetails(animeId: Long): ExternalAnimeInfo {
        require(animeId > 0) { "animeId must be positive" }
        val data = requestJson(
            method = "GET",
            apiPath = "/api/v2/bangumi/$animeId",
        ).asObject()
        check(data.boolean("success") != false) {
            "dandanplay anime detail fetch failed: ${data.string("message") ?: data.string("errorMessage") ?: "unknown error"}"
        }
        val anime = data["bangumi"]?.asObjectOrNull()
            ?: error("dandanplay anime detail response did not include bangumi")
        return anime.toExternalAnimeInfo(animeId)
    }

    private fun requestJson(
        method: String,
        apiPath: String,
        query: String? = null,
        body: JsonObject? = null,
    ): JsonElement {
        val urlPath = apiPath.trimStart('/')
        val endpoint = if (query == null) urlPath else "$urlPath?$query"
        var requestUrl = connection.baseUri.resolve(endpoint).toURL()
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val httpConnection = openHttpConnection(
                url = requestUrl,
                method = method,
                apiPath = apiPath,
                body = body,
                authenticate = redirectCount == 0,
            )

            try {
                if (body != null) {
                    httpConnection.outputStream.bufferedWriter().use {
                        it.write(body.toString())
                    }
                }
                val status = httpConnection.responseCode
                val responseBody = httpConnection.responseStream(status).use { input ->
                    val bytes = input.readNBytes(maxResponseBytes + 1)
                    check(bytes.size <= maxResponseBytes) {
                        "dandanplay response exceeded $maxResponseBytes bytes"
                    }
                    bytes.toString(Charsets.UTF_8)
                }
                if (httpConnection.shouldFollowDandanplayRedirect(method, status, body)) {
                    check(redirectCount < MAX_REDIRECTS) {
                        "dandanplay redirect limit exceeded for $requestUrl"
                    }
                    requestUrl = requestUrl.resolveHttpRedirect(httpConnection.getHeaderField("Location"))
                    return@repeat
                }
                check(status == HttpURLConnection.HTTP_OK) {
                    httpConnection.dandanplayHttpErrorMessage(status, responseBody)
                }
                return json.parseToJsonElement(responseBody)
            } finally {
                httpConnection.disconnect()
            }
        }
        error("dandanplay redirect limit exceeded for $requestUrl")
    }

    private fun openHttpConnection(
        url: URL,
        method: String,
        apiPath: String,
        body: JsonObject?,
        authenticate: Boolean,
    ): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            setRequestProperty("Accept", "application/json")
            if (authenticate) {
                setAuthenticationHeaders(apiPath)
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

    private fun HttpURLConnection.dandanplayHttpErrorMessage(
        status: Int,
        responseBody: String,
    ): String =
        buildString {
            append("dandanplay returned HTTP ")
            append(status)
            append(" for ")
            append(url)
            getHeaderField("Location")
                ?.takeIf(String::isNotBlank)
                ?.let { location ->
                    append("; redirected to ")
                    append(location)
                }
            responseBody
                .take(256)
                .takeIf(String::isNotBlank)
                ?.let { body ->
                    append("; body=")
                    append(body.replaceLineBreaks())
                }
        }

    private fun HttpURLConnection.setAuthenticationHeaders(apiPath: String) {
        if (!connection.hasCredentials) return

        val appId = connection.appId ?: return
        val appSecret = connection.appSecret ?: return
        setRequestProperty("X-AppId", appId)
        when (connection.authenticationMode) {
            DandanplayAuthenticationMode.CREDENTIAL -> {
                setRequestProperty("X-AppSecret", appSecret)
            }
            DandanplayAuthenticationMode.SIGNED -> {
                val timestamp = clock.instant().epochSecond
                setRequestProperty("X-Timestamp", timestamp.toString())
                setRequestProperty("X-Signature", generateSignature(appId, timestamp, apiPath.lowercase(), appSecret))
            }
        }
    }

    private fun HttpURLConnection.responseStream(status: Int) =
        if (status >= HttpURLConnection.HTTP_BAD_REQUEST) {
            errorStream ?: inputStream
        } else {
            inputStream
        }

    private fun HttpURLConnection.shouldFollowDandanplayRedirect(
        method: String,
        status: Int,
        body: JsonObject?,
    ): Boolean =
        body == null &&
            method.equals("GET", ignoreCase = true) &&
            status in DANDANPLAY_REDIRECT_STATUSES &&
            !getHeaderField("Location").isNullOrBlank()

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 5_000
        const val DEFAULT_READ_TIMEOUT_MILLIS = 15_000
        const val DEFAULT_MAX_RESPONSE_BYTES = 16 * 1024 * 1024
        const val MAX_REDIRECTS = 5
        val DANDANPLAY_REDIRECT_STATUSES = setOf(
            HttpURLConnection.HTTP_MOVED_PERM,
            HttpURLConnection.HTTP_MOVED_TEMP,
            HttpURLConnection.HTTP_SEE_OTHER,
            307,
            308,
        )
    }
}

data class DandanplayMediaFingerprint(
    val fileName: String,
    val fileHash: String,
    val fileSizeBytes: Long,
    val videoDurationSeconds: Long? = null,
) {
    init {
        require(fileName.isNotBlank()) { "fileName must not be blank" }
        require(fileHash.matches(Regex("[A-Fa-f0-9]{32}"))) { "fileHash must be a 32-character MD5 hex digest" }
        require(fileSizeBytes >= 0) { "fileSizeBytes must not be negative" }
        require(videoDurationSeconds == null || videoDurationSeconds >= 0) {
            "videoDurationSeconds must not be negative"
        }
    }

    val normalizedFileHash: String = fileHash.lowercase()

    fun toMatchRequest(): JsonObject =
        buildJsonObject {
            put("fileName", fileName)
            put("fileHash", normalizedFileHash)
            put("fileSize", fileSizeBytes)
            put("matchMode", "hashAndFileName")
            videoDurationSeconds?.let { put("videoDuration", it) }
        }

    companion object {
        private const val HASH_PREFIX_BYTES = 16 * 1024 * 1024

        fun fromPath(path: Path, videoDurationSeconds: Long? = null): DandanplayMediaFingerprint {
            require(Files.isRegularFile(path)) { "media path must be a file: $path" }
            return DandanplayMediaFingerprint(
                fileName = path.fileName.toString(),
                fileHash = calculatePrefixMd5(path),
                fileSizeBytes = Files.size(path),
                videoDurationSeconds = videoDurationSeconds,
            )
        }

        private fun calculatePrefixMd5(path: Path): String {
            val digest = MessageDigest.getInstance("MD5")
            Files.newInputStream(path).use { input ->
                val buffer = ByteArray(64 * 1024)
                var remaining = HASH_PREFIX_BYTES
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                    remaining -= read
                }
            }
            return digest.digest().joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
        }
    }
}

data class DandanplayMatch(
    val episodeId: Long,
    val animeId: Long?,
    val animeTitle: String?,
    val episodeTitle: String?,
    val shiftSeconds: Double?,
) {
    val displayTitle: String =
        listOfNotNull(animeTitle, episodeTitle)
            .joinToString(" - ")
            .ifBlank { episodeId.toString() }
}

data class DandanplayCommentTrack(
    val match: DandanplayMatch,
    val events: List<DanmakuEvent>,
)

private fun generateSignature(
    appId: String,
    timestamp: Long,
    apiPath: String,
    appSecret: String,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$appId$timestamp$apiPath$appSecret".toByteArray(Charsets.UTF_8))
    return Base64.getEncoder().encodeToString(digest)
}

private fun JsonObject.toDandanplayMatch(): DandanplayMatch? {
    val episodeId = long("episodeId")
        ?: long("EpisodeId")
        ?: return null
    return DandanplayMatch(
        episodeId = episodeId,
        animeId = long("animeId") ?: long("AnimeId"),
        animeTitle = string("animeTitle") ?: string("AnimeTitle"),
        episodeTitle = string("episodeTitle") ?: string("EpisodeTitle"),
        shiftSeconds = double("shift") ?: double("Shift"),
    )
}

private fun JsonObject.toExternalAnimeInfo(fallbackAnimeId: Long): ExternalAnimeInfo {
    val animeId = long("animeId")
        ?: long("AnimeId")
        ?: long("id")
        ?: fallbackAnimeId
    val primaryTitle = string("animeTitle")
        ?: string("AnimeTitle")
        ?: string("name")
        ?: string("Name")
        ?: "dandanplay anime $animeId"
    val parsedTitles = array("titles")
        .mapNotNull(JsonElement::asObjectOrNull)
        .mapNotNull { titleObject ->
            val title = titleObject.string("title") ?: return@mapNotNull null
            title to (titleObject.string("language") ?: "")
        }
    val chineseTitle = parsedTitles
        .firstOrNull { (_, language) ->
            language.contains("zh", ignoreCase = true) || language.contains("cn", ignoreCase = true)
        }
        ?.first
    val alternateNames = parsedTitles
        .map { it.first }
        .filterNot { it.equals(primaryTitle, ignoreCase = true) || it == chineseTitle }
        .distinct()
    val imageUrl = (string("imageUrl") ?: string("ImageUrl"))
        ?.toHttpsUrlOrNull()
    val episodeCount = array("episodes")
        .takeIf { it.isNotEmpty() }
        ?.size
        ?: long("episodeCount")?.toInt()
        ?: long("EpisodeCount")?.toInt()
    val startYear = (string("airDate") ?: string("AirDate"))
        ?.take(4)
        ?.toIntOrNull()
        ?.takeIf { it in 1900..2200 }
    return ExternalAnimeInfo(
        id = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, animeId),
        titles = ExternalAnimeTitleSet(
            primary = primaryTitle,
            chinese = chineseTitle,
            alternateNames = alternateNames,
        ),
        episodeCount = episodeCount,
        startYear = startYear,
        imageUrl = imageUrl,
        summary = (string("summary") ?: string("Summary"))?.takeIf(String::isNotBlank),
    )
}

private fun JsonObject.toDanmakuEvent(index: Int): DanmakuEvent? {
    val parameter = string("p")
        ?: string("P")
        ?: string("parameter")
        ?: return null
    val text = string("m")
        ?: string("M")
        ?: string("text")
        ?: string("Text")
        ?: return null
    val fallbackId = string("cid")
        ?: string("id")
        ?: string("Id")
        ?: "dandanplay-$index"
    return LocalDanmakuParser.parseBilibiliParameterString(
        parameter = parameter,
        text = text,
        fallbackId = fallbackId,
    )
}

private fun JsonElement.asObject(): JsonObject =
    asObjectOrNull() ?: JsonObject(emptyMap())

private fun JsonElement.asObjectOrNull(): JsonObject? =
    this as? JsonObject

private fun JsonElement.asArray(): JsonArray =
    this as? JsonArray ?: JsonArray(emptyList())

private fun JsonObject.array(key: String): JsonArray =
    get(key)?.asArray() ?: JsonArray(emptyList())

private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull

private fun JsonObject.boolean(key: String): Boolean? =
    (get(key) as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.long(key: String): Long? =
    (get(key) as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 }

private fun JsonObject.double(key: String): Double? =
    (get(key) as? JsonPrimitive)
        ?.jsonPrimitive
        ?.contentOrNull
        ?.toDoubleOrNull()

private fun URL.resolveHttpRedirect(location: String?): URL {
    val redirectLocation = location?.takeIf(String::isNotBlank)
        ?: error("dandanplay redirect did not include a Location header")
    val redirectUri = URI(redirectLocation)
    val resolvedUri = if (redirectUri.isAbsolute) {
        redirectUri
    } else {
        toURI().resolve(redirectUri)
    }
    require(resolvedUri.scheme.equals("http", ignoreCase = true) || resolvedUri.scheme.equals("https", ignoreCase = true)) {
        "dandanplay redirect must use http or https"
    }
    require(resolvedUri.host != null) {
        "dandanplay redirect must include a host"
    }
    return resolvedUri.toURL()
}

private fun String.replaceLineBreaks(): String =
    replace('\r', ' ').replace('\n', ' ')

private fun String.toHttpsUrlOrNull(): String? =
    trim()
        .takeIf(String::isNotBlank)
        ?.let { url ->
            when {
                url.startsWith("https://", ignoreCase = true) -> url
                url.startsWith("http://", ignoreCase = true) -> "https://${url.drop("http://".length)}"
                else -> null
            }
        }
