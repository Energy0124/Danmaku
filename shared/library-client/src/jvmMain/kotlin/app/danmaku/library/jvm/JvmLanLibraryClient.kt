package app.danmaku.library.jvm

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
import app.danmaku.domain.PlaybackProgress
import app.danmaku.library.LanLibraryClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

class JvmLanLibraryClient(
    private val json: Json = Json {
        ignoreUnknownKeys = true
    },
    private val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS,
) : LanLibraryClient {
    init {
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis must be positive" }
        require(readTimeoutMillis > 0) { "readTimeoutMillis must be positive" }
    }

    override fun fetchCatalog(
        baseUrl: String,
        pairingToken: String,
    ): LibraryCatalog {
        val connection = open("${baseUrl.trimEnd('/')}/api/library?token=${pairingToken.encoded()}")
        return try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Library server returned HTTP ${connection.responseCode}"
            }
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
                else -> error("Library server returned HTTP ${connection.responseCode}")
            }
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
            check(connection.responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                "Library server returned HTTP ${connection.responseCode}"
            }
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

private fun String.encoded(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name())
