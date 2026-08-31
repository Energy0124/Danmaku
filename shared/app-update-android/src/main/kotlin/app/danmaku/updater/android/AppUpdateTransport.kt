package app.danmaku.updater.android

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class AppUpdateDownloadResult(
    val sizeBytes: Long,
    val sha256: String,
)

internal interface AppUpdateTransport {
    suspend fun getText(url: String): String

    suspend fun download(
        url: String,
        destination: File,
        expectedSizeBytes: Long,
        onProgress: (Long) -> Unit,
    ): AppUpdateDownloadResult
}

internal class HttpAppUpdateTransport : AppUpdateTransport {
    override suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        val connection = openConnection(url, "application/json")
        try {
            requireSuccess(connection)
            val declaredLength = connection.contentLengthLong
            require(declaredLength <= MAX_MANIFEST_BYTES) { "Update manifest is too large" }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_MANIFEST_BYTES) { "Update manifest is too large" }
                    output.write(buffer, 0, count)
                }
            }
            output.toString(Charsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun download(
        url: String,
        destination: File,
        expectedSizeBytes: Long,
        onProgress: (Long) -> Unit,
    ): AppUpdateDownloadResult = withContext(Dispatchers.IO) {
        val connection = openConnection(url, "application/vnd.android.package-archive")
        try {
            requireSuccess(connection)
            val declaredLength = connection.contentLengthLong
            require(declaredLength < 0 || declaredLength == expectedSizeBytes) {
                "Update download size does not match the manifest"
            }
            destination.parentFile?.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= expectedSizeBytes) { "Update download exceeded its declared size" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        onProgress(total)
                    }
                }
            }
            AppUpdateDownloadResult(
                sizeBytes = total,
                sha256 = digest.digest().joinToString("") { "%02x".format(it) },
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, accept: String): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "Danmaku-Android-Updater/1")
        }

    private fun requireSuccess(connection: HttpURLConnection) {
        require(connection.responseCode in 200..299) {
            "Update server returned HTTP ${connection.responseCode}"
        }
    }

    private companion object {
        const val MAX_MANIFEST_BYTES = 1024L * 1024L
    }
}
