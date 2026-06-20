package app.danmaku.server.windows

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeListEntry
import app.danmaku.domain.ExternalAnimeMatchQuery
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeTrackingUpdate
import app.danmaku.domain.LanDandanplayProviderStatus
import app.danmaku.domain.LanDandanplayRuntimeCapability
import app.danmaku.domain.LanExternalAnimeProviderStatus
import app.danmaku.domain.LanExternalAnimeRuntimeCapability
import app.danmaku.domain.LanLibraryServerStatus
import app.danmaku.domain.LanProviderRuntimeStatus
import app.danmaku.domain.LanProviderSettingsStatus
import app.danmaku.host.LibraryHostMode
import app.danmaku.host.LibraryHostOperationResult
import app.danmaku.host.LibraryHostOperationStatus
import app.danmaku.host.LibraryHostRuntimeStatus
import app.danmaku.host.LibraryHostService
import app.danmaku.server.FilePlaybackProgressStore
import app.danmaku.server.LocalLibraryDiscoveryAnnouncer
import app.danmaku.server.LocalLibraryServer
import app.danmaku.server.PublicGetHook
import app.danmaku.server.PublicGetHookResponse
import app.danmaku.server.PublicRequestHook
import app.danmaku.server.PublicRequestHookRequest
import app.danmaku.server.PublishedLibrary
import app.danmaku.server.StaticWebAssets
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

fun main(args: Array<String>) {
    val options = HeadlessServerOptions.parse(args.asList(), System.getenv())
    HeadlessLibraryServer(options).use { host ->
        val start = host.start()
        println(start.message)
        println("Base URLs: ${host.runtimeStatus.baseUrls.joinToString()}")
        println("Web UI URLs: ${host.runtimeStatus.webUiUrls.joinToString().ifBlank { "disabled" }}")
        println("Pairing token: ${host.pairingToken}")
        Runtime.getRuntime().addShutdownHook(Thread { host.close() })
        CountDownLatch(1).await()
    }
}

internal data class HeadlessServerOptions(
    val dataDirectory: Path,
    val libraryRoots: List<Path> = emptyList(),
    val port: Int = LocalLibraryServer.DEFAULT_PORT,
    val pairingToken: String? = null,
    val webAssetsRoot: Path? = null,
) {
    init {
        require(port in 0..65_535) { "port must be between 0 and 65535" }
        require(pairingToken == null || pairingToken.isNotBlank()) { "pairingToken must not be blank" }
    }

    companion object {
        private const val DATA_DIR_ENV = "DANMAKU_SERVER_DATA_DIR"
        private const val WEB_UI_DIST_ENV = "DANMAKU_WEB_UI_DIST"

        fun parse(
            args: List<String>,
            environment: Map<String, String>,
        ): HeadlessServerOptions {
            var dataDirectory = environment[DATA_DIR_ENV]
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?: Path.of("data", "library-server")
            val roots = mutableListOf<Path>()
            var port = LocalLibraryServer.DEFAULT_PORT
            var pairingToken: String? = null
            var webAssetsRoot = environment[WEB_UI_DIST_ENV]
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)

            var index = 0
            while (index < args.size) {
                val arg = args[index]
                when {
                    arg == "--data-dir" -> {
                        dataDirectory = args.valueAfter(arg, index).let(Path::of)
                        index += 1
                    }
                    arg.startsWith("--data-dir=") -> {
                        dataDirectory = arg.substringAfter("=").takeIf(String::isNotBlank)?.let(Path::of)
                            ?: error("--data-dir requires a value")
                    }
                    arg == "--root" -> {
                        roots.add(args.valueAfter(arg, index).let(Path::of))
                        index += 1
                    }
                    arg.startsWith("--root=") -> {
                        roots.add(
                            arg.substringAfter("=").takeIf(String::isNotBlank)?.let(Path::of)
                                ?: error("--root requires a value"),
                        )
                    }
                    arg == "--port" -> {
                        port = args.valueAfter(arg, index).toPort()
                        index += 1
                    }
                    arg.startsWith("--port=") -> {
                        port = arg.substringAfter("=").toPort()
                    }
                    arg == "--pairing-token" -> {
                        pairingToken = args.valueAfter(arg, index)
                        index += 1
                    }
                    arg.startsWith("--pairing-token=") -> {
                        pairingToken = arg.substringAfter("=").takeIf(String::isNotBlank)
                            ?: error("--pairing-token requires a value")
                    }
                    arg == "--web-assets-dir" || arg == "--web-ui-dist" -> {
                        webAssetsRoot = args.valueAfter(arg, index).let(Path::of)
                        index += 1
                    }
                    arg.startsWith("--web-assets-dir=") -> {
                        webAssetsRoot = arg.substringAfter("=").takeIf(String::isNotBlank)?.let(Path::of)
                    }
                    arg.startsWith("--web-ui-dist=") -> {
                        webAssetsRoot = arg.substringAfter("=").takeIf(String::isNotBlank)?.let(Path::of)
                    }
                }
                index += 1
            }

            return HeadlessServerOptions(
                dataDirectory = dataDirectory,
                libraryRoots = roots,
                port = port,
                pairingToken = pairingToken,
                webAssetsRoot = webAssetsRoot,
            )
        }

        private fun List<String>.valueAfter(option: String, index: Int): String =
            getOrNull(index + 1)?.takeUnless { it.startsWith("--") }?.takeIf(String::isNotBlank)
                ?: error("$option requires a value")

        private fun String.toPort(): Int =
            toIntOrNull()
                ?.takeIf { it in 0..65_535 }
                ?: error("Port must be between 0 and 65535: $this")
    }
}

internal class HeadlessLibraryServer(
    private val options: HeadlessServerOptions,
    private val discoveryAnnouncerFactory: (Int) -> AutoCloseable = { port ->
        LocalLibraryDiscoveryAnnouncer(port).apply { start() }
    },
    private val externalAnimeSearchServiceFactory: (HeadlessServerSettings) -> HeadlessExternalAnimeSearchService =
        { settings -> settings.toExternalAnimeSearchService() },
    private val externalAnimeTrackingServiceFactory: (HeadlessServerSettings) -> HeadlessExternalAnimeTrackingService =
        { settings -> settings.toExternalAnimeTrackingService() },
    private val dandanplayProviderServiceFactory: (HeadlessServerSettings) -> HeadlessDandanplayProviderService =
        { settings -> settings.toDandanplayProviderService() },
) : AutoCloseable,
    LibraryHostService {
    private val startedAtEpochMs = System.currentTimeMillis()
    private val lockHandle = DataDirectoryLock.acquire(options.dataDirectory)
    private val progressStore = FilePlaybackProgressStore(options.dataDirectory.resolve("progress.json"))
    private val catalogStore = HeadlessLibraryCatalogStore(options.dataDirectory.resolve("catalog.json"))
    private val settingsStore = HeadlessServerSettingsStore(options.dataDirectory.resolve("server-settings.json"))
    private val serverSettings = settingsStore.loadOrCreate(options.pairingToken)
    private val externalAnimeSearchService = externalAnimeSearchServiceFactory(serverSettings)
    private val externalAnimeTrackingService = externalAnimeTrackingServiceFactory(serverSettings)
    private val dandanplayProviderService = dandanplayProviderServiceFactory(serverSettings)
    private val libraryRoots = options.libraryRoots.ifEmpty { serverSettings.libraryRoots }
    private val cachedLibrary = catalogStore.load()
    @Volatile
    private var publishedLibrary = cachedLibrary?.publishedLibrary ?: PublishedLibrary.EMPTY
    private val server = createServer()
    private val closed = AtomicBoolean(false)
    private var discoveryAnnouncer: AutoCloseable? = null

    @Volatile
    private var publishedItemCount: Int = 0

    @Volatile
    private var lastPublishedAtEpochMs: Long? = null

    @Volatile
    private var lastScanStatus: LibraryHostOperationStatus = LibraryHostOperationStatus.IDLE

    val pairingToken: String
        get() = server.pairingToken

    init {
        val storedLibrary = cachedLibrary
        if (storedLibrary == null) {
            server.publish(PublishedLibrary.EMPTY)
        } else {
            publishStoredLibrary(storedLibrary)
        }
    }

    override val runtimeStatus: LibraryHostRuntimeStatus
        get() = LibraryHostRuntimeStatus(
            mode = LibraryHostMode.HEADLESS_SERVER,
            baseUrls = server.networkUrls(),
            webUiUrls = server.webUiUrls(),
            itemCount = publishedItemCount,
            startedAtEpochMs = startedAtEpochMs,
            lastPublishedAtEpochMs = lastPublishedAtEpochMs,
            lastScanStatus = lastScanStatus,
        )

    override fun start(): LibraryHostOperationResult {
        val publishResult = publishCurrentLibrary()
        server.start()
        discoveryAnnouncer = discoveryAnnouncer ?: discoveryAnnouncerFactory(server.localPort)
        return if (publishResult.status == LibraryHostOperationStatus.SUCCEEDED) {
            LibraryHostOperationResult(
                status = LibraryHostOperationStatus.SUCCEEDED,
                message = "Headless Danmaku library server started on port ${server.localPort}; " +
                    "published ${publishedItemCount} items.",
                itemCount = publishedItemCount,
            )
        } else {
            publishResult
        }
    }

    override fun stop(): LibraryHostOperationResult {
        close()
        return LibraryHostOperationResult(
            status = LibraryHostOperationStatus.SUCCEEDED,
            message = "Headless Danmaku library server stopped.",
            itemCount = publishedItemCount,
        )
    }

    override fun rescan(reason: String): LibraryHostOperationResult =
        publishCurrentLibrary(reason)

    override fun publishCurrentLibrary(): LibraryHostOperationResult =
        publishCurrentLibrary(reason = "manual")

    private fun publishCurrentLibrary(reason: String): LibraryHostOperationResult {
        val storedLibrary = cachedLibrary
        if (libraryRoots.isEmpty() && storedLibrary != null) {
            lastScanStatus = LibraryHostOperationStatus.SUCCEEDED
            return LibraryHostOperationResult(
                status = LibraryHostOperationStatus.SUCCEEDED,
                message = "Using cached headless catalog with ${publishedItemCount} items; no roots configured ($reason).",
                itemCount = publishedItemCount,
            )
        }

        lastScanStatus = LibraryHostOperationStatus.RUNNING
        return runCatching {
            HeadlessLocalLibraryScanner.scan(libraryRoots)
        }.fold(
            onSuccess = { scan ->
                val stored = if (scan.scannedRootCount == 0 && scan.publishedLibrary.catalog.items.isEmpty()) {
                    HeadlessStoredLibrary(
                        publishedLibrary = scan.publishedLibrary,
                        savedAtEpochMs = System.currentTimeMillis(),
                    )
                } else {
                    catalogStore.save(scan.publishedLibrary)
                }
                publishStoredLibrary(stored)
                lastScanStatus = LibraryHostOperationStatus.SUCCEEDED
                LibraryHostOperationResult(
                    status = LibraryHostOperationStatus.SUCCEEDED,
                    message = "Published $publishedItemCount items from ${scan.scannedRootCount} roots ($reason).",
                    itemCount = publishedItemCount,
                )
            },
            onFailure = { error ->
                lastScanStatus = LibraryHostOperationStatus.FAILED
                LibraryHostOperationResult(
                    status = LibraryHostOperationStatus.FAILED,
                    message = "Headless library scan failed: ${error.message}",
                    itemCount = publishedItemCount,
                )
            },
        )
    }

    private fun publishStoredLibrary(stored: HeadlessStoredLibrary) {
        server.publish(stored.publishedLibrary)
        publishedLibrary = stored.publishedLibrary
        publishedItemCount = stored.publishedLibrary.catalog.items.size
        lastPublishedAtEpochMs = stored.savedAtEpochMs
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            discoveryAnnouncer?.close()
            server.close()
            lockHandle.close()
        }
    }

    private fun createServer(): LocalLibraryServer =
        LocalLibraryServer(
            port = options.port,
            pairingToken = serverSettings.pairingToken,
            progressStore = progressStore,
            publicGetHooks = listOf(
                serverSettings.providerRuntimeHook(),
                serverSettings.providerSearchHook(externalAnimeSearchService),
                serverSettings.dandanplayResolveHook(dandanplayProviderService) { publishedLibrary },
            ),
            publicRequestHooks = listOf(
                serverSettings.providerListEntryHook(externalAnimeTrackingService),
            ),
            webAssets = options.webAssetsRoot?.let(::StaticWebAssets),
            hostMode = LanLibraryServerStatus.HOST_MODE_HEADLESS_SERVER,
            providerSettings = serverSettings.toLanProviderSettingsStatus(),
        )
}

private fun HeadlessServerSettings.toLanProviderSettingsStatus(): LanProviderSettingsStatus =
    LanProviderSettingsStatus(
        dandanplay = LanDandanplayProviderStatus(
            baseUrl = dandanplay.baseUrl,
            appId = dandanplay.appId,
            hasAppSecret = dandanplay.hasAppSecret,
            authenticationMode = dandanplay.authenticationMode.name,
            cacheMaxAgeDays = dandanplay.cacheMaxAgeDays,
        ),
        externalAnime = LanExternalAnimeProviderStatus(
            myAnimeListClientId = externalAnime.myAnimeListClientId,
            hasMyAnimeListClientSecret = externalAnime.hasMyAnimeListClientSecret,
            hasMyAnimeListAccessToken = externalAnime.hasMyAnimeListAccessToken,
            bangumiBaseUrl = externalAnime.bangumiBaseUrl,
            bangumiUserAgent = externalAnime.bangumiUserAgent,
            hasBangumiAccessToken = externalAnime.hasBangumiAccessToken,
        ),
    )

private val headlessServerJson = Json {
    encodeDefaults = true
}

private fun HeadlessServerSettings.providerRuntimeHook(): PublicGetHook =
    PublicGetHook("/api/providers/runtime") { queryParameters ->
        if (!queryParameters.hasPairingToken(pairingToken)) {
            PublicGetHookResponse(status = 401, body = "Unauthorized.")
        } else {
            PublicGetHookResponse(
                status = 200,
                contentType = "application/json; charset=utf-8",
                body = headlessServerJson.encodeToString(toLanProviderRuntimeStatus()),
            )
        }
    }

private fun HeadlessServerSettings.providerSearchHook(
    searchService: HeadlessExternalAnimeSearchService,
): PublicGetHook =
    PublicGetHook("/api/providers/search") { queryParameters ->
        if (!queryParameters.hasPairingToken(pairingToken)) {
            PublicGetHookResponse(status = 401, body = "Unauthorized.")
        } else {
            queryParameters.toProviderSearchResponse(searchService)
        }
    }

private fun Map<String, String>.toProviderSearchResponse(
    searchService: HeadlessExternalAnimeSearchService,
): PublicGetHookResponse {
    val title = get("title")?.trim()
        ?.takeIf(String::isNotBlank)
        ?: return PublicGetHookResponse(status = 400, body = "Query parameter 'title' is required.")
    val limit = get("limit")?.toIntOrNull()?.takeIf { it in 1..50 } ?: 10
    val episodeCount = get("episodeCount")?.toIntOrNull()?.takeIf { it > 0 }
    val startYear = get("startYear")?.toIntOrNull()?.takeIf { it in 1900..2200 }
    if (containsKey("limit") && get("limit")?.toIntOrNull()?.takeIf { it in 1..50 } == null) {
        return PublicGetHookResponse(status = 400, body = "Query parameter 'limit' must be between 1 and 50.")
    }
    if (containsKey("episodeCount") && episodeCount == null) {
        return PublicGetHookResponse(status = 400, body = "Query parameter 'episodeCount' must be positive.")
    }
    if (containsKey("startYear") && startYear == null) {
        return PublicGetHookResponse(status = 400, body = "Query parameter 'startYear' must be between 1900 and 2200.")
    }
    val providers = get("providers")
        ?.split(",")
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?.map { provider ->
            provider.toExternalAnimeProviderOrNull()
                ?: return PublicGetHookResponse(status = 400, body = "Unsupported provider '$provider'.")
        }
        ?.toSet()
        .orEmpty()
    val matches = searchService.search(
        query = ExternalAnimeMatchQuery(
            title = title,
            episodeCount = episodeCount,
            startYear = startYear,
        ),
        providers = providers,
        limitPerProvider = limit,
    )
    return PublicGetHookResponse(
        status = 200,
        contentType = "application/json; charset=utf-8",
        body = headlessServerJson.encodeToString(matches),
    )
}

private fun String.toExternalAnimeProviderOrNull(): ExternalAnimeProvider? =
    when (trim().lowercase().replace("-", "_")) {
        "myanimelist", "my_anime_list", "mal" -> ExternalAnimeProvider.MY_ANIME_LIST
        "bangumi", "bgm" -> ExternalAnimeProvider.BANGUMI
        "dandanplay", "dan_dan_play" -> ExternalAnimeProvider.DANDANPLAY
        else -> null
    }

private fun HeadlessServerSettings.providerListEntryHook(
    trackingService: HeadlessExternalAnimeTrackingService,
): PublicRequestHook =
    PublicRequestHook("/api/providers/list/entry") { request ->
        if (!request.queryParameters.hasPairingToken(pairingToken)) {
            PublicGetHookResponse(status = 401, body = "Unauthorized.")
        } else {
            request.toProviderListEntryResponse(trackingService)
        }
    }

private fun PublicRequestHookRequest.toProviderListEntryResponse(
    trackingService: HeadlessExternalAnimeTrackingService,
): PublicGetHookResponse =
    when (method) {
        "GET" -> queryParameters.toProviderListReadResponse(trackingService)
        "POST" -> toProviderListWriteResponse(trackingService)
        else -> PublicGetHookResponse(status = 405, body = "Method not allowed.")
    }

private fun Map<String, String>.toProviderListReadResponse(
    trackingService: HeadlessExternalAnimeTrackingService,
): PublicGetHookResponse {
    val animeId = toExternalAnimeIdQuery() ?: return invalidExternalAnimeIdResponse()
    if (animeId.provider == ExternalAnimeProvider.DANDANPLAY) {
        return PublicGetHookResponse(status = 400, body = "dandanplay does not support external list entries.")
    }
    return runCatching { trackingService.fetchListEntry(animeId) }
        .fold(
            onSuccess = { entry ->
                if (entry == null) {
                    PublicGetHookResponse(status = 404, body = "External list entry was not found.")
                } else {
                    entry.toProviderListSuccessResponse()
                }
            },
            onFailure = { error -> error.toProviderListFailureResponse() },
        )
}

private fun PublicRequestHookRequest.toProviderListWriteResponse(
    trackingService: HeadlessExternalAnimeTrackingService,
): PublicGetHookResponse {
    val update = runCatching {
        headlessServerJson.decodeFromString<ExternalAnimeTrackingUpdate>(body)
    }.getOrNull()
        ?: return PublicGetHookResponse(
            status = 400,
            body = "Request body must be an ExternalAnimeTrackingUpdate JSON object.",
        )
    if (update.animeId.provider == ExternalAnimeProvider.DANDANPLAY) {
        return PublicGetHookResponse(status = 400, body = "dandanplay does not support external list entries.")
    }
    return runCatching { trackingService.updateListEntry(update) }
        .fold(
            onSuccess = ExternalAnimeListEntry::toProviderListSuccessResponse,
            onFailure = Throwable::toProviderListFailureResponse,
        )
}

private fun Map<String, String>.toExternalAnimeIdQuery(): ExternalAnimeId? {
    val provider = get("provider")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.toExternalAnimeProviderOrNull()
        ?: return null
    val value = get("animeId")
        ?.trim()
        ?.toLongOrNull()
        ?.takeIf { it > 0 }
        ?: return null
    return ExternalAnimeId(provider, value)
}

private fun Map<String, String>.invalidExternalAnimeIdResponse(): PublicGetHookResponse {
    val provider = get("provider")?.trim()
    if (provider.isNullOrBlank()) {
        return PublicGetHookResponse(status = 400, body = "Query parameter 'provider' is required.")
    }
    if (provider.toExternalAnimeProviderOrNull() == null) {
        return PublicGetHookResponse(status = 400, body = "Unsupported provider '$provider'.")
    }
    return PublicGetHookResponse(status = 400, body = "Query parameter 'animeId' must be positive.")
}

private fun ExternalAnimeListEntry.toProviderListSuccessResponse(): PublicGetHookResponse =
    PublicGetHookResponse(
        status = 200,
        contentType = "application/json; charset=utf-8",
        body = headlessServerJson.encodeToString(this),
    )

private fun Throwable.toProviderListFailureResponse(): PublicGetHookResponse =
    when (this) {
        is HeadlessExternalAnimeTrackingUnavailableException -> PublicGetHookResponse(
            status = 409,
            body = message ?: "Provider list sync credentials are not configured.",
        )
        else -> PublicGetHookResponse(
            status = 502,
            body = "external list request failed: ${message ?: this::class.simpleName}",
        )
    }

private fun HeadlessServerSettings.dandanplayResolveHook(
    providerService: HeadlessDandanplayProviderService,
    publishedLibraryProvider: () -> PublishedLibrary,
): PublicGetHook =
    PublicGetHook("/api/providers/dandanplay/resolve") { queryParameters ->
        if (!queryParameters.hasPairingToken(pairingToken)) {
            PublicGetHookResponse(status = 401, body = "Unauthorized.")
        } else {
            queryParameters.toDandanplayResolveResponse(providerService, publishedLibraryProvider())
        }
    }

private fun Map<String, String>.toDandanplayResolveResponse(
    providerService: HeadlessDandanplayProviderService,
    library: PublishedLibrary,
): PublicGetHookResponse {
    val mediaId = get("mediaId")?.trim()
        ?.takeIf(String::isNotBlank)
        ?: return PublicGetHookResponse(status = 400, body = "Query parameter 'mediaId' is required.")
    val preferredEpisodeId = get("episodeId")?.toLongOrNull()?.takeIf { it > 0 }
    if (containsKey("episodeId") && preferredEpisodeId == null) {
        return PublicGetHookResponse(status = 400, body = "Query parameter 'episodeId' must be positive.")
    }
    val withRelated = get("withRelated")?.toBooleanQueryParameter()
        ?: if (containsKey("withRelated")) {
            return PublicGetHookResponse(status = 400, body = "Query parameter 'withRelated' must be true or false.")
        } else {
            true
        }
    val mediaPath = library.filesById[mediaId]?.toAbsolutePath()?.normalize()
        ?: return PublicGetHookResponse(status = 404, body = "Media item was not found.")
    if (!Files.isRegularFile(mediaPath)) {
        return PublicGetHookResponse(status = 404, body = "Media file was not found.")
    }
    return runCatching {
        providerService.resolve(
            mediaPath = mediaPath,
            preferredEpisodeId = preferredEpisodeId,
            withRelated = withRelated,
        )
    }.fold(
        onSuccess = { result ->
            PublicGetHookResponse(
                status = 200,
                contentType = "application/json; charset=utf-8",
                body = headlessServerJson.encodeToString(
                    JsonObject.serializer(),
                    result.toJsonObject(mediaId),
                ),
            )
        },
        onFailure = { error ->
            PublicGetHookResponse(
                status = 502,
                body = "dandanplay request failed: ${error.message ?: error::class.simpleName}",
            )
        },
    )
}

private fun String.toBooleanQueryParameter(): Boolean? =
    when (trim().lowercase()) {
        "true", "1", "yes" -> true
        "false", "0", "no" -> false
        else -> null
    }

private fun HeadlessServerSettings.toLanProviderRuntimeStatus(): LanProviderRuntimeStatus =
    LanProviderRuntimeStatus(
        dandanplay = dandanplay.toLanRuntimeCapability(),
        myAnimeList = externalAnime.toMyAnimeListRuntimeCapability(),
        bangumi = externalAnime.toBangumiRuntimeCapability(),
    )

private fun HeadlessDandanplayProviderSettings.toLanRuntimeCapability(): LanDandanplayRuntimeCapability =
    LanDandanplayRuntimeCapability(
        matchAvailable = isFetchEnabled,
        commentFetchAvailable = isFetchEnabled,
        authenticated = hasCredentials,
        reasonCode = when {
            hasCredentials -> "credentials-configured"
            isFetchEnabled -> "compatible-api-server"
            else -> "missing-credentials"
        },
    )

private fun HeadlessExternalAnimeProviderSettings.toMyAnimeListRuntimeCapability(): LanExternalAnimeRuntimeCapability {
    val searchAvailable = myAnimeListClientId != null
    val listAvailable = myAnimeListAccessToken != null
    return LanExternalAnimeRuntimeCapability(
        searchAvailable = searchAvailable,
        listReadAvailable = listAvailable,
        listWriteAvailable = listAvailable,
        authenticated = listAvailable,
        reasonCode = when {
            listAvailable -> "oauth-token-saved"
            searchAvailable && hasMyAnimeListClientSecret -> "client-secret-saved"
            searchAvailable -> "client-id-saved"
            else -> "missing-client-id"
        },
    )
}

private fun HeadlessExternalAnimeProviderSettings.toBangumiRuntimeCapability(): LanExternalAnimeRuntimeCapability {
    val listAvailable = bangumiAccessToken != null
    return LanExternalAnimeRuntimeCapability(
        searchAvailable = true,
        listReadAvailable = listAvailable,
        listWriteAvailable = listAvailable,
        authenticated = listAvailable,
        reasonCode = if (listAvailable) "access-token-saved" else "public-search",
    )
}

private fun Map<String, String>.hasPairingToken(expectedToken: String): Boolean {
    val suppliedToken = this["token"] ?: return false
    return MessageDigest.isEqual(expectedToken.toByteArray(), suppliedToken.toByteArray())
}

private class DataDirectoryLock private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
) : AutoCloseable {
    override fun close() {
        lock.release()
        channel.close()
    }

    companion object {
        fun acquire(dataDirectory: Path): DataDirectoryLock {
            Files.createDirectories(dataDirectory)
            val lockFile = dataDirectory.resolve(".danmaku-host.lock")
            val channel = FileChannel.open(
                lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
            val lock = channel.tryLock()
                ?: error("Danmaku data directory is already locked by another host: $dataDirectory")
            return DataDirectoryLock(channel, lock)
        }
    }
}
