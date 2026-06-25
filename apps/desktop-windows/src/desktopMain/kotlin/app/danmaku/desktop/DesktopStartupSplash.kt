package app.danmaku.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal data class DesktopStartupProgress(
    val stage: String,
    val detail: String,
    val progress: Float,
    val isVisible: Boolean = true,
) {
    init {
        require(progress in 0f..1f) { "progress must be between 0 and 1" }
    }

    companion object {
        val Initial = DesktopStartupProgress(
            stage = "Starting Danmaku",
            detail = "Preparing desktop shell",
            progress = 0.04f,
        )

        val Hidden = DesktopStartupProgress(
            stage = "Ready",
            detail = "",
            progress = 1f,
            isVisible = false,
        )
    }
}

internal data class DesktopStartupTiming(
    val stage: String,
    val elapsedMillis: Long,
)

@Composable
internal fun DesktopStartupSplash(
    progress: DesktopStartupProgress,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DanmakuColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 560.dp)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "Danmaku",
                style = MaterialTheme.typography.h4,
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = progress.stage,
                style = MaterialTheme.typography.subtitle1,
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LinearProgressIndicator(
                progress = progress.progress,
                modifier = Modifier.fillMaxWidth(),
                color = DanmakuColors.Accent,
                backgroundColor = DanmakuColors.SurfaceRaised,
            )
            Text(
                text = progress.detail,
                style = MaterialTheme.typography.body2,
                color = DanmakuColors.TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}