package app.danmaku.library.testing

import app.danmaku.domain.LanDanmakuTrack
import app.danmaku.domain.LanLibraryServerStatus
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.PlaybackProgress
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal HTTP fixture for LAN client contract tests.
 *
 * It models only client-facing protocol behavior and never instantiates a
 * server implementation, so these tests survive the legacy JVM host removal.
 * This file is mirrored in the Android client test source set; keep the two
 * copies byte-identical.
 */
class LanProtocolFixtureServer(
    private val catalog: LibraryCatalog = LibraryCatalog(
        rootName = "Fixture Library",
        indexedAtEpochMs = 0,
        items = emptyList(),
    ),
    private val mediaByPath: Map<String, ByteArray> = emptyMap(),
    private val subtitlesByPath: Map<String, ByteArray> = emptyMap(),
    private val danmakuByMediaId: Map<String, LanDanmakuTrack> = emptyMap(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AutoCloseable {
    private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    private val executor = Executors.newSingleThreadExecutor()
    private val progressByMediaId = ConcurrentHashMap<String, PlaybackProgress>()
    private val forceRefreshCount = AtomicInteger()

    val baseUrl: String = "http://127.0.0.1:${socket.localPort}"
    val pairingToken: String = "token with spaces"
    val danmakuForceRefreshRequests: Int
        get() = forceRefreshCount.get()

    init {
        executor.submit {
            while (!socket.isClosed) {
                try {
                    socket.accept().use(::handle)
                } catch (_: SocketException) {
                    break
                }
            }
        }
    }

    override fun close() {
        socket.close()
        executor.shutdownNow()
    }

    private fun handle(client: Socket) {
        val request = client.getInputStream().buffered().readRequest() ?: return
        val response = route(request)
        client.getOutputStream().use { output ->
            val headers = buildString {
                append("HTTP/1.1 ${response.status} ${response.reason}\r\n")
                response.contentType?.let { append("Content-Type: $it\r\n") }
                append("Content-Length: ${response.body.size}\r\n")
                append("Connection: close\r\n\r\n")
            }
            output.write(headers.toByteArray(StandardCharsets.ISO_8859_1))
            output.write(response.body)
            output.flush()
        }
    }

    private fun route(request: Request): Response {
        val path = request.target.substringBefore('?')
        return when {
            request.method == "GET" && path == "/api/server/status" ->
                jsonResponse(LanLibraryServerStatus())

            request.method == "GET" && path == "/api/library" ->
                jsonResponse(catalog)

            request.method == "GET" && path == "/api/progress" ->
                jsonResponse(progressByMediaId.values.sortedBy(PlaybackProgress::mediaId))

            path.startsWith(PROGRESS_PREFIX) ->
                progressResponse(request, path.removePrefix(PROGRESS_PREFIX).decoded())

            request.method == "GET" && path.startsWith(DANMAKU_PREFIX) -> {
                val mediaId = path.removePrefix(DANMAKU_PREFIX).decoded()
                val track = danmakuByMediaId[mediaId] ?: return Response.notFound()
                if ("forceRefresh=true" in request.target.substringAfter('?', "").split('&')) {
                    forceRefreshCount.incrementAndGet()
                }
                jsonResponse(track)
            }

            request.method == "GET" && path in mediaByPath ->
                Response.ok(mediaByPath.getValue(path))

            request.method == "GET" && path in subtitlesByPath ->
                Response.ok(subtitlesByPath.getValue(path))

            else -> Response.notFound()
        }
    }

    private fun progressResponse(
        request: Request,
        mediaId: String,
    ): Response =
        when (request.method) {
            "GET" -> progressByMediaId[mediaId]
                ?.let(::jsonResponse)
                ?: Response.notFound()

            "PUT" -> {
                val progress = json.decodeFromString<PlaybackProgress>(
                    request.body.toString(StandardCharsets.UTF_8),
                )
                progressByMediaId[progress.mediaId] = progress
                Response.noContent()
            }

            else -> Response.methodNotAllowed()
        }

    private inline fun <reified T> jsonResponse(value: T): Response =
        Response.ok(
            json.encodeToString(value).toByteArray(StandardCharsets.UTF_8),
            "application/json; charset=utf-8",
        )

    private data class Request(
        val method: String,
        val target: String,
        val body: ByteArray,
    )

    private data class Response(
        val status: Int,
        val reason: String,
        val body: ByteArray = ByteArray(0),
        val contentType: String? = null,
    ) {
        companion object {
            fun ok(
                body: ByteArray,
                contentType: String = "application/octet-stream",
            ): Response = Response(200, "OK", body, contentType)

            fun noContent(): Response = Response(204, "No Content")

            fun notFound(): Response = Response(404, "Not Found")

            fun methodNotAllowed(): Response = Response(405, "Method Not Allowed")
        }
    }

    private fun BufferedInputStream.readRequest(): Request? {
        val requestLine = readAsciiLine()?.takeIf(String::isNotBlank) ?: return null
        val parts = requestLine.split(' ', limit = 3)
        require(parts.size == 3) { "invalid fixture request line: $requestLine" }
        var contentLength = 0
        while (true) {
            val header = readAsciiLine() ?: return null
            if (header.isBlank()) break
            val fields = header.split(':', limit = 2)
            if (fields.size == 2 && fields[0].equals("Content-Length", ignoreCase = true)) {
                contentLength = fields[1].trim().toInt()
            }
        }
        val body = ByteArray(contentLength)
        var offset = 0
        while (offset < body.size) {
            val count = read(body, offset, body.size - offset)
            require(count >= 0) { "fixture request body ended early" }
            offset += count
        }
        return Request(parts[0], parts[1], body)
    }

    private fun BufferedInputStream.readAsciiLine(): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            when (val next = read()) {
                -1 -> return bytes
                    .takeIf { it.size() > 0 }
                    ?.toString(StandardCharsets.ISO_8859_1)

                '\n'.code -> return bytes.toString(StandardCharsets.ISO_8859_1)
                '\r'.code -> Unit
                else -> bytes.write(next)
            }
        }
    }

    private fun String.decoded(): String =
        URLDecoder.decode(this, StandardCharsets.UTF_8)

    private companion object {
        const val PROGRESS_PREFIX = "/api/progress/"
        const val DANMAKU_PREFIX = "/api/danmaku/"
    }
}
