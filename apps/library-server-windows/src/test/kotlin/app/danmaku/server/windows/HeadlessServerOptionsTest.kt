package app.danmaku.server.windows

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LanLibraryServerStatus
import app.danmaku.domain.PlaybackProgress
import app.danmaku.host.LibraryHostMode
import app.danmaku.host.LibraryHostOperationStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HeadlessServerOptionsTest {
    @Test
    fun parsesCliAndEnvironmentOptions() {
        val options = HeadlessServerOptions.parse(
            args = listOf(
                "--root=W:/Anime",
                "--port",
                "0",
                "--pairing-token=123456",
            ),
            environment = mapOf(
                "DANMAKU_SERVER_DATA_DIR" to "S:/Danmaku/server-data",
                "DANMAKU_WEB_UI_DIST" to "apps/web-ui/dist",
            ),
        )

        assertEquals(Path.of("S:/Danmaku/server-data"), options.dataDirectory)
        assertEquals(listOf(Path.of("W:/Anime")), options.libraryRoots)
        assertEquals(0, options.port)
        assertEquals("123456", options.pairingToken)
        assertEquals(Path.of("apps/web-ui/dist"), options.webAssetsRoot)
    }

    @Test
    fun rejectsInvalidPort() {
        assertFailsWith<IllegalStateException> {
            HeadlessServerOptions.parse(listOf("--port=70000"), emptyMap())
        }
    }

    @Test
    fun persistsGeneratedPairingTokenAcrossHeadlessRestarts() {
        val dataDirectory = createTempDirectory("danmaku-headless-token")
        lateinit var token: String

        try {
            headlessServer(
                HeadlessServerOptions(
                    dataDirectory = dataDirectory,
                    port = 0,
                ),
            ).use { server ->
                server.start()
                token = server.pairingToken
                assertEquals(6, token.length)
                assertEquals(true, token.all { it.isDigit() })
                assertEquals(true, dataDirectory.resolve("server-settings.json").toFile().isFile)
            }

            headlessServer(
                HeadlessServerOptions(
                    dataDirectory = dataDirectory,
                    port = 0,
                ),
            ).use { server ->
                server.start()
                assertEquals(token, server.pairingToken)
            }
        } finally {
            dataDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun loadsLibraryRootsFromHeadlessSettings() {
        val temp = createTempDirectory("danmaku-headless-settings-roots")
        val dataDirectory = temp.resolve("data").createDirectories()
        val root = temp.resolve("Anime").createDirectories()
        root.resolve("Example Show").createDirectories()
            .resolve("Episode 01.mp4")
            .writeBytes(byteArrayOf(1, 2, 3, 4))
        dataDirectory.resolve("server-settings.json").writeText(
            """
            {
              "schemaVersion": 1,
              "pairingToken": "123456",
              "libraryRoots": [${Json.encodeToString(root.toString())}]
            }
            """.trimIndent(),
        )

        try {
            headlessServer(
                HeadlessServerOptions(
                    dataDirectory = dataDirectory,
                    port = 0,
                ),
            ).use { server ->
                server.start()
                assertEquals("123456", server.pairingToken)
                val item = fetchCatalog(server).items.single()
                assertEquals("Example Show", item.seriesTitle)
                assertEquals("Episode 01", item.episodeTitle)
            }
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    @Test
    fun loadsProviderSettingsFromHeadlessSettingsWithoutPersistingSecrets() {
        val dataDirectory = createTempDirectory("danmaku-headless-provider-settings")
        val settingsFile = dataDirectory.resolve("server-settings.json")
        settingsFile.writeText(
            """
            {
              "schemaVersion": 1,
              "pairingToken": "123456",
              "dandanplay": {
                "baseUrl": "https://worker.example/dandanplay",
                "appId": "app-id",
                "appSecret": "raw-secret",
                "hasAppSecret": true,
                "authenticationMode": "credential",
                "cacheMaxAgeDays": 7
              },
              "externalAnime": {
                "myAnimeListClientId": "mal-client-id",
                "myAnimeListClientSecret": "raw-mal-secret",
                "hasMyAnimeListClientSecret": true,
                "hasMyAnimeListAccessToken": true,
                "bangumiBaseUrl": "https://api.bgm.tv/",
                "bangumiUserAgent": "Danmaku QA",
                "bangumiAccessToken": "raw-bangumi-token",
                "hasBangumiAccessToken": true
              }
            }
            """.trimIndent(),
        )

        try {
            val settings = HeadlessServerSettingsStore(settingsFile).loadOrCreate(explicitPairingToken = null)

            assertEquals("123456", settings.pairingToken)
            assertEquals("https://worker.example/dandanplay", settings.dandanplay.baseUrl)
            assertEquals("app-id", settings.dandanplay.appId)
            assertEquals(true, settings.dandanplay.hasAppSecret)
            assertEquals(HeadlessDandanplayAuthenticationMode.CREDENTIAL, settings.dandanplay.authenticationMode)
            assertEquals(7, settings.dandanplay.cacheMaxAgeDays)
            assertEquals("mal-client-id", settings.externalAnime.myAnimeListClientId)
            assertEquals(true, settings.externalAnime.hasMyAnimeListClientSecret)
            assertEquals(true, settings.externalAnime.hasMyAnimeListAccessToken)
            assertEquals("https://api.bgm.tv/", settings.externalAnime.bangumiBaseUrl)
            assertEquals("Danmaku QA", settings.externalAnime.bangumiUserAgent)
            assertEquals(true, settings.externalAnime.hasBangumiAccessToken)

            val rewritten = settingsFile.toFile().readText()
            assertEquals(false, rewritten.contains("raw-secret"))
            assertEquals(false, rewritten.contains("raw-mal-secret"))
            assertEquals(false, rewritten.contains("raw-bangumi-token"))

            headlessServer(
                HeadlessServerOptions(
                    dataDirectory = dataDirectory,
                    port = 0,
                ),
            ).use { server ->
                server.start()
                val status = Json.decodeFromString<LanLibraryServerStatus>(
                    connection("${server.runtimeStatus.baseUrls.first()}/api/server/status")
                        .inputStream
                        .bufferedReader()
                        .use { it.readText() },
                )

                assertEquals(settings.dandanplay.baseUrl, status.providerSettings?.dandanplay?.baseUrl)
                assertEquals(settings.dandanplay.appId, status.providerSettings?.dandanplay?.appId)
                assertEquals(settings.dandanplay.hasAppSecret, status.providerSettings?.dandanplay?.hasAppSecret)
                assertEquals(settings.dandanplay.authenticationMode.name, status.providerSettings?.dandanplay?.authenticationMode)
                assertEquals(settings.dandanplay.cacheMaxAgeDays, status.providerSettings?.dandanplay?.cacheMaxAgeDays)
                assertEquals(settings.externalAnime.myAnimeListClientId, status.providerSettings?.externalAnime?.myAnimeListClientId)
                assertEquals(
                    settings.externalAnime.hasMyAnimeListAccessToken,
                    status.providerSettings?.externalAnime?.hasMyAnimeListAccessToken,
                )
                assertEquals(settings.externalAnime.bangumiBaseUrl, status.providerSettings?.externalAnime?.bangumiBaseUrl)
                assertEquals(settings.externalAnime.bangumiUserAgent, status.providerSettings?.externalAnime?.bangumiUserAgent)
                assertEquals(settings.externalAnime.hasBangumiAccessToken, status.providerSettings?.externalAnime?.hasBangumiAccessToken)
            }
        } finally {
            dataDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun startsWithDataDirectoryLockAndHeadlessStatus() {
        val dataDirectory = createTempDirectory("danmaku-headless-server")
        headlessServer(
            HeadlessServerOptions(
                dataDirectory = dataDirectory,
                port = 0,
                pairingToken = "123456",
            ),
        ).use { server ->
            server.start()
            assertEquals(LibraryHostMode.HEADLESS_SERVER, server.runtimeStatus.mode)
            assertEquals(0, server.runtimeStatus.itemCount)
        }
        dataDirectory.toFile().deleteRecursively()
    }

    @Test
    fun publishesConfiguredRootsAndStreamsMedia() {
        val temp = createTempDirectory("danmaku-headless-streaming")
        val dataDirectory = temp.resolve("data")
        val root = temp.resolve("Anime").createDirectories()
        val show = root.resolve("Example Show").createDirectories()
        val mediaBytes = byteArrayOf(1, 2, 3, 4)
        show.resolve("Episode 01.mp4").writeBytes(mediaBytes)
        show.resolve("Episode 01.en.vtt").writeText("WEBVTT\n\n00:00:00.000 --> 00:00:01.000\nHello\n")

        headlessServer(
            HeadlessServerOptions(
                dataDirectory = dataDirectory,
                libraryRoots = listOf(root),
                port = 0,
                pairingToken = "123456",
            ),
        ).use { server ->
            val start = server.start()
            assertEquals(LibraryHostOperationStatus.SUCCEEDED, start.status)
            assertEquals(1, server.runtimeStatus.itemCount)

            val catalog = Json.decodeFromString<LibraryCatalog>(
                connection("${server.runtimeStatus.baseUrls.first()}/api/library?token=123456")
                    .inputStream
                    .bufferedReader()
                    .use { it.readText() },
            )
            val item = catalog.items.single()
            assertEquals("Example Show", item.seriesTitle)
            assertEquals("Episode 01", item.episodeTitle)
            assertEquals("video/mp4", item.mediaType)
            assertEquals(1, item.subtitles.size)

            val mediaResponse = connection("${server.runtimeStatus.baseUrls.first()}${item.streamPath}?token=123456")
            assertEquals(200, mediaResponse.responseCode)
            assertContentEquals(mediaBytes, mediaResponse.inputStream.use { it.readBytes() })

            val subtitle = item.subtitles.single()
            val subtitleResponse = connection("${server.runtimeStatus.baseUrls.first()}${subtitle.streamPath}?token=123456")
            assertEquals(200, subtitleResponse.responseCode)
            assertEquals("text/vtt", subtitleResponse.getHeaderField("Content-Type"))
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun persistsProgressAcrossHeadlessRestarts() {
        val temp = createTempDirectory("danmaku-headless-progress")
        val dataDirectory = temp.resolve("data")
        val root = temp.resolve("Anime").createDirectories()
        root.resolve("Example Show").createDirectories()
            .resolve("Episode 01.mp4")
            .writeBytes(byteArrayOf(1, 2, 3, 4))
        lateinit var progress: PlaybackProgress

        try {
            headlessServer(
                HeadlessServerOptions(
                    dataDirectory = dataDirectory,
                    libraryRoots = listOf(root),
                    port = 0,
                    pairingToken = "123456",
                ),
            ).use { server ->
                server.start()
                val catalog = fetchCatalog(server)
                val item = catalog.items.single()
                progress = PlaybackProgress(
                    mediaId = item.id,
                    positionMs = 30_000,
                    durationMs = 90_000,
                    updatedAtEpochMs = 123,
                )

                assertEquals(
                    204,
                    connection(
                        url = "${server.runtimeStatus.baseUrls.first()}/api/progress/${item.id}?token=123456",
                        method = "PUT",
                        body = Json.encodeToString(progress),
                    ).responseCode,
                )
            }

            headlessServer(
                HeadlessServerOptions(
                    dataDirectory = dataDirectory,
                    libraryRoots = listOf(root),
                    port = 0,
                    pairingToken = "123456",
                ),
            ).use { server ->
                server.start()
                val catalog = fetchCatalog(server)
                val item = catalog.items.single()

                assertEquals(
                    progress,
                    Json.decodeFromString<PlaybackProgress>(
                        connection("${server.runtimeStatus.baseUrls.first()}/api/progress/${item.id}?token=123456")
                            .inputStream
                            .bufferedReader()
                            .use { it.readText() },
                    ),
                )
            }
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    @Test
    fun persistsCatalogAcrossHeadlessRestartsWithoutConfiguredRoots() {
        val temp = createTempDirectory("danmaku-headless-catalog")
        val dataDirectory = temp.resolve("data")
        val root = temp.resolve("Anime").createDirectories()
        val show = root.resolve("Example Show").createDirectories()
        val mediaBytes = byteArrayOf(10, 20, 30, 40)
        show.resolve("Episode 01.mp4").writeBytes(mediaBytes)
        show.resolve("Episode 01.en.vtt").writeText("WEBVTT\n\n00:00:00.000 --> 00:00:01.000\nCached\n")

        try {
            headlessServer(
                HeadlessServerOptions(
                    dataDirectory = dataDirectory,
                    libraryRoots = listOf(root),
                    port = 0,
                    pairingToken = "123456",
                ),
            ).use { server ->
                val start = server.start()
                assertEquals(LibraryHostOperationStatus.SUCCEEDED, start.status)
                assertEquals(1, fetchCatalog(server).items.size)
                assertEquals(true, dataDirectory.resolve("catalog.json").toFile().isFile)
            }

            headlessServer(
                HeadlessServerOptions(
                    dataDirectory = dataDirectory,
                    port = 0,
                    pairingToken = "123456",
                ),
            ).use { server ->
                val start = server.start()
                assertEquals(LibraryHostOperationStatus.SUCCEEDED, start.status)
                assertEquals(1, start.itemCount)
                assertEquals(1, server.runtimeStatus.itemCount)

                val catalog = fetchCatalog(server)
                val item = catalog.items.single()
                assertEquals("Example Show", item.seriesTitle)
                assertEquals("Episode 01", item.episodeTitle)
                assertEquals(1, item.subtitles.size)

                val mediaResponse = connection("${server.runtimeStatus.baseUrls.first()}${item.streamPath}?token=123456")
                assertEquals(200, mediaResponse.responseCode)
                assertContentEquals(mediaBytes, mediaResponse.inputStream.use { it.readBytes() })
            }
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    @Test
    fun startsAndClosesDiscoveryAnnouncements() {
        val dataDirectory = createTempDirectory("danmaku-headless-discovery")
        val announcedPorts = mutableListOf<Int>()
        val announcer = RecordingCloseable()

        try {
            HeadlessLibraryServer(
                options = HeadlessServerOptions(
                    dataDirectory = dataDirectory,
                    port = 0,
                    pairingToken = "123456",
                ),
                discoveryAnnouncerFactory = { port ->
                    announcedPorts += port
                    announcer
                },
            ).use { server ->
                server.start()

                assertEquals(listOf(server.runtimeStatus.baseUrls.first().substringAfterLast(":").toInt()), announcedPorts)
            }

            assertEquals(true, announcer.closed)
        } finally {
            dataDirectory.toFile().deleteRecursively()
        }
    }

    private fun fetchCatalog(server: HeadlessLibraryServer): LibraryCatalog =
        Json.decodeFromString(
            connection("${server.runtimeStatus.baseUrls.first()}/api/library?token=123456")
                .inputStream
                .bufferedReader()
                .use { it.readText() },
        )

    private fun connection(
        url: String,
        method: String = "GET",
        body: String? = null,
    ): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            body?.let {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.bufferedWriter().use { writer -> writer.write(it) }
            }
        }

    private fun headlessServer(options: HeadlessServerOptions): HeadlessLibraryServer =
        HeadlessLibraryServer(
            options = options,
            discoveryAnnouncerFactory = { NoOpCloseable },
        )

    private data object NoOpCloseable : AutoCloseable {
        override fun close() = Unit
    }

    private class RecordingCloseable : AutoCloseable {
        var closed: Boolean = false
            private set

        override fun close() {
            closed = true
        }
    }
}
