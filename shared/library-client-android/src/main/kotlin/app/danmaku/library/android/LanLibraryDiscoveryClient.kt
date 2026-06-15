package app.danmaku.library.android

import app.danmaku.domain.LanLibraryServerAnnouncement
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

data class DiscoveredLanLibraryServer(
    val baseUrl: String,
)

class LanLibraryDiscoveryException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class LanLibraryDiscoveryClient(
    private val json: Json = Json {
        ignoreUnknownKeys = true
    },
) {
    fun discover(timeoutMs: Int = DEFAULT_TIMEOUT_MS): List<DiscoveredLanLibraryServer> {
        require(timeoutMs > 0) { "timeoutMs must be positive" }

        val discovered = linkedSetOf<DiscoveredLanLibraryServer>()
        val deadline = System.currentTimeMillis() + timeoutMs
        DatagramSocket(null).use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(LanLibraryServerAnnouncement.DEFAULT_DISCOVERY_PORT))
            while (System.currentTimeMillis() < deadline) {
                socket.soTimeout = (deadline - System.currentTimeMillis())
                    .coerceAtLeast(1)
                    .toInt()
                val packet = DatagramPacket(ByteArray(MAX_PACKET_BYTES), MAX_PACKET_BYTES)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    break
                }
                decode(packet)?.let(discovered::add)
            }
        }
        return discovered.toList()
    }

    private fun decode(packet: DatagramPacket): DiscoveredLanLibraryServer? =
        runCatching {
            val announcement = json.decodeFromString<LanLibraryServerAnnouncement>(
                packet.data.decodeToString(0, packet.length),
            )
            DiscoveredLanLibraryServer(
                baseUrl = "http://${packet.address.hostAddress}:${announcement.port}",
            )
        }.getOrNull()

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 3_000
        private const val MAX_PACKET_BYTES = 1_024
    }
}
