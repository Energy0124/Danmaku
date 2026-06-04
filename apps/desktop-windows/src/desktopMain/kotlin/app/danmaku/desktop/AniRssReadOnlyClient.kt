package app.danmaku.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URI

class AniRssConnection(
    baseUrl: String,
    apiKey: String,
) {
    val baseUri: URI = validatedBaseUri(baseUrl)
    internal val apiKey: String = apiKey.trim().also {
        require(it.isNotEmpty()) { "ani-rss API key must not be blank" }
    }

    override fun toString(): String =
        "AniRssConnection(baseUri=$baseUri, apiKey=<redacted>)"

    private companion object {
        fun validatedBaseUri(baseUrl: String): URI {
            val uri = URI(baseUrl.trim())
            require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
                "ani-rss base URL must use http or https"
            }
            require(!uri.host.isNullOrBlank()) { "ani-rss base URL must include a host" }
            require(uri.userInfo == null) { "ani-rss base URL must not include user info" }
            require(uri.query == null) { "ani-rss base URL must not include a query" }
            require(uri.fragment == null) { "ani-rss base URL must not include a fragment" }
            return URI(uri.toString().trimEnd('/') + "/")
        }
    }
}

class AniRssReadOnlyClient(
    private val connection: AniRssConnection,
    private val json: Json = Json {
        ignoreUnknownKeys = true
    },
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

    fun ping(): AniRssHealth {
        postData(endpoint = "ping", authenticated = false)
        return AniRssHealth(available = true)
    }

    fun fetchAbout(): AniRssAbout {
        val data = postData("about").asObject()
        return AniRssAbout(
            version = data.string("version"),
            latestVersion = data.string("latest"),
            updateAvailable = data.boolean("update"),
            autoUpdateEnabled = data.boolean("autoUpdate"),
        )
    }

    fun fetchSubscriptions(): List<AniRssSubscription> =
        rawSubscriptions().map { raw ->
            val outputFolder = postData("downloadPath", raw)
                .asObject()
                .string("downloadPath")
            val completedEpisodes = postData("playList", raw)
                .asArray()
                .mapNotNull(JsonElement::asObjectOrNull)
                .map(::completedEpisode)

            AniRssSubscription(
                id = raw.string("id"),
                title = raw.string("title")
                    ?: raw.string("mikanTitle")
                    ?: raw.string("id")
                    ?: "Untitled subscription",
                enabled = raw.boolean("enable"),
                currentEpisodeNumber = raw.int("currentEpisodeNumber"),
                totalEpisodeNumber = raw.int("totalEpisodeNumber"),
                outputFolder = outputFolder,
                completedEpisodes = completedEpisodes,
            )
        }

    fun fetchDownloadObservations(): List<AniRssDownloadObservation> =
        postData("torrentsInfos")
            .asArray()
            .mapNotNull(JsonElement::asObjectOrNull)
            .map { item ->
                AniRssDownloadObservation(
                    id = item.string("id"),
                    name = item.string("name") ?: item.string("id") ?: "Unnamed download",
                    state = item.string("state"),
                    progressPercent = item.double("progress"),
                    completedBytes = item.long("completed"),
                    sizeBytes = item.long("size"),
                    downloadDirectory = item.string("downloadDir"),
                )
            }

    fun fetchSnapshot(): AniRssSnapshot =
        AniRssSnapshot(
            about = fetchAbout(),
            subscriptions = fetchSubscriptions(),
            downloads = fetchDownloadObservations(),
        )

    private fun rawSubscriptions(): List<JsonObject> {
        val data = postData("listAni").asObject()
        return data.array("weekList")
            .flatMap { it.asObject().array("items") }
            .mapNotNull(JsonElement::asObjectOrNull)
            .distinctBy { item ->
                item.string("id")
                    ?: item.string("url")
                    ?: item.string("title")
                    ?: item.toString()
            }
    }

    private fun completedEpisode(item: JsonObject): AniRssCompletedEpisode =
        AniRssCompletedEpisode(
            title = item.string("title")
                ?: item.string("name")
                ?: "Untitled episode",
            fileName = item.string("name"),
            episode = item.double("episode"),
            lastModifiedEpochMs = item.long("lastModify"),
            formattedSize = item.string("formatSize"),
            extension = item.string("extName"),
        )

    private fun postData(
        endpoint: String,
        body: JsonObject? = null,
        authenticated: Boolean = true,
    ): JsonElement {
        val connection = open(endpoint).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            if (authenticated) {
                setRequestProperty(API_KEY_HEADER, this@AniRssReadOnlyClient.connection.apiKey)
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        return try {
            if (body != null) {
                connection.outputStream.bufferedWriter().use {
                    it.write(body.toString())
                }
            }
            val status = connection.responseCode
            val responseBody = connection.responseStream(status).use { input ->
                val bytes = input.readNBytes(maxResponseBytes + 1)
                check(bytes.size <= maxResponseBytes) {
                    "ani-rss response exceeded $maxResponseBytes bytes"
                }
                bytes.toString(Charsets.UTF_8)
            }
            check(status == HttpURLConnection.HTTP_OK) {
                "ani-rss returned HTTP $status"
            }
            val envelope = json.parseToJsonElement(responseBody).asObject()
            check(envelope.int("code") == HttpURLConnection.HTTP_OK) {
                "ani-rss API request failed: ${envelope.string("message") ?: "unknown error"}"
            }
            envelope["data"] ?: JsonNull
        } finally {
            connection.disconnect()
        }
    }

    private fun open(endpoint: String): HttpURLConnection =
        (connection.baseUri.resolve("api/$endpoint").toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
        }

    private fun HttpURLConnection.responseStream(status: Int) =
        if (status >= HttpURLConnection.HTTP_BAD_REQUEST) {
            errorStream ?: inputStream
        } else {
            inputStream
        }

    private companion object {
        const val API_KEY_HEADER = "api-key"
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 5_000
        const val DEFAULT_READ_TIMEOUT_MILLIS = 10_000
        const val DEFAULT_MAX_RESPONSE_BYTES = 4 * 1024 * 1024
    }
}

data class AniRssHealth(
    val available: Boolean,
)

data class AniRssAbout(
    val version: String?,
    val latestVersion: String?,
    val updateAvailable: Boolean?,
    val autoUpdateEnabled: Boolean?,
)

data class AniRssSubscription(
    val id: String?,
    val title: String,
    val enabled: Boolean?,
    val currentEpisodeNumber: Int?,
    val totalEpisodeNumber: Int?,
    val outputFolder: String?,
    val completedEpisodes: List<AniRssCompletedEpisode>,
)

data class AniRssCompletedEpisode(
    val title: String,
    val fileName: String?,
    val episode: Double?,
    val lastModifiedEpochMs: Long?,
    val formattedSize: String?,
    val extension: String?,
)

data class AniRssDownloadObservation(
    val id: String?,
    val name: String,
    val state: String?,
    val progressPercent: Double?,
    val completedBytes: Long?,
    val sizeBytes: Long?,
    val downloadDirectory: String?,
)

data class AniRssSnapshot(
    val about: AniRssAbout,
    val subscriptions: List<AniRssSubscription>,
    val downloads: List<AniRssDownloadObservation>,
)

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

private fun JsonObject.int(key: String): Int? =
    (get(key) as? JsonPrimitive)?.intOrNull

private fun JsonObject.long(key: String): Long? =
    (get(key) as? JsonPrimitive)?.longOrNull

private fun JsonObject.double(key: String): Double? =
    (get(key) as? JsonPrimitive)?.doubleOrNull
