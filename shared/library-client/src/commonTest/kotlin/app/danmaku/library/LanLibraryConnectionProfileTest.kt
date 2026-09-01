package app.danmaku.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LanLibraryConnectionProfileTest {
    @Test
    fun derivesStableProfileIdentityFromNormalizedBaseUrl() {
        val profile = lanLibraryConnectionProfile(
            baseUrl = " http://192.168.1.12:8686/ ",
        )

        assertEquals("http://192.168.1.12:8686", profile.id)
        assertEquals("192.168.1.12:8686", profile.displayName)
        assertEquals("http://192.168.1.12:8686", profile.baseUrl)
        assertEquals("http://192.168.1.12:8686", profile.normalizedBaseUrl)
    }

    @Test
    fun acceptsCustomDisplayNameAndLastConnectedTime() {
        val profile = lanLibraryConnectionProfile(
            baseUrl = "http://pc.local:8686",
            displayName = "Living Room PC",
            lastConnectedAtEpochMs = 42,
        )

        assertEquals("Living Room PC", profile.displayName)
        assertEquals(42, profile.lastConnectedAtEpochMs)
    }

    @Test
    fun rejectsBlankBaseUrlAndNegativeConnectionTime() {
        assertFailsWith<IllegalArgumentException> {
            lanLibraryConnectionProfile(baseUrl = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            lanLibraryConnectionProfile(
                baseUrl = "http://pc.local:8686",
                lastConnectedAtEpochMs = -1,
            )
        }
    }
}
