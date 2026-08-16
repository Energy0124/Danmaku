package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
internal fun TvOnboardingScreen(
    navigation: TvNavigationState,
    navigator: TvNavigator,
    isDiscovering: Boolean,
    errorMessage: String?,
    onDiscover: () -> Unit,
    onOpenPc: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.onboarding_eyebrow),
            color = TvAccent,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.onboarding_title),
            color = TvContent,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = stringResource(R.string.onboarding_body),
            color = TvSecondaryContent,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(680.dp)
                .padding(top = 16.dp, bottom = 28.dp),
        )
        Button(
            onClick = onDiscover,
            enabled = !isDiscovering,
            modifier = Modifier
                .width(280.dp)
                .tvRouteFocus(
                    navigation,
                    navigator,
                    TvRoute.Onboarding,
                    "onboarding-discover",
                    isDefault = true,
                )
                .tvFocusHalo(RoundedCornerShape(20.dp))
                .testTag("onboarding-discover"),
            colors = tvButtonColors(selected = true),
            scale = tvButtonScale(),
        ) {
            Text(
                if (isDiscovering) {
                    stringResource(R.string.status_discovering)
                } else {
                    stringResource(R.string.action_discover_pc)
                },
            )
        }
        Button(
            onClick = onOpenPc,
            modifier = Modifier
                .width(280.dp)
                .padding(top = 12.dp)
                .tvRouteFocus(
                    navigation,
                    navigator,
                    TvRoute.Onboarding,
                    "onboarding-manual",
                )
                .tvFocusHalo(RoundedCornerShape(20.dp))
                .testTag("onboarding-manual"),
            colors = tvButtonColors(),
            scale = tvButtonScale(),
        ) {
            Text(stringResource(R.string.action_manual_connection))
        }
        errorMessage?.let {
            Text(
                text = it,
                color = TvError,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}
