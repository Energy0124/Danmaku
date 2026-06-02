package app.danmaku.library.android

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.resumePositionMs
import app.danmaku.domain.toPlaybackProgress
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

class LanLibraryClient(
    private val json: Json = Json {
        ignoreUnknownKeys = true
    },
) {
    fun fetchCatalog(
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

    fun streamUrl(
        baseUrl: String,
        item: LibraryMediaItem,
        pairingToken: String,
    ): String =
        "${baseUrl.trimEnd('/')}${item.streamPath}?token=${pairingToken.encoded()}"

    fun fetchProgress(
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

    fun saveProgress(
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
            connectTimeout = 5_000
            readTimeout = 10_000
    }
}

data class LanPlaybackTarget(
    val baseUrl: String,
    val pairingToken: String,
    val mediaId: String,
) {
    init {
        require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
        require(mediaId.isNotBlank()) { "mediaId must not be blank" }
    }
}

class LanPlaybackProgressSync(
    private val libraryClient: LanLibraryClient,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    fun fetchResumePositionMs(target: LanPlaybackTarget): Long? =
        libraryClient
            .fetchProgress(target.baseUrl, target.mediaId, target.pairingToken)
            ?.resumePositionMs()

    fun saveProgress(
        target: LanPlaybackTarget,
        snapshot: PlaybackSnapshot,
    ) {
        snapshot
            .toPlaybackProgress(target.mediaId, currentTimeMillis())
            ?.let {
                libraryClient.saveProgress(target.baseUrl, target.pairingToken, it)
            }
    }
}

private fun String.encoded(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name())
