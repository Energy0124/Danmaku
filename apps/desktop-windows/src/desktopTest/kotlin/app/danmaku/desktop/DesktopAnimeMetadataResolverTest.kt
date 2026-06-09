package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeInfo
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeTitleSet
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySeason
import app.danmaku.domain.LibrarySeries
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DesktopAnimeMetadataResolverTest {
    @Test
    fun usesCachedDandanplayAnimeIdFromPreparedDanmakuBeforeMatchingFile() {
        val temp = createTempDirectory("danmaku-anime-metadata-resolver")
        val databasePath = temp.resolve("catalog.db")
        val mediaPath = temp.resolve("Episode 01.mkv")
        val item = libraryMediaItem(id = "episode-1")
        val series = LibrarySeries(
            id = "example-show",
            title = "Example Show",
            seasons = listOf(
                LibrarySeason(
                    id = "example-show-season-01",
                    label = "Season 1",
                    sortKey = 1,
                    items = listOf(item),
                ),
            ),
        )

        DesktopLibraryCatalogStore(databasePath).use { store ->
            store.saveDandanplayCommentCache(
                DesktopDandanplayCommentCache(
                    mediaId = item.id,
                    fileHash = "0123456789abcdef0123456789abcdef",
                    fileName = "Episode 01.mkv",
                    fileSizeBytes = 12,
                    episodeId = 194510001,
                    animeId = 19451,
                    animeTitle = "Cached Title",
                    episodeTitle = "Episode 1",
                    shiftSeconds = null,
                    commentsJson = """{"events":[]}""",
                    renderedAssPath = null,
                    fetchedAtEpochMs = 123,
                ),
            )

            var matchedFile = false
            val resolver = DesktopAnimeMetadataResolver(
                catalogStore = store,
                loadConnection = { DandanplayConnection("http://127.0.0.1:9") },
                fetchAnimeDetails = { _, animeId ->
                    assertEquals(19451, animeId)
                    ExternalAnimeInfo(
                        id = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, animeId),
                        titles = ExternalAnimeTitleSet(primary = "Cached Title"),
                        episodeCount = 12,
                        startYear = 2026,
                        imageUrl = null,
                        summary = null,
                    )
                },
                matchAnimeIdForPath = { _, _ ->
                    matchedFile = true
                    null
                },
                nowEpochMs = { 456 },
            )

            assertNull(
                resolver.ensureDandanplayPosterForSeries(
                    series = series,
                    mediaPathById = mapOf(item.id to mediaPath),
                ),
            )

            assertFalse(matchedFile)
            val dandanplayId = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, 19451)
            assertNotNull(store.loadExternalAnimeMetadataCache(dandanplayId))
            assertEquals(
                dandanplayId,
                store.loadExternalAnimeMappings(series.id).single().animeId,
            )
        }

        temp.toFile().deleteRecursively()
    }
}

private fun libraryMediaItem(id: String): LibraryMediaItem =
    LibraryMediaItem(
        id = id,
        seriesTitle = "Example Show",
        episodeTitle = "Episode 01",
        relativePath = "Example Show/Season 1/Episode 01.mkv",
        sizeBytes = 12,
        mediaType = "video/x-matroska",
        streamPath = "/library/items/$id/stream",
    )
