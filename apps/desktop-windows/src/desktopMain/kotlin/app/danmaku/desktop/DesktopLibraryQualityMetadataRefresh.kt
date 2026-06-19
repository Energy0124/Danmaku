package app.danmaku.desktop

import app.danmaku.domain.LibraryMediaItem
import java.nio.file.Path

internal data class DesktopLibraryQualityMetadataRefreshResult(
    val attemptedCount: Int,
    val refreshedCount: Int,
    val skippedMissingPathCount: Int,
    val skippedNoMatchCount: Int,
    val failureMessages: List<String>,
)

internal class DesktopLibraryQualityMetadataRefresher(
    private val cachedDandanplayAnimeIdForItem: (LibraryMediaItem) -> Long?,
    private val resolveDandanplayAnimeId: (LibraryMediaItem, Path) -> Long?,
    private val refreshDandanplayMetadataForItem: (LibraryMediaItem, Long) -> Unit,
) {
    fun refresh(
        items: List<LibraryMediaItem>,
        mediaPathById: Map<String, Path>,
    ): DesktopLibraryQualityMetadataRefreshResult {
        var refreshedCount = 0
        var skippedMissingPathCount = 0
        var skippedNoMatchCount = 0
        val failures = mutableListOf<String>()

        items.forEach { item ->
            val mediaPath = mediaPathById[item.id]
            if (mediaPath == null) {
                skippedMissingPathCount += 1
                return@forEach
            }
            val refreshed = runCatching {
                val animeId = cachedDandanplayAnimeIdForItem(item)
                    ?: resolveDandanplayAnimeId(item, mediaPath)
                if (animeId == null) {
                    skippedNoMatchCount += 1
                    false
                } else {
                    refreshDandanplayMetadataForItem(item, animeId)
                    true
                }
            }.fold(
                onSuccess = { refreshed -> refreshed },
                onFailure = { error ->
                    failures += "${item.episodeTitle}: ${error.message ?: error::class.simpleName}"
                    false
                },
            )
            if (refreshed) {
                refreshedCount += 1
            }
        }

        return DesktopLibraryQualityMetadataRefreshResult(
            attemptedCount = items.size,
            refreshedCount = refreshedCount,
            skippedMissingPathCount = skippedMissingPathCount,
            skippedNoMatchCount = skippedNoMatchCount,
            failureMessages = failures,
        )
    }
}
