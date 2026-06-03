package app.danmaku.player.android

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSource
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
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = instrumentation.context.assets
            .open("short-stream.mp4")
            .use { it.readBytes() }
        val ended = CountDownLatch(1)
        val playerError = AtomicReference<PlaybackException?>()
        val durationMs = AtomicLong()

        FixtureHttpServer(fixture).use { server ->
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
                assertTrue("Media3 did not finish the fixture", ended.await(15, TimeUnit.SECONDS))
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
    ) : AutoCloseable {
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
            val range = request
                .firstOrNull { it.startsWith("Range:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
                ?.parseRange(fixture.size)
            val start = range?.first ?: 0
            val endInclusive = range?.last ?: fixture.lastIndex
            val contentLength = endInclusive - start + 1
            val output = socket.getOutputStream()

            output.bufferedWriter().apply {
                write("HTTP/1.1 ${if (range == null) "200 OK" else "206 Partial Content"}\r\n")
                write("Content-Type: video/mp4\r\n")
                write("Accept-Ranges: bytes\r\n")
                write("Content-Length: $contentLength\r\n")
                range?.let {
                    write("Content-Range: bytes $start-$endInclusive/${fixture.size}\r\n")
                }
                write("Connection: close\r\n")
                write("\r\n")
                flush()
            }
            if (method != "HEAD") {
                output.write(fixture, start, contentLength)
                output.flush()
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
