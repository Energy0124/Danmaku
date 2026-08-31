package app.danmaku.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import app.danmaku.updater.android.AppUpdateState
import app.danmaku.updater.android.AvailableAppUpdate
import org.junit.Rule
import org.junit.Test

class MobileAppUpdateUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun availableUpdateShowsLocalizedActions() {
        composeRule.setContent {
            MaterialTheme {
                MobileAppUpdateDialog(
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

        composeRule.onNodeWithTag("app-update-available-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Update now").assertIsDisplayed()
        composeRule.onNodeWithText("Later").assertIsDisplayed()
    }

    @Test
    fun updateCardShowsInstalledVersionAndCurrentState() {
        composeRule.setContent {
            MaterialTheme {
                MobileAppUpdateCard(
                    state = AppUpdateState.Current("0.1.0"),
                    currentVersionName = "0.1.0",
                    onCheck = {},
                    onDownload = {},
                    onInstall = {},
                )
            }
        }

        composeRule.onNodeWithTag("app-update-card").assertIsDisplayed()
        composeRule.onNodeWithText("Installed version: 0.1.0").assertIsDisplayed()
        composeRule.onNodeWithText("You’re using the latest version.").assertIsDisplayed()
    }

    private fun update() = AvailableAppUpdate(
        releaseTag = "v0.2.0",
        versionName = "0.2.0",
        versionCode = 2_000,
        releasePageUrl = "https://github.com/Energy0124/Danmaku/releases/tag/v0.2.0",
        assetName = "danmaku-android-mobile.apk",
        apkUrl = "https://github.com/Energy0124/Danmaku/releases/download/v0.2.0/danmaku-android-mobile.apk",
        sha256 = "a".repeat(64),
        sizeBytes = 10,
    )
}
