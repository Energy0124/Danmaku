package app.danmaku.desktop

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class DesktopAnimePosterCache(
    private val cacheDirectory: Path = defaultCacheDirectory(),
    private val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS,
    private val maxImageBytes: Int = DEFAULT_MAX_IMAGE_BYTES,
) {
    init {
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis must be positive" }
        require(readTimeoutMillis > 0) { "readTimeoutMillis must be positive" }
        require(maxImageBytes > 0) { "maxImageBytes must be positive" }
    }

    fun cachedPath(imageUrl: String?): Path? {
        val url = imageUrl?.toValidatedPosterUrl() ?: return null
        val cachePath = cachePathFor(url)
        return cachePath.takeIf(Files::isRegularFile)
    }

    fun fetch(imageUrl: String?, forceRefresh: Boolean = false): Path? {
        val url = imageUrl?.toValidatedPosterUrl() ?: return null
        val cachePath = cachePathFor(url)
        if (!forceRefresh && Files.isRegularFile(cachePath)) {
            return cachePath
        }

        Files.createDirectories(cacheDirectory)
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.setRequestProperty("Accept", "image/*,*/*;q=0.8")
            connection.setRequestProperty("User-Agent", "Danmaku/1.0")
            val status = connection.responseCode
            check(status == HttpURLConnection.HTTP_OK) {
                "poster fetch returned HTTP $status for $url"
            }
            val bytes = connection.inputStream.use { input ->
                input.readNBytes(maxImageBytes + 1)
            }
            check(bytes.size <= maxImageBytes) {
                "poster response exceeded $maxImageBytes bytes"
            }
            Files.write(cachePath, bytes)
            cachePath
        } finally {
            connection.disconnect()
        }
    }

    private fun cachePathFor(url: URL): Path =
        cacheDirectory.resolve("${url.toString().sha256Hex()}.img")

    companion object {
        private const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 5_000
        private const val DEFAULT_READ_TIMEOUT_MILLIS = 15_000
        private const val DEFAULT_MAX_IMAGE_BYTES = 8 * 1024 * 1024

        fun default(): DesktopAnimePosterCache = DesktopAnimePosterCache()

        private fun defaultCacheDirectory(): Path {
            val localAppData = System.getenv("LOCALAPPDATA")
            if (!localAppData.isNullOrBlank()) {
                return Path.of(localAppData).resolve("Danmaku").resolve("poster-cache")
            }
            return Path.of(System.getProperty("java.io.tmpdir")).resolve("Danmaku").resolve("poster-cache")
        }
    }
}

private fun String.toValidatedPosterUrl(): URL? =
    trim()
        .takeIf(String::isNotBlank)
        ?.let(URI::create)
        ?.takeIf { uri ->
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null &&
                uri.fragment == null
        }
        ?.toURL()

private fun String.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
