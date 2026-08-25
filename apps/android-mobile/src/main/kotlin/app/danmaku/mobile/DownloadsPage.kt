package app.danmaku.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.danmaku.library.android.OfflineCacheEntry
import app.danmaku.library.android.OfflineCacheState

@Composable
internal fun DownloadsPage(
    contentPadding: PaddingValues,
    entries: List<OfflineCacheEntry>,
    availableBytes: Long,
    error: String?,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    PageColumn(contentPadding) {
        item(key = "downloads-header") {
            PageHeader(
                icon = Icons.Filled.Download,
                title = stringResource(R.string.downloads_title),
                subtitle = stringResource(
                    R.string.downloads_summary,
                    entries.count { it.state == OfflineCacheState.READY },
                    entries.sumOf(OfflineCacheEntry::downloadedBytes).formatCacheSize(),
                    availableBytes.formatCacheSize(),
                ),
            )
        }
        item(key = "downloads-navigation") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack, modifier = Modifier.testTag("downloads-back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_back))
                }
                if (entries.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        modifier = Modifier.testTag("downloads-clear"),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_clear_all))
                    }
                }
            }
        }
        error?.let {
            item(key = "downloads-error") { ErrorText(it) }
        }
        if (entries.isEmpty()) {
            item(key = "downloads-empty") {
                EmptyPanel(
                    title = stringResource(R.string.downloads_empty_title),
                    body = stringResource(R.string.downloads_empty_body),
                )
            }
        } else {
            entries.sortedWith(
                compareBy<OfflineCacheEntry> { it.state == OfflineCacheState.READY }
                    .thenBy { it.item.seriesTitle.lowercase() }
                    .thenBy { it.item.episodeTitle.lowercase() },
            ).forEach { entry ->
                item(key = "download:${entry.key}") {
                    DownloadEntryCard(entry, onPlay, onPause, onResume, onDelete)
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.downloads_clear_confirm_title)) },
            text = { Text(stringResource(R.string.downloads_clear_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClear()
                    },
                ) { Text(stringResource(R.string.action_clear_all)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun DownloadEntryCard(
    entry: OfflineCacheEntry,
    onPlay: (String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("download-entry:${entry.item.id}"),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF17212A),
        border = BorderStroke(1.dp, Color(0xFF304454)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(entry.item.episodeTitle, fontWeight = FontWeight.SemiBold)
            Text(entry.item.seriesTitle, color = SubtleText, style = MaterialTheme.typography.bodySmall)
            Text(
                "${entry.state.displayLabel()} · ${entry.downloadedBytes.formatCacheSize()} / ${entry.totalBytes.formatCacheSize()}",
                color = SubtleText,
                style = MaterialTheme.typography.bodySmall,
            )
            if (entry.state in setOf(
                    OfflineCacheState.QUEUED,
                    OfflineCacheState.DOWNLOADING,
                    OfflineCacheState.RETRYING,
                )
            ) {
                val progress = if (entry.totalBytes > 0) {
                    (entry.downloadedBytes.toFloat() / entry.totalBytes).coerceIn(0f, 1f)
                } else {
                    0f
                }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            entry.errorMessage?.let { ErrorText(it) }
            entry.warnings.forEach { Text(it, color = AccentAmber, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (entry.state) {
                    OfflineCacheState.READY -> Button(onClick = { onPlay(entry.key) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_play))
                    }
                    OfflineCacheState.QUEUED,
                    OfflineCacheState.DOWNLOADING,
                    OfflineCacheState.RETRYING,
                    -> OutlinedButton(onClick = { onPause(entry.key) }) {
                        Icon(Icons.Filled.Pause, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_pause))
                    }
                    OfflineCacheState.PAUSED,
                    OfflineCacheState.FAILED,
                    -> OutlinedButton(onClick = { onResume(entry.key) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_retry))
                    }
                }
                OutlinedButton(onClick = { onDelete(entry.key) }) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
    }
}

private fun OfflineCacheState.displayLabel(): String =
    name.lowercase().replaceFirstChar(Char::uppercase)
