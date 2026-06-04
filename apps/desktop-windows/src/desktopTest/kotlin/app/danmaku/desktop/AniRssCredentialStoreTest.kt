package app.danmaku.desktop

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AniRssCredentialStoreTest {
    @Test
    fun storesApiKeyAndWebhookTokenAsProtectedSettings() {
        val temp = createTempDirectory("danmaku-ani-rss-credentials")
        val databasePath = temp.resolve("catalog.db")
        val apiKey = "api-key-secret"
        val webhookToken = "0123456789abcdef"

        DesktopLibraryCatalogStore(databasePath).use { catalogStore ->
            val credentialStore = AniRssCredentialStore(
                store = catalogStore,
                secretProtector = ReversingSecretProtector,
                nowEpochMs = { 123 },
                generateWebhookToken = { webhookToken },
            )

            credentialStore.saveConnection("http://127.0.0.1:7789/", apiKey)
            assertEquals("http://127.0.0.1:7789/", credentialStore.loadConnection()?.baseUri.toString())
            assertEquals(apiKey, credentialStore.loadConnection()?.apiKey)
            assertEquals(webhookToken, credentialStore.loadOrCreateWebhookToken())

            val settings = catalogStore.loadSettings()
            assertFalse(settings.any { it.value.contains(apiKey) })
            assertFalse(settings.any { it.value.contains(webhookToken) })
        }

        DesktopLibraryCatalogStore(databasePath).use { catalogStore ->
            val credentialStore = AniRssCredentialStore(
                store = catalogStore,
                secretProtector = ReversingSecretProtector,
                generateWebhookToken = { "different-token-value" },
            )

            assertEquals(apiKey, credentialStore.loadConnection()?.apiKey)
            assertEquals(webhookToken, credentialStore.loadOrCreateWebhookToken())
            credentialStore.deleteConnection()
            assertNull(credentialStore.loadConnection())
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun roundTripsSecretsWithWindowsDpapi() {
        val protector = WindowsDpapiSecretProtector()
        val plaintext = "danmaku-dpapi-round-trip".toByteArray()

        val protected = protector.protect(plaintext)

        assertFalse(protected.contentEquals(plaintext))
        assertEquals(
            plaintext.toList(),
            protector.unprotect(protected).toList(),
        )
    }

    private object ReversingSecretProtector : DesktopSecretProtector {
        override fun protect(value: ByteArray): ByteArray =
            byteArrayOf(1, 2, 3) + value.reversedArray()

        override fun unprotect(value: ByteArray): ByteArray =
            value.drop(3).toByteArray().reversedArray()
    }
}
