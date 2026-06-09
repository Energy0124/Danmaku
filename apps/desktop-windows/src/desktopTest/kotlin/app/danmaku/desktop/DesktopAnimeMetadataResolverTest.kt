package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeInfo
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeMappingSource
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeTitleSet
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySeason
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.groupedSeries
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
            assertEquals(emptyList(), store.loadExternalAnimeMappings(series.id))
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun displayCatalogGroupsItemsByCachedAnimeMetadata() {
        val temp = createTempDirectory("danmaku-anime-display-metadata")
        val databasePath = temp.resolve("catalog.db")
        val firstItem = libraryMediaItem(
            id = "episode-1",
            seriesTitle = "Yomotsu Hegui",
            episodeTitle = "Episode 01",
        )
        val secondItem = libraryMediaItem(
            id = "episode-2",
            seriesTitle = "黄泉使者",
            episodeTitle = "Episode 02",
        )
        val catalog = LibraryCatalog(
            rootName = "Anime",
            indexedAtEpochMs = 123,
            items = listOf(firstItem, secondItem),
        )
        val animeId = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, 19451)
        val anime = ExternalAnimeInfo(
            id = animeId,
            titles = ExternalAnimeTitleSet(
                primary = "黄泉的使者",
                chinese = "黄泉的使者",
            ),
        )

        DesktopLibraryCatalogStore(databasePath).use { store ->
            listOf(firstItem, secondItem).forEach { item ->
                store.saveExternalAnimeItemMapping(
                    DesktopExternalAnimeItemMapping(
                        localMediaId = item.id,
                        animeId = animeId,
                        source = ExternalAnimeMappingSource.AUTO,
                        confidence = 1.0,
                        mappedAtEpochMs = 456,
                    ),
                )
            }
            store.saveExternalAnimeMetadataCache(
                DesktopExternalAnimeMetadataCache(
                    anime = anime,
                    fetchedAtEpochMs = 456,
                ),
            )
            val resolver = DesktopAnimeMetadataResolver(
                catalogStore = store,
                loadConnection = { DandanplayConnection("http://127.0.0.1:9") },
            )
            val displayLibrary = IndexedLocalLibrary(
                catalog = catalog,
                filesById = emptyMap(),
            ).withExternalAnimeMetadata(resolver)

            val displaySeries = displayLibrary.catalog.groupedSeries()
            val metadataById = displayLibrary.catalog.items.associate { item ->
                item.id to item.animeMetadata
            }

            assertEquals(
                mapOf(
                    "黄泉的使者" to listOf(firstItem.id, secondItem.id),
                ),
                displaySeries.associate { series ->
                    series.title to series.seasons.flatMap { season -> season.items.map(LibraryMediaItem::id) }
                },
            )
            assertEquals("黄泉的使者", metadataById[firstItem.id]?.displayTitle)
            assertEquals("黄泉的使者", metadataById[secondItem.id]?.displayTitle)
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun itemMetadataRefreshCreatesItemMappingOnly() {
        val temp = createTempDirectory("danmaku-anime-item-metadata")
        val databasePath = temp.resolve("catalog.db")
        val item = libraryMediaItem(id = "episode-1")
        val animeId = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, 18844)

        DesktopLibraryCatalogStore(databasePath).use { store ->
            val resolver = DesktopAnimeMetadataResolver(
                catalogStore = store,
                loadConnection = { DandanplayConnection("http://127.0.0.1:9") },
                fetchAnimeDetails = { _, requestedAnimeId ->
                    ExternalAnimeInfo(
                        id = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, requestedAnimeId),
                        titles = ExternalAnimeTitleSet(primary = "Classroom S4"),
                    )
                },
            )

            assertNull(resolver.refreshDandanplayMetadataForItem(item, animeId.value))

            assertNotNull(store.loadExternalAnimeMetadataCache(animeId))
            assertEquals(animeId, store.loadExternalAnimeItemMappings(item.id).single().animeId)
            assertEquals(emptyList(), store.loadExternalAnimeMappings("example-show"))
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun displayCatalogSplitsOnlyItemsWithItemMetadata() {
        val temp = createTempDirectory("danmaku-anime-item-display-metadata")
        val databasePath = temp.resolve("catalog.db")
        val firstItem = libraryMediaItem(id = "episode-1")
        val secondItem = libraryMediaItem(id = "episode-2", episodeTitle = "Episode 02")
        val catalog = LibraryCatalog(
            rootName = "Anime",
            indexedAtEpochMs = 123,
            items = listOf(firstItem, secondItem),
        )
        val localSeries = catalog.groupedSeries().single()
        val seriesAnimeId = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, 19451)
        val itemAnimeId = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, 18844)

        DesktopLibraryCatalogStore(databasePath).use { store ->
            store.saveExternalAnimeMapping(
                ExternalAnimeMapping(
                    localSeriesId = localSeries.id,
                    animeId = seriesAnimeId,
                    source = ExternalAnimeMappingSource.AUTO,
                    confidence = 1.0,
                    mappedAtEpochMs = 456,
                ),
            )
            store.saveExternalAnimeMetadataCache(
                DesktopExternalAnimeMetadataCache(
                    anime = ExternalAnimeInfo(
                        id = seriesAnimeId,
                        titles = ExternalAnimeTitleSet(primary = "Series Anime"),
                    ),
                    fetchedAtEpochMs = 456,
                ),
            )
            store.saveExternalAnimeMetadataCache(
                DesktopExternalAnimeMetadataCache(
                    anime = ExternalAnimeInfo(
                        id = itemAnimeId,
                        titles = ExternalAnimeTitleSet(primary = "Item Anime"),
                    ),
                    fetchedAtEpochMs = 789,
                ),
            )
            store.saveDandanplayCommentCache(
                dandanplayCommentCache(
                    mediaId = firstItem.id,
                    animeId = itemAnimeId.value,
                ),
            )
            val resolver = DesktopAnimeMetadataResolver(
                catalogStore = store,
                loadConnection = { DandanplayConnection("http://127.0.0.1:9") },
            )

            val displayCatalog = IndexedLocalLibrary(
                catalog = catalog,
                filesById = emptyMap(),
            ).withExternalAnimeMetadata(resolver).catalog
            val displaySeries = displayCatalog.groupedSeries()

            assertEquals(
                mapOf(
                    "Item Anime" to listOf(firstItem.id),
                    "Example Show" to listOf(secondItem.id),
                ),
                displaySeries.associate { series ->
                    series.title to series.seasons.flatMap { season -> season.items.map(LibraryMediaItem::id) }
                },
            )
            assertEquals("Item Anime", displayCatalog.items.first { it.id == firstItem.id }.animeMetadata?.displayTitle)
            assertNull(displayCatalog.items.first { it.id == secondItem.id }.animeMetadata)
        }

        temp.toFile().deleteRecursively()
    }
}

private fun libraryMediaItem(
    id: String,
    seriesTitle: String = "Example Show",
    episodeTitle: String = "Episode 01",
): LibraryMediaItem =
    LibraryMediaItem(
        id = id,
        seriesTitle = seriesTitle,
        episodeTitle = episodeTitle,
        relativePath = "$seriesTitle/Season 1/$episodeTitle.mkv",
        sizeBytes = 12,
        mediaType = "video/x-matroska",
        streamPath = "/library/items/$id/stream",
    )

private fun dandanplayCommentCache(
    mediaId: String,
    animeId: Long,
): DesktopDandanplayCommentCache =
    DesktopDandanplayCommentCache(
        mediaId = mediaId,
        fileHash = "0123456789abcdef0123456789abcdef",
        fileName = "$mediaId.mkv",
        fileSizeBytes = 12,
        episodeId = animeId * 10_000 + 1,
        animeId = animeId,
        animeTitle = "Anime $animeId",
        episodeTitle = "Episode 1",
        shiftSeconds = null,
        commentsJson = """{"events":[]}""",
        renderedAssPath = null,
        fetchedAtEpochMs = 123,
    )
