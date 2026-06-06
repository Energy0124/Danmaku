package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LibraryNextUpTest {
    @Test
    fun returnsResumableItemsNewestFirst() {
        val catalog = catalogOf(
            item("one"),
            item("two"),
            item("three"),
        )

        val nextUp = catalog.nextUpItems(
            progresses = listOf(
                progress("one", positionMs = 60_000, durationMs = 1_200_000, updatedAtEpochMs = 10),
                progress("two", positionMs = 90_000, durationMs = 1_200_000, updatedAtEpochMs = 20),
            ),
        )

        assertEquals(listOf("two", "one"), nextUp.map { it.mediaItem.id })
        assertEquals(listOf(LibraryNextUpReason.RESUME, LibraryNextUpReason.RESUME), nextUp.map { it.reason })
    }

    @Test
    fun promotesNextEpisodeAfterNearEndProgress() {
        val catalog = catalogOf(
            item("one"),
            item("two"),
            item("three"),
        )

        val nextUp = catalog.nextUpItems(
            progresses = listOf(
                progress("one", positionMs = 1_190_000, durationMs = 1_200_000, updatedAtEpochMs = 30),
            ),
        )

        assertEquals(listOf("two"), nextUp.map { it.mediaItem.id })
        assertEquals(LibraryNextUpReason.NEXT_EPISODE, nextUp.single().reason)
        assertEquals("one", nextUp.single().sourceProgress?.mediaId)
    }

    @Test
    fun skipsNextEpisodeWhenItAlreadyHasProgress() {
        val catalog = catalogOf(
            item("one"),
            item("two"),
            item("three"),
        )

        val nextUp = catalog.nextUpItems(
            progresses = listOf(
                progress("one", positionMs = 1_190_000, durationMs = 1_200_000, updatedAtEpochMs = 30),
                progress("two", positionMs = 60_000, durationMs = 1_200_000, updatedAtEpochMs = 20),
            ),
        )

        assertEquals(listOf("two"), nextUp.map { it.mediaItem.id })
        assertEquals(LibraryNextUpReason.RESUME, nextUp.single().reason)
    }

    @Test
    fun returnsFirstCatalogItemWhenThereIsNoProgress() {
        val catalog = catalogOf(item("one"), item("two"))

        val nextUp = catalog.nextUpItems(progresses = emptyList())

        assertEquals(listOf("one"), nextUp.map { it.mediaItem.id })
        assertEquals(LibraryNextUpReason.START, nextUp.single().reason)
    }

    @Test
    fun ignoresProgressForUnknownCatalogItems() {
        val catalog = catalogOf(item("one"), item("two"))

        val nextUp = catalog.nextUpItems(
            progresses = listOf(
                progress("missing", positionMs = 60_000, durationMs = 1_200_000, updatedAtEpochMs = 20),
            ),
        )

        assertEquals(listOf("one"), nextUp.map { it.mediaItem.id })
        assertEquals(LibraryNextUpReason.START, nextUp.single().reason)
    }

    @Test
    fun validatesInputs() {
        val catalog = catalogOf(item("one"))

        assertFailsWith<IllegalArgumentException> {
            catalog.nextUpItems(progresses = emptyList(), limit = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            catalog.nextUpItems(progresses = emptyList(), minimumResumePositionMs = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            catalog.nextUpItems(progresses = emptyList(), minimumRemainingMs = -1)
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
