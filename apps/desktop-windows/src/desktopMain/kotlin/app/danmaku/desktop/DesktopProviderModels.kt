package app.danmaku.desktop

import app.danmaku.domain.DanmakuEvent
import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeListEntry
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeTrackingUpdate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Desktop-owned provider value types retained for cached data and UI state. */
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

data class MyAnimeListSearchConnection(val clientId: String) {
    init {
        require(clientId.isNotBlank()) { "clientId must not be blank" }
    }
}

class DandanplayClientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

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
        listOfNotNull(animeTitle, episodeTitle).joinToString(" - ").ifBlank { episodeId.toString() }
}

data class DandanplayCommentTrack(
    val match: DandanplayMatch,
    val events: List<DanmakuEvent>,
)

interface ExternalAnimeTrackingClient {
    val provider: ExternalAnimeProvider
    fun fetchListEntry(animeId: ExternalAnimeId): ExternalAnimeListEntry?
    fun updateListEntry(update: ExternalAnimeTrackingUpdate): ExternalAnimeListEntry
}

class ExternalAnimeProviderException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private fun <T> unavailable(): T =
    throw ExternalAnimeProviderException("Direct JVM provider access was removed; use the Rust server provider API.")

internal fun directProviderAccessRemoved(): Nothing = unavailable()
