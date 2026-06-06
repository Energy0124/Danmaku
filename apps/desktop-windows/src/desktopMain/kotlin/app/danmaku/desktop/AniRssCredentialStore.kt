package app.danmaku.desktop

import com.sun.jna.platform.win32.Crypt32Util
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class AniRssCredentialStore(
    private val store: DesktopLibraryCatalogStore,
    private val secretProtector: DesktopSecretProtector = DesktopSecretProtector.default(),
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

    companion object {
        fun default(platform: DesktopHostPlatform = DesktopHostPlatform.current()): DesktopSecretProtector =
            when (platform) {
                DesktopHostPlatform.WINDOWS -> WindowsDpapiSecretProtector()
                else -> LocalAesGcmSecretProtector.default()
            }
    }
}

class WindowsDpapiSecretProtector : DesktopSecretProtector {
    override fun protect(value: ByteArray): ByteArray =
        Crypt32Util.cryptProtectData(value)

    override fun unprotect(value: ByteArray): ByteArray =
        Crypt32Util.cryptUnprotectData(value)
}

class LocalAesGcmSecretProtector(
    private val keyPath: Path,
) : DesktopSecretProtector {
    override fun protect(value: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_BYTES)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(loadOrCreateKey(), AES_ALGORITHM),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        return iv + cipher.doFinal(value)
    }

    override fun unprotect(value: ByteArray): ByteArray {
        require(value.size > GCM_IV_BYTES) { "protected secret is too short" }
        val iv = value.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = value.copyOfRange(GCM_IV_BYTES, value.size)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(loadOrCreateKey(), AES_ALGORITHM),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        return cipher.doFinal(ciphertext)
    }

    private fun loadOrCreateKey(): ByteArray {
        if (Files.isRegularFile(keyPath)) {
            return Base64.getDecoder().decode(Files.readString(keyPath).trim())
        }
        Files.createDirectories(keyPath.parent)
        val key = ByteArray(AES_KEY_BYTES)
        SecureRandom().nextBytes(key)
        Files.writeString(keyPath, Base64.getEncoder().encodeToString(key))
        restrictOwnerAccess(keyPath)
        return key
    }

    companion object {
        fun default(): LocalAesGcmSecretProtector =
            LocalAesGcmSecretProtector(desktopAppDataDirectory().resolve("secret.key"))

        private const val AES_ALGORITHM = "AES"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_BYTES = 32
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}

private fun desktopAppDataDirectory(): Path {
    System.getenv("LOCALAPPDATA")
        ?.takeIf(String::isNotBlank)
        ?.let { return Path.of(it).resolve("Danmaku") }
    if (DesktopHostPlatform.current() == DesktopHostPlatform.MACOS) {
        return Path.of(System.getProperty("user.home"), "Library", "Application Support", "Danmaku")
    }
    return Path.of(System.getProperty("user.home"), ".danmaku")
}

private fun restrictOwnerAccess(path: Path) {
    runCatching {
        val permissions = setOf(
            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
        )
        Files.setPosixFilePermissions(path, permissions)
    }
}
