package app.danmaku.server

import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LanLibraryServerStatus
import app.danmaku.domain.LanProviderSettingsStatus
import app.danmaku.domain.PlaybackProgress
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
import java.util.concurrent.TimeUnit

class LocalLibraryServer(
    port: Int = DEFAULT_PORT,
    val pairingToken: String = generatePairingToken(),
    private val progressStore: PlaybackProgressStore = InMemoryPlaybackProgressStore(),
    authenticatedPostHooks: List<AuthenticatedPostHook> = emptyList(),
    publicGetHooks: List<PublicGetHook> = emptyList(),
    publicRequestHooks: List<PublicRequestHook> = emptyList(),
    private val webAssets: StaticWebAssets? = null,
    private val hostMode: String = LanLibraryServerStatus.HOST_MODE_EMBEDDED_DESKTOP,
    private val providerSettings: LanProviderSettingsStatus? = null,
    private val eventSink: (LocalLibraryServerEvent) -> Unit = {},
) : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress(port), 0)
    private val executor = Executors.newCachedThreadPool()

    @Volatile
    private var library = PublishedLibrary.EMPTY

    val localPort: Int
        get() = server.address.port

    init {
        require(authenticatedPostHooks.map(AuthenticatedPostHook::path).distinct().size == authenticatedPostHooks.size) {
            "authenticated hook paths must be unique"
        }
        require(publicGetHooks.map(PublicGetHook::path).distinct().size == publicGetHooks.size) {
            "public GET hook paths must be unique"
        }
        require(publicRequestHooks.map(PublicRequestHook::path).distinct().size == publicRequestHooks.size) {
            "public request hook paths must be unique"
        }
        val hookPaths = authenticatedPostHooks.map(AuthenticatedPostHook::path) +
            publicGetHooks.map(PublicGetHook::path) +
            publicRequestHooks.map(PublicRequestHook::path)
        require(hookPaths.distinct().size == hookPaths.size) {
            "server hook paths must be unique"
        }
        server.executor = executor
        server.createContext("/api/server/status", ::handleServerStatus)
        server.createContext("/api/library", ::handleCatalog)
        server.createContext("/api/progress", ::handleProgressList)
        server.createContext("/api/progress/", ::handleProgress)
        server.createContext("/media/", ::handleMedia)
        server.createContext("/subtitles/", ::handleSubtitle)
        server.createContext("/posters/", ::handlePoster)
        authenticatedPostHooks.forEach { hook ->
            server.createContext(hook.path) { exchange ->
                handleAuthenticatedPostHook(exchange, hook)
            }
        }
        publicGetHooks.forEach { hook ->
            server.createContext(hook.path) { exchange ->
                handlePublicGetHook(exchange, hook)
            }
        }
        publicRequestHooks.forEach { hook ->
            server.createContext(hook.path) { exchange ->
                handlePublicRequestHook(exchange, hook)
            }
        }
        webAssets?.let { assets ->
            server.createContext(assets.pathPrefix, ::handleWebAsset)
        }
    }

    fun start() {
        server.start()
    }

    fun publish(library: PublishedLibrary) {
        this.library = library
        recordEvent("library", "PUBLISH", "/api/library", 200, "items=${library.catalog.items.size}")
    }

    private fun handleServerStatus(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            exchange.recordRequest("server-status", 405, "method=${exchange.requestMethod}")
            exchange.sendStatus(405)
            return
        }

        val status = LanLibraryServerStatus(
            webUiAvailable = webAssets != null,
            webUiPath = webAssets?.pathPrefix,
            hostMode = hostMode,
            providerSettings = providerSettings,
        )
        exchange.recordRequest(
            "server-status",
            200,
            "api=${status.apiVersion}; pairingRequired=${status.pairingRequired}",
        )
        exchange.sendJson(Json.encodeToString(status))
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

    fun webUiUrls(): List<String> =
        webAssets
            ?.let { assets -> networkUrls().map { url -> "$url${assets.pathPrefix}/" } }
            .orEmpty()

    override fun close() {
        server.stop(1)
        executor.shutdown()
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (_: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    private fun handleCatalog(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            exchange.recordRequest("catalog", 405, "method=${exchange.requestMethod}")
            exchange.sendStatus(405)
            return
        }
        if (!exchange.isAuthorized()) {
            exchange.recordRequest("catalog", 401, "unauthorized")
            exchange.sendStatus(401)
            return
        }

        val body = Json.encodeToString(library.catalog).toByteArray()
        exchange.responseHeaders["Content-Type"] = listOf("application/json; charset=utf-8")
        exchange.responseHeaders["Cache-Control"] = listOf("no-store")
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.recordRequest("catalog", 200, "bytes=${body.size}; items=${library.catalog.items.size}")
        exchange.responseBody.use { it.write(body) }
    }

    private fun handleMedia(exchange: HttpExchange) {
        if (exchange.requestMethod !in setOf("GET", "HEAD")) {
            exchange.recordRequest("media", 405, "method=${exchange.requestMethod}")
            exchange.sendStatus(405)
            return
        }
        if (!exchange.isAuthorized()) {
            exchange.recordRequest("media", 401, "unauthorized")
            exchange.sendStatus(401)
            return
        }

        val id = exchange.requestURI.path.removePrefix("/media/")
        val path = library.filesById[id]
        if (path == null || !Files.isRegularFile(path)) {
            exchange.recordRequest("media", 404, "id=$id")
            exchange.sendStatus(404)
            return
        }

        val fileSize = Files.size(path)
        val rangeHeader = exchange.requestHeaders.getFirst("Range")
        val range = exchange.requestHeaders.getFirst("Range")
            ?.let { parseRange(it, fileSize) }
        if (exchange.requestHeaders.containsKey("Range") && range == null) {
            exchange.responseHeaders["Content-Range"] = listOf("bytes */$fileSize")
            exchange.recordRequest("media", 416, "id=$id; range=$rangeHeader; size=$fileSize")
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
        exchange.recordRequest(
            "media",
            if (range == null) 200 else 206,
            "id=$id; range=${rangeHeader ?: "none"}; bytes=$contentLength; size=$fileSize; file=${path.fileName}",
        )
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

    private fun handleSubtitle(exchange: HttpExchange) {
        if (exchange.requestMethod !in setOf("GET", "HEAD")) {
            exchange.recordRequest("subtitle", 405, "method=${exchange.requestMethod}")
            exchange.sendStatus(405)
            return
        }
        if (!exchange.isAuthorized()) {
            exchange.recordRequest("subtitle", 401, "unauthorized")
            exchange.sendStatus(401)
            return
        }

        val id = exchange.requestURI.path.removePrefix("/subtitles/")
        val path = library.subtitleFilesById[id]
        if (path == null || !Files.isRegularFile(path)) {
            exchange.recordRequest("subtitle", 404, "id=$id")
            exchange.sendStatus(404)
            return
        }

        val contentLength = Files.size(path)
        exchange.responseHeaders["Content-Type"] = listOf(contentType(path))
        exchange.responseHeaders["Cache-Control"] = listOf("no-store")
        if (exchange.requestMethod == "HEAD") {
            exchange.responseHeaders["Content-Length"] = listOf(contentLength.toString())
            exchange.sendResponseHeaders(200, -1)
            exchange.recordRequest("subtitle", 200, "id=$id; method=HEAD; bytes=$contentLength")
            exchange.close()
            return
        }
        exchange.sendResponseHeaders(200, contentLength)
        exchange.recordRequest("subtitle", 200, "id=$id; bytes=$contentLength; file=${path.fileName}")
        Files.newInputStream(path).use { input ->
            exchange.responseBody.use(input::copyTo)
        }
    }

    private fun handlePoster(exchange: HttpExchange) {
        if (exchange.requestMethod !in setOf("GET", "HEAD")) {
            exchange.recordRequest("poster", 405, "method=${exchange.requestMethod}")
            exchange.sendStatus(405)
            return
        }
        if (!exchange.isAuthorized()) {
            exchange.recordRequest("poster", 401, "unauthorized")
            exchange.sendStatus(401)
            return
        }

        val id = exchange.requestURI.path.removePrefix("/posters/")
        val path = library.posterFilesById[id]
        if (path == null || !Files.isRegularFile(path)) {
            exchange.recordRequest("poster", 404, "id=$id")
            exchange.sendStatus(404)
            return
        }

        val contentLength = Files.size(path)
        exchange.responseHeaders["Content-Type"] = listOf(contentType(path))
        exchange.responseHeaders["Cache-Control"] = listOf("private, max-age=3600")
        if (exchange.requestMethod == "HEAD") {
            exchange.responseHeaders["Content-Length"] = listOf(contentLength.toString())
            exchange.sendResponseHeaders(200, -1)
            exchange.recordRequest("poster", 200, "id=$id; method=HEAD; bytes=$contentLength")
            exchange.close()
            return
        }
        exchange.sendResponseHeaders(200, contentLength)
        exchange.recordRequest("poster", 200, "id=$id; bytes=$contentLength; file=${path.fileName}")
        Files.newInputStream(path).use { input ->
            exchange.responseBody.use(input::copyTo)
        }
    }

    private fun handleProgress(exchange: HttpExchange) {
        if (exchange.requestMethod !in setOf("GET", "PUT")) {
            exchange.recordRequest("progress", 405, "method=${exchange.requestMethod}")
            exchange.sendStatus(405)
            return
        }
        if (!exchange.isAuthorized()) {
            exchange.recordRequest("progress", 401, "unauthorized")
            exchange.sendStatus(401)
            return
        }

        val mediaId = exchange.requestURI.path
            .removePrefix("/api/progress/")
            .takeIf(String::isNotBlank)
            ?.let { URLDecoder.decode(it, Charsets.UTF_8) }
            ?.takeIf { id -> library.catalog.items.any { it.id == id } }
        if (mediaId == null) {
            exchange.recordRequest("progress", 404, "unknown media")
            exchange.sendStatus(404)
            return
        }

        if (exchange.requestMethod == "GET") {
            val progress = progressStore.loadProgress(mediaId)
            if (progress == null) {
                exchange.recordRequest("progress", 404, "id=$mediaId; no saved progress")
                exchange.sendStatus(404)
                return
            }
            exchange.recordRequest("progress", 200, "id=$mediaId; position=${progress.positionMs}")
            exchange.sendJson(Json.encodeToString(progress))
            return
        }

        val progress = runCatching {
            Json.decodeFromString<PlaybackProgress>(
                exchange.requestBody.bufferedReader().use { it.readText() },
            )
        }.getOrNull()
        if (progress == null || progress.mediaId != mediaId) {
            exchange.recordRequest("progress", 400, "id=$mediaId; invalid body")
            exchange.sendStatus(400)
            return
        }
        progressStore.saveProgress(progress)
        exchange.recordRequest("progress", 204, "id=$mediaId; position=${progress.positionMs}")
        exchange.sendStatus(204)
    }

    private fun handleProgressList(exchange: HttpExchange) {
        if (exchange.requestURI.path != "/api/progress") {
            exchange.recordRequest("progress-list", 404, "path=${exchange.requestURI.path}")
            exchange.sendStatus(404)
            return
        }
        if (exchange.requestMethod != "GET") {
            exchange.recordRequest("progress-list", 405, "method=${exchange.requestMethod}")
            exchange.sendStatus(405)
            return
        }
        if (!exchange.isAuthorized()) {
            exchange.recordRequest("progress-list", 401, "unauthorized")
            exchange.sendStatus(401)
            return
        }

        val publishedIds = library.catalog.items.mapTo(mutableSetOf(), LibraryMediaItem::id)
        val progress = progressStore
            .loadAllProgress()
            .filter { it.mediaId in publishedIds }
        exchange.recordRequest("progress-list", 200, "items=${progress.size}")
        exchange.sendJson(Json.encodeToString(progress))
    }

    private fun handleAuthenticatedPostHook(
        exchange: HttpExchange,
        hook: AuthenticatedPostHook,
    ) {
        if (exchange.requestMethod != "POST") {
            exchange.recordRequest("hook", 405, "method=${exchange.requestMethod}")
            exchange.sendStatus(405)
            return
        }
        val suppliedToken = exchange.requestHeaders.getFirst(WEBHOOK_TOKEN_HEADER)
        if (!hook.isAuthorized(suppliedToken)) {
            exchange.recordRequest("hook", 401, "path=${hook.path}")
            exchange.sendStatus(401)
            return
        }

        runCatching(hook::accept)
            .onSuccess {
                exchange.recordRequest("hook", 202, "path=${hook.path}")
                exchange.sendStatus(202)
            }
            .onFailure { error ->
                exchange.recordRequest("hook", 500, "path=${hook.path}; error=${error.message}")
                exchange.sendStatus(500)
            }
    }

    private fun handlePublicGetHook(
        exchange: HttpExchange,
        hook: PublicGetHook,
    ) {
        if (exchange.requestMethod != "GET") {
            exchange.recordRequest("public-hook", 405, "method=${exchange.requestMethod}")
            exchange.sendStatus(405)
            return
        }

        runCatching { hook.handle(exchange.requestURI.rawQuery.parseQueryParameters()) }
            .onSuccess { response ->
                exchange.recordRequest("public-hook", response.status, "path=${hook.path}")
                exchange.sendText(response.status, response.contentType, response.body)
            }
            .onFailure { error ->
                exchange.recordRequest("public-hook", 500, "path=${hook.path}; error=${error.message}")
                exchange.sendText(
                    status = 500,
                    contentType = "text/plain; charset=utf-8",
                    body = "Request failed.",
                )
            }
    }

    private fun handlePublicRequestHook(
        exchange: HttpExchange,
        hook: PublicRequestHook,
    ) {
        val body = if (exchange.requestMethod in setOf("POST", "PUT", "PATCH")) {
            exchange.requestBody.bufferedReader().use { it.readText() }
        } else {
            ""
        }
        runCatching {
            hook.handle(
                PublicRequestHookRequest(
                    method = exchange.requestMethod,
                    queryParameters = exchange.requestURI.rawQuery.parseQueryParameters(),
                    body = body,
                ),
            )
        }
            .onSuccess { response ->
                exchange.recordRequest("public-hook", response.status, "path=${hook.path}")
                exchange.sendText(response.status, response.contentType, response.body)
            }
            .onFailure { error ->
                exchange.recordRequest("public-hook", 500, "path=${hook.path}; error=${error.message}")
                exchange.sendText(
                    status = 500,
                    contentType = "text/plain; charset=utf-8",
                    body = "Request failed.",
                )
            }
    }
    private fun handleWebAsset(exchange: HttpExchange) {
        if (exchange.requestMethod !in setOf("GET", "HEAD")) {
            exchange.recordRequest("web", 405, "method=${exchange.requestMethod}")
            exchange.sendStatus(405)
            return
        }

        val assets = webAssets
        if (assets == null) {
            exchange.recordRequest("web", 404, "disabled")
            exchange.sendStatus(404)
            return
        }

        val requestPath = exchange.requestURI.path
        if (requestPath == assets.pathPrefix) {
            exchange.responseHeaders["Location"] = listOf("${assets.pathPrefix}/")
            exchange.recordRequest("web", 302, "redirect")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
            return
        }
        if (requestPath != "${assets.pathPrefix}/" && !requestPath.startsWith("${assets.pathPrefix}/")) {
            exchange.recordRequest("web", 404, "path=$requestPath")
            exchange.sendStatus(404)
            return
        }

        val relativePath = requestPath
            .removePrefix("${assets.pathPrefix}/")
            .ifBlank { assets.indexFileName }
            .let { URLDecoder.decode(it, Charsets.UTF_8) }
        val target = assets.normalizedRoot
            .resolve(relativePath)
            .normalize()
        if (!target.startsWith(assets.normalizedRoot)) {
            exchange.recordRequest("web", 404, "path=$requestPath; escaped-root")
            exchange.sendStatus(404)
            return
        }

        val file = when {
            Files.isRegularFile(target) -> target
            exchange.shouldServeWebIndex(relativePath) -> assets.indexFilePath.takeIf(Files::isRegularFile)
            else -> null
        }
        if (file == null) {
            exchange.recordRequest("web", 404, "path=$requestPath")
            exchange.sendStatus(404)
            return
        }

        exchange.sendWebFile(file, noStore = file == assets.indexFilePath)
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
            endInclusive = if (endText.isBlank()) {
                fileSize - 1
            } else {
                endText.toLongOrNull()?.coerceAtMost(fileSize - 1) ?: return null
            }
        }
        return (start..endInclusive).takeIf { start < fileSize && start <= endInclusive }
    }

    private fun contentType(path: Path): String =
        when (path.fileName.toString().substringAfterLast('.', "").lowercase()) {
            "ass" -> "text/x-ass"
            "css" -> "text/css; charset=utf-8"
            "html" -> "text/html; charset=utf-8"
            "js" -> "text/javascript; charset=utf-8"
            "json" -> "application/json; charset=utf-8"
            "srt" -> "application/x-subrip"
            "ssa" -> "text/x-ssa"
            "svg" -> "image/svg+xml"
            "vtt" -> "text/vtt"
            else -> Files.probeContentType(path) ?: "application/octet-stream"
        }

    private fun HttpExchange.shouldServeWebIndex(relativePath: String): Boolean =
        requestMethod == "GET" &&
            (
                requestHeaders.getFirst("Accept")?.contains("text/html") == true ||
                    !relativePath.substringAfterLast('/').contains('.')
                )

    private fun HttpExchange.sendWebFile(
        path: Path,
        noStore: Boolean,
    ) {
        val bodyLength = Files.size(path)
        responseHeaders["Content-Type"] = listOf(contentType(path))
        responseHeaders["Cache-Control"] = listOf(
            if (noStore) "no-store" else "public, max-age=3600",
        )
        if (requestMethod == "HEAD") {
            responseHeaders["Content-Length"] = listOf(bodyLength.toString())
            recordRequest("web", 200, "file=${path.fileName}; method=HEAD; bytes=$bodyLength")
            sendResponseHeaders(200, -1)
            close()
            return
        }
        sendResponseHeaders(200, bodyLength)
        recordRequest("web", 200, "file=${path.fileName}; bytes=$bodyLength")
        Files.newInputStream(path).use { input ->
            responseBody.use(input::copyTo)
        }
    }

    private fun HttpExchange.isAuthorized(): Boolean {
        val suppliedToken = requestURI.rawQuery
            .parseQueryParameters()
            .entries
            .firstOrNull { (key) -> key == "token" }
            ?.value
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

    private fun HttpExchange.sendJson(json: String) {
        val body = json.toByteArray()
        responseHeaders["Content-Type"] = listOf("application/json; charset=utf-8")
        responseHeaders["Cache-Control"] = listOf("no-store")
        sendResponseHeaders(200, body.size.toLong())
        responseBody.use { it.write(body) }
    }

    private fun HttpExchange.sendText(
        status: Int,
        contentType: String,
        body: String,
    ) {
        val bytes = body.toByteArray()
        responseHeaders["Content-Type"] = listOf(contentType)
        responseHeaders["Cache-Control"] = listOf("no-store")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private fun HttpExchange.recordRequest(
        category: String,
        status: Int,
        detail: String,
    ) {
        recordEvent(category, requestMethod, requestURI.path, status, detail)
    }

    private fun recordEvent(
        category: String,
        method: String,
        path: String,
        status: Int,
        detail: String,
    ) {
        runCatching {
            eventSink(
                LocalLibraryServerEvent(
                    occurredAtEpochMs = System.currentTimeMillis(),
                    category = category,
                    method = method,
                    path = path,
                    status = status,
                    detail = detail,
                ),
            )
        }
    }

    companion object {
        const val DEFAULT_PORT = 8686
        const val WEBHOOK_TOKEN_HEADER = "X-Danmaku-Webhook-Token"

        private fun generatePairingToken(): String =
            SecureRandom()
                .nextInt(1_000_000)
                .toString()
                .padStart(6, '0')
    }
}

data class PublicGetHook(
    val path: String,
    val onAccepted: (Map<String, String>) -> PublicGetHookResponse,
) {
    init {
        require(path.startsWith("/")) { "public GET hook path must be absolute" }
    }

    fun handle(queryParameters: Map<String, String>): PublicGetHookResponse =
        onAccepted(queryParameters)
}

data class PublicGetHookResponse(
    val status: Int,
    val contentType: String = "text/plain; charset=utf-8",
    val body: String,
) {
    init {
        require(status in 100..599) { "status must be a valid HTTP status code" }
        require(contentType.isNotBlank()) { "contentType must not be blank" }
    }
}

data class PublicRequestHook(
    val path: String,
    val onAccepted: (PublicRequestHookRequest) -> PublicGetHookResponse,
) {
    init {
        require(path.startsWith("/")) { "public request hook path must be absolute" }
    }

    fun handle(request: PublicRequestHookRequest): PublicGetHookResponse =
        onAccepted(request)
}

data class PublicRequestHookRequest(
    val method: String,
    val queryParameters: Map<String, String>,
    val body: String,
)
data class LocalLibraryServerEvent(
    val occurredAtEpochMs: Long,
    val category: String,
    val method: String,
    val path: String,
    val status: Int,
    val detail: String,
)

data class StaticWebAssets(
    val root: Path,
    val pathPrefix: String = "/web",
    val indexFileName: String = "index.html",
) {
    init {
        require(pathPrefix.startsWith("/")) { "pathPrefix must be absolute" }
        require(pathPrefix != "/") { "pathPrefix must not be the root path" }
        require(!pathPrefix.endsWith("/")) { "pathPrefix must not end with '/'" }
        require(indexFileName.isNotBlank()) { "indexFileName must not be blank" }
        require('/' !in indexFileName && '\\' !in indexFileName) {
            "indexFileName must be a file name, not a path"
        }
    }

    val normalizedRoot: Path =
        root.toAbsolutePath().normalize()

    val indexFilePath: Path =
        normalizedRoot.resolve(indexFileName).normalize()
}

private fun String?.parseQueryParameters(): Map<String, String> =
    this
        ?.split('&')
        ?.mapNotNull { parameter ->
            parameter.split('=', limit = 2)
                .takeIf { it.size == 2 }
                ?.let { (key, value) ->
                    URLDecoder.decode(key, Charsets.UTF_8) to
                        URLDecoder.decode(value, Charsets.UTF_8)
                }
        }
        ?.toMap()
        .orEmpty()
