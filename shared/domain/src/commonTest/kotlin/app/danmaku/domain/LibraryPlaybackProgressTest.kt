package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LibraryPlaybackProgressTest {
    @Test
    fun returnsResumableContinueWatchingItemsNewestFirst() {
        val catalog = catalogOf(item("one"), item("two"), item("three"))

        val continueWatching = catalog.continueWatchingItems(
            progresses = listOf(
                progress("one", positionMs = 60_000, durationMs = 1_200_000, updatedAtEpochMs = 10),
                progress("two", positionMs = 90_000, durationMs = 1_200_000, updatedAtEpochMs = 20),
                progress("three", positionMs = 1_190_000, durationMs = 1_200_000, updatedAtEpochMs = 30),
            ),
        )

        assertEquals(listOf("two", "one"), continueWatching.map { it.mediaItem.id })
    }

    @Test
    fun usesLatestProgressForDuplicateContinueWatchingItems() {
        val catalog = catalogOf(item("one"))

        val continueWatching = catalog.continueWatchingItems(
            progresses = listOf(
                progress("one", positionMs = 1_190_000, durationMs = 1_200_000, updatedAtEpochMs = 20),
                progress("one", positionMs = 90_000, durationMs = 1_200_000, updatedAtEpochMs = 30),
            ),
        )

        assertEquals(listOf("one"), continueWatching.map { it.mediaItem.id })
        assertEquals(90_000, continueWatching.single().progress.positionMs)
    }

    @Test
    fun returnsRecentlyWatchedItemsNewestFirst() {
        val catalog = catalogOf(item("one"), item("two"), item("three"))

        val recentlyWatched = catalog.recentlyWatchedItems(
            progresses = listOf(
                progress("one", positionMs = 60_000, durationMs = 1_200_000, updatedAtEpochMs = 10),
                progress("missing", positionMs = 90_000, durationMs = 1_200_000, updatedAtEpochMs = 40),
                progress("two", positionMs = 90_000, durationMs = 1_200_000, updatedAtEpochMs = 20),
            ),
        )

        assertEquals(listOf("two", "one"), recentlyWatched.map { it.mediaItem.id })
    }

    @Test
    fun appliesLimits() {
        val catalog = catalogOf(item("one"), item("two"))

        assertEquals(
            listOf("two"),
            catalog.continueWatchingItems(
                progresses = listOf(
                    progress("one", positionMs = 60_000, durationMs = 1_200_000, updatedAtEpochMs = 10),
                    progress("two", positionMs = 90_000, durationMs = 1_200_000, updatedAtEpochMs = 20),
                ),
                limit = 1,
            ).map { it.mediaItem.id },
        )
        assertEquals(
            listOf("two"),
            catalog.recentlyWatchedItems(
                progresses = listOf(
                    progress("one", positionMs = 60_000, durationMs = 1_200_000, updatedAtEpochMs = 10),
                    progress("two", positionMs = 90_000, durationMs = 1_200_000, updatedAtEpochMs = 20),
                ),
                limit = 1,
            ).map { it.mediaItem.id },
        )
    }

    @Test
    fun validatesInputs() {
        val catalog = catalogOf(item("one"))

        assertFailsWith<IllegalArgumentException> {
            catalog.continueWatchingItems(progresses = emptyList(), limit = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            catalog.continueWatchingItems(progresses = emptyList(), minimumResumePositionMs = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            catalog.continueWatchingItems(progresses = emptyList(), minimumRemainingMs = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            catalog.recentlyWatchedItems(progresses = emptyList(), limit = -1)
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
