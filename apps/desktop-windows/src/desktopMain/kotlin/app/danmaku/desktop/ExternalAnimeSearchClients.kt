package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeInfo
import app.danmaku.domain.ExternalAnimeMatchQuery
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeTitleSet
import app.danmaku.domain.rankExternalAnimeMatches
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

interface ExternalAnimeSearchClient {
    val provider: ExternalAnimeProvider

    fun search(query: ExternalAnimeMatchQuery, limit: Int = DEFAULT_SEARCH_LIMIT): List<ExternalAnimeInfo>
}

class ExternalAnimeSearchService(
    private val clients: List<ExternalAnimeSearchClient>,
    private val catalogStore: DesktopLibraryCatalogStore,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    init {
        require(clients.map(ExternalAnimeSearchClient::provider).distinct().size == clients.size) {
            "external anime search clients must be unique by provider"
        }
    }

    fun searchAndCache(
        query: ExternalAnimeMatchQuery,
        providers: Set<ExternalAnimeProvider> = clients.mapTo(mutableSetOf(), ExternalAnimeSearchClient::provider),
        limitPerProvider: Int = DEFAULT_SEARCH_LIMIT,
    ): List<app.danmaku.domain.ExternalAnimeMatchCandidate> {
        require(limitPerProvider in 1..50) { "limitPerProvider must be between 1 and 50" }
        val results = clients
            .filter { client -> client.provider in providers }
            .flatMap { client -> client.search(query, limitPerProvider) }
            .distinctBy { anime -> anime.id }
        results.forEach { anime ->
            catalogStore.saveExternalAnimeMetadataCache(
                DesktopExternalAnimeMetadataCache(
                    anime = anime,
                    fetchedAtEpochMs = nowEpochMs(),
                ),
            )
        }
        return rankExternalAnimeMatches(query, results)
    }
}

data class MyAnimeListSearchConnection(
    val clientId: String,
    val baseUri: URI = URI("https://api.myanimelist.net/v2/"),
) {
    init {
        require(clientId.isNotBlank()) { "MyAnimeList clientId must not be blank" }
        require(baseUri.scheme == "https") { "MyAnimeList baseUri must be HTTPS" }
    }
}

class MyAnimeListAnimeSearchClient(
    private val connection: MyAnimeListSearchConnection,
    private val httpGet: ExternalAnimeHttpGet = ExternalAnimeHttpGet.default(),
    private val json: Json = externalAnimeSearchJson,
) : ExternalAnimeSearchClient {
    override val provider: ExternalAnimeProvider = ExternalAnimeProvider.MY_ANIME_LIST

    override fun search(query: ExternalAnimeMatchQuery, limit: Int): List<ExternalAnimeInfo> {
        require(limit in 1..50) { "limit must be between 1 and 50" }
        val fields = "id,title,alternative_titles,num_episodes,start_date,main_picture,synopsis"
        val apiPath = "anime?q=${query.title.urlEncode()}&limit=$limit&fields=${fields.urlEncode()}"
        val body = httpGet.get(
            url = connection.baseUri.resolve(apiPath).toURL(),
            headers = mapOf(
                "Accept" to "application/json",
                "X-MAL-CLIENT-ID" to connection.clientId,
            ),
        )
        return json.parseToJsonElement(body)
            .asObject()
            .array("data")
            .mapNotNull(JsonElement::asObjectOrNull)
            .mapNotNull { item -> item["node"]?.asObjectOrNull()?.toMyAnimeListAnimeInfo() }
    }
}

class BangumiAnimeSearchClient(
    private val baseUri: URI = URI(DEFAULT_BANGUMI_BASE_URL),
    private val userAgent: String = DEFAULT_BANGUMI_USER_AGENT,
    private val httpPost: ExternalAnimeHttpPost = ExternalAnimeHttpPost.default(),
    private val json: Json = externalAnimeSearchJson,
) : ExternalAnimeSearchClient {
    override val provider: ExternalAnimeProvider = ExternalAnimeProvider.BANGUMI

    init {
        require(baseUri.scheme == "https") { "Bangumi baseUri must be HTTPS" }
        require(userAgent.isNotBlank()) { "userAgent must not be blank" }
    }

    override fun search(query: ExternalAnimeMatchQuery, limit: Int): List<ExternalAnimeInfo> {
        require(limit in 1..50) { "limit must be between 1 and 50" }
        val body = buildJsonObject {
            put("keyword", query.title)
            putJsonObject("filter") {
                putJsonArray("type") {
                    add(JsonPrimitive(BANGUMI_ANIME_SUBJECT_TYPE))
                }
            }
        }.toString()
        val response = httpPost.post(
            url = baseUri.resolve("v0/search/subjects?limit=$limit&offset=0").toURL(),
            headers = mapOf(
                "Accept" to "application/json",
                "Content-Type" to "application/json",
                "User-Agent" to userAgent,
            ),
            body = body,
        )
        return json.parseToJsonElement(response)
            .asObject()
            .array("data")
            .mapNotNull(JsonElement::asObjectOrNull)
            .mapNotNull(JsonObject::toBangumiAnimeInfo)
    }
}

fun interface ExternalAnimeHttpGet {
    fun get(url: URL, headers: Map<String, String>): String

    companion object {
        fun default(): ExternalAnimeHttpGet =
            ExternalAnimeHttpGet { url, headers ->
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = DEFAULT_CONNECT_TIMEOUT_MILLIS
                    readTimeout = DEFAULT_READ_TIMEOUT_MILLIS
                    headers.forEach(::setRequestProperty)
                }.readExternalAnimeResponse()
            }
    }
}

fun interface ExternalAnimeHttpPost {
    fun post(url: URL, headers: Map<String, String>, body: String): String

    companion object {
        fun default(): ExternalAnimeHttpPost =
            ExternalAnimeHttpPost { url, headers, body ->
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = DEFAULT_CONNECT_TIMEOUT_MILLIS
                    readTimeout = DEFAULT_READ_TIMEOUT_MILLIS
                    headers.forEach(::setRequestProperty)
                    outputStream.bufferedWriter().use { writer -> writer.write(body) }
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
                val bytes = input.readNBytes(DEFAULT_MAX_RESPONSE_BYTES + 1)
                if (bytes.size > DEFAULT_MAX_RESPONSE_BYTES) {
                    throw ExternalAnimeProviderException(
                        "external anime response exceeded $DEFAULT_MAX_RESPONSE_BYTES bytes",
                    )
                }
                bytes.toString(Charsets.UTF_8)
            }
        if (status !in 200..299) {
            throw ExternalAnimeProviderException(
                "external anime request failed with HTTP $status: ${responseBody.take(200)}",
            )
        }
        responseBody
    } finally {
        disconnect()
    }

private fun JsonObject.toMyAnimeListAnimeInfo(): ExternalAnimeInfo? {
    val id = long("id")?.takeIf { it > 0 } ?: return null
    val primaryTitle = string("title")?.takeIf(String::isNotBlank) ?: return null
    val alternativeTitles = get("alternative_titles")?.asObjectOrNull()
    val synonyms = alternativeTitles
        ?.array("synonyms")
        ?.mapNotNull { element -> element.asStringOrNull()?.takeIf(String::isNotBlank) }
        .orEmpty()
    return ExternalAnimeInfo(
        id = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, id),
        titles = ExternalAnimeTitleSet(
            primary = primaryTitle,
            english = alternativeTitles?.string("en")?.takeIf(String::isNotBlank),
            japanese = alternativeTitles?.string("ja")?.takeIf(String::isNotBlank),
            alternateNames = synonyms,
        ),
        episodeCount = int("num_episodes")?.takeIf { it > 0 },
        startYear = string("start_date")?.take(4)?.toIntOrNull()?.takeIf { it in 1900..2200 },
        imageUrl = get("main_picture")?.asObjectOrNull()?.string("large")?.toHttpsUrlOrNull()
            ?: get("main_picture")?.asObjectOrNull()?.string("medium")?.toHttpsUrlOrNull(),
        summary = string("synopsis")?.takeIf(String::isNotBlank),
    )
}

private fun JsonObject.toBangumiAnimeInfo(): ExternalAnimeInfo? {
    val id = long("id")?.takeIf { it > 0 } ?: return null
    val primaryTitle = string("name")?.takeIf(String::isNotBlank) ?: return null
    val chineseTitle = string("name_cn")?.takeIf(String::isNotBlank)
    val imageObject = get("images")?.asObjectOrNull()
    return ExternalAnimeInfo(
        id = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, id),
        titles = ExternalAnimeTitleSet(
            primary = primaryTitle,
            chinese = chineseTitle,
        ),
        episodeCount = int("eps")?.takeIf { it > 0 },
        startYear = string("date")?.take(4)?.toIntOrNull()?.takeIf { it in 1900..2200 },
        imageUrl = imageObject?.string("large")?.toHttpsUrlOrNull()
            ?: imageObject?.string("common")?.toHttpsUrlOrNull()
            ?: imageObject?.string("medium")?.toHttpsUrlOrNull(),
        summary = string("summary")?.takeIf(String::isNotBlank),
    )
}

private fun JsonElement.asObject(): JsonObject =
    asObjectOrNull() ?: JsonObject(emptyMap())

private fun JsonElement.asObjectOrNull(): JsonObject? =
    this as? JsonObject

private fun JsonElement.asArray(): JsonArray =
    this as? JsonArray ?: JsonArray(emptyList())

private fun JsonElement.asStringOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull

private fun JsonObject.array(key: String): JsonArray =
    get(key)?.asArray() ?: JsonArray(emptyList())

private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(key: String): Long? =
    (get(key) as? JsonPrimitive)?.longOrNull

private fun JsonObject.int(key: String): Int? =
    (get(key) as? JsonPrimitive)?.intOrNull

private fun String.urlEncode(): String =
    URLEncoder.encode(this, Charsets.UTF_8)

private fun String.toHttpsUrlOrNull(): String? =
    trim()
        .replace("http://", "https://")
        .takeIf { it.startsWith("https://") }

private val externalAnimeSearchJson = Json {
    ignoreUnknownKeys = true
}

private const val BANGUMI_ANIME_SUBJECT_TYPE = 2
private const val DEFAULT_SEARCH_LIMIT = 10
private const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000
private const val DEFAULT_READ_TIMEOUT_MILLIS = 20_000
private const val DEFAULT_MAX_RESPONSE_BYTES = 1_000_000
