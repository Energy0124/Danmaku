package app.danmaku.tv

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackProgress

internal data class TvQaFixture(
    val catalog: LibraryCatalog,
    val progresses: List<PlaybackProgress>,
    val favorites: Set<String>,
)

internal fun createTvQaFixture(
    seriesCount: Int = 18,
    episodesPerSeries: Int = 12,
): TvQaFixture {
    val items = buildList {
        repeat(seriesCount) { seriesIndex ->
            repeat(episodesPerSeries) { episodeIndex ->
                val id = "qa-$seriesIndex-$episodeIndex"
                add(
                    LibraryMediaItem(
                        id = id,
                        seriesTitle = "Living Room Series ${seriesIndex + 1}",
                        episodeTitle = "Episode ${episodeIndex + 1}",
                        relativePath = "Series $seriesIndex/Episode $episodeIndex.mkv",
                        sizeBytes = 512L * 1024L * 1024L,
                        mediaType = "video/x-matroska",
                        streamPath = "/media/$id",
                        indexedAtEpochMs = 1_800_000_000_000L -
                            (seriesIndex * episodesPerSeries + episodeIndex) * 60_000L,
                    ),
                )
            }
        }
    }
    val progress = listOf(
        PlaybackProgress(
            mediaId = "qa-0-2",
            positionMs = 8 * 60_000L,
            durationMs = 24 * 60_000L,
            updatedAtEpochMs = 1_800_000_000_000L,
        ),
        PlaybackProgress(
            mediaId = "qa-1-5",
            positionMs = 12 * 60_000L,
            durationMs = 24 * 60_000L,
            updatedAtEpochMs = 1_799_999_000_000L,
        ),
    )
    return TvQaFixture(
        catalog = LibraryCatalog(
            rootName = "TV QA Library",
            indexedAtEpochMs = 1_800_000_000_000L,
            items = items,
        ),
        progresses = progress,
        favorites = setOf("qa-2-0", "qa-2-1", "qa-4-0"),
    )
}

internal const val TV_QA_FIXTURE_EXTRA = "app.danmaku.tv.QA_FIXTURE"
