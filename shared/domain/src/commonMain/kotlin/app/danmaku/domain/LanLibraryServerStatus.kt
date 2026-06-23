package app.danmaku.domain

import kotlinx.serialization.Serializable

@Serializable
data class LanLibraryServerStatus(
    val appName: String = "Danmaku",
    val apiVersion: Int = CURRENT_API_VERSION,
    val pairingRequired: Boolean = false,
    val mediaStreaming: Boolean = true,
    val progressSync: Boolean = true,
    val trustedDeviceManagement: Boolean = false,
    val webUiAvailable: Boolean = false,
    val webUiPath: String? = null,
    val hostMode: String = HOST_MODE_EMBEDDED_DESKTOP,
    val providerSettings: LanProviderSettingsStatus? = null,
) {
    init {
        require(appName.isNotBlank()) { "appName must not be blank" }
        require(apiVersion > 0) { "apiVersion must be positive" }
        require(webUiPath == null || webUiPath.startsWith("/")) { "webUiPath must be absolute when present" }
        require(hostMode.isNotBlank()) { "hostMode must not be blank" }
    }

    companion object {
        const val CURRENT_API_VERSION = 1
        const val HOST_MODE_EMBEDDED_DESKTOP = "embedded-desktop"
        const val HOST_MODE_HEADLESS_SERVER = "headless-server"
    }
}

@Serializable
data class LanProviderSettingsStatus(
    val dandanplay: LanDandanplayProviderStatus = LanDandanplayProviderStatus(),
    val externalAnime: LanExternalAnimeProviderStatus = LanExternalAnimeProviderStatus(),
)

@Serializable
data class LanDandanplayProviderStatus(
    val baseUrl: String? = null,
    val appId: String? = null,
    val hasAppSecret: Boolean = false,
    val authenticationMode: String? = null,
    val cacheMaxAgeDays: Int? = null,
) {
    init {
        require(baseUrl == null || baseUrl.isNotBlank()) { "dandanplay baseUrl must not be blank" }
        require(appId == null || appId.isNotBlank()) { "dandanplay appId must not be blank" }
        require(authenticationMode == null || authenticationMode.isNotBlank()) {
            "dandanplay authenticationMode must not be blank"
        }
        require(cacheMaxAgeDays == null || cacheMaxAgeDays >= 1) {
            "dandanplay cacheMaxAgeDays must be positive"
        }
    }
}

@Serializable
data class LanExternalAnimeProviderStatus(
    val myAnimeListClientId: String? = null,
    val hasMyAnimeListClientSecret: Boolean = false,
    val hasMyAnimeListAccessToken: Boolean = false,
    val bangumiBaseUrl: String? = null,
    val bangumiUserAgent: String? = null,
    val hasBangumiAccessToken: Boolean = false,
) {
    init {
        require(myAnimeListClientId == null || myAnimeListClientId.isNotBlank()) {
            "myAnimeListClientId must not be blank"
        }
        require(bangumiBaseUrl == null || bangumiBaseUrl.isNotBlank()) { "bangumiBaseUrl must not be blank" }
        require(bangumiUserAgent == null || bangumiUserAgent.isNotBlank()) { "bangumiUserAgent must not be blank" }
    }
}

fun LanLibraryServerStatus.compatibilityErrorMessage(
    supportedApiVersion: Int = LanLibraryServerStatus.CURRENT_API_VERSION,
): String? {
    require(supportedApiVersion > 0) { "supportedApiVersion must be positive" }
    return when {
        apiVersion > supportedApiVersion ->
            "This Windows library server requires a newer Danmaku app. " +
                "Server API $apiVersion is newer than supported API $supportedApiVersion."
        !mediaStreaming ->
            "This Windows library server does not expose media streaming."
        else -> null
    }
}
