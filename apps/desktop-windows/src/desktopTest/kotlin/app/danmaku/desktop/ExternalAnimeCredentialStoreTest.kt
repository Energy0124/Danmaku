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
                myAnimeListClientSecret = " mal-secret ",
                myAnimeListAccessToken = " mal-token ",
                bangumiBaseUrl = "https://api.bgm.tv",
                bangumiUserAgent = " DanmakuTest/1.0 ",
                bangumiAccessToken = " bangumi-token ",
            )

            assertEquals("mal-client", settings.myAnimeListClientId)
            assertTrue(settings.hasMyAnimeListClientSecret)
            assertTrue(settings.hasMyAnimeListAccessToken)
            assertEquals("https://api.bgm.tv/", settings.bangumiBaseUrl)
            assertEquals("DanmakuTest/1.0", settings.bangumiUserAgent)
            assertTrue(settings.hasBangumiAccessToken)
            assertEquals("mal-secret", store.loadMyAnimeListClientSecret())
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
                myAnimeListClientSecret = "old-mal-secret",
                myAnimeListAccessToken = "old-mal-token",
                bangumiBaseUrl = DEFAULT_BANGUMI_BASE_URL,
                bangumiUserAgent = DEFAULT_BANGUMI_USER_AGENT,
                bangumiAccessToken = "old-bangumi-token",
            )

            val settings = store.saveSettings(
                myAnimeListClientId = "mal-client",
                myAnimeListClientSecret = "",
                myAnimeListAccessToken = "",
                bangumiBaseUrl = DEFAULT_BANGUMI_BASE_URL,
                bangumiUserAgent = "DanmakuTest/2.0",
                bangumiAccessToken = "",
            )

            assertTrue(settings.hasMyAnimeListClientSecret)
            assertTrue(settings.hasMyAnimeListAccessToken)
            assertTrue(settings.hasBangumiAccessToken)
            assertEquals("old-mal-secret", store.loadMyAnimeListClientSecret())
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
                myAnimeListClientSecret = "mal-secret",
                myAnimeListAccessToken = "mal-token",
                bangumiBaseUrl = DEFAULT_BANGUMI_BASE_URL,
                bangumiUserAgent = DEFAULT_BANGUMI_USER_AGENT,
                bangumiAccessToken = "bangumi-token",
            )

            val malSettings = store.clearMyAnimeListSettings()
            assertNull(malSettings.myAnimeListClientId)
            assertFalse(malSettings.hasMyAnimeListClientSecret)
            assertFalse(malSettings.hasMyAnimeListAccessToken)
            assertNull(store.loadMyAnimeListClientSecret())
            assertNull(store.loadMyAnimeListAccessToken())

            val bangumiSettings = store.clearBangumiSettings()
            assertEquals(DEFAULT_BANGUMI_BASE_URL, bangumiSettings.bangumiBaseUrl)
            assertFalse(bangumiSettings.hasBangumiAccessToken)
            assertNull(store.loadBangumiAccessToken())
        }
    }

    @Test
    fun savesAndLoadsMyAnimeListOAuthTokens() {
        val temp = createTempDirectory("danmaku-external-anime-oauth-credentials")
        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { catalogStore ->
            val store = ExternalAnimeCredentialStore(
                store = catalogStore,
                secretProtector = ReversingSecretProtector,
                nowEpochMs = { 1_000 },
            )
            store.saveSettings(
                myAnimeListClientId = "mal-client",
                myAnimeListClientSecret = null,
                myAnimeListAccessToken = null,
                bangumiBaseUrl = DEFAULT_BANGUMI_BASE_URL,
                bangumiUserAgent = DEFAULT_BANGUMI_USER_AGENT,
                bangumiAccessToken = null,
            )

            val settings = store.saveMyAnimeListOAuthTokens(
                accessToken = "oauth-access",
                refreshToken = "oauth-refresh",
                expiresInSeconds = 3_600,
            )
            val tokens = store.loadMyAnimeListOAuthTokens()

            assertTrue(settings.hasMyAnimeListAccessToken)
            assertNotNull(tokens)
            assertEquals("oauth-access", tokens.accessToken)
            assertEquals("oauth-refresh", tokens.refreshToken)
            assertEquals(3_601_000, tokens.expiresAtEpochMs)
        }
    }

    private object ReversingSecretProtector : DesktopSecretProtector {
        override fun protect(value: ByteArray): ByteArray =
            value.reversedArray()

        override fun unprotect(value: ByteArray): ByteArray =
            value.reversedArray()
    }
}
