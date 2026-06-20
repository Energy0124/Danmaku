package app.danmaku.server.windows

import app.danmaku.domain.ExternalAnimeExternalLink
import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeInfo
import app.danmaku.domain.ExternalAnimeMatchCandidate
import app.danmaku.domain.ExternalAnimeMatchQuery
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeTitleSet
import app.danmaku.domain.rankExternalAnimeMatches
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
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

internal interface HeadlessExternalAnimeSearchService {
    fun search(
        query: ExternalAnimeMatchQuery,
        providers: Set<ExternalAnimeProvider>,
        limitPerProvider: Int,
    ): List<ExternalAnimeMatchCandidate>
}

internal class DefaultHeadlessExternalAnimeSearchService(
    private val clients: List<HeadlessExternalAnimeSearchClient>,
) : HeadlessExternalAnimeSearchService {
    init {
        require(clients.map(HeadlessExternalAnimeSearchClient::provider).distinct().size == clients.size) {
            "external anime search clients must be unique by provider"
        }
    }

    override fun search(
        query: ExternalAnimeMatchQuery,
        providers: Set<ExternalAnimeProvider>,
        limitPerProvider: Int,
    ): List<ExternalAnimeMatchCandidate> {
        require(limitPerProvider in 1..MAX_SEARCH_LIMIT) { "limitPerProvider must be between 1 and $MAX_SEARCH_LIMIT" }
        val results = clients
            .filter { client -> providers.isEmpty() || client.provider in providers }
            .flatMap { client ->
                query.searchTitles.flatMap { searchTitle ->
                    try {
                        client.search(
                            query.copy(
                                title = searchTitle,
                                alternateTitles = emptyList(),
                            ),
                            limitPerProvider,
                        )
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }
            .distinctBy { anime -> anime.id }
        return rankExternalAnimeMatches(query, results)
    }
}

internal interface HeadlessExternalAnimeSearchClient {
    val provider: ExternalAnimeProvider

    fun search(query: ExternalAnimeMatchQuery, limit: Int): List<ExternalAnimeInfo>
}

internal fun HeadlessServerSettings.toExternalAnimeSearchService(): HeadlessExternalAnimeSearchService =
    DefaultHeadlessExternalAnimeSearchService(
        buildList {
            externalAnime.myAnimeListClientId?.let { clientId ->
                add(
                    HeadlessMyAnimeListAnimeSearchClient(
                        connection = HeadlessMyAnimeListSearchConnection(clientId),
                    ),
                )
            }
            add(
                HeadlessBangumiAnimeSearchClient(
                    baseUri = URI(externalAnime.bangumiBaseUrl),
                    userAgent = externalAnime.bangumiUserAgent,
                ),
            )
        },
    )

internal data class HeadlessMyAnimeListSearchConnection(
    val clientId: String,
    val baseUri: URI = URI("https://api.myanimelist.net/v2/"),
) {
    init {
        require(clientId.isNotBlank()) { "MyAnimeList clientId must not be blank" }
        require(baseUri.scheme == "https") { "MyAnimeList baseUri must be HTTPS" }
    }
}

internal class HeadlessMyAnimeListAnimeSearchClient(
    private val connection: HeadlessMyAnimeListSearchConnection,
    private val httpGet: HeadlessExternalAnimeHttpGet = HeadlessExternalAnimeHttpGet.default(),
    private val json: Json = headlessExternalAnimeSearchJson,
) : HeadlessExternalAnimeSearchClient {
    override val provider: ExternalAnimeProvider = ExternalAnimeProvider.MY_ANIME_LIST

    override fun search(query: ExternalAnimeMatchQuery, limit: Int): List<ExternalAnimeInfo> {
        require(limit in 1..MAX_SEARCH_LIMIT) { "limit must be between 1 and $MAX_SEARCH_LIMIT" }
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

internal class HeadlessBangumiAnimeSearchClient(
    private val baseUri: URI,
    private val userAgent: String,
    private val httpPost: HeadlessExternalAnimeHttpPost = HeadlessExternalAnimeHttpPost.default(),
    private val httpGet: HeadlessExternalAnimeHttpGet = HeadlessExternalAnimeHttpGet.default(),
    private val json: Json = headlessExternalAnimeSearchJson,
) : HeadlessExternalAnimeSearchClient {
    override val provider: ExternalAnimeProvider = ExternalAnimeProvider.BANGUMI

    init {
        require(baseUri.scheme == "https") { "Bangumi baseUri must be HTTPS" }
        require(userAgent.isNotBlank()) { "userAgent must not be blank" }
    }

    override fun search(query: ExternalAnimeMatchQuery, limit: Int): List<ExternalAnimeInfo> {
        require(limit in 1..MAX_SEARCH_LIMIT) { "limit must be between 1 and $MAX_SEARCH_LIMIT" }
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
        val searchResults = json.parseToJsonElement(response)
            .asObject()
            .array("data")
            .mapNotNull(JsonElement::asObjectOrNull)
            .mapNotNull(JsonObject::toBangumiAnimeInfo)
        return searchResults.mapIndexed { index, anime ->
            if (index < BANGUMI_DETAIL_ENRICH_LIMIT) {
                fetchBangumiSubject(anime.id.value) ?: anime
            } else {
                anime
            }
        }
    }

    private fun fetchBangumiSubject(animeId: Long): ExternalAnimeInfo? =
        runCatching {
            val response = httpGet.get(
                url = baseUri.resolve("v0/subjects/$animeId").toURL(),
                headers = mapOf(
                    "Accept" to "application/json",
                    "User-Agent" to userAgent,
                ),
            )
            json.parseToJsonElement(response)
                .asObject()
                .toBangumiAnimeInfo()
        }.getOrNull()
}

internal fun interface HeadlessExternalAnimeHttpGet {
    fun get(url: URL, headers: Map<String, String>): String

    companion object {
        fun default(): HeadlessExternalAnimeHttpGet =
            HeadlessExternalAnimeHttpGet { url, headers ->
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = DEFAULT_CONNECT_TIMEOUT_MILLIS
                    readTimeout = DEFAULT_READ_TIMEOUT_MILLIS
                    headers.forEach(::setRequestProperty)
                }.readHeadlessExternalAnimeResponse()
            }
    }
}

internal fun interface HeadlessExternalAnimeHttpPost {
    fun post(url: URL, headers: Map<String, String>, body: String): String

    companion object {
        fun default(): HeadlessExternalAnimeHttpPost =
            HeadlessExternalAnimeHttpPost { url, headers, body ->
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = DEFAULT_CONNECT_TIMEOUT_MILLIS
                    readTimeout = DEFAULT_READ_TIMEOUT_MILLIS
                    headers.forEach(::setRequestProperty)
                    outputStream.bufferedWriter().use { writer -> writer.write(body) }
                }.readHeadlessExternalAnimeResponse()
            }
    }
}

internal class HeadlessExternalAnimeProviderException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

private fun HttpURLConnection.readHeadlessExternalAnimeResponse(): String =
    try {
        val status = responseCode
        val responseBody = (if (status >= HttpURLConnection.HTTP_BAD_REQUEST) errorStream else inputStream)
            .use { input ->
                val bytes = input.readNBytes(DEFAULT_MAX_RESPONSE_BYTES + 1)
                if (bytes.size > DEFAULT_MAX_RESPONSE_BYTES) {
                    throw HeadlessExternalAnimeProviderException(
                        "external anime response exceeded $DEFAULT_MAX_RESPONSE_BYTES bytes",
                    )
                }
                bytes.toString(Charsets.UTF_8)
            }
        if (status !in 200..299) {
            throw HeadlessExternalAnimeProviderException(
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
        externalLinks = listOf(
            ExternalAnimeExternalLink(ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, id)),
        ),
    )
}

private fun JsonObject.toBangumiAnimeInfo(): ExternalAnimeInfo? {
    val id = long("id")?.takeIf { it > 0 } ?: return null
    val primaryTitle = string("name")?.takeIf(String::isNotBlank) ?: return null
    val infoboxAliases = infoboxValues("别名", "英文名", "英语名", "日文名", "原名")
    val chineseTitle = string("name_cn")?.takeIf(String::isNotBlank)
        ?: infoboxValues("中文名").firstOrNull()
    val englishTitle = infoboxAliases.firstOrNull(String::looksEnglishTitle)
    val japaneseTitle = infoboxAliases.firstOrNull(String::containsJapanese)
        ?: primaryTitle.takeIf(String::containsJapanese)
    val alternateNames = infoboxAliases
        .plus(infoboxValues("中文名"))
        .filterNot { title ->
            title == primaryTitle ||
                title == chineseTitle ||
                title == englishTitle ||
                title == japaneseTitle
        }
        .distinctBy { it.trim().lowercase() }
    val imageObject = get("images")?.asObjectOrNull()
    return ExternalAnimeInfo(
        id = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, id),
        titles = ExternalAnimeTitleSet(
            primary = primaryTitle,
            chinese = chineseTitle,
            english = englishTitle,
            japanese = japaneseTitle,
            alternateNames = alternateNames,
        ),
        episodeCount = int("eps")?.takeIf { it > 0 },
        startYear = (string("date") ?: string("air_date"))
            ?.take(4)
            ?.toIntOrNull()
            ?.takeIf { it in 1900..2200 },
        imageUrl = imageObject?.string("large")?.toHttpsUrlOrNull()
            ?: imageObject?.string("common")?.toHttpsUrlOrNull()
            ?: imageObject?.string("medium")?.toHttpsUrlOrNull(),
        summary = string("summary")?.takeIf(String::isNotBlank)
            ?: string("short_summary")?.takeIf(String::isNotBlank),
        externalLinks = listOf(
            ExternalAnimeExternalLink(ExternalAnimeId(ExternalAnimeProvider.BANGUMI, id)),
        ),
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

private fun JsonObject.infoboxValues(vararg keys: String): List<String> =
    array("infobox")
        .mapNotNull(JsonElement::asObjectOrNull)
        .filter { item -> item.string("key") in keys }
        .flatMap { item -> item["value"]?.infoboxTextValues().orEmpty() }
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()

private fun JsonElement.infoboxTextValues(): List<String> =
    when (this) {
        is JsonPrimitive -> listOfNotNull(contentOrNull)
        is JsonArray -> mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> element.contentOrNull
                is JsonObject -> element.string("v")
                    ?: element.string("value")
                    ?: element.string("name")
                else -> null
            }
        }
        is JsonObject -> listOfNotNull(
            string("v") ?: string("value") ?: string("name"),
        )
    }

private fun String.looksEnglishTitle(): Boolean =
    any { it in 'A'..'Z' || it in 'a'..'z' } && none { it.isCjkOrKana() }

private fun String.containsJapanese(): Boolean =
    any { it in '\u3040'..'\u30ff' || it in '\u31f0'..'\u31ff' }

private fun Char.isCjkOrKana(): Boolean =
    this in '\u3040'..'\u30ff' ||
        this in '\u31f0'..'\u31ff' ||
        this in '\u3400'..'\u4dbf' ||
        this in '\u4e00'..'\u9fff'

private fun String.urlEncode(): String =
    URLEncoder.encode(this, Charsets.UTF_8)

private fun String.toHttpsUrlOrNull(): String? =
    trim()
        .replace("http://", "https://")
        .takeIf { it.startsWith("https://") }

private val headlessExternalAnimeSearchJson = Json {
    ignoreUnknownKeys = true
}

private const val BANGUMI_ANIME_SUBJECT_TYPE = 2
private const val BANGUMI_DETAIL_ENRICH_LIMIT = 5
private const val MAX_SEARCH_LIMIT = 50
private const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000
private const val DEFAULT_READ_TIMEOUT_MILLIS = 20_000
private const val DEFAULT_MAX_RESPONSE_BYTES = 1_000_000
