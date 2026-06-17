package app.danmaku.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.danmaku.domain.ExternalAnimeMatchCandidate
import app.danmaku.domain.ExternalAnimeMatchQuery
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.displayName
import java.nio.file.Path

@Composable
internal fun ExternalAnimeMappingPanel(
    strings: DesktopStrings,
    selectedSeries: LibrarySeries,
    selectedItem: LibraryMediaItem,
    seriesMappings: List<ExternalAnimeMapping>,
    itemMappings: List<DesktopExternalAnimeItemMapping>,
    externalAnimeProviderSettings: ExternalAnimeProviderSettings,
    onSaveExternalAnimeMapping: (LibrarySeries, ExternalAnimeProvider, String) -> Unit,
    onDeleteExternalAnimeMapping: (LibrarySeries, ExternalAnimeProvider) -> Unit,
    onSaveExternalAnimeItemMapping: (LibraryMediaItem, ExternalAnimeProvider, String) -> Unit,
    onDeleteExternalAnimeItemMapping: (LibraryMediaItem, ExternalAnimeProvider) -> Unit,
    onSearchExternalAnimeMatches: suspend (ExternalAnimeMatchQuery, Set<ExternalAnimeProvider>) -> Result<List<ExternalAnimeMatchCandidate>>,
    onFetchMetadataMatchPoster: suspend (String?) -> Path?,
) {
    var showMatchDialog by remember(selectedSeries.id) { mutableStateOf(false) }
    val malMapping = seriesMappings.firstOrNull { it.animeId.provider == ExternalAnimeProvider.MY_ANIME_LIST }
    val bangumiMapping = seriesMappings.firstOrNull { it.animeId.provider == ExternalAnimeProvider.BANGUMI }
    val dandanplayItemMapping = itemMappings.firstOrNull { it.animeId.provider == ExternalAnimeProvider.DANDANPLAY }
    val displayedDandanplayId = dandanplayItemMapping?.animeId?.value
        ?: selectedItem.animeMetadata
            ?.animeId
            ?.takeIf { it.provider == ExternalAnimeProvider.DANDANPLAY }
            ?.value

    Divider(color = DanmakuColors.SurfaceRaised)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(strings.externalIdsTitle, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        LibraryActionButton(
            imageVector = Icons.Filled.Search,
            label = strings.matchAction,
            onClick = { showMatchDialog = true },
        )
    }
    ExternalSeriesMappingRow(
        strings = strings,
        provider = ExternalAnimeProvider.MY_ANIME_LIST,
        mapping = malMapping,
        selectedSeries = selectedSeries,
        onSave = onSaveExternalAnimeMapping,
        onDelete = onDeleteExternalAnimeMapping,
    )
    ExternalSeriesMappingRow(
        strings = strings,
        provider = ExternalAnimeProvider.BANGUMI,
        mapping = bangumiMapping,
        selectedSeries = selectedSeries,
        onSave = onSaveExternalAnimeMapping,
        onDelete = onDeleteExternalAnimeMapping,
    )
    ExternalItemMappingRow(
        strings = strings,
        provider = ExternalAnimeProvider.DANDANPLAY,
        currentId = displayedDandanplayId,
        hasManualMapping = dandanplayItemMapping != null,
        selectedItem = selectedItem,
        onSave = onSaveExternalAnimeItemMapping,
        onDelete = onDeleteExternalAnimeItemMapping,
    )
    if (showMatchDialog) {
        MetadataMatchDialog(
            strings = strings,
            selectedSeries = selectedSeries,
            currentMappings = seriesMappings,
            externalAnimeProviderSettings = externalAnimeProviderSettings,
            onSearchExternalAnimeMatches = onSearchExternalAnimeMatches,
            onFetchMetadataMatchPoster = onFetchMetadataMatchPoster,
            onSaveExternalAnimeMapping = onSaveExternalAnimeMapping,
            onDismiss = { showMatchDialog = false },
        )
    }
}

@Composable
internal fun ExternalSeriesMappingRow(
    strings: DesktopStrings,
    provider: ExternalAnimeProvider,
    mapping: ExternalAnimeMapping?,
    selectedSeries: LibrarySeries,
    onSave: (LibrarySeries, ExternalAnimeProvider, String) -> Unit,
    onDelete: (LibrarySeries, ExternalAnimeProvider) -> Unit,
) {
    var animeIdText by remember(selectedSeries.id, provider, mapping?.animeId?.value) {
        mutableStateOf(mapping?.animeId?.value?.toString().orEmpty())
    }
    ExternalMappingEditRow(
        label = provider.displayName,
        value = animeIdText,
        onValueChange = { animeIdText = it.filter(Char::isDigit) },
        saveLabel = if (mapping == null) strings.linkAction else strings.replaceAction,
        deleteEnabled = mapping != null,
        onSave = { onSave(selectedSeries, provider, animeIdText) },
        onDelete = { onDelete(selectedSeries, provider) },
        removeLabel = strings.removeAction,
    )
}

@Composable
internal fun ExternalItemMappingRow(
    strings: DesktopStrings,
    provider: ExternalAnimeProvider,
    currentId: Long?,
    hasManualMapping: Boolean,
    selectedItem: LibraryMediaItem,
    onSave: (LibraryMediaItem, ExternalAnimeProvider, String) -> Unit,
    onDelete: (LibraryMediaItem, ExternalAnimeProvider) -> Unit,
) {
    var animeIdText by remember(selectedItem.id, provider, currentId) {
        mutableStateOf(currentId?.toString().orEmpty())
    }
    ExternalMappingEditRow(
        label = strings.providerEpisodeLabel(provider.displayName),
        value = animeIdText,
        onValueChange = { animeIdText = it.filter(Char::isDigit) },
        saveLabel = if (hasManualMapping) strings.replaceAction else strings.correctAction,
        deleteEnabled = hasManualMapping,
        onSave = { onSave(selectedItem, provider, animeIdText) },
        onDelete = { onDelete(selectedItem, provider) },
        removeLabel = strings.removeAction,
    )
}

@Composable
internal fun ExternalMappingEditRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    saveLabel: String,
    deleteEnabled: Boolean,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    removeLabel: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            enabled = value.toLongOrNull()?.let { it > 0 } == true,
            onClick = onSave,
        ) {
            Text(saveLabel)
        }
        Button(
            enabled = deleteEnabled,
            onClick = onDelete,
        ) {
            Text(removeLabel)
        }
    }
}
