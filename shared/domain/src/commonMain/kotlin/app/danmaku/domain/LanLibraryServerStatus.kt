package app.danmaku.domain

import kotlinx.serialization.Serializable

@Serializable
data class LanLibraryServerStatus(
    val appName: String = "Danmaku",
    val apiVersion: Int = CURRENT_API_VERSION,
    val pairingRequired: Boolean = true,
    val mediaStreaming: Boolean = true,
    val progressSync: Boolean = true,
    val trustedDeviceManagement: Boolean = false,
    val webUiAvailable: Boolean = false,
    val webUiPath: String? = null,
    val hostMode: String = HOST_MODE_EMBEDDED_DESKTOP,
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
