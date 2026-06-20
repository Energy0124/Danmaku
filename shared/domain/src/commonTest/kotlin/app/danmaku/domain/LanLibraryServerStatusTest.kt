package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

class LanLibraryServerStatusTest {
    @Test
    fun validatesServerStatusFields() {
        assertFailsWith<IllegalArgumentException> {
            LanLibraryServerStatus(appName = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            LanLibraryServerStatus(apiVersion = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            LanLibraryServerStatus(webUiPath = "web")
        }
        assertFailsWith<IllegalArgumentException> {
            LanLibraryServerStatus(hostMode = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            LanDandanplayProviderStatus(appId = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            LanDandanplayProviderStatus(cacheMaxAgeDays = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            LanExternalAnimeProviderStatus(myAnimeListClientId = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            LanExternalAnimeProviderStatus(bangumiUserAgent = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            LanDandanplayRuntimeCapability(reasonCode = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            LanExternalAnimeRuntimeCapability(reasonCode = " ")
        }
    }

    @Test
    fun reportsCompatibilityErrorsForUnsupportedServers() {
        kotlin.test.assertEquals(
            null,
            LanLibraryServerStatus().compatibilityErrorMessage(),
        )
        kotlin.test.assertEquals(
            "This Windows library server requires a newer Danmaku app. " +
                "Server API 2 is newer than supported API 1.",
            LanLibraryServerStatus(apiVersion = 2).compatibilityErrorMessage(),
        )
        kotlin.test.assertEquals(
            "This Windows library server does not expose media streaming.",
            LanLibraryServerStatus(mediaStreaming = false).compatibilityErrorMessage(),
        )
        assertFailsWith<IllegalArgumentException> {
            LanLibraryServerStatus().compatibilityErrorMessage(supportedApiVersion = 0)
        }
    }
}
