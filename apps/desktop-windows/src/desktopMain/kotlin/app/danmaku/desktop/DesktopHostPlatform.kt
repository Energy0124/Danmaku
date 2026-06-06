package app.danmaku.desktop

enum class DesktopHostPlatform(
    val displayName: String,
    val bridgeLibraryNames: List<String>,
    val libmpvLibraryNames: List<String>,
    val requiresEmbeddedMpvVideoHost: Boolean,
) {
    WINDOWS(
        displayName = "Windows",
        bridgeLibraryNames = listOf("player_windows_mpv.dll"),
        libmpvLibraryNames = listOf("libmpv-2.dll"),
        requiresEmbeddedMpvVideoHost = true,
    ),
    MACOS(
        displayName = "macOS",
        bridgeLibraryNames = listOf("libplayer_windows_mpv.dylib"),
        libmpvLibraryNames = listOf("libmpv.2.dylib", "libmpv.dylib"),
        requiresEmbeddedMpvVideoHost = false,
    ),
    LINUX(
        displayName = "Linux",
        bridgeLibraryNames = listOf("libplayer_windows_mpv.so"),
        libmpvLibraryNames = listOf("libmpv.so.2", "libmpv.so.1", "libmpv.so"),
        requiresEmbeddedMpvVideoHost = false,
    ),
    UNKNOWN(
        displayName = "Desktop",
        bridgeLibraryNames = emptyList(),
        libmpvLibraryNames = emptyList(),
        requiresEmbeddedMpvVideoHost = false,
    ),
    ;

    companion object {
        fun current(osName: String = System.getProperty("os.name").orEmpty()): DesktopHostPlatform {
            val normalized = osName.lowercase()
            return when {
                "windows" in normalized -> WINDOWS
                "mac" in normalized || "darwin" in normalized -> MACOS
                "linux" in normalized -> LINUX
                else -> UNKNOWN
            }
        }
    }
}
