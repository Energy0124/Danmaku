package app.danmaku.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import danmaku.apps.desktop_windows.generated.resources.Res
import danmaku.apps.desktop_windows.generated.resources.desktop_all_episodes_slice_label
import danmaku.apps.desktop_windows.generated.resources.desktop_ani_rss_root_count_label
import danmaku.apps.desktop_windows.generated.resources.desktop_ani_rss_imports_managed_downloads_text
import danmaku.apps.desktop_windows.generated.resources.desktop_anime_series_slice_label
import danmaku.apps.desktop_windows.generated.resources.desktop_app_label
import danmaku.apps.desktop_windows.generated.resources.desktop_app_log_label
import danmaku.apps.desktop_windows.generated.resources.desktop_back_ten_seconds_action
import danmaku.apps.desktop_windows.generated.resources.desktop_back_thirty_seconds_action
import danmaku.apps.desktop_windows.generated.resources.desktop_browse_all_action
import danmaku.apps.desktop_windows.generated.resources.desktop_choose_anime_library_folder_title
import danmaku.apps.desktop_windows.generated.resources.desktop_choose_ani_rss_completed_media_folder_title
import danmaku.apps.desktop_windows.generated.resources.desktop_choose_media_file_title
import danmaku.apps.desktop_windows.generated.resources.desktop_continue_watching_title
import danmaku.apps.desktop_windows.generated.resources.desktop_credentials_privacy_text
import danmaku.apps.desktop_windows.generated.resources.desktop_episodes_suffix
import danmaku.apps.desktop_windows.generated.resources.desktop_enter_fullscreen_action
import danmaku.apps.desktop_windows.generated.resources.desktop_exit_fullscreen_action
import danmaku.apps.desktop_windows.generated.resources.desktop_favorites_slice_label
import danmaku.apps.desktop_windows.generated.resources.desktop_favorites_summary_title
import danmaku.apps.desktop_windows.generated.resources.desktop_favorite_episodes_filter_empty_text
import danmaku.apps.desktop_windows.generated.resources.desktop_favorites_only_action
import danmaku.apps.desktop_windows.generated.resources.desktop_focus_mode_label
import danmaku.apps.desktop_windows.generated.resources.desktop_folders_label
import danmaku.apps.desktop_windows.generated.resources.desktop_forward_ten_seconds_action
import danmaku.apps.desktop_windows.generated.resources.desktop_forward_thirty_seconds_action
import danmaku.apps.desktop_windows.generated.resources.desktop_episode_count_short_label
import danmaku.apps.desktop_windows.generated.resources.desktop_episode_count_summary
import danmaku.apps.desktop_windows.generated.resources.desktop_episodes_filter_empty_text
import danmaku.apps.desktop_windows.generated.resources.desktop_external_lists_label
import danmaku.apps.desktop_windows.generated.resources.desktop_favorite_count_summary
import danmaku.apps.desktop_windows.generated.resources.desktop_hide_danmaku_panel_action
import danmaku.apps.desktop_windows.generated.resources.desktop_hide_player_chrome_action
import danmaku.apps.desktop_windows.generated.resources.desktop_home_action
import danmaku.apps.desktop_windows.generated.resources.desktop_home_library_empty_text
import danmaku.apps.desktop_windows.generated.resources.desktop_imports_label
import danmaku.apps.desktop_windows.generated.resources.desktop_in_progress_caption
import danmaku.apps.desktop_windows.generated.resources.desktop_indexing_label
import danmaku.apps.desktop_windows.generated.resources.desktop_lan_browser_ready_label
import danmaku.apps.desktop_windows.generated.resources.desktop_language_description
import danmaku.apps.desktop_windows.generated.resources.desktop_language_title
import danmaku.apps.desktop_windows.generated.resources.desktop_library_action
import danmaku.apps.desktop_windows.generated.resources.desktop_library_preparation_step_label
import danmaku.apps.desktop_windows.generated.resources.desktop_library_slices_title
import danmaku.apps.desktop_windows.generated.resources.desktop_library_settings_description
import danmaku.apps.desktop_windows.generated.resources.desktop_library_snapshot_title
import danmaku.apps.desktop_windows.generated.resources.desktop_library_step_label
import danmaku.apps.desktop_windows.generated.resources.desktop_library_host_subtitle
import danmaku.apps.desktop_windows.generated.resources.desktop_library_search_label
import danmaku.apps.desktop_windows.generated.resources.desktop_library_toolbar_compact_description
import danmaku.apps.desktop_windows.generated.resources.desktop_library_toolbar_description
import danmaku.apps.desktop_windows.generated.resources.desktop_library_view_all_series
import danmaku.apps.desktop_windows.generated.resources.desktop_library_view_continue
import danmaku.apps.desktop_windows.generated.resources.desktop_library_view_external_sync
import danmaku.apps.desktop_windows.generated.resources.desktop_library_view_favorites
import danmaku.apps.desktop_windows.generated.resources.desktop_library_view_files
import danmaku.apps.desktop_windows.generated.resources.desktop_library_view_history
import danmaku.apps.desktop_windows.generated.resources.desktop_library_view_next_up
import danmaku.apps.desktop_windows.generated.resources.desktop_library_view_paired
import danmaku.apps.desktop_windows.generated.resources.desktop_last_scan_counts_summary
import danmaku.apps.desktop_windows.generated.resources.desktop_last_scan_summary
import danmaku.apps.desktop_windows.generated.resources.desktop_last_scanned_at_label
import danmaku.apps.desktop_windows.generated.resources.desktop_last_watched_label
import danmaku.apps.desktop_windows.generated.resources.desktop_loaded_now_label
import danmaku.apps.desktop_windows.generated.resources.desktop_local_pc_label
import danmaku.apps.desktop_windows.generated.resources.desktop_matched_groups_caption
import danmaku.apps.desktop_windows.generated.resources.desktop_media_hub
import danmaku.apps.desktop_windows.generated.resources.desktop_metadata_label
import danmaku.apps.desktop_windows.generated.resources.desktop_metadata_refresh_library_details_text
import danmaku.apps.desktop_windows.generated.resources.desktop_more_items_label
import danmaku.apps.desktop_windows.generated.resources.desktop_mpv_executor_label
import danmaku.apps.desktop_windows.generated.resources.desktop_mpv_log_label
import danmaku.apps.desktop_windows.generated.resources.desktop_mpv_renderer_description
import danmaku.apps.desktop_windows.generated.resources.desktop_movies_slice_label
import danmaku.apps.desktop_windows.generated.resources.desktop_my_library_title
import danmaku.apps.desktop_windows.generated.resources.desktop_newly_added_empty_text
import danmaku.apps.desktop_windows.generated.resources.desktop_next_after_label
import danmaku.apps.desktop_windows.generated.resources.desktop_next_episode_action
import danmaku.apps.desktop_windows.generated.resources.desktop_next_episode_with_title
import danmaku.apps.desktop_windows.generated.resources.desktop_next_playable_label
import danmaku.apps.desktop_windows.generated.resources.desktop_no_resume_queue_text
import danmaku.apps.desktop_windows.generated.resources.desktop_no_folders_label
import danmaku.apps.desktop_windows.generated.resources.desktop_no_indexed_series_text
import danmaku.apps.desktop_windows.generated.resources.desktop_no_in_progress_local_episodes_text
import danmaku.apps.desktop_windows.generated.resources.desktop_no_library_label
import danmaku.apps.desktop_windows.generated.resources.desktop_no_next_up_item_text
import danmaku.apps.desktop_windows.generated.resources.desktop_no_recently_watched_local_episodes_text
import danmaku.apps.desktop_windows.generated.resources.desktop_no_series_filter_matches_text
import danmaku.apps.desktop_windows.generated.resources.desktop_now_playing_title
import danmaku.apps.desktop_windows.generated.resources.desktop_open_media_file_action
import danmaku.apps.desktop_windows.generated.resources.desktop_open_library_import_panel_action
import danmaku.apps.desktop_windows.generated.resources.desktop_ovas_specials_slice_label
import danmaku.apps.desktop_windows.generated.resources.desktop_paired_devices_label
import danmaku.apps.desktop_windows.generated.resources.desktop_path_sort_label
import danmaku.apps.desktop_windows.generated.resources.desktop_player_status_prefix
import danmaku.apps.desktop_windows.generated.resources.desktop_player_focus_mode_description
import danmaku.apps.desktop_windows.generated.resources.desktop_player_runtime_step_label
import danmaku.apps.desktop_windows.generated.resources.desktop_playback_runtime_title
import danmaku.apps.desktop_windows.generated.resources.desktop_play_action
import danmaku.apps.desktop_windows.generated.resources.desktop_preparing_media_step_text
import danmaku.apps.desktop_windows.generated.resources.desktop_previous_episode_action
import danmaku.apps.desktop_windows.generated.resources.desktop_previous_episode_with_title
import danmaku.apps.desktop_windows.generated.resources.desktop_primary_targets_label
import danmaku.apps.desktop_windows.generated.resources.desktop_privacy_credentials_description
import danmaku.apps.desktop_windows.generated.resources.desktop_privacy_credentials_title
import danmaku.apps.desktop_windows.generated.resources.desktop_privacy_title
import danmaku.apps.desktop_windows.generated.resources.desktop_provider_episode_label
import danmaku.apps.desktop_windows.generated.resources.desktop_recent_playback_empty_text
import danmaku.apps.desktop_windows.generated.resources.desktop_recently_added_detail_label
import danmaku.apps.desktop_windows.generated.resources.desktop_recently_added_title
import danmaku.apps.desktop_windows.generated.resources.desktop_recently_watched_title
import danmaku.apps.desktop_windows.generated.resources.desktop_rescan_library
import danmaku.apps.desktop_windows.generated.resources.desktop_rescan_folders_action
import danmaku.apps.desktop_windows.generated.resources.desktop_renderer_label
import danmaku.apps.desktop_windows.generated.resources.desktop_require_subtitles_action
import danmaku.apps.desktop_windows.generated.resources.desktop_resume_at_label
import danmaku.apps.desktop_windows.generated.resources.desktop_resume_saved_position_label
import danmaku.apps.desktop_windows.generated.resources.desktop_resume_value_label
import danmaku.apps.desktop_windows.generated.resources.desktop_reused_count_label
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
import danmaku.apps.desktop_windows.generated.resources.desktop_show_danmaku_panel_action
import danmaku.apps.desktop_windows.generated.resources.desktop_show_all_favorites_action
import danmaku.apps.desktop_windows.generated.resources.desktop_show_all_subtitles_action
import danmaku.apps.desktop_windows.generated.resources.desktop_show_player_chrome_action
import danmaku.apps.desktop_windows.generated.resources.desktop_sort_by_path_action
import danmaku.apps.desktop_windows.generated.resources.desktop_sort_by_title_action
import danmaku.apps.desktop_windows.generated.resources.desktop_storage_cleanup_description
import danmaku.apps.desktop_windows.generated.resources.desktop_saved_episodes_caption
import danmaku.apps.desktop_windows.generated.resources.desktop_saved_position_label
import danmaku.apps.desktop_windows.generated.resources.desktop_series_summary_title
import danmaku.apps.desktop_windows.generated.resources.desktop_start_watching_library_label
import danmaku.apps.desktop_windows.generated.resources.desktop_subtitle_short_label
import danmaku.apps.desktop_windows.generated.resources.desktop_subtitles_indexed_label
import danmaku.apps.desktop_windows.generated.resources.desktop_subtitles_only_label
import danmaku.apps.desktop_windows.generated.resources.desktop_supported_label
import danmaku.apps.desktop_windows.generated.resources.desktop_tab_downloads
import danmaku.apps.desktop_windows.generated.resources.desktop_tab_home
import danmaku.apps.desktop_windows.generated.resources.desktop_tab_media_library
import danmaku.apps.desktop_windows.generated.resources.desktop_tab_playback
import danmaku.apps.desktop_windows.generated.resources.desktop_tab_profile
import danmaku.apps.desktop_windows.generated.resources.desktop_tab_tracking
import danmaku.apps.desktop_windows.generated.resources.desktop_ui_language_label
import danmaku.apps.desktop_windows.generated.resources.desktop_ui_languages_value
import danmaku.apps.desktop_windows.generated.resources.desktop_volume_label
import danmaku.apps.desktop_windows.generated.resources.desktop_watched_at_label
import danmaku.apps.desktop_windows.generated.resources.desktop_watching_summary_title
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
    val openMediaFileAction = stringResource(Res.string.desktop_open_media_file_action)
    val chooseMediaFileTitle = stringResource(Res.string.desktop_choose_media_file_title)
    val chooseAnimeLibraryFolderTitle = stringResource(Res.string.desktop_choose_anime_library_folder_title)
    val chooseAniRssCompletedMediaFolderTitle =
        stringResource(Res.string.desktop_choose_ani_rss_completed_media_folder_title)
    val homeAction = stringResource(Res.string.desktop_home_action)
    val libraryAction = stringResource(Res.string.desktop_library_action)
    val showPlayerChromeAction = stringResource(Res.string.desktop_show_player_chrome_action)
    val hidePlayerChromeAction = stringResource(Res.string.desktop_hide_player_chrome_action)
    val previousEpisodeAction = stringResource(Res.string.desktop_previous_episode_action)
    val previousEpisodeWithTitle = stringResource(Res.string.desktop_previous_episode_with_title)
    val nextEpisodeAction = stringResource(Res.string.desktop_next_episode_action)
    val nextEpisodeWithTitle = stringResource(Res.string.desktop_next_episode_with_title)
    val backThirtySecondsAction = stringResource(Res.string.desktop_back_thirty_seconds_action)
    val backTenSecondsAction = stringResource(Res.string.desktop_back_ten_seconds_action)
    val playAction = stringResource(Res.string.desktop_play_action)
    val forwardTenSecondsAction = stringResource(Res.string.desktop_forward_ten_seconds_action)
    val forwardThirtySecondsAction = stringResource(Res.string.desktop_forward_thirty_seconds_action)
    val volumeLabel = stringResource(Res.string.desktop_volume_label)
    val subtitleShortLabel = stringResource(Res.string.desktop_subtitle_short_label)
    val hideDanmakuPanelAction = stringResource(Res.string.desktop_hide_danmaku_panel_action)
    val showDanmakuPanelAction = stringResource(Res.string.desktop_show_danmaku_panel_action)
    val exitFullscreenAction = stringResource(Res.string.desktop_exit_fullscreen_action)
    val enterFullscreenAction = stringResource(Res.string.desktop_enter_fullscreen_action)
    val libraryStepLabel = stringResource(Res.string.desktop_library_step_label)
    val preparingMediaStepText = stringResource(Res.string.desktop_preparing_media_step_text)
    val playerRuntimeStepLabel = stringResource(Res.string.desktop_player_runtime_step_label)
    val libraryPreparationStepLabel = stringResource(Res.string.desktop_library_preparation_step_label)
    val librarySlicesTitle = stringResource(Res.string.desktop_library_slices_title)
    val animeSeriesSliceLabel = stringResource(Res.string.desktop_anime_series_slice_label)
    val moviesSliceLabel = stringResource(Res.string.desktop_movies_slice_label)
    val ovasSpecialsSliceLabel = stringResource(Res.string.desktop_ovas_specials_slice_label)
    val allEpisodesSliceLabel = stringResource(Res.string.desktop_all_episodes_slice_label)
    val favoritesSliceLabel = stringResource(Res.string.desktop_favorites_slice_label)
    val nowPlayingTitle = stringResource(Res.string.desktop_now_playing_title)
    val recentlyAddedTitle = stringResource(Res.string.desktop_recently_added_title)
    val browseAllAction = stringResource(Res.string.desktop_browse_all_action)
    val newlyAddedEmptyText = stringResource(Res.string.desktop_newly_added_empty_text)
    val recentlyWatchedTitle = stringResource(Res.string.desktop_recently_watched_title)
    val recentPlaybackEmptyText = stringResource(Res.string.desktop_recent_playback_empty_text)
    val myLibraryTitle = stringResource(Res.string.desktop_my_library_title)
    val seriesSummaryTitle = stringResource(Res.string.desktop_series_summary_title)
    val matchedGroupsCaption = stringResource(Res.string.desktop_matched_groups_caption)
    val favoritesSummaryTitle = stringResource(Res.string.desktop_favorites_summary_title)
    val savedEpisodesCaption = stringResource(Res.string.desktop_saved_episodes_caption)
    val watchingSummaryTitle = stringResource(Res.string.desktop_watching_summary_title)
    val inProgressCaption = stringResource(Res.string.desktop_in_progress_caption)
    val homeLibraryEmptyText = stringResource(Res.string.desktop_home_library_empty_text)
    val librarySnapshotTitle = stringResource(Res.string.desktop_library_snapshot_title)
    val continueWatchingTitle = stringResource(Res.string.desktop_continue_watching_title)
    val noResumeQueueText = stringResource(Res.string.desktop_no_resume_queue_text)
    val loadedNowLabel = stringResource(Res.string.desktop_loaded_now_label)
    val resumeSavedPositionLabel = stringResource(Res.string.desktop_resume_saved_position_label)
    val savedPositionLabel = stringResource(Res.string.desktop_saved_position_label)
    val startWatchingLibraryLabel = stringResource(Res.string.desktop_start_watching_library_label)
    val libraryViewContinue = stringResource(Res.string.desktop_library_view_continue)
    val libraryViewNextUp = stringResource(Res.string.desktop_library_view_next_up)
    val libraryViewAllSeries = stringResource(Res.string.desktop_library_view_all_series)
    val libraryViewHistory = stringResource(Res.string.desktop_library_view_history)
    val libraryViewFavorites = stringResource(Res.string.desktop_library_view_favorites)
    val libraryViewFiles = stringResource(Res.string.desktop_library_view_files)
    val libraryViewExternalSync = stringResource(Res.string.desktop_library_view_external_sync)
    val libraryViewPaired = stringResource(Res.string.desktop_library_view_paired)
    val libraryHostSubtitle = stringResource(Res.string.desktop_library_host_subtitle)
    val librarySearchLabel = stringResource(Res.string.desktop_library_search_label)
    val libraryToolbarCompactDescription = stringResource(Res.string.desktop_library_toolbar_compact_description)
    val libraryToolbarDescription = stringResource(Res.string.desktop_library_toolbar_description)
    val favoriteEpisodesFilterEmptyText = stringResource(Res.string.desktop_favorite_episodes_filter_empty_text)
    val episodesFilterEmptyText = stringResource(Res.string.desktop_episodes_filter_empty_text)
    val noIndexedSeriesText = stringResource(Res.string.desktop_no_indexed_series_text)
    val noSeriesFilterMatchesText = stringResource(Res.string.desktop_no_series_filter_matches_text)
    val noInProgressLocalEpisodesText = stringResource(Res.string.desktop_no_in_progress_local_episodes_text)
    val noNextUpItemText = stringResource(Res.string.desktop_no_next_up_item_text)
    val noRecentlyWatchedLocalEpisodesText = stringResource(Res.string.desktop_no_recently_watched_local_episodes_text)
    val subtitlesOnlyLabel = stringResource(Res.string.desktop_subtitles_only_label)
    val pathSortLabel = stringResource(Res.string.desktop_path_sort_label)
    val requireSubtitlesAction = stringResource(Res.string.desktop_require_subtitles_action)
    val showAllSubtitlesAction = stringResource(Res.string.desktop_show_all_subtitles_action)
    val favoritesOnlyAction = stringResource(Res.string.desktop_favorites_only_action)
    val showAllFavoritesAction = stringResource(Res.string.desktop_show_all_favorites_action)
    val sortByPathAction = stringResource(Res.string.desktop_sort_by_path_action)
    val sortByTitleAction = stringResource(Res.string.desktop_sort_by_title_action)
    val openLibraryImportPanelAction = stringResource(Res.string.desktop_open_library_import_panel_action)
    val rescanFoldersAction = stringResource(Res.string.desktop_rescan_folders_action)
    val localPcLabel = stringResource(Res.string.desktop_local_pc_label)
    val indexingLabel = stringResource(Res.string.desktop_indexing_label)
    val externalListsLabel = stringResource(Res.string.desktop_external_lists_label)
    val noLibraryLabel = stringResource(Res.string.desktop_no_library_label)
    val pairedDevicesLabel = stringResource(Res.string.desktop_paired_devices_label)
    val lanBrowserReadyLabel = stringResource(Res.string.desktop_lan_browser_ready_label)
    val foldersLabel = stringResource(Res.string.desktop_folders_label)
    val noFoldersLabel = stringResource(Res.string.desktop_no_folders_label)
    val recentlyAddedDetailLabel = stringResource(Res.string.desktop_recently_added_detail_label)
    val watchedAtLabel = stringResource(Res.string.desktop_watched_at_label)
    val resumeAtLabel = stringResource(Res.string.desktop_resume_at_label)
    val lastWatchedLabel = stringResource(Res.string.desktop_last_watched_label)
    val nextAfterLabel = stringResource(Res.string.desktop_next_after_label)
    val episodeCountShortLabel = stringResource(Res.string.desktop_episode_count_short_label)
    val episodeCountSummary = stringResource(Res.string.desktop_episode_count_summary)
    val favoriteCountSummary = stringResource(Res.string.desktop_favorite_count_summary)
    val moreItemsLabel = stringResource(Res.string.desktop_more_items_label)
    val lastScanSummary = stringResource(Res.string.desktop_last_scan_summary)
    val lastScanCountsSummary = stringResource(Res.string.desktop_last_scan_counts_summary)
    val aniRssRootCountLabel = stringResource(Res.string.desktop_ani_rss_root_count_label)
    val reusedCountLabel = stringResource(Res.string.desktop_reused_count_label)
    val lastScannedAtLabel = stringResource(Res.string.desktop_last_scanned_at_label)
    val nextPlayableLabel = stringResource(Res.string.desktop_next_playable_label)
    val providerEpisodeLabel = stringResource(Res.string.desktop_provider_episode_label)
    val resumeValueLabel = stringResource(Res.string.desktop_resume_value_label)
    val subtitlesIndexedLabel = stringResource(Res.string.desktop_subtitles_indexed_label)

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
            this.openMediaFileAction = openMediaFileAction
            this.chooseMediaFileTitle = { host -> chooseMediaFileTitle.formatResourceString(host) }
            this.chooseAnimeLibraryFolderTitle = chooseAnimeLibraryFolderTitle
            this.chooseAniRssCompletedMediaFolderTitle = chooseAniRssCompletedMediaFolderTitle
            this.homeAction = homeAction
            this.libraryAction = libraryAction
            this.showPlayerChromeAction = showPlayerChromeAction
            this.hidePlayerChromeAction = hidePlayerChromeAction
            this.previousEpisodeAction = previousEpisodeAction
            this.previousEpisodeWithTitle = { title -> previousEpisodeWithTitle.formatResourceString(title) }
            this.nextEpisodeAction = nextEpisodeAction
            this.nextEpisodeWithTitle = { title -> nextEpisodeWithTitle.formatResourceString(title) }
            this.backThirtySecondsAction = backThirtySecondsAction
            this.backTenSecondsAction = backTenSecondsAction
            this.playAction = playAction
            this.forwardTenSecondsAction = forwardTenSecondsAction
            this.forwardThirtySecondsAction = forwardThirtySecondsAction
            this.volumeLabel = volumeLabel
            this.subtitleShortLabel = subtitleShortLabel
            this.hideDanmakuPanelAction = hideDanmakuPanelAction
            this.showDanmakuPanelAction = showDanmakuPanelAction
            this.exitFullscreenAction = exitFullscreenAction
            this.enterFullscreenAction = enterFullscreenAction
            this.libraryStepLabel = libraryStepLabel
            this.preparingMediaStepText = preparingMediaStepText
            this.playerRuntimeStepLabel = playerRuntimeStepLabel
            this.libraryPreparationStepLabel = libraryPreparationStepLabel
            this.librarySlicesTitle = librarySlicesTitle
            this.animeSeriesSliceLabel = animeSeriesSliceLabel
            this.moviesSliceLabel = moviesSliceLabel
            this.ovasSpecialsSliceLabel = ovasSpecialsSliceLabel
            this.allEpisodesSliceLabel = allEpisodesSliceLabel
            this.favoritesSliceLabel = favoritesSliceLabel
            this.nowPlayingTitle = nowPlayingTitle
            this.recentlyAddedTitle = recentlyAddedTitle
            this.browseAllAction = browseAllAction
            this.newlyAddedEmptyText = newlyAddedEmptyText
            this.recentlyWatchedTitle = recentlyWatchedTitle
            this.recentPlaybackEmptyText = recentPlaybackEmptyText
            this.myLibraryTitle = myLibraryTitle
            this.seriesSummaryTitle = seriesSummaryTitle
            this.matchedGroupsCaption = matchedGroupsCaption
            this.favoritesSummaryTitle = favoritesSummaryTitle
            this.savedEpisodesCaption = savedEpisodesCaption
            this.watchingSummaryTitle = watchingSummaryTitle
            this.inProgressCaption = inProgressCaption
            this.homeLibraryEmptyText = homeLibraryEmptyText
            this.librarySnapshotTitle = librarySnapshotTitle
            this.continueWatchingTitle = continueWatchingTitle
            this.noResumeQueueText = noResumeQueueText
            this.loadedNowLabel = loadedNowLabel
            this.resumeSavedPositionLabel = resumeSavedPositionLabel
            this.savedPositionLabel = savedPositionLabel
            this.startWatchingLibraryLabel = startWatchingLibraryLabel
            libraryViewTitles = mapOf(
                WindowsLibraryView.CONTINUE_WATCHING to libraryViewContinue,
                WindowsLibraryView.NEXT_UP to libraryViewNextUp,
                WindowsLibraryView.ALL_SERIES to libraryViewAllSeries,
                WindowsLibraryView.RECENTLY_WATCHED to libraryViewHistory,
                WindowsLibraryView.FAVORITES to libraryViewFavorites,
                WindowsLibraryView.FILES to libraryViewFiles,
                WindowsLibraryView.EXTERNAL_SYNC to libraryViewExternalSync,
                WindowsLibraryView.PAIRED to libraryViewPaired,
            )
            this.libraryHostSubtitle = libraryHostSubtitle
            this.librarySearchLabel = librarySearchLabel
            this.libraryToolbarCompactDescription = libraryToolbarCompactDescription
            this.libraryToolbarDescription = libraryToolbarDescription
            this.favoriteEpisodesFilterEmptyText = favoriteEpisodesFilterEmptyText
            this.episodesFilterEmptyText = episodesFilterEmptyText
            this.noIndexedSeriesText = noIndexedSeriesText
            this.noSeriesFilterMatchesText = noSeriesFilterMatchesText
            this.noInProgressLocalEpisodesText = noInProgressLocalEpisodesText
            this.noNextUpItemText = noNextUpItemText
            this.noRecentlyWatchedLocalEpisodesText = noRecentlyWatchedLocalEpisodesText
            this.subtitlesOnlyLabel = subtitlesOnlyLabel
            this.pathSortLabel = pathSortLabel
            this.requireSubtitlesAction = requireSubtitlesAction
            this.showAllSubtitlesAction = showAllSubtitlesAction
            this.favoritesOnlyAction = favoritesOnlyAction
            this.showAllFavoritesAction = showAllFavoritesAction
            this.sortByPathAction = sortByPathAction
            this.sortByTitleAction = sortByTitleAction
            this.openLibraryImportPanelAction = openLibraryImportPanelAction
            this.rescanFoldersAction = rescanFoldersAction
            this.localPcLabel = localPcLabel
            this.indexingLabel = indexingLabel
            this.externalListsLabel = externalListsLabel
            this.noLibraryLabel = noLibraryLabel
            this.pairedDevicesLabel = pairedDevicesLabel
            this.lanBrowserReadyLabel = lanBrowserReadyLabel
            this.foldersLabel = foldersLabel
            this.noFoldersLabel = noFoldersLabel
            this.recentlyAddedDetailLabel = { indexedAtEpochMs, sizeText ->
                recentlyAddedDetailLabel.formatResourceString(indexedAtEpochMs.formatEpochTime(), sizeText)
            }
            this.watchedAtLabel = { positionMs -> watchedAtLabel.formatResourceString(positionMs.formatPlaybackTime()) }
            this.resumeAtLabel = { positionMs -> resumeAtLabel.formatResourceString(positionMs.formatPlaybackTime()) }
            this.lastWatchedLabel = lastWatchedLabel
            this.nextAfterLabel = { positionMs ->
                nextAfterLabel.formatResourceString(positionMs?.formatPlaybackTime() ?: lastWatchedLabel)
            }
            this.episodeCountShortLabel = { count -> episodeCountShortLabel.formatResourceString(count) }
            this.episodeCountSummary = { visible, total -> episodeCountSummary.formatResourceString(visible, total) }
            this.favoriteCountSummary = { count -> favoriteCountSummary.formatResourceString(count) }
            this.moreItemsLabel = { count -> moreItemsLabel.formatResourceString(count) }
            this.lastScanSummary = { reused, refreshed -> lastScanSummary.formatResourceString(reused, refreshed) }
            this.lastScanCountsSummary = { reused, refreshed ->
                lastScanCountsSummary.formatResourceString(reused, refreshed)
            }
            this.aniRssRootCountLabel = { count -> aniRssRootCountLabel.formatResourceString(count) }
            this.reusedCountLabel = { count -> reusedCountLabel.formatResourceString(count) }
            this.lastScannedAtLabel = { epochMs ->
                lastScannedAtLabel.formatResourceString(epochMs.formatEpochTime())
            }
            this.nextPlayableLabel = { title -> nextPlayableLabel.formatResourceString(title) }
            this.providerEpisodeLabel = { provider -> providerEpisodeLabel.formatResourceString(provider) }
            this.resumeValueLabel = { positionMs -> resumeValueLabel.formatResourceString(positionMs.formatPlaybackTime()) }
            this.subtitlesIndexedLabel = { count -> subtitlesIndexedLabel.formatResourceString(count) }
        }
    }
}

private fun String.formatResourceString(vararg args: Any): String =
    java.lang.String.format(java.util.Locale.ROOT, this, *args)

private fun DesktopUiLanguage.matchesResourceLocale(locale: Locale): Boolean =
    when (this) {
        DesktopUiLanguage.ENGLISH -> locale.language.equals("en", ignoreCase = true)
        DesktopUiLanguage.ZH_TW ->
            locale.language.equals("zh", ignoreCase = true) &&
                locale.region.equals("TW", ignoreCase = true)
    }
