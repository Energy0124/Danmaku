package app.danmaku.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.danmaku.domain.LibraryQualityIssue
import app.danmaku.domain.LibraryQualityIssueSeverity
import app.danmaku.domain.LibraryQualityReport

@Composable
internal fun LibraryQualityReviewView(
    strings: DesktopStrings,
    report: LibraryQualityReport?,
) {
    if (report == null) {
        EmptyState(strings.libraryQualityNoLibraryText)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard(
                title = strings.libraryQualityTotalTitle,
                value = report.issueCount.toString(),
                caption = strings.libraryQualityTotalCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.libraryQualityReviewTitle,
                value = report.reviewCount.toString(),
                caption = strings.libraryQualityReviewCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.libraryQualityWarningTitle,
                value = report.warningCount.toString(),
                caption = strings.libraryQualityWarningCaption,
                modifier = Modifier.weight(1f),
            )
        }

        if (report.issues.isEmpty()) {
            EmptyState(strings.libraryQualityEmptyText)
            return@Column
        }

        LazyColumn(
            modifier = Modifier.heightIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                report.issues,
                key = { issue ->
                    "${issue.type}-${issue.seriesId}-${issue.relativePaths.firstOrNull().orEmpty()}"
                },
            ) { issue ->
                LibraryQualityIssueRow(
                    strings = strings,
                    issue = issue,
                )
            }
        }
        Text(
            strings.libraryQualityActionsPlannedText,
            color = DanmakuColors.TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LibraryQualityIssueRow(
    strings: DesktopStrings,
    issue: LibraryQualityIssue,
) {
    val issueColor = issue.severity.issueColor()
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
                    text = strings.libraryQualitySeverityLabel(issue.severity),
                    active = true,
                    color = issueColor,
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
        }
    }
}

private fun LibraryQualityIssueSeverity.issueColor(): Color =
    when (this) {
        LibraryQualityIssueSeverity.REVIEW -> DanmakuColors.Accent
        LibraryQualityIssueSeverity.WARNING -> DanmakuColors.Warning
    }
