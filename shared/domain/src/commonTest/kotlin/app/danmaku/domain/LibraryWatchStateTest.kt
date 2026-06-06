package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LibraryWatchStateTest {
    @Test
    fun marksCatalogItemsWithoutProgressAsNew() {
        val status = catalogOf(item("one"))
            .watchStatusByMediaId(progresses = emptyList())
            .getValue("one")

        assertEquals(LibraryWatchState.NEW, status.state)
        assertEquals(null, status.progress)
    }

    @Test
    fun marksResumableProgressAsInProgress() {
        val status = catalogOf(item("one"))
            .watchStatusByMediaId(
                progresses = listOf(
                    progress("one", positionMs = 60_000, durationMs = 1_200_000, updatedAtEpochMs = 20),
                ),
            )
            .getValue("one")

        assertEquals(LibraryWatchState.IN_PROGRESS, status.state)
        assertEquals(60_000, status.progress?.positionMs)
    }

    @Test
    fun marksNearEndProgressAsWatched() {
        val status = catalogOf(item("one"))
            .watchStatusByMediaId(
                progresses = listOf(
                    progress("one", positionMs = 1_190_000, durationMs = 1_200_000, updatedAtEpochMs = 20),
                ),
            )
            .getValue("one")

        assertEquals(LibraryWatchState.WATCHED, status.state)
    }

    @Test
    fun usesTheLatestProgressForDuplicateMediaIds() {
        val status = catalogOf(item("one"))
            .watchStatusByMediaId(
                progresses = listOf(
                    progress("one", positionMs = 1_190_000, durationMs = 1_200_000, updatedAtEpochMs = 20),
                    progress("one", positionMs = 90_000, durationMs = 1_200_000, updatedAtEpochMs = 30),
                ),
            )
            .getValue("one")

        assertEquals(LibraryWatchState.IN_PROGRESS, status.state)
        assertEquals(90_000, status.progress?.positionMs)
    }

    @Test
    fun ignoresUnknownProgressRows() {
        val statuses = catalogOf(item("one"))
            .watchStatusByMediaId(
                progresses = listOf(
                    progress("missing", positionMs = 90_000, durationMs = 1_200_000, updatedAtEpochMs = 30),
                ),
            )

        assertEquals(setOf("one"), statuses.keys)
        assertEquals(LibraryWatchState.NEW, statuses.getValue("one").state)
    }

    @Test
    fun validatesThresholds() {
        val catalog = catalogOf(item("one"))

        assertFailsWith<IllegalArgumentException> {
            catalog.watchStatusByMediaId(progresses = emptyList(), minimumStartedPositionMs = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            catalog.watchStatusByMediaId(progresses = emptyList(), watchedRemainingMs = -1)
        }
    }

    private fun catalogOf(vararg items: LibraryMediaItem): LibraryCatalog =
        LibraryCatalog(
            rootName = "Anime",
            indexedAtEpochMs = 123,
            items = items.toList(),
        )

    private fun item(id: String): LibraryMediaItem =
        LibraryMediaItem(
            id = id,
            seriesTitle = "Series",
            episodeTitle = "Episode $id",
            relativePath = "Series/Episode $id.mkv",
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
