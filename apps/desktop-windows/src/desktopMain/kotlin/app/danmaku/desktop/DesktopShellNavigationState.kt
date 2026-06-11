package app.danmaku.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DesktopShellNavigationState(
    private val catalogStore: DesktopLibraryCatalogStore,
    private val scope: CoroutineScope,
    private val appendDiagnostic: (String, String) -> Unit,
) {
    var selectedTab by mutableStateOf(DesktopShellTab.HOME)
    var globalSearchText by mutableStateOf("")
    var librarySearchSeed by mutableStateOf("")
    var librarySearchSeedVersion by mutableStateOf(0)
    val globalSearchFocusRequester = FocusRequester()

    var desktopLanguage by mutableStateOf(
        DesktopUiLanguage.fromStorageValue(
            catalogStore.loadSetting(DESKTOP_UI_LANGUAGE_SETTING_KEY)?.value,
        ),
    )

    val desktopStrings: DesktopStrings
        get() = desktopLanguage.strings

    fun submitGlobalSearch() {
        val query = globalSearchText.trim()
        if (query.isBlank()) {
            return
        }
        librarySearchSeed = query
        librarySearchSeedVersion += 1
        selectedTab = DesktopShellTab.MEDIA_LIBRARY
        appendDiagnostic("shell", "Global search routed to Library: $query")
    }

    fun selectTabFromShortcut(tab: DesktopShellTab): Boolean {
        selectedTab = tab
        appendDiagnostic("shell", "Shortcut routed to ${desktopStrings.tabTitle(tab)}")
        return true
    }

    fun updateDesktopLanguage(language: DesktopUiLanguage) {
        if (desktopLanguage == language) {
            return
        }
        desktopLanguage = language
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    catalogStore.saveSetting(
                        DesktopAppSetting(
                            key = DESKTOP_UI_LANGUAGE_SETTING_KEY,
                            value = language.storageValue,
                            updatedAtEpochMs = System.currentTimeMillis(),
                        ),
                    )
                }
            }.onSuccess {
                appendDiagnostic("settings", "Desktop language set to ${language.displayName}")
            }.onFailure {
                appendDiagnostic("settings", "Failed to save desktop language: ${it.message}")
            }
        }
    }
}

@Composable
internal fun rememberDesktopShellNavigationState(
    catalogStore: DesktopLibraryCatalogStore,
    scope: CoroutineScope,
    appendDiagnostic: (String, String) -> Unit,
): DesktopShellNavigationState =
    remember(catalogStore, scope) {
        DesktopShellNavigationState(
            catalogStore = catalogStore,
            scope = scope,
            appendDiagnostic = appendDiagnostic,
        )
    }
