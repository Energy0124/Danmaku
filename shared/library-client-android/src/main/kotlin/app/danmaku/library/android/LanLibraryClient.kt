package app.danmaku.library.android

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LanDanmakuTrack
import app.danmaku.domain.LanLibraryServerStatus
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
import app.danmaku.domain.PlaybackProgress
import app.danmaku.library.LanLibraryClientException
import app.danmaku.library.LanLibraryClient as SharedLanLibraryClient
import app.danmaku.library.LanPlaybackTarget
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

class LanLibraryClient(
    private val json: Json = Json {
        ignoreUnknownKeys = true
    },
    private val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS,
) : SharedLanLibraryClient {
    init {
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis must be positive" }
        require(readTimeoutMillis > 0) { "readTimeoutMillis must be positive" }
    }

    override fun fetchServerStatus(baseUrl: String): LanLibraryServerStatus {
        val connection = open("${baseUrl.trimEnd('/')}/api/server/status")
        return try {
            connection.requireResponse(HttpURLConnection.HTTP_OK)
            json.decodeFromString(
                connection.inputStream.bufferedReader().use { it.readText() },
            )
        } finally {
            connection.disconnect()
        }
    }

    override fun fetchCatalog(
        baseUrl: String,
        pairingToken: String,
    ): LibraryCatalog {
        val connection = open("${baseUrl.trimEnd('/')}/api/library?token=${pairingToken.encoded()}")
        return try {
            connection.requireResponse(HttpURLConnection.HTTP_OK)
            json.decodeFromString(
                connection.inputStream.bufferedReader().use { it.readText() },
            )
        } finally {
            connection.disconnect()
        }
    }

    override fun streamUrl(
        baseUrl: String,
        item: LibraryMediaItem,
        pairingToken: String,
    ): String =
        "${baseUrl.trimEnd('/')}${item.streamPath}?token=${pairingToken.encoded()}"

    override fun subtitleUrl(
        baseUrl: String,
        subtitle: LibrarySubtitleTrack,
        pairingToken: String,
    ): String =
        "${baseUrl.trimEnd('/')}${subtitle.streamPath}?token=${pairingToken.encoded()}"

    override fun fetchProgress(
        baseUrl: String,
        mediaId: String,
        pairingToken: String,
    ): PlaybackProgress? {
        val connection = open(progressUrl(baseUrl, mediaId, pairingToken))
        return try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> null
                HttpURLConnection.HTTP_OK -> json.decodeFromString(
                    connection.inputStream.bufferedReader().use { it.readText() },
                )
                else -> throw connection.httpException()
            }
        } finally {
            connection.disconnect()
        }
    }

    override fun fetchAllProgress(
        baseUrl: String,
        pairingToken: String,
    ): List<PlaybackProgress> {
        val connection = open(progressListUrl(baseUrl, pairingToken))
        return try {
            connection.requireResponse(HttpURLConnection.HTTP_OK)
            json.decodeFromString(
                connection.inputStream.bufferedReader().use { it.readText() },
            )
        } finally {
            connection.disconnect()
        }
    }

    override fun fetchDanmaku(
        baseUrl: String,
        mediaId: String,
        pairingToken: String,
        forceRefresh: Boolean,
    ): LanDanmakuTrack {
        val connection = open(danmakuUrl(baseUrl, mediaId, pairingToken, forceRefresh))
        return try {
            connection.requireResponse(HttpURLConnection.HTTP_OK)
            json.decodeFromString(
                connection.inputStream.bufferedReader().use { it.readText() },
            )
        } finally {
            connection.disconnect()
        }
    }

    override fun saveProgress(
        baseUrl: String,
        pairingToken: String,
        progress: PlaybackProgress,
    ) {
        val connection = open(progressUrl(baseUrl, progress.mediaId, pairingToken)).apply {
            requestMethod = "PUT"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            connection.outputStream.bufferedWriter().use {
                it.write(json.encodeToString(progress))
            }
            connection.requireResponse(HttpURLConnection.HTTP_NO_CONTENT)
        } finally {
            connection.disconnect()
        }
    }

    private fun progressUrl(
        baseUrl: String,
        mediaId: String,
        pairingToken: String,
    ): String =
        "${baseUrl.trimEnd('/')}/api/progress/${mediaId.encoded()}?token=${pairingToken.encoded()}"

    private fun progressListUrl(
        baseUrl: String,
        pairingToken: String,
    ): String =
        "${baseUrl.trimEnd('/')}/api/progress?token=${pairingToken.encoded()}"

    private fun danmakuUrl(
        baseUrl: String,
        mediaId: String,
        pairingToken: String,
        forceRefresh: Boolean,
    ): String =
        "${baseUrl.trimEnd('/')}/api/danmaku/${mediaId.encoded()}?token=${pairingToken.encoded()}" +
            "&forceRefresh=$forceRefresh"

    private fun open(url: String): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
        }

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 5_000
        const val DEFAULT_READ_TIMEOUT_MILLIS = 10_000
    }
}

fun lanPlaybackTargetFromStreamUrl(url: String): LanPlaybackTarget? =
    runCatching {
        val uri = URI(url)
        val mediaId = uri.rawPath
            ?.takeIf { it.startsWith(MEDIA_PATH_PREFIX) }
            ?.removePrefix(MEDIA_PATH_PREFIX)
            ?.takeIf { it.isNotBlank() && '/' !in it }
            ?.decoded()
            ?: return null
        val pairingToken = uri.rawQuery
            ?.split('&')
            ?.mapNotNull { parameter ->
                parameter.split('=', limit = 2)
                    .takeIf { it.size == 2 }
                    ?.let { (key, value) -> key.decoded() to value.decoded() }
            }
            ?.firstOrNull { (key) -> key == "token" }
            ?.second
            ?: return null
        val baseUrl = "${uri.scheme}://${uri.rawAuthority}"
            .takeIf { uri.scheme in setOf("http", "https") && uri.rawAuthority != null }
            ?: return null
        LanPlaybackTarget(baseUrl, pairingToken, mediaId)
    }.getOrNull()

private fun String.encoded(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name())

private fun String.decoded(): String =
    java.net.URLDecoder.decode(this, Charsets.UTF_8.name())

private fun HttpURLConnection.requireResponse(expectedCode: Int) {
    if (responseCode != expectedCode) {
        throw httpException()
    }
}

private fun HttpURLConnection.httpException(): LanLibraryClientException =
    LanLibraryClientException("Library server returned HTTP $responseCode")

private const val MEDIA_PATH_PREFIX = "/media/"
