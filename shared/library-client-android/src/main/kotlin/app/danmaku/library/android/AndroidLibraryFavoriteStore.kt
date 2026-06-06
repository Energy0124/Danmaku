package app.danmaku.library.android

import android.content.Context

class AndroidLibraryFavoriteStore(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun loadFavoriteMediaIds(): Set<String> =
        preferences
            .getString(FAVORITE_MEDIA_IDS_KEY, null)
            ?.lineSequence()
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.toSet()
            ?: emptySet()

    fun saveFavoriteMediaIds(mediaIds: Set<String>) {
        require(mediaIds.none { it.isBlank() }) { "favorite media ids must not contain blank ids" }
        preferences.edit().apply {
            if (mediaIds.isEmpty()) {
                remove(FAVORITE_MEDIA_IDS_KEY)
            } else {
                putString(FAVORITE_MEDIA_IDS_KEY, mediaIds.sorted().joinToString("\n"))
            }
        }.apply()
    }

    fun setFavoriteMediaId(mediaId: String, isFavorite: Boolean): Set<String> {
        require(mediaId.isNotBlank()) { "mediaId must not be blank" }
        val updatedMediaIds = if (isFavorite) {
            loadFavoriteMediaIds() + mediaId
        } else {
            loadFavoriteMediaIds() - mediaId
        }
        saveFavoriteMediaIds(updatedMediaIds)
        return loadFavoriteMediaIds()
    }

    private companion object {
        const val PREFERENCES_NAME = "danmaku_library_favorites"
        const val FAVORITE_MEDIA_IDS_KEY = "favorite_media_ids"
    }
}
