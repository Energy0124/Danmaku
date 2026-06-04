package app.danmaku.desktop

import com.sun.jna.platform.win32.Crypt32Util
import java.security.SecureRandom
import java.util.Base64

class AniRssCredentialStore(
    private val store: DesktopLibraryCatalogStore,
    private val secretProtector: DesktopSecretProtector = WindowsDpapiSecretProtector(),
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val generateWebhookToken: () -> String = ::secureWebhookToken,
) {
    fun loadConnection(): AniRssConnection? {
        val baseUrl = store.loadSetting(BASE_URL_SETTING_KEY)?.value ?: return null
        val apiKey = loadSecret(API_KEY_SETTING_KEY) ?: return null
        return AniRssConnection(baseUrl, apiKey)
    }

    fun saveConnection(
        baseUrl: String,
        apiKey: String,
    ) {
        val connection = AniRssConnection(baseUrl, apiKey)
        saveSetting(
            key = BASE_URL_SETTING_KEY,
            value = connection.baseUri.toString().trimEnd('/'),
        )
        saveSecret(API_KEY_SETTING_KEY, connection.apiKey)
    }

    fun deleteConnection() {
        store.deleteSetting(BASE_URL_SETTING_KEY)
        store.deleteSetting(API_KEY_SETTING_KEY)
    }

    fun loadOrCreateWebhookToken(): String =
        loadSecret(WEBHOOK_TOKEN_SETTING_KEY)
            ?: generateWebhookToken().also {
                saveSecret(WEBHOOK_TOKEN_SETTING_KEY, it)
            }

    fun rotateWebhookToken(): String =
        generateWebhookToken().also {
            saveSecret(WEBHOOK_TOKEN_SETTING_KEY, it)
        }

    private fun loadSecret(key: String): String? =
        store.loadSetting(key)
            ?.value
            ?.let(Base64.getDecoder()::decode)
            ?.let(secretProtector::unprotect)
            ?.toString(Charsets.UTF_8)

    private fun saveSecret(
        key: String,
        value: String,
    ) {
        val protected = secretProtector.protect(value.toByteArray(Charsets.UTF_8))
        saveSetting(key, Base64.getEncoder().encodeToString(protected))
    }

    private fun saveSetting(
        key: String,
        value: String,
    ) {
        store.saveSetting(
            DesktopAppSetting(
                key = key,
                value = value,
                updatedAtEpochMs = nowEpochMs(),
            ),
        )
    }

    private companion object {
        const val BASE_URL_SETTING_KEY = "ani_rss.base_url"
        const val API_KEY_SETTING_KEY = "ani_rss.api_key.dpapi"
        const val WEBHOOK_TOKEN_SETTING_KEY = "ani_rss.webhook_token.dpapi"

        fun secureWebhookToken(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}

interface DesktopSecretProtector {
    fun protect(value: ByteArray): ByteArray

    fun unprotect(value: ByteArray): ByteArray
}

class WindowsDpapiSecretProtector : DesktopSecretProtector {
    override fun protect(value: ByteArray): ByteArray =
        Crypt32Util.cryptProtectData(value)

    override fun unprotect(value: ByteArray): ByteArray =
        Crypt32Util.cryptUnprotectData(value)
}
