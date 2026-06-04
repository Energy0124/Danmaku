package app.danmaku.desktop

object DesktopMpvWindowsOptions {
    fun forWindowId(windowId: Long): Map<String, String> {
        val unsignedWindowId = windowId.toUInt()
        require(unsignedWindowId != 0u) { "windowId must not be zero" }
        return mapOf("wid" to unsignedWindowId.toString())
    }
}
