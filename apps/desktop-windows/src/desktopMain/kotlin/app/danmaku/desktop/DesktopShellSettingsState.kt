package app.danmaku.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal class DesktopShellSettingsState(
    playbackPreferencesStore: DesktopPlaybackPreferencesStore,
    dandanplayCredentialStore: DandanplayCredentialStore,
    externalAnimeCredentialStore: ExternalAnimeCredentialStore,
    catalogStore: DesktopLibraryCatalogStore,
) {
    var playbackPreferences by mutableStateOf(playbackPreferencesStore.load())
    var danmakuSettings by mutableStateOf(playbackPreferences.danmakuSettings)
    var dandanplaySettings by mutableStateOf(dandanplayCredentialStore.loadSettings())
    var externalAnimeProviderSettings by mutableStateOf(externalAnimeCredentialStore.loadSettings())
    var dandanplayConnectionTestStatus by mutableStateOf<SettingsConnectionTestStatus?>(null)
    var myAnimeListConnectionTestStatus by mutableStateOf<SettingsConnectionTestStatus?>(null)
    var bangumiConnectionTestStatus by mutableStateOf<SettingsConnectionTestStatus?>(null)
    var localServerConnectionTestStatus by mutableStateOf<SettingsConnectionTestStatus?>(null)
    var dandanplayCacheEntries by mutableStateOf(catalogStore.loadDandanplayCommentCaches())

    fun updatePlaybackPreferences(updatedPreferences: DesktopPlaybackPreferences) {
        playbackPreferences = updatedPreferences
        danmakuSettings = updatedPreferences.danmakuSettings
    }
}

@Composable
internal fun rememberDesktopShellSettingsState(
    playbackPreferencesStore: DesktopPlaybackPreferencesStore,
    dandanplayCredentialStore: DandanplayCredentialStore,
    externalAnimeCredentialStore: ExternalAnimeCredentialStore,
    catalogStore: DesktopLibraryCatalogStore,
): DesktopShellSettingsState =
    remember(playbackPreferencesStore, dandanplayCredentialStore, externalAnimeCredentialStore, catalogStore) {
        DesktopShellSettingsState(
            playbackPreferencesStore = playbackPreferencesStore,
            dandanplayCredentialStore = dandanplayCredentialStore,
            externalAnimeCredentialStore = externalAnimeCredentialStore,
            catalogStore = catalogStore,
        )
    }
