package app.danmaku.desktop

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.Properties

class ExternalAnimeCredentialStore(
    private val store: DesktopLibraryCatalogStore,
    private val secretProtector: DesktopSecretProtector = DesktopSecretProtector.default(),
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val localPropertiesPaths: List<Path> = defaultLocalPropertiesPaths(System.getenv()),
) {
    fun loadSettings(): ExternalAnimeProviderSettings {
        val localDefaults = loadLocalCredentialDefaults()
        return ExternalAnimeProviderSettings(
            myAnimeListClientId = store.loadSetting(MAL_CLIENT_ID_SETTING_KEY)?.value?.takeIf(String::isNotBlank)
                ?: localDefaults.myAnimeListClientId,
            hasMyAnimeListClientSecret = loadSecret(MAL_CLIENT_SECRET_SETTING_KEY) != null ||
                localDefaults.myAnimeListClientSecret != null,
            hasMyAnimeListAccessToken = loadSecret(MAL_ACCESS_TOKEN_SETTING_KEY) != null,
            bangumiBaseUrl = store.loadSetting(BANGUMI_BASE_URL_SETTING_KEY)?.value
                ?: DEFAULT_BANGUMI_BASE_URL,
            bangumiUserAgent = store.loadSetting(BANGUMI_USER_AGENT_SETTING_KEY)?.value
                ?: DEFAULT_BANGUMI_USER_AGENT,
            hasBangumiAccessToken = loadSecret(BANGUMI_ACCESS_TOKEN_SETTING_KEY) != null,
        )
    }

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

    fun loadMyAnimeListTrackingClient(): MyAnimeListTrackingClient? =
        loadMyAnimeListAccessToken()
            ?.let { accessToken ->
                MyAnimeListTrackingClient(
                    MyAnimeListTrackingConnection(accessToken = accessToken),
                )
            }

    fun loadMyAnimeListClientSecret(): String? =
        loadSecret(MAL_CLIENT_SECRET_SETTING_KEY)
            ?: loadLocalCredentialDefaults().myAnimeListClientSecret

    fun loadMyAnimeListOAuthTokens(): MyAnimeListOAuthTokens? {
        val accessToken = loadSecret(MAL_ACCESS_TOKEN_SETTING_KEY) ?: return null
        return MyAnimeListOAuthTokens(
            accessToken = accessToken,
            refreshToken = loadSecret(MAL_REFRESH_TOKEN_SETTING_KEY),
            expiresAtEpochMs = store.loadSetting(MAL_EXPIRES_AT_SETTING_KEY)?.value?.toLongOrNull(),
        )
    }

    fun loadBangumiAccessToken(): String? =
        loadSecret(BANGUMI_ACCESS_TOKEN_SETTING_KEY)

    fun loadBangumiTrackingClient(): BangumiTrackingClient? {
        val accessToken = loadBangumiAccessToken() ?: return null
        val settings = loadSettings()
        return BangumiTrackingClient(
            BangumiTrackingConnection(
                accessToken = accessToken,
                baseUri = URI(settings.bangumiBaseUrl),
                userAgent = settings.bangumiUserAgent,
            ),
        )
    }

    fun saveSettings(
        myAnimeListClientId: String?,
        myAnimeListClientSecret: String?,
        myAnimeListAccessToken: String?,
        bangumiBaseUrl: String,
        bangumiUserAgent: String,
        bangumiAccessToken: String?,
    ): ExternalAnimeProviderSettings {
        val existing = loadSettings()
        val trimmedMalClientId = myAnimeListClientId?.trim()?.takeIf(String::isNotBlank)
        val trimmedMalSecret = myAnimeListClientSecret?.trim()?.takeIf(String::isNotBlank)
        val trimmedMalToken = myAnimeListAccessToken?.trim()?.takeIf(String::isNotBlank)
        val trimmedBangumiToken = bangumiAccessToken?.trim()?.takeIf(String::isNotBlank)
        val normalizedBangumiBaseUrl = normalizeHttpsBaseUrl(bangumiBaseUrl, "Bangumi")
        val normalizedBangumiUserAgent = bangumiUserAgent.trim().takeIf(String::isNotBlank)
            ?: DEFAULT_BANGUMI_USER_AGENT

        if (trimmedMalClientId == null) {
            store.deleteSetting(MAL_CLIENT_ID_SETTING_KEY)
            store.deleteSetting(MAL_CLIENT_SECRET_SETTING_KEY)
            store.deleteSetting(MAL_ACCESS_TOKEN_SETTING_KEY)
            store.deleteSetting(MAL_REFRESH_TOKEN_SETTING_KEY)
            store.deleteSetting(MAL_EXPIRES_AT_SETTING_KEY)
        } else {
            saveSetting(MAL_CLIENT_ID_SETTING_KEY, trimmedMalClientId)
            if (trimmedMalSecret != null) {
                saveSecret(MAL_CLIENT_SECRET_SETTING_KEY, trimmedMalSecret)
            } else if (!existing.hasMyAnimeListClientSecret) {
                store.deleteSetting(MAL_CLIENT_SECRET_SETTING_KEY)
            }
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

    fun saveMyAnimeListOAuthTokens(
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Long?,
    ): ExternalAnimeProviderSettings {
        require(accessToken.isNotBlank()) { "accessToken must not be blank" }
        saveSecret(MAL_ACCESS_TOKEN_SETTING_KEY, accessToken)
        refreshToken
            ?.takeIf(String::isNotBlank)
            ?.let { saveSecret(MAL_REFRESH_TOKEN_SETTING_KEY, it) }
        expiresInSeconds
            ?.takeIf { it > 0 }
            ?.let { saveSetting(MAL_EXPIRES_AT_SETTING_KEY, (nowEpochMs() + it * 1_000).toString()) }
        return loadSettings()
    }

    fun clearMyAnimeListSettings(): ExternalAnimeProviderSettings {
        store.deleteSetting(MAL_CLIENT_ID_SETTING_KEY)
        store.deleteSetting(MAL_CLIENT_SECRET_SETTING_KEY)
        store.deleteSetting(MAL_ACCESS_TOKEN_SETTING_KEY)
        store.deleteSetting(MAL_REFRESH_TOKEN_SETTING_KEY)
        store.deleteSetting(MAL_EXPIRES_AT_SETTING_KEY)
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

    private fun loadLocalCredentialDefaults(): MyAnimeListLocalCredentialDefaults {
        val properties = Properties()
        localPropertiesPaths
            .filter(Files::isRegularFile)
            .forEach { path ->
                Files.newBufferedReader(path).use(properties::load)
            }

        fun value(
            propertyName: String,
            environmentName: String,
        ): String? =
            (System.getenv(environmentName) ?: properties.getProperty(propertyName))
                ?.trim()
                ?.takeIf(String::isNotEmpty)

        return MyAnimeListLocalCredentialDefaults(
            myAnimeListClientId = value(
                "danmaku.myanimelist.clientId",
                "DANMAKU_MYANIMELIST_CLIENT_ID",
            ),
            myAnimeListClientSecret = value(
                "danmaku.myanimelist.clientSecret",
                "DANMAKU_MYANIMELIST_CLIENT_SECRET",
            ),
        )
    }

    private companion object {
        const val MAL_CLIENT_ID_SETTING_KEY = "external.my_anime_list.client_id"
        const val MAL_CLIENT_SECRET_SETTING_KEY = "external.my_anime_list.client_secret.dpapi"
        const val MAL_ACCESS_TOKEN_SETTING_KEY = "external.my_anime_list.access_token.dpapi"
        const val MAL_REFRESH_TOKEN_SETTING_KEY = "external.my_anime_list.refresh_token.dpapi"
        const val MAL_EXPIRES_AT_SETTING_KEY = "external.my_anime_list.expires_at_epoch_ms"
        const val BANGUMI_BASE_URL_SETTING_KEY = "external.bangumi.base_url"
        const val BANGUMI_USER_AGENT_SETTING_KEY = "external.bangumi.user_agent"
        const val BANGUMI_ACCESS_TOKEN_SETTING_KEY = "external.bangumi.access_token.dpapi"

        fun defaultLocalPropertiesPaths(environment: Map<String, String>): List<Path> =
            buildList {
                add(Path.of(System.getProperty("user.dir"), "local.properties"))
                environment["LOCALAPPDATA"]
                    ?.takeIf(String::isNotBlank)
                    ?.let(Path::of)
                    ?.resolve("Danmaku")
                    ?.resolve("local.properties")
                    ?.let(::add)
                System.getProperty("user.home")
                    ?.takeIf(String::isNotBlank)
                    ?.let(Path::of)
                    ?.resolve(".danmaku")
                    ?.resolve("local.properties")
                    ?.let(::add)
                System.getProperty("danmaku.localProperties")
                    ?.takeIf(String::isNotBlank)
                    ?.let(Path::of)
                    ?.let(::add)
                environment["DANMAKU_LOCAL_PROPERTIES"]
                    ?.takeIf(String::isNotBlank)
                    ?.let(Path::of)
                    ?.let(::add)
            }.distinct()
    }
}

private data class MyAnimeListLocalCredentialDefaults(
    val myAnimeListClientId: String?,
    val myAnimeListClientSecret: String?,
)

data class ExternalAnimeProviderSettings(
    val myAnimeListClientId: String? = null,
    val hasMyAnimeListClientSecret: Boolean = false,
    val hasMyAnimeListAccessToken: Boolean = false,
    val bangumiBaseUrl: String = DEFAULT_BANGUMI_BASE_URL,
    val bangumiUserAgent: String = DEFAULT_BANGUMI_USER_AGENT,
    val hasBangumiAccessToken: Boolean = false,
) {
    val myAnimeListStatusText: String
        get() = when {
            myAnimeListClientId == null -> "not configured"
            hasMyAnimeListAccessToken -> "OAuth token saved"
            hasMyAnimeListClientSecret -> "client ID and secret saved"
            else -> "client ID saved for anime search"
        }

    val bangumiStatusText: String
        get() = if (hasBangumiAccessToken) {
            "access token saved"
        } else {
            "public search only"
        }
}

data class MyAnimeListOAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMs: Long?,
) {
    init {
        require(accessToken.isNotBlank()) { "accessToken must not be blank" }
        require(refreshToken == null || refreshToken.isNotBlank()) { "refreshToken must not be blank" }
        require(expiresAtEpochMs == null || expiresAtEpochMs >= 0) { "expiresAtEpochMs must not be negative" }
    }
}

private fun normalizeHttpsBaseUrl(value: String, label: String): String {
    val uri = URI(value.trim().ifBlank { DEFAULT_BANGUMI_BASE_URL })
    require(uri.scheme == "https") { "$label base URL must be HTTPS" }
    return uri.toString().trimEnd('/') + "/"
}

const val DEFAULT_BANGUMI_BASE_URL = "https://api.bgm.tv/"
const val DEFAULT_BANGUMI_USER_AGENT = "Danmaku/0.1 (https://github.com/Energy0124/Danmaku)"
