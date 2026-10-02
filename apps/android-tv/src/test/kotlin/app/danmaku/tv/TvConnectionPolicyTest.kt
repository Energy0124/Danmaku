package app.danmaku.tv

import app.danmaku.library.lanLibraryConnectionProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvConnectionPolicyTest {
    private val first = "http://192.168.1.10:8686"
    private val second = "http://192.168.1.11:8686"

    @Test
    fun firstLaunchConnectsOnlyWhenDiscoveryIsUnambiguous() {
        assertEquals(first, automaticTvConnection(listOf(first, "$first/"), emptyList()))
        assertNull(automaticTvConnection(listOf(first, second), emptyList()))
        assertNull(automaticTvConnection(emptyList(), emptyList()))
    }

    @Test
    fun reconnectPrefersTheMostRecentlyUsedDiscoveredPc() {
        val saved = listOf(lanLibraryConnectionProfile(first), lanLibraryConnectionProfile(second))
        assertEquals(first, automaticTvConnection(listOf(second, first), saved))
        assertEquals(second, automaticTvConnection(listOf(second), saved))
        assertNull(automaticTvConnection(listOf("http://unknown:8686"), saved))
    }

    @Test
    fun manualEntryAcceptsBareNamesAndAddressesWithoutChangingExplicitUrls() {
        assertEquals(first, normalizeTvServerAddress(" 192.168.1.10 "))
        assertEquals("http://my-pc:8686", normalizeTvServerAddress("my-pc"))
        assertEquals("http://my-pc:9000", normalizeTvServerAddress("my-pc:9000/"))
        assertEquals("https://my-pc", normalizeTvServerAddress("https://my-pc/"))
        assertEquals("http://[::1]:8686", normalizeTvServerAddress("[::1]"))
    }

    @Test
    fun manualEntryRejectsInvalidAddressesAndCredentials() {
        listOf("", "bad address", "ftp://pc", "http://pc:99999", "http://user:secret@pc", "pc?token=secret", "pc#fragment").forEach {
            assertEquals(it, true, runCatching { normalizeTvServerAddress(it) }.isFailure)
        }
    }
}
