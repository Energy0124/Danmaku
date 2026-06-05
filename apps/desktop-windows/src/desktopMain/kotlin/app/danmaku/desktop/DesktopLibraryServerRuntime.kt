package app.danmaku.desktop

import app.danmaku.server.AuthenticatedPostHook
import app.danmaku.server.LocalLibraryServer
import app.danmaku.server.LocalLibraryServerEvent

class DesktopLibraryServerRuntime private constructor(
    val server: LocalLibraryServer,
    val aniRssWebhookToken: String,
    private val aniRssRescanTrigger: AniRssCompletionRescanTrigger,
) : AutoCloseable {
    fun aniRssWebhookUrls(): List<String> =
        server.networkUrls().map { "$it$ANI_RSS_DOWNLOAD_END_WEBHOOK_PATH" }

    override fun close() {
        aniRssRescanTrigger.close()
        server.close()
    }

    override fun toString(): String =
        "DesktopLibraryServerRuntime(server=$server, aniRssWebhookToken=<redacted>)"

    companion object {
        const val ANI_RSS_DOWNLOAD_END_WEBHOOK_PATH = "/api/hooks/ani-rss/download-end"

        fun start(
            catalogStore: DesktopLibraryCatalogStore,
            rootScanner: DesktopLibraryRootScanner,
            port: Int = LocalLibraryServer.DEFAULT_PORT,
            pairingToken: String? = null,
            aniRssWebhookToken: String,
            debounceMillis: Long = 1_000L,
            onLibraryPublished: (IndexedLocalLibrary) -> Unit = {},
            onServerEvent: (LocalLibraryServerEvent) -> Unit = {},
        ): DesktopLibraryServerRuntime {
            lateinit var server: LocalLibraryServer
            val trigger = AniRssCompletionRescanTrigger(
                scanAniRssRoots = rootScanner::scanAniRssRoots,
                onScanComplete = { batch ->
                    server.publish(batch.publishedLibrary.toPublishedLibrary())
                    onLibraryPublished(batch.publishedLibrary)
                },
                debounceMillis = debounceMillis,
            )
            val hook = AuthenticatedPostHook(
                path = ANI_RSS_DOWNLOAD_END_WEBHOOK_PATH,
                token = aniRssWebhookToken,
                onAccepted = { trigger.requestRescan() },
            )
            return try {
                server = if (pairingToken == null) {
                    LocalLibraryServer(
                        port = port,
                        progressStore = catalogStore,
                        authenticatedPostHooks = listOf(hook),
                        eventSink = onServerEvent,
                    )
                } else {
                    LocalLibraryServer(
                        port = port,
                        pairingToken = pairingToken,
                        progressStore = catalogStore,
                        authenticatedPostHooks = listOf(hook),
                        eventSink = onServerEvent,
                    )
                }
                server.start()
                DesktopLibraryServerRuntime(
                    server = server,
                    aniRssWebhookToken = aniRssWebhookToken,
                    aniRssRescanTrigger = trigger,
                )
            } catch (error: Throwable) {
                trigger.close()
                throw error
            }
        }
    }
}
