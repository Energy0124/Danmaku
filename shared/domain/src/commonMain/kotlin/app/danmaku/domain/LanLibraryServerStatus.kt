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
) {
    init {
        require(appName.isNotBlank()) { "appName must not be blank" }
        require(apiVersion > 0) { "apiVersion must be positive" }
    }

    companion object {
        const val CURRENT_API_VERSION = 1
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
