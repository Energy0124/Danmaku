package app.danmaku.desktop

import app.danmaku.domain.LibraryMediaItem
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopLibraryQualityMetadataRefreshTest {
    @Test
    fun refreshesCachedAndResolvedItemsWhileSkippingMissingPathsAndNoMatches() {
        val cached = item("cached")
        val resolved = item("resolved")
        val missingPath = item("missing-path")
        val noMatch = item("no-match")
        val resolvedIds = mutableListOf<String>()
        val refreshed = mutableListOf<Pair<String, Long>>()
        val refresher = DesktopLibraryQualityMetadataRefresher(
            cachedDandanplayAnimeIdForItem = { item ->
                if (item.id == cached.id) 1001L else null
            },
            resolveDandanplayAnimeId = { item, _ ->
                resolvedIds += item.id
                if (item.id == resolved.id) 2002L else null
            },
            refreshDandanplayMetadataForItem = { item, animeId ->
                refreshed += item.id to animeId
            },
        )

        val result = refresher.refresh(
            items = listOf(cached, resolved, missingPath, noMatch),
            mediaPathById = mapOf(
                cached.id to Path.of("cached.mkv"),
                resolved.id to Path.of("resolved.mkv"),
                noMatch.id to Path.of("no-match.mkv"),
            ),
        )

        assertEquals(4, result.attemptedCount)
        assertEquals(2, result.refreshedCount)
        assertEquals(1, result.skippedMissingPathCount)
        assertEquals(1, result.skippedNoMatchCount)
        assertTrue(result.failureMessages.isEmpty())
        assertEquals(listOf("resolved", "no-match"), resolvedIds)
        assertEquals(listOf("cached" to 1001L, "resolved" to 2002L), refreshed)
    }

    @Test
    fun recordsFailuresAndContinuesRefreshingOtherItems() {
        val resolveFailure = item("resolve-failure")
        val refreshFailure = item("refresh-failure")
        val success = item("success")
        val refreshed = mutableListOf<Pair<String, Long>>()
        val refresher = DesktopLibraryQualityMetadataRefresher(
            cachedDandanplayAnimeIdForItem = { null },
            resolveDandanplayAnimeId = { item, _ ->
                if (item.id == resolveFailure.id) {
                    error("resolver unavailable")
                }
                when (item.id) {
                    refreshFailure.id -> 3003L
                    success.id -> 4004L
                    else -> null
                }
            },
            refreshDandanplayMetadataForItem = { item, animeId ->
                if (item.id == refreshFailure.id) {
                    error("metadata fetch failed")
                }
                refreshed += item.id to animeId
            },
        )

        val result = refresher.refresh(
            items = listOf(resolveFailure, refreshFailure, success),
            mediaPathById = mapOf(
                resolveFailure.id to Path.of("resolve-failure.mkv"),
                refreshFailure.id to Path.of("refresh-failure.mkv"),
                success.id to Path.of("success.mkv"),
            ),
        )

        assertEquals(3, result.attemptedCount)
        assertEquals(1, result.refreshedCount)
        assertEquals(0, result.skippedMissingPathCount)
        assertEquals(0, result.skippedNoMatchCount)
        assertEquals(listOf("success" to 4004L), refreshed)
        assertEquals(2, result.failureMessages.size)
        assertTrue(result.failureMessages[0].contains("resolver unavailable"))
        assertTrue(result.failureMessages[1].contains("metadata fetch failed"))
    }

    private fun item(id: String): LibraryMediaItem =
        LibraryMediaItem(
            id = id,
            seriesTitle = "Example Show",
            episodeTitle = "Example Show - $id",
            relativePath = "Example Show/$id.mkv",
            sizeBytes = 123,
            mediaType = "video/x-matroska",
            streamPath = "/media/$id",
        )
}
