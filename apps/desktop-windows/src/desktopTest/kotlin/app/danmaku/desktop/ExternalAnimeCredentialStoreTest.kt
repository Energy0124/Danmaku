package app.danmaku.desktop

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExternalAnimeCredentialStoreTest {
    @Test
    fun savesAndLoadsExternalProviderSettings() {
        val temp = createTempDirectory("danmaku-external-anime-credentials")
        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { catalogStore ->
            val store = ExternalAnimeCredentialStore(
                store = catalogStore,
                secretProtector = ReversingSecretProtector,
                nowEpochMs = { 123 },
            )

            val settings = store.saveSettings(
                myAnimeListClientId = " mal-client ",
                myAnimeListAccessToken = " mal-token ",
                bangumiBaseUrl = "https://api.bgm.tv",
                bangumiUserAgent = " DanmakuTest/1.0 ",
                bangumiAccessToken = " bangumi-token ",
            )

            assertEquals("mal-client", settings.myAnimeListClientId)
            assertTrue(settings.hasMyAnimeListAccessToken)
            assertEquals("https://api.bgm.tv/", settings.bangumiBaseUrl)
            assertEquals("DanmakuTest/1.0", settings.bangumiUserAgent)
            assertTrue(settings.hasBangumiAccessToken)
            assertEquals("mal-token", store.loadMyAnimeListAccessToken())
            assertEquals("bangumi-token", store.loadBangumiAccessToken())
            assertNotNull(store.loadMyAnimeListSearchConnection())
        }
    }

    @Test
    fun keepsExistingTokensWhenTokenFieldsAreBlank() {
        val temp = createTempDirectory("danmaku-external-anime-credentials")
        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { catalogStore ->
            val store = ExternalAnimeCredentialStore(
                store = catalogStore,
                secretProtector = ReversingSecretProtector,
            )
            store.saveSettings(
                myAnimeListClientId = "mal-client",
                myAnimeListAccessToken = "old-mal-token",
                bangumiBaseUrl = DEFAULT_BANGUMI_BASE_URL,
                bangumiUserAgent = DEFAULT_BANGUMI_USER_AGENT,
                bangumiAccessToken = "old-bangumi-token",
            )

            val settings = store.saveSettings(
                myAnimeListClientId = "mal-client",
                myAnimeListAccessToken = "",
                bangumiBaseUrl = DEFAULT_BANGUMI_BASE_URL,
                bangumiUserAgent = "DanmakuTest/2.0",
                bangumiAccessToken = "",
            )

            assertTrue(settings.hasMyAnimeListAccessToken)
            assertTrue(settings.hasBangumiAccessToken)
            assertEquals("old-mal-token", store.loadMyAnimeListAccessToken())
            assertEquals("old-bangumi-token", store.loadBangumiAccessToken())
            assertEquals("DanmakuTest/2.0", settings.bangumiUserAgent)
        }
    }

    @Test
    fun clearingProviderSettingsRemovesStoredSecrets() {
        val temp = createTempDirectory("danmaku-external-anime-credentials")
        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { catalogStore ->
            val store = ExternalAnimeCredentialStore(
                store = catalogStore,
                secretProtector = ReversingSecretProtector,
            )
            store.saveSettings(
                myAnimeListClientId = "mal-client",
                myAnimeListAccessToken = "mal-token",
                bangumiBaseUrl = DEFAULT_BANGUMI_BASE_URL,
                bangumiUserAgent = DEFAULT_BANGUMI_USER_AGENT,
                bangumiAccessToken = "bangumi-token",
            )

            val malSettings = store.clearMyAnimeListSettings()
            assertNull(malSettings.myAnimeListClientId)
            assertFalse(malSettings.hasMyAnimeListAccessToken)
            assertNull(store.loadMyAnimeListAccessToken())

            val bangumiSettings = store.clearBangumiSettings()
            assertEquals(DEFAULT_BANGUMI_BASE_URL, bangumiSettings.bangumiBaseUrl)
            assertFalse(bangumiSettings.hasBangumiAccessToken)
            assertNull(store.loadBangumiAccessToken())
        }
    }

    private object ReversingSecretProtector : DesktopSecretProtector {
        override fun protect(value: ByteArray): ByteArray =
            value.reversedArray()

        override fun unprotect(value: ByteArray): ByteArray =
            value.reversedArray()
    }
}
