package app.danmaku.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.danmaku.domain.ExternalAnimeTrackingPlan
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibraryNextUpItem
import app.danmaku.domain.LibraryPlaybackProgressItem
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LibrarySeriesWatchSummary
import app.danmaku.domain.PlaybackSnapshot
import java.nio.file.Path

@Composable
internal fun HomeMainColumn(
    strings: DesktopStrings,
    playbackSnapshot: PlaybackSnapshot,
    continueWatchingItems: List<LibraryPlaybackProgressItem>,
    nextUpItems: List<LibraryNextUpItem>,
    recentlyWatchedItems: List<LibraryPlaybackProgressItem>,
    recentlyAddedItems: List<LibraryMediaItem>,
    series: List<LibrarySeries>,
    seriesByMediaId: Map<String, LibrarySeries>,
    seriesPosterById: Map<String, Path?>,
    seriesWatchSummaryById: Map<String, LibrarySeriesWatchSummary>,
    favoriteMediaIds: Set<String>,
    isPreparingLocalPlayback: Boolean,
    onOpenLibrary: () -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HomeHeroCard(
            strings = strings,
            playbackSnapshot = playbackSnapshot,
            continueWatchingItems = continueWatchingItems,
            nextUpItems = nextUpItems,
            seriesByMediaId = seriesByMediaId,
            seriesPosterById = seriesPosterById,
            isPreparingLocalPlayback = isPreparingLocalPlayback,
            onOpenLibrary = onOpenLibrary,
            onPlayLocalPlayback = onPlayLocalPlayback,
        )
        HomeSectionHeader(
            title = strings.recentlyAddedTitle,
            actionLabel = strings.browseAllAction,
            onAction = onOpenLibrary,
        )
        if (recentlyAddedItems.isEmpty()) {
            EmptyState(strings.newlyAddedEmptyText)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                recentlyAddedItems.take(4).forEach { mediaItem ->
                    HomeEpisodeCard(
                        strings = strings,
                        mediaItem = mediaItem,
                        coverPath = seriesByMediaId[mediaItem.id]?.let { seriesPosterById[it.id] },
                        progressPercent = null,
                        detail = mediaItem.recentlyAddedDetail(strings),
                        isPreparing = isPreparingLocalPlayback,
                        playContentDescription = strings.playAction,
                        onPlay = { onPlayLocalPlayback(mediaItem) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat((4 - recentlyAddedItems.take(4).size).coerceAtLeast(0)) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        HomeSectionHeader(
            title = strings.recentlyWatchedTitle,
            actionLabel = strings.openLibraryAction,
            onAction = onOpenLibrary,
        )
        if (recentlyWatchedItems.isEmpty()) {
            EmptyState(strings.recentPlaybackEmptyText)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                recentlyWatchedItems.take(4).forEach { item ->
                    HomeEpisodeCard(
                        strings = strings,
                        mediaItem = item.mediaItem,
                        coverPath = seriesByMediaId[item.mediaItem.id]?.let { seriesPosterById[it.id] },
                        progressPercent = item.progress.progressPercent(),
                        detail = strings.watchedAtLabel(item.progress.positionMs),
                        isPreparing = isPreparingLocalPlayback,
                        playContentDescription = strings.playAction,
                        onPlay = { onPlayLocalPlayback(item.mediaItem) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat((4 - recentlyWatchedItems.take(4).size).coerceAtLeast(0)) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        HomeSectionHeader(
            title = strings.myLibraryTitle,
            actionLabel = strings.browseAllAction,
            onAction = onOpenLibrary,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                title = strings.seriesSummaryTitle,
                value = series.size.toString(),
                caption = strings.matchedGroupsCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.favoritesSummaryTitle,
                value = favoriteMediaIds.size.toString(),
                caption = strings.savedEpisodesCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.watchingSummaryTitle,
                value = continueWatchingItems.size.toString(),
                caption = strings.inProgressCaption,
                modifier = Modifier.weight(1f),
            )
        }
        if (series.isEmpty()) {
            EmptyState(strings.homeLibraryEmptyText, strings.openLibraryAction, onOpenLibrary)
        } else {
            SectionCard(strings.librarySnapshotTitle) {
                series.take(6).forEach { librarySeries ->
                    HomeSeriesSummaryRow(
                        strings = strings,
                        series = librarySeries,
                        coverPath = seriesPosterById[librarySeries.id],
                        watchSummary = seriesWatchSummaryById[librarySeries.id],
                        onClick = onOpenLibrary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeHeroCard(
    strings: DesktopStrings,
    playbackSnapshot: PlaybackSnapshot,
    continueWatchingItems: List<LibraryPlaybackProgressItem>,
    nextUpItems: List<LibraryNextUpItem>,
    seriesByMediaId: Map<String, LibrarySeries>,
    seriesPosterById: Map<String, Path?>,
    isPreparingLocalPlayback: Boolean,
    onOpenLibrary: () -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    val resumeCards = buildList {
        continueWatchingItems.take(3).forEach { item ->
            add(
                HomeResumeCardModel(
                    mediaItem = item.mediaItem,
                    detail = strings.resumeAtLabel(item.progress.positionMs),
                    progressPercent = item.progress.progressPercent(),
                    actionLabel = strings.resumeAction,
                ),
            )
        }
        nextUpItems.take(3).forEach { item ->
            add(
                HomeResumeCardModel(
                    mediaItem = item.mediaItem,
                    detail = item.nextUpLabel(strings),
                    progressPercent = item.progress?.progressPercent(),
                    actionLabel = item.nextUpActionLabel(strings),
                ),
            )
        }
    }.distinctBy { it.mediaItem.id }.take(3)

    SectionCard(strings.continueWatchingTitle) {
        if (resumeCards.isEmpty()) {
            EmptyState(strings.noResumeQueueText, strings.openLibraryAction, onOpenLibrary)
            return@SectionCard
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            resumeCards.forEach { card ->
                HomeResumeCard(
                    strings = strings,
                    card = card,
                    coverPath = seriesByMediaId[card.mediaItem.id]?.let { seriesPosterById[it.id] },
                    isPreparing = isPreparingLocalPlayback,
                    onPlay = { onPlayLocalPlayback(card.mediaItem) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        playbackSnapshot.source?.let { source ->
            MetadataRow(strings.loadedNowLabel, source.toString().redactToken())
        }
    }
}

internal data class HomeResumeCardModel(
    val mediaItem: LibraryMediaItem,
    val detail: String,
    val progressPercent: Int?,
    val actionLabel: String,
)

@Composable
internal fun HomeResumeCard(
    strings: DesktopStrings,
    card: HomeResumeCardModel,
    coverPath: Path?,
    isPreparing: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DanmakuColors.SurfaceRaised.copy(alpha = 0.74f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.55f)
                .clip(RoundedCornerShape(6.dp))
                .background(DanmakuColors.AccentSoft),
        ) {
            SeriesPosterImage(
                coverPath = coverPath,
                title = card.mediaItem.displaySeriesTitle(),
                modifier = Modifier.fillMaxSize(),
            )
            PlayerIconButton(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = card.actionLabel,
                enabled = !isPreparing,
                onClick = onPlay,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Text(card.mediaItem.displaySeriesTitle(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(card.mediaItem.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        card.mediaItem.localSeriesLabel(strings)?.let { label ->
            Text(label, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MiniProgressBar(percent = card.progressPercent)
        Text(card.detail, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun HomeEpisodeCard(
    strings: DesktopStrings,
    mediaItem: LibraryMediaItem,
    coverPath: Path?,
    progressPercent: Int?,
    detail: String,
    isPreparing: Boolean,
    playContentDescription: String,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DanmakuColors.Surface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        SeriesPosterImage(
            coverPath = coverPath,
            title = mediaItem.displaySeriesTitle(),
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Text(mediaItem.displaySeriesTitle(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(mediaItem.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        mediaItem.localSeriesLabel(strings)?.let { label ->
            Text(label, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MiniProgressBar(percent = progressPercent)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(detail, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            PlayerIconButton(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = playContentDescription,
                enabled = !isPreparing,
                onClick = onPlay,
            )
        }
    }
}

@Composable
internal fun HomeSeriesSummaryRow(
    strings: DesktopStrings,
    series: LibrarySeries,
    coverPath: Path?,
    watchSummary: LibrarySeriesWatchSummary?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SeriesPosterImage(
            coverPath = coverPath,
            title = series.title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(series.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(watchSummary.progressLabel(strings), color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StatusPill(strings.episodeCountShortLabel(series.episodeCount))
    }
}

@Composable
internal fun HomeStatusColumn(
    strings: DesktopStrings,
    registeredRoots: List<DesktopLibraryRoot>,
    episodeCount: Int,
    seriesCount: Int,
    networkUrls: List<String>,
    pairingToken: String,
    overlayStatus: String,
    libraryError: String?,
    lastScanStats: LocalMediaLibraryScanStats?,
    isIndexing: Boolean,
    isRefreshingSeriesPosters: Boolean,
    metadataRefreshingCount: Int,
    posterReadyCount: Int,
    externalTrackingPlan: ExternalAnimeTrackingPlan?,
    dandanplayCacheStatus: DandanplayPlaybackUiStatus?,
    diagnosticLog: List<DesktopDiagnosticLogEntry>,
    onOpenLibrary: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTracking: () -> Unit,
    onRefreshMetadata: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OperationalStatusCard(
            icon = Icons.Filled.Computer,
            title = strings.homeServerStatusTitle,
            value = when {
                libraryError != null -> strings.attentionNeededLabel
                isIndexing -> strings.indexingLabel
                else -> strings.onlineLabel
            },
            detail = networkUrls.firstOrNull() ?: strings.noLanUrlDetectedLabel,
            statusColor = when {
                libraryError != null -> DanmakuColors.Warning
                isIndexing -> DanmakuColors.Info
                else -> DanmakuColors.Good
            },
            actionLabel = strings.openLibraryAction,
            onAction = onOpenLibrary,
        ) {
            MetadataRow(strings.pairingLabel, pairingToken)
            MetadataRow(strings.foldersLabel, registeredRoots.size.toString())
            MetadataRow(strings.episodesLabel, episodeCount.toString())
            libraryError?.let { MetadataRow(strings.errorLabel, it, DanmakuColors.Warning) }
        }
        OperationalStatusCard(
            icon = Icons.Filled.GridView,
            title = strings.metadataAndPostersTitle,
            value = when {
                isRefreshingSeriesPosters || metadataRefreshingCount > 0 -> strings.loadingLabel
                posterReadyCount > 0 -> strings.readyStatusLabel
                seriesCount > 0 -> strings.partialLabel
                else -> strings.waitingLabel
            },
            detail = strings.postersReadySummary(posterReadyCount, seriesCount),
            statusColor = when {
                isRefreshingSeriesPosters || metadataRefreshingCount > 0 -> DanmakuColors.Info
                posterReadyCount > 0 -> DanmakuColors.Good
                seriesCount > 0 -> DanmakuColors.Warning
                else -> DanmakuColors.TextMuted
            },
            actionLabel = if (isRefreshingSeriesPosters || metadataRefreshingCount > 0) strings.refreshingAction else strings.refreshAction,
            actionEnabled = !isRefreshingSeriesPosters,
            onAction = onRefreshMetadata,
        ) {
            lastScanStats?.let {
                MetadataRow(strings.lastScanLabel, strings.lastScanCountsSummary(it.reusedItemCount, it.refreshedItemCount))
            }
            MetadataRow(strings.groupsLabel, seriesCount.toString())
        }
        OperationalStatusCard(
            icon = Icons.Filled.Refresh,
            title = strings.externalSyncTitle,
            value = externalTrackingPlan?.summary?.localizedLabel(strings) ?: strings.notMappedLabel,
            detail = strings.readyUpdatesLabel(externalTrackingPlan?.summary?.updateCount ?: 0),
            statusColor = if ((externalTrackingPlan?.summary?.updateCount ?: 0) > 0) DanmakuColors.Info else DanmakuColors.TextMuted,
            actionLabel = strings.openTrackingAction,
            onAction = onOpenTracking,
        )
        OperationalStatusCard(
            icon = Icons.Filled.FolderOpen,
            title = strings.tabTitle(DesktopShellTab.DOWNLOADS),
            value = strings.downloadQueueReadyLabel,
            detail = strings.downloadsImportDetail,
            statusColor = DanmakuColors.TextMuted,
            actionLabel = strings.openDownloadsAction,
            onAction = onOpenDownloads,
        )
        OperationalStatusCard(
            icon = if (dandanplayCacheStatus?.summary?.isDandanplayWarningStatus() == true) Icons.Filled.Warning else Icons.Filled.CheckCircle,
            title = strings.cachedDanmakuTitle,
            value = dandanplayCacheStatus?.summary ?: strings.notCheckedLabel,
            detail = overlayStatus,
            statusColor = when {
                dandanplayCacheStatus == null -> DanmakuColors.TextMuted
                dandanplayCacheStatus.summary.isDandanplayWarningStatus() -> DanmakuColors.Warning
                else -> DanmakuColors.Good
            },
            actionLabel = strings.manageCacheAction,
            onAction = onOpenSettings,
        )
        DiagnosticsPanel(
            strings = strings,
            diagnosticLog = diagnosticLog,
            modifier = Modifier.heightIn(max = 280.dp),
        )
    }
}

@Composable
internal fun OperationalStatusCard(
    icon: ImageVector,
    title: String,
    value: String,
    detail: String,
    statusColor: Color,
    actionLabel: String,
    actionEnabled: Boolean = true,
    onAction: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    SectionCard(title) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(value, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, color = DanmakuColors.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        content()
        LibraryActionButton(
            imageVector = Icons.Filled.PlayArrow,
            label = actionLabel,
            enabled = actionEnabled,
            modifier = Modifier.fillMaxWidth(),
            onClick = onAction,
        )
    }
}

@Composable
internal fun HomeSectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.weight(1f))
        LibraryActionButton(
            imageVector = Icons.AutoMirrored.Filled.ViewList,
            label = actionLabel,
            onClick = onAction,
        )
    }
}
