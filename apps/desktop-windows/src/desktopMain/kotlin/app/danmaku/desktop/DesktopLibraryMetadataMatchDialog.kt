package app.danmaku.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.danmaku.domain.ExternalAnimeMatchCandidate
import app.danmaku.domain.ExternalAnimeMatchQuery
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.displayName
import kotlinx.coroutines.launch
import java.nio.file.Path

@Composable
internal fun MetadataMatchDialog(
    strings: DesktopStrings,
    selectedSeries: LibrarySeries,
    currentMappings: List<ExternalAnimeMapping>,
    externalAnimeProviderSettings: ExternalAnimeProviderSettings,
    onSearchExternalAnimeMatches: suspend (ExternalAnimeMatchQuery, Set<ExternalAnimeProvider>) -> Result<List<ExternalAnimeMatchCandidate>>,
    onFetchMetadataMatchPoster: suspend (String?) -> Path?,
    onSaveExternalAnimeMapping: (LibrarySeries, ExternalAnimeProvider, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var queryText by remember(selectedSeries.id) { mutableStateOf(selectedSeries.title) }
    val myAnimeListSearchAvailable = externalAnimeProviderSettings.myAnimeListClientId != null
    val bangumiSearchAvailable = externalAnimeProviderSettings.bangumiBaseUrl.isNotBlank() &&
        externalAnimeProviderSettings.bangumiUserAgent.isNotBlank()
    var includeMyAnimeList by remember(selectedSeries.id, myAnimeListSearchAvailable) {
        mutableStateOf(myAnimeListSearchAvailable && currentMappings.none { it.animeId.provider == ExternalAnimeProvider.MY_ANIME_LIST })
    }
    var includeBangumi by remember(selectedSeries.id, bangumiSearchAvailable) {
        mutableStateOf(bangumiSearchAvailable && currentMappings.none { it.animeId.provider == ExternalAnimeProvider.BANGUMI })
    }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var candidates by remember(selectedSeries.id) { mutableStateOf<List<ExternalAnimeMatchCandidate>>(emptyList()) }

    fun runSearch() {
        val title = queryText.trim()
        if (title.isBlank() || isSearching) return
        val providers = buildSet {
            if (includeMyAnimeList) add(ExternalAnimeProvider.MY_ANIME_LIST)
            if (includeBangumi) add(ExternalAnimeProvider.BANGUMI)
        }
        if (providers.isEmpty()) {
            searchError = strings.metadataMatchSelectProviderError
            candidates = emptyList()
            return
        }
        isSearching = true
        searchError = null
        scope.launch {
            val result = onSearchExternalAnimeMatches(
                selectedSeries.externalAnimeMatchQuery(searchTitle = title),
                providers,
            )
            result.onSuccess {
                candidates = it
                searchError = if (it.isEmpty()) strings.metadataMatchNoCandidates(title) else null
            }.onFailure {
                candidates = emptyList()
                searchError = it.metadataMatchSearchErrorMessage(strings)
            }
            isSearching = false
        }
    }

    AlertDialog(
        modifier = Modifier.width(860.dp),
        onDismissRequest = onDismiss,
        title = { Text(strings.metadataMatchTitle) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    strings.metadataMatchDescription(selectedSeries.title),
                    color = DanmakuColors.TextMuted,
                )
                OutlinedTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    label = { Text(strings.metadataMatchSearchTitleLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetadataMatchProviderToggle(
                        label = "MyAnimeList",
                        detail = externalAnimeProviderSettings.myAnimeListStatusLabel(strings),
                        selected = includeMyAnimeList,
                        enabled = myAnimeListSearchAvailable,
                        onToggle = {
                            if (myAnimeListSearchAvailable) {
                                includeMyAnimeList = !includeMyAnimeList
                            }
                        },
                    )
                    MetadataMatchProviderToggle(
                        label = "Bangumi",
                        detail = externalAnimeProviderSettings.bangumiStatusLabel(strings),
                        selected = includeBangumi,
                        enabled = bangumiSearchAvailable,
                        onToggle = {
                            if (bangumiSearchAvailable) {
                                includeBangumi = !includeBangumi
                            }
                        },
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    LibraryActionButton(
                        imageVector = Icons.Filled.Search,
                        label = if (isSearching) strings.searchingAction else strings.searchAction,
                        enabled = !isSearching && queryText.isNotBlank(),
                        onClick = ::runSearch,
                    )
                }
                if (!myAnimeListSearchAvailable || !bangumiSearchAvailable) {
                    Text(
                        buildList {
                            if (!myAnimeListSearchAvailable) add(strings.metadataMatchMyAnimeListUnavailable)
                            if (!bangumiSearchAvailable) add(strings.metadataMatchBangumiUnavailable)
                        }.joinToString("; "),
                        color = DanmakuColors.Warning,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                currentMappings.takeIf { it.isNotEmpty() }?.let { mappings ->
                    Text(
                        strings.metadataMatchCurrentMappingsPrefix + " " +
                            mappings.joinToString { "${it.animeId.provider.displayName} #${it.animeId.value}" },
                        color = DanmakuColors.TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                searchError?.let { error ->
                    Text(error, color = DanmakuColors.Warning, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Divider(color = DanmakuColors.SurfaceRaised)
                if (candidates.isEmpty()) {
                    EmptyState(strings.metadataMatchEmptyState)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(candidates, key = { "${it.anime.id.provider.name}:${it.anime.id.value}" }) { candidate ->
                            MetadataMatchCandidateRow(
                                strings = strings,
                                candidate = candidate,
                                alreadyMapped = currentMappings.any { it.animeId == candidate.anime.id },
                                onFetchPoster = onFetchMetadataMatchPoster,
                                onUse = {
                                    onSaveExternalAnimeMapping(
                                        selectedSeries,
                                        candidate.anime.id.provider,
                                        candidate.anime.id.value.toString(),
                                    )
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = ::runSearch,
                enabled = !isSearching && queryText.isNotBlank(),
            ) {
                Text(if (isSearching) strings.searchingAction else strings.searchAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.closeAction)
            }
        },
    )
}

@Composable
internal fun MetadataMatchProviderToggle(
    label: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val backgroundColor = when {
        selected -> DanmakuColors.AccentSoft
        enabled -> DanmakuColors.SurfaceRaised
        else -> DanmakuColors.SurfaceRaised.copy(alpha = 0.48f)
    }
    val iconColor = when {
        selected -> DanmakuColors.Good
        enabled -> DanmakuColors.TextMuted
        else -> DanmakuColors.Warning
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundColor)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = when {
                selected -> Icons.Filled.CheckCircle
                enabled -> Icons.Filled.Search
                else -> Icons.Filled.Warning
            },
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(16.dp),
        )
        Column {
            Text(label, maxLines = 1, color = if (enabled) Color.White else DanmakuColors.TextMuted)
            Text(detail, maxLines = 1, color = if (enabled) DanmakuColors.TextMuted else DanmakuColors.Warning)
        }
    }
}

@Composable
internal fun MetadataMatchCandidateRow(
    strings: DesktopStrings,
    candidate: ExternalAnimeMatchCandidate,
    alreadyMapped: Boolean,
    onFetchPoster: suspend (String?) -> Path?,
    onUse: () -> Unit,
) {
    val anime = candidate.anime
    var posterPath by remember(anime.id, anime.imageUrl) { mutableStateOf<Path?>(null) }
    var isPosterLoading by remember(anime.id, anime.imageUrl) { mutableStateOf(!anime.imageUrl.isNullOrBlank()) }

    LaunchedEffect(anime.id, anime.imageUrl) {
        val imageUrl = anime.imageUrl
        posterPath = null
        isPosterLoading = !imageUrl.isNullOrBlank()
        if (!imageUrl.isNullOrBlank()) {
            posterPath = runCatching { onFetchPoster(imageUrl) }.getOrNull()
            isPosterLoading = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DanmakuColors.SurfaceRaised.copy(alpha = 0.62f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetadataMatchPosterPreview(
            posterPath = posterPath,
            title = anime.titles.primary,
            isLoading = isPosterLoading,
            loadingLabel = strings.posterLoadingLabel,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(anime.titles.primary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildList {
                    add("${anime.id.provider.displayName} #${anime.id.value}")
                    anime.episodeCount?.let { add("$it ${strings.episodesSuffix}") }
                    anime.startYear?.let { add(it.toString()) }
                    candidate.matchedTitle?.takeIf { it != anime.titles.primary }?.let {
                        add("${strings.metadataMatchMatchedTitlePrefix} $it")
                    }
                    if (anime.imageUrl != null && posterPath == null) {
                        add(if (isPosterLoading) strings.posterLoadingLabel else strings.posterUnavailableLabel)
                    }
                }.joinToString(" - "),
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            anime.summary?.let {
                Text(it, color = DanmakuColors.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (candidate.evidence.isNotEmpty()) {
                Text(
                    candidate.evidence.take(3).joinToString(" - "),
                    color = DanmakuColors.Good,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        StatusPill(
            text = candidate.confidence.formatConfidence(),
            icon = Icons.Filled.CheckCircle,
            active = candidate.confidence >= 0.7,
            color = if (candidate.confidence >= 0.7) DanmakuColors.Good else DanmakuColors.Accent,
        )
        Button(
            enabled = !alreadyMapped,
            onClick = onUse,
        ) {
            Text(if (alreadyMapped) strings.mappedAction else strings.useAction)
        }
    }
}

@Composable
internal fun MetadataMatchPosterPreview(
    posterPath: Path?,
    title: String,
    isLoading: Boolean,
    loadingLabel: String,
) {
    val bitmap = rememberLocalImageBitmap(posterPath)
    Box(
        modifier = Modifier
            .width(54.dp)
            .height(76.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(DanmakuColors.AccentSoft),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = title.initialsForPoster(),
                color = Color.White,
                style = MaterialTheme.typography.subtitle2,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.56f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = loadingLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
