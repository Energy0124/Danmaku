package app.danmaku.desktop

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class AniRssCompletionRescanTrigger(
    private val scanAniRssRoots: () -> DesktopLibraryScanBatchResult,
    private val onScanComplete: (DesktopLibraryScanBatchResult) -> Unit = {},
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
) : AutoCloseable {
    private val lock = Any()
    private var pendingScan: ScheduledFuture<*>? = null

    init {
        require(debounceMillis >= 0) { "debounceMillis must not be negative" }
    }

    fun requestRescan(): Boolean =
        synchronized(lock) {
            if (pendingScan?.isDone == false) {
                false
            } else {
                pendingScan = scheduler.schedule(
                    ::runScan,
                    debounceMillis,
                    TimeUnit.MILLISECONDS,
                )
                true
            }
        }

    override fun close() {
        scheduler.shutdownNow()
    }

    private fun runScan() {
        try {
            onScanComplete(scanAniRssRoots())
        } finally {
            synchronized(lock) {
                pendingScan = null
            }
        }
    }

    private companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 1_000L
    }
}
