package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.danmaku.domain.LibraryCatalog

@Composable
internal fun TvHomeStatusPanel(
    catalog: LibraryCatalog?,
    onShowPc: () -> Unit,
    onShowLibrary: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(TvPanelRaisedColor)
            .padding(14.dp)
            .testTag("home-operational-status"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.home_operational_status),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        TvRailPill(
            if (catalog == null) {
                stringResource(R.string.pc_offline)
            } else {
                stringResource(R.string.pc_ready)
            },
            active = catalog != null,
        )
        Button(
            onClick = if (catalog == null) onShowPc else onShowLibrary,
            modifier = Modifier.tvFocusHalo(RoundedCornerShape(16.dp)),
        ) {
            Text(
                if (catalog == null) {
                    stringResource(R.string.action_open_pc)
                } else {
                    stringResource(R.string.action_open_library)
                },
            )
        }
    }
}
