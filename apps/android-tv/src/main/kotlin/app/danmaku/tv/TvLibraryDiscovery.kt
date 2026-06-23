package app.danmaku.tv

import app.danmaku.library.android.DiscoveredLanLibraryServer

internal fun interface TvLibraryDiscovery {
    fun discover(): List<DiscoveredLanLibraryServer>
}
