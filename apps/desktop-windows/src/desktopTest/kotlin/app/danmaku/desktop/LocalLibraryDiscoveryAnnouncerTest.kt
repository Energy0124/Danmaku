package app.danmaku.desktop

import app.danmaku.domain.LanLibraryServerAnnouncement
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalLibraryDiscoveryAnnouncerTest {
    @Test
    fun broadcastsLibraryPort() {
        DatagramSocket(0, InetAddress.getLoopbackAddress()).use { receiver ->
            receiver.soTimeout = 1_000
            LocalLibraryDiscoveryAnnouncer(
                serverPort = 9_876,
                discoveryPort = receiver.localPort,
                intervalMs = 25,
                targetAddresses = { listOf(InetAddress.getLoopbackAddress()) },
            ).use { announcer ->
                announcer.start()
                val packet = DatagramPacket(ByteArray(1_024), 1_024)
                receiver.receive(packet)
                val announcement = Json.decodeFromString<LanLibraryServerAnnouncement>(
                    packet.data.decodeToString(0, packet.length),
                )

                assertEquals(LanLibraryServerAnnouncement.PROTOCOL, announcement.protocol)
                assertEquals(LanLibraryServerAnnouncement.VERSION, announcement.version)
                assertEquals(9_876, announcement.port)
            }
        }
    }
}
