package app.danmaku.desktop

import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopLibraryRootRegistryTest {
    @Test
    fun registersUserSelectedAndAniRssRootsWithStableIds() {
        val temp = createTempDirectory("danmaku-root-registry")
        val animeRoot = temp.resolve("Anime").createDirectories()
        val aniRssRoot = temp.resolve("ani-rss").createDirectories()
        var now = 100L

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            val registry = DesktopLibraryRootRegistry(store) { now }
            val userRoot = registry.addUserSelectedRoot(animeRoot)
            now = 200
            val sameUserRoot = registry.addUserSelectedRoot(animeRoot)
            val aniRssOutputRoot = registry.addAniRssOutputRoot(
                path = aniRssRoot,
                displayName = "ani-rss completed",
            )

            assertEquals(userRoot.id, sameUserRoot.id)
            assertEquals(100, sameUserRoot.addedAtEpochMs)
            assertEquals(DesktopLibraryRootProvenance.USER_SELECTED, userRoot.provenance)
            assertEquals(DesktopLibraryRootProvenance.ANI_RSS_OUTPUT_FOLDER, aniRssOutputRoot.provenance)
            assertEquals(listOf(sameUserRoot, aniRssOutputRoot), registry.loadRoots())
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun marksRootsAvailableAndMissingAfterScans() {
        val temp = createTempDirectory("danmaku-root-registry")
        val animeRoot = temp.resolve("Anime").createDirectories()
        var now = 100L

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            val registry = DesktopLibraryRootRegistry(store) { now }
            val root = registry.addUserSelectedRoot(animeRoot)

            now = 200
            val missing = registry.markMissing(root, "Folder is no longer available")
            assertEquals(DesktopLibraryRootState.MISSING, missing.state)
            assertEquals(200, missing.lastScannedAtEpochMs)
            assertEquals("Folder is no longer available", missing.lastError)

            now = 300
            val available = registry.markAvailable(missing)
            assertEquals(DesktopLibraryRootState.AVAILABLE, available.state)
            assertEquals(300, available.lastScannedAtEpochMs)
            assertEquals(null, available.lastError)
            assertEquals(available, store.loadLibraryRoot(root.id))
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun rejectsMissingRootsAtRegistrationTime() {
        val temp = createTempDirectory("danmaku-root-registry")

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            val registry = DesktopLibraryRootRegistry(store) { 100 }
            assertFailsWith<IllegalArgumentException> {
                registry.addUserSelectedRoot(temp.resolve("Missing"))
            }
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun prefixesRootIdsByProvenance() {
        val temp = createTempDirectory("danmaku-root-registry")
        val root = temp.resolve("Anime").createDirectories()

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            val registry = DesktopLibraryRootRegistry(store) { 100 }

            assertTrue(registry.addUserSelectedRoot(root).id.startsWith("user_selected-"))
            assertTrue(registry.addAniRssOutputRoot(root).id.startsWith("ani_rss_output_folder-"))
        }

        temp.toFile().deleteRecursively()
    }
}
