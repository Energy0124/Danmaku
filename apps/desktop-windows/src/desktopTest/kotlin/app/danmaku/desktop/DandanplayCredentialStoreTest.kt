package app.danmaku.desktop

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
            assertEquals("app-id-value", connection.appId)
            assertEquals(appSecret, connection.appSecret)
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
            assertEquals(appSecret, credentialStore.loadConnection().appSecret)
            assertEquals(DandanplayAuthenticationMode.SIGNED, credentialStore.loadConnection().authenticationMode)
        }

        DesktopLibraryCatalogStore(databasePath).use { catalogStore ->
            val credentialStore = DandanplayCredentialStore(
                store = catalogStore,
                secretProtector = ReversingSecretProtector,
            )

            assertEquals(appSecret, credentialStore.loadConnection().appSecret)
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
            assertEquals("local-secret", credentialStore.loadConnection().appSecret)
            assertFalse(catalogStore.loadSettings().any { it.value.contains("local-secret") })

            credentialStore.saveSettings(
                baseUrl = "http://127.0.0.1:9000",
                appId = "",
                appSecret = "",
                authenticationMode = DandanplayAuthenticationMode.SIGNED,
            )

            assertNull(credentialStore.loadSettings().appId)
            assertNull(credentialStore.loadConnection().appSecret)
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
        assertEquals(DandanplayAuthenticationMode.CREDENTIAL, defaults?.authenticationMode)
        assertEquals(9, defaults?.cacheMaxAgeDays)
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
            assertNull(credentialStore.loadConnection().appId)
            assertNull(credentialStore.loadConnection().appSecret)
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
