package app.danmaku.library.android

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
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

    private fun open(url: String): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 10_000
    }
}

private fun String.encoded(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name())
