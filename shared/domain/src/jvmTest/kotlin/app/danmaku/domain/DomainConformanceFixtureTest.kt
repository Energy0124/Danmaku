package app.danmaku.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals

class DomainConformanceFixtureTest {
    @Test
    fun recordsAndVerifiesDomainConformanceFixtures() {
        Files.createDirectories(fixtureDirectory)
        fixtureDefinitions.forEach { definition ->
            writeFixture("${definition.name}.json", definition.toFixtureJson())
        }

        Files.list(fixtureDirectory).use { paths ->
            paths
                .filter { path -> path.fileName.toString().endsWith(".json") }
                .sorted(Comparator.comparing { path -> path.fileName.toString() })
                .forEach { path -> verifyFixture(path) }
        }
    }

    private fun verifyFixture(path: Path) {
        val root = fixtureJson.parseToJsonElement(Files.readString(path)).jsonObject
        val input = root.getValue("input").jsonObject
        val catalog = fixtureJson.decodeFromJsonElement<LibraryCatalog>(input.getValue("catalog"))
        val progress = fixtureJson.decodeFromJsonElement<List<PlaybackProgress>>(
            input.getValue("progress"),
        )
        val options = input["options"]?.jsonObject ?: JsonObject(emptyMap())
        val feature = root.getValue("feature").jsonPrimitive.contentOrNull
            ?: error("fixture ${path.fileName} is missing feature")

        assertEquals(
            root.getValue("expected"),
            expectedFor(feature, catalog, progress, options),
            "fixture ${path.fileName}",
        )
    }

    private fun FixtureDefinition.toFixtureJson(): JsonObject =
        buildJsonObject {
            put("schemaVersion", FIXTURE_SCHEMA_VERSION)
            put("name", name)
            put("feature", feature)
            put("description", description)
            put("input", buildJsonObject {
                put("catalog", fixtureJson.encodeToJsonElement(catalog))
                put("progress", fixtureJson.encodeToJsonElement(progress))
                put("options", options)
            })
            put("expected", expectedFor(feature, catalog, progress, options))
        }

    private fun expectedFor(
        feature: String,
        catalog: LibraryCatalog,
        progress: List<PlaybackProgress>,
        options: JsonObject,
    ): JsonObject =
        when (feature) {
            "series-grouping" -> expectedSeriesGrouping(catalog)
            "watch-state" -> expectedWatchState(catalog, progress, options)
            "next-up" -> expectedNextUp(catalog, progress, options)
            "continue-watching" -> expectedContinueWatching(catalog, progress, options)
            else -> error("Unsupported fixture feature: $feature")
        }

    private fun expectedSeriesGrouping(catalog: LibraryCatalog): JsonObject =
        buildJsonObject {
            put("groupedSeries", buildJsonArray {
                catalog.groupedSeries().forEach { series ->
                    add(series.toFixtureJson())
                }
            })
        }

    private fun expectedWatchState(
        catalog: LibraryCatalog,
        progress: List<PlaybackProgress>,
        options: JsonObject,
    ): JsonObject {
        val minimumStartedPositionMs = options.longOption("minimumStartedPositionMs", 10_000)
        val watchedRemainingMs = options.longOption("watchedRemainingMs", 30_000)
        return buildJsonObject {
            put("watchStatusByMediaId", buildJsonArray {
                catalog.watchStatusByMediaId(
                    progresses = progress,
                    minimumStartedPositionMs = minimumStartedPositionMs,
                    watchedRemainingMs = watchedRemainingMs,
                )
                    .toSortedMap()
                    .values
                    .forEach { status -> add(status.toFixtureJson()) }
            })
            put("seriesWatchSummaryById", buildJsonArray {
                catalog.seriesWatchSummaryById(
                    progresses = progress,
                    minimumStartedPositionMs = minimumStartedPositionMs,
                    watchedRemainingMs = watchedRemainingMs,
                )
                    .values
                    .sortedBy(LibrarySeriesWatchSummary::seriesId)
                    .forEach { summary -> add(summary.toFixtureJson()) }
            })
        }
    }

    private fun expectedNextUp(
        catalog: LibraryCatalog,
        progress: List<PlaybackProgress>,
        options: JsonObject,
    ): JsonObject =
        buildJsonObject {
            put("nextUp", buildJsonArray {
                catalog.nextUpItems(
                    progresses = progress,
                    limit = options.intOption("limit", 8),
                    minimumResumePositionMs = options.longOption("minimumResumePositionMs", 10_000),
                    minimumRemainingMs = options.longOption("minimumRemainingMs", 30_000),
                ).forEach { item -> add(item.toFixtureJson()) }
            })
        }

    private fun expectedContinueWatching(
        catalog: LibraryCatalog,
        progress: List<PlaybackProgress>,
        options: JsonObject,
    ): JsonObject =
        buildJsonObject {
            put("continueWatching", buildJsonArray {
                catalog.continueWatchingItems(
                    progresses = progress,
                    limit = options.intOption("limit", 8),
                    minimumResumePositionMs = options.longOption("minimumResumePositionMs", 10_000),
                    minimumRemainingMs = options.longOption("minimumRemainingMs", 30_000),
                ).forEach { item -> add(item.toFixtureJson()) }
            })
        }

    private fun LibrarySeries.toFixtureJson(): JsonObject =
        buildJsonObject {
            put("id", id)
            put("title", title)
            put("episodeCount", episodeCount)
            put("subtitleTrackCount", subtitleTrackCount)
            put("totalSizeBytes", totalSizeBytes)
            put("latestIndexedMediaId", latestIndexedItem.id)
            put("seasons", buildJsonArray {
                seasons.forEach { season ->
                    add(
                        buildJsonObject {
                            put("id", season.id)
                            put("label", season.label)
                            put("sortKey", season.sortKey)
                            put("itemIds", buildJsonArray {
                                season.items.forEach { item -> add(item.id) }
                            })
                        },
                    )
                }
            })
        }

    private fun LibraryWatchStatus.toFixtureJson(): JsonObject =
        buildJsonObject {
            put("mediaId", mediaItem.id)
            put("state", state.name)
            put("progress", progress?.toFixtureJson() ?: JsonNull)
        }

    private fun LibrarySeriesWatchSummary.toFixtureJson(): JsonObject =
        buildJsonObject {
            put("seriesId", seriesId)
            put("totalCount", totalCount)
            put("watchedCount", watchedCount)
            put("inProgressCount", inProgressCount)
            put("newCount", newCount)
        }

    private fun LibraryNextUpItem.toFixtureJson(): JsonObject =
        buildJsonObject {
            put("mediaId", mediaItem.id)
            put("reason", reason.name)
            putNullableString("progressMediaId", progress?.mediaId)
            putNullableString("sourceProgressMediaId", sourceProgress?.mediaId)
        }

    private fun LibraryPlaybackProgressItem.toFixtureJson(): JsonObject =
        buildJsonObject {
            put("mediaId", mediaItem.id)
            put("progress", progress.toFixtureJson())
        }

    private fun PlaybackProgress.toFixtureJson(): JsonObject =
        fixtureJson.encodeToJsonElement(this).jsonObject

    private fun JsonObject.longOption(
        name: String,
        defaultValue: Long,
    ): Long =
        get(name)?.jsonPrimitive?.longOrNull ?: defaultValue

    private fun JsonObject.intOption(
        name: String,
        defaultValue: Int,
    ): Int =
        get(name)?.jsonPrimitive?.intOrNull ?: defaultValue

    private fun JsonObjectBuilder.putNullableString(
        name: String,
        value: String?,
    ) {
        if (value == null) {
            put(name, JsonNull)
        } else {
            put(name, value)
        }
    }

    private fun writeFixture(fileName: String, body: JsonObject) {
        val rendered = fixtureJson.encodeToString(JsonObject.serializer(), body).trimEnd() + "\n"
        Files.writeString(fixtureDirectory.resolve(fileName), rendered)
        assertEquals(rendered, Files.readString(fixtureDirectory.resolve(fileName)))
    }

    private data class FixtureDefinition(
        val name: String,
        val feature: String,
        val description: String,
        val catalog: LibraryCatalog,
        val progress: List<PlaybackProgress> = emptyList(),
        val options: JsonObject = JsonObject(emptyMap()),
    )

    private val fixtureDefinitions: List<FixtureDefinition> =
        listOf(
            FixtureDefinition(
                name = "series-grouping",
                feature = "series-grouping",
                description = "Groups local and provider-matched catalog items into sorted series.",
                catalog = LibraryCatalog(
                    rootName = "Domain Fixture Library",
                    indexedAtEpochMs = 1_700_000_000_000,
                    items = listOf(
                        mediaItem(
                            id = "beta-01",
                            seriesTitle = "Beta Show",
                            episodeTitle = "Episode 01",
                        ),
                        mediaItem(
                            id = "alpha-s2e02",
                            seriesTitle = "Alpha Show",
                            episodeTitle = "S02E02",
                            relativePath = "Alpha Show/Season 2/Episode 02.mkv",
                            subtitleCount = 1,
                        ),
                        mediaItem(
                            id = "alpha-s1e01",
                            seriesTitle = "Alpha Show",
                            episodeTitle = "S01E01",
                            relativePath = "Alpha Show/Season 1/Episode 01.mkv",
                        ),
                        mediaItem(
                            id = "matched-01",
                            seriesTitle = "Folder A",
                            episodeTitle = "Episode 01",
                            animeMetadata = animeMetadata(101, "Matched Anime"),
                        ),
                        mediaItem(
                            id = "matched-02",
                            seriesTitle = "Folder B",
                            episodeTitle = "Episode 02",
                            animeMetadata = animeMetadata(101, "Matched Anime"),
                        ),
                    ),
                ),
            ),
            FixtureDefinition(
                name = "watch-state",
                feature = "watch-state",
                description = "Derives media watch states and per-series watch counts.",
                catalog = LibraryCatalog(
                    rootName = "Domain Fixture Library",
                    indexedAtEpochMs = 1_700_000_000_000,
                    items = listOf(
                        mediaItem("one", "Series", "Episode 01"),
                        mediaItem("two", "Series", "Episode 02"),
                        mediaItem("three", "Series", "Episode 03"),
                        mediaItem("four", "Series", "Episode 04"),
                    ),
                ),
                progress = listOf(
                    progress("missing", positionMs = 90_000, durationMs = 1_200_000, updatedAtEpochMs = 50),
                    progress("two", positionMs = 60_000, durationMs = 1_200_000, updatedAtEpochMs = 20),
                    progress("three", positionMs = 1_190_000, durationMs = 1_200_000, updatedAtEpochMs = 30),
                    progress("four", positionMs = 1_190_000, durationMs = 1_200_000, updatedAtEpochMs = 10),
                    progress("four", positionMs = 5_000, durationMs = 1_200_000, updatedAtEpochMs = 40),
                ),
                options = watchStateOptions(),
            ),
            FixtureDefinition(
                name = "next-up",
                feature = "next-up",
                description = "Ranks resumable media and promotes the following episode after near-end progress.",
                catalog = LibraryCatalog(
                    rootName = "Domain Fixture Library",
                    indexedAtEpochMs = 1_700_000_000_000,
                    items = listOf(
                        mediaItem("one", "Series", "Episode 01"),
                        mediaItem("two", "Series", "Episode 02"),
                        mediaItem("three", "Series", "Episode 03"),
                        mediaItem("four", "Series", "Episode 04"),
                    ),
                ),
                progress = listOf(
                    progress("one", positionMs = 1_190_000, durationMs = 1_200_000, updatedAtEpochMs = 30),
                    progress("three", positionMs = 90_000, durationMs = 1_200_000, updatedAtEpochMs = 40),
                    progress("missing", positionMs = 60_000, durationMs = 1_200_000, updatedAtEpochMs = 50),
                ),
                options = resumeOptions(),
            ),
            FixtureDefinition(
                name = "continue-watching",
                feature = "continue-watching",
                description = "Keeps latest resumable progress rows newest-first.",
                catalog = LibraryCatalog(
                    rootName = "Domain Fixture Library",
                    indexedAtEpochMs = 1_700_000_000_000,
                    items = listOf(
                        mediaItem("one", "Series", "Episode 01"),
                        mediaItem("two", "Series", "Episode 02"),
                        mediaItem("three", "Series", "Episode 03"),
                    ),
                ),
                progress = listOf(
                    progress("one", positionMs = 60_000, durationMs = 1_200_000, updatedAtEpochMs = 10),
                    progress("two", positionMs = 1_190_000, durationMs = 1_200_000, updatedAtEpochMs = 5),
                    progress("two", positionMs = 90_000, durationMs = 1_200_000, updatedAtEpochMs = 20),
                    progress("three", positionMs = 1_190_000, durationMs = 1_200_000, updatedAtEpochMs = 30),
                ),
                options = resumeOptions(),
            ),
        )

    private fun mediaItem(
        id: String,
        seriesTitle: String,
        episodeTitle: String,
        relativePath: String = "$seriesTitle/$episodeTitle.mkv",
        sizeBytes: Long = 123,
        subtitleCount: Int = 0,
        animeMetadata: LibraryAnimeMetadata? = null,
    ): LibraryMediaItem =
        LibraryMediaItem(
            id = id,
            seriesTitle = seriesTitle,
            episodeTitle = episodeTitle,
            relativePath = relativePath,
            sizeBytes = sizeBytes,
            mediaType = "video/x-matroska",
            streamPath = "/media/$id",
            indexedAtEpochMs = 1_700_000_000_000,
            subtitles = (1..subtitleCount).map { index ->
                LibrarySubtitleTrack(
                    id = "$id-subtitle-$index",
                    label = "Subtitle $index",
                    relativePath = "$seriesTitle/$episodeTitle.$index.ass",
                    mediaType = "text/x-ass",
                    streamPath = "/subtitles/$id-$index",
                )
            },
            animeMetadata = animeMetadata,
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

    private fun animeMetadata(
        animeId: Long,
        displayTitle: String,
    ): LibraryAnimeMetadata =
        LibraryAnimeMetadata(
            animeId = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, animeId),
            displayTitle = displayTitle,
            primaryTitle = displayTitle,
        )

    private fun watchStateOptions(): JsonObject =
        buildJsonObject {
            put("minimumStartedPositionMs", 10_000)
            put("watchedRemainingMs", 30_000)
        }

    private fun resumeOptions(): JsonObject =
        buildJsonObject {
            put("limit", 8)
            put("minimumResumePositionMs", 10_000)
            put("minimumRemainingMs", 30_000)
        }

    private val repositoryRoot: Path =
        generateSequence(Path.of("").toAbsolutePath().normalize()) { path -> path.parent }
            .first { path -> Files.isRegularFile(path.resolve("settings.gradle.kts")) }

    private val fixtureDirectory: Path =
        repositoryRoot.resolve(
            Path.of(
                "shared",
                "domain",
                "src",
                "jvmTest",
                "resources",
                "domain-conformance-fixtures",
            ),
        )

    private companion object {
        const val FIXTURE_SCHEMA_VERSION = 1

        val fixtureJson = Json {
            prettyPrint = true
            encodeDefaults = true
        }
    }
}
