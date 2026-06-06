package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

class LanLibraryServerStatusTest {
    @Test
    fun validatesServerStatusFields() {
        assertFailsWith<IllegalArgumentException> {
            LanLibraryServerStatus(appName = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            LanLibraryServerStatus(apiVersion = 0)
        }
    }
}
