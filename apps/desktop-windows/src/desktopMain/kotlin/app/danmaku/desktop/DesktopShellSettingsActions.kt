package app.danmaku.desktop

import app.danmaku.domain.DanmakuDisplaySettings
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.jvm.JvmLanLibraryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DesktopShellSettingsActions(
    private val scope: CoroutineScope,
    private val playbackPreferencesStore: DesktopPlaybackPreferencesStore,
    private val dandanplayCredentialStore: DandanplayCredentialStore,
    private val externalAnimeCredentialStore: ExternalAnimeCredentialStore,
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
        @Suppress("UNUSED_PARAMETER") myAnimeListClientId: String?,
        @Suppress("UNUSED_PARAMETER") myAnimeListClientSecret: String?,
    ) {
        appendDiagnostic(
            "settings",
            "MyAnimeList OAuth is managed by the Rust server; the removed embedded callback is unavailable in Compose desktop.",
        )
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
                    LanLibraryConnectionSession(JvmLanLibraryClient()).validateServer(serverBaseUrl())
                }
            }.onSuccess { status ->
                appendDiagnostic(
                    "settings",
                    "Rust provider host is reachable: API ${status.apiVersion}",
                )
                settingsState.dandanplayConnectionTestStatus = SettingsConnectionTestStatus(
                    outcome = SettingsConnectionTestOutcome.SUCCESS,
                    detail = "Rust server API ${status.apiVersion}",
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
        settingsState.myAnimeListConnectionTestStatus = SettingsConnectionTestStatus(
            outcome = SettingsConnectionTestOutcome.TESTING,
            detail = "Searching MyAnimeList with the saved client ID...",
        )
        scope.launch {
            appendDiagnostic("settings", "Testing saved MyAnimeList connection...")
            runCatching {
                withContext(Dispatchers.IO) {
                    LanLibraryConnectionSession(JvmLanLibraryClient()).validateServer(serverBaseUrl())
                }
            }.onSuccess { status ->
                appendDiagnostic(
                    "settings",
                    "Rust provider host is reachable: API ${status.apiVersion}",
                )
                settingsState.myAnimeListConnectionTestStatus = SettingsConnectionTestStatus(
                    outcome = SettingsConnectionTestOutcome.SUCCESS,
                    detail = "Rust server API ${status.apiVersion}",
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
        settingsState.bangumiConnectionTestStatus = SettingsConnectionTestStatus(
            outcome = SettingsConnectionTestOutcome.TESTING,
            detail = "Searching Bangumi with the saved base URL and User-Agent...",
        )
        scope.launch {
            appendDiagnostic("settings", "Testing saved Bangumi connection...")
            runCatching {
                withContext(Dispatchers.IO) {
                    LanLibraryConnectionSession(JvmLanLibraryClient()).validateServer(serverBaseUrl())
                }
            }.onSuccess { status ->
                appendDiagnostic(
                    "settings",
                    "Rust provider host is reachable: API ${status.apiVersion}",
                )
                settingsState.bangumiConnectionTestStatus = SettingsConnectionTestStatus(
                    outcome = SettingsConnectionTestOutcome.SUCCESS,
                    detail = "Rust server API ${status.apiVersion}",
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
