package app.danmaku.desktop

import app.danmaku.domain.LibraryCatalog
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors

class LocalLibraryServer(
    port: Int = DEFAULT_PORT,
    val pairingToken: String = generatePairingToken(),
) : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress(port), 0)
    private val executor = Executors.newCachedThreadPool()

    @Volatile
    private var library = IndexedLocalLibrary(
        catalog = LibraryCatalog(
            rootName = "No folder selected",
            indexedAtEpochMs = 0,
            items = emptyList(),
        ),
        filesById = emptyMap(),
    )

    val localPort: Int
        get() = server.address.port

    init {
        server.executor = executor
        server.createContext("/api/library", ::handleCatalog)
        server.createContext("/media/", ::handleMedia)
    }

    fun start() {
        server.start()
    }

    fun publish(library: IndexedLocalLibrary) {
        this.library = library
    }

    fun networkUrls(): List<String> =
        buildList {
            add(baseUrl())
            NetworkInterface.getNetworkInterfaces()
                .toList()
                .flatMap { it.inetAddresses.toList() }
                .filter(InetAddress::isSiteLocalAddress)
                .filterNot(InetAddress::isLoopbackAddress)
                .mapTo(this) { address -> baseUrl(address.hostAddress) }
        }.distinct()

    fun baseUrl(host: String = "127.0.0.1"): String =
        "http://$host:$localPort"

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }

    private fun handleCatalog(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            exchange.sendStatus(405)
            return
        }
        if (!exchange.isAuthorized()) {
            exchange.sendStatus(401)
            return
        }

        val body = Json.encodeToString(library.catalog).toByteArray()
        exchange.responseHeaders["Content-Type"] = listOf("application/json; charset=utf-8")
        exchange.responseHeaders["Cache-Control"] = listOf("no-store")
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private fun handleMedia(exchange: HttpExchange) {
        if (exchange.requestMethod !in setOf("GET", "HEAD")) {
            exchange.sendStatus(405)
            return
        }
        if (!exchange.isAuthorized()) {
            exchange.sendStatus(401)
            return
        }

        val id = exchange.requestURI.path.removePrefix("/media/")
        val path = library.filesById[id]
        if (path == null || !Files.isRegularFile(path)) {
            exchange.sendStatus(404)
            return
        }

        val fileSize = Files.size(path)
        val range = exchange.requestHeaders.getFirst("Range")
            ?.let { parseRange(it, fileSize) }
        if (exchange.requestHeaders.containsKey("Range") && range == null) {
            exchange.responseHeaders["Content-Range"] = listOf("bytes */$fileSize")
            exchange.sendStatus(416)
            return
        }

        val start = range?.first ?: 0
        val endInclusive = range?.last ?: (fileSize - 1)
        val contentLength = if (fileSize == 0L) 0 else endInclusive - start + 1
        exchange.responseHeaders["Accept-Ranges"] = listOf("bytes")
        exchange.responseHeaders["Content-Type"] = listOf(contentType(path))
        if (range != null) {
            exchange.responseHeaders["Content-Range"] =
                listOf("bytes $start-$endInclusive/$fileSize")
        }

        exchange.sendResponseHeaders(if (range == null) 200 else 206, contentLength)
        if (exchange.requestMethod == "HEAD" || contentLength == 0L) {
            exchange.close()
            return
        }

        RandomAccessFile(path.toFile(), "r").use { file ->
            exchange.responseBody.use { output ->
                file.seek(start)
                var remaining = contentLength
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (remaining > 0) {
                    val read = file.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
            }
        }
    }

    private fun parseRange(header: String, fileSize: Long): LongRange? {
        if (!header.startsWith("bytes=") || fileSize == 0L) return null
        val value = header.removePrefix("bytes=")
        if (',' in value) return null
        val (startText, endText) = value.split('-', limit = 2).takeIf { it.size == 2 }
            ?: return null

        val start: Long
        val endInclusive: Long
        if (startText.isBlank()) {
            val suffixLength = endText.toLongOrNull()?.takeIf { it > 0 } ?: return null
            start = (fileSize - suffixLength).coerceAtLeast(0)
            endInclusive = fileSize - 1
        } else {
            start = startText.toLongOrNull()?.takeIf { it >= 0 } ?: return null
            endInclusive = endText
                .takeIf(String::isNotBlank)
                ?.toLongOrNull()
                ?.coerceAtMost(fileSize - 1)
                ?: (fileSize - 1)
        }
        return (start..endInclusive).takeIf { start < fileSize && start <= endInclusive }
    }

    private fun contentType(path: Path): String =
        Files.probeContentType(path) ?: "application/octet-stream"

    private fun HttpExchange.isAuthorized(): Boolean {
        val suppliedToken = requestURI.rawQuery
            ?.split('&')
            ?.mapNotNull { parameter ->
                parameter.split('=', limit = 2)
                    .takeIf { it.size == 2 }
                    ?.let { (key, value) ->
                        URLDecoder.decode(key, Charsets.UTF_8) to
                            URLDecoder.decode(value, Charsets.UTF_8)
                    }
            }
            ?.firstOrNull { (key) -> key == "token" }
            ?.second
            ?: return false
        return MessageDigest.isEqual(
            pairingToken.toByteArray(),
            suppliedToken.toByteArray(),
        )
    }

    private fun HttpExchange.sendStatus(status: Int) {
        sendResponseHeaders(status, -1)
        close()
    }

    companion object {
        const val DEFAULT_PORT = 8686

        private fun generatePairingToken(): String =
            SecureRandom()
                .nextInt(1_000_000)
                .toString()
                .padStart(6, '0')
    }
}
