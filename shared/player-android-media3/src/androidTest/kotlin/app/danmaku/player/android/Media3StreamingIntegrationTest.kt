package app.danmaku.player.android

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.PlaybackTrackKind
import app.danmaku.library.LanPlaybackPreparation
import app.danmaku.library.LanPlaybackTarget
import app.danmaku.library.LanSubtitlePreparation
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Media3StreamingIntegrationTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun playsShortHttpFixtureToCompletion() {
        playHttpFixtureToCompletion { fixture ->
            FixtureHttpServer(fixture)
        }
    }

    @Test
    fun attachesPreparedLanSubtitlesToMediaItem() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val subtitleTrack = LibrarySubtitleTrack(
            id = "subtitle-id",
            label = "English",
            relativePath = "Example Show/Episode 01.en.srt",
            mediaType = "text/x-ass",
            streamPath = "/subtitles/subtitle-id",
        )
        val preparation = LanPlaybackPreparation(
            item = LibraryMediaItem(
                id = "episode-id",
                seriesTitle = "Example Show",
                episodeTitle = "Episode 01",
                relativePath = "Example Show/Episode 01.mp4",
                sizeBytes = 123,
                mediaType = "video/mp4",
                streamPath = "/media/episode-id",
                subtitles = listOf(subtitleTrack),
            ),
            target = LanPlaybackTarget(
                baseUrl = "http://127.0.0.1:8686",
                pairingToken = "123456",
                mediaId = "episode-id",
            ),
            source = PlaybackSource.RemoteStream(
                "http://127.0.0.1:8686/media/episode-id?token=123456",
            ),
            subtitles = listOf(
                LanSubtitlePreparation(
                    track = subtitleTrack,
                    source = PlaybackSource.RemoteStream(
                        "http://127.0.0.1:8686/subtitles/subtitle-id?token=123456",
                    ),
                ),
            ),
            resumePositionMs = null,
        )

        instrumentation.runOnMainSync {
            val player = ExoPlayer.Builder(instrumentation.targetContext).build()
            try {
                Media3PlaybackController(player).load(preparation)

                val mediaItem = checkNotNull(player.currentMediaItem)
                val subtitle = checkNotNull(mediaItem.localConfiguration)
                    .subtitleConfigurations
                    .single()
                assertEquals(subtitleTrack.id, subtitle.id)
                assertEquals(subtitleTrack.label, subtitle.label)
                assertEquals(MimeTypes.TEXT_SSA, subtitle.mimeType)
                assertEquals(preparation.subtitles.single().source.url, subtitle.uri.toString())
            } finally {
                player.release()
            }
        }
    }

    @Test
    fun discoversSelectsAndDisablesPreparedLanSubtitles() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = instrumentation.context.assets
            .open("short-stream.mp4")
            .use { it.readBytes() }
        val subtitleFixture = "1\n00:00:00,000 --> 00:00:01,000\nHello\n".toByteArray()
        val tracksKnown = CountDownLatch(1)
        val playerError = AtomicReference<PlaybackException?>()

        FixtureHttpServer(fixture, subtitleFixture = subtitleFixture).use { server ->
            val subtitleTrack = LibrarySubtitleTrack(
                id = "subtitle-id",
                label = "English",
                relativePath = "Example Show/Episode 01.en.srt",
                mediaType = "application/x-subrip",
                streamPath = "/subtitles/subtitle-id",
            )
            val preparation = LanPlaybackPreparation(
                item = LibraryMediaItem(
                    id = "episode-id",
                    seriesTitle = "Example Show",
                    episodeTitle = "Episode 01",
                    relativePath = "Example Show/Episode 01.mp4",
                    sizeBytes = fixture.size.toLong(),
                    mediaType = "video/mp4",
                    streamPath = "/media/episode-id",
                    subtitles = listOf(subtitleTrack),
                ),
                target = LanPlaybackTarget(server.url, "123456", "episode-id"),
                source = PlaybackSource.RemoteStream(server.url),
                resumePositionMs = null,
                subtitles = listOf(
                    LanSubtitlePreparation(
                        track = subtitleTrack,
                        source = PlaybackSource.RemoteStream(
                            server.subtitleUrl(subtitleTrack.id, "123456"),
                        ),
                    ),
                ),
            )

            lateinit var player: ExoPlayer
            lateinit var controller: Media3PlaybackController
            instrumentation.runOnMainSync {
                player = ExoPlayer.Builder(instrumentation.targetContext).build().apply {
                    addListener(
                        object : Player.Listener {
                            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                                if (tracks.containsType(C.TRACK_TYPE_TEXT)) {
                                    tracksKnown.countDown()
                                }
                            }

                            override fun onPlayerError(error: PlaybackException) {
                                playerError.set(error)
                                tracksKnown.countDown()
                            }
                        },
                    )
                }
                controller = Media3PlaybackController(player)
                controller.load(preparation)
            }

            try {
                assertTrue("Media3 did not discover the sidecar subtitle", tracksKnown.await(10, TimeUnit.SECONDS))
                assertNull(playerError.get()?.message, playerError.get())
                instrumentation.runOnMainSync {
                    val track = controller.snapshot().tracks
                        .single { it.kind == PlaybackTrackKind.SUBTITLE }
                    assertEquals("English", track.label)
                    assertTrue(track.supported)

                    controller.dispatch(PlaybackCommand.SelectSubtitleTrack(track.id))
                    assertFalse(
                        player.trackSelectionParameters.disabledTrackTypes
                            .contains(C.TRACK_TYPE_TEXT),
                    )
                    assertTrue(player.trackSelectionParameters.overrides.isNotEmpty())

                    controller.dispatch(PlaybackCommand.SelectSubtitleTrack(null))
                    assertTrue(
                        player.trackSelectionParameters.disabledTrackTypes
                            .contains(C.TRACK_TYPE_TEXT),
                    )
                }
            } finally {
                instrumentation.runOnMainSync {
                    player.release()
                }
            }
        }
    }

    @Test
    fun playsSlowHttpFixtureToCompletion() {
        playHttpFixtureToCompletion(timeoutSeconds = 20) { fixture ->
            FixtureHttpServer(
                fixture = fixture,
                chunkSize = 256,
                chunkDelayMillis = 25,
            )
        }
    }

    private fun playHttpFixtureToCompletion(
        timeoutSeconds: Long = 15,
        serverFactory: (ByteArray) -> FixtureHttpServer,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = instrumentation.context.assets
            .open("short-stream.mp4")
            .use { it.readBytes() }
        val ended = CountDownLatch(1)
        val playerError = AtomicReference<PlaybackException?>()
        val durationMs = AtomicLong()

        serverFactory(fixture).use { server ->
            lateinit var player: ExoPlayer
            instrumentation.runOnMainSync {
                player = ExoPlayer.Builder(instrumentation.targetContext).build().apply {
                    addListener(
                        object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_ENDED) {
                                    durationMs.set(duration.coerceAtLeast(0))
                                    ended.countDown()
                                }
                            }

                            override fun onPlayerError(error: PlaybackException) {
                                playerError.set(error)
                                ended.countDown()
                            }
                        },
                    )
                    setMediaItem(MediaItem.fromUri(server.url))
                    prepare()
                    play()
                }
            }

            try {
                assertTrue(
                    "Media3 did not finish the fixture",
                    ended.await(timeoutSeconds, TimeUnit.SECONDS),
                )
                assertNull(playerError.get()?.message, playerError.get())
                instrumentation.runOnMainSync {
                    assertEquals(Player.STATE_ENDED, player.playbackState)
                }
                assertTrue("Expected a positive media duration", durationMs.get() > 0)
            } finally {
                instrumentation.runOnMainSync {
                    player.release()
                }
            }
        }
    }

    @Test
    fun serviceUploadsProgressAfterUiConnectionCloses() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = instrumentation.context.assets
            .open("short-stream.mp4")
            .use { it.readBytes() }
        val connected = CountDownLatch(1)
        val connectionError = AtomicReference<Throwable?>()
        val mediaId = "episode 01"
        val pairingToken = "123456"
        val mainHandler = Handler(Looper.getMainLooper())

        FixtureHttpServer(fixture, json).use { server ->
            Media3PlaybackServiceConnection(instrumentation.targetContext).use { connection ->
                connection.connect(
                    executor = { mainHandler.post(it) },
                    onConnected = { controller ->
                        controller.load(
                            PlaybackSource.RemoteStream(
                                server.streamUrl(mediaId, pairingToken),
                            ),
                        )
                        controller.dispatch(PlaybackCommand.SetPlaybackRate(0.1f))
                        controller.dispatch(PlaybackCommand.Play)
                        connected.countDown()
                    },
                    onFailure = {
                        connectionError.set(it)
                        connected.countDown()
                    },
                )

                assertTrue(
                    "Media3 playback service did not connect",
                    connected.await(10, TimeUnit.SECONDS),
                )
                assertNull(connectionError.get()?.message, connectionError.get())
            }

            val progress = server.awaitProgress(12, TimeUnit.SECONDS)
            assertEquals(mediaId, progress.mediaId)
            assertTrue("Expected a non-negative saved position", progress.positionMs >= 0)
            assertTrue("Expected a positive media duration", (progress.durationMs ?: 0) > 0)
            assertTrue("Expected a progress timestamp", progress.updatedAtEpochMs > 0)
        }
    }

    private class FixtureHttpServer(
        private val fixture: ByteArray,
        private val json: Json = Json,
        private val chunkSize: Int = fixture.size.coerceAtLeast(1),
        private val chunkDelayMillis: Long = 0,
        private val subtitleFixture: ByteArray? = null,
    ) : AutoCloseable {
        init {
            require(chunkSize > 0) { "chunkSize must be positive" }
            require(chunkDelayMillis >= 0) { "chunkDelayMillis must not be negative" }
        }

        private val serverSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
        private val progressLatch = CountDownLatch(1)
        private val progress = AtomicReference<PlaybackProgress?>()

        val url: String =
            "http://127.0.0.1:${serverSocket.localPort}/short-stream.mp4"

        fun streamUrl(
            mediaId: String,
            pairingToken: String,
        ): String =
            "http://127.0.0.1:${serverSocket.localPort}/media/${mediaId.encoded()}?token=${pairingToken.encoded()}"

        fun subtitleUrl(
            subtitleId: String,
            pairingToken: String,
        ): String =
            "http://127.0.0.1:${serverSocket.localPort}/subtitles/${subtitleId.encoded()}?token=${pairingToken.encoded()}"

        fun awaitProgress(
            timeout: Long,
            unit: TimeUnit,
        ): PlaybackProgress {
            assertTrue("Playback service did not upload progress", progressLatch.await(timeout, unit))
            return checkNotNull(progress.get()) { "Progress latch completed without a payload" }
        }

        init {
            executor.submit {
                while (!serverSocket.isClosed) {
                    runCatching {
                        serverSocket.accept().use(::serve)
                    }
                }
            }
        }

        override fun close() {
            serverSocket.close()
            executor.shutdownNow()
        }

        private fun serve(socket: Socket) {
            val reader = socket.getInputStream().bufferedReader()
            val request = reader.readLinesUntilBlank()
            val method = request.firstOrNull()?.substringBefore(' ') ?: return
            val path = request.firstOrNull()
                ?.split(' ', limit = 3)
                ?.getOrNull(1)
                ?.substringBefore('?')
                ?: return
            if (method == "PUT" && path.startsWith("/api/progress/")) {
                val contentLength = request
                    .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                    ?.substringAfter(':')
                    ?.trim()
                    ?.toIntOrNull()
                    ?: 0
                val body = reader.readChars(contentLength)
                progress.set(json.decodeFromString<PlaybackProgress>(body))
                progressLatch.countDown()
                socket.getOutputStream().bufferedWriter().apply {
                    write("HTTP/1.1 204 No Content\r\n")
                    write("Content-Length: 0\r\n")
                    write("Connection: close\r\n")
                    write("\r\n")
                    flush()
                }
                return
            }
            val responseBody = if (path.startsWith("/subtitles/")) {
                subtitleFixture ?: return
            } else {
                fixture
            }
            val contentType = if (path.startsWith("/subtitles/")) {
                "application/x-subrip"
            } else {
                "video/mp4"
            }
            val range = request
                .firstOrNull { it.startsWith("Range:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
                ?.parseRange(responseBody.size)
            val start = range?.first ?: 0
            val endInclusive = range?.last ?: responseBody.lastIndex
            val contentLength = endInclusive - start + 1
            val output = socket.getOutputStream()

            output.bufferedWriter().apply {
                write("HTTP/1.1 ${if (range == null) "200 OK" else "206 Partial Content"}\r\n")
                write("Content-Type: $contentType\r\n")
                write("Accept-Ranges: bytes\r\n")
                write("Content-Length: $contentLength\r\n")
                range?.let {
                    write("Content-Range: bytes $start-$endInclusive/${responseBody.size}\r\n")
                }
                write("Connection: close\r\n")
                write("\r\n")
                flush()
            }
            if (method != "HEAD") {
                output.writeFixture(responseBody, start, contentLength)
            }
        }

        private fun java.io.OutputStream.writeFixture(
            bytes: ByteArray,
            start: Int,
            contentLength: Int,
        ) {
            var offset = start
            var remaining = contentLength
            while (remaining > 0) {
                val count = minOf(chunkSize, remaining)
                write(bytes, offset, count)
                flush()
                offset += count
                remaining -= count
                if (chunkDelayMillis > 0 && remaining > 0) {
                    Thread.sleep(chunkDelayMillis)
                }
            }
        }
    }
}

private fun java.io.BufferedReader.readLinesUntilBlank(): List<String> =
    buildList {
        while (true) {
            val line = readLine() ?: break
            if (line.isBlank()) break
            add(line)
        }
    }

private fun java.io.BufferedReader.readChars(length: Int): String {
    val buffer = CharArray(length)
    var offset = 0
    while (offset < length) {
        val read = read(buffer, offset, length - offset)
        if (read < 0) break
        offset += read
    }
    return buffer.concatToString(endIndex = offset)
}

private fun String.parseRange(size: Int): IntRange? {
    if (!startsWith("bytes=")) return null
    val (startText, endText) = removePrefix("bytes=")
        .split('-', limit = 2)
        .takeIf { it.size == 2 }
        ?: return null
    val start = startText.toIntOrNull() ?: return null
    val endInclusive = if (endText.isBlank()) {
        size - 1
    } else {
        endText.toIntOrNull()?.coerceAtMost(size - 1) ?: return null
    }
    return (start..endInclusive).takeIf { start >= 0 && start <= endInclusive && start < size }
}

private fun String.encoded(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name())
