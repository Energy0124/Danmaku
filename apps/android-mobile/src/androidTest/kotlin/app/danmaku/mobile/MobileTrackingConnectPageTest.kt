package app.danmaku.mobile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeListStatus
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeMappingSource
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeTrackingUpdate
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.library.android.ExternalTrackingDocument
import app.danmaku.library.android.ExternalTrackingPlan
import app.danmaku.library.android.ExternalTrackingPlanSummary
import app.danmaku.library.android.ExternalTrackingPlanUpdate
import app.danmaku.library.android.ProviderAccountState
import app.danmaku.library.android.ProviderAccountStatus
import app.danmaku.library.android.ProviderAccountsDocument
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MobileTrackingConnectPageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun freshPreviewRequiresConfirmationAndShowsExactUpdate() {
        var syncCount = 0
        composeRule.setContent {
            MaterialTheme {
                ConnectPage(
                    contentPadding = PaddingValues(0.dp),
                    catalog = LibraryCatalog("PC", 1, emptyList()),
                    snapshot = PlaybackSnapshot(),
                    nowPlaying = null,
                    serverUrl = "http://pc:8686",
                    savedConnections = emptyList(),
                    libraryError = null,
                    tracking = trackingState(),
                    onServerUrlChange = {},
                    onSelectConnection = {},
                    onEditConnection = {},
                    onForgetConnection = {},
                    onSaveConnection = {},
                    onDiscover = {},
                    onRefresh = {},
                    onLoadTracking = {},
                    onReadTracking = {},
                    onSyncTracking = { syncCount += 1 },
                    onPlayPause = {},
                    onOpenPlayer = {},
                )
            }
        }

        composeRule.onNodeWithTag("tracking-sync").performScrollTo().performClick()

        assertEquals(0, syncCount)
        composeRule.onNodeWithText("Sync external progress?").assertExists()
        composeRule.onNodeWithText("MyAnimeList · Watching · 3/12 watched").assertExists()

        composeRule.onNodeWithTag("tracking-confirm-sync").performClick()
        assertEquals(1, syncCount)
    }

    private fun trackingState(): MobileTrackingState {
        val animeId = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 42)
        val mapping = ExternalAnimeMapping(
            localSeriesId = "example",
            animeId = animeId,
            source = ExternalAnimeMappingSource.MANUAL,
            confidence = 1.0,
            mappedAtEpochMs = 1,
        )
        val update = ExternalAnimeTrackingUpdate(
            animeId = animeId,
            status = ExternalAnimeListStatus.WATCHING,
            watchedEpisodes = 3,
        )
        val candidate = ExternalTrackingPlanUpdate(
            localSeriesId = "example",
            localSeriesIds = listOf("example"),
            seriesTitle = "Example",
            episodeCount = 12,
            mapping = mapping,
            update = update,
        )
        return MobileTrackingState(
            accounts = ProviderAccountsDocument(
                myAnimeList = ProviderAccountStatus(
                    state = ProviderAccountState.CONNECTED,
                    displayName = "MAL user",
                ),
                bangumi = ProviderAccountStatus(ProviderAccountState.DISCONNECTED),
                bangumiTokenUrl = "https://next.bgm.tv/demo/access-token",
            ),
            document = ExternalTrackingDocument(
                generatedAtEpochMs = 1,
                series = emptyList(),
                mappings = listOf(mapping),
                listEntries = emptyList(),
                plan = ExternalTrackingPlan(
                    summary = ExternalTrackingPlanSummary(1, 0, 0, 0, 1, 0),
                    updates = listOf(candidate),
                    skipped = emptyList(),
                    conflicts = emptyList(),
                    mappingConflicts = emptyList(),
                    failures = emptyList(),
                ),
            ),
            hasFreshReadback = true,
        )
    }
}
