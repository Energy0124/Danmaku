package app.danmaku.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.danmaku.domain.ExternalAnimeSyncFailure
import app.danmaku.domain.ExternalAnimeTrackingPlan
import app.danmaku.domain.ExternalAnimeTrackingPlanConflict
import app.danmaku.domain.ExternalAnimeTrackingPlanUpdate
import app.danmaku.domain.displayName

@Composable
internal fun ExternalSyncPreviewView(
    strings: DesktopStrings,
    plan: ExternalAnimeTrackingPlan?,
    isSyncing: Boolean,
    onSync: (ExternalAnimeTrackingPlan) -> Unit,
) {
    if (plan == null) {
        EmptyState(strings.noExternalSyncLibraryText)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard(
                title = strings.readySummaryTitle,
                value = plan.summary.updateCount.toString(),
                caption = strings.providerUpdatesCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.conflictsSummaryTitle,
                value = plan.summary.conflictCount.toString(),
                caption = strings.externalAheadCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.skippedLabel,
                value = plan.summary.skippedCount.toString(),
                caption = strings.mappingChecksCaption,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                enabled = plan.updates.isNotEmpty() && !isSyncing,
                onClick = { onSync(plan) },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isSyncing) strings.syncingUpdatesAction else strings.syncReadyUpdatesAction)
            }
            Text(
                if (plan.updates.isEmpty()) {
                    strings.noProviderWritesReadyText
                } else {
                    strings.writesReadyUpdatesText(plan.updates.size)
                },
                color = DanmakuColors.TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (plan.summary.providerUpdateCounts.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                plan.summary.providerUpdateCounts.toSortedMap(compareBy { it.name }).forEach { (provider, count) ->
                    StatusPill("${provider.displayName}: $count", icon = Icons.Filled.Refresh, active = count > 0)
                }
            }
        }
        Text(strings.dryRunUpdatesTitle, fontWeight = FontWeight.Bold)
        if (plan.updates.isEmpty()) {
            EmptyState(strings.noExternalProgressUpdatesText)
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(plan.updates, key = { update -> "${update.mapping.localSeriesId}-${update.mapping.animeId.provider}" }) { update ->
                    ExternalSyncUpdateRow(strings = strings, update = update)
                }
            }
        }
        Text(strings.conflictsSummaryTitle, fontWeight = FontWeight.Bold)
        if (plan.conflicts.isEmpty()) {
            EmptyState(strings.noExternalProgressConflictsText)
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(plan.conflicts, key = { conflict -> "${conflict.mapping.localSeriesId}-${conflict.mapping.animeId.provider}" }) { conflict ->
                    ExternalSyncConflictRow(strings = strings, conflict = conflict)
                }
            }
        }
        Text(strings.syncFailuresTitle, fontWeight = FontWeight.Bold)
        if (plan.failures.isEmpty()) {
            EmptyState(strings.noSyncFailuresText)
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 180.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(plan.failures, key = { failure -> "${failure.animeId.provider}-${failure.animeId.value}" }) { failure ->
                    ExternalSyncFailureRow(strings = strings, failure = failure)
                }
            }
        }
        if (plan.skipped.isNotEmpty()) {
            Text(strings.skippedLabel, fontWeight = FontWeight.Bold)
            LazyColumn(
                modifier = Modifier.heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(plan.skipped.take(40), key = { skip -> "${skip.localSeriesId}-${skip.provider}-${skip.reason}" }) { skip ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DanmakuColors.SurfaceRaised, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = DanmakuColors.TextMuted)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(skip.provider?.displayName ?: strings.externalProviderLabel, color = Color.White, maxLines = 1)
                            Text(skip.reason.localizedLabel(strings), color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(skip.localSeriesId, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (plan.skipped.size > 40) {
                Text(strings.moreSkippedLabel(plan.skipped.size - 40), color = DanmakuColors.TextMuted)
            }
        }
    }
}

@Composable
internal fun ExternalSyncConflictRow(
    strings: DesktopStrings,
    conflict: ExternalAnimeTrackingPlanConflict,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DanmakuColors.SurfaceRaised, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = DanmakuColors.Warning)
        Column(modifier = Modifier.weight(1f)) {
            Text(conflict.series.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${conflict.mapping.animeId.provider.displayName} #${conflict.mapping.animeId.value}",
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(conflict.reason.localizedLabel(strings), color = DanmakuColors.Warning, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(strings.localWatchedEpisodesLabel(conflict.localUpdate.watchedEpisodes ?: 0), color = DanmakuColors.TextMuted, maxLines = 1)
            Text(strings.externalWatchedEpisodesLabel(conflict.externalEntry.watchedEpisodes ?: 0), color = DanmakuColors.Warning, maxLines = 1)
        }
    }
}

@Composable
internal fun ExternalSyncFailureRow(
    strings: DesktopStrings,
    failure: ExternalAnimeSyncFailure,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DanmakuColors.SurfaceRaised, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = DanmakuColors.Warning)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${failure.animeId.provider.displayName} #${failure.animeId.value}",
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(failure.message, color = DanmakuColors.Warning, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(strings.syncAttemptLabel(failure.attemptCount), color = DanmakuColors.TextMuted, maxLines = 1)
            Text(strings.retryAtLabel(failure.retryAfterEpochMs), color = DanmakuColors.TextMuted, maxLines = 1)
        }
    }
}

@Composable
internal fun ExternalSyncUpdateRow(
    strings: DesktopStrings,
    update: ExternalAnimeTrackingPlanUpdate,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DanmakuColors.SurfaceRaised, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = DanmakuColors.Good)
        Column(modifier = Modifier.weight(1f)) {
            Text(update.series.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${update.mapping.animeId.provider.displayName} #${update.mapping.animeId.value}",
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(update.update.status.localizedLabel(strings), color = DanmakuColors.Good, maxLines = 1)
            Text(
                strings.watchedEpisodeProgressLabel(update.update.watchedEpisodes ?: 0, update.series.episodeCount),
                color = DanmakuColors.TextMuted,
                maxLines = 1,
            )
        }
    }
}
