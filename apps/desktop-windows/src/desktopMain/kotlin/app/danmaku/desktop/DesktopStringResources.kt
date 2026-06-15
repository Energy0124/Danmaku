package app.danmaku.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import danmaku.apps.desktop_windows.generated.resources.Res
import danmaku.apps.desktop_windows.generated.resources.desktop_add_folder_action
import danmaku.apps.desktop_windows.generated.resources.desktop_all_episodes_slice_label
import danmaku.apps.desktop_windows.generated.resources.desktop_ani_rss_root_count_label
import danmaku.apps.desktop_windows.generated.resources.desktop_ani_rss_imports_managed_downloads_text
import danmaku.apps.desktop_windows.generated.resources.desktop_ani_rss_output_folder_label
import danmaku.apps.desktop_windows.generated.resources.desktop_anime_series_slice_label
import danmaku.apps.desktop_windows.generated.resources.desktop_app_label
import danmaku.apps.desktop_windows.generated.resources.desktop_app_log_label
import danmaku.apps.desktop_windows.generated.resources.desktop_back_ten_seconds_action
import danmaku.apps.desktop_windows.generated.resources.desktop_back_thirty_seconds_action
import danmaku.apps.desktop_windows.generated.resources.desktop_browse_all_action
import danmaku.apps.desktop_windows.generated.resources.desktop_choose_anime_library_folder_title
import danmaku.apps.desktop_windows.generated.resources.desktop_choose_ani_rss_completed_media_folder_title
import danmaku.apps.desktop_windows.generated.resources.desktop_choose_media_file_title
import danmaku.apps.desktop_windows.generated.resources.desktop_check_cached_danmaku_action
import danmaku.apps.desktop_windows.generated.resources.desktop_continue_watching_title
import danmaku.apps.desktop_windows.generated.resources.desktop_credentials_privacy_text
import danmaku.apps.desktop_windows.generated.resources.desktop_details_action
import danmaku.apps.desktop_windows.generated.resources.desktop_episodes_suffix
import danmaku.apps.desktop_windows.generated.resources.desktop_enter_fullscreen_action
import danmaku.apps.desktop_windows.generated.resources.desktop_exit_fullscreen_action
import danmaku.apps.desktop_windows.generated.resources.desktop_favorites_slice_label
import danmaku.apps.desktop_windows.generated.resources.desktop_favorites_summary_title
import danmaku.apps.desktop_windows.generated.resources.desktop_favorite_episodes_filter_empty_text
import danmaku.apps.desktop_windows.generated.resources.desktop_favorites_only_action
import danmaku.apps.desktop_windows.generated.resources.desktop_favorite_action
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
import danmaku.apps.desktop_windows.generated.resources.desktop_import_ani_rss_output_action
import danmaku.apps.desktop_windows.generated.resources.desktop_in_progress_caption
import danmaku.apps.desktop_windows.generated.resources.desktop_indexing_label
import danmaku.apps.desktop_windows.generated.resources.desktop_indexing_library_roots_label
import danmaku.apps.desktop_windows.generated.resources.desktop_inspector_empty_text
import danmaku.apps.desktop_windows.generated.resources.desktop_inspector_resize_handle_label
import danmaku.apps.desktop_windows.generated.resources.desktop_inspector_title
import danmaku.apps.desktop_windows.generated.resources.desktop_lan_browser_ready_label
import danmaku.apps.desktop_windows.generated.resources.desktop_language_description
import danmaku.apps.desktop_windows.generated.resources.desktop_language_title
import danmaku.apps.desktop_windows.generated.resources.desktop_library_action
import danmaku.apps.desktop_windows.generated.resources.desktop_library_import_empty_text
import danmaku.apps.desktop_windows.generated.resources.desktop_library_import_title
import danmaku.apps.desktop_windows.generated.resources.desktop_library_preparation_step_label
import danmaku.apps.desktop_windows.generated.resources.desktop_library_root_available_label
import danmaku.apps.desktop_windows.generated.resources.desktop_library_root_missing_label
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
import danmaku.apps.desktop_windows.generated.resources.desktop_last_scan_title
import danmaku.apps.desktop_windows.generated.resources.desktop_last_scanned_at_label
import danmaku.apps.desktop_windows.generated.resources.desktop_last_watched_label
import danmaku.apps.desktop_windows.generated.resources.desktop_loaded_now_label
import danmaku.apps.desktop_windows.generated.resources.desktop_loading_action
import danmaku.apps.desktop_windows.generated.resources.desktop_local_pc_label
import danmaku.apps.desktop_windows.generated.resources.desktop_matched_groups_caption
import danmaku.apps.desktop_windows.generated.resources.desktop_media_hub
import danmaku.apps.desktop_windows.generated.resources.desktop_metadata_label
import danmaku.apps.desktop_windows.generated.resources.desktop_metadata_refresh_library_details_text
import danmaku.apps.desktop_windows.generated.resources.desktop_more_items_label
import danmaku.apps.desktop_windows.generated.resources.desktop_more_episode_actions_action
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
import danmaku.apps.desktop_windows.generated.resources.desktop_not_run_label
import danmaku.apps.desktop_windows.generated.resources.desktop_not_scanned_yet_label
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
import danmaku.apps.desktop_windows.generated.resources.desktop_prepare_action
import danmaku.apps.desktop_windows.generated.resources.desktop_prepare_short_action
import danmaku.apps.desktop_windows.generated.resources.desktop_preparing_action
import danmaku.apps.desktop_windows.generated.resources.desktop_preparing_media_step_text
import danmaku.apps.desktop_windows.generated.resources.desktop_previous_episode_action
import danmaku.apps.desktop_windows.generated.resources.desktop_previous_episode_with_title
import danmaku.apps.desktop_windows.generated.resources.desktop_primary_targets_label
import danmaku.apps.desktop_windows.generated.resources.desktop_privacy_credentials_description
import danmaku.apps.desktop_windows.generated.resources.desktop_privacy_credentials_title
import danmaku.apps.desktop_windows.generated.resources.desktop_privacy_title
import danmaku.apps.desktop_windows.generated.resources.desktop_provider_episode_label
import danmaku.apps.desktop_windows.generated.resources.desktop_published_label
import danmaku.apps.desktop_windows.generated.resources.desktop_recent_playback_empty_text
import danmaku.apps.desktop_windows.generated.resources.desktop_recently_added_detail_label
import danmaku.apps.desktop_windows.generated.resources.desktop_recently_added_title
import danmaku.apps.desktop_windows.generated.resources.desktop_recently_watched_title
import danmaku.apps.desktop_windows.generated.resources.desktop_rescan_library
import danmaku.apps.desktop_windows.generated.resources.desktop_rescan_action
import danmaku.apps.desktop_windows.generated.resources.desktop_rescan_all_action
import danmaku.apps.desktop_windows.generated.resources.desktop_rescan_folders_action
import danmaku.apps.desktop_windows.generated.resources.desktop_renderer_label
import danmaku.apps.desktop_windows.generated.resources.desktop_require_subtitles_action
import danmaku.apps.desktop_windows.generated.resources.desktop_registered_roots_title
import danmaku.apps.desktop_windows.generated.resources.desktop_remove_folder_action
import danmaku.apps.desktop_windows.generated.resources.desktop_remove_library_folder_text
import danmaku.apps.desktop_windows.generated.resources.desktop_remove_library_folder_title
import danmaku.apps.desktop_windows.generated.resources.desktop_reset_inspector_width_action
import danmaku.apps.desktop_windows.generated.resources.desktop_resume_at_label
import danmaku.apps.desktop_windows.generated.resources.desktop_resume_saved_position_label
import danmaku.apps.desktop_windows.generated.resources.desktop_resume_value_label
import danmaku.apps.desktop_windows.generated.resources.desktop_reused_count_label
import danmaku.apps.desktop_windows.generated.resources.desktop_runtime_title
import danmaku.apps.desktop_windows.generated.resources.desktop_search_label
import danmaku.apps.desktop_windows.generated.resources.desktop_scan_running_label
import danmaku.apps.desktop_windows.generated.resources.desktop_scanning_action
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
import danmaku.apps.desktop_windows.generated.resources.desktop_show_episode_details_action
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
import danmaku.apps.desktop_windows.generated.resources.desktop_unfavorite_action
import danmaku.apps.desktop_windows.generated.resources.desktop_user_selected_folder_label
import danmaku.apps.desktop_windows.generated.resources.desktop_volume_label
import danmaku.apps.desktop_windows.generated.resources.desktop_watched_at_label
import danmaku.apps.desktop_windows.generated.resources.desktop_watching_summary_title
import danmaku.apps.desktop_windows.generated.resources.desktop_advanced_title
import danmaku.apps.desktop_windows.generated.resources.desktop_attach_local_danmaku_action
import danmaku.apps.desktop_windows.generated.resources.desktop_attach_local_danmaku_short_action
import danmaku.apps.desktop_windows.generated.resources.desktop_auto_next_off_label
import danmaku.apps.desktop_windows.generated.resources.desktop_auto_next_on_label
import danmaku.apps.desktop_windows.generated.resources.desktop_clear_cache_action
import danmaku.apps.desktop_windows.generated.resources.desktop_clear_danmaku_cache_action
import danmaku.apps.desktop_windows.generated.resources.desktop_correct_action
import danmaku.apps.desktop_windows.generated.resources.desktop_disable_auto_next_action
import danmaku.apps.desktop_windows.generated.resources.desktop_enable_auto_next_action
import danmaku.apps.desktop_windows.generated.resources.desktop_episodes_title
import danmaku.apps.desktop_windows.generated.resources.desktop_external_ids_title
import danmaku.apps.desktop_windows.generated.resources.desktop_favorite_status_label
import danmaku.apps.desktop_windows.generated.resources.desktop_link_action
import danmaku.apps.desktop_windows.generated.resources.desktop_load_into_player_action
import danmaku.apps.desktop_windows.generated.resources.desktop_match_action
import danmaku.apps.desktop_windows.generated.resources.desktop_not_checked_yet_label
import danmaku.apps.desktop_windows.generated.resources.desktop_not_prepared_label
import danmaku.apps.desktop_windows.generated.resources.desktop_prepared_playback_label
import danmaku.apps.desktop_windows.generated.resources.desktop_prepare_playback_action
import danmaku.apps.desktop_windows.generated.resources.desktop_prepare_to_inspect_tracks_label
import danmaku.apps.desktop_windows.generated.resources.desktop_readiness_title
import danmaku.apps.desktop_windows.generated.resources.desktop_refresh_danmaku_action
import danmaku.apps.desktop_windows.generated.resources.desktop_refresh_episode_metadata_action
import danmaku.apps.desktop_windows.generated.resources.desktop_refresh_series_metadata_action
import danmaku.apps.desktop_windows.generated.resources.desktop_refreshing_episode_metadata_action
import danmaku.apps.desktop_windows.generated.resources.desktop_refreshing_series_metadata_action
import danmaku.apps.desktop_windows.generated.resources.desktop_remove_overlay_action
import danmaku.apps.desktop_windows.generated.resources.desktop_replace_action
import danmaku.apps.desktop_windows.generated.resources.desktop_add_ani_rss_output_folder_action
import danmaku.apps.desktop_windows.generated.resources.desktop_ani_rss_webhook_title
import danmaku.apps.desktop_windows.generated.resources.desktop_authorized_imports_only_label
import danmaku.apps.desktop_windows.generated.resources.desktop_cancel_action
import danmaku.apps.desktop_windows.generated.resources.desktop_created_label
import danmaku.apps.desktop_windows.generated.resources.desktop_download_execution_planned_text
import danmaku.apps.desktop_windows.generated.resources.desktop_download_filter_active
import danmaku.apps.desktop_windows.generated.resources.desktop_download_filter_all
import danmaku.apps.desktop_windows.generated.resources.desktop_download_filter_completed
import danmaku.apps.desktop_windows.generated.resources.desktop_download_filter_failed
import danmaku.apps.desktop_windows.generated.resources.desktop_download_filter_queued
import danmaku.apps.desktop_windows.generated.resources.desktop_download_inspector_empty_text
import danmaku.apps.desktop_windows.generated.resources.desktop_download_inspector_title
import danmaku.apps.desktop_windows.generated.resources.desktop_download_queue_empty_text
import danmaku.apps.desktop_windows.generated.resources.desktop_download_queue_filter_empty_text
import danmaku.apps.desktop_windows.generated.resources.desktop_download_queue_policy_text
import danmaku.apps.desktop_windows.generated.resources.desktop_download_queue_title
import danmaku.apps.desktop_windows.generated.resources.desktop_download_setup_authorized_sources_text
import danmaku.apps.desktop_windows.generated.resources.desktop_download_setup_authorized_sources_title
import danmaku.apps.desktop_windows.generated.resources.desktop_downloads_active_caption
import danmaku.apps.desktop_windows.generated.resources.desktop_downloads_active_title
import danmaku.apps.desktop_windows.generated.resources.desktop_downloads_completed_caption
import danmaku.apps.desktop_windows.generated.resources.desktop_downloads_completed_title
import danmaku.apps.desktop_windows.generated.resources.desktop_downloads_failed_caption
import danmaku.apps.desktop_windows.generated.resources.desktop_downloads_failed_title
import danmaku.apps.desktop_windows.generated.resources.desktop_downloads_queued_caption
import danmaku.apps.desktop_windows.generated.resources.desktop_downloads_queued_title
import danmaku.apps.desktop_windows.generated.resources.desktop_failure_label
import danmaku.apps.desktop_windows.generated.resources.desktop_import_root_count_label
import danmaku.apps.desktop_windows.generated.resources.desktop_import_roots_title
import danmaku.apps.desktop_windows.generated.resources.desktop_no_ani_rss_roots_text
import danmaku.apps.desktop_windows.generated.resources.desktop_no_webhook_url_text
import danmaku.apps.desktop_windows.generated.resources.desktop_open_folder_action
import danmaku.apps.desktop_windows.generated.resources.desktop_open_output_folder_action
import danmaku.apps.desktop_windows.generated.resources.desktop_output_label
import danmaku.apps.desktop_windows.generated.resources.desktop_pause_action
import danmaku.apps.desktop_windows.generated.resources.desktop_progress_label
import danmaku.apps.desktop_windows.generated.resources.desktop_queue_execution_planned_label
import danmaku.apps.desktop_windows.generated.resources.desktop_refresh_download_queue_action
import danmaku.apps.desktop_windows.generated.resources.desktop_remove_action
import danmaku.apps.desktop_windows.generated.resources.desktop_remove_download_text
import danmaku.apps.desktop_windows.generated.resources.desktop_remove_download_title
import danmaku.apps.desktop_windows.generated.resources.desktop_remove_queue_item_action
import danmaku.apps.desktop_windows.generated.resources.desktop_resume_action
import danmaku.apps.desktop_windows.generated.resources.desktop_retry_action
import danmaku.apps.desktop_windows.generated.resources.desktop_source_label
import danmaku.apps.desktop_windows.generated.resources.desktop_state_label
import danmaku.apps.desktop_windows.generated.resources.desktop_updated_label
import danmaku.apps.desktop_windows.generated.resources.desktop_webhook_header_label
import danmaku.apps.desktop_windows.generated.resources.desktop_webhook_token_label
import danmaku.apps.desktop_windows.generated.resources.desktop_webhook_url_label
import danmaku.apps.desktop_windows.generated.resources.desktop_api_base_url_label
import danmaku.apps.desktop_windows.generated.resources.desktop_app_id_optional_label
import danmaku.apps.desktop_windows.generated.resources.desktop_app_secret_keep_label
import danmaku.apps.desktop_windows.generated.resources.desktop_app_secret_optional_label
import danmaku.apps.desktop_windows.generated.resources.desktop_bangumi_access_token_keep_label
import danmaku.apps.desktop_windows.generated.resources.desktop_bangumi_access_token_optional_label
import danmaku.apps.desktop_windows.generated.resources.desktop_bangumi_api_base_url_label
import danmaku.apps.desktop_windows.generated.resources.desktop_bangumi_test_label
import danmaku.apps.desktop_windows.generated.resources.desktop_bangumi_user_agent_label
import danmaku.apps.desktop_windows.generated.resources.desktop_bangumi_user_agent_required_error
import danmaku.apps.desktop_windows.generated.resources.desktop_cache_expiry_label
import danmaku.apps.desktop_windows.generated.resources.desktop_cache_max_age_days_label
import danmaku.apps.desktop_windows.generated.resources.desktop_clean_expired_cache_action
import danmaku.apps.desktop_windows.generated.resources.desktop_clean_expired_dandanplay_text
import danmaku.apps.desktop_windows.generated.resources.desktop_clean_expired_dandanplay_title
import danmaku.apps.desktop_windows.generated.resources.desktop_clear_action
import danmaku.apps.desktop_windows.generated.resources.desktop_clear_bangumi_action
import danmaku.apps.desktop_windows.generated.resources.desktop_clear_bangumi_text
import danmaku.apps.desktop_windows.generated.resources.desktop_clear_bangumi_title
import danmaku.apps.desktop_windows.generated.resources.desktop_clear_dandanplay_text
import danmaku.apps.desktop_windows.generated.resources.desktop_clear_dandanplay_title
import danmaku.apps.desktop_windows.generated.resources.desktop_clear_my_anime_list_action
import danmaku.apps.desktop_windows.generated.resources.desktop_clear_my_anime_list_text
import danmaku.apps.desktop_windows.generated.resources.desktop_clear_my_anime_list_title
import danmaku.apps.desktop_windows.generated.resources.desktop_connect_my_anime_list_action
import danmaku.apps.desktop_windows.generated.resources.desktop_credential_auth_action
import danmaku.apps.desktop_windows.generated.resources.desktop_current_auth_label
import danmaku.apps.desktop_windows.generated.resources.desktop_dandanplay_providers_description
import danmaku.apps.desktop_windows.generated.resources.desktop_dandanplay_providers_title
import danmaku.apps.desktop_windows.generated.resources.desktop_external_anime_lists_description
import danmaku.apps.desktop_windows.generated.resources.desktop_external_anime_lists_title
import danmaku.apps.desktop_windows.generated.resources.desktop_last_test_label
import danmaku.apps.desktop_windows.generated.resources.desktop_my_anime_list_access_token_keep_label
import danmaku.apps.desktop_windows.generated.resources.desktop_my_anime_list_access_token_optional_label
import danmaku.apps.desktop_windows.generated.resources.desktop_my_anime_list_client_id_label
import danmaku.apps.desktop_windows.generated.resources.desktop_my_anime_list_client_secret_keep_label
import danmaku.apps.desktop_windows.generated.resources.desktop_my_anime_list_client_secret_optional_label
import danmaku.apps.desktop_windows.generated.resources.desktop_my_anime_list_test_label
import danmaku.apps.desktop_windows.generated.resources.desktop_save_dandanplay_settings_action
import danmaku.apps.desktop_windows.generated.resources.desktop_save_external_lists_action
import danmaku.apps.desktop_windows.generated.resources.desktop_signed_auth_action
import danmaku.apps.desktop_windows.generated.resources.desktop_test_bangumi_action
import danmaku.apps.desktop_windows.generated.resources.desktop_test_my_anime_list_action
import danmaku.apps.desktop_windows.generated.resources.desktop_test_saved_action
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
    val libraryImportTitle = stringResource(Res.string.desktop_library_import_title)
    val scanRunningLabel = stringResource(Res.string.desktop_scan_running_label)
    val publishedLabel = stringResource(Res.string.desktop_published_label)
    val lastScanTitle = stringResource(Res.string.desktop_last_scan_title)
    val notRunLabel = stringResource(Res.string.desktop_not_run_label)
    val addFolderAction = stringResource(Res.string.desktop_add_folder_action)
    val importAniRssOutputAction = stringResource(Res.string.desktop_import_ani_rss_output_action)
    val rescanAllAction = stringResource(Res.string.desktop_rescan_all_action)
    val rescanAction = stringResource(Res.string.desktop_rescan_action)
    val scanningAction = stringResource(Res.string.desktop_scanning_action)
    val indexingLibraryRootsLabel = stringResource(Res.string.desktop_indexing_library_roots_label)
    val registeredRootsTitle = stringResource(Res.string.desktop_registered_roots_title)
    val libraryImportEmptyText = stringResource(Res.string.desktop_library_import_empty_text)
    val removeLibraryFolderTitle = stringResource(Res.string.desktop_remove_library_folder_title)
    val removeLibraryFolderText = stringResource(Res.string.desktop_remove_library_folder_text)
    val removeFolderAction = stringResource(Res.string.desktop_remove_folder_action)
    val userSelectedFolderLabel = stringResource(Res.string.desktop_user_selected_folder_label)
    val aniRssOutputFolderLabel = stringResource(Res.string.desktop_ani_rss_output_folder_label)
    val libraryRootAvailableLabel = stringResource(Res.string.desktop_library_root_available_label)
    val libraryRootMissingLabel = stringResource(Res.string.desktop_library_root_missing_label)
    val notScannedYetLabel = stringResource(Res.string.desktop_not_scanned_yet_label)
    val inspectorResizeHandleLabel = stringResource(Res.string.desktop_inspector_resize_handle_label)
    val resetInspectorWidthAction = stringResource(Res.string.desktop_reset_inspector_width_action)
    val inspectorTitle = stringResource(Res.string.desktop_inspector_title)
    val inspectorEmptyText = stringResource(Res.string.desktop_inspector_empty_text)
    val loadingAction = stringResource(Res.string.desktop_loading_action)
    val preparingAction = stringResource(Res.string.desktop_preparing_action)
    val prepareAction = stringResource(Res.string.desktop_prepare_action)
    val prepareShortAction = stringResource(Res.string.desktop_prepare_short_action)
    val detailsAction = stringResource(Res.string.desktop_details_action)
    val favoriteAction = stringResource(Res.string.desktop_favorite_action)
    val unfavoriteAction = stringResource(Res.string.desktop_unfavorite_action)
    val showEpisodeDetailsAction = stringResource(Res.string.desktop_show_episode_details_action)
    val checkCachedDanmakuAction = stringResource(Res.string.desktop_check_cached_danmaku_action)
    val moreEpisodeActionsAction = stringResource(Res.string.desktop_more_episode_actions_action)
    val externalIdsTitle = stringResource(Res.string.desktop_external_ids_title)
    val matchAction = stringResource(Res.string.desktop_match_action)
    val linkAction = stringResource(Res.string.desktop_link_action)
    val replaceAction = stringResource(Res.string.desktop_replace_action)
    val correctAction = stringResource(Res.string.desktop_correct_action)
    val preparePlaybackAction = stringResource(Res.string.desktop_prepare_playback_action)
    val refreshEpisodeMetadataAction = stringResource(Res.string.desktop_refresh_episode_metadata_action)
    val refreshingEpisodeMetadataAction = stringResource(Res.string.desktop_refreshing_episode_metadata_action)
    val refreshSeriesMetadataAction = stringResource(Res.string.desktop_refresh_series_metadata_action)
    val refreshingSeriesMetadataAction = stringResource(Res.string.desktop_refreshing_series_metadata_action)
    val loadIntoPlayerAction = stringResource(Res.string.desktop_load_into_player_action)
    val refreshDanmakuAction = stringResource(Res.string.desktop_refresh_danmaku_action)
    val attachLocalDanmakuAction = stringResource(Res.string.desktop_attach_local_danmaku_action)
    val attachLocalDanmakuShortAction = stringResource(Res.string.desktop_attach_local_danmaku_short_action)
    val removeOverlayAction = stringResource(Res.string.desktop_remove_overlay_action)
    val clearDanmakuCacheAction = stringResource(Res.string.desktop_clear_danmaku_cache_action)
    val clearCacheAction = stringResource(Res.string.desktop_clear_cache_action)
    val enableAutoNextAction = stringResource(Res.string.desktop_enable_auto_next_action)
    val disableAutoNextAction = stringResource(Res.string.desktop_disable_auto_next_action)
    val autoNextOnLabel = stringResource(Res.string.desktop_auto_next_on_label)
    val autoNextOffLabel = stringResource(Res.string.desktop_auto_next_off_label)
    val readinessTitle = stringResource(Res.string.desktop_readiness_title)
    val preparedPlaybackLabel = stringResource(Res.string.desktop_prepared_playback_label)
    val prepareToInspectTracksLabel = stringResource(Res.string.desktop_prepare_to_inspect_tracks_label)
    val notPreparedLabel = stringResource(Res.string.desktop_not_prepared_label)
    val notCheckedYetLabel = stringResource(Res.string.desktop_not_checked_yet_label)
    val episodesTitle = stringResource(Res.string.desktop_episodes_title)
    val advancedTitle = stringResource(Res.string.desktop_advanced_title)
    val favoriteStatusLabel = stringResource(Res.string.desktop_favorite_status_label)
    val downloadFilterAll = stringResource(Res.string.desktop_download_filter_all)
    val downloadFilterActive = stringResource(Res.string.desktop_download_filter_active)
    val downloadFilterQueued = stringResource(Res.string.desktop_download_filter_queued)
    val downloadFilterCompleted = stringResource(Res.string.desktop_download_filter_completed)
    val downloadFilterFailed = stringResource(Res.string.desktop_download_filter_failed)
    val downloadsActiveTitle = stringResource(Res.string.desktop_downloads_active_title)
    val downloadsActiveCaption = stringResource(Res.string.desktop_downloads_active_caption)
    val downloadsQueuedTitle = stringResource(Res.string.desktop_downloads_queued_title)
    val downloadsQueuedCaption = stringResource(Res.string.desktop_downloads_queued_caption)
    val downloadsCompletedTitle = stringResource(Res.string.desktop_downloads_completed_title)
    val downloadsCompletedCaption = stringResource(Res.string.desktop_downloads_completed_caption)
    val downloadsFailedTitle = stringResource(Res.string.desktop_downloads_failed_title)
    val downloadsFailedCaption = stringResource(Res.string.desktop_downloads_failed_caption)
    val downloadQueueTitle = stringResource(Res.string.desktop_download_queue_title)
    val refreshDownloadQueueAction = stringResource(Res.string.desktop_refresh_download_queue_action)
    val downloadQueuePolicyText = stringResource(Res.string.desktop_download_queue_policy_text)
    val downloadQueueEmptyText = stringResource(Res.string.desktop_download_queue_empty_text)
    val downloadQueueFilterEmptyText = stringResource(Res.string.desktop_download_queue_filter_empty_text)
    val openOutputFolderAction = stringResource(Res.string.desktop_open_output_folder_action)
    val removeQueueItemAction = stringResource(Res.string.desktop_remove_queue_item_action)
    val removeDownloadTitle = stringResource(Res.string.desktop_remove_download_title)
    val removeDownloadText = stringResource(Res.string.desktop_remove_download_text)
    val removeAction = stringResource(Res.string.desktop_remove_action)
    val downloadSetupAuthorizedSourcesTitle =
        stringResource(Res.string.desktop_download_setup_authorized_sources_title)
    val downloadSetupAuthorizedSourcesText = stringResource(Res.string.desktop_download_setup_authorized_sources_text)
    val authorizedImportsOnlyLabel = stringResource(Res.string.desktop_authorized_imports_only_label)
    val queueExecutionPlannedLabel = stringResource(Res.string.desktop_queue_execution_planned_label)
    val importRootCountLabel = stringResource(Res.string.desktop_import_root_count_label)
    val addAniRssOutputFolderAction = stringResource(Res.string.desktop_add_ani_rss_output_folder_action)
    val aniRssWebhookTitle = stringResource(Res.string.desktop_ani_rss_webhook_title)
    val noWebhookUrlText = stringResource(Res.string.desktop_no_webhook_url_text)
    val webhookUrlLabel = stringResource(Res.string.desktop_webhook_url_label)
    val webhookHeaderLabel = stringResource(Res.string.desktop_webhook_header_label)
    val webhookTokenLabel = stringResource(Res.string.desktop_webhook_token_label)
    val importRootsTitle = stringResource(Res.string.desktop_import_roots_title)
    val noAniRssRootsText = stringResource(Res.string.desktop_no_ani_rss_roots_text)
    val downloadInspectorTitle = stringResource(Res.string.desktop_download_inspector_title)
    val downloadInspectorEmptyText = stringResource(Res.string.desktop_download_inspector_empty_text)
    val stateLabel = stringResource(Res.string.desktop_state_label)
    val progressLabel = stringResource(Res.string.desktop_progress_label)
    val createdLabel = stringResource(Res.string.desktop_created_label)
    val updatedLabel = stringResource(Res.string.desktop_updated_label)
    val outputLabel = stringResource(Res.string.desktop_output_label)
    val sourceLabel = stringResource(Res.string.desktop_source_label)
    val failureLabel = stringResource(Res.string.desktop_failure_label)
    val openFolderAction = stringResource(Res.string.desktop_open_folder_action)
    val pauseAction = stringResource(Res.string.desktop_pause_action)
    val resumeAction = stringResource(Res.string.desktop_resume_action)
    val retryAction = stringResource(Res.string.desktop_retry_action)
    val cancelAction = stringResource(Res.string.desktop_cancel_action)
    val downloadExecutionPlannedText = stringResource(Res.string.desktop_download_execution_planned_text)
    val dandanplayProvidersTitle = stringResource(Res.string.desktop_dandanplay_providers_title)
    val dandanplayProvidersDescription = stringResource(Res.string.desktop_dandanplay_providers_description)
    val cacheExpiryLabel = stringResource(Res.string.desktop_cache_expiry_label)
    val lastTestLabel = stringResource(Res.string.desktop_last_test_label)
    val apiBaseUrlLabel = stringResource(Res.string.desktop_api_base_url_label)
    val appIdOptionalLabel = stringResource(Res.string.desktop_app_id_optional_label)
    val appSecretKeepLabel = stringResource(Res.string.desktop_app_secret_keep_label)
    val appSecretOptionalLabel = stringResource(Res.string.desktop_app_secret_optional_label)
    val cacheMaxAgeDaysLabel = stringResource(Res.string.desktop_cache_max_age_days_label)
    val signedAuthAction = stringResource(Res.string.desktop_signed_auth_action)
    val credentialAuthAction = stringResource(Res.string.desktop_credential_auth_action)
    val currentAuthLabel = stringResource(Res.string.desktop_current_auth_label)
    val saveDandanplaySettingsAction = stringResource(Res.string.desktop_save_dandanplay_settings_action)
    val testSavedAction = stringResource(Res.string.desktop_test_saved_action)
    val clearAction = stringResource(Res.string.desktop_clear_action)
    val cleanExpiredCacheAction = stringResource(Res.string.desktop_clean_expired_cache_action)
    val clearDandanplayTitle = stringResource(Res.string.desktop_clear_dandanplay_title)
    val clearDandanplayText = stringResource(Res.string.desktop_clear_dandanplay_text)
    val cleanExpiredDandanplayTitle = stringResource(Res.string.desktop_clean_expired_dandanplay_title)
    val cleanExpiredDandanplayText = stringResource(Res.string.desktop_clean_expired_dandanplay_text)
    val externalAnimeListsTitle = stringResource(Res.string.desktop_external_anime_lists_title)
    val externalAnimeListsDescription = stringResource(Res.string.desktop_external_anime_lists_description)
    val myAnimeListTestLabel = stringResource(Res.string.desktop_my_anime_list_test_label)
    val bangumiTestLabel = stringResource(Res.string.desktop_bangumi_test_label)
    val myAnimeListClientIdLabel = stringResource(Res.string.desktop_my_anime_list_client_id_label)
    val myAnimeListAccessTokenKeepLabel =
        stringResource(Res.string.desktop_my_anime_list_access_token_keep_label)
    val myAnimeListAccessTokenOptionalLabel =
        stringResource(Res.string.desktop_my_anime_list_access_token_optional_label)
    val myAnimeListClientSecretKeepLabel =
        stringResource(Res.string.desktop_my_anime_list_client_secret_keep_label)
    val myAnimeListClientSecretOptionalLabel =
        stringResource(Res.string.desktop_my_anime_list_client_secret_optional_label)
    val bangumiApiBaseUrlLabel = stringResource(Res.string.desktop_bangumi_api_base_url_label)
    val bangumiUserAgentLabel = stringResource(Res.string.desktop_bangumi_user_agent_label)
    val bangumiUserAgentRequiredError = stringResource(Res.string.desktop_bangumi_user_agent_required_error)
    val bangumiAccessTokenKeepLabel = stringResource(Res.string.desktop_bangumi_access_token_keep_label)
    val bangumiAccessTokenOptionalLabel = stringResource(Res.string.desktop_bangumi_access_token_optional_label)
    val saveExternalListsAction = stringResource(Res.string.desktop_save_external_lists_action)
    val connectMyAnimeListAction = stringResource(Res.string.desktop_connect_my_anime_list_action)
    val testMyAnimeListAction = stringResource(Res.string.desktop_test_my_anime_list_action)
    val testBangumiAction = stringResource(Res.string.desktop_test_bangumi_action)
    val clearMyAnimeListAction = stringResource(Res.string.desktop_clear_my_anime_list_action)
    val clearBangumiAction = stringResource(Res.string.desktop_clear_bangumi_action)
    val clearMyAnimeListTitle = stringResource(Res.string.desktop_clear_my_anime_list_title)
    val clearMyAnimeListText = stringResource(Res.string.desktop_clear_my_anime_list_text)
    val clearBangumiTitle = stringResource(Res.string.desktop_clear_bangumi_title)
    val clearBangumiText = stringResource(Res.string.desktop_clear_bangumi_text)
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
            this.libraryImportTitle = libraryImportTitle
            this.scanRunningLabel = scanRunningLabel
            this.publishedLabel = publishedLabel
            this.lastScanTitle = lastScanTitle
            this.notRunLabel = notRunLabel
            this.addFolderAction = addFolderAction
            this.importAniRssOutputAction = importAniRssOutputAction
            this.rescanAllAction = rescanAllAction
            this.rescanAction = rescanAction
            this.scanningAction = scanningAction
            this.indexingLibraryRootsLabel = indexingLibraryRootsLabel
            this.registeredRootsTitle = registeredRootsTitle
            this.libraryImportEmptyText = libraryImportEmptyText
            this.removeLibraryFolderTitle = removeLibraryFolderTitle
            this.removeLibraryFolderText = { name -> removeLibraryFolderText.formatResourceString(name) }
            this.removeFolderAction = removeFolderAction
            this.userSelectedFolderLabel = userSelectedFolderLabel
            this.aniRssOutputFolderLabel = aniRssOutputFolderLabel
            this.libraryRootAvailableLabel = libraryRootAvailableLabel
            this.libraryRootMissingLabel = libraryRootMissingLabel
            this.notScannedYetLabel = notScannedYetLabel
            this.inspectorResizeHandleLabel = inspectorResizeHandleLabel
            this.resetInspectorWidthAction = resetInspectorWidthAction
            this.inspectorTitle = inspectorTitle
            this.inspectorEmptyText = inspectorEmptyText
            this.loadingAction = loadingAction
            this.preparingAction = preparingAction
            this.prepareAction = prepareAction
            this.prepareShortAction = prepareShortAction
            this.detailsAction = detailsAction
            this.favoriteAction = favoriteAction
            this.unfavoriteAction = unfavoriteAction
            this.showEpisodeDetailsAction = showEpisodeDetailsAction
            this.checkCachedDanmakuAction = checkCachedDanmakuAction
            this.moreEpisodeActionsAction = moreEpisodeActionsAction
            this.externalIdsTitle = externalIdsTitle
            this.matchAction = matchAction
            this.linkAction = linkAction
            this.replaceAction = replaceAction
            this.correctAction = correctAction
            this.preparePlaybackAction = preparePlaybackAction
            this.refreshEpisodeMetadataAction = refreshEpisodeMetadataAction
            this.refreshingEpisodeMetadataAction = refreshingEpisodeMetadataAction
            this.refreshSeriesMetadataAction = refreshSeriesMetadataAction
            this.refreshingSeriesMetadataAction = refreshingSeriesMetadataAction
            this.loadIntoPlayerAction = loadIntoPlayerAction
            this.refreshDanmakuAction = refreshDanmakuAction
            this.attachLocalDanmakuAction = attachLocalDanmakuAction
            this.attachLocalDanmakuShortAction = attachLocalDanmakuShortAction
            this.removeOverlayAction = removeOverlayAction
            this.clearDanmakuCacheAction = clearDanmakuCacheAction
            this.clearCacheAction = clearCacheAction
            this.enableAutoNextAction = enableAutoNextAction
            this.disableAutoNextAction = disableAutoNextAction
            this.autoNextOnLabel = autoNextOnLabel
            this.autoNextOffLabel = autoNextOffLabel
            this.readinessTitle = readinessTitle
            this.preparedPlaybackLabel = preparedPlaybackLabel
            this.prepareToInspectTracksLabel = prepareToInspectTracksLabel
            this.notPreparedLabel = notPreparedLabel
            this.notCheckedYetLabel = notCheckedYetLabel
            this.episodesTitle = episodesTitle
            this.advancedTitle = advancedTitle
            this.favoriteStatusLabel = favoriteStatusLabel
            downloadFilterTitles = mapOf(
                DownloadQueueFilter.ALL to downloadFilterAll,
                DownloadQueueFilter.ACTIVE to downloadFilterActive,
                DownloadQueueFilter.QUEUED to downloadFilterQueued,
                DownloadQueueFilter.COMPLETED to downloadFilterCompleted,
                DownloadQueueFilter.FAILED to downloadFilterFailed,
            )
            this.downloadsActiveTitle = downloadsActiveTitle
            this.downloadsActiveCaption = downloadsActiveCaption
            this.downloadsQueuedTitle = downloadsQueuedTitle
            this.downloadsQueuedCaption = downloadsQueuedCaption
            this.downloadsCompletedTitle = downloadsCompletedTitle
            this.downloadsCompletedCaption = downloadsCompletedCaption
            this.downloadsFailedTitle = downloadsFailedTitle
            this.downloadsFailedCaption = downloadsFailedCaption
            this.downloadQueueTitle = downloadQueueTitle
            this.refreshDownloadQueueAction = refreshDownloadQueueAction
            this.downloadQueuePolicyText = downloadQueuePolicyText
            this.downloadQueueEmptyText = downloadQueueEmptyText
            this.downloadQueueFilterEmptyText = { filter ->
                downloadQueueFilterEmptyText.formatResourceString(filter.lowercase())
            }
            this.openOutputFolderAction = openOutputFolderAction
            this.removeQueueItemAction = removeQueueItemAction
            this.removeDownloadTitle = removeDownloadTitle
            this.removeDownloadText = { outputPath -> removeDownloadText.formatResourceString(outputPath) }
            this.removeAction = removeAction
            this.downloadSetupAuthorizedSourcesTitle = downloadSetupAuthorizedSourcesTitle
            this.downloadSetupAuthorizedSourcesText = downloadSetupAuthorizedSourcesText
            this.authorizedImportsOnlyLabel = authorizedImportsOnlyLabel
            this.queueExecutionPlannedLabel = queueExecutionPlannedLabel
            this.importRootCountLabel = { count -> importRootCountLabel.formatResourceString(count) }
            this.addAniRssOutputFolderAction = addAniRssOutputFolderAction
            this.aniRssWebhookTitle = aniRssWebhookTitle
            this.noWebhookUrlText = noWebhookUrlText
            this.webhookUrlLabel = webhookUrlLabel
            this.webhookHeaderLabel = webhookHeaderLabel
            this.webhookTokenLabel = webhookTokenLabel
            this.importRootsTitle = importRootsTitle
            this.noAniRssRootsText = noAniRssRootsText
            this.downloadInspectorTitle = downloadInspectorTitle
            this.downloadInspectorEmptyText = downloadInspectorEmptyText
            this.stateLabel = stateLabel
            this.progressLabel = progressLabel
            this.createdLabel = createdLabel
            this.updatedLabel = updatedLabel
            this.outputLabel = outputLabel
            this.sourceLabel = sourceLabel
            this.failureLabel = failureLabel
            this.openFolderAction = openFolderAction
            this.pauseAction = pauseAction
            this.resumeAction = resumeAction
            this.retryAction = retryAction
            this.cancelAction = cancelAction
            this.downloadExecutionPlannedText = downloadExecutionPlannedText
            this.dandanplayProvidersTitle = dandanplayProvidersTitle
            this.dandanplayProvidersDescription = dandanplayProvidersDescription
            this.cacheExpiryLabel = cacheExpiryLabel
            this.lastTestLabel = lastTestLabel
            this.apiBaseUrlLabel = apiBaseUrlLabel
            this.appIdOptionalLabel = appIdOptionalLabel
            this.appSecretKeepLabel = appSecretKeepLabel
            this.appSecretOptionalLabel = appSecretOptionalLabel
            this.cacheMaxAgeDaysLabel = cacheMaxAgeDaysLabel
            this.signedAuthAction = signedAuthAction
            this.credentialAuthAction = credentialAuthAction
            this.currentAuthLabel = { mode -> currentAuthLabel.formatResourceString(mode) }
            this.saveDandanplaySettingsAction = saveDandanplaySettingsAction
            this.testSavedAction = testSavedAction
            this.clearAction = clearAction
            this.cleanExpiredCacheAction = cleanExpiredCacheAction
            this.clearDandanplayTitle = clearDandanplayTitle
            this.clearDandanplayText = clearDandanplayText
            this.cleanExpiredDandanplayTitle = cleanExpiredDandanplayTitle
            this.cleanExpiredDandanplayText = cleanExpiredDandanplayText
            this.externalAnimeListsTitle = externalAnimeListsTitle
            this.externalAnimeListsDescription = externalAnimeListsDescription
            this.myAnimeListTestLabel = myAnimeListTestLabel
            this.bangumiTestLabel = bangumiTestLabel
            this.myAnimeListClientIdLabel = myAnimeListClientIdLabel
            this.myAnimeListAccessTokenKeepLabel = myAnimeListAccessTokenKeepLabel
            this.myAnimeListAccessTokenOptionalLabel = myAnimeListAccessTokenOptionalLabel
            this.myAnimeListClientSecretKeepLabel = myAnimeListClientSecretKeepLabel
            this.myAnimeListClientSecretOptionalLabel = myAnimeListClientSecretOptionalLabel
            this.bangumiApiBaseUrlLabel = bangumiApiBaseUrlLabel
            this.bangumiUserAgentLabel = bangumiUserAgentLabel
            this.bangumiUserAgentRequiredError = bangumiUserAgentRequiredError
            this.bangumiAccessTokenKeepLabel = bangumiAccessTokenKeepLabel
            this.bangumiAccessTokenOptionalLabel = bangumiAccessTokenOptionalLabel
            this.saveExternalListsAction = saveExternalListsAction
            this.connectMyAnimeListAction = connectMyAnimeListAction
            this.testMyAnimeListAction = testMyAnimeListAction
            this.testBangumiAction = testBangumiAction
            this.clearMyAnimeListAction = clearMyAnimeListAction
            this.clearBangumiAction = clearBangumiAction
            this.clearMyAnimeListTitle = clearMyAnimeListTitle
            this.clearMyAnimeListText = clearMyAnimeListText
            this.clearBangumiTitle = clearBangumiTitle
            this.clearBangumiText = clearBangumiText
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
