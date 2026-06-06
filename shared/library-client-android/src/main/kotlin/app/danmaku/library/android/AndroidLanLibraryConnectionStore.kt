package app.danmaku.library.android

import android.content.Context
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.library.lanLibraryConnectionProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidLanLibraryConnectionStore(
    context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun loadProfiles(): List<LanLibraryConnectionProfile> =
        preferences
            .getString(PROFILES_KEY, null)
            ?.let(::decodeProfiles)
            ?: emptyList()

    fun saveProfile(profile: LanLibraryConnectionProfile) {
        val updatedProfiles = listOf(profile) + loadProfiles().filterNot { it.id == profile.id }
        saveProfiles(updatedProfiles.take(MAX_PROFILES))
    }

    fun saveCurrentConnection(
        baseUrl: String,
        pairingToken: String,
        displayName: String? = null,
        connectedAtEpochMs: Long = System.currentTimeMillis(),
    ): LanLibraryConnectionProfile {
        val profile = lanLibraryConnectionProfile(
            baseUrl = baseUrl,
            pairingToken = pairingToken,
            displayName = displayName,
            lastConnectedAtEpochMs = connectedAtEpochMs,
        )
        saveProfile(profile)
        return profile
    }

    fun forgetProfile(profileId: String) {
        saveProfiles(loadProfiles().filterNot { it.id == profileId })
    }

    private fun saveProfiles(profiles: List<LanLibraryConnectionProfile>) {
        preferences.edit()
            .putString(PROFILES_KEY, json.encodeToString(profiles.map { it.toStoredProfile() }))
            .apply()
    }

    private fun decodeProfiles(encodedProfiles: String): List<LanLibraryConnectionProfile> =
        runCatching {
            json.decodeFromString<List<StoredConnectionProfile>>(encodedProfiles)
                .mapNotNull { it.toConnectionProfileOrNull() }
                .distinctBy { it.id }
        }.getOrDefault(emptyList())

    private companion object {
        const val PREFERENCES_NAME = "danmaku_lan_library_connections"
        const val PROFILES_KEY = "profiles"
        const val MAX_PROFILES = 8
    }
}

@Serializable
private data class StoredConnectionProfile(
    val baseUrl: String,
    val pairingToken: String,
    val displayName: String,
    val lastConnectedAtEpochMs: Long? = null,
)

private fun LanLibraryConnectionProfile.toStoredProfile(): StoredConnectionProfile =
    StoredConnectionProfile(
        baseUrl = normalizedBaseUrl,
        pairingToken = pairingToken,
        displayName = displayName,
        lastConnectedAtEpochMs = lastConnectedAtEpochMs,
    )

private fun StoredConnectionProfile.toConnectionProfileOrNull(): LanLibraryConnectionProfile? =
    runCatching {
        lanLibraryConnectionProfile(
            baseUrl = baseUrl,
            pairingToken = pairingToken,
            displayName = displayName,
            lastConnectedAtEpochMs = lastConnectedAtEpochMs,
        )
    }.getOrNull()
