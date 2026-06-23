package app.danmaku.server.windows

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeListEntry
import app.danmaku.domain.ExternalAnimeListStatus
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeTrackingUpdate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class HeadlessExternalAnimeTrackingTest {
    @Test
    fun exposesExternalListEntryReadAndWriteEndpointWithoutPairingToken() {
        val dataDirectory = createTempDirectory("danmaku-headless-external-tracking")
        val capturedReads = mutableListOf<ExternalAnimeId>()
        val capturedWrites = mutableListOf<ExternalAnimeTrackingUpdate>()
        val service = object : HeadlessExternalAnimeTrackingService {
            override fun fetchListEntry(animeId: ExternalAnimeId): ExternalAnimeListEntry? {
                capturedReads += animeId
                return ExternalAnimeListEntry(
                    animeId = animeId,
                    status = ExternalAnimeListStatus.WATCHING,
                    watchedEpisodes = 4,
                    score = 8,
                    updatedAtEpochMs = 1_704_164_645_000,
                )
            }

            override fun updateListEntry(update: ExternalAnimeTrackingUpdate): ExternalAnimeListEntry {
                capturedWrites += update
                return ExternalAnimeListEntry(
                    animeId = update.animeId,
                    status = update.status,
                    watchedEpisodes = update.watchedEpisodes,
                    score = update.score,
                    updatedAtEpochMs = 1_704_164_645_000,
                )
            }
        }

        try {
            HeadlessLibraryServer(
                options = HeadlessServerOptions(
                    dataDirectory = dataDirectory,
                    port = 0,
                    pairingToken = "123456",
                ),
                discoveryAnnouncerFactory = { NoOpCloseable },
                externalAnimeTrackingServiceFactory = { service },
            ).use { server ->
                server.start()
                val baseUrl = server.runtimeStatus.baseUrls.first()
                val route = "$baseUrl/api/providers/list/entry"

                assertEquals(400, connection("$route?animeId=52991").responseCode)
                assertEquals(400, connection("$route?provider=unknown&animeId=52991").responseCode)
                assertEquals(400, connection("$route?provider=dandanplay&animeId=333").responseCode)
                assertEquals(405, connection("$route", method = "PUT").responseCode)

                val entry = Json.decodeFromString<ExternalAnimeListEntry>(
                    connection("$route?provider=mal&animeId=52991")
                        .inputStream
                        .bufferedReader()
                        .use { it.readText() },
                )

                assertEquals(listOf(ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 52991)), capturedReads)
                assertEquals(ExternalAnimeListStatus.WATCHING, entry.status)
                assertEquals(4, entry.watchedEpisodes)
                assertEquals(8, entry.score)
                assertEquals(1_704_164_645_000, entry.updatedAtEpochMs)

                val update = ExternalAnimeTrackingUpdate(
                    animeId = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 400602),
                    status = ExternalAnimeListStatus.COMPLETED,
                    watchedEpisodes = 28,
                    score = 9,
                )
                val written = Json.decodeFromString<ExternalAnimeListEntry>(
                    connection(
                        url = "$route",
                        method = "POST",
                        body = Json.encodeToString(update),
                    )
                        .inputStream
                        .bufferedReader()
                        .use { it.readText() },
                )

                assertEquals(listOf(update), capturedWrites)
                assertEquals(ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 400602), written.animeId)
                assertEquals(ExternalAnimeListStatus.COMPLETED, written.status)
                assertEquals(28, written.watchedEpisodes)
                assertEquals(9, written.score)
                assertEquals(400, connection("$route", method = "POST", body = "{}").responseCode)
            }
        } finally {
            dataDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun listEntryEndpointReportsMissingProviderCredentials() {
        val dataDirectory = createTempDirectory("danmaku-headless-external-tracking-missing")
        try {
            HeadlessLibraryServer(
                options = HeadlessServerOptions(
                    dataDirectory = dataDirectory,
                    port = 0,
                    pairingToken = "123456",
                ),
                discoveryAnnouncerFactory = { NoOpCloseable },
            ).use { server ->
                server.start()
                val baseUrl = server.runtimeStatus.baseUrls.first()

                assertEquals(
                    409,
                    connection("$baseUrl/api/providers/list/entry?provider=mal&animeId=52991")
                        .responseCode,
                )
            }
        } finally {
            dataDirectory.toFile().deleteRecursively()
        }
    }

    private fun connection(
        url: String,
        method: String = "GET",
        body: String? = null,
    ): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.bufferedWriter().use { writer -> writer.write(body) }
            }
        }

    private data object NoOpCloseable : AutoCloseable {
        override fun close() = Unit
    }
}
