package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LibraryEpisodeDetailTest {
    @Test
    fun returnsSeriesSeasonNeighborsAndWatchState() {
        val catalog = catalogOf(
            item("one", episodeTitle = "Episode 01"),
            item("two", episodeTitle = "Episode 02"),
            item("three", episodeTitle = "Episode 03"),
        )

        val detail = catalog.episodeDetail(
            mediaId = "two",
            progresses = listOf(
                progress("two", positionMs = 90_000, durationMs = 1_200_000, updatedAtEpochMs = 10),
            ),
        )

        requireNotNull(detail)
        assertEquals("two", detail.mediaItem.id)
        assertEquals("Example Show", detail.series.title)
        assertEquals("Season unknown", detail.season.label)
        assertEquals(LibraryWatchState.IN_PROGRESS, detail.watchStatus.state)
        assertEquals("one", detail.previousItem?.id)
        assertEquals("three", detail.nextItem?.id)
    }

    @Test
    fun returnsNullForUnknownMediaId() {
        assertNull(catalogOf(item("one")).episodeDetail("missing"))
    }

    @Test
    fun validatesMediaId() {
        assertFailsWith<IllegalArgumentException> {
            catalogOf(item("one")).episodeDetail("")
        }
    }

    private fun catalogOf(vararg items: LibraryMediaItem): LibraryCatalog =
        LibraryCatalog(
            rootName = "Anime",
            indexedAtEpochMs = 123,
            items = items.toList(),
        )

    private fun item(
        id: String,
        episodeTitle: String = "Episode $id",
    ): LibraryMediaItem =
        LibraryMediaItem(
            id = id,
            seriesTitle = "Example Show",
            episodeTitle = episodeTitle,
            relativePath = "Example Show/$episodeTitle.mkv",
            sizeBytes = 123,
            mediaType = "video/x-matroska",
            streamPath = "/media/$id",
        )

    private fun progress(
        mediaId: String,
        positionMs: Long,
        durationMs: Long?,
        updatedAtEpochMs: Long,
    ): PlaybackProgress =
        PlaybackProgress(
            mediaId = mediaId,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAtEpochMs = updatedAtEpochMs,
        )
}
