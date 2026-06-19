package app.danmaku.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibraryQualityIssue
import app.danmaku.domain.LibraryQualityIssueSeverity
import app.danmaku.domain.LibraryQualityIssueType
import app.danmaku.domain.LibraryQualityReport
import app.danmaku.domain.stableKey

@Composable
internal fun LibraryQualityReviewView(
    strings: DesktopStrings,
    catalog: LibraryCatalog?,
    report: LibraryQualityReport?,
    decisionByKey: Map<String, DesktopLibraryQualityIssueDecision>,
    onSetDecision: (LibraryQualityIssue, DesktopLibraryQualityIssueDecisionState?) -> Unit,
    onApplyMappings: (LibraryQualityIssue) -> Unit,
    onReviewItem: (LibraryMediaItem) -> Unit,
) {
    if (report == null) {
        EmptyState(strings.libraryQualityNoLibraryText)
        return
    }
    val mediaItemById = remember(catalog) {
        catalog?.items?.associateBy(LibraryMediaItem::id).orEmpty()
    }
    var showHandledIssues by remember { mutableStateOf(false) }
    val openIssues = remember(report, decisionByKey) {
        report.issues.filter { issue -> issue.stableKey() !in decisionByKey }
    }
    val handledIssues = remember(report, decisionByKey) {
        report.issues.filter { issue -> issue.stableKey() in decisionByKey }
    }
    val visibleIssues = if (showHandledIssues) report.issues else openIssues

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard(
                title = strings.libraryQualityTotalTitle,
                value = openIssues.size.toString(),
                caption = strings.libraryQualityTotalCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.libraryQualityReviewTitle,
                value = openIssues.count { it.severity == LibraryQualityIssueSeverity.REVIEW }.toString(),
                caption = strings.libraryQualityReviewCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.libraryQualityWarningTitle,
                value = openIssues.count { it.severity == LibraryQualityIssueSeverity.WARNING }.toString(),
                caption = strings.libraryQualityWarningCaption,
                modifier = Modifier.weight(1f),
            )
        }

        if (report.issues.isEmpty()) {
            EmptyState(strings.libraryQualityEmptyText)
            return@Column
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusPill(
                strings.libraryQualityOpenCountSummary(openIssues.size),
                active = openIssues.isNotEmpty(),
                color = if (openIssues.isNotEmpty()) DanmakuColors.Warning else DanmakuColors.TextMuted,
            )
            StatusPill(strings.libraryQualityHandledCountSummary(handledIssues.size))
            Spacer(modifier = Modifier.weight(1f))
            if (handledIssues.isNotEmpty()) {
                TextButton(onClick = { showHandledIssues = !showHandledIssues }) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        if (showHandledIssues) {
                            strings.libraryQualityHideHandledAction
                        } else {
                            strings.libraryQualityShowHandledAction
                        },
                    )
                }
            }
        }

        if (visibleIssues.isEmpty()) {
            EmptyState(strings.libraryQualityAllHandledText)
            return@Column
        }

        LazyColumn(
            modifier = Modifier.heightIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                visibleIssues,
                key = { issue -> issue.stableKey() },
            ) { issue ->
                LibraryQualityIssueRow(
                    strings = strings,
                    issue = issue,
                    reviewItem = issue.firstExistingItem(mediaItemById),
                    canApplyMappings = catalog?.let { issue.libraryQualityMappingApplyPlan(it, 0) } != null,
                    decision = decisionByKey[issue.stableKey()],
                    onSetDecision = onSetDecision,
                    onApplyMappings = onApplyMappings,
                    onReviewItem = onReviewItem,
                )
            }
        }
    }
}

@Composable
private fun LibraryQualityIssueRow(
    strings: DesktopStrings,
    issue: LibraryQualityIssue,
    reviewItem: LibraryMediaItem?,
    canApplyMappings: Boolean,
    decision: DesktopLibraryQualityIssueDecision?,
    onSetDecision: (LibraryQualityIssue, DesktopLibraryQualityIssueDecisionState?) -> Unit,
    onApplyMappings: (LibraryQualityIssue) -> Unit,
    onReviewItem: (LibraryMediaItem) -> Unit,
) {
    val issueColor = issue.severity.issueColor()
    val stateColor = decision?.state?.decisionColor() ?: issueColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DanmakuColors.SurfaceRaised, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (issue.severity == LibraryQualityIssueSeverity.WARNING) {
                Icons.Filled.Warning
            } else {
                Icons.Filled.CheckCircle
            },
            contentDescription = null,
            tint = issueColor,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    strings.libraryQualityIssueTypeLabel(issue.type),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(
                    text = decision?.state?.let(strings::libraryQualityDecisionStateLabel)
                        ?: strings.libraryQualitySeverityLabel(issue.severity),
                    active = true,
                    color = stateColor,
                )
            }
            Text(
                issue.seriesTitle,
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                strings.libraryQualityAffectedFilesLabel(issue.relativePaths.size),
                color = DanmakuColors.TextMuted,
                maxLines = 1,
            )
            strings.libraryQualityIssueGuidance(issue.type)?.let { guidance ->
                Text(
                    guidance,
                    color = DanmakuColors.Info,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            issue.relativePaths.take(3).forEach { path ->
                Text(
                    path,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (issue.relativePaths.size > 3) {
                Text(
                    strings.moreItemsLabel(issue.relativePaths.size - 3),
                    color = DanmakuColors.TextMuted,
                    maxLines = 1,
                )
            }
            if (issue.evidence.isNotEmpty()) {
                Text(
                    strings.libraryQualityEvidenceLabel,
                    color = DanmakuColors.TextMuted,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                issue.evidence.take(4).forEach { evidence ->
                    Text(
                        evidence,
                        color = DanmakuColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (issue.evidence.size > 4) {
                    Text(
                        strings.moreItemsLabel(issue.evidence.size - 4),
                        color = DanmakuColors.TextMuted,
                        maxLines = 1,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                reviewItem?.let { item ->
                    TextButton(onClick = { onReviewItem(item) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ViewList,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(strings.libraryQualityOpenInspectorAction)
                    }
                }
                if (decision == null) {
                    if (canApplyMappings) {
                        Button(
                            onClick = { onApplyMappings(issue) },
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(strings.libraryQualityApplyMappingsAction)
                        }
                    }
                    TextButton(
                        onClick = { onSetDecision(issue, DesktopLibraryQualityIssueDecisionState.IGNORED) },
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(strings.libraryQualityIgnoreAction)
                    }
                    if (!canApplyMappings) {
                        Button(
                            onClick = { onSetDecision(issue, DesktopLibraryQualityIssueDecisionState.RESOLVED) },
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(strings.libraryQualityResolveAction)
                        }
                    }
                } else {
                    TextButton(onClick = { onSetDecision(issue, null) }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(strings.libraryQualityReopenAction)
                    }
                }
            }
        }
    }
}

private fun LibraryQualityIssueSeverity.issueColor(): Color =
    when (this) {
        LibraryQualityIssueSeverity.REVIEW -> DanmakuColors.Accent
        LibraryQualityIssueSeverity.WARNING -> DanmakuColors.Warning
    }

private fun DesktopLibraryQualityIssueDecisionState.decisionColor(): Color =
    when (this) {
        DesktopLibraryQualityIssueDecisionState.IGNORED -> DanmakuColors.TextMuted
        DesktopLibraryQualityIssueDecisionState.RESOLVED -> DanmakuColors.Accent
    }

private fun LibraryQualityIssue.firstExistingItem(
    mediaItemById: Map<String, LibraryMediaItem>,
): LibraryMediaItem? =
    mediaItemIds
        .asSequence()
        .mapNotNull(mediaItemById::get)
        .firstOrNull()
