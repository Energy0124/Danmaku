package app.danmaku.desktop

import app.danmaku.domain.LibraryCatalog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AniRssCompletionRescanTriggerTest {
    @Test
    fun debouncesRepeatedCompletionNotifications() {
        val scanCount = AtomicInteger()
        val completed = CountDownLatch(1)
        val result = DesktopLibraryScanBatchResult(
            results = emptyList(),
            publishedLibrary = IndexedLocalLibrary(
                catalog = LibraryCatalog(
                    rootName = "Empty",
                    indexedAtEpochMs = 0,
                    items = emptyList(),
                ),
                filesById = emptyMap(),
                fileMetadataByRelativePath = emptyMap(),
                scanStats = LocalMediaLibraryScanStats(0, 0),
            ),
        )

        AniRssCompletionRescanTrigger(
            scanAniRssRoots = {
                scanCount.incrementAndGet()
                result
            },
            onScanComplete = { completed.countDown() },
            debounceMillis = 50,
        ).use { trigger ->
            assertTrue(trigger.requestRescan())
            assertFalse(trigger.requestRescan())
            assertFalse(trigger.requestRescan())
            assertTrue(completed.await(2, TimeUnit.SECONDS))
            assertEquals(1, scanCount.get())
        }
    }
}
