use std::path::PathBuf;

use crate::catalog::HeadlessStoredLibrary;
use crate::cli::ServerOptions;
use crate::scanner::LibraryScan;
use crate::settings::{HeadlessDandanplayAuthenticationMode, HeadlessServerSettings};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StartupSummary {
    data_directory: PathBuf,
    port: u16,
    effective_root_count: usize,
    cli_root_count: usize,
    web_assets_configured: bool,
    catalog_item_count: Option<usize>,
    catalog_saved_at_epoch_ms: Option<u64>,
    catalog_scan_summary: Option<CatalogScanSummary>,
    provider_summary: ProviderSummary,
}

impl StartupSummary {
    pub fn from_loaded_state(
        options: &ServerOptions,
        settings: &HeadlessServerSettings,
        effective_library_roots: &[PathBuf],
        stored_library: Option<&HeadlessStoredLibrary>,
        catalog_scan_summary: Option<CatalogScanSummary>,
    ) -> Self {
        Self {
            data_directory: options.data_directory.clone(),
            port: options.port,
            effective_root_count: effective_library_roots.len(),
            cli_root_count: options.library_roots.len(),
            web_assets_configured: options.web_assets_root.is_some(),
            catalog_item_count: stored_library
                .map(|library| library.published_library.catalog.items.len()),
            catalog_saved_at_epoch_ms: stored_library.map(|library| library.saved_at_epoch_ms),
            catalog_scan_summary,
            provider_summary: ProviderSummary::from(settings),
        }
    }

    pub fn to_log_lines(&self) -> Vec<String> {
        vec![
            format!(
                "Danmaku Rust library-server foundations loaded; dataDir={}; port={}",
                self.data_directory.display(),
                self.port
            ),
            format!(
                "Library roots: effective={}; cliProvided={}",
                self.effective_root_count, self.cli_root_count
            ),
            format!(
                "Web assets: {}",
                if self.web_assets_configured {
                    "configured"
                } else {
                    "disabled"
                }
            ),
            match (self.catalog_item_count, self.catalog_saved_at_epoch_ms) {
                (Some(items), Some(saved_at)) => {
                    format!("Catalog snapshot: loaded; items={items}; savedAtEpochMs={saved_at}")
                }
                _ => "Catalog snapshot: absent".to_owned(),
            },
            self.catalog_scan_summary
                .as_ref()
                .map(CatalogScanSummary::to_log_line)
                .unwrap_or_else(|| {
                    if self.effective_root_count == 0 {
                        "Catalog scan: skipped; no roots configured".to_owned()
                    } else {
                        "Catalog scan: not run".to_owned()
                    }
                }),
            self.provider_summary.to_log_line(),
        ]
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CatalogScanSummary {
    scanned_root_count: usize,
    item_count: usize,
    subtitle_track_count: usize,
    reused_item_count: usize,
    refreshed_item_count: usize,
}

impl CatalogScanSummary {
    pub fn to_log_line(&self) -> String {
        format!(
            "Catalog scan: completed; roots={}; items={}; subtitles={}; reused={}; refreshed={}",
            self.scanned_root_count,
            self.item_count,
            self.subtitle_track_count,
            self.reused_item_count,
            self.refreshed_item_count,
        )
    }
}

impl From<&LibraryScan> for CatalogScanSummary {
    fn from(scan: &LibraryScan) -> Self {
        Self {
            scanned_root_count: scan.scanned_root_count,
            item_count: scan.published_library.catalog.items.len(),
            subtitle_track_count: scan.subtitle_track_count(),
            reused_item_count: scan.reused_item_count,
            refreshed_item_count: scan.refreshed_item_count,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
struct ProviderSummary {
    dandanplay_app_id_configured: bool,
    dandanplay_app_secret_configured: bool,
    dandanplay_authentication_mode: HeadlessDandanplayAuthenticationMode,
    dandanplay_cache_max_age_days: u32,
    mal_client_id_configured: bool,
    mal_client_secret_configured: bool,
    mal_access_token_configured: bool,
    bangumi_access_token_configured: bool,
}

impl ProviderSummary {
    fn to_log_line(&self) -> String {
        format!(
            "Provider settings: dandanplay(appIdConfigured={}; appSecretConfigured={}; authMode={}; cacheMaxAgeDays={}); externalAnime(malClientIdConfigured={}; malClientSecretConfigured={}; malAccessTokenConfigured={}; bangumiAccessTokenConfigured={})",
            self.dandanplay_app_id_configured,
            self.dandanplay_app_secret_configured,
            self.dandanplay_authentication_mode.jvm_name(),
            self.dandanplay_cache_max_age_days,
            self.mal_client_id_configured,
            self.mal_client_secret_configured,
            self.mal_access_token_configured,
            self.bangumi_access_token_configured,
        )
    }
}

impl From<&HeadlessServerSettings> for ProviderSummary {
    fn from(settings: &HeadlessServerSettings) -> Self {
        Self {
            dandanplay_app_id_configured: settings.dandanplay.app_id.is_some(),
            dandanplay_app_secret_configured: settings.dandanplay.has_app_secret,
            dandanplay_authentication_mode: settings.dandanplay.authentication_mode,
            dandanplay_cache_max_age_days: settings.dandanplay.cache_max_age_days,
            mal_client_id_configured: settings.external_anime.my_anime_list_client_id.is_some(),
            mal_client_secret_configured: settings.external_anime.has_my_anime_list_client_secret,
            mal_access_token_configured: settings.external_anime.has_my_anime_list_access_token,
            bangumi_access_token_configured: settings.external_anime.has_bangumi_access_token,
        }
    }
}

#[cfg(test)]
mod tests {
    use std::path::PathBuf;

    use super::*;
    use crate::catalog::{HeadlessStoredLibrary, PublishedLibrary};
    use crate::settings::{
        HeadlessDandanplayProviderSettings, HeadlessExternalAnimeProviderSettings,
    };

    #[test]
    fn startup_summary_omits_pairing_token_and_provider_values() {
        let options = ServerOptions {
            data_directory: PathBuf::from("data/library-server"),
            library_roots: vec![PathBuf::from("W:/Anime")],
            port: 8686,
            pairing_token: Some("123456".to_owned()),
            web_assets_root: Some(PathBuf::from("apps/web-ui/dist")),
            import_desktop_catalog: None,
        };
        let settings = HeadlessServerSettings {
            pairing_token: "123456".to_owned(),
            library_roots: vec![PathBuf::from("W:/Anime")],
            dandanplay: HeadlessDandanplayProviderSettings {
                base_url: "https://signed.example.invalid/dandanplay?token=raw-url-token"
                    .to_owned(),
                app_id: Some("app-id-secret-ish".to_owned()),
                app_secret: Some("raw-app-secret".to_owned()),
                has_app_secret: true,
                authentication_mode: HeadlessDandanplayAuthenticationMode::Credential,
                cache_max_age_days: 7,
            },
            external_anime: HeadlessExternalAnimeProviderSettings {
                my_anime_list_client_id: Some("mal-client-id".to_owned()),
                my_anime_list_client_secret: Some("raw-mal-secret".to_owned()),
                has_my_anime_list_client_secret: true,
                my_anime_list_access_token: Some("raw-mal-token".to_owned()),
                has_my_anime_list_access_token: true,
                bangumi_base_url: "https://api.bgm.tv/?access_token=raw".to_owned(),
                bangumi_user_agent: "Danmaku QA secret-agent".to_owned(),
                bangumi_access_token: Some("raw-bangumi-token".to_owned()),
                has_bangumi_access_token: true,
            },
        };
        let stored_library = HeadlessStoredLibrary {
            published_library: PublishedLibrary::empty(),
            saved_at_epoch_ms: 1700000000000,
            file_last_modified_epoch_ms_by_id: Default::default(),
        };

        let summary = StartupSummary::from_loaded_state(
            &options,
            &settings,
            &settings.library_roots,
            Some(&stored_library),
            None,
        );
        let output = summary.to_log_lines().join("\n");

        for forbidden in [
            "123456",
            "raw-url-token",
            "app-id-secret-ish",
            "raw-app-secret",
            "mal-client-id",
            "access_token=raw",
            "secret-agent",
        ] {
            assert!(
                !output.contains(forbidden),
                "startup summary leaked {forbidden}: {output}"
            );
        }
        assert!(output.contains("appSecretConfigured=true"));
        assert!(output.contains("malAccessTokenConfigured=true"));
        assert!(output.contains("Catalog snapshot: loaded"));
    }
}
