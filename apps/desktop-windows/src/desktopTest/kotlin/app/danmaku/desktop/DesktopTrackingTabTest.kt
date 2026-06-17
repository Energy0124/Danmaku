package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeListEntry
import app.danmaku.domain.ExternalAnimeListStatus
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeMappingSource
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.externalAnimeTrackingPlan
import app.danmaku.domain.groupedSeries
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopTrackingTabTest {
    @Test
    fun trackingRowsExposeProviderReadbackStatusAndScoreForUpdates() {
        val catalog = trackingCatalog()
        val series = catalog.groupedSeries().single()
        val animeId = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 52991)
        val mapping = externalMapping(series.id, animeId)
        val externalEntry = ExternalAnimeListEntry(
            animeId = animeId,
            status = ExternalAnimeListStatus.WATCHING,
            watchedEpisodes = 1,
            score = 8,
        )
        val plan = catalog.externalAnimeTrackingPlan(
            mappings = listOf(mapping),
            progresses = listOf(watchedProgress("episode-1")),
            externalEntries = listOf(externalEntry),
            providers = setOf(ExternalAnimeProvider.MY_ANIME_LIST),
        )

        val row = buildTrackingTableRows(
            strings = trackingTestStrings(),
            plan = plan,
            mappings = listOf(mapping),
            externalEntries = listOf(externalEntry),
            seriesById = mapOf(series.id to series),
        ).single()

        assertEquals(TrackingRowKind.UPDATE, row.kind)
        assertEquals("1/2, Watching", row.providerProgress)
        assertEquals("Watching", row.providerListStatus)
        assertEquals("8/10", row.providerScore)
    }

    @Test
    fun trackingRowsExposeProviderReadbackStatusAndScoreForConflicts() {
        val catalog = trackingCatalog()
        val series = catalog.groupedSeries().single()
        val animeId = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 52991)
        val mapping = externalMapping(series.id, animeId)
        val externalEntry = ExternalAnimeListEntry(
            animeId = animeId,
            status = ExternalAnimeListStatus.COMPLETED,
            watchedEpisodes = 2,
            score = 10,
        )
        val plan = catalog.externalAnimeTrackingPlan(
            mappings = listOf(mapping),
            progresses = listOf(watchedProgress("episode-1")),
            externalEntries = listOf(externalEntry),
            providers = setOf(ExternalAnimeProvider.MY_ANIME_LIST),
        )

        val row = buildTrackingTableRows(
            strings = trackingTestStrings(),
            plan = plan,
            mappings = listOf(mapping),
            externalEntries = listOf(externalEntry),
            seriesById = mapOf(series.id to series),
        ).single()

        assertEquals(TrackingRowKind.CONFLICT, row.kind)
        assertEquals("2/2", row.providerProgress)
        assertEquals("Completed", row.providerListStatus)
        assertEquals("10/10", row.providerScore)
    }

    @Test
    fun trackingRowsDistinguishExistingUnchangedReadbackFromPendingReadback() {
        val catalog = trackingCatalog()
        val series = catalog.groupedSeries().single()
        val animeId = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 52991)
        val mapping = externalMapping(series.id, animeId)
        val externalEntry = ExternalAnimeListEntry(
            animeId = animeId,
            watchedEpisodes = 1,
        )
        val plan = catalog.externalAnimeTrackingPlan(
            mappings = listOf(mapping),
            progresses = listOf(watchedProgress("episode-1")),
            externalEntries = listOf(externalEntry),
            providers = setOf(ExternalAnimeProvider.MY_ANIME_LIST),
        )

        val row = buildTrackingTableRows(
            strings = trackingTestStrings(),
            plan = plan,
            mappings = listOf(mapping),
            externalEntries = listOf(externalEntry),
            seriesById = mapOf(series.id to series),
        ).single()

        assertEquals("1/2, Unchanged", row.providerProgress)
        assertEquals("Unchanged", row.providerListStatus)
        assertEquals("None", row.providerScore)
    }

    private fun trackingTestStrings(): DesktopStrings =
        desktopStrings {
            noneLabel = "None"
            externalListWatchingStatus = "Watching"
            externalListCompletedStatus = "Completed"
            externalListOnHoldStatus = "On hold"
            externalListDroppedStatus = "Dropped"
            externalListPlanToWatchStatus = "Plan to watch"
            externalListUnchangedStatus = "Unchanged"
            readbackPendingLabel = "Readback pending"
            watchedCountLabel = { count -> "$count watched" }
            readyStatusLabel = "Ready"
            reviewConflictAction = "Review conflict"
            conflictStatusLabel = "Conflict"
        }

    private fun trackingCatalog(): LibraryCatalog =
        LibraryCatalog(
            rootName = "Anime",
            indexedAtEpochMs = 123,
            items = listOf(
                libraryMediaItem("episode-1", "Episode 01"),
                libraryMediaItem("episode-2", "Episode 02"),
            ),
        )

    private fun libraryMediaItem(id: String, episodeTitle: String): LibraryMediaItem =
        LibraryMediaItem(
            id = id,
            seriesTitle = "Frieren",
            episodeTitle = episodeTitle,
            relativePath = "$episodeTitle.mkv",
            sizeBytes = 1_000,
            mediaType = "video/x-matroska",
            streamPath = "/library/items/$id/stream",
        )

    private fun externalMapping(localSeriesId: String, animeId: ExternalAnimeId): ExternalAnimeMapping =
        ExternalAnimeMapping(
            localSeriesId = localSeriesId,
            animeId = animeId,
            source = ExternalAnimeMappingSource.MANUAL,
            confidence = 1.0,
            mappedAtEpochMs = 123,
        )

    private fun watchedProgress(mediaId: String): PlaybackProgress =
        PlaybackProgress(
            mediaId = mediaId,
            positionMs = 590_000,
            durationMs = 600_000,
            updatedAtEpochMs = 123,
        )
}
