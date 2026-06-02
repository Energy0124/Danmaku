package app.danmaku.desktop

import app.danmaku.domain.LanLibraryServerAnnouncement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LocalLibraryDiscoveryAnnouncer(
    serverPort: Int,
    private val discoveryPort: Int = LanLibraryServerAnnouncement.DEFAULT_DISCOVERY_PORT,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val targetAddresses: () -> List<InetAddress> = ::broadcastAddresses,
) : AutoCloseable {
    private val socket = DatagramSocket().apply {
        broadcast = true
    }
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val payload = Json.encodeToString(
        LanLibraryServerAnnouncement(port = serverPort),
    ).toByteArray()

    fun start() {
        executor.scheduleAtFixedRate(
            ::announce,
            0,
            intervalMs,
            TimeUnit.MILLISECONDS,
        )
    }

    override fun close() {
        executor.shutdownNow()
        socket.close()
    }

    private fun announce() {
        targetAddresses().forEach { address ->
            runCatching {
                socket.send(
                    DatagramPacket(
                        payload,
                        payload.size,
                        address,
                        discoveryPort,
                    ),
                )
            }
        }
    }

    companion object {
        private const val DEFAULT_INTERVAL_MS = 1_500L

        private fun broadcastAddresses(): List<InetAddress> =
            buildList {
                add(InetAddress.getByName("255.255.255.255"))
                NetworkInterface.getNetworkInterfaces()
                    .toList()
                    .filter(NetworkInterface::isUp)
                    .filterNot(NetworkInterface::isLoopback)
                    .flatMap { it.interfaceAddresses }
                    .mapNotNullTo(this) { it.broadcast }
            }.distinct()
    }
}
