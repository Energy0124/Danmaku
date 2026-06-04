package app.danmaku.domain

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

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
    }
}
