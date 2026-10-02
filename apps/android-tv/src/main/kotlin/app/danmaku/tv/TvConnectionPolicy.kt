package app.danmaku.tv

import app.danmaku.library.LanLibraryConnectionProfile
import java.net.URI

internal fun automaticTvConnection(
    discoveredUrls: List<String>,
    savedConnections: List<LanLibraryConnectionProfile>,
): String? {
    val urls = discoveredUrls.map { it.trim().trimEnd('/') }.distinct()
    if (savedConnections.isEmpty()) return urls.singleOrNull()
    return savedConnections.firstOrNull { it.normalizedBaseUrl in urls }?.baseUrl
}

internal fun normalizeTvServerAddress(address: String): String {
    val trimmed = address.trim()
    require(trimmed.isNotEmpty())
    val hasScheme = "://" in trimmed
    val uri = URI(if (hasScheme) trimmed else "http://$trimmed")
    require(uri.scheme == "http" || uri.scheme == "https")
    require(!uri.host.isNullOrBlank() && uri.rawUserInfo == null)
    require(uri.rawQuery == null && uri.rawFragment == null)
    require(uri.port == -1 || uri.port in 1..65_535)
    val port = if (!hasScheme && uri.port == -1) 8_686 else uri.port
    return URI(uri.scheme, null, uri.host, port, uri.path, null, null)
        .toASCIIString().trimEnd('/')
}
