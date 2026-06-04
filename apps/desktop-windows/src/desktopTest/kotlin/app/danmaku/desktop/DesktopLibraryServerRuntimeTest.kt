package app.danmaku.desktop

import app.danmaku.domain.LibraryCatalog
import app.danmaku.server.LocalLibraryServer
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopLibraryServerRuntimeTest {
    @Test
    fun authenticatedAniRssWebhookPublishesNewlyDownloadedMedia() {
        val temp = createTempDirectory("danmaku-server-runtime")
        val aniRssRoot = temp.resolve("ani-rss").createDirectories()

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            val registry = DesktopLibraryRootRegistry(store)
            val scanner = DesktopLibraryRootScanner(store, registry)
            registry.addAniRssOutputRoot(aniRssRoot)

            DesktopLibraryServerRuntime.start(
                catalogStore = store,
                rootScanner = scanner,
                port = 0,
                pairingToken = "123456",
                aniRssWebhookToken = "0123456789abcdef",
                debounceMillis = 0,
            ).use { runtime ->
                val webhookUrl = runtime.aniRssWebhookUrls()
                    .first { it.startsWith("http://127.0.0.1:") }
                assertEquals(401, connection(webhookUrl, method = "POST").responseCode)

                aniRssRoot.resolve("Example Show").createDirectories()
                    .resolve("Episode 01.mkv")
                    .writeBytes(byteArrayOf(1, 2, 3))

                assertEquals(
                    202,
                    connection(
                        url = webhookUrl,
                        method = "POST",
                        headers = mapOf(
                            LocalLibraryServer.WEBHOOK_TOKEN_HEADER to runtime.aniRssWebhookToken,
                        ),
                    ).responseCode,
                )

                val catalog = waitForCatalog(runtime.server.baseUrl())
                assertEquals(1, catalog.items.size)
                assertEquals("Example Show", catalog.items.single().seriesTitle)
                assertFalse(runtime.toString().contains(runtime.aniRssWebhookToken))
            }
        }

        temp.toFile().deleteRecursively()
    }

    private fun waitForCatalog(baseUrl: String): LibraryCatalog {
        repeat(50) {
            val response = connection("$baseUrl/api/library?token=123456")
            if (response.responseCode == 200) {
                val catalog = Json.decodeFromString<LibraryCatalog>(
                    response.inputStream.bufferedReader().use { it.readText() },
                )
                if (catalog.items.isNotEmpty()) {
                    return catalog
                }
            }
            Thread.sleep(20)
        }
        error("Webhook scan did not publish media")
    }

    private fun connection(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
    ): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            headers.forEach(::setRequestProperty)
        }
}
