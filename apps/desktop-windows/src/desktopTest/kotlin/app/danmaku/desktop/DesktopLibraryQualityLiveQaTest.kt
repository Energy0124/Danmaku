package app.danmaku.desktop

import app.danmaku.domain.LibraryQualityIssue
import app.danmaku.domain.LibraryQualityIssueType
import app.danmaku.domain.libraryQualityReport
import app.danmaku.domain.stableKey
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class DesktopLibraryQualityLiveQaTest {
    @Test
    fun appliesMappingPlansAgainstCopiedCatalogDatabase() {
        val config = LiveQaConfig.fromEnvironment() ?: return
        requireSafeDatabasePath(config.databasePath)

        DesktopLibraryCatalogStore(config.databasePath).use { store ->
            val metadataResolver = DesktopAnimeMetadataResolver(
                catalogStore = store,
                loadConnection = { error("Live QA uses cached metadata only") },
                posterCache = DesktopAnimePosterCache(config.reportPath.parent.resolve("poster-cache")),
            )
            val library = loadLiveQaLibrary(store, config)
            val displayLibrary = library.indexedLibrary.withExternalAnimeMetadata(metadataResolver)
            val report = displayLibrary.catalog.libraryQualityReport()
            val decisionsBefore = store.loadLibraryQualityIssueDecisions()
                .associateBy(DesktopLibraryQualityIssueDecision::issueKey)
            val issuePlans = report.issues.mapNotNull { issue ->
                issue.libraryQualityMappingApplyPlan(displayLibrary.catalog, config.appliedAtEpochMs)
                    ?.let { plan -> LiveQaIssuePlan(issue, plan) }
            }

            issuePlans.forEach { issuePlan ->
                store.applyLibraryQualityMappingPlan(
                    issue = issuePlan.issue,
                    plan = issuePlan.plan,
                    appliedAtEpochMs = config.appliedAtEpochMs,
                )
                assertApplied(store, issuePlan)
            }

            val decisionsAfter = store.loadLibraryQualityIssueDecisions()
                .associateBy(DesktopLibraryQualityIssueDecision::issueKey)
            val reportText = liveQaReportText(
                config = config,
                library = displayLibrary,
                librarySource = library.source,
                issues = report.issues,
                issuePlans = issuePlans,
                decisionsBefore = decisionsBefore,
                decisionsAfter = decisionsAfter,
            )
            config.reportPath.parent.createDirectories()
            Files.writeString(config.reportPath, reportText)

            assertTrue(displayLibrary.catalog.items.isNotEmpty(), "Live QA catalog must contain media items")
            issuePlans.forEach { issuePlan ->
                assertEquals(
                    DesktopLibraryQualityIssueDecisionState.RESOLVED,
                    decisionsAfter[issuePlan.issue.stableKey()]?.state,
                )
            }
        }
    }

    private fun loadLiveQaLibrary(
        store: DesktopLibraryCatalogStore,
        config: LiveQaConfig,
    ): LiveQaLibrary {
        config.libraryRoot?.let { root ->
            if (!root.exists()) {
                fail("Live QA library root does not exist: ${root.absolutePathString()}")
            }
            val storedLibrary = store.load(root)
            return if (storedLibrary != null) {
                LiveQaLibrary(
                    indexedLibrary = storedLibrary,
                    source = "explicit root loaded from database cache",
                )
            } else {
                LiveQaLibrary(
                    indexedLibrary = LocalMediaLibraryIndexer.index(root),
                    source = "explicit root fresh filesystem scan",
                )
            }
        }

        val registered = store.loadRegisteredLibrary()
        if (registered.catalog.items.isNotEmpty()) {
            return LiveQaLibrary(
                indexedLibrary = registered,
                source = "registered database roots",
            )
        }
        fail("Set DANMAKU_LIVE_QA_LIBRARY_ROOT when the copied database has no registered roots")
    }

    private fun assertApplied(
        store: DesktopLibraryCatalogStore,
        issuePlan: LiveQaIssuePlan,
    ) {
        issuePlan.plan.itemMappings.forEach { expected ->
            assertEquals(
                expected,
                store.loadExternalAnimeItemMappings(expected.localMediaId)
                    .singleOrNull { mapping -> mapping.animeId.provider == expected.animeId.provider },
            )
        }
        issuePlan.plan.seriesMappings.forEach { expected ->
            assertEquals(
                expected,
                store.loadExternalAnimeMappings(expected.localSeriesId)
                    .singleOrNull { mapping -> mapping.animeId.provider == expected.animeId.provider },
            )
        }
        val decision = assertNotNull(store.loadLibraryQualityIssueDecision(issuePlan.issue.stableKey()))
        assertEquals(DesktopLibraryQualityIssueDecisionState.RESOLVED, decision.state)
    }

    private fun requireSafeDatabasePath(databasePath: Path) {
        val normalized = databasePath.toAbsolutePath().normalize()
        val localAppData = System.getenv("LOCALAPPDATA")
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?.toAbsolutePath()
            ?.normalize()
        val activeDatabase = localAppData?.resolve("Danmaku")?.resolve("library.db")?.normalize()
        require(normalized != activeDatabase) {
            "Live QA must run against a copied database, not the active desktop database"
        }
    }

    private fun liveQaReportText(
        config: LiveQaConfig,
        library: IndexedLocalLibrary,
        librarySource: String,
        issues: List<LibraryQualityIssue>,
        issuePlans: List<LiveQaIssuePlan>,
        decisionsBefore: Map<String, DesktopLibraryQualityIssueDecision>,
        decisionsAfter: Map<String, DesktopLibraryQualityIssueDecision>,
    ): String {
        val issueCountByType = issues
            .groupingBy(LibraryQualityIssue::type)
            .eachCount()
            .toSortedMap(compareBy(LibraryQualityIssueType::name))
        val openBefore = issues.count { issue -> issue.stableKey() !in decisionsBefore }
        val openAfter = issues.count { issue -> issue.stableKey() !in decisionsAfter }
        val plannedItemMappings = issuePlans.sumOf { issuePlan -> issuePlan.plan.itemMappings.size }
        val plannedSeriesMappings = issuePlans.sumOf { issuePlan -> issuePlan.plan.seriesMappings.size }

        return buildString {
            appendLine("# Library Quality Live QA")
            appendLine()
            appendLine("- Database copy: `${config.databasePath.absolutePathString()}`")
            appendLine("- Library root override: `${config.libraryRoot?.absolutePathString() ?: "registered database roots"}`")
            appendLine("- Library source: $librarySource")
            appendLine("- Catalog items: ${library.catalog.items.size}")
            appendLine("- Quality issues: ${issues.size}")
            appendLine("- Open before apply: $openBefore")
            appendLine("- Apply-capable split/merge issues: ${issuePlans.size}")
            appendLine("- Item mappings applied: $plannedItemMappings")
            appendLine("- Series mappings applied: $plannedSeriesMappings")
            appendLine("- Open after apply decisions: $openAfter")
            appendLine()
            appendLine("## Issues By Type")
            appendLine()
            if (issueCountByType.isEmpty()) {
                appendLine("- none")
            } else {
                issueCountByType.forEach { (type, count) ->
                    appendLine("- `${type.name}`: $count")
                }
            }
            appendLine()
            appendLine("## Applied Examples")
            appendLine()
            if (issuePlans.isEmpty()) {
                appendLine("No split/merge issue had enough cached metadata to build an apply plan.")
            } else {
                issuePlans.take(config.maxExamples).forEachIndexed { index, issuePlan ->
                    appendLine("${index + 1}. `${issuePlan.issue.type.name}` - ${issuePlan.issue.seriesTitle}")
                    appendLine("   - Affected items: ${issuePlan.issue.mediaItemIds.size}")
                    appendLine("   - Planned item mappings: ${issuePlan.plan.itemMappings.size}")
                    appendLine("   - Planned series mappings: ${issuePlan.plan.seriesMappings.size}")
                    issuePlan.plan.seriesMappings.take(5).forEach { mapping ->
                        appendLine(
                            "   - Series `${mapping.localSeriesId}` -> " +
                                "`${mapping.animeId.provider.name}:${mapping.animeId.value}`",
                        )
                    }
                }
            }
            appendLine()
            appendLine("## Tooling Decision")
            appendLine()
            appendLine(
                "The apply flow can resolve metadata-backed split/merge review rows without moving files. " +
                    "Filesystem organization or rename tooling should stay optional and preview-first, not a prerequisite.",
            )
        }
    }
}

private data class LiveQaConfig(
    val databasePath: Path,
    val libraryRoot: Path?,
    val reportPath: Path,
    val appliedAtEpochMs: Long,
    val maxExamples: Int,
) {
    companion object {
        fun fromEnvironment(): LiveQaConfig? {
            val databasePath = System.getenv("DANMAKU_LIVE_QA_DATABASE")
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?: return null
            val reportPath = System.getenv("DANMAKU_LIVE_QA_REPORT")
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?: Path.of("build", "qa", "library-quality", "live-apply-mappings.md")
            val libraryRoot = System.getenv("DANMAKU_LIVE_QA_LIBRARY_ROOT")
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
            val appliedAtEpochMs = System.getenv("DANMAKU_LIVE_QA_APPLIED_AT_EPOCH_MS")
                ?.toLongOrNull()
                ?: 1_800_000_000_000L
            val maxExamples = System.getenv("DANMAKU_LIVE_QA_MAX_EXAMPLES")
                ?.toIntOrNull()
                ?.coerceAtLeast(1)
                ?: 12
            return LiveQaConfig(
                databasePath = databasePath,
                libraryRoot = libraryRoot,
                reportPath = reportPath,
                appliedAtEpochMs = appliedAtEpochMs,
                maxExamples = maxExamples,
            )
        }
    }
}

private data class LiveQaIssuePlan(
    val issue: LibraryQualityIssue,
    val plan: DesktopLibraryQualityMappingApplyPlan,
)

private data class LiveQaLibrary(
    val indexedLibrary: IndexedLocalLibrary,
    val source: String,
)
