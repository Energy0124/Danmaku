package app.danmaku.mobile

import androidx.test.platform.app.InstrumentationRegistry
import app.danmaku.domain.DanmakuDisplaySettings
import org.junit.Assert.assertEquals
import org.junit.Test

class MobileDanmakuSettingsStoreTest {
    @Test
    fun savesAndReloadsPlaybackDanmakuSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = MobileDanmakuSettingsStore(context)
        val original = store.load()
        val expected = DanmakuDisplaySettings(
            visible = false,
            opacityPercent = 60,
            fontScalePercent = 130,
            speedPercent = 150,
            densityPercent = 80,
            displayAreaPercent = 40,
            offsetMs = 1_500L,
        )

        try {
            store.save(expected)
            assertEquals(expected, MobileDanmakuSettingsStore(context).load())
        } finally {
            store.save(original)
        }
    }
}
