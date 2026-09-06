package app.danmaku.tv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.danmaku.updater.android.AppUpdateState
import app.danmaku.updater.android.AvailableAppUpdate
import org.junit.Rule
import org.junit.Test

class TvAppUpdateUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun availableUpdateDefaultsFocusToLater() {
        composeRule.setContent {
            DanmakuTvTheme {
                TvAppUpdateDialog(
                    state = AppUpdateState.Available(update()),
                    permissionRequired = false,
                    installerUnavailable = false,
                    onDownload = {},
                    onRetry = {},
                    onInstall = {},
                    onOpenPermissionSettings = {},
                    onLater = {},
                )
            }
        }

        composeRule.onNodeWithText("Update now").assertIsDisplayed()
        composeRule.onNodeWithText("Later").assertIsDisplayed().assertIsFocused()
    }

    private fun update() = AvailableAppUpdate(
        releaseTag = "v0.2.0",
        versionName = "0.2.0",
        versionCode = 2_000,
        releasePageUrl = "https://github.com/Energy0124/Danmaku/releases/tag/v0.2.0",
        assetName = "danmaku-android-tv.apk",
        apkUrl = "https://github.com/Energy0124/Danmaku/releases/download/v0.2.0/danmaku-android-tv.apk",
        sha256 = "b".repeat(64),
        sizeBytes = 10,
    )
}
