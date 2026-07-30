package app.danmaku.tv

import android.content.Context
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.PlaybackProgress
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal data class TvCachedCatalog(
    val catalog: LibraryCatalog,
    val playbackProgresses: List<PlaybackProgress>,
    val cachedAtEpochMs: Long,
)

internal interface TvCatalogCache {
    suspend fun load(serverUrl: String): TvCachedCatalog?

    suspend fun save(
        serverUrl: String,
        catalog: LibraryCatalog,
        playbackProgresses: List<PlaybackProgress>,
    )

    suspend fun clear(serverUrl: String)
}

internal class AndroidTvCatalogCache(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) : TvCatalogCache {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override suspend fun load(serverUrl: String): TvCachedCatalog? =
        withContext(ioDispatcher) {
            val key = serverUrl.cacheKey()
            if (preferences.getInt("$key.version", 0) != CACHE_VERSION) {
                remove(key)
                return@withContext null
            }
            runCatching {
                val catalogJson = preferences.getString("$key.catalog", null)
                    ?: return@runCatching null
                val progressJson = preferences.getString("$key.progress", null)
                    ?: "[]"
                TvCachedCatalog(
                    catalog = json.decodeFromString(catalogJson),
                    playbackProgresses = json.decodeFromString(progressJson),
                    cachedAtEpochMs = preferences.getLong("$key.cached_at", 0L),
                )
            }.getOrElse {
                remove(key)
                null
            }
        }

    override suspend fun save(
        serverUrl: String,
        catalog: LibraryCatalog,
        playbackProgresses: List<PlaybackProgress>,
    ) {
        withContext(ioDispatcher) {
            val key = serverUrl.cacheKey()
            preferences.edit()
                .putInt("$key.version", CACHE_VERSION)
                .putString("$key.catalog", json.encodeToString(catalog))
                .putString("$key.progress", json.encodeToString(playbackProgresses))
                .putLong("$key.cached_at", now())
                .commit()
        }
    }

    override suspend fun clear(serverUrl: String) {
        withContext(ioDispatcher) {
            remove(serverUrl.cacheKey())
        }
    }

    private fun remove(key: String) {
        preferences.edit()
            .remove("$key.version")
            .remove("$key.catalog")
            .remove("$key.progress")
            .remove("$key.cached_at")
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "danmaku_tv_catalog_cache"
        const val CACHE_VERSION = 1
    }
}

private fun String.cacheKey(): String {
    val normalized = trim().trimEnd('/').lowercase()
    val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
    return digest.take(16).joinToString(separator = "") { byte -> "%02x".format(byte) }
}
