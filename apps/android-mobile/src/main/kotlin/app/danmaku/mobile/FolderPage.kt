package app.danmaku.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.fileName
import app.danmaku.domain.folderHeading
import app.danmaku.domain.folderListing

@Composable
internal fun FolderPage(
    contentPadding: PaddingValues,
    catalog: LibraryCatalog?,
    path: List<String>,
    onOpenFolder: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onPlay: (LibraryMediaItem) -> Unit,
    onConnect: () -> Unit,
    isRefreshing: Boolean = false,
    refreshFilesSeen: Long? = null,
    refreshError: MobileFolderRefreshError? = null,
    refreshErrorDetail: String? = null,
    onRefresh: (List<String>) -> Unit = { _ -> },
) {
    val listing = remember(catalog, path) { catalog?.folderListing(path) }
    val refreshErrorText = refreshError?.let {
        stringResource(
            when (it) {
                MobileFolderRefreshError.ACCESS_CODE_REQUIRED ->
                    R.string.folders_refresh_access_code_required
                MobileFolderRefreshError.ALREADY_RUNNING ->
                    R.string.folders_refresh_already_running
                MobileFolderRefreshError.SCAN_FAILED ->
                    R.string.folders_refresh_scan_failed
                MobileFolderRefreshError.REQUEST_FAILED ->
                    R.string.folders_refresh_request_failed
            },
        )
    }

    BackHandler(enabled = path.isNotEmpty(), onBack = onNavigateUp)

    PageColumn(contentPadding = contentPadding) {
        item(key = "folder-page-header") {
            PageHeader(
                icon = Icons.Filled.Folder,
                title = catalog?.folderHeading(path) ?: stringResource(R.string.folders_title),
                subtitle = listing?.let {
                    stringResource(R.string.folders_summary, it.folders.size, it.files.size)
                } ?: stringResource(R.string.folders_connect_subtitle),
            )
        }
        if (path.isNotEmpty()) {
            item(key = "folder-up") {
                TextButton(
                    onClick = onNavigateUp,
                    modifier = Modifier.testTag("folder-up"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_up))
                }
            }
        }
        if (catalog != null) {
            item(key = "folder-refresh") {
                Button(
                    onClick = { onRefresh(path) },
                    enabled = !isRefreshing,
                    modifier = Modifier.testTag("folder-refresh"),
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isRefreshing) {
                            refreshFilesSeen?.let {
                                stringResource(R.string.folders_refresh_progress, it)
                            } ?: stringResource(R.string.folders_refresh_scanning)
                        } else {
                            stringResource(R.string.folders_refresh_action)
                        },
                    )
                }
            }
        }
        refreshErrorText?.let { errorText ->
            item(key = "folder-refresh-error") {
                FolderMessageCard(
                    title = stringResource(R.string.folders_refresh_failed_title),
                    body = buildString {
                        append(errorText)
                        refreshErrorDetail?.takeIf(String::isNotBlank)?.let {
                            append("\n")
                            append(it)
                        }
                    },
                )
            }
        }
        when {
            catalog == null -> item(key = "folders-disconnected") {
                FolderMessageCard(
                    title = stringResource(R.string.library_empty_connect_title),
                    body = stringResource(R.string.folders_connect_body),
                    action = {
                        Button(onClick = onConnect) {
                            Text(stringResource(R.string.nav_connect))
                        }
                    },
                )
            }
            listing == null || (listing.folders.isEmpty() && listing.files.isEmpty()) ->
                item(key = "folders-empty") {
                    FolderMessageCard(
                        title = stringResource(R.string.folders_empty_title),
                        body = stringResource(R.string.folders_empty_body),
                    )
                }
            else -> {
                items(listing.folders, key = { "folder:${it.name}" }) { folder ->
                    FolderBrowserRow(
                        icon = Icons.Filled.Folder,
                        title = folder.name,
                        subtitle = stringResource(R.string.folder_item_count, folder.itemCount),
                        testTag = "folder-entry:${folder.name}",
                        onClick = { onOpenFolder(folder.name) },
                    )
                }
                items(listing.files, key = { "file:${it.id}" }) { file ->
                    FolderBrowserRow(
                        icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                        title = file.fileName(),
                        subtitle = "${file.seriesTitle} · ${file.episodeTitle}",
                        testTag = "folder-file:${file.id}",
                        onClick = { onPlay(file) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderBrowserRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF17212A),
        border = BorderStroke(1.dp, Color(0xFF304454)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = SubtleText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FolderMessageCard(
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF17212A),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, color = SubtleText)
            action?.invoke()
        }
    }
}
