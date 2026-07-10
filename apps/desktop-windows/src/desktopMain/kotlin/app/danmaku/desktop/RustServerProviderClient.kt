package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeInfo
import app.danmaku.domain.ExternalAnimeListEntry
import app.danmaku.domain.ExternalAnimeMatchCandidate
import app.danmaku.domain.ExternalAnimeMatchQuery
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeTrackingUpdate
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

internal class RustServerProviderClient(
    override val provider: ExternalAnimeProvider,
    private val baseUrl: () -> String,
    private val pairingToken: () -> String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ExternalAnimeSearchClient, ExternalAnimeTrackingClient {
    init {
        require(provider != ExternalAnimeProvider.DANDANPLAY) {
            "dandanplay does not support external list tracking"
        }
    }

    override fun search(query: ExternalAnimeMatchQuery, limit: Int): List<ExternalAnimeInfo> {
        require(limit in 1..50) { "limit must be between 1 and 50" }
        val parameters = buildList {
            add("title" to query.title)
            add("providers" to provider.alias)
            add("limit" to limit.toString())
            query.episodeCount?.let { add("episodeCount" to it.toString()) }
            query.startYear?.let { add("startYear" to it.toString()) }
        }
        val response = request("GET", "/api/providers/search?${parameters.formEncode()}")
        return json.decodeFromString<List<ExternalAnimeMatchCandidate>>(response.body).map(ExternalAnimeMatchCandidate::anime)
    }

    override fun fetchListEntry(animeId: ExternalAnimeId): ExternalAnimeListEntry? {
        require(animeId.provider == provider) { "animeId provider must match client provider" }
        val query = listOf(
            "provider" to provider.alias,
            "animeId" to animeId.value.toString(),
        ).formEncode()
        val response = request("GET", "/api/providers/list/entry?$query", allowNotFound = true)
        return if (response.status == HttpURLConnection.HTTP_NOT_FOUND) null else json.decodeFromString(response.body)
    }

    override fun updateListEntry(update: ExternalAnimeTrackingUpdate): ExternalAnimeListEntry {
        require(update.animeId.provider == provider) { "update provider must match client provider" }
        val response = request(
            method = "POST",
            path = "/api/providers/list/entry",
            body = json.encodeToString(update),
        )
        return json.decodeFromString(response.body)
    }

    private fun request(
        method: String,
        path: String,
        body: String? = null,
        allowNotFound: Boolean = false,
    ): ProviderHttpResponse {
        val separator = if ('?' in path) '&' else '?'
        val tokenQuery = pairingToken().takeIf(String::isNotBlank)
            ?.let { "$separator" + listOf("token" to it).formEncode() }
            .orEmpty()
        val connection = URI(baseUrl().trimEnd('/') + path + tokenQuery).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "application/json")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        return try {
            val status = connection.responseCode
            val responseBody = (if (status >= 400) connection.errorStream else connection.inputStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299 && !(allowNotFound && status == HttpURLConnection.HTTP_NOT_FOUND)) {
                throw ExternalAnimeProviderException(
                    "Rust server provider request failed with HTTP $status: ${responseBody.take(200)}",
                )
            }
            ProviderHttpResponse(status, responseBody)
        } finally {
            connection.disconnect()
        }
    }
}

private data class ProviderHttpResponse(val status: Int, val body: String)

private val ExternalAnimeProvider.alias: String
    get() = when (this) {
        ExternalAnimeProvider.MY_ANIME_LIST -> "my_anime_list"
        ExternalAnimeProvider.BANGUMI -> "bangumi"
        ExternalAnimeProvider.DANDANPLAY -> "dandanplay"
    }

private fun List<Pair<String, String>>.formEncode(): String =
    joinToString("&") { (key, value) -> "${key.urlEncode()}=${value.urlEncode()}" }

private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8)
