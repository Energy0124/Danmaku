package app.danmaku.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import danmaku.apps.desktop_windows.generated.resources.Res
import danmaku.apps.desktop_windows.generated.resources.desktop_ani_rss_imports_managed_downloads_text
import danmaku.apps.desktop_windows.generated.resources.desktop_app_label
import danmaku.apps.desktop_windows.generated.resources.desktop_app_log_label
import danmaku.apps.desktop_windows.generated.resources.desktop_credentials_privacy_text
import danmaku.apps.desktop_windows.generated.resources.desktop_episodes_suffix
import danmaku.apps.desktop_windows.generated.resources.desktop_focus_mode_label
import danmaku.apps.desktop_windows.generated.resources.desktop_home_action
import danmaku.apps.desktop_windows.generated.resources.desktop_imports_label
import danmaku.apps.desktop_windows.generated.resources.desktop_language_description
import danmaku.apps.desktop_windows.generated.resources.desktop_language_title
import danmaku.apps.desktop_windows.generated.resources.desktop_library_action
import danmaku.apps.desktop_windows.generated.resources.desktop_library_settings_description
import danmaku.apps.desktop_windows.generated.resources.desktop_media_hub
import danmaku.apps.desktop_windows.generated.resources.desktop_metadata_label
import danmaku.apps.desktop_windows.generated.resources.desktop_metadata_refresh_library_details_text
import danmaku.apps.desktop_windows.generated.resources.desktop_mpv_executor_label
import danmaku.apps.desktop_windows.generated.resources.desktop_mpv_log_label
import danmaku.apps.desktop_windows.generated.resources.desktop_mpv_renderer_description
import danmaku.apps.desktop_windows.generated.resources.desktop_player_status_prefix
import danmaku.apps.desktop_windows.generated.resources.desktop_player_focus_mode_description
import danmaku.apps.desktop_windows.generated.resources.desktop_playback_runtime_title
import danmaku.apps.desktop_windows.generated.resources.desktop_primary_targets_label
import danmaku.apps.desktop_windows.generated.resources.desktop_privacy_credentials_description
import danmaku.apps.desktop_windows.generated.resources.desktop_privacy_credentials_title
import danmaku.apps.desktop_windows.generated.resources.desktop_privacy_title
import danmaku.apps.desktop_windows.generated.resources.desktop_rescan_library
import danmaku.apps.desktop_windows.generated.resources.desktop_renderer_label
import danmaku.apps.desktop_windows.generated.resources.desktop_runtime_title
import danmaku.apps.desktop_windows.generated.resources.desktop_search_label
import danmaku.apps.desktop_windows.generated.resources.desktop_settings_description
import danmaku.apps.desktop_windows.generated.resources.desktop_settings_section_danmaku
import danmaku.apps.desktop_windows.generated.resources.desktop_settings_section_diagnostics
import danmaku.apps.desktop_windows.generated.resources.desktop_settings_section_general
import danmaku.apps.desktop_windows.generated.resources.desktop_settings_section_library
import danmaku.apps.desktop_windows.generated.resources.desktop_settings_section_playback
import danmaku.apps.desktop_windows.generated.resources.desktop_settings_section_privacy
import danmaku.apps.desktop_windows.generated.resources.desktop_settings_section_providers
import danmaku.apps.desktop_windows.generated.resources.desktop_settings_section_server
import danmaku.apps.desktop_windows.generated.resources.desktop_settings_section_storage
import danmaku.apps.desktop_windows.generated.resources.desktop_settings_title
import danmaku.apps.desktop_windows.generated.resources.desktop_shell_subtitle
import danmaku.apps.desktop_windows.generated.resources.desktop_storage_cleanup_description
import danmaku.apps.desktop_windows.generated.resources.desktop_supported_label
import danmaku.apps.desktop_windows.generated.resources.desktop_tab_downloads
import danmaku.apps.desktop_windows.generated.resources.desktop_tab_home
import danmaku.apps.desktop_windows.generated.resources.desktop_tab_media_library
import danmaku.apps.desktop_windows.generated.resources.desktop_tab_playback
import danmaku.apps.desktop_windows.generated.resources.desktop_tab_profile
import danmaku.apps.desktop_windows.generated.resources.desktop_tab_tracking
import danmaku.apps.desktop_windows.generated.resources.desktop_ui_language_label
import danmaku.apps.desktop_windows.generated.resources.desktop_ui_languages_value
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
    val settingsSectionGeneral = stringResource(Res.string.desktop_settings_section_general)
    val settingsSectionLibrary = stringResource(Res.string.desktop_settings_section_library)
    val settingsSectionPlayback = stringResource(Res.string.desktop_settings_section_playback)
    val settingsSectionDanmaku = stringResource(Res.string.desktop_settings_section_danmaku)
    val settingsSectionProviders = stringResource(Res.string.desktop_settings_section_providers)
    val settingsSectionServer = stringResource(Res.string.desktop_settings_section_server)
    val settingsSectionStorage = stringResource(Res.string.desktop_settings_section_storage)
    val settingsSectionPrivacy = stringResource(Res.string.desktop_settings_section_privacy)
    val settingsSectionDiagnostics = stringResource(Res.string.desktop_settings_section_diagnostics)
    val languageTitle = stringResource(Res.string.desktop_language_title)
    val languageDescription = stringResource(Res.string.desktop_language_description)
    val uiLanguageLabel = stringResource(Res.string.desktop_ui_language_label)
    val uiLanguagesValue = stringResource(Res.string.desktop_ui_languages_value)
    val appLabel = stringResource(Res.string.desktop_app_label)
    val primaryTargetsLabel = stringResource(Res.string.desktop_primary_targets_label)
    val supportedLabel = stringResource(Res.string.desktop_supported_label)
    val librarySettingsDescription = stringResource(Res.string.desktop_library_settings_description)
    val metadataLabel = stringResource(Res.string.desktop_metadata_label)
    val metadataRefreshLibraryDetailsText = stringResource(Res.string.desktop_metadata_refresh_library_details_text)
    val importsLabel = stringResource(Res.string.desktop_imports_label)
    val aniRssImportsManagedDownloadsText = stringResource(Res.string.desktop_ani_rss_imports_managed_downloads_text)
    val playbackRuntimeTitle = stringResource(Res.string.desktop_playback_runtime_title)
    val mpvExecutorLabel = stringResource(Res.string.desktop_mpv_executor_label)
    val rendererLabel = stringResource(Res.string.desktop_renderer_label)
    val mpvRendererDescription = stringResource(Res.string.desktop_mpv_renderer_description)
    val focusModeLabel = stringResource(Res.string.desktop_focus_mode_label)
    val playerFocusModeDescription = stringResource(Res.string.desktop_player_focus_mode_description)
    val storageCleanupDescription = stringResource(Res.string.desktop_storage_cleanup_description)
    val appLogLabel = stringResource(Res.string.desktop_app_log_label)
    val mpvLogLabel = stringResource(Res.string.desktop_mpv_log_label)
    val privacyCredentialsTitle = stringResource(Res.string.desktop_privacy_credentials_title)
    val privacyCredentialsDescription = stringResource(Res.string.desktop_privacy_credentials_description)
    val desktopRuntimeTitle = stringResource(Res.string.desktop_runtime_title)
    val privacyTitle = stringResource(Res.string.desktop_privacy_title)
    val credentialsPrivacyText = stringResource(Res.string.desktop_credentials_privacy_text)
    val homeAction = stringResource(Res.string.desktop_home_action)
    val libraryAction = stringResource(Res.string.desktop_library_action)

    return language.strings.apply {
        if (useResourceStrings) {
            tabTitles = mapOf(
                DesktopShellTab.HOME to tabHome,
                DesktopShellTab.PLAYBACK to tabPlayback,
                DesktopShellTab.MEDIA_LIBRARY to tabMediaLibrary,
                DesktopShellTab.DOWNLOADS to tabDownloads,
                DesktopShellTab.TRACKING to tabTracking,
                DesktopShellTab.PROFILE to tabProfile,
            )
            settingsSectionTitles = mapOf(
                DesktopSettingsSection.GENERAL to settingsSectionGeneral,
                DesktopSettingsSection.LIBRARY to settingsSectionLibrary,
                DesktopSettingsSection.PLAYBACK to settingsSectionPlayback,
                DesktopSettingsSection.DANMAKU to settingsSectionDanmaku,
                DesktopSettingsSection.PROVIDERS to settingsSectionProviders,
                DesktopSettingsSection.SERVER to settingsSectionServer,
                DesktopSettingsSection.STORAGE to settingsSectionStorage,
                DesktopSettingsSection.PRIVACY to settingsSectionPrivacy,
                DesktopSettingsSection.DIAGNOSTICS to settingsSectionDiagnostics,
            )
            this.mediaHub = mediaHub
            this.shellSubtitle = shellSubtitle
            this.searchLabel = searchLabel
            this.playerStatusPrefix = playerStatusPrefix
            this.episodesSuffix = episodesSuffix
            this.rescanLibrary = rescanLibrary
            this.settingsTitle = settingsTitle
            this.settingsDescription = settingsDescription
            this.languageTitle = languageTitle
            this.languageDescription = languageDescription
            this.uiLanguageLabel = uiLanguageLabel
            this.uiLanguagesValue = uiLanguagesValue
            this.appLabel = appLabel
            this.primaryTargetsLabel = primaryTargetsLabel
            this.supportedLabel = supportedLabel
            this.librarySettingsDescription = librarySettingsDescription
            this.metadataLabel = metadataLabel
            this.metadataRefreshLibraryDetailsText = metadataRefreshLibraryDetailsText
            this.importsLabel = importsLabel
            this.aniRssImportsManagedDownloadsText = aniRssImportsManagedDownloadsText
            this.playbackRuntimeTitle = playbackRuntimeTitle
            this.mpvExecutorLabel = mpvExecutorLabel
            this.rendererLabel = rendererLabel
            this.mpvRendererDescription = mpvRendererDescription
            this.focusModeLabel = focusModeLabel
            this.playerFocusModeDescription = playerFocusModeDescription
            this.storageCleanupDescription = storageCleanupDescription
            this.appLogLabel = appLogLabel
            this.mpvLogLabel = mpvLogLabel
            this.privacyCredentialsTitle = privacyCredentialsTitle
            this.privacyCredentialsDescription = privacyCredentialsDescription
            this.desktopRuntimeTitle = desktopRuntimeTitle
            this.privacyTitle = privacyTitle
            this.credentialsPrivacyText = credentialsPrivacyText
            this.homeAction = homeAction
            this.libraryAction = libraryAction
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
