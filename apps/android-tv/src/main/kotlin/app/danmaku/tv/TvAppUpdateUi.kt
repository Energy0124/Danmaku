package app.danmaku.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import app.danmaku.updater.android.AppUpdateFailureStage
import app.danmaku.updater.android.AppUpdateState

@Composable
internal fun TvAppUpdateCard(
    navigation: TvNavigationState,
    navigator: TvNavigator,
    state: AppUpdateState,
    currentVersionName: String,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        colors = SurfaceDefaults.colors(containerColor = TvSurfaceRaised),
        modifier = Modifier.testTag("app-update-card"),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.update_card_title))
            Text(stringResource(R.string.update_current_version, currentVersionName), color = TvSecondaryContent)
            Text(tvUpdateStatusText(state), color = TvSecondaryContent)
            when (state) {
                is AppUpdateState.Available -> Button(
                    onClick = onDownload,
                    modifier = Modifier.tvRouteFocus(navigation, navigator, TvRoute.Pc, "app-update-download"),
                ) { Text(stringResource(R.string.update_now)) }
                is AppUpdateState.Ready -> Button(
                    onClick = { onInstall(state.apkPath) },
                    modifier = Modifier.tvRouteFocus(navigation, navigator, TvRoute.Pc, "app-update-install"),
                ) { Text(stringResource(R.string.update_install)) }
                is AppUpdateState.Failed -> Button(
                    onClick = if (state.update == null) onCheck else onDownload,
                    modifier = Modifier.tvRouteFocus(navigation, navigator, TvRoute.Pc, "app-update-retry"),
                ) { Text(stringResource(R.string.update_retry)) }
                else -> Button(
                    onClick = onCheck,
                    enabled = state !is AppUpdateState.Checking &&
                        state !is AppUpdateState.Downloading &&
                        state !is AppUpdateState.Disabled,
                    modifier = Modifier.tvRouteFocus(navigation, navigator, TvRoute.Pc, "app-update-check"),
                ) { Text(stringResource(R.string.update_check)) }
            }
        }
    }
}

@Composable
internal fun TvAppUpdateDialog(
    state: AppUpdateState,
    permissionRequired: Boolean,
    installerUnavailable: Boolean,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    onInstall: (String) -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onLater: () -> Unit,
) {
    if (
        state !is AppUpdateState.Available &&
        state !is AppUpdateState.Downloading &&
        state !is AppUpdateState.Ready &&
        state !is AppUpdateState.Failed
    ) return

    val defaultFocus = remember(state::class) { FocusRequester() }
    LaunchedEffect(state::class) {
        if (state !is AppUpdateState.Downloading) defaultFocus.requestFocus()
    }
    Dialog(
        onDismissRequest = {
            if (state !is AppUpdateState.Downloading) onLater()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            colors = SurfaceDefaults.colors(containerColor = TvSurfaceRaised),
        ) {
            Column(
                modifier = Modifier.padding(28.dp).testTag("app-update-dialog"),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                when (state) {
                    is AppUpdateState.Available -> {
                        Text(stringResource(R.string.update_available_title))
                        Text(stringResource(R.string.update_available_body, state.update.versionName))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onLater, modifier = Modifier.focusRequester(defaultFocus)) {
                                Text(stringResource(R.string.update_later))
                            }
                            Button(onClick = onDownload) { Text(stringResource(R.string.update_now)) }
                        }
                    }
                    is AppUpdateState.Downloading -> {
                        Text(stringResource(R.string.update_downloading_title))
                        Text(
                            stringResource(
                                R.string.update_downloading_progress,
                                updateProgressPercent(state.downloadedBytes, state.update.sizeBytes),
                            ),
                        )
                    }
                    is AppUpdateState.Ready -> {
                        Text(
                            if (permissionRequired) {
                                stringResource(R.string.update_permission_title)
                            } else {
                                stringResource(R.string.update_ready_title)
                            },
                        )
                        Text(
                            when {
                                permissionRequired -> stringResource(R.string.update_permission_body)
                                installerUnavailable -> stringResource(R.string.update_installer_unavailable)
                                else -> stringResource(R.string.update_ready_body)
                            },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onLater, modifier = Modifier.focusRequester(defaultFocus)) {
                                Text(stringResource(R.string.update_later))
                            }
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
                        }
                    }
                    is AppUpdateState.Failed -> {
                        Text(stringResource(R.string.update_failed_title))
                        Text(tvUpdateFailureText(state.stage))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onLater, modifier = Modifier.focusRequester(defaultFocus)) {
                                Text(stringResource(R.string.update_later))
                            }
                            Button(onClick = onRetry) { Text(stringResource(R.string.update_retry)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun tvUpdateStatusText(state: AppUpdateState): String = when (state) {
    AppUpdateState.Disabled -> stringResource(R.string.update_disabled)
    AppUpdateState.Idle -> stringResource(R.string.update_ready_to_check)
    AppUpdateState.Checking -> stringResource(R.string.update_checking)
    is AppUpdateState.Current -> stringResource(R.string.update_current)
    is AppUpdateState.Available -> stringResource(R.string.update_available_status, state.update.versionName)
    is AppUpdateState.Downloading -> stringResource(
        R.string.update_downloading_progress,
        updateProgressPercent(state.downloadedBytes, state.update.sizeBytes),
    )
    is AppUpdateState.Ready -> stringResource(R.string.update_ready_status, state.update.versionName)
    is AppUpdateState.Failed -> tvUpdateFailureText(state.stage)
}

@Composable
private fun tvUpdateFailureText(stage: AppUpdateFailureStage): String = when (stage) {
    AppUpdateFailureStage.CHECK -> stringResource(R.string.update_check_failed)
    AppUpdateFailureStage.DOWNLOAD -> stringResource(R.string.update_download_failed)
    AppUpdateFailureStage.VERIFY -> stringResource(R.string.update_verify_failed)
}

private fun updateProgressPercent(downloadedBytes: Long, sizeBytes: Long): Int =
    if (sizeBytes <= 0) 0 else ((downloadedBytes * 100) / sizeBytes).toInt().coerceIn(0, 100)
