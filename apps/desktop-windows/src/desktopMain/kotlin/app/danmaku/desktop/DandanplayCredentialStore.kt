package app.danmaku.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.Properties

class DandanplayCredentialStore(
    private val store: DesktopLibraryCatalogStore,
    private val secretProtector: DesktopSecretProtector = DesktopSecretProtector.default(),
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val localDefaultsProvider: () -> DandanplayLocalCredentialDefaults? = {
        DandanplayLocalCredentialDefaults.load()
    },
) {
    fun loadSettings(): DandanplayProviderSettings {
        val appId = store.loadSetting(APP_ID_SETTING_KEY)?.value?.takeIf(String::isNotBlank)
        val defaults = loadDefaultsIfUnconfigured()
        return DandanplayProviderSettings(
            baseUrl = store.loadSetting(BASE_URL_SETTING_KEY)?.value
                ?: defaults?.baseUrl
                ?: DandanplayConnection.DEFAULT_BASE_URL,
            appId = appId ?: defaults?.appId,
            hasAppSecret = (appId ?: defaults?.appId) != null &&
                (loadSecret(APP_SECRET_SETTING_KEY) ?: defaults?.appSecret) != null,
            authenticationMode = store.loadSetting(AUTHENTICATION_MODE_SETTING_KEY)
                ?.value
                ?.let(::authenticationModeOrDefault)
                ?: defaults?.authenticationMode
                ?: DandanplayAuthenticationMode.SIGNED,
            cacheMaxAgeDays = store.loadSetting(CACHE_MAX_AGE_DAYS_SETTING_KEY)
                ?.value
                ?.toIntOrNull()
                ?.takeIf { it >= MIN_CACHE_MAX_AGE_DAYS }
                ?: defaults?.cacheMaxAgeDays
                ?: DEFAULT_CACHE_MAX_AGE_DAYS,
        )
    }

    fun loadConnection(): DandanplayConnection {
        val settings = loadSettings()
        val appSecret = loadSecret(APP_SECRET_SETTING_KEY) ?: loadDefaultsIfUnconfigured()?.appSecret
        return if (settings.appId != null && appSecret != null) {
            DandanplayConnection(
                baseUrl = settings.baseUrl,
                appId = settings.appId,
                appSecret = appSecret,
                authenticationMode = settings.authenticationMode,
            )
        } else {
            DandanplayConnection(settings.baseUrl)
        }
    }

    fun saveSettings(
        baseUrl: String,
        appId: String?,
        appSecret: String?,
        authenticationMode: DandanplayAuthenticationMode,
        cacheMaxAgeDays: Int = loadSettings().cacheMaxAgeDays,
    ): DandanplayProviderSettings {
        require(cacheMaxAgeDays >= MIN_CACHE_MAX_AGE_DAYS) {
            "dandanplay cache max age must be at least $MIN_CACHE_MAX_AGE_DAYS day"
        }
        val existingSettings = loadSettings()
        val existingSecret = loadSecret(APP_SECRET_SETTING_KEY)
        val trimmedAppId = appId?.trim()?.takeIf(String::isNotEmpty)
        val trimmedAppSecret = appSecret?.trim()?.takeIf(String::isNotEmpty)
            ?: existingSecret?.takeIf { trimmedAppId == existingSettings.appId }
        val validatedConnection = if (trimmedAppId != null || trimmedAppSecret != null) {
            DandanplayConnection(baseUrl, trimmedAppId, trimmedAppSecret, authenticationMode)
        } else {
            DandanplayConnection(baseUrl)
        }

        saveSetting(BASE_URL_SETTING_KEY, validatedConnection.baseUri.toString().trimEnd('/'))
        if (validatedConnection.hasCredentials) {
            saveSetting(APP_ID_SETTING_KEY, trimmedAppId ?: error("validated app id missing"))
            saveSecret(APP_SECRET_SETTING_KEY, trimmedAppSecret ?: error("validated app secret missing"))
            saveSetting(AUTHENTICATION_MODE_SETTING_KEY, validatedConnection.authenticationMode.name)
        } else {
            store.deleteSetting(APP_ID_SETTING_KEY)
            store.deleteSetting(APP_SECRET_SETTING_KEY)
            store.deleteSetting(AUTHENTICATION_MODE_SETTING_KEY)
        }
        saveSetting(CACHE_MAX_AGE_DAYS_SETTING_KEY, cacheMaxAgeDays.toString())
        return loadSettings()
    }

    fun deleteSettings() {
        store.deleteSetting(BASE_URL_SETTING_KEY)
        store.deleteSetting(APP_ID_SETTING_KEY)
        store.deleteSetting(APP_SECRET_SETTING_KEY)
        store.deleteSetting(AUTHENTICATION_MODE_SETTING_KEY)
        store.deleteSetting(CACHE_MAX_AGE_DAYS_SETTING_KEY)
    }

    private fun loadSecret(key: String): String? =
        store.loadSetting(key)
            ?.value
            ?.let(Base64.getDecoder()::decode)
            ?.let(secretProtector::unprotect)
            ?.toString(Charsets.UTF_8)

    private fun loadDefaultsIfUnconfigured(): DandanplayLocalCredentialDefaults? =
        if (
            store.loadSetting(BASE_URL_SETTING_KEY) == null &&
            store.loadSetting(APP_ID_SETTING_KEY) == null &&
            store.loadSetting(APP_SECRET_SETTING_KEY) == null &&
            store.loadSetting(AUTHENTICATION_MODE_SETTING_KEY) == null
        ) {
            localDefaultsProvider()
        } else {
            null
        }

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
        const val BASE_URL_SETTING_KEY = "dandanplay.base_url"
        const val APP_ID_SETTING_KEY = "dandanplay.app_id"
        const val APP_SECRET_SETTING_KEY = "dandanplay.app_secret.dpapi"
        const val AUTHENTICATION_MODE_SETTING_KEY = "dandanplay.authentication_mode"
        const val CACHE_MAX_AGE_DAYS_SETTING_KEY = "dandanplay.cache_max_age_days"
        const val DEFAULT_CACHE_MAX_AGE_DAYS = 30
        const val MIN_CACHE_MAX_AGE_DAYS = 1

        fun authenticationModeOrDefault(value: String): DandanplayAuthenticationMode =
            runCatching { DandanplayAuthenticationMode.valueOf(value) }
                .getOrDefault(DandanplayAuthenticationMode.SIGNED)
    }
}

data class DandanplayLocalCredentialDefaults(
    val baseUrl: String = DandanplayConnection.DEFAULT_BASE_URL,
    val proxyBaseUrl: String? = null,
    val appId: String? = null,
    val appSecret: String? = null,
    val authenticationMode: DandanplayAuthenticationMode = DandanplayAuthenticationMode.SIGNED,
    val cacheMaxAgeDays: Int? = null,
) {
    companion object {
        fun load(
            environment: Map<String, String> = System.getenv(),
            propertiesPath: Path? = null,
        ): DandanplayLocalCredentialDefaults? {
            val properties = Properties()
            val paths = propertiesPath
                ?.let(::listOf)
                ?: defaultLocalPropertiesPaths(environment)
            paths
                .filter(Files::isRegularFile)
                .forEach { path ->
                    Files.newInputStream(path).use(properties::load)
                }

            fun value(
                propertyName: String,
                environmentName: String,
            ): String? =
                (environment[environmentName] ?: properties.getProperty(propertyName))
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)

            val baseUrl = value("danmaku.dandanplay.baseUrl", "DANMAKU_DANDANPLAY_BASE_URL")
                ?: DandanplayConnection.DEFAULT_BASE_URL
            val proxyBaseUrl = value("danmaku.dandanplay.proxyBaseUrl", "DANMAKU_DANDANPLAY_PROXY_BASE_URL")
            val appId = value("danmaku.dandanplay.appId", "DANMAKU_DANDANPLAY_APP_ID")
            val appSecret = value("danmaku.dandanplay.appSecret", "DANMAKU_DANDANPLAY_APP_SECRET")
            val authenticationMode = value(
                "danmaku.dandanplay.authenticationMode",
                "DANMAKU_DANDANPLAY_AUTHENTICATION_MODE",
            )
                ?.let { runCatching { DandanplayAuthenticationMode.valueOf(it.uppercase()) }.getOrNull() }
                ?: DandanplayAuthenticationMode.SIGNED
            val cacheMaxAgeDays = value(
                "danmaku.dandanplay.cacheMaxAgeDays",
                "DANMAKU_DANDANPLAY_CACHE_MAX_AGE_DAYS",
            )?.toIntOrNull()?.takeIf { it >= 1 }
            val hasDirectCredentials = appId != null && appSecret != null
            val effectiveBaseUrl = if (hasDirectCredentials) baseUrl else proxyBaseUrl ?: baseUrl

            return if (
                appId == null &&
                appSecret == null &&
                proxyBaseUrl == null &&
                baseUrl == DandanplayConnection.DEFAULT_BASE_URL
            ) {
                null
            } else {
                DandanplayLocalCredentialDefaults(
                    baseUrl = effectiveBaseUrl,
                    proxyBaseUrl = proxyBaseUrl,
                    appId = appId,
                    appSecret = appSecret,
                    authenticationMode = authenticationMode,
                    cacheMaxAgeDays = cacheMaxAgeDays,
                )
            }
        }

        private fun defaultLocalPropertiesPaths(environment: Map<String, String>): List<Path> =
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

data class DandanplayProviderSettings(
    val baseUrl: String,
    val appId: String?,
    val hasAppSecret: Boolean,
    val authenticationMode: DandanplayAuthenticationMode,
    val cacheMaxAgeDays: Int,
) {
    init {
        require(cacheMaxAgeDays >= 1) { "cacheMaxAgeDays must be positive" }
    }

    val hasCredentials: Boolean
        get() = appId != null && hasAppSecret

    val isFetchEnabled: Boolean
        get() = hasCredentials || baseUrl != DandanplayConnection.DEFAULT_BASE_URL

    val statusText: String
        get() =
            when {
                hasCredentials ->
                    "configured for AppId ${appId?.redactMiddle()} using ${authenticationMode.name.lowercase()} auth"
                isFetchEnabled ->
                    "configured for compatible API server without credentials"
                else ->
                    "not configured for automatic fetching yet"
            }
}

private fun String.redactMiddle(): String =
    when {
        length <= 4 -> "<redacted>"
        length <= 8 -> "${take(2)}...${takeLast(2)}"
        else -> "${take(4)}...${takeLast(4)}"
    }
