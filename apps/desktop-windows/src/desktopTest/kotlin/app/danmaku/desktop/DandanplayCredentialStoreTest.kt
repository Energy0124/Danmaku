package app.danmaku.desktop

import app.danmaku.provider.dandanplay.DandanplayAuthenticationMode
import app.danmaku.provider.dandanplay.DandanplayConnection
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DandanplayCredentialStoreTest {
    @Test
    fun storesDandanplayCredentialsAsProtectedSettings() {
        val temp = createTempDirectory("danmaku-dandanplay-credentials")
        val databasePath = temp.resolve("catalog.db")
        val appSecret = "app-secret-value"

        DesktopLibraryCatalogStore(databasePath).use { catalogStore ->
            val credentialStore = DandanplayCredentialStore(
                store = catalogStore,
                secretProtector = ReversingSecretProtector,
                nowEpochMs = { 456 },
                localDefaultsProvider = { null },
            )

            val settings = credentialStore.saveSettings(
                baseUrl = "http://127.0.0.1:8732/",
                appId = "app-id-value",
                appSecret = appSecret,
                authenticationMode = DandanplayAuthenticationMode.CREDENTIAL,
                cacheMaxAgeDays = 14,
            )

            assertEquals("http://127.0.0.1:8732", settings.baseUrl)
            assertEquals("app-id-value", settings.appId)
            assertTrue(settings.hasCredentials)
            assertEquals(DandanplayAuthenticationMode.CREDENTIAL, settings.authenticationMode)
            assertEquals(14, settings.cacheMaxAgeDays)
            assertFalse(settings.statusText.contains(appSecret))

            val connection = credentialStore.loadConnection()
            assertEquals("http://127.0.0.1:8732/", connection.baseUri.toString())
            assertTrue(connection.hasCredentials)
            assertTrue(connection.toString().contains("app-id-value"))
            assertFalse(connection.toString().contains(appSecret))
            assertEquals(DandanplayAuthenticationMode.CREDENTIAL, connection.authenticationMode)

            val storedSettings = catalogStore.loadSettings()
            assertFalse(storedSettings.any { it.value.contains(appSecret) })

            val updatedSettings = credentialStore.saveSettings(
                baseUrl = "http://127.0.0.1:8733/",
                appId = "app-id-value",
                appSecret = "",
                authenticationMode = DandanplayAuthenticationMode.SIGNED,
                cacheMaxAgeDays = 7,
            )
            assertEquals("http://127.0.0.1:8733", updatedSettings.baseUrl)
            assertEquals(7, updatedSettings.cacheMaxAgeDays)
            assertTrue(credentialStore.loadConnection().hasCredentials)
            assertEquals(DandanplayAuthenticationMode.SIGNED, credentialStore.loadConnection().authenticationMode)
        }

        DesktopLibraryCatalogStore(databasePath).use { catalogStore ->
            val credentialStore = DandanplayCredentialStore(
                store = catalogStore,
                secretProtector = ReversingSecretProtector,
                localDefaultsProvider = { null },
            )

            assertTrue(credentialStore.loadConnection().hasCredentials)
            credentialStore.deleteSettings()

            val resetSettings = credentialStore.loadSettings()
            assertEquals(DandanplayConnection.DEFAULT_BASE_URL, resetSettings.baseUrl)
            assertNull(resetSettings.appId)
            assertFalse(resetSettings.hasCredentials)
            assertFalse(resetSettings.isFetchEnabled)
            assertEquals(30, resetSettings.cacheMaxAgeDays)
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun loadsIgnoredLocalDefaultsWhenNoSettingsAreStored() {
        val temp = createTempDirectory("danmaku-dandanplay-local-defaults")
        val databasePath = temp.resolve("catalog.db")

        DesktopLibraryCatalogStore(databasePath).use { catalogStore ->
            val credentialStore = DandanplayCredentialStore(
                store = catalogStore,
                secretProtector = ReversingSecretProtector,
                localDefaultsProvider = {
                    DandanplayLocalCredentialDefaults(
                        appId = "local-app-id",
                        appSecret = "local-secret",
                        authenticationMode = DandanplayAuthenticationMode.CREDENTIAL,
                        cacheMaxAgeDays = 21,
                    )
                },
            )

            val settings = credentialStore.loadSettings()

            assertEquals("local-app-id", settings.appId)
            assertTrue(settings.hasCredentials)
            assertEquals(DandanplayAuthenticationMode.CREDENTIAL, settings.authenticationMode)
            assertEquals(21, settings.cacheMaxAgeDays)
            assertTrue(credentialStore.loadConnection().hasCredentials)
            assertFalse(catalogStore.loadSettings().any { it.value.contains("local-secret") })

            credentialStore.saveSettings(
                baseUrl = "http://127.0.0.1:9000",
                appId = "",
                appSecret = "",
                authenticationMode = DandanplayAuthenticationMode.SIGNED,
            )

            assertNull(credentialStore.loadSettings().appId)
            assertFalse(credentialStore.loadConnection().hasCredentials)
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun loadsDandanplayDefaultsFromEnvironmentAndLocalProperties() {
        val propertiesPath = createTempDirectory("danmaku-dandanplay-properties").resolve("local.properties")
        propertiesPath.toFile().writeText(
            """
            danmaku.dandanplay.appId=local-file-app-id
            danmaku.dandanplay.appSecret=local-file-secret
            danmaku.dandanplay.proxyBaseUrl=https://proxy.example.test
            danmaku.dandanplay.authenticationMode=credential
            danmaku.dandanplay.cacheMaxAgeDays=9
            """.trimIndent(),
        )

        val defaults = DandanplayLocalCredentialDefaults.load(
            environment = mapOf("DANMAKU_DANDANPLAY_APP_ID" to "environment-app-id"),
            propertiesPath = propertiesPath,
        )

        assertEquals("environment-app-id", defaults?.appId)
        assertEquals("local-file-secret", defaults?.appSecret)
        assertEquals(DandanplayConnection.DEFAULT_BASE_URL, defaults?.baseUrl)
        assertEquals("https://proxy.example.test", defaults?.proxyBaseUrl)
        assertEquals(DandanplayAuthenticationMode.CREDENTIAL, defaults?.authenticationMode)
        assertEquals(9, defaults?.cacheMaxAgeDays)
    }

    @Test
    fun loadsLocalDefaultsFromStableUserConfigPath() {
        val appData = createTempDirectory("danmaku-dandanplay-appdata")
        val propertiesPath = appData.resolve("Danmaku").resolve("local.properties")
        propertiesPath.parent.toFile().mkdirs()
        propertiesPath.toFile().writeText(
            """
            danmaku.dandanplay.appId=user-config-app-id
            danmaku.dandanplay.appSecret=user-config-secret
            danmaku.dandanplay.authenticationMode=signed
            """.trimIndent(),
        )

        val defaults = DandanplayLocalCredentialDefaults.load(
            environment = mapOf("LOCALAPPDATA" to appData.toString()),
        )

        assertEquals("user-config-app-id", defaults?.appId)
        assertEquals("user-config-secret", defaults?.appSecret)
        assertEquals(DandanplayAuthenticationMode.SIGNED, defaults?.authenticationMode)
    }

    @Test
    fun usesProxyDefaultsOnlyWhenDirectSecretIsAbsent() {
        val propertiesPath = createTempDirectory("danmaku-dandanplay-proxy").resolve("local.properties")
        propertiesPath.toFile().writeText(
            """
            danmaku.dandanplay.proxyBaseUrl=https://proxy.example.test
            danmaku.dandanplay.appId=local-file-app-id
            """.trimIndent(),
        )

        val proxyDefaults = DandanplayLocalCredentialDefaults.load(
            environment = emptyMap(),
            propertiesPath = propertiesPath,
        )

        assertEquals("https://proxy.example.test", proxyDefaults?.baseUrl)
        assertEquals("https://proxy.example.test", proxyDefaults?.proxyBaseUrl)
        assertEquals("local-file-app-id", proxyDefaults?.appId)
        assertFalse(proxyDefaults?.appSecret != null)

        val directDefaults = DandanplayLocalCredentialDefaults.load(
            environment = mapOf("DANMAKU_DANDANPLAY_APP_SECRET" to "environment-secret"),
            propertiesPath = propertiesPath,
        )

        assertEquals(DandanplayConnection.DEFAULT_BASE_URL, directDefaults?.baseUrl)
        assertEquals("https://proxy.example.test", directDefaults?.proxyBaseUrl)
        assertEquals("local-file-app-id", directDefaults?.appId)
        assertEquals("environment-secret", directDefaults?.appSecret)
    }

    @Test
    fun supportsCompatibleServerSettingsWithoutCredentials() {
        val temp = createTempDirectory("danmaku-dandanplay-compatible")
        val databasePath = temp.resolve("catalog.db")

        DesktopLibraryCatalogStore(databasePath).use { catalogStore ->
            val credentialStore = DandanplayCredentialStore(
                store = catalogStore,
                secretProtector = ReversingSecretProtector,
            )

            val settings = credentialStore.saveSettings(
                baseUrl = "http://127.0.0.1:9000",
                appId = "",
                appSecret = "",
                authenticationMode = DandanplayAuthenticationMode.SIGNED,
            )

            assertEquals("http://127.0.0.1:9000", settings.baseUrl)
            assertFalse(settings.hasCredentials)
            assertTrue(settings.isFetchEnabled)
            assertEquals("http://127.0.0.1:9000/", credentialStore.loadConnection().baseUri.toString())
            assertFalse(credentialStore.loadConnection().hasCredentials)
        }

        temp.toFile().deleteRecursively()
    }

    private object ReversingSecretProtector : DesktopSecretProtector {
        override fun protect(value: ByteArray): ByteArray =
            byteArrayOf(1, 2, 3) + value.reversedArray()

        override fun unprotect(value: ByteArray): ByteArray =
            value.drop(3).toByteArray().reversedArray()
    }
}
