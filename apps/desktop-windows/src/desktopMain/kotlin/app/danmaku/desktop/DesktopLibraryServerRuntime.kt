package app.danmaku.desktop

import app.danmaku.server.AuthenticatedPostHook
import app.danmaku.host.LibraryHostMode
import app.danmaku.host.LibraryHostOperationResult
import app.danmaku.host.LibraryHostOperationStatus
import app.danmaku.host.LibraryHostRuntimeStatus
import app.danmaku.host.LibraryHostService
import app.danmaku.server.LocalLibraryServer
import app.danmaku.server.LocalLibraryServerEvent
import app.danmaku.server.PublicGetHook
import app.danmaku.server.PublicGetHookResponse
import app.danmaku.server.StaticWebAssets
import java.nio.file.Path

class DesktopLibraryServerRuntime private constructor(
    val server: LocalLibraryServer,
    val aniRssWebhookToken: String,
    private val aniRssRescanTrigger: AniRssCompletionRescanTrigger,
    private val startedAtEpochMs: Long = System.currentTimeMillis(),
) : AutoCloseable,
    LibraryHostService {
    @Volatile
    private var publishedItemCount: Int = 0

    @Volatile
    private var lastPublishedAtEpochMs: Long? = null

    fun aniRssWebhookUrls(): List<String> =
        server.networkUrls().map { "$it$ANI_RSS_DOWNLOAD_END_WEBHOOK_PATH" }

    override val runtimeStatus: LibraryHostRuntimeStatus
        get() = LibraryHostRuntimeStatus(
            mode = LibraryHostMode.EMBEDDED_DESKTOP,
            baseUrls = server.networkUrls(),
            webUiUrls = server.webUiUrls(),
            itemCount = publishedItemCount,
            startedAtEpochMs = startedAtEpochMs,
            lastPublishedAtEpochMs = lastPublishedAtEpochMs,
        )

    override fun start(): LibraryHostOperationResult =
        LibraryHostOperationResult(
            status = LibraryHostOperationStatus.SUCCEEDED,
            message = "Desktop library host is already started.",
            itemCount = publishedItemCount,
        )

    override fun stop(): LibraryHostOperationResult {
        close()
        return LibraryHostOperationResult(
            status = LibraryHostOperationStatus.SUCCEEDED,
            message = "Desktop library host stopped.",
            itemCount = publishedItemCount,
        )
    }

    override fun rescan(reason: String): LibraryHostOperationResult =
        LibraryHostOperationResult(
            status = LibraryHostOperationStatus.FAILED,
            message = "Desktop rescans are currently owned by desktop library actions: $reason.",
            itemCount = publishedItemCount,
        )

    override fun publishCurrentLibrary(): LibraryHostOperationResult =
        LibraryHostOperationResult(
            status = LibraryHostOperationStatus.FAILED,
            message = "Desktop publishes are currently owned by desktop library actions.",
            itemCount = publishedItemCount,
        )

    fun recordPublishedLibrary(itemCount: Int) {
        publishedItemCount = itemCount
        lastPublishedAtEpochMs = System.currentTimeMillis()
    }

    override fun close() {
        aniRssRescanTrigger.close()
        server.close()
    }

    override fun toString(): String =
        "DesktopLibraryServerRuntime(server=$server, aniRssWebhookToken=<redacted>)"

    companion object {
        const val ANI_RSS_DOWNLOAD_END_WEBHOOK_PATH = "/api/hooks/ani-rss/download-end"
        const val MY_ANIME_LIST_OAUTH_CALLBACK_PATH = "/oauth/myanimelist/callback"

        fun start(
            catalogStore: DesktopLibraryCatalogStore,
            rootScanner: DesktopLibraryRootScanner,
            metadataResolver: DesktopAnimeMetadataResolver? = null,
            dandanplayDanmakuResolver: DesktopDandanplayDanmakuResolver? = null,
            port: Int = LocalLibraryServer.DEFAULT_PORT,
            pairingToken: String? = null,
            aniRssWebhookToken: String,
            webAssetsRoot: Path? = null,
            debounceMillis: Long = 1_000L,
            onLibraryPublished: (IndexedLocalLibrary) -> Unit = {},
            onServerEvent: (LocalLibraryServerEvent) -> Unit = {},
            onMyAnimeListOAuthCallback: ((Map<String, String>) -> PublicGetHookResponse)? = null,
        ): DesktopLibraryServerRuntime {
            lateinit var server: LocalLibraryServer
            lateinit var runtime: DesktopLibraryServerRuntime
            val trigger = AniRssCompletionRescanTrigger(
                scanAniRssRoots = rootScanner::scanAniRssRoots,
                onScanComplete = { batch ->
                    server.publish(batch.publishedLibrary.toPublishedLibrary(metadataResolver))
                    runtime.recordPublishedLibrary(batch.publishedLibrary.catalog.items.size)
                    onLibraryPublished(batch.publishedLibrary)
                },
                debounceMillis = debounceMillis,
            )
            val hook = AuthenticatedPostHook(
                path = ANI_RSS_DOWNLOAD_END_WEBHOOK_PATH,
                token = aniRssWebhookToken,
                onAccepted = { trigger.requestRescan() },
            )
            val publicGetHooks = listOfNotNull(
                onMyAnimeListOAuthCallback?.let { callback ->
                    PublicGetHook(
                        path = MY_ANIME_LIST_OAUTH_CALLBACK_PATH,
                        onAccepted = callback,
                    )
                },
            )
            return try {
                server = if (pairingToken == null) {
                    LocalLibraryServer(
                        port = port,
                        progressStore = catalogStore,
                        authenticatedPostHooks = listOf(hook),
                        publicGetHooks = publicGetHooks,
                        webAssets = webAssetsRoot?.let(::StaticWebAssets),
                        danmakuResolver = dandanplayDanmakuResolver?.let(::DesktopLanDanmakuResolver),
                        eventSink = onServerEvent,
                    )
                } else {
                    LocalLibraryServer(
                        port = port,
                        pairingToken = pairingToken,
                        progressStore = catalogStore,
                        authenticatedPostHooks = listOf(hook),
                        publicGetHooks = publicGetHooks,
                        webAssets = webAssetsRoot?.let(::StaticWebAssets),
                        danmakuResolver = dandanplayDanmakuResolver?.let(::DesktopLanDanmakuResolver),
                        eventSink = onServerEvent,
                    )
                }
                runtime = DesktopLibraryServerRuntime(
                    server = server,
                    aniRssWebhookToken = aniRssWebhookToken,
                    aniRssRescanTrigger = trigger,
                )
                server.start()
                runtime
            } catch (error: Throwable) {
                trigger.close()
                throw error
            }
        }
    }
}
