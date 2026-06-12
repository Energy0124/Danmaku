package app.danmaku.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.intl.Locale
import danmaku.apps.desktop_windows.generated.resources.Res
import danmaku.apps.desktop_windows.generated.resources.desktop_episodes_suffix
import danmaku.apps.desktop_windows.generated.resources.desktop_home_action
import danmaku.apps.desktop_windows.generated.resources.desktop_library_action
import danmaku.apps.desktop_windows.generated.resources.desktop_media_hub
import danmaku.apps.desktop_windows.generated.resources.desktop_player_status_prefix
import danmaku.apps.desktop_windows.generated.resources.desktop_rescan_library
import danmaku.apps.desktop_windows.generated.resources.desktop_search_label
import danmaku.apps.desktop_windows.generated.resources.desktop_settings_description
import danmaku.apps.desktop_windows.generated.resources.desktop_settings_title
import danmaku.apps.desktop_windows.generated.resources.desktop_shell_subtitle
import danmaku.apps.desktop_windows.generated.resources.desktop_tab_downloads
import danmaku.apps.desktop_windows.generated.resources.desktop_tab_home
import danmaku.apps.desktop_windows.generated.resources.desktop_tab_media_library
import danmaku.apps.desktop_windows.generated.resources.desktop_tab_playback
import danmaku.apps.desktop_windows.generated.resources.desktop_tab_profile
import danmaku.apps.desktop_windows.generated.resources.desktop_tab_tracking
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun rememberDesktopResourceStrings(language: DesktopUiLanguage): DesktopStrings {
    val useResourceStrings = language.matchesResourceLocale(Locale.current)
    val tabHome = stringResource(Res.string.desktop_tab_home)
    val tabPlayback = stringResource(Res.string.desktop_tab_playback)
    val tabMediaLibrary = stringResource(Res.string.desktop_tab_media_library)
    val tabDownloads = stringResource(Res.string.desktop_tab_downloads)
    val tabTracking = stringResource(Res.string.desktop_tab_tracking)
    val tabProfile = stringResource(Res.string.desktop_tab_profile)
    val mediaHub = stringResource(Res.string.desktop_media_hub)
    val shellSubtitle = stringResource(Res.string.desktop_shell_subtitle)
    val searchLabel = stringResource(Res.string.desktop_search_label)
    val playerStatusPrefix = stringResource(Res.string.desktop_player_status_prefix)
    val episodesSuffix = stringResource(Res.string.desktop_episodes_suffix)
    val rescanLibrary = stringResource(Res.string.desktop_rescan_library)
    val settingsTitle = stringResource(Res.string.desktop_settings_title)
    val settingsDescription = stringResource(Res.string.desktop_settings_description)
    val homeAction = stringResource(Res.string.desktop_home_action)
    val libraryAction = stringResource(Res.string.desktop_library_action)

    return remember(
        language,
        useResourceStrings,
        tabHome,
        tabPlayback,
        tabMediaLibrary,
        tabDownloads,
        tabTracking,
        tabProfile,
        mediaHub,
        shellSubtitle,
        searchLabel,
        playerStatusPrefix,
        episodesSuffix,
        rescanLibrary,
        settingsTitle,
        settingsDescription,
        homeAction,
        libraryAction,
    ) {
        language.strings.apply {
            if (useResourceStrings) {
                tabTitles = mapOf(
                    DesktopShellTab.HOME to tabHome,
                    DesktopShellTab.PLAYBACK to tabPlayback,
                    DesktopShellTab.MEDIA_LIBRARY to tabMediaLibrary,
                    DesktopShellTab.DOWNLOADS to tabDownloads,
                    DesktopShellTab.TRACKING to tabTracking,
                    DesktopShellTab.PROFILE to tabProfile,
                )
                this.mediaHub = mediaHub
                this.shellSubtitle = shellSubtitle
                this.searchLabel = searchLabel
                this.playerStatusPrefix = playerStatusPrefix
                this.episodesSuffix = episodesSuffix
                this.rescanLibrary = rescanLibrary
                this.settingsTitle = settingsTitle
                this.settingsDescription = settingsDescription
                this.homeAction = homeAction
                this.libraryAction = libraryAction
            }
        }
    }
}

private fun DesktopUiLanguage.matchesResourceLocale(locale: Locale): Boolean =
    when (this) {
        DesktopUiLanguage.ENGLISH -> locale.language.equals("en", ignoreCase = true)
        DesktopUiLanguage.ZH_TW ->
            locale.language.equals("zh", ignoreCase = true) &&
                locale.region.equals("TW", ignoreCase = true)
    }
