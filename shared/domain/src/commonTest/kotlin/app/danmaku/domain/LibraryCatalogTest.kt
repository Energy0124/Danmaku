package app.danmaku.domain

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LibraryCatalogTest {
    @Test
    fun decodesCatalogItemsCreatedBeforeSubtitleMetadata() {
        val catalog = Json.decodeFromString<LibraryCatalog>(
            """
            {
              "rootName": "Example",
              "indexedAtEpochMs": 123,
              "items": [
                {
                  "id": "episode-id",
                  "seriesTitle": "Example Show",
                  "episodeTitle": "Episode 01",
                  "relativePath": "Example Show/Episode 01.mkv",
                  "sizeBytes": 456,
                  "mediaType": "video/mp4",
                  "streamPath": "/media/episode-id"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(emptyList(), catalog.items.single().subtitles)
        assertEquals(0, catalog.items.single().indexedAtEpochMs)
    }

    @Test
    fun findsPreviousAndNextItemsInCatalogOrder() {
        val catalog = catalogOf(
            item("one"),
            item("two"),
            item("three"),
        )

        assertNull(catalog.previousItem("one"))
        assertEquals("one", catalog.previousItem("two")?.id)
        assertEquals("three", catalog.nextItem("two")?.id)
        assertNull(catalog.nextItem("three"))
    }

    @Test
    fun returnsNullForUnknownNavigationItem() {
        val catalog = catalogOf(item("one"))

        assertNull(catalog.previousItem("missing"))
        assertNull(catalog.nextItem("missing"))
    }

    @Test
    fun rejectsBlankNavigationItemIds() {
        val catalog = catalogOf(item("one"))

        assertFailsWith<IllegalArgumentException> {
            catalog.previousItem(" ")
        }
        assertFailsWith<IllegalArgumentException> {
            catalog.nextItem(" ")
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
            seriesTitle = "Series $id",
            episodeTitle = "Episode $id",
            relativePath = "Series $id/Episode $id.mkv",
            sizeBytes = 123,
            mediaType = "video/x-matroska",
            streamPath = "/media/$id",
        )
}
