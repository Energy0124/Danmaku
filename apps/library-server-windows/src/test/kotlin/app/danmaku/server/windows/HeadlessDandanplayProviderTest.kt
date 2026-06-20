package app.danmaku.server.windows

import app.danmaku.domain.DanmakuEvent
import app.danmaku.domain.DanmakuMode
import app.danmaku.domain.LibraryCatalog
import app.danmaku.provider.dandanplay.DandanplayCommentTrack
import app.danmaku.provider.dandanplay.DandanplayMatch
import app.danmaku.provider.dandanplay.DandanplayMediaFingerprint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeadlessDandanplayProviderTest {
    @Test
    fun exposesAuthenticatedDandanplayResolveEndpointForCatalogMedia() {
        val temp = createTempDirectory("danmaku-headless-dandanplay")
        val dataDirectory = temp.resolve("data")
        val root = temp.resolve("Anime").createDirectories()
        val media = root.resolve("Example Show").createDirectories().resolve("Episode 01.mkv")
        media.writeBytes("hello".encodeToByteArray())
        val capturedPaths = mutableListOf<Path>()
        val service = object : HeadlessDandanplayProviderService {
            override fun resolve(
                mediaPath: Path,
                preferredEpisodeId: Long?,
                withRelated: Boolean,
            ): HeadlessDandanplayResolveResult {
                capturedPaths.add(mediaPath)
                assertEquals(media.toAbsolutePath().normalize(), mediaPath)
                assertEquals(222L, preferredEpisodeId)
                assertEquals(false, withRelated)
                val match = DandanplayMatch(
                    episodeId = 222,
                    animeId = 333,
                    animeTitle = "Example Anime",
                    episodeTitle = "Episode 01",
                    shiftSeconds = 0.5,
                )
                return HeadlessDandanplayResolveResult(
                    fingerprint = DandanplayMediaFingerprint.fromPath(mediaPath),
                    matchCandidates = listOf(
                        DandanplayMatch(
                            episodeId = 111,
                            animeId = 333,
                            animeTitle = "Example Anime",
                            episodeTitle = "Episode 00",
                            shiftSeconds = null,
                        ),
                        match,
                    ),
                    selectedTrack = DandanplayCommentTrack(
                        match = match,
                        events = listOf(
                            DanmakuEvent(
                                id = "comment-1",
                                timestampMs = 1_500,
                                text = "hello",
                            ),
                            DanmakuEvent(
                                id = "comment-2",
                                timestampMs = 2_000,
                                text = "top",
                                style = app.danmaku.domain.DanmakuStyle(mode = DanmakuMode.TOP),
                            ),
                        ),
                    ),
                )
            }
        }

        try {
            HeadlessLibraryServer(
                options = HeadlessServerOptions(
                    dataDirectory = dataDirectory,
                    libraryRoots = listOf(root),
                    port = 0,
                    pairingToken = "123456",
                ),
                discoveryAnnouncerFactory = { NoOpCloseable },
                dandanplayProviderServiceFactory = { service },
            ).use { server ->
                server.start()
                val baseUrl = server.runtimeStatus.baseUrls.first()
                val item = Json.decodeFromString<LibraryCatalog>(
                    connection("$baseUrl/api/library?token=123456")
                        .inputStream
                        .bufferedReader()
                        .use { it.readText() },
                ).items.single()

                assertEquals(401, connection("$baseUrl/api/providers/dandanplay/resolve?mediaId=${item.id}").responseCode)
                assertEquals(400, connection("$baseUrl/api/providers/dandanplay/resolve?token=123456").responseCode)
                assertEquals(
                    404,
                    connection("$baseUrl/api/providers/dandanplay/resolve?token=123456&mediaId=missing")
                        .responseCode,
                )

                val response = Json.parseToJsonElement(
                    connection(
                        "$baseUrl/api/providers/dandanplay/resolve?token=123456&mediaId=${item.id}" +
                            "&episodeId=222&withRelated=false",
                    ).inputStream.bufferedReader().use { it.readText() },
                ).jsonObject

                assertEquals(listOf(media.toAbsolutePath().normalize()), capturedPaths)
                assertEquals(item.id, response["mediaId"]?.jsonPrimitive?.content)
                assertEquals("Episode 01.mkv", response["fingerprint"]?.jsonObject?.get("fileName")?.jsonPrimitive?.content)
                assertEquals(2, response["matches"]?.jsonArray?.size)
                assertEquals(222, response["selectedMatch"]?.jsonObject?.get("episodeId")?.jsonPrimitive?.content?.toInt())
                assertEquals(2, response["commentCount"]?.jsonPrimitive?.content?.toInt())
                assertEquals("hello", response["comments"]?.jsonArray?.first()?.jsonObject?.get("text")?.jsonPrimitive?.content)
                assertEquals(
                    "TOP",
                    response["comments"]?.jsonArray?.get(1)?.jsonObject
                        ?.get("style")?.jsonObject
                        ?.get("mode")?.jsonPrimitive?.content,
                )
            }
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    private fun connection(url: String): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
        }

    private data object NoOpCloseable : AutoCloseable {
        override fun close() = Unit
    }
}
