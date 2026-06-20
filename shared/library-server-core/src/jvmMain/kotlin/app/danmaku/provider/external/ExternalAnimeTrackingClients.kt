package app.danmaku.provider.external

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeListEntry
import app.danmaku.domain.ExternalAnimeListStatus
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeTrackingUpdate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

interface ExternalAnimeTrackingClient {
    val provider: ExternalAnimeProvider

    fun fetchListEntry(animeId: ExternalAnimeId): ExternalAnimeListEntry?

    fun updateListEntry(update: ExternalAnimeTrackingUpdate): ExternalAnimeListEntry
}

data class MyAnimeListTrackingConnection(
    val accessToken: String,
    val baseUri: URI = URI("https://api.myanimelist.net/v2/"),
) {
    init {
        require(accessToken.isNotBlank()) { "accessToken must not be blank" }
        require(baseUri.scheme == "https") { "MyAnimeList baseUri must be HTTPS" }
    }
}

class MyAnimeListTrackingClient(
    private val connection: MyAnimeListTrackingConnection,
    private val httpGet: ExternalAnimeHttpGet = ExternalAnimeHttpGet.default(),
    private val httpPatch: ExternalAnimeHttpPatch = ExternalAnimeHttpPatch.default(),
    private val json: Json = externalAnimeTrackingJson,
) : ExternalAnimeTrackingClient {
    override val provider: ExternalAnimeProvider = ExternalAnimeProvider.MY_ANIME_LIST

    override fun fetchListEntry(animeId: ExternalAnimeId): ExternalAnimeListEntry? {
        require(animeId.provider == provider) { "animeId provider must be MyAnimeList" }
        val fields = "my_list_status"
        val response = httpGet.get(
            url = connection.baseUri.resolve("anime/${animeId.value}?fields=${fields.urlEncode()}").toURL(),
            headers = mapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer ${connection.accessToken}",
            ),
        )
        val listStatus = json
            .parseToJsonElement(response)
            .asObject()
            .objectOrNull("my_list_status")
            ?: return null
        return listStatus.toMyAnimeListEntry(animeId)
    }

    override fun updateListEntry(update: ExternalAnimeTrackingUpdate): ExternalAnimeListEntry {
        require(update.animeId.provider == provider) { "update animeId provider must be MyAnimeList" }
        val fields = buildList {
            update.status?.let { add("status" to it.toMyAnimeListStatus()) }
            if (update.trackingEnabled) {
                update.watchedEpisodes?.let { add("num_watched_episodes" to it.toString()) }
            }
            if (update.ratingEnabled) {
                update.score?.let { add("score" to it.toString()) }
            }
        }
        require(fields.isNotEmpty()) { "MyAnimeList update must include at least one field" }
        val response = httpPatch.patch(
            url = connection.baseUri.resolve("anime/${update.animeId.value}/my_list_status").toURL(),
            headers = mapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer ${connection.accessToken}",
                "Content-Type" to "application/x-www-form-urlencoded",
            ),
            body = fields.formEncode(),
        )
        val root = json.parseToJsonElement(response).asObject()
        return ExternalAnimeListEntry(
            animeId = update.animeId,
            status = root.string("status")?.toMyAnimeListStatus(),
            watchedEpisodes = root.int("num_episodes_watched") ?: update.watchedEpisodes,
            score = root.int("score") ?: update.score,
        )
    }
}

data class BangumiTrackingConnection(
    val accessToken: String,
    val baseUri: URI = URI(DEFAULT_EXTERNAL_BANGUMI_BASE_URL),
    val userAgent: String = DEFAULT_EXTERNAL_BANGUMI_USER_AGENT,
) {
    init {
        require(accessToken.isNotBlank()) { "accessToken must not be blank" }
        require(baseUri.scheme == "https") { "Bangumi baseUri must be HTTPS" }
        require(userAgent.isNotBlank()) { "userAgent must not be blank" }
    }
}

class BangumiTrackingClient(
    private val connection: BangumiTrackingConnection,
    private val httpGet: ExternalAnimeHttpGet = ExternalAnimeHttpGet.default(),
    private val httpPatch: ExternalAnimeHttpPatch = ExternalAnimeHttpPatch.default(),
    private val json: Json = externalAnimeTrackingJson,
) : ExternalAnimeTrackingClient {
    override val provider: ExternalAnimeProvider = ExternalAnimeProvider.BANGUMI

    override fun fetchListEntry(animeId: ExternalAnimeId): ExternalAnimeListEntry? {
        require(animeId.provider == provider) { "animeId provider must be Bangumi" }
        val response = httpGet.get(
            url = connection.baseUri.resolve("v0/users/-/collections/${animeId.value}").toURL(),
            headers = mapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer ${connection.accessToken}",
                "User-Agent" to connection.userAgent,
            ),
        )
        return json
            .parseToJsonElement(response)
            .asObject()
            .toBangumiEntry(animeId)
    }

    override fun updateListEntry(update: ExternalAnimeTrackingUpdate): ExternalAnimeListEntry {
        require(update.animeId.provider == provider) { "update animeId provider must be Bangumi" }
        val body = buildJsonObject {
            update.status?.let { put("type", it.toBangumiCollectionType()) }
            if (update.trackingEnabled) {
                update.watchedEpisodes?.let { put("ep_status", it) }
            }
            if (update.ratingEnabled) {
                update.score?.let { put("rate", it) }
            }
        }.toString()
        require(body != "{}") { "Bangumi update must include at least one field" }
        val response = httpPatch.patch(
            url = connection.baseUri.resolve("v0/users/-/collections/${update.animeId.value}").toURL(),
            headers = mapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer ${connection.accessToken}",
                "Content-Type" to "application/json",
                "User-Agent" to connection.userAgent,
            ),
            body = body,
        )
        val root = json.parseToJsonElement(response).asObject()
        return ExternalAnimeListEntry(
            animeId = update.animeId,
            status = root.int("type")?.toBangumiListStatus(),
            watchedEpisodes = root.int("ep_status") ?: update.watchedEpisodes,
            score = root.int("rate") ?: update.score,
        )
    }
}

fun interface ExternalAnimeHttpGet {
    fun get(url: URL, headers: Map<String, String>): String

    companion object {
        fun default(): ExternalAnimeHttpGet =
            ExternalAnimeHttpGet { url, headers ->
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = DEFAULT_TRACKING_CONNECT_TIMEOUT_MILLIS
                    readTimeout = DEFAULT_TRACKING_READ_TIMEOUT_MILLIS
                    headers.forEach(::setRequestProperty)
                }.readExternalAnimeResponse()
            }
    }
}

class ExternalAnimeProviderException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

private fun HttpURLConnection.readExternalAnimeResponse(): String =
    try {
        val status = responseCode
        val responseBody = (if (status >= HttpURLConnection.HTTP_BAD_REQUEST) errorStream else inputStream)
            .use { input ->
                val bytes = input.readNBytes(DEFAULT_TRACKING_MAX_RESPONSE_BYTES + 1)
                if (bytes.size > DEFAULT_TRACKING_MAX_RESPONSE_BYTES) {
                    throw ExternalAnimeProviderException(
                        "external anime tracking response exceeded $DEFAULT_TRACKING_MAX_RESPONSE_BYTES bytes",
                    )
                }
                bytes.toString(Charsets.UTF_8)
            }
        if (status !in 200..299) {
            throw ExternalAnimeProviderException(
                "external anime tracking request failed with HTTP $status: ${responseBody.take(200)}",
            )
        }
        responseBody
    } finally {
        disconnect()
    }

fun interface ExternalAnimeHttpPatch {
    fun patch(url: URL, headers: Map<String, String>, body: String): String

    companion object {
        fun default(): ExternalAnimeHttpPatch =
            ExternalAnimeHttpPatch { url, headers, body ->
                val requestBuilder = HttpRequest.newBuilder(url.toURI())
                    .timeout(Duration.ofMillis(DEFAULT_TRACKING_READ_TIMEOUT_MILLIS.toLong()))
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                headers.forEach(requestBuilder::header)
                val response = HttpClient
                    .newBuilder()
                    .connectTimeout(Duration.ofMillis(DEFAULT_TRACKING_CONNECT_TIMEOUT_MILLIS.toLong()))
                    .build()
                    .send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
                val responseBody = response.body()
                if (responseBody.toByteArray(Charsets.UTF_8).size > DEFAULT_TRACKING_MAX_RESPONSE_BYTES) {
                    throw ExternalAnimeProviderException(
                        "external anime tracking response exceeded $DEFAULT_TRACKING_MAX_RESPONSE_BYTES bytes",
                    )
                }
                if (response.statusCode() !in 200..299) {
                    throw ExternalAnimeProviderException(
                        "external anime tracking request failed with HTTP ${response.statusCode()}: ${responseBody.take(200)}",
                    )
                }
                responseBody
            }
    }
}

private fun JsonObject.toMyAnimeListEntry(animeId: ExternalAnimeId): ExternalAnimeListEntry =
    ExternalAnimeListEntry(
        animeId = animeId,
        status = string("status")?.toMyAnimeListStatus(),
        watchedEpisodes = int("num_episodes_watched"),
        score = int("score"),
        updatedAtEpochMs = string("updated_at")?.toEpochMillisOrNull(),
    )

private fun JsonObject.toBangumiEntry(animeId: ExternalAnimeId): ExternalAnimeListEntry =
    ExternalAnimeListEntry(
        animeId = animeId,
        status = int("type")?.toBangumiListStatus(),
        watchedEpisodes = int("ep_status"),
        score = int("rate"),
        updatedAtEpochMs = null,
    )

private fun ExternalAnimeListStatus.toMyAnimeListStatus(): String =
    when (this) {
        ExternalAnimeListStatus.WATCHING -> "watching"
        ExternalAnimeListStatus.COMPLETED -> "completed"
        ExternalAnimeListStatus.ON_HOLD -> "on_hold"
        ExternalAnimeListStatus.DROPPED -> "dropped"
        ExternalAnimeListStatus.PLAN_TO_WATCH -> "plan_to_watch"
    }

private fun String.toMyAnimeListStatus(): ExternalAnimeListStatus? =
    when (this) {
        "watching" -> ExternalAnimeListStatus.WATCHING
        "completed" -> ExternalAnimeListStatus.COMPLETED
        "on_hold" -> ExternalAnimeListStatus.ON_HOLD
        "dropped" -> ExternalAnimeListStatus.DROPPED
        "plan_to_watch" -> ExternalAnimeListStatus.PLAN_TO_WATCH
        else -> null
    }

private fun ExternalAnimeListStatus.toBangumiCollectionType(): Int =
    when (this) {
        ExternalAnimeListStatus.PLAN_TO_WATCH -> 1
        ExternalAnimeListStatus.COMPLETED -> 2
        ExternalAnimeListStatus.WATCHING -> 3
        ExternalAnimeListStatus.ON_HOLD -> 4
        ExternalAnimeListStatus.DROPPED -> 5
    }

private fun Int.toBangumiListStatus(): ExternalAnimeListStatus? =
    when (this) {
        1 -> ExternalAnimeListStatus.PLAN_TO_WATCH
        2 -> ExternalAnimeListStatus.COMPLETED
        3 -> ExternalAnimeListStatus.WATCHING
        4 -> ExternalAnimeListStatus.ON_HOLD
        5 -> ExternalAnimeListStatus.DROPPED
        else -> null
    }

private fun List<Pair<String, String>>.formEncode(): String =
    joinToString("&") { (key, value) -> "${key.urlEncode()}=${value.urlEncode()}" }

private fun String.urlEncode(): String =
    URLEncoder.encode(this, Charsets.UTF_8)

private fun JsonElement.asObject(): JsonObject =
    this as? JsonObject ?: JsonObject(emptyMap())

private fun JsonObject.objectOrNull(key: String): JsonObject? =
    get(key) as? JsonObject

private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(key: String): Int? =
    (get(key) as? JsonPrimitive)?.intOrNull

private fun String.toEpochMillisOrNull(): Long? =
    try {
        Instant.parse(this).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }

private val externalAnimeTrackingJson = Json {
    ignoreUnknownKeys = true
}

private const val DEFAULT_TRACKING_CONNECT_TIMEOUT_MILLIS = 10_000
private const val DEFAULT_TRACKING_READ_TIMEOUT_MILLIS = 20_000
private const val DEFAULT_TRACKING_MAX_RESPONSE_BYTES = 1_000_000
private const val DEFAULT_EXTERNAL_BANGUMI_BASE_URL = "https://api.bgm.tv/"
private const val DEFAULT_EXTERNAL_BANGUMI_USER_AGENT = "Danmaku/0.1 (https://github.com/Energy0124/Danmaku)"
