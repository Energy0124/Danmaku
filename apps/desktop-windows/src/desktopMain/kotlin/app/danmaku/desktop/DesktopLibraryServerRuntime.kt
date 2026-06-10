package app.danmaku.desktop

import app.danmaku.server.AuthenticatedPostHook
import app.danmaku.server.LocalLibraryServer
import app.danmaku.server.LocalLibraryServerEvent
import app.danmaku.server.PublicGetHook
import app.danmaku.server.PublicGetHookResponse

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
        const val MY_ANIME_LIST_OAUTH_CALLBACK_PATH = "/oauth/myanimelist/callback"

        fun start(
            catalogStore: DesktopLibraryCatalogStore,
            rootScanner: DesktopLibraryRootScanner,
            metadataResolver: DesktopAnimeMetadataResolver? = null,
            port: Int = LocalLibraryServer.DEFAULT_PORT,
            pairingToken: String? = null,
            aniRssWebhookToken: String,
            debounceMillis: Long = 1_000L,
            onLibraryPublished: (IndexedLocalLibrary) -> Unit = {},
            onServerEvent: (LocalLibraryServerEvent) -> Unit = {},
            onMyAnimeListOAuthCallback: ((Map<String, String>) -> PublicGetHookResponse)? = null,
        ): DesktopLibraryServerRuntime {
            lateinit var server: LocalLibraryServer
            val trigger = AniRssCompletionRescanTrigger(
                scanAniRssRoots = rootScanner::scanAniRssRoots,
                onScanComplete = { batch ->
                    server.publish(batch.publishedLibrary.toPublishedLibrary(metadataResolver))
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
                        eventSink = onServerEvent,
                    )
                } else {
                    LocalLibraryServer(
                        port = port,
                        pairingToken = pairingToken,
                        progressStore = catalogStore,
                        authenticatedPostHooks = listOf(hook),
                        publicGetHooks = publicGetHooks,
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
