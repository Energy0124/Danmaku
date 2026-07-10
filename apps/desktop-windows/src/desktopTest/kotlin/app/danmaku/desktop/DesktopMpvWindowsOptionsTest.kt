package app.danmaku.desktop

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopMpvWindowsOptionsTest {
    @Test
    fun usesBuiltInOscWhenNoCustomControlScriptIsProvided() {
        assertEquals(
            mapOf(
                "input-default-bindings" to "yes",
                "input-cursor" to "yes",
                "input-vo-keyboard" to "yes",
                "osc" to "yes",
                "script-opts" to "osc-layout=bottombar,osc-visibility=always",
                "wid" to "18446744073709551615",
            ),
            DesktopMpvWindowsOptions.forWindowId(-1L),
        )
    }

    @Test
    fun usesCustomOscScriptWhenProvided() {
        val scriptPath = Path.of("S:/Projects/Danmaku/build/test-danmaku-osc.lua")

        assertEquals(
            mapOf(
                "input-default-bindings" to "yes",
                "input-cursor" to "yes",
                "input-vo-keyboard" to "yes",
                "osc" to "no",
                "scripts" to scriptPath.toAbsolutePath().normalize().toString(),
                "wid" to "18446744073709551615",
            ),
            DesktopMpvWindowsOptions.forWindowId(-1L, scriptPath),
        )
    }

    @Test
    fun preservesFullUnsignedWindowsHandle() {
        val windowHandle = 0x1_0000_0001L

        assertEquals(
            "4294967297",
            DesktopMpvWindowsOptions.forWindowId(windowHandle).getValue("wid"),
        )
    }

    @Test
    fun rejectsZeroWindowHandles() {
        assertFailsWith<IllegalArgumentException> {
            DesktopMpvWindowsOptions.forWindowId(0L)
        }
    }
}
