package app.danmaku.desktop

import app.danmaku.provider.dandanplay.DandanplayAuthenticationMode
import app.danmaku.provider.dandanplay.DandanplayDanmakuClient
import app.danmaku.domain.DanmakuDisplaySettings
import app.danmaku.domain.ExternalAnimeMatchQuery
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.jvm.JvmLanLibraryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI

internal class DesktopShellSettingsActions(
    private val scope: CoroutineScope,
    private val playbackPreferencesStore: DesktopPlaybackPreferencesStore,
    private val dandanplayCredentialStore: DandanplayCredentialStore,
    private val externalAnimeCredentialStore: ExternalAnimeCredentialStore,
    private val myAnimeListOAuthService: MyAnimeListOAuthService,
    private val dandanplayDanmakuResolver: DesktopDandanplayDanmakuResolver,
    private val catalogStore: DesktopLibraryCatalogStore,
    private val settingsState: DesktopShellSettingsState,
    private val libraryState: DesktopShellLibraryState,
    private val serverBaseUrl: () -> String,
    private val pairingToken: () -> String,
    private val appendDiagnostic: (String, String) -> Unit,
    private val updateOverlayStatus: (String) -> Unit,
) {
    fun saveDandanplaySettings(
        baseUrl: String,
        appId: String?,
        appSecret: String?,
        authenticationMode: DandanplayAuthenticationMode,
        cacheMaxAgeDays: Int,
    ) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    dandanplayCredentialStore.saveSettings(
                        baseUrl = baseUrl,
                        appId = appId,
                        appSecret = appSecret,
                        authenticationMode = authenticationMode,
                        cacheMaxAgeDays = cacheMaxAgeDays,
                    )
                }
            }.onSuccess { updatedSettings ->
                settingsState.dandanplaySettings = updatedSettings
                appendDiagnostic("settings", "Saved dandanplay provider settings for ${updatedSettings.baseUrl}")
            }.onFailure {
                appendDiagnostic("settings", "Failed to save dandanplay provider settings: ${it.message}")
            }
        }
    }

    fun clearDandanplaySettings() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    dandanplayCredentialStore.deleteSettings()
                    dandanplayCredentialStore.loadSettings()
                }
            }.onSuccess { updatedSettings ->
                settingsState.dandanplaySettings = updatedSettings
                appendDiagnostic("settings", "Cleared dandanplay provider settings")
            }.onFailure {
                appendDiagnostic("settings", "Failed to clear dandanplay provider settings: ${it.message}")
            }
        }
    }

    fun saveExternalAnimeProviderSettings(
        myAnimeListClientId: String?,
        myAnimeListClientSecret: String?,
        myAnimeListAccessToken: String?,
        bangumiBaseUrl: String,
        bangumiUserAgent: String,
        bangumiAccessToken: String?,
    ) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    externalAnimeCredentialStore.saveSettings(
                        myAnimeListClientId = myAnimeListClientId,
                        myAnimeListClientSecret = myAnimeListClientSecret,
                        myAnimeListAccessToken = myAnimeListAccessToken,
                        bangumiBaseUrl = bangumiBaseUrl,
                        bangumiUserAgent = bangumiUserAgent,
                        bangumiAccessToken = bangumiAccessToken,
                    )
                }
            }.onSuccess { updatedSettings ->
                settingsState.externalAnimeProviderSettings = updatedSettings
                appendDiagnostic("settings", "Saved MyAnimeList/Bangumi provider settings")
            }.onFailure {
                appendDiagnostic("settings", "Failed to save external anime provider settings: ${it.message}")
            }
        }
    }

    fun startMyAnimeListOAuth(
        myAnimeListClientId: String?,
        myAnimeListClientSecret: String?,
    ) {
        scope.launch {
            runCatching {
                val updatedSettings = withContext(Dispatchers.IO) {
                    externalAnimeCredentialStore.saveSettings(
                        myAnimeListClientId = myAnimeListClientId,
                        myAnimeListClientSecret = myAnimeListClientSecret,
                        myAnimeListAccessToken = null,
                        bangumiBaseUrl = settingsState.externalAnimeProviderSettings.bangumiBaseUrl,
                        bangumiUserAgent = settingsState.externalAnimeProviderSettings.bangumiUserAgent,
                        bangumiAccessToken = null,
                    )
                }
                settingsState.externalAnimeProviderSettings = updatedSettings
                val redirectUri = "${serverBaseUrl()}${DesktopLibraryServerRuntime.MY_ANIME_LIST_OAUTH_CALLBACK_PATH}"
                val clientSecret = myAnimeListClientSecret?.trim()?.takeIf(String::isNotBlank)
                    ?: withContext(Dispatchers.IO) {
                        externalAnimeCredentialStore.loadMyAnimeListClientSecret()
                    }
                val authorizationUri = myAnimeListOAuthService.beginAuthorization(
                    redirectUri = redirectUri,
                    clientSecret = clientSecret,
                )
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(authorizationUri)
                    "Opened MyAnimeList authorization in browser; callback=$redirectUri"
                } else {
                    "Open this MyAnimeList authorization URL: $authorizationUri"
                }
            }.onSuccess { message ->
                appendDiagnostic("settings", message)
            }.onFailure {
                appendDiagnostic("settings", "Failed to start MyAnimeList OAuth: ${it.message}")
            }
        }
    }

    fun clearMyAnimeListSettings() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    externalAnimeCredentialStore.clearMyAnimeListSettings()
                }
            }.onSuccess { updatedSettings ->
                settingsState.externalAnimeProviderSettings = updatedSettings
                appendDiagnostic("settings", "Cleared MyAnimeList provider settings")
            }.onFailure {
                appendDiagnostic("settings", "Failed to clear MyAnimeList provider settings: ${it.message}")
            }
        }
    }

    fun clearBangumiSettings() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    externalAnimeCredentialStore.clearBangumiSettings()
                }
            }.onSuccess { updatedSettings ->
                settingsState.externalAnimeProviderSettings = updatedSettings
                appendDiagnostic("settings", "Cleared Bangumi provider settings")
            }.onFailure {
                appendDiagnostic("settings", "Failed to clear Bangumi provider settings: ${it.message}")
            }
        }
    }

    fun testDandanplayConnection() {
        settingsState.dandanplayConnectionTestStatus = SettingsConnectionTestStatus(
            outcome = SettingsConnectionTestOutcome.TESTING,
            detail = "Checking saved dandanplay settings...",
        )
        scope.launch {
            appendDiagnostic("settings", "Testing saved dandanplay connection...")
            runCatching {
                withContext(Dispatchers.IO) {
                    DandanplayDanmakuClient(dandanplayCredentialStore.loadConnection())
                        .fetchAnimeDetails(1L)
                }
            }.onSuccess { anime ->
                appendDiagnostic(
                    "settings",
                    "dandanplay connection OK: ${anime.titles.primary} (#${anime.id.value})",
                )
                settingsState.dandanplayConnectionTestStatus = SettingsConnectionTestStatus(
                    outcome = SettingsConnectionTestOutcome.SUCCESS,
                    detail = "${anime.titles.primary} (#${anime.id.value})",
                )
            }.onFailure {
                val message = it.readableMessage()
                appendDiagnostic("settings", "dandanplay connection test failed: $message")
                settingsState.dandanplayConnectionTestStatus = SettingsConnectionTestStatus(
                    outcome = SettingsConnectionTestOutcome.FAILURE,
                    detail = message,
                )
            }
        }
    }

    fun testMyAnimeListConnection() {
        val clientId = settingsState.externalAnimeProviderSettings.myAnimeListClientId
        if (clientId.isNullOrBlank()) {
            appendDiagnostic("settings", "MyAnimeList connection test needs a saved client ID")
            settingsState.myAnimeListConnectionTestStatus = SettingsConnectionTestStatus(
                outcome = SettingsConnectionTestOutcome.FAILURE,
                detail = "Save a MyAnimeList client ID first.",
            )
            return
        }
        settingsState.myAnimeListConnectionTestStatus = SettingsConnectionTestStatus(
            outcome = SettingsConnectionTestOutcome.TESTING,
            detail = "Searching MyAnimeList with the saved client ID...",
        )
        scope.launch {
            appendDiagnostic("settings", "Testing saved MyAnimeList connection...")
            runCatching {
                withContext(Dispatchers.IO) {
                    MyAnimeListAnimeSearchClient(MyAnimeListSearchConnection(clientId))
                        .search(ExternalAnimeMatchQuery(title = "Frieren"), limit = 1)
                }
            }.onSuccess { results ->
                appendDiagnostic(
                    "settings",
                    "MyAnimeList connection OK: ${results.firstOrNull()?.titles?.primary ?: "no anime returned"}",
                )
                settingsState.myAnimeListConnectionTestStatus = SettingsConnectionTestStatus(
                    outcome = SettingsConnectionTestOutcome.SUCCESS,
                    detail = results.firstOrNull()?.titles?.primary ?: "No anime returned.",
                )
            }.onFailure {
                val message = it.readableMessage()
                appendDiagnostic("settings", "MyAnimeList connection test failed: $message")
                settingsState.myAnimeListConnectionTestStatus = SettingsConnectionTestStatus(
                    outcome = SettingsConnectionTestOutcome.FAILURE,
                    detail = message,
                )
            }
        }
    }

    fun testBangumiConnection() {
        val settings = settingsState.externalAnimeProviderSettings
        settingsState.bangumiConnectionTestStatus = SettingsConnectionTestStatus(
            outcome = SettingsConnectionTestOutcome.TESTING,
            detail = "Searching Bangumi with the saved base URL and User-Agent...",
        )
        scope.launch {
            appendDiagnostic("settings", "Testing saved Bangumi connection...")
            runCatching {
                withContext(Dispatchers.IO) {
                    BangumiAnimeSearchClient(
                        baseUri = URI(settings.bangumiBaseUrl),
                        userAgent = settings.bangumiUserAgent,
                    ).search(ExternalAnimeMatchQuery(title = "Frieren"), limit = 1)
                }
            }.onSuccess { results ->
                appendDiagnostic(
                    "settings",
                    "Bangumi connection OK: ${results.firstOrNull()?.titles?.primary ?: "no anime returned"}",
                )
                settingsState.bangumiConnectionTestStatus = SettingsConnectionTestStatus(
                    outcome = SettingsConnectionTestOutcome.SUCCESS,
                    detail = results.firstOrNull()?.titles?.primary ?: "No anime returned.",
                )
            }.onFailure {
                val message = it.readableMessage()
                appendDiagnostic("settings", "Bangumi connection test failed: $message")
                settingsState.bangumiConnectionTestStatus = SettingsConnectionTestStatus(
                    outcome = SettingsConnectionTestOutcome.FAILURE,
                    detail = message,
                )
            }
        }
    }

    fun testLocalServerConnection() {
        val baseUrl = serverBaseUrl()
        val token = pairingToken()
        settingsState.localServerConnectionTestStatus = SettingsConnectionTestStatus(
            outcome = SettingsConnectionTestOutcome.TESTING,
            detail = "Checking status and catalog access...",
        )
        scope.launch {
            appendDiagnostic("settings", "Testing local server at $baseUrl...")
            runCatching {
                withContext(Dispatchers.IO) {
                    val client = JvmLanLibraryClient()
                    val session = LanLibraryConnectionSession(client)
                    val status = session.validateServer(baseUrl)
                    val catalog = client.fetchCatalog(baseUrl, token)
                    status to catalog.items.size
                }
            }.onSuccess { (status, itemCount) ->
                appendDiagnostic(
                    "settings",
                    "Local server OK: API ${status.apiVersion}, streaming=${status.mediaStreaming}, items=$itemCount",
                )
                settingsState.localServerConnectionTestStatus = SettingsConnectionTestStatus(
                    outcome = SettingsConnectionTestOutcome.SUCCESS,
                    detail = "API ${status.apiVersion}, streaming=${status.mediaStreaming}, $itemCount items",
                )
            }.onFailure {
                val message = it.readableMessage()
                appendDiagnostic("settings", "Local server test failed: $message")
                settingsState.localServerConnectionTestStatus = SettingsConnectionTestStatus(
                    outcome = SettingsConnectionTestOutcome.FAILURE,
                    detail = message,
                )
            }
        }
    }

    fun cleanupExpiredDandanplayCaches() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    dandanplayDanmakuResolver.cleanupExpiredCaches()
                    catalogStore.loadDandanplayCommentCaches()
                }
            }.onSuccess { updatedEntries ->
                settingsState.dandanplayCacheEntries = updatedEntries
                appendDiagnostic("danmaku", "Cleaned up expired dandanplay cache entries")
            }.onFailure {
                appendDiagnostic("danmaku", "Failed to clean expired dandanplay cache entries: ${it.message}")
            }
        }
    }

    fun refreshDandanplayCacheEntries() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    catalogStore.loadDandanplayCommentCaches()
                }
            }.onSuccess {
                settingsState.dandanplayCacheEntries = it
                appendDiagnostic("danmaku", "Reloaded ${it.size} dandanplay cache entr${if (it.size == 1) "y" else "ies"}")
            }.onFailure {
                appendDiagnostic("danmaku", "Failed to reload dandanplay cache entries: ${it.message}")
            }
        }
    }

    fun deleteDandanplayCacheEntry(mediaId: String) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    dandanplayDanmakuResolver.clearCache(mediaId)
                    catalogStore.loadDandanplayCommentCaches()
                }
            }.onSuccess { updatedEntries ->
                settingsState.dandanplayCacheEntries = updatedEntries
                if (libraryState.dandanplayCacheStatus?.mediaId == mediaId) {
                    libraryState.dandanplayCacheStatus = dandanplayStatusMessage(
                        mediaId = mediaId,
                        summary = "dandanplay cache cleared",
                    )
                }
                appendDiagnostic("danmaku", "Deleted dandanplay cache entry for $mediaId")
            }.onFailure {
                appendDiagnostic("danmaku", "Failed to delete dandanplay cache entry for $mediaId: ${it.message}")
            }
        }
    }

    fun saveDanmakuSettings(settings: DanmakuDisplaySettings) {
        runCatching {
            playbackPreferencesStore.saveDanmakuSettings(settings)
            playbackPreferencesStore.load()
        }.onSuccess { updatedPreferences ->
            settingsState.updatePlaybackPreferences(updatedPreferences)
            updateOverlayStatus(
                if (updatedPreferences.danmakuSettings.visible) {
                    "Danmaku settings saved; reload media or refresh cache to apply"
                } else {
                    "Danmaku hidden by display settings"
                },
            )
            appendDiagnostic("settings", "Saved danmaku display settings")
        }.onFailure {
            appendDiagnostic("settings", "Failed to save danmaku display settings: ${it.message}")
        }
    }
}
