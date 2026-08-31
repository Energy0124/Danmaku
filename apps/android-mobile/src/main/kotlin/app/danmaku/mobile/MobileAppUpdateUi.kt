package app.danmaku.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.danmaku.updater.android.AppUpdateFailureStage
import app.danmaku.updater.android.AppUpdateState

@Composable
internal fun MobileAppUpdateCard(
    state: AppUpdateState,
    currentVersionName: String,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("app-update-card"),
        shape = RoundedCornerShape(20.dp),
        color = PanelAltColor,
        border = BorderStroke(1.dp, Color(0xFF343D45)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.update_card_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.update_current_version, currentVersionName),
                color = SubtleText,
            )
            Text(updateStatusText(state), color = SubtleText)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state) {
                    is AppUpdateState.Available -> Button(onClick = onDownload) {
                        Text(stringResource(R.string.update_now))
                    }
                    is AppUpdateState.Ready -> Button(onClick = { onInstall(state.apkPath) }) {
                        Text(stringResource(R.string.update_install))
                    }
                    is AppUpdateState.Failed -> Button(
                        onClick = if (state.update == null) onCheck else onDownload,
                    ) {
                        Text(stringResource(R.string.update_retry))
                    }
                    else -> OutlinedButton(
                        onClick = onCheck,
                        enabled = state !is AppUpdateState.Checking &&
                            state !is AppUpdateState.Downloading &&
                            state !is AppUpdateState.Disabled,
                    ) {
                        Text(stringResource(R.string.update_check))
                    }
                }
            }
        }
    }
}

@Composable
internal fun MobileAppUpdateDialog(
    state: AppUpdateState,
    permissionRequired: Boolean,
    installerUnavailable: Boolean,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    onInstall: (String) -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onLater: () -> Unit,
) {
    when (state) {
        is AppUpdateState.Available -> AlertDialog(
            onDismissRequest = onLater,
            modifier = Modifier.testTag("app-update-available-dialog"),
            title = { Text(stringResource(R.string.update_available_title)) },
            text = { Text(stringResource(R.string.update_available_body, state.update.versionName)) },
            confirmButton = {
                Button(onClick = onDownload) { Text(stringResource(R.string.update_now)) }
            },
            dismissButton = {
                TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) }
            },
        )
        is AppUpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_downloading_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val progress = updateProgress(state.downloadedBytes, state.update.sizeBytes)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.update_downloading_progress, (progress * 100).toInt()))
                }
            },
            confirmButton = {},
        )
        is AppUpdateState.Ready -> AlertDialog(
            onDismissRequest = onLater,
            title = {
                Text(
                    if (permissionRequired) {
                        stringResource(R.string.update_permission_title)
                    } else {
                        stringResource(R.string.update_ready_title)
                    },
                )
            },
            text = {
                Text(
                    when {
                        permissionRequired -> stringResource(R.string.update_permission_body)
                        installerUnavailable -> stringResource(R.string.update_installer_unavailable)
                        else -> stringResource(R.string.update_ready_body)
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = if (permissionRequired) {
                        onOpenPermissionSettings
                    } else {
                        { onInstall(state.apkPath) }
                    },
                ) {
                    Text(
                        if (permissionRequired) {
                            stringResource(R.string.update_open_settings)
                        } else {
                            stringResource(R.string.update_install)
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) }
            },
        )
        is AppUpdateState.Failed -> AlertDialog(
            onDismissRequest = onLater,
            title = { Text(stringResource(R.string.update_failed_title)) },
            text = { Text(updateFailureText(state.stage)) },
            confirmButton = {
                Button(onClick = onRetry) { Text(stringResource(R.string.update_retry)) }
            },
            dismissButton = {
                TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) }
            },
        )
        else -> Unit
    }
}

@Composable
private fun updateStatusText(state: AppUpdateState): String = when (state) {
    AppUpdateState.Disabled -> stringResource(R.string.update_disabled)
    AppUpdateState.Idle -> stringResource(R.string.update_ready_to_check)
    AppUpdateState.Checking -> stringResource(R.string.update_checking)
    is AppUpdateState.Current -> stringResource(R.string.update_current)
    is AppUpdateState.Available -> stringResource(R.string.update_available_status, state.update.versionName)
    is AppUpdateState.Downloading -> stringResource(
        R.string.update_downloading_progress,
        (updateProgress(state.downloadedBytes, state.update.sizeBytes) * 100).toInt(),
    )
    is AppUpdateState.Ready -> stringResource(R.string.update_ready_status, state.update.versionName)
    is AppUpdateState.Failed -> updateFailureText(state.stage)
}

@Composable
private fun updateFailureText(stage: AppUpdateFailureStage): String = when (stage) {
    AppUpdateFailureStage.CHECK -> stringResource(R.string.update_check_failed)
    AppUpdateFailureStage.DOWNLOAD -> stringResource(R.string.update_download_failed)
    AppUpdateFailureStage.VERIFY -> stringResource(R.string.update_verify_failed)
}

private fun updateProgress(downloadedBytes: Long, sizeBytes: Long): Float =
    if (sizeBytes <= 0) 0f else (downloadedBytes.toDouble() / sizeBytes.toDouble()).toFloat().coerceIn(0f, 1f)
