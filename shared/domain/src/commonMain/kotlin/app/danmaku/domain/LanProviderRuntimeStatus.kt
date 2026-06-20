package app.danmaku.domain

import kotlinx.serialization.Serializable

@Serializable
data class LanProviderRuntimeStatus(
    val dandanplay: LanDandanplayRuntimeCapability = LanDandanplayRuntimeCapability(),
    val myAnimeList: LanExternalAnimeRuntimeCapability = LanExternalAnimeRuntimeCapability(),
    val bangumi: LanExternalAnimeRuntimeCapability = LanExternalAnimeRuntimeCapability(),
)

@Serializable
data class LanDandanplayRuntimeCapability(
    val matchAvailable: Boolean = false,
    val commentFetchAvailable: Boolean = false,
    val authenticated: Boolean = false,
    val reasonCode: String = "not-configured",
) {
    init {
        require(reasonCode.isNotBlank()) { "reasonCode must not be blank" }
    }
}

@Serializable
data class LanExternalAnimeRuntimeCapability(
    val searchAvailable: Boolean = false,
    val listReadAvailable: Boolean = false,
    val listWriteAvailable: Boolean = false,
    val authenticated: Boolean = false,
    val reasonCode: String = "not-configured",
) {
    init {
        require(reasonCode.isNotBlank()) { "reasonCode must not be blank" }
    }
}
