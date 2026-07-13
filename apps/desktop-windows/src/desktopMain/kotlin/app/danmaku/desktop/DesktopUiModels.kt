package app.danmaku.desktop

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

internal enum class DesktopShellTab(
    val title: String,
) {
    HOME("Home"),
    PLAYBACK("Playback"),
    MEDIA_LIBRARY("Library"),
    DOWNLOADS("Downloads"),
    TRACKING("Tracking"),
    PROFILE("Settings"),
}

internal enum class WindowsLibraryView(
    val label: String,
) {
    CONTINUE_WATCHING("Continue"),
    NEXT_UP("Next up"),
    ALL_SERIES("All Series"),
    RECENTLY_WATCHED("History"),
    FAVORITES("Favorites"),
    FILES("Files"),
    QUALITY("Quality"),
    EXTERNAL_SYNC("External Sync"),
    PAIRED("Paired"),
}

internal enum class DownloadQueueFilter(
    val label: String,
) {
    ALL("All"),
    ACTIVE("Active"),
    QUEUED("Queued"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    ;

    fun matches(item: DesktopDownloadQueueItem): Boolean =
        when (this) {
            ALL -> true
            ACTIVE -> item.isActiveDownload()
            QUEUED -> item.isQueuedDownload()
            COMPLETED -> item.isCompletedDownload()
            FAILED -> item.isFailedDownload()
        }
}

internal enum class DesktopSettingsSection(
    val title: String,
    val icon: ImageVector,
) {
    GENERAL("General", Icons.Filled.MoreHoriz),
    LIBRARY("Library", Icons.AutoMirrored.Filled.LibraryBooks),
    PLAYBACK("Playback", Icons.Filled.PlayArrow),
    DANMAKU("Danmaku", Icons.Filled.Subtitles),
    PROVIDERS("Providers", Icons.Filled.Refresh),
    SERVER("Server", Icons.Filled.Devices),
    STORAGE("Storage", Icons.Filled.FolderOpen),
    PRIVACY("Privacy", Icons.Filled.Warning),
    DIAGNOSTICS("Diagnostics", Icons.Filled.Computer),
}
