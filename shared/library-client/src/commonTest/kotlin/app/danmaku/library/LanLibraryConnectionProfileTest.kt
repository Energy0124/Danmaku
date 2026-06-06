package app.danmaku.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LanLibraryConnectionProfileTest {
    @Test
    fun derivesStableProfileIdentityFromNormalizedBaseUrl() {
        val profile = lanLibraryConnectionProfile(
            baseUrl = " http://192.168.1.12:8686/ ",
            pairingToken = "123456",
        )

        assertEquals("http://192.168.1.12:8686", profile.id)
        assertEquals("192.168.1.12:8686", profile.displayName)
        assertEquals("http://192.168.1.12:8686", profile.baseUrl)
        assertEquals("http://192.168.1.12:8686", profile.normalizedBaseUrl)
        assertEquals("123456", profile.pairingToken)
    }

    @Test
    fun acceptsCustomDisplayNameAndLastConnectedTime() {
        val profile = lanLibraryConnectionProfile(
            baseUrl = "http://pc.local:8686",
            pairingToken = "",
            displayName = "Living Room PC",
            lastConnectedAtEpochMs = 42,
        )

        assertEquals("Living Room PC", profile.displayName)
        assertEquals(42, profile.lastConnectedAtEpochMs)
    }

    @Test
    fun rejectsBlankBaseUrlAndNegativeConnectionTime() {
        assertFailsWith<IllegalArgumentException> {
            lanLibraryConnectionProfile(baseUrl = " ", pairingToken = "123456")
        }
        assertFailsWith<IllegalArgumentException> {
            lanLibraryConnectionProfile(
                baseUrl = "http://pc.local:8686",
                pairingToken = "123456",
                lastConnectedAtEpochMs = -1,
            )
        }
    }
}
