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
            )

            assertEquals("http://127.0.0.1:8732", settings.baseUrl)
            assertEquals("app-id-value", settings.appId)
            assertTrue(settings.hasCredentials)
            assertEquals(DandanplayAuthenticationMode.CREDENTIAL, settings.authenticationMode)
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
            )
            assertEquals("http://127.0.0.1:8733", updatedSettings.baseUrl)
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
        }

        temp.toFile().deleteRecursively()
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
