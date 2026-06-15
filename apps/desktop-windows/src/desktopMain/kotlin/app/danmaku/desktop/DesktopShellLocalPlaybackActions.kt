package app.danmaku.desktop

import app.danmaku.domain.LibraryMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

internal class DesktopShellLocalPlaybackActions(
    private val scope: CoroutineScope,
    private val localPlaybackPreparer: DesktopLocalPlaybackPreparer,
    private val dandanplayDanmakuResolver: DesktopDandanplayDanmakuResolver,
    private val animeMetadataResolver: DesktopAnimeMetadataResolver,
    private val settingsState: DesktopShellSettingsState,
    private val libraryState: DesktopShellLibraryState,
    private val queuePlaybackUntilHostReady: (DesktopPlaybackRequest) -> Unit,
    private val selectDanmakuFile: (String) -> Path?,
    private val appendDiagnostic: (String, String) -> Unit,
) {
    fun prepareLocalPlayback(
        item: LibraryMediaItem,
        loadAfterPrepare: Boolean = false,
        refreshDandanplay: Boolean = false,
        preferredDandanplayEpisodeId: Long? = null,
    ) {
        val library = libraryState.indexedLibrary
        if (library == null) {
            libraryState.dandanplayCacheStatus = dandanplayStatusMessage(
                mediaId = item.id,
                summary = "library is not indexed; cannot check danmaku",
                details = item.dandanplayStatusContext(settingsState.dandanplaySettings),
            )
            libraryState.libraryError = "Index or scan a local library before preparing playback."
            appendDiagnostic("playback", "Cannot prepare local playback; library is not indexed")
            return
        }
        scope.launch {
            appendDiagnostic("playback", "Preparing local library playback: ${item.id}")
            libraryState.isPreparingLocalPlayback = true
            libraryState.dandanplayCacheStatus = dandanplayStatusMessage(
                mediaId = item.id,
                summary = when {
                    preferredDandanplayEpisodeId != null -> "loading selected dandanplay match..."
                    refreshDandanplay -> "refreshing dandanplay match..."
                    else -> "checking dandanplay match..."
                },
                details = item.dandanplayStatusContext(settingsState.dandanplaySettings) +
                    listOfNotNull(
                        preferredDandanplayEpisodeId?.let {
                            DandanplayPlaybackUiDetail("Requested episode ID", it.toString())
                        },
                    ),
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    val preparation = localPlaybackPreparer.prepare(library, item)
                    if (!settingsState.dandanplaySettings.isFetchEnabled) {
                        PreparedLocalPlaybackResult(
                            preparation = preparation,
                            dandanplayResolution = null,
                            dandanplayError = null,
                        )
                    } else {
                        val mediaPath = library.requireMediaPath(item)
                        val dandanplayResult = runCatching {
                            dandanplayDanmakuResolver.resolve(
                                mediaId = item.id,
                                mediaPath = mediaPath,
                                forceRefresh = refreshDandanplay,
                                preferredEpisodeId = preferredDandanplayEpisodeId,
                            )
                        }
                        val dandanplaySubtitle = dandanplayResult
                            .getOrNull()
                            ?.subtitle
                        PreparedLocalPlaybackResult(
                            preparation = if (dandanplaySubtitle == null) {
                                preparation
                            } else {
                                preparation.copy(subtitles = preparation.subtitles + dandanplaySubtitle)
                            },
                            dandanplayResolution = dandanplayResult.getOrNull(),
                            dandanplayError = dandanplayResult.exceptionOrNull(),
                        )
                    }
                }
            }.onSuccess { result ->
                libraryState.selectedLocalPlaybackPreparation = result.preparation
                libraryState.libraryError = null
                result.dandanplayResolution?.match?.animeId?.let { animeId ->
                    scope.launch {
                        val metadataRefreshResult = withContext(Dispatchers.IO) {
                            runCatching {
                                animeMetadataResolver.refreshDandanplayMetadataForItem(
                                    item = item,
                                    animeId = animeId,
                                    forceRefresh = refreshDandanplay,
                                )
                            }
                        }
                        metadataRefreshResult.onSuccess { posterPath ->
                            if (posterPath == null) {
                                appendDiagnostic(
                                    "metadata",
                                    "Refreshed dandanplay metadata for ${item.seriesTitle}; no poster URL available",
                                )
                            } else {
                                appendDiagnostic(
                                    "metadata",
                                    "Refreshed dandanplay poster metadata for ${item.seriesTitle}",
                                )
                            }
                            libraryState.libraryMetadataVersion += 1
                        }.onFailure { error ->
                            appendDiagnostic(
                                "metadata",
                                "Dandanplay poster metadata refresh failed for ${item.seriesTitle}: " +
                                    (error.message ?: error::class.simpleName),
                            )
                        }
                    }
                }
                appendDiagnostic(
                    "playback",
                    "Prepared local playback: ${item.id}; source=${result.preparation.source.path}; resume=${result.preparation.resumePositionMs}",
                )
                when {
                    !settingsState.dandanplaySettings.isFetchEnabled ->
                        appendDiagnostic("danmaku", "dandanplay fetching is not configured; no automatic danmaku overlay attached")
                    result.dandanplayError != null ->
                        appendDiagnostic("danmaku", "dandanplay fetch failed for ${item.id}: ${result.dandanplayError.message}")
                    result.dandanplayResolution?.subtitle != null ->
                        appendDiagnostic(
                            "danmaku",
                            "Attached dandanplay match ${result.dandanplayResolution.match?.displayTitle ?: "unknown"} " +
                                "with ${result.dandanplayResolution.eventCount} comments from ${result.dandanplayResolution.source.name.lowercase()}",
                        )
                    result.dandanplayResolution?.match != null ->
                        appendDiagnostic(
                            "danmaku",
                            "dandanplay matched ${result.dandanplayResolution.match.displayTitle} but returned no comments",
                        )
                    else ->
                        appendDiagnostic("danmaku", "dandanplay found no match for ${item.id}")
                }
                libraryState.dandanplayCacheStatus = when {
                    !settingsState.dandanplaySettings.isFetchEnabled ->
                        dandanplayStatusMessage(
                            mediaId = item.id,
                            summary = "dandanplay fetching is not configured",
                            details = item.dandanplayStatusContext(settingsState.dandanplaySettings) +
                                DandanplayPlaybackUiDetail(
                                    "Result",
                                    "automatic danmaku matching skipped",
                                ),
                        )
                    result.dandanplayError != null ->
                        dandanplayStatusMessage(
                            mediaId = item.id,
                            summary = "dandanplay match failed",
                            details = item.dandanplayStatusContext(settingsState.dandanplaySettings) +
                                DandanplayPlaybackUiDetail(
                                    "Error",
                                    result.dandanplayError.readableMessage(),
                                ),
                        )
                    result.dandanplayResolution != null -> {
                        val resolution = result.dandanplayResolution
                        val playbackDetail = when {
                            resolution.subtitle == null -> null
                            loadAfterPrepare -> DandanplayPlaybackUiDetail("Playback", "loading into player now")
                            else -> DandanplayPlaybackUiDetail(
                                "Playback",
                                "overlay prepared; use Load into player to attach it",
                            )
                        }
                        val status = dandanplayStatusFromResolution(item.id, resolution)
                        status.copy(
                            details = item.dandanplayStatusContext(settingsState.dandanplaySettings) +
                                status.details +
                                listOfNotNull(playbackDetail),
                        )
                    }
                    else ->
                        dandanplayStatusMessage(
                            mediaId = item.id,
                            summary = "dandanplay returned no result",
                            details = item.dandanplayStatusContext(settingsState.dandanplaySettings),
                        )
                }
                if (loadAfterPrepare) {
                    appendDiagnostic("playback", "Auto-loading prepared local playback: ${item.id}")
                    queuePlaybackUntilHostReady(result.preparation.toPlaybackRequest())
                }
            }.onFailure {
                libraryState.libraryError = it.message
                libraryState.dandanplayCacheStatus = dandanplayStatusMessage(
                    mediaId = item.id,
                    summary = "prepare failed before danmaku could load",
                    details = item.dandanplayStatusContext(settingsState.dandanplaySettings) +
                        DandanplayPlaybackUiDetail("Error", it.readableMessage()),
                )
                appendDiagnostic("playback", "Prepare local playback failed: ${it.message}")
            }
            libraryState.isPreparingLocalPlayback = false
        }
    }

    fun inspectCachedDandanplay(item: LibraryMediaItem) {
        val library = libraryState.indexedLibrary
        if (library == null) {
            libraryState.dandanplayCacheStatus = dandanplayStatusMessage(
                mediaId = item.id,
                summary = "library is not indexed; cannot check cached danmaku",
                details = item.dandanplayStatusContext(settingsState.dandanplaySettings),
            )
            return
        }
        val existingStatus = libraryState.dandanplayCacheStatus
        val hasPreparedOverlay = libraryState.selectedLocalPlaybackPreparation
            ?.takeIf { it.item.id == item.id }
            ?.subtitles
            ?.any(DesktopPlaybackSubtitle::isDanmakuOverlay) == true
        if (hasPreparedOverlay || existingStatus?.mediaId == item.id && !existingStatus.summary.contains("checking")) {
            return
        }

        scope.launch {
            libraryState.dandanplayCacheStatus = dandanplayStatusMessage(
                mediaId = item.id,
                summary = "checking cached danmaku...",
                details = item.dandanplayStatusContext(settingsState.dandanplaySettings),
            )
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val mediaPath = library.requireMediaPath(item)
                    dandanplayDanmakuResolver.resolveCached(item.id, mediaPath)
                }
            }
            result.onSuccess { resolution ->
                libraryState.dandanplayCacheStatus = if (resolution == null) {
                    dandanplayStatusMessage(
                        mediaId = item.id,
                        summary = "no valid cached danmaku",
                        details = item.dandanplayStatusContext(settingsState.dandanplaySettings) +
                            DandanplayPlaybackUiDetail(
                                "Cache",
                                "prepare or refresh danmaku to fetch comments",
                            ),
                    )
                } else {
                    val status = dandanplayCachedInspectionStatus(item.id, resolution)
                    status.copy(
                        details = item.dandanplayStatusContext(settingsState.dandanplaySettings) + status.details,
                    )
                }
            }.onFailure { error ->
                libraryState.dandanplayCacheStatus = dandanplayStatusMessage(
                    mediaId = item.id,
                    summary = "cached danmaku check failed",
                    details = item.dandanplayStatusContext(settingsState.dandanplaySettings) +
                        DandanplayPlaybackUiDetail("Error", error.readableMessage()),
                )
            }
        }
    }

    fun refreshPreparedDandanplay(preparation: DesktopLocalPlaybackPreparation) {
        if (!settingsState.dandanplaySettings.isFetchEnabled) {
            libraryState.dandanplayCacheStatus = dandanplayStatusMessage(
                mediaId = preparation.item.id,
                summary = "dandanplay fetching is not configured",
                details = preparation.item.dandanplayStatusContext(settingsState.dandanplaySettings) +
                    DandanplayPlaybackUiDetail(
                        "Result",
                        "automatic danmaku refresh skipped",
                    ),
            )
            appendDiagnostic("danmaku", "Cannot refresh dandanplay cache; provider fetching is not configured")
            return
        }
        appendDiagnostic("danmaku", "Refreshing dandanplay cache for ${preparation.item.id}")
        prepareLocalPlayback(preparation.item, refreshDandanplay = true)
    }

    fun selectPreparedDandanplayMatch(
        preparation: DesktopLocalPlaybackPreparation,
        match: DandanplayMatch,
    ) {
        appendDiagnostic(
            "danmaku",
            "Selecting dandanplay match ${match.displayTitle} (${match.episodeId}) for ${preparation.item.id}",
        )
        prepareLocalPlayback(
            item = preparation.item,
            refreshDandanplay = true,
            preferredDandanplayEpisodeId = match.episodeId,
        )
    }

    fun clearPreparedDandanplayCache(preparation: DesktopLocalPlaybackPreparation) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    dandanplayDanmakuResolver.clearCache(preparation.item.id)
                }
            }.onSuccess {
                libraryState.selectedLocalPlaybackPreparation = preparation.withoutDanmakuOverlays()
                libraryState.dandanplayCacheStatus = dandanplayStatusMessage(
                    mediaId = preparation.item.id,
                    summary = "dandanplay cache cleared",
                    details = preparation.item.dandanplayStatusContext(settingsState.dandanplaySettings),
                )
                appendDiagnostic("danmaku", "Cleared dandanplay cache for ${preparation.item.id}")
            }.onFailure {
                appendDiagnostic("danmaku", "Failed to clear dandanplay cache for ${preparation.item.id}: ${it.message}")
            }
        }
    }

    fun clearPreparedDanmakuOverlay(preparation: DesktopLocalPlaybackPreparation) {
        libraryState.selectedLocalPlaybackPreparation = preparation.withoutDanmakuOverlays()
        libraryState.dandanplayCacheStatus = dandanplayStatusMessage(
            mediaId = preparation.item.id,
            summary = "prepared danmaku overlay removed",
            details = preparation.item.dandanplayStatusContext(settingsState.dandanplaySettings),
        )
        appendDiagnostic("danmaku", "Removed prepared danmaku overlay for ${preparation.item.id}")
    }

    fun attachManualDanmaku(preparation: DesktopLocalPlaybackPreparation) {
        val danmakuPath = selectDanmakuFile("Choose local danmaku XML or JSON file") ?: return
        scope.launch {
            appendDiagnostic("danmaku", "Rendering manual danmaku overlay: $danmakuPath")
            runCatching {
                withContext(Dispatchers.IO) {
                    DesktopManualDanmakuOverlayRenderer.render(
                        inputPath = danmakuPath,
                        settings = settingsState.danmakuSettings,
                    )
                }
            }.onSuccess { overlay ->
                libraryState.selectedLocalPlaybackPreparation = preparation.withManualDanmakuOverlay(overlay)
                libraryState.dandanplayCacheStatus = dandanplayStatusMessage(
                    mediaId = preparation.item.id,
                    summary = "manual danmaku: attached ${overlay.eventCount} comments",
                    details = preparation.item.dandanplayStatusContext(settingsState.dandanplaySettings) +
                        listOf(
                            DandanplayPlaybackUiDetail("Source file", overlay.inputPath.toString()),
                            DandanplayPlaybackUiDetail("Comments", "${overlay.eventCount} comments"),
                        ),
                )
                appendDiagnostic(
                    "danmaku",
                    "Attached manual danmaku ${overlay.inputPath.fileName} with ${overlay.eventCount} comments",
                )
            }.onFailure {
                appendDiagnostic("danmaku", "Manual danmaku attach failed: ${it.message}")
            }
        }
    }
}
