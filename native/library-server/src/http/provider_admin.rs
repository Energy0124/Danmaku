use std::{
    collections::BTreeMap,
    path::PathBuf,
    sync::{Arc, Mutex, RwLock},
};

use serde::{Deserialize, Serialize};

use crate::{
    Result,
    attention::AttentionFailureStore,
    catalog::ExternalAnimeId,
    dandanplay::{DandanplayResolver, apply_dandanplay_local_defaults},
    external_provider::{
        ExternalAnimeTrackingUpdate, ExternalProviderService, MAL_OAUTH_CALLBACK_URL,
        MyAnimeListTokenError, exchange_my_anime_list_authorization_code, fetch_bangumi_identity,
        fetch_my_anime_list_identity, my_anime_list_authorization_url, provider_runtime_status,
        refresh_my_anime_list_token,
    },
    provider_secrets::{ProviderSecretStore, ProviderSecrets},
    settings::{
        HeadlessDandanplayAuthenticationMode, HeadlessServerSettings, SettingsStore,
        apply_external_anime_local_defaults, embedded_my_anime_list_client_id, is_http_base_url,
        is_https_base_url,
    },
    tracking::{ExternalTrackingStore, current_epoch_ms},
};

use super::LanProviderSettingsStatus;

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(super) struct ProviderSettingsUpdate {
    dandanplay: DandanplaySettingsUpdate,
    external_anime: ExternalAnimeSettingsUpdate,
}
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(super) struct ExternalTrackingMappingRequest {
    pub(super) local_series_id: String,
    pub(super) anime_id: ExternalAnimeId,
}
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(super) struct ExternalTrackingSyncRequest {
    pub(super) expected_updates: Vec<ExternalAnimeTrackingUpdate>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(super) struct ExternalTrackingConflictImportRequest {
    pub(super) local_series_id: String,
    pub(super) anime_id: ExternalAnimeId,
    pub(super) expected_external_watched_episodes: u32,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(super) struct MyAnimeListOAuthCompleteRequest {
    flow_id: String,
    state: String,
    code: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(super) struct BangumiAccountRequest {
    pub(super) access_token: String,
}

#[derive(Debug, Clone)]
struct PendingMyAnimeListOAuth {
    state: String,
    code_verifier: String,
    expires_at_epoch_ms: u64,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub(super) struct MyAnimeListOAuthStartResponse {
    flow_id: String,
    authorization_url: String,
    callback_url: &'static str,
    expires_at_epoch_ms: u64,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub(super) struct ProviderAccountsResponse {
    pub(super) my_anime_list: ProviderAccountStatus,
    pub(super) bangumi: ProviderAccountStatus,
    pub(super) bangumi_token_url: &'static str,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub(super) struct ProviderAccountStatus {
    pub(super) state: &'static str,
    #[serde(skip_serializing_if = "Option::is_none")]
    user_id: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    display_name: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    last_verified_at_epoch_ms: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub(super) reason_code: Option<&'static str>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(super) struct DandanplaySettingsUpdate {
    base_url: String,
    #[serde(default)]
    app_id: Option<String>,
    #[serde(default)]
    app_secret: Option<String>,
    #[serde(default)]
    clear_app_secret: bool,
    authentication_mode: String,
    cache_max_age_days: u32,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(super) struct ExternalAnimeSettingsUpdate {
    #[serde(default)]
    my_anime_list_client_id: Option<String>,
    #[serde(default)]
    my_anime_list_client_secret: Option<String>,
    #[serde(default)]
    clear_my_anime_list_client_secret: bool,
    #[serde(default)]
    my_anime_list_access_token: Option<String>,
    #[serde(default)]
    clear_my_anime_list_access_token: bool,
    bangumi_base_url: String,
    bangumi_user_agent: String,
    #[serde(default)]
    bangumi_access_token: Option<String>,
    #[serde(default)]
    clear_bangumi_access_token: bool,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub(super) struct ProviderSettingsAdminResponse {
    settings: LanProviderSettingsStatus,
    runtime: crate::external_provider::LanProviderRuntimeStatus,
}

#[derive(Debug)]
pub(super) struct ProviderRuntimeResources {
    settings: LanProviderSettingsStatus,
    runtime: crate::external_provider::LanProviderRuntimeStatus,
    pub(super) external_provider_service: Arc<ExternalProviderService>,
    dandanplay_resolver: Option<Arc<DandanplayResolver>>,
}

impl ProviderRuntimeResources {
    fn from_settings(
        settings: &HeadlessServerSettings,
        dandanplay_resolver: Option<Arc<DandanplayResolver>>,
    ) -> Self {
        Self {
            settings: LanProviderSettingsStatus::from(settings),
            runtime: provider_runtime_status(settings),
            external_provider_service: Arc::new(ExternalProviderService::from_settings(settings)),
            dandanplay_resolver,
        }
    }
}

#[derive(Debug)]
pub struct ProviderAdminState {
    data_directory: PathBuf,
    settings_store: SettingsStore,
    secret_store: ProviderSecretStore,
    pub(super) persisted_settings: Mutex<HeadlessServerSettings>,
    pub(super) runtime: RwLock<ProviderRuntimeResources>,
    attention_failures: AttentionFailureStore,
    tracking_store: ExternalTrackingStore,
    pending_my_anime_list_oauth: Mutex<BTreeMap<String, PendingMyAnimeListOAuth>>,
}

impl ProviderAdminState {
    pub fn new(
        data_directory: PathBuf,
        persisted_settings: HeadlessServerSettings,
        effective_settings: HeadlessServerSettings,
        dandanplay_resolver: Option<Arc<DandanplayResolver>>,
    ) -> Result<Self> {
        let secret_store =
            ProviderSecretStore::platform(data_directory.join("provider-secrets.json"));
        Self::with_secret_store(
            data_directory,
            persisted_settings,
            effective_settings,
            dandanplay_resolver,
            secret_store,
        )
    }

    fn with_secret_store(
        data_directory: PathBuf,
        persisted_settings: HeadlessServerSettings,
        effective_settings: HeadlessServerSettings,
        dandanplay_resolver: Option<Arc<DandanplayResolver>>,
        secret_store: ProviderSecretStore,
    ) -> Result<Self> {
        Ok(Self {
            settings_store: SettingsStore::new(data_directory.join("server-settings.json")),
            secret_store,
            attention_failures: AttentionFailureStore::open(
                data_directory.join("library-attention.json"),
            )?,
            tracking_store: ExternalTrackingStore::open(
                data_directory.join("external-tracking.json"),
            )?,
            pending_my_anime_list_oauth: Mutex::new(BTreeMap::new()),
            data_directory,
            persisted_settings: Mutex::new(persisted_settings),
            runtime: RwLock::new(ProviderRuntimeResources::from_settings(
                &effective_settings,
                dandanplay_resolver,
            )),
        })
    }

    #[cfg(test)]
    pub(super) fn new_for_tests(
        data_directory: PathBuf,
        persisted_settings: HeadlessServerSettings,
        effective_settings: HeadlessServerSettings,
        secret_store: ProviderSecretStore,
    ) -> Self {
        Self::with_secret_store(
            data_directory,
            persisted_settings,
            effective_settings,
            None,
            secret_store,
        )
        .expect("test tracking store should open")
    }

    pub(super) fn tracking_store(&self) -> &ExternalTrackingStore {
        &self.tracking_store
    }

    pub(super) fn attention_failures(&self) -> &AttentionFailureStore {
        &self.attention_failures
    }

    pub(super) fn snapshot(&self) -> ProviderSettingsAdminResponse {
        let runtime = self
            .runtime
            .read()
            .expect("provider runtime lock should not be poisoned");
        ProviderSettingsAdminResponse {
            settings: runtime.settings.clone(),
            runtime: runtime.runtime.clone(),
        }
    }

    pub(super) fn provider_settings(&self) -> LanProviderSettingsStatus {
        self.runtime
            .read()
            .expect("provider runtime lock should not be poisoned")
            .settings
            .clone()
    }

    pub(super) fn runtime_status(&self) -> crate::external_provider::LanProviderRuntimeStatus {
        self.runtime
            .read()
            .expect("provider runtime lock should not be poisoned")
            .runtime
            .clone()
    }

    pub(super) fn external_provider_service(&self) -> Arc<ExternalProviderService> {
        Arc::clone(
            &self
                .runtime
                .read()
                .expect("provider runtime lock should not be poisoned")
                .external_provider_service,
        )
    }

    pub(super) fn dandanplay_resolver(&self) -> Option<Arc<DandanplayResolver>> {
        self.runtime
            .read()
            .expect("provider runtime lock should not be poisoned")
            .dandanplay_resolver
            .clone()
    }

    pub(super) fn update(
        &self,
        update: ProviderSettingsUpdate,
    ) -> crate::Result<ProviderSettingsAdminResponse> {
        let mut persisted = self
            .persisted_settings
            .lock()
            .map_err(|_| crate::LibraryServerError::new("provider settings lock is unavailable"))?;
        let mut next = persisted.clone();
        apply_provider_settings_update(&mut next, update)?;

        self.commit_settings(&mut persisted, next)
    }

    fn commit_settings(
        &self,
        persisted: &mut HeadlessServerSettings,
        next: HeadlessServerSettings,
    ) -> crate::Result<ProviderSettingsAdminResponse> {
        let previous_secrets = ProviderSecrets::from_settings(persisted);
        let next_secrets = ProviderSecrets::from_settings(&next);
        self.secret_store.save(&next_secrets)?;
        if let Err(error) = self.settings_store.save(&next) {
            let _ = self.secret_store.save(&previous_secrets);
            return Err(error);
        }

        let effective =
            apply_external_anime_local_defaults(apply_dandanplay_local_defaults(next.clone()));
        let resolver =
            DandanplayResolver::from_settings(&effective, &self.data_directory).map(Arc::new);
        let resources = ProviderRuntimeResources::from_settings(&effective, resolver);
        let response = ProviderSettingsAdminResponse {
            settings: resources.settings.clone(),
            runtime: resources.runtime.clone(),
        };
        *self.runtime.write().map_err(|_| {
            crate::LibraryServerError::new("provider runtime lock is unavailable")
        })? = resources;
        *persisted = next;
        Ok(response)
    }

    fn account_client_id(&self) -> Option<String> {
        self.runtime
            .read()
            .ok()
            .and_then(|runtime| {
                runtime
                    .settings
                    .external_anime
                    .my_anime_list_client_id
                    .clone()
            })
            .or_else(embedded_my_anime_list_client_id)
    }

    pub(super) fn accounts(&self) -> ProviderAccountsResponse {
        let settings = self
            .persisted_settings
            .lock()
            .expect("provider settings lock should not be poisoned");
        let external = &settings.external_anime;
        let my_anime_list = if self.account_client_id().is_none() {
            ProviderAccountStatus::unavailable("MAL_CLIENT_ID_UNAVAILABLE")
        } else if external.my_anime_list_access_token.is_some()
            && external.my_anime_list_user_id.is_some()
        {
            ProviderAccountStatus::connected(
                external.my_anime_list_user_id.clone(),
                external.my_anime_list_user_name.clone(),
                external.my_anime_list_last_verified_at_epoch_ms,
            )
        } else if external.my_anime_list_user_id.is_some() {
            ProviderAccountStatus::needs_reconnect("AUTHORIZATION_EXPIRED")
        } else if external.my_anime_list_access_token.is_some() {
            ProviderAccountStatus::needs_reconnect("ACCOUNT_NOT_VERIFIED")
        } else {
            ProviderAccountStatus::disconnected()
        };
        let bangumi =
            if external.bangumi_access_token.is_some() && external.bangumi_user_id.is_some() {
                ProviderAccountStatus::connected(
                    external.bangumi_user_id.clone(),
                    external.bangumi_user_name.clone(),
                    external.bangumi_last_verified_at_epoch_ms,
                )
            } else if external.bangumi_access_token.is_some() {
                ProviderAccountStatus::needs_reconnect("ACCOUNT_NOT_VERIFIED")
            } else {
                ProviderAccountStatus::disconnected()
            };
        ProviderAccountsResponse {
            my_anime_list,
            bangumi,
            bangumi_token_url: "https://next.bgm.tv/demo/access-token",
        }
    }

    pub(super) fn start_my_anime_list_oauth(&self) -> crate::Result<MyAnimeListOAuthStartResponse> {
        let client_id = self.account_client_id().ok_or_else(|| {
            crate::LibraryServerError::new(
                "MyAnimeList sign-in is unavailable because this build has no client ID",
            )
        })?;
        let flow_id = random_hex(16)?;
        let state = random_hex(24)?;
        let code_verifier = random_hex(32)?;
        let expires_at_epoch_ms = current_epoch_ms().saturating_add(10 * 60 * 1_000);
        self.pending_my_anime_list_oauth
            .lock()
            .map_err(|_| crate::LibraryServerError::new("OAuth state lock is unavailable"))?
            .insert(
                flow_id.clone(),
                PendingMyAnimeListOAuth {
                    state: state.clone(),
                    code_verifier: code_verifier.clone(),
                    expires_at_epoch_ms,
                },
            );
        Ok(MyAnimeListOAuthStartResponse {
            flow_id,
            authorization_url: my_anime_list_authorization_url(&client_id, &state, &code_verifier),
            callback_url: MAL_OAUTH_CALLBACK_URL,
            expires_at_epoch_ms,
        })
    }

    pub(super) fn complete_my_anime_list_oauth(
        &self,
        request: MyAnimeListOAuthCompleteRequest,
    ) -> crate::Result<ProviderAccountsResponse> {
        let pending = self
            .pending_my_anime_list_oauth
            .lock()
            .map_err(|_| crate::LibraryServerError::new("OAuth state lock is unavailable"))?
            .remove(request.flow_id.trim())
            .ok_or_else(|| crate::LibraryServerError::new("OAuth flow is missing or expired"))?;
        if pending.expires_at_epoch_ms < current_epoch_ms() || pending.state != request.state {
            return Err(crate::LibraryServerError::new(
                "OAuth state is invalid or expired",
            ));
        }
        let client_id = self.account_client_id().ok_or_else(|| {
            crate::LibraryServerError::new("MyAnimeList client ID is unavailable")
        })?;
        let token = exchange_my_anime_list_authorization_code(
            &client_id,
            request.code.trim(),
            &pending.code_verifier,
        )?;
        let identity = fetch_my_anime_list_identity(&token.access_token)?;
        let now = current_epoch_ms();
        let mut persisted = self
            .persisted_settings
            .lock()
            .map_err(|_| crate::LibraryServerError::new("provider settings lock is unavailable"))?;
        let mut next = persisted.clone();
        let external = &mut next.external_anime;
        external.my_anime_list_access_token = Some(token.access_token);
        external.has_my_anime_list_access_token = true;
        external.my_anime_list_refresh_token = token.refresh_token;
        external.has_my_anime_list_refresh_token = external.my_anime_list_refresh_token.is_some();
        external.my_anime_list_token_expires_at_epoch_ms =
            Some(now.saturating_add(token.expires_in_seconds.saturating_mul(1_000)));
        external.my_anime_list_user_id = Some(identity.user_id);
        external.my_anime_list_user_name = Some(identity.display_name);
        external.my_anime_list_last_verified_at_epoch_ms = Some(now);
        self.commit_settings(&mut persisted, next)?;
        drop(persisted);
        Ok(self.accounts())
    }

    pub(super) fn connect_bangumi(
        &self,
        access_token: String,
    ) -> crate::Result<ProviderAccountsResponse> {
        let access_token = access_token.trim();
        if access_token.is_empty() || access_token.len() > 4_096 {
            return Err(crate::LibraryServerError::new("Bangumi token is invalid"));
        }
        let (base_url, user_agent) = {
            let persisted = self.persisted_settings.lock().map_err(|_| {
                crate::LibraryServerError::new("provider settings lock is unavailable")
            })?;
            (
                persisted.external_anime.bangumi_base_url.clone(),
                persisted.external_anime.bangumi_user_agent.clone(),
            )
        };
        let identity = fetch_bangumi_identity(&base_url, &user_agent, access_token)?;
        let now = current_epoch_ms();
        let mut persisted = self
            .persisted_settings
            .lock()
            .map_err(|_| crate::LibraryServerError::new("provider settings lock is unavailable"))?;
        let mut next = persisted.clone();
        let external = &mut next.external_anime;
        external.bangumi_access_token = Some(access_token.to_owned());
        external.has_bangumi_access_token = true;
        external.bangumi_user_id = Some(identity.user_id);
        external.bangumi_user_name = Some(identity.display_name);
        external.bangumi_last_verified_at_epoch_ms = Some(now);
        self.commit_settings(&mut persisted, next)?;
        drop(persisted);
        Ok(self.accounts())
    }

    pub(super) fn disconnect_account(
        &self,
        provider: &str,
    ) -> crate::Result<ProviderAccountsResponse> {
        let mut persisted = self
            .persisted_settings
            .lock()
            .map_err(|_| crate::LibraryServerError::new("provider settings lock is unavailable"))?;
        let mut next = persisted.clone();
        let external = &mut next.external_anime;
        match provider {
            "myanimelist" => {
                external.my_anime_list_access_token = None;
                external.has_my_anime_list_access_token = false;
                external.my_anime_list_refresh_token = None;
                external.has_my_anime_list_refresh_token = false;
                external.my_anime_list_token_expires_at_epoch_ms = None;
                external.my_anime_list_user_id = None;
                external.my_anime_list_user_name = None;
                external.my_anime_list_last_verified_at_epoch_ms = None;
            }
            "bangumi" => {
                external.bangumi_access_token = None;
                external.has_bangumi_access_token = false;
                external.bangumi_user_id = None;
                external.bangumi_user_name = None;
                external.bangumi_last_verified_at_epoch_ms = None;
            }
            _ => return Err(crate::LibraryServerError::new("unknown provider account")),
        }
        self.commit_settings(&mut persisted, next)?;
        drop(persisted);
        Ok(self.accounts())
    }

    pub(super) fn refresh_my_anime_list_if_needed(&self) -> crate::Result<()> {
        let now = current_epoch_ms();
        let (client_id, refresh_token, expires_at) = {
            let persisted = self.persisted_settings.lock().map_err(|_| {
                crate::LibraryServerError::new("provider settings lock is unavailable")
            })?;
            (
                self.account_client_id(),
                persisted.external_anime.my_anime_list_refresh_token.clone(),
                persisted
                    .external_anime
                    .my_anime_list_token_expires_at_epoch_ms,
            )
        };
        if !expires_at.is_some_and(|expires| expires <= now.saturating_add(60_000)) {
            return Ok(());
        }
        let (Some(client_id), Some(refresh_token)) = (client_id, refresh_token) else {
            return Ok(());
        };
        let token = match refresh_my_anime_list_token(&client_id, &refresh_token) {
            Ok(token) => token,
            Err(error) => return self.handle_my_anime_list_refresh_error(error),
        };
        let mut persisted = self
            .persisted_settings
            .lock()
            .map_err(|_| crate::LibraryServerError::new("provider settings lock is unavailable"))?;
        let mut next = persisted.clone();
        next.external_anime.my_anime_list_access_token = Some(token.access_token);
        next.external_anime.my_anime_list_refresh_token =
            token.refresh_token.or(Some(refresh_token));
        next.external_anime.my_anime_list_token_expires_at_epoch_ms =
            Some(now.saturating_add(token.expires_in_seconds.saturating_mul(1_000)));
        self.commit_settings(&mut persisted, next)?;
        Ok(())
    }

    pub(super) fn handle_my_anime_list_refresh_error(
        &self,
        error: MyAnimeListTokenError,
    ) -> crate::Result<()> {
        match error {
            MyAnimeListTokenError::InvalidGrant => {
                let mut persisted = self.persisted_settings.lock().map_err(|_| {
                    crate::LibraryServerError::new("provider settings lock is unavailable")
                })?;
                let mut next = persisted.clone();
                let external = &mut next.external_anime;
                external.my_anime_list_access_token = None;
                external.has_my_anime_list_access_token = false;
                external.my_anime_list_refresh_token = None;
                external.has_my_anime_list_refresh_token = false;
                external.my_anime_list_token_expires_at_epoch_ms = None;
                self.commit_settings(&mut persisted, next)?;
                Err(crate::LibraryServerError::new(
                    "MyAnimeList authorization expired; reconnect the account",
                ))
            }
            MyAnimeListTokenError::Other(error) => Err(error),
        }
    }
}

impl ProviderAccountStatus {
    fn disconnected() -> Self {
        Self {
            state: "DISCONNECTED",
            user_id: None,
            display_name: None,
            last_verified_at_epoch_ms: None,
            reason_code: None,
        }
    }

    fn connected(
        user_id: Option<String>,
        display_name: Option<String>,
        last_verified_at_epoch_ms: Option<u64>,
    ) -> Self {
        Self {
            state: "CONNECTED",
            user_id,
            display_name,
            last_verified_at_epoch_ms,
            reason_code: None,
        }
    }

    fn needs_reconnect(reason_code: &'static str) -> Self {
        Self {
            state: "NEEDS_RECONNECT",
            reason_code: Some(reason_code),
            ..Self::disconnected()
        }
    }

    fn unavailable(reason_code: &'static str) -> Self {
        Self {
            state: "UNAVAILABLE",
            reason_code: Some(reason_code),
            ..Self::disconnected()
        }
    }
}

fn random_hex(byte_count: usize) -> crate::Result<String> {
    const HEX: &[u8; 16] = b"0123456789abcdef";
    let mut bytes = vec![0_u8; byte_count];
    getrandom::fill(&mut bytes).map_err(|error| {
        crate::LibraryServerError::with_context(error, "OAuth randomness failed")
    })?;
    let mut value = String::with_capacity(byte_count * 2);
    for byte in bytes {
        value.push(HEX[(byte >> 4) as usize] as char);
        value.push(HEX[(byte & 0x0f) as usize] as char);
    }
    Ok(value)
}

fn apply_provider_settings_update(
    settings: &mut HeadlessServerSettings,
    update: ProviderSettingsUpdate,
) -> crate::Result<()> {
    if !is_http_base_url(&update.dandanplay.base_url) || update.dandanplay.base_url.len() > 2_048 {
        return Err(crate::LibraryServerError::new(
            "dandanplay baseUrl must be a valid HTTP(S) URL",
        ));
    }
    if update.dandanplay.cache_max_age_days == 0 {
        return Err(crate::LibraryServerError::new(
            "dandanplay cacheMaxAgeDays must be at least 1",
        ));
    }
    let authentication_mode = match update
        .dandanplay
        .authentication_mode
        .trim()
        .to_ascii_uppercase()
        .as_str()
    {
        "SIGNED" => HeadlessDandanplayAuthenticationMode::Signed,
        "CREDENTIAL" => HeadlessDandanplayAuthenticationMode::Credential,
        _ => {
            return Err(crate::LibraryServerError::new(
                "dandanplay authenticationMode must be SIGNED or CREDENTIAL",
            ));
        }
    };
    settings.dandanplay.base_url = update.dandanplay.base_url.trim().to_owned();
    settings.dandanplay.app_id =
        normalized_optional(update.dandanplay.app_id, 512, "dandanplay appId")?;
    settings.dandanplay.authentication_mode = authentication_mode;
    settings.dandanplay.cache_max_age_days = update.dandanplay.cache_max_age_days;
    apply_secret_update(
        &mut settings.dandanplay.app_secret,
        update.dandanplay.app_secret,
        update.dandanplay.clear_app_secret,
        "dandanplay appSecret",
    )?;
    settings.dandanplay.has_app_secret = settings.dandanplay.app_secret.is_some();

    if !is_https_base_url(&update.external_anime.bangumi_base_url)
        || update.external_anime.bangumi_base_url.len() > 2_048
    {
        return Err(crate::LibraryServerError::new(
            "Bangumi baseUrl must be a valid HTTPS URL",
        ));
    }
    let bangumi_user_agent = update.external_anime.bangumi_user_agent.trim();
    if bangumi_user_agent.is_empty() || bangumi_user_agent.len() > 512 {
        return Err(crate::LibraryServerError::new(
            "Bangumi userAgent must be between 1 and 512 bytes",
        ));
    }
    settings.external_anime.my_anime_list_client_id = normalized_optional(
        update.external_anime.my_anime_list_client_id,
        512,
        "MyAnimeList clientId",
    )?;
    settings.external_anime.bangumi_base_url =
        update.external_anime.bangumi_base_url.trim().to_owned();
    settings.external_anime.bangumi_user_agent = bangumi_user_agent.to_owned();
    apply_secret_update(
        &mut settings.external_anime.my_anime_list_client_secret,
        update.external_anime.my_anime_list_client_secret,
        update.external_anime.clear_my_anime_list_client_secret,
        "MyAnimeList clientSecret",
    )?;
    apply_secret_update(
        &mut settings.external_anime.my_anime_list_access_token,
        update.external_anime.my_anime_list_access_token,
        update.external_anime.clear_my_anime_list_access_token,
        "MyAnimeList accessToken",
    )?;
    apply_secret_update(
        &mut settings.external_anime.bangumi_access_token,
        update.external_anime.bangumi_access_token,
        update.external_anime.clear_bangumi_access_token,
        "Bangumi accessToken",
    )?;
    settings.external_anime.has_my_anime_list_client_secret = settings
        .external_anime
        .my_anime_list_client_secret
        .is_some();
    settings.external_anime.has_my_anime_list_access_token =
        settings.external_anime.my_anime_list_access_token.is_some();
    settings.external_anime.has_bangumi_access_token =
        settings.external_anime.bangumi_access_token.is_some();
    Ok(())
}

fn normalized_optional(
    value: Option<String>,
    max_bytes: usize,
    label: &str,
) -> crate::Result<Option<String>> {
    let value = value.map(|value| value.trim().to_owned());
    let Some(value) = value.filter(|value| !value.is_empty()) else {
        return Ok(None);
    };
    if value.len() > max_bytes {
        return Err(crate::LibraryServerError::new(format!(
            "{label} must be no more than {max_bytes} bytes"
        )));
    }
    Ok(Some(value))
}

fn apply_secret_update(
    current: &mut Option<String>,
    replacement: Option<String>,
    clear: bool,
    label: &str,
) -> crate::Result<()> {
    if clear && replacement.is_some() {
        return Err(crate::LibraryServerError::new(format!(
            "{label} cannot be replaced and cleared in the same request"
        )));
    }
    if clear {
        *current = None;
        return Ok(());
    }
    let Some(replacement) = replacement else {
        return Ok(());
    };
    if replacement.trim().is_empty() || replacement.len() > 4_096 {
        return Err(crate::LibraryServerError::new(format!(
            "{label} must be between 1 and 4096 bytes"
        )));
    }
    *current = Some(replacement);
    Ok(())
}
