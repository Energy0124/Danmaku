package app.danmaku.player.android

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Media3StreamingIntegrationTest {
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

    private class FixtureHttpServer(
        private val fixture: ByteArray,
    ) : AutoCloseable {
        private val serverSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()

        val url: String =
            "http://127.0.0.1:${serverSocket.localPort}/short-stream.mp4"

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
            val request = socket.getInputStream()
                .bufferedReader()
                .readLinesUntilBlank()
            val method = request.firstOrNull()?.substringBefore(' ') ?: return
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
