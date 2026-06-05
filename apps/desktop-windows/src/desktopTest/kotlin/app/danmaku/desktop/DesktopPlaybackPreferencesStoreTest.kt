package app.danmaku.desktop

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPlaybackPreferencesStoreTest {
    @Test
    fun loadsDefaultsWhenSettingsAreMissing() {
        val temp = createTempDirectory("danmaku-playback-preferences")

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { catalogStore ->
            val store = DesktopPlaybackPreferencesStore(catalogStore)

            assertEquals(DesktopPlaybackPreferences(), store.load())
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun persistsPlaybackPreferences() {
        val temp = createTempDirectory("danmaku-playback-preferences")
        val databasePath = temp.resolve("catalog.db")

        DesktopLibraryCatalogStore(databasePath).use { catalogStore ->
            val store = DesktopPlaybackPreferencesStore(catalogStore, currentTimeMillis = { 123 })

            store.savePlaybackRate(1.25f)
            store.saveVolumePercent(42)
            store.saveVideoAspectMode(DesktopVideoAspectMode.WIDE_16_9)
        }

        DesktopLibraryCatalogStore(databasePath).use { catalogStore ->
            val store = DesktopPlaybackPreferencesStore(catalogStore)

            assertEquals(
                DesktopPlaybackPreferences(
                    playbackRate = 1.25f,
                    volumePercent = 42,
                    videoAspectMode = DesktopVideoAspectMode.WIDE_16_9,
                ),
                store.load(),
            )
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun ignoresInvalidStoredPlaybackPreferences() {
        val temp = createTempDirectory("danmaku-playback-preferences")

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { catalogStore ->
            catalogStore.saveSetting(DesktopAppSetting("playback.default_rate", "0", 1))
            catalogStore.saveSetting(DesktopAppSetting("playback.volume_percent", "900", 1))
            catalogStore.saveSetting(DesktopAppSetting("playback.video_aspect_mode", "CINEMA", 1))

            assertEquals(DesktopPlaybackPreferences(), DesktopPlaybackPreferencesStore(catalogStore).load())
        }

        temp.toFile().deleteRecursively()
    }
}
