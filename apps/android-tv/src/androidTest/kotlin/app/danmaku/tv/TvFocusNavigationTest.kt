package app.danmaku.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvFocusNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun startsWithDiscoverPcFocused() {
        composeRule.onNodeWithText("Discover PC").assertIsFocused()
    }

    @Test
    fun dpadLeftMovesFromDiscoverToRefresh() {
        composeRule
            .onNodeWithText("Discover PC")
            .assertIsFocused()
            .performKeyInput {
                pressKey(Key.DirectionLeft)
            }

        composeRule.onNodeWithText("Refresh PC library").assertIsFocused()
    }
}
