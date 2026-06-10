package app.danmaku.desktop

import java.net.URI
import java.util.Base64

class ExternalAnimeCredentialStore(
    private val store: DesktopLibraryCatalogStore,
    private val secretProtector: DesktopSecretProtector = DesktopSecretProtector.default(),
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    fun loadSettings(): ExternalAnimeProviderSettings =
        ExternalAnimeProviderSettings(
            myAnimeListClientId = store.loadSetting(MAL_CLIENT_ID_SETTING_KEY)?.value?.takeIf(String::isNotBlank),
            hasMyAnimeListAccessToken = loadSecret(MAL_ACCESS_TOKEN_SETTING_KEY) != null,
            bangumiBaseUrl = store.loadSetting(BANGUMI_BASE_URL_SETTING_KEY)?.value
                ?: DEFAULT_BANGUMI_BASE_URL,
            bangumiUserAgent = store.loadSetting(BANGUMI_USER_AGENT_SETTING_KEY)?.value
                ?: DEFAULT_BANGUMI_USER_AGENT,
            hasBangumiAccessToken = loadSecret(BANGUMI_ACCESS_TOKEN_SETTING_KEY) != null,
        )

    fun loadMyAnimeListSearchConnection(): MyAnimeListSearchConnection? =
        loadSettings()
            .myAnimeListClientId
            ?.let(::MyAnimeListSearchConnection)

    fun loadBangumiSearchClient(): BangumiAnimeSearchClient {
        val settings = loadSettings()
        return BangumiAnimeSearchClient(
            baseUri = URI(settings.bangumiBaseUrl),
            userAgent = settings.bangumiUserAgent,
        )
    }

    fun loadMyAnimeListAccessToken(): String? =
        loadSecret(MAL_ACCESS_TOKEN_SETTING_KEY)

    fun loadBangumiAccessToken(): String? =
        loadSecret(BANGUMI_ACCESS_TOKEN_SETTING_KEY)

    fun saveSettings(
        myAnimeListClientId: String?,
        myAnimeListAccessToken: String?,
        bangumiBaseUrl: String,
        bangumiUserAgent: String,
        bangumiAccessToken: String?,
    ): ExternalAnimeProviderSettings {
        val existing = loadSettings()
        val trimmedMalClientId = myAnimeListClientId?.trim()?.takeIf(String::isNotBlank)
        val trimmedMalToken = myAnimeListAccessToken?.trim()?.takeIf(String::isNotBlank)
        val trimmedBangumiToken = bangumiAccessToken?.trim()?.takeIf(String::isNotBlank)
        val normalizedBangumiBaseUrl = normalizeHttpsBaseUrl(bangumiBaseUrl, "Bangumi")
        val normalizedBangumiUserAgent = bangumiUserAgent.trim().takeIf(String::isNotBlank)
            ?: DEFAULT_BANGUMI_USER_AGENT

        if (trimmedMalClientId == null) {
            store.deleteSetting(MAL_CLIENT_ID_SETTING_KEY)
            store.deleteSetting(MAL_ACCESS_TOKEN_SETTING_KEY)
        } else {
            saveSetting(MAL_CLIENT_ID_SETTING_KEY, trimmedMalClientId)
            if (trimmedMalToken != null) {
                saveSecret(MAL_ACCESS_TOKEN_SETTING_KEY, trimmedMalToken)
            } else if (!existing.hasMyAnimeListAccessToken) {
                store.deleteSetting(MAL_ACCESS_TOKEN_SETTING_KEY)
            }
        }

        saveSetting(BANGUMI_BASE_URL_SETTING_KEY, normalizedBangumiBaseUrl)
        saveSetting(BANGUMI_USER_AGENT_SETTING_KEY, normalizedBangumiUserAgent)
        if (trimmedBangumiToken != null) {
            saveSecret(BANGUMI_ACCESS_TOKEN_SETTING_KEY, trimmedBangumiToken)
        } else if (!existing.hasBangumiAccessToken) {
            store.deleteSetting(BANGUMI_ACCESS_TOKEN_SETTING_KEY)
        }

        return loadSettings()
    }

    fun clearMyAnimeListSettings(): ExternalAnimeProviderSettings {
        store.deleteSetting(MAL_CLIENT_ID_SETTING_KEY)
        store.deleteSetting(MAL_ACCESS_TOKEN_SETTING_KEY)
        return loadSettings()
    }

    fun clearBangumiSettings(): ExternalAnimeProviderSettings {
        store.deleteSetting(BANGUMI_BASE_URL_SETTING_KEY)
        store.deleteSetting(BANGUMI_USER_AGENT_SETTING_KEY)
        store.deleteSetting(BANGUMI_ACCESS_TOKEN_SETTING_KEY)
        return loadSettings()
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
        const val MAL_CLIENT_ID_SETTING_KEY = "external.my_anime_list.client_id"
        const val MAL_ACCESS_TOKEN_SETTING_KEY = "external.my_anime_list.access_token.dpapi"
        const val BANGUMI_BASE_URL_SETTING_KEY = "external.bangumi.base_url"
        const val BANGUMI_USER_AGENT_SETTING_KEY = "external.bangumi.user_agent"
        const val BANGUMI_ACCESS_TOKEN_SETTING_KEY = "external.bangumi.access_token.dpapi"
    }
}

data class ExternalAnimeProviderSettings(
    val myAnimeListClientId: String? = null,
    val hasMyAnimeListAccessToken: Boolean = false,
    val bangumiBaseUrl: String = DEFAULT_BANGUMI_BASE_URL,
    val bangumiUserAgent: String = DEFAULT_BANGUMI_USER_AGENT,
    val hasBangumiAccessToken: Boolean = false,
) {
    val myAnimeListStatusText: String
        get() = when {
            myAnimeListClientId == null -> "not configured"
            hasMyAnimeListAccessToken -> "client ID and access token saved"
            else -> "client ID saved for anime search"
        }

    val bangumiStatusText: String
        get() = if (hasBangumiAccessToken) {
            "access token saved"
        } else {
            "public search only"
        }
}

private fun normalizeHttpsBaseUrl(value: String, label: String): String {
    val uri = URI(value.trim().ifBlank { DEFAULT_BANGUMI_BASE_URL })
    require(uri.scheme == "https") { "$label base URL must be HTTPS" }
    return uri.toString().trimEnd('/') + "/"
}

const val DEFAULT_BANGUMI_BASE_URL = "https://api.bgm.tv/"
const val DEFAULT_BANGUMI_USER_AGENT = "Danmaku/0.1 (https://github.com/Energy0124/Danmaku)"
