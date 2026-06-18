package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import app.danmaku.library.LanLibraryConnectionProfile

@Composable
internal fun TvSavedConnectionCard(
    connection: LanLibraryConnectionProfile,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onForget: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(320.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Color(0xFF273747) else TvCardColor)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            connection.displayName,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(connection.normalizedBaseUrl, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSelect,
                modifier = Modifier
                    .tvFocusHalo(RoundedCornerShape(16.dp))
                    .testTag("saved-connection:${connection.id}"),
            ) {
                Text(
                    if (isSelected) {
                        stringResource(R.string.status_selected)
                    } else {
                        stringResource(R.string.action_open)
                    },
                )
            }
            Button(
                onClick = onForget,
                modifier = Modifier
                    .tvFocusHalo(RoundedCornerShape(16.dp))
                    .testTag("saved-connection-forget:${connection.id}"),
            ) {
                Text(stringResource(R.string.action_forget))
            }
        }
    }
}
