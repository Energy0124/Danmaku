package app.danmaku.server

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
import app.danmaku.domain.LanLibraryServerAnnouncement
import app.danmaku.domain.PlaybackProgress
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class LanProtocolFixtureRecorderTest {
    @Test
    fun recordsCoreLanProtocolFixtures() {
        val temp = createTempDirectory("danmaku-lan-protocol")
        try {
            val mediaBytes = byteArrayOf(0, 1, 2, 3, 4, 5)
            val mediaFile = createTempFile("danmaku-media-fixture", ".bin")
            val subtitleFile = createTempFile("danmaku-subtitle-fixture", ".srt")
            val posterFile = createTempFile("danmaku-poster-fixture", ".jpg")
            val webRoot = temp.resolve("web").createDirectories()
            val webAssets = webRoot.resolve("assets").createDirectories()

            mediaFile.writeBytes(mediaBytes)
            subtitleFile.writeText("1\n00:00:00,000 --> 00:00:01,000\nHello\n")
            posterFile.writeBytes(byteArrayOf(1, 35, 69, 103))
            webRoot.resolve("index.html").writeText("<!doctype html><title>Danmaku</title>")
            webAssets.resolve("app.js").writeText("window.__danmakuFixture = true;\n")

            val subtitle = LibrarySubtitleTrack(
                id = "subtitle-id",
                label = "English",
                relativePath = "Example Show/Episode 01.en.srt",
                mediaType = "application/x-subrip",
                streamPath = "/subtitles/subtitle-id",
            )
            val item = LibraryMediaItem(
                id = "episode-id",
                seriesTitle = "Example Show",
                episodeTitle = "Episode 01",
                relativePath = "Example Show/Episode 01.bin",
                sizeBytes = mediaBytes.size.toLong(),
                mediaType = "application/octet-stream",
                streamPath = "/media/episode-id",
                indexedAtEpochMs = 1_700_000_000_000,
                subtitles = listOf(subtitle),
                posterPath = "/posters/episode-id",
            )
            val posterPath = item.posterPath!!
            val catalog = LibraryCatalog(
                rootName = "Fixture Library",
                indexedAtEpochMs = 1_700_000_000_000,
                items = listOf(item),
            )
            val hook = AuthenticatedPostHook(
                path = "/api/hooks/fixture",
                token = "0123456789abcdef",
                onAccepted = {},
            )
            val progress = PlaybackProgress(
                mediaId = item.id,
                positionMs = 12_345,
                durationMs = 98_765,
                updatedAtEpochMs = 1_700_000_100_000,
            )

            LocalLibraryServer(
                port = 0,
                pairingToken = "654321",
                authenticatedPostHooks = listOf(hook),
                webAssets = StaticWebAssets(webRoot),
            ).use { server ->
                server.publish(
                    PublishedLibrary(
                        catalog = catalog,
                        filesById = mapOf(item.id to mediaFile),
                        subtitleFilesById = mapOf(subtitle.id to subtitleFile),
                        posterFilesById = mapOf(item.id to posterFile),
                    ),
                )
                server.start()
                val baseUrl = server.baseUrl()

                writeFixture(
                    "server-status.json",
                    recordHttpFixture("server-status", baseUrl, "GET", "/api/server/status", BodyFormat.JSON),
                )
                writeFixture(
                    "catalog.json",
                    recordHttpFixture("catalog", baseUrl, "GET", "/api/library", BodyFormat.JSON),
                )
                writeFixture(
                    "pairing-token-auth-not-enforced.json",
                    recordHttpFixture(
                        name = "pairing-token-auth-not-enforced",
                        baseUrl = baseUrl,
                        method = "GET",
                        path = "/api/library",
                        responseBodyFormat = BodyFormat.JSON,
                        headers = mapOf("X-Danmaku-Pairing-Token" to "wrong-token"),
                        notes = listOf(
                            "Current core routes do not inspect pairing-token headers.",
                            "This request succeeds with the same catalog body as an unauthenticated request.",
                        ),
                    ),
                )
                writeFixture(
                    "media-full.json",
                    recordHttpFixture("media-full", baseUrl, "GET", item.streamPath, BodyFormat.BINARY),
                )
                writeFixture(
                    "media-partial-range.json",
                    recordHttpFixture(
                        name = "media-partial-range",
                        baseUrl = baseUrl,
                        method = "GET",
                        path = item.streamPath,
                        responseBodyFormat = BodyFormat.BINARY,
                        range = "bytes=1-3",
                    ),
                )
                writeFixture(
                    "media-invalid-range.json",
                    recordHttpFixture(
                        name = "media-invalid-range",
                        baseUrl = baseUrl,
                        method = "GET",
                        path = item.streamPath,
                        responseBodyFormat = BodyFormat.NONE,
                        range = "bytes=9-10",
                    ),
                )
                writeFixture(
                    "subtitle-get.json",
                    recordHttpFixture("subtitle-get", baseUrl, "GET", subtitle.streamPath, BodyFormat.TEXT),
                )
                writeFixture(
                    "subtitle-head.json",
                    recordHttpFixture("subtitle-head", baseUrl, "HEAD", subtitle.streamPath, BodyFormat.NONE),
                )
                writeFixture(
                    "poster-get.json",
                    recordHttpFixture("poster-get", baseUrl, "GET", posterPath, BodyFormat.BINARY),
                )
                writeFixture(
                    "poster-head.json",
                    recordHttpFixture("poster-head", baseUrl, "HEAD", posterPath, BodyFormat.NONE),
                )
                writeFixture(
                    "progress-missing.json",
                    recordHttpFixture(
                        "progress-missing",
                        baseUrl,
                        "GET",
                        "/api/progress/${item.id}",
                        BodyFormat.NONE,
                    ),
                )
                writeFixture(
                    "progress-put.json",
                    recordHttpFixture(
                        name = "progress-put",
                        baseUrl = baseUrl,
                        method = "PUT",
                        path = "/api/progress/${item.id}",
                        responseBodyFormat = BodyFormat.NONE,
                        body = Json.encodeToString(progress),
                        requestBodyFormat = BodyFormat.JSON,
                    ),
                )
                writeFixture(
                    "progress-get.json",
                    recordHttpFixture(
                        "progress-get",
                        baseUrl,
                        "GET",
                        "/api/progress/${item.id}",
                        BodyFormat.JSON,
                    ),
                )
                writeFixture(
                    "progress-list.json",
                    recordHttpFixture("progress-list", baseUrl, "GET", "/api/progress", BodyFormat.JSON),
                )
                writeFixture(
                    "danmaku-unavailable.json",
                    recordHttpFixture(
                        "danmaku-unavailable",
                        baseUrl,
                        "GET",
                        "/api/danmaku/${item.id}",
                        BodyFormat.JSON,
                    ),
                )
                writeFixture(
                    "web-redirect.json",
                    recordHttpFixture(
                        name = "web-redirect",
                        baseUrl = baseUrl,
                        method = "GET",
                        path = "/web",
                        responseBodyFormat = BodyFormat.NONE,
                        followRedirects = false,
                    ),
                )
                writeFixture(
                    "web-index.json",
                    recordHttpFixture("web-index", baseUrl, "GET", "/web/", BodyFormat.TEXT),
                )
                writeFixture(
                    "web-asset.json",
                    recordHttpFixture("web-asset", baseUrl, "GET", "/web/assets/app.js", BodyFormat.TEXT),
                )
                writeFixture(
                    "webhook-auth-failure.json",
                    recordHttpFixture(
                        "webhook-auth-failure",
                        baseUrl,
                        "POST",
                        hook.path,
                        BodyFormat.NONE,
                    ),
                )
            }
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    @Test
    fun recordsDiscoveryAnnouncementPacketFixture() {
        val payload = Json.encodeToString(
            LanLibraryServerAnnouncement(port = LocalLibraryServer.DEFAULT_PORT),
        ).encodeToByteArray()
        writeFixture(
            "discovery-announcement.json",
            buildJsonObject {
                put("schemaVersion", FIXTURE_SCHEMA_VERSION)
                put("name", "discovery-announcement")
                put("description", "UDP discovery datagram payload encoded by LocalLibraryDiscoveryAnnouncer.")
                put("transport", "udp")
                put("destinationPort", LanLibraryServerAnnouncement.DEFAULT_DISCOVERY_PORT)
                put("encoding", "utf-8-json")
                put("byteLength", payload.size)
                put("hex", payload.toHex())
                put("text", payload.decodeToString())
                put("json", Json.parseToJsonElement(payload.decodeToString()))
                put("decoded", buildJsonObject {
                    put("protocol", LanLibraryServerAnnouncement.PROTOCOL)
                    put("version", LanLibraryServerAnnouncement.VERSION)
                    put("port", LocalLibraryServer.DEFAULT_PORT)
                })
                put("notes", buildJsonArray {
                    add("Default protocol and version fields are omitted from the emitted JSON bytes.")
                    add("Receivers fill protocol=danmaku-library and version=1 from domain defaults.")
                })
            },
        )
    }

    private fun recordHttpFixture(
        name: String,
        baseUrl: String,
        method: String,
        path: String,
        responseBodyFormat: BodyFormat,
        range: String? = null,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        requestBodyFormat: BodyFormat = BodyFormat.TEXT,
        followRedirects: Boolean = true,
        notes: List<String> = emptyList(),
    ): JsonObject {
        val requestHeaders = buildMap {
            range?.let { put("Range", it) }
            putAll(headers)
            if (body != null) {
                put("Content-Type", "application/json; charset=utf-8")
            }
        }
        val connection = (URI("$baseUrl$path").toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = followRedirects
            requestHeaders.forEach(::setRequestProperty)
            body?.let { requestBody ->
                doOutput = true
                outputStream.bufferedWriter().use { writer -> writer.write(requestBody) }
            }
        }
        val status = connection.responseCode
        val responseBody = connection.responseBodyBytes(status)
        val fixture = buildJsonObject {
            put("schemaVersion", FIXTURE_SCHEMA_VERSION)
            put("name", name)
            put("request", buildJsonObject {
                put("method", method)
                put("path", path)
                putHeaders(requestHeaders)
                body?.let { requestBody ->
                    put("body", bodyJson(requestBody.encodeToByteArray(), requestBodyFormat))
                }
            })
            put("response", buildJsonObject {
                put("status", status)
                putHeaders(connection.selectedResponseHeaders())
                put("body", bodyJson(responseBody, responseBodyFormat))
            })
            if (notes.isNotEmpty()) {
                put("notes", buildJsonArray {
                    notes.forEach(::add)
                })
            }
        }
        connection.disconnect()
        return fixture
    }

    private fun HttpURLConnection.responseBodyBytes(status: Int): ByteArray {
        val stream = runCatching {
            if (status >= HttpURLConnection.HTTP_BAD_REQUEST) errorStream else inputStream
        }.getOrNull()
        return stream?.use { it.readBytes() } ?: ByteArray(0)
    }

    private fun HttpURLConnection.selectedResponseHeaders(): Map<String, String> =
        RESPONSE_HEADER_NAMES
            .mapNotNull { name -> getHeaderField(name)?.let { value -> name to value } }
            .toMap()

    private fun bodyJson(
        bytes: ByteArray,
        format: BodyFormat,
    ): JsonObject =
        buildJsonObject {
            put("byteLength", bytes.size)
            when {
                bytes.isEmpty() -> Unit
                format == BodyFormat.JSON -> {
                    val text = bytes.decodeToString()
                    put("text", text)
                    put("json", Json.parseToJsonElement(text))
                }
                format == BodyFormat.TEXT -> put("text", bytes.decodeToString())
                format == BodyFormat.BINARY -> {
                    put("base64", Base64.getEncoder().encodeToString(bytes))
                    put("hex", bytes.toHex())
                }
                format == BodyFormat.NONE -> Unit
            }
        }

    private fun JsonObjectBuilder.putHeaders(headers: Map<String, String>) {
        put(
            "headers",
            buildJsonObject {
                headers.toSortedMap().forEach { (name, value) -> put(name, value) }
            },
        )
    }

    private fun writeFixture(fileName: String, body: JsonObject) {
        Files.createDirectories(fixtureDirectory)
        val rendered = fixtureJson.encodeToString(JsonObject.serializer(), body).trimEnd() + "\n"
        Files.writeString(fixtureDirectory.resolve(fileName), rendered)
        assertEquals(rendered, Files.readString(fixtureDirectory.resolve(fileName)))
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte) }

    private val repositoryRoot: Path =
        generateSequence(Path.of("").toAbsolutePath().normalize()) { path -> path.parent }
            .first { path -> Files.isRegularFile(path.resolve("settings.gradle.kts")) }

    private val fixtureDirectory: Path =
        repositoryRoot.resolve(
            Path.of(
                "shared",
                "library-server-core",
                "src",
                "jvmTest",
                "resources",
                "lan-protocol-fixtures",
            ),
        )

    private enum class BodyFormat {
        JSON,
        TEXT,
        BINARY,
        NONE,
    }

    private companion object {
        const val FIXTURE_SCHEMA_VERSION = 1

        val RESPONSE_HEADER_NAMES = listOf(
            "Accept-Ranges",
            "Cache-Control",
            "Content-Length",
            "Content-Range",
            "Content-Type",
            "Location",
        )

        val fixtureJson = Json {
            prettyPrint = true
            encodeDefaults = true
        }
    }
}
